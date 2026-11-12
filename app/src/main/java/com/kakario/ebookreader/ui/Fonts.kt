package com.kakario.ebookreader.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 고를 수 있는 글꼴 하나. files: 굵기 → 파일 이름 (굵기 0 = 굵기를 마음대로 바꿀 수 있는 가변 글꼴). */
class FontInfo(
    val id: String,
    val name: String,
    val kind: String,
    val note: String,
    val files: Map<Int, String>,
    /** 앱 안에 들어 있는 글꼴 (받을 필요 없음) */
    val builtIn: Boolean = false,
)

/**
 * 글꼴 목록과 받기.
 *  - 기본 2개(노토 산스, 나눔명조)는 앱 안에 들어 있다.
 *  - 나머지는 무료(OFL) 한글 글꼴로, 고르면 저장소의 'fonts' 릴리스에서 받아 폰에 저장한다.
 *    (앱을 가볍게 유지하고, 업데이트할 때마다 글꼴까지 다시 받지 않도록)
 */
object AppFonts {
    private const val BASE = "https://github.com/kakario777-netizen/ebook-reader/releases/download/fonts/"

    val catalog: List<FontInfo> = listOf(
        FontInfo("noto-sans-kr", "노토 산스 (기본)", "고딕", "또렷하고 고른 고딕, 굵기 자유", mapOf(0 to "gothic.ttf"), builtIn = true),
        FontInfo("nanum-myeongjo", "나눔명조", "명조", "종이책 느낌의 명조", mapOf(400 to "serif-400.ttf", 700 to "serif-700.ttf", 800 to "serif-800.ttf"), builtIn = true),
        FontInfo("nanum-gothic", "나눔고딕", "고딕", "가장 익숙한 화면용 고딕", mapOf(400 to "nanumgothic-400.ttf", 700 to "nanumgothic-700.ttf", 800 to "nanumgothic-800.ttf")),
        FontInfo("pretendard", "프리텐다드", "고딕", "깔끔한 요즘 고딕, 굵기 자유", mapOf(0 to "pretendard-var.ttf")),
        FontInfo("ibm-plex-sans-kr", "IBM 플렉스 산스", "고딕", "단정하고 읽기 편한 고딕", mapOf(400 to "ibmplexsanskr-400.ttf", 500 to "ibmplexsanskr-500.ttf", 600 to "ibmplexsanskr-600.ttf", 700 to "ibmplexsanskr-700.ttf")),
        FontInfo("gothic-a1", "고딕 A1", "고딕", "또렷한 본문용 고딕", mapOf(400 to "gothica1-400.ttf", 500 to "gothica1-500.ttf", 600 to "gothica1-600.ttf", 700 to "gothica1-700.ttf")),
        FontInfo("gowun-dodum", "고운돋움", "고딕", "부드럽고 둥근 느낌", mapOf(400 to "gowundodum-400.ttf")),
        FontInfo("gowun-batang", "고운바탕", "명조", "단정한 책 본문용 바탕", mapOf(400 to "gowunbatang-400.ttf", 700 to "gowunbatang-700.ttf")),
        FontInfo("noto-serif-kr", "노토 세리프", "명조", "또렷한 명조, 굵기 자유 (용량 큼)", mapOf(0 to "notoserifkr-var.ttf")),
        FontInfo("hahmlet", "함렛", "명조", "현대적인 명조, 굵기 자유", mapOf(0 to "hahmlet-var.ttf")),
        FontInfo("song-myung", "송명", "명조", "옛 활자 느낌의 명조", mapOf(400 to "songmyung-400.ttf")),
        FontInfo("system", "기기 글꼴", "기기", "기기에 들어 있는 기본 글꼴", emptyMap(), builtIn = true),
    )

    /** 앱 화면(목록·메뉴)에 쓰는 글꼴 */
    var gothic: FontFamily = FontFamily.Default
        private set

    /** 글꼴을 새로 받으면 올라가는 번호 — 목록·책이 다시 그려지게 하는 신호 */
    var version by mutableIntStateOf(0)
        private set

    private lateinit var appContext: Context
    private val families = HashMap<String, FontFamily>()
    private val dir: File get() = File(appContext.filesDir, "fonts").apply { mkdirs() }

    fun init(context: Context) {
        appContext = context.applicationContext
        families.clear()
        family("noto-sans-kr")?.let { gothic = it }
    }

    fun info(id: String): FontInfo = catalog.firstOrNull { it.id == id } ?: catalog.first()

    fun isReady(id: String): Boolean {
        val f = info(id)
        if (f.builtIn) return true
        return f.files.values.all { File(dir, it).length() > 0 }
    }

    /** 글꼴을 쓸 수 있으면 FontFamily, 아직 안 받았으면 null. */
    fun family(id: String): FontFamily? {
        families[id]?.let { return it }
        val f = info(id)
        if (f.id == "system") return FontFamily.Default
        val fonts = runCatching {
            if (f.builtIn) {
                val assets = appContext.assets
                val have = assets.list("fonts")?.toSet().orEmpty()
                if (f.files.values.any { it !in have }) return null
                f.files.flatMap { (w, file) ->
                    if (w == 0) (1..9).map { i -> Font("fonts/$file", assets, FontWeight(i * 100)) }
                    else listOf(Font("fonts/$file", assets, FontWeight(w)))
                }
            } else {
                if (!isReady(id)) return null
                f.files.flatMap { (w, file) ->
                    val path = File(dir, file)
                    if (w == 0) (1..9).map { i -> Font(path, FontWeight(i * 100)) }
                    else listOf(Font(path, FontWeight(w)))
                }
            }
        }.getOrNull() ?: return null
        return FontFamily(fonts).also { families[id] = it }
    }

    /** 책에 쓸 글꼴 (아직 안 받은 글꼴이면 기본 글꼴). */
    fun forReader(id: String): FontFamily = family(id) ?: gothic

    /** 글꼴 받기. onProgress(받은 바이트). */
    suspend fun download(id: String, onProgress: (Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        val f = info(id)
        var total = 0L
        for (file in f.files.values) {
            val dest = File(dir, file)
            if (dest.length() > 0) continue
            val tmp = File(dir, "$file.part")
            val before = total
            com.kakario.ebookreader.update.Downloader.fetch(BASE + file, tmp, -1) { done, _ -> onProgress(before + done) }
            total = before + tmp.length()
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                error("저장하지 못했어요")
            }
        }
        families.remove(id)
        withContext(Dispatchers.Main) { version++ }
    }

    fun delete(id: String) {
        val f = info(id)
        if (f.builtIn) return
        f.files.values.forEach { File(dir, it).delete() }
        families.remove(id)
        version++
    }

    /** 받은 글꼴 파일 크기 합 */
    fun downloadedBytes(id: String): Long = info(id).files.values.sumOf { File(dir, it).length() }
}
