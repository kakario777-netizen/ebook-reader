package com.kakario.ebookreader.book

/**
 * TXT 장 제목 자동 찾기 — 파일은 그대로 두고, 보여줄 때만 제목으로 꾸민다.
 *
 * 이런 짧은 줄을 장 제목으로 본다:
 *   제1장 / 제 12 화 / 제三장 김장군의 비밀 / 1장 / 12화 / 1권
 *   Chapter 3 / Episode 2 / Part IV
 *   프롤로그 / 에필로그 / 서장 / 종장 / 외전 / 번외 / 후기 / 작가의 말
 *   #12 / < 1화 > / 【 제3장 】 / [12화] / - 3 -
 * '제1장'처럼 제목 표시만 있고 다음 줄이 짧은 말이면 그 줄은 부제목(김장군의 비밀)으로 붙인다.
 * 문장처럼 끝나는 줄(…다. …요?)이나 긴 줄은 제목으로 보지 않는다.
 */
object Chapters {

    private const val MAX_TITLE = 40
    private const val MAX_SUB = 30
    private const val PARTICLES = "을를이가은는의에도과와로만부까"

    private val NUM = "[0-9０-９一二三四五六七八九十百千零〇]+"
    private val PATTERNS = listOf(
        // 제1장, 제 12 화, 제三부
        Regex("^제\\s*$NUM\\s*[장화권부편절막회]"),
        // 1장, 12화, 3권 (뒤는 끝이거나 공백·문장부호)
        Regex("^$NUM\\s*[장화권부편막회](?=$|[\\s.:·\\-—)\\]】>〉》])"),
        // Chapter 3, Episode 2, Part IV, Ch.5
        Regex("^(chapter|episode|part|ch\\.|ep\\.)\\s*([0-9]+|[ivxlc]+)\\b", RegexOption.IGNORE_CASE),
        // 프롤로그, 에필로그 …
        Regex("^(프롤로그|에필로그|서장|종장|서문|序章|終章|외전|번외편?|후기|작가의\\s*말|작가\\s*후기|prologue|epilogue|intro|outro)(?=$|[\\s.:·\\-—()\\[\\]0-9])", RegexOption.IGNORE_CASE),
        // #12
        Regex("^#\\s*[0-9]+(?=$|\\s)"),
    )
    /** 괄호로 둘러싼 줄: < 1화 >, 【 제3장 】, [12화], - 3 - , ― 12 ― */
    private val WRAPPED = Regex("^[<〈《【\\[(（\\-―─=*]+\\s*(.+?)\\s*[>〉》】\\])）\\-―─=*]+$")
    private val NUMBER_ONLY = Regex("^$NUM$")
    /** 문장으로 끝나는 줄 (제목이 아님) */
    private val SENTENCE = Regex("(다|요|죠|까|네|군|지|어|아|야)[.!?…]+[\"'”’]?$")

