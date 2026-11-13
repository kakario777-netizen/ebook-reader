package com.kakario.ebookreader.data

import android.content.Context
import com.kakario.ebookreader.book.Reflow

/** 만화(ZIP)를 열 때의 보기 방식. 회전도 이 값에 따라 강제로 맞춘다. */
enum class ComicLayout(val label: String, val short: String) {
    DOUBLE_LANDSCAPE("두 장 · 가로 고정", "두 장"),
    SINGLE_PORTRAIT("한 장 · 세로 고정", "한 장"),
}

/** 페이지마다 하는 가벼운 새로고침. */
enum class LightRefresh(val label: String) {
    TEXT("글자 줄만"),
    WHITE("흰 화면"),
    OFF("안 함"),
}

/** 폴더 목록 정렬 기준. */
enum class ListSort(val label: String) {
    NAME("이름"),
    DATE("날짜"),
    SIZE("크기"),
    TYPE("형식"),
}

/** 책장 정렬 방식. */
enum class ShelfSort(val label: String) {
    ADDED("꽂은 순"),
    RECENT("최근 읽은 순"),
    NAME("이름순"),
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
    /** 글자 책 글꼴 (FontCatalog의 id) */
    val readerFont: String = "noto-sans-kr",
    /** 글자 크기 (sp) */
    val fontSize: Int = 20,
    /** 글꼴 굵기 100~900 (400 보통, 700 굵게) */
    val fontWeight: Int = 600,
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
    /** 글자를 순수 검정/흰색으로 또렷하게 (이잉크에서 테두리가 뭉개지고 깨지는 것 방지) */
    val crispText: Boolean = false,
    /** 또렷하게 할 때 진하기 1~5 (클수록 굵고 진함) */
    val crispLevel: Int = 3,
    /** TXT 잘린 문장 다시 잇기 */
    val txtReflow: Reflow.Mode = Reflow.Mode.AUTO,
    /** TXT 보기: false = 기본(문단), true = 문장마다 줄바꿈 */
    val sentencePerLine: Boolean = false,
    /** TXT 장 제목(제1장·프롤로그 등) 자동으로 찾아 크게 */
    val txtChapters: Boolean = true,
    /** 여백 (dp) */
    val marginTop: Int = 28,
    val marginBottom: Int = 20,
    val marginLeft: Int = 22,
    val marginRight: Int = 22,

    // ── 화면 새로고침 (이잉크 잔상 지우기)
    /** 페이지마다: 바뀐 글자 줄만 짧게 검게 칠했다 지우기 */
    val lightRefresh: LightRefresh = LightRefresh.TEXT,
    /** 가벼운 새로고침 시간 (ms) */
    val lightMs: Int = 70,
    /** 몇 페이지마다 화면 전체를 진하게 새로고침할지 (0 = 안 함) */
    val fullEvery: Int = 5,
    /** 전체 새로고침 때 검게 칠해 두는 시간 (ms) */
    val fullMs: Int = 300,
    /** 기기 자체 새로고침도 함께 부르기 (지원하는 이잉크 기기만) */
    val deviceRefresh: Boolean = true,
    /** 목록·화면이 바뀔 때도 새로고침 */
    val refreshOnScreens: Boolean = true,

    // ── 화면 전체
    /** 앱 글자 굵기 단계 (0 보통, 1 조금 굵게, 2 굵게, 3 아주 굵게) */
    val uiWeight: Int = 2,
    /** 앱 글자 크기 % */
    val uiScale: Int = 105,
    /** 책장 한 줄 권수 */
    val shelfColumns: Int = 6,
    /** 책장 정렬 */
    val shelfSort: ShelfSort = ShelfSort.ADDED,
    /** 만화: 두 번 톡 쳐서 확대 */
    val doubleTapZoom: Boolean = false,
    /** 보는 중 화면 아래에 시간·진행률 표시 */
    val showInfoBar: Boolean = true,
    /** 볼륨 버튼으로 넘기기 (페이지 터너 키는 항상 동작) */
    val volumeKeys: Boolean = true,
    /** 목록에 표지 작게 보여주기 (끄면 글자만) */
    val listCovers: Boolean = true,
    /** 폴더 목록을 표지 격자로 */
    val listGrid: Boolean = true,
    /** 격자 한 줄 칸 수 */
    val listColumns: Int = 5,
    /** 폴더 목록 정렬 기준 */
    val listSort: ListSort = ListSort.NAME,
    /** 거꾸로 (내림차순) */
    val listDesc: Boolean = false,
    /** 폴더를 위에 (false면 아래) */
    val foldersTop: Boolean = true,
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

            readerFont = prefs.getString("reader_font_id", null) ?: when (prefs.getString("reader_font2", null)) {
                "SERIF" -> "nanum-myeongjo"
                "SYSTEM" -> "system"
                else -> d.readerFont
            },
            fontSize = prefs.getInt("font_size", d.fontSize).coerceIn(FONT_MIN, FONT_MAX),
            fontWeight = prefs.getInt("font_weight2", d.fontWeight).coerceIn(WEIGHT_MIN, WEIGHT_MAX),
            strokeBoost = prefs.getInt("stroke_boost", d.strokeBoost).coerceIn(0, STROKE_MAX),
            lineSpacing = prefs.getInt("line_spacing", d.lineSpacing).coerceIn(LINE_MIN, LINE_MAX),
            letterSpacing = prefs.getInt("letter_spacing", d.letterSpacing).coerceIn(LETTER_MIN, LETTER_MAX),
            indent = prefs.getInt("indent", d.indent).coerceIn(0, INDENT_MAX),
            justify = prefs.getBoolean("justify", d.justify),
            txtReflow = enumOf(prefs.getString("txt_reflow", null), d.txtReflow),
            sentencePerLine = prefs.getBoolean("sentence_per_line", d.sentencePerLine),
            crispText = prefs.getBoolean("crisp_text2", d.crispText),
            crispLevel = prefs.getInt("crisp_level", d.crispLevel).coerceIn(1, 5),
            txtChapters = prefs.getBoolean("txt_chapters", d.txtChapters),
            marginTop = prefs.getInt("margin_top", d.marginTop).coerceIn(0, MARGIN_MAX),
            marginBottom = prefs.getInt("margin_bottom", d.marginBottom).coerceIn(0, MARGIN_MAX),
            marginLeft = prefs.getInt("margin_left", if (oldMargin >= 0) oldMargin else d.marginLeft).coerceIn(0, MARGIN_MAX),
            marginRight = prefs.getInt("margin_right", if (oldMargin >= 0) oldMargin else d.marginRight).coerceIn(0, MARGIN_MAX),

