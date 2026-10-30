package com.kakario.ebookreader.ui

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kakario.ebookreader.data.ReaderFont

/**
 * 앱에 넣은 한글 글꼴 (둘 다 무료 OFL 글꼴, 빌드할 때 받아 넣는다).
 *  - 고딕: Noto Sans KR 가변 글꼴 — 굵기를 100~900까지 '진짜로' 바꿀 수 있다.
 *    (기기 기본 한글 글꼴은 보통 굵기 하나뿐이라, 굵게를 골라도 얇게 나오는 문제가 있었다)
 *  - 명조: 나눔명조 보통·굵게·아주 굵게
 * 파일이 없으면(예: 빌드 중 받기 실패) 기기 글꼴을 쓴다.
 */
object AppFonts {
    var gothic: FontFamily = FontFamily.Default
        private set
    var serif: FontFamily = FontFamily.Serif
        private set
    var bundled: Boolean = false
        private set

    fun init(context: Context) {
        val assets = context.assets
        val files = runCatching { assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
        if ("gothic.ttf" in files) {
            // 가변 글꼴: 굵기마다 같은 파일을 그 굵기로 불러온다
            gothic = FontFamily(
                (1..9).map { i -> Font("fonts/gothic.ttf", assets, FontWeight(i * 100)) }
            )
            bundled = true
        }
        val serifFiles = listOf(400, 700, 800).filter { "serif-$it.ttf" in files }
        if (serifFiles.isNotEmpty()) {
            serif = FontFamily(serifFiles.map { w -> Font("fonts/serif-$w.ttf", assets, FontWeight(w)) })
        }
    }

    fun forReader(font: ReaderFont): FontFamily = when (font) {
        ReaderFont.GOTHIC -> gothic
        ReaderFont.SERIF -> serif
        ReaderFont.SYSTEM -> FontFamily.Default
    }
}