    /** 이 줄이 장 제목인지. 맞으면 제목 표시 부분이 줄 전체인지(부제목이 다음 줄에 올 수 있는지)도 알려준다. */
    private fun titleKind(line: String): Kind? {
        val t = line.trim()
        if (t.isEmpty() || t.length > MAX_TITLE) return null
        if (SENTENCE.containsMatchIn(t)) return null
        PATTERNS.forEach { re ->
            val m = re.find(t) ?: return@forEach
            // '제1장을', '3화의'처럼 바로 조사가 붙으면 문장 속 낱말이지 제목이 아니다
            if (t.getOrNull(m.range.last + 1)?.let { it in PARTICLES } == true) return@forEach
            val rest = t.substring(m.range.last + 1).trim(' ', '.', ':', '·', '-', '—', '(', ')', '[', ']')
            return if (rest.isEmpty()) Kind.MARK_ONLY else Kind.WITH_NAME
        }
        WRAPPED.matchEntire(t)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (NUMBER_ONLY.matches(inner)) return Kind.MARK_ONLY
            if (PATTERNS.any { it.containsMatchIn(inner) }) return Kind.MARK_ONLY
        }
        return null
    }

    private enum class Kind { MARK_ONLY, WITH_NAME }

    /** 장면 전환 줄: * * *, ◇◇◇, -----, ======, …… 등 */
    private val SCENE = Regex("^[\\s*＊◇◆○●□■☆★=\\-~#·.…_+※]{3,}$")
    /** 번호만 있는 줄: 1, 12., 003) */
    private val NUMBER_LINE = Regex("^([0-9]{1,4})\\s*[.)]?$")
    /** 권 표시로 끝나는 줄: '책이름 1권', '책이름 03권 (완)' */
    private val VOLUME = Regex("[0-9]+\\s*권\\s*(\\(?완\\)?|\\[?완결\\]?)?$")

    fun isSceneBreak(t: String): Boolean {
        val s = t.trim()
        return s.length in 3..40 && SCENE.matches(s) && s.any { !it.isWhitespace() }
    }

    /** 패턴만으로 확실한 장 제목 (줄 잇기에서 이 줄 앞뒤는 잇지 않는다). */
    fun looksLikeTitle(t: String): Boolean = titleKind(t) != null

    /** 문단 목록 → 제목 표시를 붙인 블록 목록. */
    fun mark(paragraphs: List<String>): List<Html.Block> {
        val n = paragraphs.size
        fun blank(k: Int) = k < 0 || k >= n || paragraphs[k].isBlank()
        fun isolated(k: Int) = blank(k - 1) && blank(k + 1)

        // 번호만 있는 줄이 앞뒤가 비어 있고, 1·2·3처럼 차례로 늘어나면 → 장 번호
        val numbered = HashSet<Int>()
        run {
            val found = ArrayList<Pair<Int, Int>>()
            for (k in 0 until n) {
                val m = NUMBER_LINE.matchEntire(paragraphs[k].trim()) ?: continue
                if (isolated(k)) found += k to m.groupValues[1].toInt()
            }
            if (found.size >= 3) {
                val inc = found.zipWithNext().count { (a, b) -> b.second == a.second + 1 }
                if (inc >= (found.size - 1) * 0.6) found.forEach { numbered += it.first }
            }
        }
        // 같은 꾸밈(- 제목 -, 【제목】, < 제목 >)으로 둘러싼 짧은 줄이 여러 번 나오면 → 장 제목
        val decoCount = HashMap<String, Int>()
        for (k in 0 until n) {
            val t = paragraphs[k].trim()
            if (t.length in 3..MAX_TITLE && WRAPPED.matches(t) && isolated(k)) {
                val key = "${t.first()}${t.last()}"
                decoCount[key] = (decoCount[key] ?: 0) + 1
            }
        }

        val out = ArrayList<Html.Block>(n)
        var i = 0
        while (i < n) {
            val p = paragraphs[i]
            val t = p.trim()
            if (t.isNotEmpty() && isSceneBreak(t)) {
                out += Html.Block.Para(t, level = 4)
                i++
                continue
            }
            var kind = titleKind(p)
            if (kind == null && t.isNotEmpty() && t.length <= MAX_TITLE && !SENTENCE.containsMatchIn(t)) {
                val decorated = WRAPPED.matches(t) && isolated(i) && (decoCount["${t.first()}${t.last()}"] ?: 0) >= 3
                val volume = VOLUME.containsMatchIn(t) && isolated(i)
                if (i in numbered || decorated || volume) kind = if (i in numbered) Kind.MARK_ONLY else Kind.WITH_NAME
            }
            if (kind == null) {
                out += Html.Block.Para(p)
                i++
                continue
            }
            out += Html.Block.Para(p.trim(), level = 1)
            i++
            if (kind == Kind.MARK_ONLY) {
                // 바로 다음 글줄(빈 줄 하나까지 건너뜀)이 짧은 말이면 부제목
                var j = i
                if (j < paragraphs.size && paragraphs[j].isBlank()) j++
                val next = paragraphs.getOrNull(j)?.trim()
                // 부제목은 짧고, 문장이 아니고, 그 뒤가 빈 줄이어야 한다 (본문 첫 줄을 잘못 잡지 않게)
                if (next != null && next.isNotEmpty() && next.length <= MAX_SUB && blank(j + 1) &&
                    !SENTENCE.containsMatchIn(next) && titleKind(next) == null && !isSceneBreak(next) &&
                    next.last() !in ".!?…\"”'’"
                ) {
                    out += Html.Block.Para(next, level = 1, sub = true)
                    i = j + 1
                }
            }
        }
        return out
    }
}