            lightRefresh = enumOf(prefs.getString("light_refresh", null), d.lightRefresh),
            lightMs = prefs.getInt("light_ms", d.lightMs).coerceIn(30, 300),
            fullEvery = prefs.getInt("full_every", d.fullEvery).coerceIn(0, 50),
            fullMs = prefs.getInt("full_ms", d.fullMs).coerceIn(100, 1000),
            deviceRefresh = prefs.getBoolean("device_refresh", d.deviceRefresh),
            refreshOnScreens = prefs.getBoolean("refresh_screens", d.refreshOnScreens),

            uiWeight = prefs.getInt("ui_weight2", d.uiWeight).coerceIn(0, 3),
            uiScale = prefs.getInt("ui_scale", d.uiScale).coerceIn(UI_SCALE_MIN, UI_SCALE_MAX),
            shelfColumns = prefs.getInt("shelf_columns2", d.shelfColumns).coerceIn(SHELF_MIN, SHELF_MAX),
            showInfoBar = prefs.getBoolean("info_bar", d.showInfoBar),
            shelfSort = enumOf(prefs.getString("shelf_sort", null), d.shelfSort),
            doubleTapZoom = prefs.getBoolean("double_tap_zoom", d.doubleTapZoom),
            volumeKeys = prefs.getBoolean("volume_keys", d.volumeKeys),
            listCovers = prefs.getBoolean("list_covers", d.listCovers),
            listGrid = prefs.getBoolean("list_grid", d.listGrid),
            listColumns = prefs.getInt("list_columns", d.listColumns).coerceIn(SHELF_MIN, SHELF_MAX),
            listSort = enumOf(prefs.getString("list_sort", null), d.listSort),
            listDesc = prefs.getBoolean("list_desc", d.listDesc),
            foldersTop = prefs.getBoolean("folders_top", d.foldersTop),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("comic_layout", s.comicLayout.name)
            .putBoolean("cover_alone", s.coverAlone)
            .putBoolean("slide_animation", s.slideAnimation)
            .putInt("ftp_connections", s.ftpConnections)
            .putInt("prefetch_pages", s.prefetchPages)
            .putString("reader_font_id", s.readerFont)
            .putInt("font_size", s.fontSize)
            .putInt("font_weight2", s.fontWeight)
            .putInt("stroke_boost", s.strokeBoost)
            .putInt("line_spacing", s.lineSpacing)
            .putInt("letter_spacing", s.letterSpacing)
            .putInt("indent", s.indent)
            .putBoolean("justify", s.justify)
            .putString("txt_reflow", s.txtReflow.name)
            .putBoolean("sentence_per_line", s.sentencePerLine)
            .putBoolean("crisp_text2", s.crispText)
            .putInt("crisp_level", s.crispLevel)
            .putBoolean("txt_chapters", s.txtChapters)
            .putInt("margin_top", s.marginTop)
            .putInt("margin_bottom", s.marginBottom)
            .putInt("margin_left", s.marginLeft)
            .putInt("margin_right", s.marginRight)
            .putString("light_refresh", s.lightRefresh.name)
            .putInt("light_ms", s.lightMs)
            .putInt("full_every", s.fullEvery)
            .putInt("full_ms", s.fullMs)
            .putBoolean("device_refresh", s.deviceRefresh)
            .putBoolean("refresh_screens", s.refreshOnScreens)
            .putInt("ui_weight2", s.uiWeight)
            .putInt("ui_scale", s.uiScale)
            .putInt("shelf_columns2", s.shelfColumns)
            .putBoolean("info_bar", s.showInfoBar)
            .putString("shelf_sort", s.shelfSort.name)
            .putBoolean("double_tap_zoom", s.doubleTapZoom)
            .putBoolean("volume_keys", s.volumeKeys)
            .putBoolean("list_covers", s.listCovers)
            .putBoolean("list_grid", s.listGrid)
            .putInt("list_columns", s.listColumns)
            .putString("list_sort", s.listSort.name)
            .putBoolean("list_desc", s.listDesc)
            .putBoolean("folders_top", s.foldersTop)
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
        const val SHELF_MIN = 3
        const val SHELF_MAX = 10
        const val UI_SCALE_MIN = 85
        const val UI_SCALE_MAX = 140

        /** 전체 새로고침 간격 고르기 (0 = 안 함) */
        val FULL_EVERY_OPTIONS = listOf(0, 3, 5, 8, 10, 20)
        /** 전체 새로고침 검은 시간 */
        val FULL_MS_OPTIONS = listOf(200, 300, 450, 600)
        /** 가벼운 새로고침 시간 */
        val LIGHT_MS_OPTIONS = listOf(40, 70, 100, 150)

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
