package com.kakario.ebookreader.data

import android.content.Context

/** 만화(ZIP)를 열 때의 보기 방식. 회전도 이 값에 따라 강제로 맞춘다. */
enum class ComicLayout(val label: String, val short: String) {
    DOUBLE_LANDSCAPE("두 장 · 가로 고정", "두 장"),
    SINGLE_PORTRAIT("한 장 · 세로 고정", "한 장"),
}

/** 앱 설정 전체. 화면에서는 이 묶음 하나를 주고받는다. */
data class AppSettings(
    // ── 만화
    val comicLayout: ComicLayout = ComicLayout.DOUBLE_LANDSCAPE,
    /** 두 장 보기에서 표지(첫 장)만 한 장으로 따로 보여주기 */
    val coverAlone: Boolean = true,
    /** 넘길 때 옆으로 미끄러지는 효과 (끄면 바로 바뀜) */
    val slideAnimation: Boolean = false,
    /** FTP 동시 연결 수 (1~4) */
    val ftpConnections: Int = 3,
    /** 미리 받아둘 페이지 수 (2~12) */
    val prefetchPages: Int = 6,
    // ── 글자 책 (TXT·EPUB)
    /** 글자 크기 (sp) */
    val fontSize: Int = 19,
    /** 줄 간격 ×10 (17 = 1.7배) */
    val lineSpacing: Int = 17,
    /** 좌우 여백 (dp) */
    val margin: Int = 22,
    // ── 공통
    /** 보는 중 화면 아래에 시간·진행률 표시 */
    val showInfoBar: Boolean = true,
    /** 볼륨 버튼으로 넘기기 (페이지 터너 키는 항상 동작) */
    val volumeKeys: Boolean = true,
    /** 목록에 표지 작게 보여주기 (끄면 글자만) */
    val listCovers: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            comicLayout = prefs.getString("comic_layout", null)
                ?.let { n -> ComicLayout.entries.firstOrNull { it.name == n } } ?: d.comicLayout,
            coverAlone = prefs.getBoolean("cover_alone", d.coverAlone),
            slideAnimation = prefs.getBoolean("slide_animation", d.slideAnimation),
            ftpConnections = prefs.getInt("ftp_connections", d.ftpConnections).coerceIn(1, 4),
            prefetchPages = prefs.getInt("prefetch_pages", d.prefetchPages).coerceIn(2, 12),
            fontSize = prefs.getInt("font_size", d.fontSize).coerceIn(FONT_MIN, FONT_MAX),
            lineSpacing = prefs.getInt("line_spacing", d.lineSpacing).coerceIn(LINE_MIN, LINE_MAX),
            margin = prefs.getInt("margin", d.margin).coerceIn(MARGIN_MIN, MARGIN_MAX),
            showInfoBar = prefs.getBoolean("info_bar", d.showInfoBar),
            volumeKeys = prefs.getBoolean("volume_keys", d.volumeKeys),
            listCovers = prefs.getBoolean("list_covers", d.listCovers),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("comic_layout", s.comicLayout.name)
            .putBoolean("cover_alone", s.coverAlone)
            .putBoolean("slide_animation", s.slideAnimation)
            .putInt("ftp_connections", s.ftpConnections)
            .putInt("prefetch_pages", s.prefetchPages)
            .putInt("font_size", s.fontSize)
            .putInt("line_spacing", s.lineSpacing)
            .putInt("margin", s.margin)
            .putBoolean("info_bar", s.showInfoBar)
            .putBoolean("volume_keys", s.volumeKeys)
            .putBoolean("list_covers", s.listCovers)
            .apply()
    }

    companion object {
        const val FONT_MIN = 12
        const val FONT_MAX = 36
        const val LINE_MIN = 12
        const val LINE_MAX = 26
        const val MARGIN_MIN = 4
        const val MARGIN_MAX = 60
    }
}
