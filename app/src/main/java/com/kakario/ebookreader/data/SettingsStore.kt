package com.kakario.ebookreader.data

import android.content.Context

/** 만화(ZIP)를 열 때의 보기 방식. 회전도 이 값에 따라 강제로 맞춘다. */
enum class ComicLayout(val label: String, val short: String) {
    DOUBLE_LANDSCAPE("두 장 · 가로 고정", "두 장"),
    SINGLE_PORTRAIT("한 장 · 세로 고정", "한 장"),
}

/** 글자 책 글꼴. */
enum class ReaderFont(val label: String) {
    DEFAULT("기본 고딕"),
    SERIF("명조"),
    MONO("고정폭"),
}

/** 화면 새로고침(잔상 지우기) 방식. */
enum class RefreshStyle(val label: String) {
    BLACK("검은 화면 → 흰 화면 (확실하게)"),
    WHITE("흰 화면만 (부드럽게)"),
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
    val readerFont: ReaderFont = ReaderFont.DEFAULT,
    /** 글자 크기 (sp) */
    val fontSize: Int = 20,
    /** 글꼴 굵기 100~900 (400 보통, 700 굵게) */
    val fontWeight: Int = 500,
    /** 획 두께 보정 0~8 (글꼴과 상관없이 획을 더 두껍게 칠함) */
    val strokeBoost: Int = 0,
    /** 줄 간격 ×10 (17 = 1.7배) */
    val lineSpacing: Int = 17,
    /** 자간 ×100 em (0 = 기본) */
    val letterSpacing: Int = 0,
    /** 문단 첫 줄 들여쓰기 (글자 수) */
    val indent: Int = 0,
    /** 양쪽 맞춤 (끄면 왼쪽 맞춤) */
    val justify: Boolean = true,
    /** 여백 (dp) */
    val marginTop: Int = 28,
    val marginBottom: Int = 20,
    val marginLeft: Int = 22,
    val marginRight: Int = 22,

    // ── 화면 새로고침 (이잉크 잔상 지우기)
    /** 몇 페이지마다 새로고침할지 (0 = 안 함) */
    val refreshEvery: Int = 1,
    val refreshStyle: RefreshStyle = RefreshStyle.BLACK,
    /** 목록·화면이 바뀔 때도 새로고침 */
    val refreshOnScreens: Boolean = true,
    /** 깜빡이는 시간 (ms) */
    val refreshMs: Int = 100,

