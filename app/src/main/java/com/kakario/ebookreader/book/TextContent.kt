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
    /** 찾아낸 장 제목들 (목차) */
    val toc: List<TocEntry> = emptyList(),
    private val images: (String) -> ByteArray?,
    private val onClose: () -> Unit = {},
) {
    sealed interface Section {
        /** 책 전체에서 이 구간이 시작하는 글자 위치 */
        val start: Int
        val length: Int
    }

    /** 제목 줄: 구간 안 글자 범위와 크기(1 = 장 제목 크게, 2 = 작은 제목, 3 = 부제목). */
    class Heading(val range: IntRange, val level: Int)

    /** 목차 한 줄: 제목과 책 처음부터 센 글자 위치. */
    class TocEntry(val title: String, val position: Int)

    /** 글자 구간: 문단들을 줄바꿈으로 이은 글. */
    class TextSection(
        override val start: Int,
        val text: String,
        val headings: List<Heading>,
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

    /** 글자 위치가 속한 장 (목차 번호, 없으면 -1). */
    fun tocIndexAt(position: Int): Int = toc.indexOfLast { it.position <= position }

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
            val heads = ArrayList<Heading>()
            val toc = ArrayList<TocEntry>()
            var afterTitle = false

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

            fun appendLine(text: String) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(text)
            }

            fun addPara(text: String, level: Int, sub: Boolean) {
                if (level == 1 && !sub && text.isNotEmpty()) {
                    // 장 제목: 새 페이지 맨 위에서 시작
                    flushText()
                    toc += TocEntry(text, pos)
                    appendLine(text)
                    heads += Heading((sb.length - text.length) until sb.length, 1)
                    afterTitle = true
                    return
                }
                if (sub && afterTitle && text.isNotEmpty()) {
                    // 부제목: 장 제목 바로 아래, 목차에도 붙인다
                    appendLine(text)
                    heads += Heading((sb.length - text.length) until sb.length, 3)
                    toc.lastOrNull()?.let { last -> toc[toc.size - 1] = TocEntry(last.title + "  " + text, last.position) }
                    return
                }
                if (afterTitle) {
                    // 제목과 본문 사이 한 줄 띄움
                    afterTitle = false
                    if (text.isNotEmpty()) appendLine("")
                }
                if (sb.isEmpty() && text.isEmpty()) return // 구간 첫 줄은 빈 줄로 시작하지 않음
                appendLine(text)
                if (level > 0 && text.isNotEmpty()) heads += Heading((sb.length - text.length) until sb.length, if (level == 4) 4 else 2)
                if (sb.length >= CHUNK) flushText()
            }

            for (chapter in chapters) {
                for (b in chapter) {
                    when (b) {
                        is Html.Block.Para -> {
                            if (b.text.length <= LONG_PARA) {
                                addPara(b.text, b.level, b.sub)
                            } else {
                                splitLong(b.text).forEach { addPara(it, b.level, false) }
                            }
                        }
                        is Html.Block.Image -> {
                            afterTitle = false
                            flushText()
                            sections += ImageSection(pos, b.path)
                            pos += 2
                        }
                    }
                }
                flushText()
            }
            return TextContent(title, sections, toc, images, onClose)
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

        /** 정리 방식이 바뀌면 올려서 예전 정리본을 안 쓰게 한다. */
        const val PREPARED_VERSION = 1

        /** 정리 설정을 짧은 글자로 (정리본 이름에 붙임). */
        fun prepCode(reflow: Reflow.Mode, chapters: Boolean, sentencePerLine: Boolean): String =
            "v$PREPARED_VERSION${reflow.name.first()}${if (chapters) 1 else 0}${if (sentencePerLine) 1 else 0}"

        /** 정리된 TXT: 문단(제목 표시 포함)들 + 문장을 이었는지. */
        class Prepared(val blocks: List<Html.Block>, val reflowed: Boolean)

        /**
         * TXT 원본 → 정리본. 오래 걸리는 부분이라 단계마다 알려준다 (stage 글, 0~1 진행).
         */
        fun prepareTxt(
            file: File,
            reflow: Reflow.Mode,
            chapters: Boolean,
            sentencePerLine: Boolean,
            report: (String, Float) -> Unit = { _, _ -> },
        ): Prepared {
            report("글자 읽는 중", 0.02f)
            val lines = TextDecode.readLines(file)
            report("줄 정리하는 중", 0.06f)
            val result = Reflow.process(lines, reflow) { report("줄 정리하는 중", 0.06f + 0.6f * it) }
            lines.clear()
            val paras0 = result.paragraphs.map { if (result.reflowed) it else it.trimEnd() }
            report("문장 나누는 중", 0.68f)
            val paras = if (!sentencePerLine) paras0 else paras0.flatMap { p ->
                val t = p.trim()
                if (t.isEmpty() || t.length < 25 || Chapters.looksLikeTitle(t) || Chapters.isSceneBreak(t)) listOf(p)
                else Reflow.splitSentences(t)
            }
            report("장 제목 찾는 중", 0.74f)
            val blocks = if (chapters) Chapters.mark(paras) else paras.map { Html.Block.Para(it) }
            report("문단 다듬는 중", 0.86f)
            return Prepared(Html.tidy(blocks), result.reflowed)
        }

        /** 정리본 → 책 내용 (빠름). */
        fun fromPrepared(title: String, p: Prepared): TextContent =
            build(title, listOf(p.blocks), images = { null }).also { it.reflowed = p.reflowed }

        /** 정리본을 압축 파일 바이트로. 한 줄에 문단 하나: "<크기><부제목 s/->\t<글>" */
        fun encodePrepared(p: Prepared): ByteArray {
            val bos = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("EBTXTP$PREPARED_VERSION\t${if (p.reflowed) 1 else 0}\n")
                for (b in p.blocks) {
                    if (b !is Html.Block.Para) continue
                    w.write(b.level.toString())
                    w.write(if (b.sub) "s" else "-")
                    w.write("\t")
                    w.write(b.text.replace("\\", "\\\\").replace("\n", "\\n"))
                    w.write("\n")
                }
            }
            return bos.toByteArray()
        }

        fun decodePrepared(bytes: ByteArray): Prepared? = try {
            java.util.zip.GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { r ->
                val head = r.readLine() ?: return null
                val hp = head.split('\t')
                if (hp[0] != "EBTXTP$PREPARED_VERSION") return null
                val reflowed = hp.getOrNull(1) == "1"
                val out = ArrayList<Html.Block>()
                while (true) {
                    val line = r.readLine() ?: break
                    val tab = line.indexOf('\t')
                    if (tab < 2) continue
                    val level = line.substring(0, tab - 1).toIntOrNull() ?: 0
                    val sub = line[tab - 1] == 's'
                    val raw = line.substring(tab + 1)
                    val text = if (raw.indexOf('\\') < 0) raw else buildString {
                        var i = 0
                        while (i < raw.length) {
                            val c = raw[i]
                            if (c == '\\' && i + 1 < raw.length) {
                                append(if (raw[i + 1] == 'n') '\n' else raw[i + 1])
                                i += 2
                            } else {
                                append(c)
                                i++
                            }
                        }
                    }
                    out += Html.Block.Para(text, level, sub)
                }
                Prepared(out, reflowed)
            }
        } catch (e: Exception) {
            null
        }

        /** TXT 파일 → 내용. 줄 하나가 문단 하나 (잘린 문장은 reflow 설정에 따라 다시 잇는다). */
        fun loadTxt(
            file: File,
            reflow: Reflow.Mode = Reflow.Mode.AUTO,
            chapters: Boolean = true,
            sentencePerLine: Boolean = false,
        ): TextContent {
            val text = TextDecode.decodeText(file.readBytes())
            val result = Reflow.process(text.split('\n'), reflow)
            val paras0 = result.paragraphs.map { if (result.reflowed) it else it.trimEnd() }
            // '문장마다 줄바꿈': 제목·장면 전환은 그대로, 본문 문단만 문장마다 나눈다
            val paras = if (!sentencePerLine) paras0 else paras0.flatMap { p ->
                val t = p.trim()
                if (t.isEmpty() || t.length < 25 || Chapters.looksLikeTitle(t) || Chapters.isSceneBreak(t)) listOf(p)
                else Reflow.splitSentences(t)
            }
            val blocks = if (chapters) Chapters.mark(paras) else paras.map { Html.Block.Para(it) }
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
