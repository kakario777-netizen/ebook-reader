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
                fl.full -> {
                    paint = Paint.Fill(Ink)
                    delay(fl.ms)
                    paint = Paint.Fill(Paper)
                    delay((fl.ms / 3).coerceAtLeast(60))
                    paint = null
                    // 화면이 다 그려진 뒤 기기 자체 전체 새로고침 (지원 기기만, 아니면 아무 일 없음)
                    if (refresher.deviceRefresh) {
                        delay(16)
                        EinkDevice.fullRefresh(view)
                    }
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
 * 기기 자체 이잉크 새로고침 부르기.
 * 일부 이잉크 기기(오닉스 BOOX 계열 등)는 앱이 '전체 새로고침(GC)'을 요청할 수 있는 숨은 기능이 있다.
 * 기능이 없는 기기에서는 조용히 아무것도 하지 않는다.
 */
object EinkDevice {
    private val onyx: Pair<java.lang.reflect.Method, Any>? by lazy {
        runCatching {
            val controller = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val modeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")
            val gc = modeClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "GC" } ?: return@runCatching null
            controller.getMethod("invalidate", View::class.java, modeClass) to gc
        }.getOrNull()
    }

    val supported: Boolean get() = onyx != null

    fun fullRefresh(view: View) {
        val (method, gc) = onyx ?: return
        runCatching { method.invoke(null, view, gc) }
    }
}