    // ── 화면 전체
    /** 앱 글자 굵기 단계 (0 보통, 1 조금 굵게, 2 굵게) */
    val uiWeight: Int = 1,
    /** 앱 글자 크기 % */
    val uiScale: Int = 105,
    /** 책장 한 줄 권수 */
    val shelfColumns: Int = 3,
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
        val oldMargin = prefs.getInt("margin", -1) // 예전 버전의 좌우 여백 하나
        return AppSettings(
            comicLayout = enumOf(prefs.getString("comic_layout", null), d.comicLayout),
            coverAlone = prefs.getBoolean("cover_alone", d.coverAlone),
            slideAnimation = prefs.getBoolean("slide_animation", d.slideAnimation),
            ftpConnections = prefs.getInt("ftp_connections", d.ftpConnections).coerceIn(1, 4),
            prefetchPages = prefs.getInt("prefetch_pages", d.prefetchPages).coerceIn(2, 12),

            readerFont = enumOf(prefs.getString("reader_font", null), d.readerFont),
            fontSize = prefs.getInt("font_size", d.fontSize).coerceIn(FONT_MIN, FONT_MAX),
            fontWeight = prefs.getInt("font_weight", d.fontWeight).coerceIn(WEIGHT_MIN, WEIGHT_MAX),
            strokeBoost = prefs.getInt("stroke_boost", d.strokeBoost).coerceIn(0, STROKE_MAX),
            lineSpacing = prefs.getInt("line_spacing", d.lineSpacing).coerceIn(LINE_MIN, LINE_MAX),
            letterSpacing = prefs.getInt("letter_spacing", d.letterSpacing).coerceIn(LETTER_MIN, LETTER_MAX),
            indent = prefs.getInt("indent", d.indent).coerceIn(0, INDENT_MAX),
            justify = prefs.getBoolean("justify", d.justify),
            marginTop = prefs.getInt("margin_top", d.marginTop).coerceIn(0, MARGIN_MAX),
            marginBottom = prefs.getInt("margin_bottom", d.marginBottom).coerceIn(0, MARGIN_MAX),
            marginLeft = prefs.getInt("margin_left", if (oldMargin >= 0) oldMargin else d.marginLeft).coerceIn(0, MARGIN_MAX),
            marginRight = prefs.getInt("margin_right", if (oldMargin >= 0) oldMargin else d.marginRight).coerceIn(0, MARGIN_MAX),

            refreshEvery = prefs.getInt("refresh_every", d.refreshEvery).coerceIn(0, 20),
            refreshStyle = enumOf(prefs.getString("refresh_style", null), d.refreshStyle),
            refreshOnScreens = prefs.getBoolean("refresh_screens", d.refreshOnScreens),
            refreshMs = prefs.getInt("refresh_ms", d.refreshMs).coerceIn(40, 400),

            uiWeight = prefs.getInt("ui_weight", d.uiWeight).coerceIn(0, 2),
            uiScale = prefs.getInt("ui_scale", d.uiScale).coerceIn(UI_SCALE_MIN, UI_SCALE_MAX),
            shelfColumns = prefs.getInt("shelf_columns", d.shelfColumns).coerceIn(2, 6),
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
            .putString("reader_font", s.readerFont.name)
            .putInt("font_size", s.fontSize)
            .putInt("font_weight", s.fontWeight)
            .putInt("stroke_boost", s.strokeBoost)
            .putInt("line_spacing", s.lineSpacing)
            .putInt("letter_spacing", s.letterSpacing)
            .putInt("indent", s.indent)
            .putBoolean("justify", s.justify)
            .putInt("margin_top", s.marginTop)
            .putInt("margin_bottom", s.marginBottom)
            .putInt("margin_left", s.marginLeft)
            .putInt("margin_right", s.marginRight)
            .putInt("refresh_every", s.refreshEvery)
            .putString("refresh_style", s.refreshStyle.name)
            .putBoolean("refresh_screens", s.refreshOnScreens)
            .putInt("refresh_ms", s.refreshMs)
            .putInt("ui_weight", s.uiWeight)
            .putInt("ui_scale", s.uiScale)
            .putInt("shelf_columns", s.shelfColumns)
            .putBoolean("info_bar", s.showInfoBar)
            .putBoolean("volume_keys", s.volumeKeys)
            .putBoolean("list_covers", s.listCovers)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val FONT_MIN = 12
        const val FONT_MAX = 40
        const val WEIGHT_MIN = 100
        const val WEIGHT_MAX = 900
        const val STROKE_MAX = 8
        const val LINE_MIN = 10
        const val LINE_MAX = 30
        const val LETTER_MIN = -10
        const val LETTER_MAX = 30
        const val INDENT_MAX = 4
        const val MARGIN_MAX = 80
        const val UI_SCALE_MIN = 85
        const val UI_SCALE_MAX = 140

        /** 새로고침 간격 고르기 (0 = 안 함) */
        val REFRESH_EVERY_OPTIONS = listOf(0, 1, 2, 3, 5, 10)
        /** 깜빡임 시간 고르기 */
        val REFRESH_MS_OPTIONS = listOf(60, 100, 160, 250)

        fun weightName(w: Int): String = when {
            w <= 200 -> "아주 가늘게"
            w <= 300 -> "가늘게"
            w <= 400 -> "보통"
            w <= 500 -> "조금 굵게"
            w <= 600 -> "굵게"
            w <= 700 -> "더 굵게"
            else -> "아주 굵게"
        }
    }
}
