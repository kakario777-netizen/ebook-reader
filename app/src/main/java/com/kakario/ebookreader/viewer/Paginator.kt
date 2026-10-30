package com.kakario.ebookreader.viewer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import com.kakario.ebookreader.book.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 페이지 하나 = 구간 글의 몇 번째 줄부터 몇 번째 줄까지. */
class PageSpan(
    val firstLine: Int,
    val lastLine: Int,
    /** 구간 안에서 이 페이지가 시작하는 글자 위치 */
    val startOffset: Int,
    /** 줄 위치(px): 이 페이지 맨 위 / 맨 아래 */
    val top: Float,
    val bottom: Float,
)

/** 구간 하나를 화면 크기로 나눈 결과. */
sealed interface SectionPages {
    val count: Int

    /** 구간 안 글자 위치가 들어 있는 페이지 번호. */
    fun pageFor(localOffset: Int): Int

    fun startOffset(page: Int): Int

    class Text(val layout: TextLayoutResult, val pages: List<PageSpan>) : SectionPages {
        override val count: Int get() = pages.size
        override fun pageFor(localOffset: Int): Int {
            var result = 0
            for (i in pages.indices) {
                if (pages[i].startOffset <= localOffset) result = i else break
            }
            return result
        }
        override fun startOffset(page: Int): Int = pages.getOrNull(page)?.startOffset ?: 0
    }

    class Image(val path: String) : SectionPages {
        override val count: Int get() = 1
        override fun pageFor(localOffset: Int): Int = 0
        override fun startOffset(page: Int): Int = 0
    }
}

/**
 * 글자 책을 지금 화면 크기·글자 크기에 맞춰 페이지로 나눈다.
 * 구간마다 필요할 때 한 번만 재고(최근 몇 개만 기억), 나머지는 건드리지 않아 큰 책도 가볍다.
 * 화면 크기나 글자 설정이 바뀌면 새로 만든다.
 */
class Paginator(
    private val content: TextContent,
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val widthPx: Int,
    private val heightPx: Int,
) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Int, SectionPages>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, SectionPages>?): Boolean = size > 6
    }

    val sectionCount: Int get() = content.sections.size

    @Synchronized
    fun cached(index: Int): SectionPages? = cache[index]

    suspend fun get(index: Int): SectionPages {
        cached(index)?.let { return it }
        return mutex.withLock {
            cached(index) ?: withContext(Dispatchers.Default) { measure(index) }.also { put(index, it) }
        }
    }

    @Synchronized
    private fun put(index: Int, pages: SectionPages) {
        cache[index] = pages
    }

    private fun measure(index: Int): SectionPages {
        val section = content.sections[index]
        if (section is TextContent.ImageSection) return SectionPages.Image(section.path)
        section as TextContent.TextSection

        val text: AnnotatedString = buildAnnotatedString {
            append(section.text)
            val base = if (style.fontSize.isSpecified) style.fontSize else 18.sp
            section.headings.forEach { h ->
                val r = h.range
                if (r.last >= section.text.length) return@forEach
                when (h.level) {
                    // 장 제목: 크게, 아주 굵게, 가운데
                    1 -> {
                        addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = base * 1.6f), r.first, r.last + 1)
                        addStyle(
                            ParagraphStyle(textAlign = TextAlign.Center, textIndent = TextIndent.None, lineHeight = 1.4.em),
                            r.first, r.last + 1,
                        )
                    }
                    // 부제목: 조금 크게, 가운데
                    3 -> {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = base * 1.2f), r.first, r.last + 1)
                        addStyle(
                            ParagraphStyle(textAlign = TextAlign.Center, textIndent = TextIndent.None, lineHeight = 1.5.em),
                            r.first, r.last + 1,
                        )
                    }
                    // 작은 제목: 굵게
                    else -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = base * 1.15f), r.first, r.last + 1)
                }
            }
        }
        val layout = measurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
            skipCache = true,
        )

        val pages = ArrayList<PageSpan>()
        val lineCount = layout.lineCount
        var first = 0
        while (first < lineCount) {
            // 페이지 맨 위가 빈 줄로 시작하지 않게 건너뛴다 (구간 첫 줄 제외)
            while (first > 0 && first < lineCount && isBlank(layout, section.text, first)) first++
            if (first >= lineCount) break
            val top = layout.getLineTop(first)
            var last = first
            while (last + 1 < lineCount && layout.getLineBottom(last + 1) - top <= heightPx) last++
            pages += PageSpan(first, last, layout.getLineStart(first), top, layout.getLineBottom(last))
            first = last + 1
        }
        if (pages.isEmpty()) pages += PageSpan(0, 0, 0, 0f, 0f)
        return SectionPages.Text(layout, pages)
    }

    private fun isBlank(layout: TextLayoutResult, text: String, line: Int): Boolean {
        val s = layout.getLineStart(line)
        val e = layout.getLineEnd(line)
        for (i in s until minOf(e, text.length)) if (!text[i].isWhitespace()) return false
        return true
    }
}
