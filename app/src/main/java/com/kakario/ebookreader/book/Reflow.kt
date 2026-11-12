package com.kakario.ebookreader.book

/**
 * TXT 문단 다듬기 엔진 — '중간에 잘린 문장'을 사람이 읽듯 다시 잇는다.
 *
 * 파일 전체를 한 번에 판단하지 않고, 줄마다 앞뒤 30줄(구간)을 보고 판단한다.
 * 그래서 합본처럼 어떤 권은 잘려 있고 어떤 권은 안 잘려 있어도 각각 맞게 처리된다.
 *
 *  1) 구간의 '잘린 폭' 찾기: 그 구간에서 줄 길이가 가장 많이 몰린 길이(±3자).
 *  2) 잘린 구간인지: 그 폭 근처에서 끝나는 줄이 많고, 그 줄들 상당수가 문장 중간에서 끝나면.
 *  3) 이어 붙이기: 잘린 구간에서 꽉 찬 줄이 문장 중간에서 끝났고, 다음 줄이 대사(" 「 ‘ -)·제목·
 *     장면 전환(* * *)으로 시작하지 않으면 잇는다. 문장이 끝난 줄 뒤에서는 끊는다.
 *  4) 띄어쓰기: 줄 경계에 원래 공백이 있었으면 띄우고, 글자 수로 강제로 자른 파일(경계 공백이
 *     남아 있는 파일)이면 공백 없던 곳은 단어 중간 → 붙인다. 아니면 띄운다.
 *  5) 한 줄씩 띄워 쓴 파일(줄마다 빈 줄)은 빈 줄 하나를 무시하고, 두 개 이상만 문단 구분.
 */
object Reflow {

    enum class Mode(val label: String) {
        AUTO("자동"),
        ON("항상"),
        OFF("끔"),
    }

    class Result(val paragraphs: List<String>, val reflowed: Boolean)

    private val SENTENCE_END = setOf(
        '.', '!', '?', '。', '…', '"', '\'', '”', '’', '」', '』', ')', '）', ']', '】', '>', '〉', '》',
        ':', ';', '~', '♡', '♥', '★', '☆',
    )
    private val DIALOG_START = setOf('"', '“', '\'', '‘', '「', '『', '<', '〈', '《', '[', '【', '(', '（', '-', '―', '─', '*')
    private const val WINDOW = 30

    fun endsSentence(t: String): Boolean = t.isEmpty() || t.last() in SENTENCE_END

    fun process(rawLines: List<String>, mode: Mode): Result {
        val raw = rawLines.map { it.trimEnd('\r') }
        val lines = raw.map { it.trimEnd(' ', '\t', '　', ' ').replace('\t', ' ') }
        if (mode == Mode.OFF) return Result(lines, false)

        val n = lines.size
        val len = IntArray(n) { lines[it].trim().length }
        val nb = (0 until n).filter { lines[it].isNotBlank() }
        if (nb.isEmpty()) return Result(lines, false)

        // ── 1·2) 줄마다 구간의 잘린 폭과 '잘린 구간' 여부
        val width = IntArray(n)
        val wrapped = BooleanArray(n)
        var anyWrapped = false
        val counts = IntArray(402)
        for (k in nb.indices) {
            val from = maxOf(0, k - WINDOW)
            val to = minOf(nb.size - 1, k + WINDOW)
            java.util.Arrays.fill(counts, 0)
            var winSize = 0
            var maxLen = 0
            for (j in from..to) {
                val l = len[nb[j]]
                if (l >= 15) {
                    counts[l.coerceAtMost(400)]++
                    winSize++
                    if (l > maxLen) maxLen = l
                }
            }
            if (winSize < 6) continue
            var best = 0
            var bestAt = 0
            for (c in 15..maxLen.coerceAtMost(397)) {
                var sum = 0
                for (d in -3..3) sum += counts[(c + d).coerceIn(0, 401)]
                if (sum > best) {
                    best = sum
                    bestAt = c
                }
            }
            val w = bestAt + 3
            width[nb[k]] = w
            if (mode == Mode.ON) {
                wrapped[nb[k]] = true
                anyWrapped = true
                continue
            }
            var band = 0
            var open = 0
            for (j in from..to) {
                val i = nb[j]
                if (len[i] >= w * 0.8 && len[i] <= w * 1.1) {
                    band++
                    if (!endsSentence(lines[i].trim())) open++
                }
            }
            val isWrapped = best >= 6 && best.toDouble() / winSize >= 0.3 &&
                open.toDouble() / band.coerceAtLeast(1) >= 0.3
            wrapped[nb[k]] = isWrapped
            if (isWrapped) anyWrapped = true
        }
        if (!anyWrapped) return Result(lines, false)

        // ── 4) 글자 수로 강제로 자른 파일인지 (줄 경계에 공백이 남아 있는가)
        var boundaries = 0
        var edgeSpaces = 0
        for (i in 0 until n - 1) {
            if (!wrapped[i] || lines[i].isBlank() || lines[i + 1].isBlank()) continue
            if (len[i] < width[i] * 0.8) continue
            boundaries++
            if (isSpace(raw[i].last()) || isSpace(raw[i + 1].first())) edgeSpaces++
        }
        val fixedCut = boundaries >= 10 && edgeSpaces.toDouble() / boundaries >= 0.08

        // ── 5) 한 줄씩 띄워 쓴 파일
        var followedByBlank = 0
        for (i in nb) if (i + 1 < n && lines[i + 1].isBlank()) followedByBlank++
        val doubleSpaced = nb.size >= 20 && followedByBlank.toDouble() / nb.size > 0.8

        fun looksNew(i: Int): Boolean {
            val t = lines[i].trim()
            if (t.first() in DIALOG_START) return true
            if (Chapters.isSceneBreak(t) || Chapters.looksLikeTitle(t)) return true
            // 들여쓰기는 새 문단 표시 — 단, 글자 수로 자른 파일은 줄 앞 공백이 잘린 자국일 수 있다
            if (!fixedCut && isSpace(lines[i].first())) return true
            return false
        }

        fun shouldJoin(a: Int, b: Int): Boolean {
            if (!wrapped[a]) return false
            val w = width[a]
            if (len[a] < w * 0.8 || len[a] > w * 1.1) return false
            val ta = lines[a].trim()
            if (looksNew(b) || Chapters.looksLikeTitle(ta) || Chapters.isSceneBreak(ta)) return false
            return !endsSentence(ta)
        }

        // ── 3) 잇기
        val out = ArrayList<String>()
        val para = StringBuilder()
        var prev = -1
        var blanks = 0
        for (i in 0 until n) {
            if (lines[i].isBlank()) {
                blanks++
                continue
            }
            var joined = false
            if (para.isNotEmpty() && prev >= 0) {
                val gapOk = blanks == 0 || (doubleSpaced && blanks == 1)
                if (gapOk && shouldJoin(prev, i)) {
                    val edge = isSpace(raw[prev].last()) || isSpace(raw[i].first())
                    if (edge || !fixedCut) para.append(' ')
                    para.append(lines[i].trim())
                    joined = true
                }
            }
            if (!joined) {
                if (para.isNotEmpty()) out += para.toString()
                if (blanks > 0 && (!doubleSpaced || blanks >= 2) && out.isNotEmpty()) out += ""
                para.setLength(0)
                para.append(if (wrapped[i]) lines[i].trim() else lines[i])
            }
            blanks = 0
            prev = i
        }
        if (para.isNotEmpty()) out += para.toString()
        return Result(out, true)
    }

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '　' || c == ' ' || c == '\t'
}
