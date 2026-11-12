package com.kakario.ebookreader.book

/**
 * TXT의 '중간에 잘린 문장' 다시 잇기.
 *
 * 옛 텍스트 파일 중에는 한 줄을 일정 글자 수(예: 40자)에서 강제로 엔터 쳐 둔 것이 많다.
 * 그대로 보여주면 글자 크기를 바꿔도 문장이 화면 중간에서 끊겨 읽기 불편하다.
 *
 * 판별: 글자가 있는 줄들의 길이를 보고, 많은 줄이 비슷한 '최대 길이' 근처에서 끝나고
 *       그 줄들이 문장 끝(. ! ? 」 등)이 아니면 → 잘린 파일로 본다.
 * 잇기: 꽉 찬 줄은 다음 줄과 이어 붙이고, 짧은 줄(문단 마지막 줄)이나 빈 줄에서 문단을 끊는다.
 *       새 문단처럼 시작하는 줄(들여쓰기, 따옴표 대사, 장 제목 등) 앞에서도 끊는다.
 * 빈 줄이 매 줄마다 끼어 있는 파일(한 줄 띄워 쓴 파일)도 알아서 정리한다.
 */
object Reflow {

    enum class Mode(val label: String) {
        AUTO("자동"),
        ON("항상"),
        OFF("끔"),
    }

    /** 결과: 문단 목록(빈 문자열 = 빈 줄)과 실제로 이었는지. */
    class Result(val paragraphs: List<String>, val reflowed: Boolean)

    private val SENTENCE_END = setOf(
        '.', '!', '?', '。', '…', '"', '\'', '”', '’', '」', '』', ')', '）', ']', '】', '>', '〉', '》',
        ':', ';', '~', '♡', '♥', '★', '☆', '-', '=', '*',
    )
    private val PARA_START = setOf('"', '“', '\'', '‘', '「', '『', '(', '（', '[', '【', '<', '〈', '《', '-', '*', '#', '=', '◆', '◇', '■', '□', '●', '○', '※', '▶', '▷', '☆', '★')
    private val HEADING = Regex("^(제\\s*\\d+\\s*[장화권부편절]|\\d+\\s*[장화권부편]|chapter\\s*\\d+|prologue|epilogue|프롤로그|에필로그|외전|후기|작가의\\s*말)", RegexOption.IGNORE_CASE)

    fun process(rawLines: List<String>, mode: Mode): Result {
        val lines = rawLines.map { it.trimEnd('\r', ' ', '\t', '　', ' ').replace('\t', ' ') }
        if (mode == Mode.OFF) return Result(lines, false)

        val texts = lines.filter { it.isNotBlank() }
        if (texts.isEmpty()) return Result(lines, false)
        val lengths = texts.map { it.trim().length }.sorted()
        // 대표 줄 길이: 상위 10% 지점 (아주 긴 몇 줄에 휘둘리지 않게)
        val width = lengths[(lengths.size * 0.9).toInt().coerceAtMost(lengths.size - 1)]

        if (mode == Mode.AUTO) {
            if (texts.size < 20 || width < 16) return Result(lines, false)
            // '꽉 찬 줄인데 문장 중간에서 끝난 줄'이 충분히 많으면 잘린 파일.
            // (대사처럼 짧은 줄이 많이 섞여 있어도 알아보도록, 전체 줄 수 대비 비율로 본다)
            val openFull = texts.count { val t = it.trim(); t.length >= width * 0.75 && !endsSentence(t) }
            if (openFull < 15 || openFull.toDouble() / texts.size < 0.12) return Result(lines, false)
        }
        val w = if (width < 10) 40 else width

        // 한 줄 띄워 쓴 파일: 거의 모든 글줄 사이에 빈 줄이 하나씩 → 빈 줄 하나는 무시, 두 개 이상만 문단 구분
        val doubleSpaced = isDoubleSpaced(lines)

        // 글자(바이트) 수로 강제로 자른 파일인지 → 단어 중간에서도 잘리므로 공백 없이 붙여야 한다.
        // 이런 파일은 마침 띄어쓰기 자리에서 잘리면 그 공백이 줄 끝/시작에 그대로 남아 있다.
        // 반대로 단어 단위로 줄을 바꾼 파일은 줄 끝 공백이 거의 없다.
        var boundaries = 0
        var edgeSpaces = 0
        for (k in 0 until rawLines.size - 1) {
            val a = rawLines[k].trimEnd('\r')
            val b = rawLines[k + 1].trimEnd('\r')
            if (a.isBlank() || b.isBlank() || a.trim().length < w * 0.75) continue
            boundaries++
            if (a.last() == ' ' || a.last() == '\u3000' || b.first() == ' ' || b.first() == '\u3000') edgeSpaces++
        }
        val exactCut = (boundaries >= 10 && edgeSpaces.toDouble() / boundaries >= 0.08) ||
            texts.count { it.trim().length in (w - 1)..(w + 1) }.toDouble() / texts.size > 0.45

        val out = ArrayList<String>()
        val para = StringBuilder()
        var blankRun = 0
        var prevLine = "" // 바로 앞의 글줄 (잘렸는지 판단용)

        fun endPara() {
            if (para.isNotEmpty()) {
                out += para.toString()
                para.setLength(0)
            }
        }

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) {
                blankRun++
                i++
                continue
            }
            if (blankRun > 0) {
                val breakHere = !doubleSpaced || blankRun >= 2
                if (breakHere) {
                    endPara()
                    out += ""
                } else if (para.isNotEmpty() && !shouldJoin(prevLine, raw, w)) {
                    endPara()
                }
                blankRun = 0
            } else if (para.isNotEmpty() && !shouldJoin(prevLine, raw, w)) {
                endPara()
            }

