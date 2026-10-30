package com.kakario.ebookreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kakario.ebookreader.data.AppSettings
import com.kakario.ebookreader.data.RefreshStyle
import kotlinx.coroutines.delay

/**
 * 이잉크 화면 새로고침(잔상 지우기).
 * 이잉크는 화면을 조금씩 바꾸면 전 페이지 글자가 흐릿하게 남는다.
 * 페이지를 넘길 때 화면 전체를 잠깐 검게 → 희게 칠했다가 새 페이지를 보여주면
 * 화면이 통째로 다시 그려져 찌꺼기가 사라진다.
 */
class Refresher {
    private var settings = AppSettings()
    private var pages = 0

    /** 올라갈 때마다 한 번 깜빡인다. */
    var flash by mutableIntStateOf(0)
        private set

    val style: RefreshStyle get() = settings.refreshStyle
    val durationMs: Long get() = settings.refreshMs.toLong()

    fun update(s: AppSettings) {
        settings = s
    }

    /** 페이지를 한 장 넘겼을 때. 설정한 장 수마다 깜빡인다. */
    fun pageTurn() {
        val every = settings.refreshEvery
        if (every <= 0) return
        pages++
        if (pages >= every) {
            pages = 0
            flash++
        }
    }

    /** 목록·화면이 바뀌었을 때. */
    fun screenChange() {
        if (!settings.refreshOnScreens) return
        pages = 0
        flash++
    }

    /** 지금 바로 (메뉴의 새로고침 버튼). */
    fun now() {
        pages = 0
        flash++
    }
}

val LocalRefresher = compositionLocalOf<Refresher?> { null }

/** 맨 위에 덮어서 잠깐 칠하는 판. 다 칠하면 사라진다. */
@Composable
fun RefreshOverlay(refresher: Refresher) {
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(refresher.flash) {
        if (refresher.flash == 0) return@LaunchedEffect
        val ms = refresher.durationMs
        try {
            if (refresher.style == RefreshStyle.BLACK) {
                color = Ink
                delay(ms)
                color = Paper
                delay(ms * 2 / 3)
            } else {
                color = Paper
                delay(ms)
            }
        } finally {
            color = null
        }
    }
    color?.let { Box(Modifier.fillMaxSize().background(it)) }
}
