package com.kakario.ebookreader.ui

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.kakario.ebookreader.data.AppSettings
import com.kakario.ebookreader.data.LightRefresh
import kotlinx.coroutines.delay

/**
 * 이잉크 화면 새로고침(잔상 지우기) — 정품 이북리더기처럼 두 단계로 한다.
 *
 *  1) 가벼운 새로고침 (매 페이지): 앞 페이지와 새 페이지의 '글자 줄'만 아주 짧게 검게 칠했다 지운다.
 *     바뀌는 부분만 다시 그려지므로 빠르고, 글자가 진하고 깔끔하게 남는다.
 *  2) 전체 새로고침 (N페이지마다): 화면 전체를 약 0.3초 진하게 검게 칠했다가 희게 → 쌓인 잔상을 통째로 지운다.
 *     기기가 지원하면(오닉스 계열 등) 기기 자체 전체 새로고침도 함께 부른다.
 */
class Refresher {
    private var settings = AppSettings()
    private var pages = 0
    private var nextId = 1L

    class Flash(val id: Long, val full: Boolean, val rects: List<Rect>?, val ms: Long, val white: Boolean = false)

    var flash by mutableStateOf<Flash?>(null)
        private set

    val deviceRefresh: Boolean get() = settings.deviceRefresh

    fun update(s: AppSettings) {
        settings = s
    }

    /**
     * 페이지를 한 장 넘겼을 때.
     * textRects: 앞뒤 페이지 글자 줄 영역(화면 좌표). 그림 페이지처럼 없으면 null.
     */
    fun pageTurn(textRects: List<Rect>? = null) {
        pages++
        val every = settings.fullEvery
        if (every > 0 && pages >= every) {
            pages = 0
            fire(full = true, rects = null, ms = settings.fullMs.toLong())
            return
        }
        // 기기가 고화질(Regal) 모드로 그리는 중이면 매 페이지는 기기에 맡긴다 (기본 뷰어와 같은 방식)
        if (EinkDevice.active && settings.deviceRefresh) return
        when (settings.lightRefresh) {
            LightRefresh.OFF -> {}
            LightRefresh.TEXT -> if (!textRects.isNullOrEmpty()) {
                fire(full = false, rects = textRects, ms = settings.lightMs.toLong())
            }
            LightRefresh.WHITE -> fire(full = false, rects = null, ms = settings.lightMs.toLong(), white = true)
        }
    }

    /** 목록·화면이 바뀌었을 때 — 짧게 전체를 한 번. */
    fun screenChange() {
        if (!settings.refreshOnScreens) return
        pages = 0
        fire(full = true, rects = null, ms = (settings.fullMs / 2).toLong().coerceAtLeast(120))
    }

    /** 지금 바로 전체 새로고침 (메뉴의 버튼). */
    fun now() {
        pages = 0
        fire(full = true, rects = null, ms = settings.fullMs.toLong())
    }

    private fun fire(full: Boolean, rects: List<Rect>?, ms: Long, white: Boolean = false) {
        flash = Flash(nextId++, full, rects, ms, white)
    }
}

val LocalRefresher = compositionLocalOf<Refresher?> { null }

private sealed interface Paint {
    data class Fill(val color: Color) : Paint
    data class Bars(val rects: List<Rect>) : Paint
}