            val line = raw.trim()
            if (para.isEmpty()) {
                para.append(line)
            } else {
                val prev = para.last()
                when {
                    // 영어 단어 하이픈 끊김: "infor-" + "mation"
                    prev == '-' && para.length >= 2 && para[para.length - 2].isLetter() && line.first().isLowerCase() -> {
                        para.setLength(para.length - 1)
                        para.append(line)
                    }
                    // 줄 앞이나 끝에 공백이 있었으면 단어 경계였던 것
                    rawLines[i].first().isWhitespace() ||
                        rawLines.getOrNull(i - 1)?.trimEnd('\r')?.let { p -> p.isNotEmpty() && p.last().isWhitespace() } == true -> {
                        para.append(' ').append(line)
                    }
                    // 강제로 자른 파일: 줄 경계에 공백이 없었으면 단어 중간 → 그대로 붙인다 ("때"+"문에")
                    exactCut && !prev.isWhitespace() && !line.first().isWhitespace() -> para.append(line)
                    else -> para.append(' ').append(line)
                }
            }
            prevLine = line
            i++
        }
        endPara()
        return Result(out, true)
    }

    /** 앞 글줄(prev) 뒤에 다음 줄을 이어 붙일지. */
    private fun shouldJoin(prev: String, next: String, width: Int): Boolean {
        val lastLineLen = prev.length
        val nextTrim = next.trim()
        val nextLooksNew = next.first().isWhitespace() ||
            nextTrim.first() in PARA_START ||
            HEADING.containsMatchIn(nextTrim)
        // 짧은 줄 = 문단의 마지막 줄
        if (lastLineLen < width * 0.7) return false
        // 꽉 찬 줄이 문장 끝으로 끝났고 다음 줄이 새 문단처럼 시작하면 끊는다
        if (endsSentence(prev) && nextLooksNew) return false
        // 장 제목 같은 줄은 항상 따로
        if (HEADING.containsMatchIn(nextTrim) && nextTrim.length < width / 2) return false
        return true
    }

    private fun endsSentence(s: String): Boolean {
        val c = s.lastOrNull() ?: return true
        return c in SENTENCE_END
    }

    private fun isHangul(c: Char): Boolean = c in '가'..'힣' || c in 'ㄱ'..'ㆎ'

    private fun isDoubleSpaced(lines: List<String>): Boolean {
        var textLines = 0
        var followedByBlank = 0
        for (i in lines.indices) {
            if (lines[i].isBlank()) continue
            textLines++
            if (i + 1 < lines.size && lines[i + 1].isBlank()) followedByBlank++
        }
        return textLines >= 20 && followedByBlank.toDouble() / textLines > 0.8
    }
}
