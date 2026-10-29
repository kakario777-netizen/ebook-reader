package com.kakario.ebookreader.book

/**
 * EPUB 안의 XHTML을 '문단 / 그림' 목록으로 바꾸는 가벼운 해석기.
 * 엄격한 XML 해석기는 &nbsp; 같은 표기나 조금 틀린 문서에서 멈추기 때문에
 * 태그를 직접 훑어서 글자만 뽑는다.
 */
object Html {

    sealed interface Block {
        /** 문단 하나. text가 빈 문자열이면 빈 줄. */
        class Para(val text: String, val heading: Boolean = false) : Block
        /** 그림 하나 (ZIP 안 경로) — 한 페이지를 통째로 쓴다. */
        class Image(val path: String) : Block
    }

    private val BREAK_TAGS = setOf(
        "p", "div", "li", "blockquote", "section", "article", "tr", "dd", "dt", "pre",
        "figure", "figcaption", "table", "ul", "ol", "header", "footer", "aside", "nav", "hr",
    )
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val SKIP_TAGS = setOf("head", "script", "style", "rt", "rp")

    fun parse(html: String, baseDir: String): List<Block> {
        val out = ArrayList<Block>()
        val sb = StringBuilder()
        var heading = false
        var skipUntil: String? = null
        var bodyStarted = !html.contains("<body", ignoreCase = true)

        fun flush(blankIfEmpty: Boolean = false) {
            val t = collapse(sb)
            sb.setLength(0)
            if (t.isNotEmpty()) out += Block.Para(t, heading)
            else if (blankIfEmpty) out += Block.Para("")
            heading = false
        }

        var i = 0
        val n = html.length
        while (i < n) {
            if (html[i] == '<') {
                if (html.startsWith("<!--", i)) {
                    val end = html.indexOf("-->", i + 4)
                    i = if (end < 0) n else end + 3
                    continue
                }
                val end = html.indexOf('>', i)
                if (end < 0) break
                val raw = html.substring(i + 1, end)
                i = end + 1
                if (raw.startsWith("!") || raw.startsWith("?")) continue
                val closing = raw.startsWith("/")
                val selfClosing = raw.endsWith("/")
                val inner = raw.removePrefix("/").removeSuffix("/")
                val name = inner.takeWhile { !it.isWhitespace() }.lowercase().substringAfter(':')

                if (skipUntil != null) {
                    if (closing && name == skipUntil) skipUntil = null
                    continue
                }
                if (!bodyStarted) {
                    if (!closing && name == "body") bodyStarted = true
                    continue
                }
                when {
                    name in SKIP_TAGS -> if (!closing && !selfClosing) skipUntil = name
                    name == "br" -> flush(blankIfEmpty = true)
                    name in BREAK_TAGS -> flush()
                    name in HEADINGS -> {
                        flush()
                        if (!closing) heading = true
                    }
                    name == "img" && !closing -> {
                        flush()
                        Epub.attrs(inner)["src"]?.let { out += Block.Image(Epub.resolve(baseDir, it)) }
                    }
                    name == "image" && !closing -> {
                        // SVG 안의 그림 (<image xlink:href="...">) — 표지에서 자주 쓴다
                        flush()
                        Epub.attrs(inner)["href"]?.let { out += Block.Image(Epub.resolve(baseDir, it)) }
                    }
                }
                continue
            }
            val next = html.indexOf('<', i).let { if (it < 0) n else it }
            if (skipUntil == null && bodyStarted) sb.append(decodeEntities(html.substring(i, next)))
            i = next
        }
        flush()
        return tidy(out)
    }

    /** 빈 줄이 여러 개 이어지면 하나로, 처음·끝의 빈 줄은 없앤다. */
    fun tidy(blocks: List<Block>): List<Block> {
        val out = ArrayList<Block>(blocks.size)
        for (b in blocks) {
            val blank = b is Block.Para && b.text.isEmpty()
            if (blank) {
                val last = out.lastOrNull()
                if (last == null || (last is Block.Para && last.text.isEmpty())) continue
            }
            out += b
        }
        while (true) {
            val last = out.lastOrNull()
            if (last is Block.Para && last.text.isEmpty()) out.removeAt(out.size - 1) else break
        }
        return out
    }

    /** 문단 사이가 붙어 있는 책은 읽기 편하게 빈 줄을 하나씩 넣는다. */
    fun spaced(blocks: List<Block>): List<Block> {
        val out = ArrayList<Block>(blocks.size * 2)
        for (b in blocks) {
            val prev = out.lastOrNull()
            if (b is Block.Para && b.text.isNotEmpty() && prev is Block.Para && prev.text.isNotEmpty()) {
                out += Block.Para("")
            }
            out += b
        }
        return out
    }

    private fun collapse(sb: CharSequence): String {
        val out = StringBuilder(sb.length)
        var space = false
        for (c in sb) {
            if (c.isWhitespace() || c == ' ') {
                space = true
            } else {
                if (space && out.isNotEmpty()) out.append(' ')
                space = false
                out.append(c)
            }
        }
        return out.toString()
    }

    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val NAMED = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "middot" to "·", "copy" to "©", "reg" to "®",
        "trade" to "™", "laquo" to "«", "raquo" to "»", "bull" to "•", "times" to "×",
        "deg" to "°", "shy" to "", "zwj" to "", "zwnj" to "", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    )

    fun decodeEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        return ENTITY.replace(s) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") ->
                    v.substring(2).toIntOrNull(16)?.let { codePoint(it) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { codePoint(it) } ?: m.value
                else -> NAMED[v] ?: NAMED[v.lowercase()] ?: m.value
            }
        }
    }

    private fun codePoint(cp: Int): String =
        if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else ""
}
