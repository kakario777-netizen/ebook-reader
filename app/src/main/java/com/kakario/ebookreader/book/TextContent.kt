package com.kakario.ebookreader.book

import java.io.File

/**
 * 글자 책(TXT·EPUB) 한 권의 내용.
 * 책 전체를 '구간(section)'으로 나눠 둔다 — 화면에 맞춰 페이지를 나누는 일은
 * 지금 읽는 구간과 그 앞뒤만 하므로 큰 책도 바로 열린다.
 *
 * 위치는 책 처음부터 센 '글자 위치' 하나로 기억한다.
 * 글자 크기나 화면 방향이 바뀌어 페이지가 다시 나뉘어도 같은 문장에서 이어진다.
 */
class TextContent(
    val title: String?,
    val sections: List<Section>,
    private val images: (String) -> ByteArray?,
    private val onClose: () -> Unit = {},
) {
    sealed interface Section {
        /** 책 전체에서 이 구간이 시작하는 글자 위치 */
        val start: Int
        val length: Int
    }

    /** 글자 구간: 문단들을 줄바꿈으로 이은 글. heading은 제목 줄의 (시작, 끝) 목록. */
    class TextSection(
        override val start: Int,
        val text: String,
        val headings: List<IntRange>,
    ) : Section {
        override val length: Int get() = text.length
    }

    /** 그림 한 장 (한 페이지). */
    class ImageSection(override val start: Int, val path: String) : Section {
        override val length: Int get() = 1
    }

    val totalChars: Int =
        sections.lastOrNull()?.let { it.start + it.length } ?: 0

    /** 글자 위치가 들어 있는 구간 번호. */
    fun sectionAt(position: Int): Int {
        if (sections.isEmpty()) return 0
        var lo = 0
        var hi = sections.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (sections[mid].start <= position) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun imageBytes(path: String): ByteArray? = images(path)

    fun close() = onClose()

    /** TXT에서 잘린 문장을 다시 이었는지 (메뉴에 표시). */
    var reflowed: Boolean = false

    companion object {
        /** 한 구간의 대략적인 크기 (글자 수). 이보다 커지면 문단 경계에서 나눈다. */
        private const val CHUNK = 10_000
        /** 줄바꿈 없이 아주 긴 문단은 이 길이쯤에서 자른다. */
        private const val LONG_PARA = 6_000

        /** 문서(장) 단위 블록 목록들로 구간을 만든다. 장마다 새 구간으로 시작. */
        fun build(
            title: String?,
            chapters: List<List<Html.Block>>,
            images: (String) -> ByteArray?,
            onClose: () -> Unit = {},
        ): TextContent {
            val sections = ArrayList<Section>()
            var pos = 0
            val sb = StringBuilder()
            val heads = ArrayList<IntRange>()

            fun flushText() {
                // 구간 끝의 빈 줄은 버린다
                while (sb.endsWith("\n")) sb.setLength(sb.length - 1)
                if (sb.isNotEmpty()) {
                    sections += TextSection(pos, sb.toString(), heads.toList())
                    pos += sb.length + 1
                }
                sb.setLength(0)
                heads.clear()
            }

            fun addPara(text: String, heading: Boolean) {
                if (sb.isEmpty() && text.isEmpty()) return // 구간 첫 줄은 빈 줄로 시작하지 않음
                if (sb.isNotEmpty()) sb.append('\n')
                val from = sb.length
                sb.append(text)
                if (heading && text.isNotEmpty()) heads += from until sb.length
                if (sb.length >= CHUNK) flushText()
            }

            for (chapter in chapters) {
                for (b in chapter) {
                    when (b) {
                        is Html.Block.Para -> {
                            if (b.text.length <= LONG_PARA) {
                                addPara(b.text, b.heading)
                            } else {
                                splitLong(b.text).forEach { addPara(it, b.heading) }
                            }
                        }
                        is Html.Block.Image -> {
                            flushText()
                            sections += ImageSection(pos, b.path)
                            pos += 2
                        }
                    }
                }
                flushText()
            }
            return TextContent(title, sections, images, onClose)
        }

        private fun splitLong(text: String): List<String> {
            val out = ArrayList<String>()
            var from = 0
            while (from < text.length) {
                var to = minOf(text.length, from + LONG_PARA)
                if (to < text.length) {
                    val space = text.lastIndexOf(' ', to)
                    if (space > from + LONG_PARA / 2) to = space + 1
                }
                out += text.substring(from, to).trimEnd()
                from = to
            }
            return out
        }

        /** TXT 파일 → 내용. 줄 하나가 문단 하나 (잘린 문장은 reflow 설정에 따라 다시 잇는다). */
        fun loadTxt(file: File, reflow: Reflow.Mode = Reflow.Mode.AUTO): TextContent {
            val text = TextDecode.decodeText(file.readBytes())
            val result = Reflow.process(text.split('\n'), reflow)
            val blocks = result.paragraphs.map { Html.Block.Para(if (result.reflowed) it else it.trimEnd()) }
            val tidy = Html.tidy(blocks)
            return build(file.nameWithoutExtension, listOf(tidy), images = { null }).also { it.reflowed = result.reflowed }
        }

        /** EPUB 파일 → 내용. 그림은 열 때마다 파일에서 꺼낸다 (닫을 때 파일도 닫음). */
        fun loadEpub(file: File): TextContent? {
            val src = FileRange(file)
            val book = try {
                Epub.open(src)
            } catch (e: Exception) {
                null
            }
            if (book == null || book.spine.isEmpty()) {
                src.close()
                return null
            }
            val chapters = book.spine.map { path ->
                val html = Epub.text(src, book.entries, path) ?: return@map emptyList<Html.Block>()
                Html.spaced(Html.parse(html, path.substringBeforeLast('/', "")))
            }
            return build(
                title = book.title,
                chapters = chapters,
                images = { p -> Epub.bytes(src, book.entries, p) },
                onClose = { src.close() },
            )
        }
    }
}