/** 맨 위에 덮어서 잠깐 칠하는 판. 다 칠하면 사라진다. */
@Composable
fun RefreshOverlay(refresher: Refresher) {
    val view = LocalView.current
    var paint by remember { mutableStateOf<Paint?>(null) }
    val f = refresher.flash
    LaunchedEffect(f?.id) {
        val fl = f ?: return@LaunchedEffect
        try {
            when {
                // 오닉스 기기: 직접 칠하지 않고 기기 자체 전체 새로고침
                fl.full && EinkDevice.active && refresher.deviceRefresh -> {
                    EinkDevice.fullRefresh(view)
                }
                fl.full -> {
                    paint = Paint.Fill(Ink)
                    delay(fl.ms)
                    paint = Paint.Fill(Paper)
                    delay((fl.ms / 3).coerceAtLeast(60))
                    paint = null
                }
                fl.white -> {
                    paint = Paint.Fill(Paper)
                    delay(fl.ms)
                }
                fl.rects != null -> {
                    paint = Paint.Bars(fl.rects)
                    delay(fl.ms)
                }
            }
        } finally {
            paint = null
        }
    }
    when (val p = paint) {
        is Paint.Fill -> Box(Modifier.fillMaxSize().background(p.color))
        is Paint.Bars -> Canvas(Modifier.fillMaxSize()) {
            p.rects.forEach { r -> drawRect(Ink, topLeft = Offset(r.left, r.top), size = Size(r.width, r.height)) }
        }
        null -> {}
    }
}

/**
 * 오닉스(BOOX) 계열 이잉크 기기 제어 — sam 10 Plus처럼 NeoReader가 들어 있는 기기.
 * 기기 기본 뷰어와 같은 방식으로 그리도록 앱 화면 전체를 'Regal(고화질)' 모드로 요청하고,
 * 전체 새로고침은 기기 자체 GC(진하게 한 번 번쩍)로 한다.
 * 오닉스가 아닌 기기에서는 요청이 거절되어 아무 일도 일어나지 않는다 (그때는 앱이 직접 칠하는 방식).
 */
object EinkDevice {
    /** 기기가 고화질 모드 요청을 받아들였는지 (받아들였으면 앱이 직접 깜빡이지 않는다). */
    @Volatile var active: Boolean = false
        private set

    /** 예전 설정 화면 호환: 기기 제어가 되는지. */
    val supported: Boolean get() = active

    private fun mode(vararg names: String): com.onyx.android.sdk.api.device.epd.UpdateMode? {
        val all = com.onyx.android.sdk.api.device.epd.UpdateMode.values()
        for (n in names) all.firstOrNull { it.name == n }?.let { return it }
        return null
    }

    /**
     * 기기 빠른 설정의 'A2 모드'(빠르지만 회색을 못 쓰는 모드)를 이 앱에서만 끈다.
     * 기기 기본 뷰어처럼 A2가 켜져 있어도 우리 앱은 고화질로 그리게 하려는 것.
     * 오닉스 도구 버전에 따라 이름이 달라서 있는 것을 찾아 부르고, 없으면 조용히 넘어간다.
     */
    private fun disableFastMode(view: View) {
        val c = com.onyx.android.sdk.api.device.epd.EpdController::class.java
        val pkg = view.context.packageName
        val z = java.lang.Boolean.TYPE
        runCatching { c.getMethod("clearApplicationFastMode").invoke(null) }
        runCatching { c.getMethod("applyApplicationFastMode", String::class.java, z, z).invoke(null, pkg, false, true) }
        runCatching { c.getMethod("disableA2ForSpecificView", View::class.java).invoke(null, view) }
        runCatching { c.getMethod("setAppScopeRefreshMode", String::class.java, Int::class.javaPrimitiveType).invoke(null, pkg, 0) }
    }

    /** 화면 전체를 책 읽기용 고화질 모드로. 성공하면 true. */
    fun applyReadingMode(view: View): Boolean {
        disableFastMode(view)
        val ok = runCatching {
            val m = mode("REGAL", "REGAL_D", "GU", "GC") ?: return@runCatching false
            com.onyx.android.sdk.api.device.epd.EpdController.setViewDefaultUpdateMode(view, m)
        }.getOrDefault(false)
        active = ok
        return ok
    }

    fun resetMode(view: View) {
        runCatching { com.onyx.android.sdk.api.device.epd.EpdController.resetViewUpdateMode(view) }
        active = false
    }

    /** 기기 자체 전체 새로고침 (진하게 한 번 번쩍여 잔상 지우기). */
    fun fullRefresh(view: View) {
        runCatching {
            val m = mode("GC", "GC4") ?: return@runCatching
            com.onyx.android.sdk.api.device.epd.EpdController.invalidate(view, m)
        }
    }
}
