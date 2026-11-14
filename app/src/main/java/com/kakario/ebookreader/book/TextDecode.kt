package com.kakario.ebookreader.book

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 글자 인코딩 자동 판별.
 * 한국 TXT는 UTF-8과 옛 방식(EUC-KR/CP949)이 섞여 있어서,
 * UTF-8로 깨짐 없이 읽히면 UTF-8, 아니면 CP949로 읽는다.
 */
object TextDecode {

    val korean: Charset = runCatching { Charset.forName("MS949") }
        .recoverCatching { Charset.forName("EUC-KR") }
        .getOrDefault(Charsets.ISO_8859_1)

    fun decodeText(b: ByteArray): String {
        // 1) 맨 앞 표시(BOM)
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) {
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        }
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) {
            return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        }
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) {
            return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        }
        // 2) 표시 없는 UTF-16 (0 바이트가 많음)
        val sample = minOf(b.size, 4096)
        var evenZero = 0
        var oddZero = 0
        for (i in 0 until sample) {
            if (b[i].toInt() == 0) if (i % 2 == 0) evenZero++ else oddZero++
        }
        if (sample > 0 && (evenZero + oddZero) > sample / 5) {
            return String(b, if (oddZero >= evenZero) Charsets.UTF_16LE else Charsets.UTF_16BE)
        }
        // 3) UTF-8로 깨짐 없이 읽히는지, 아니면 거의 UTF-8인지 (깨진 글자 조금 섞인 파일)
        strictUtf8(b)?.let { return it }
        if (isUtf8Bytes(b)) return String(b, Charsets.UTF_8)
        // 4) 옛 한글 방식
        return String(b, korean)
    }

    /**
     * 큰 TXT용: 파일 전체를 한 번에 메모리에 올리지 않고 줄 단위로 읽는다.
     * (원본 바이트 + 통짜 글 + 줄 목록을 동시에 들고 있지 않아서 큰 책도 메모리가 버틴다)
     */
    fun readLines(file: java.io.File): ArrayList<String> {
        val cs = detectCharset(file)
        val out = ArrayList<String>()
        java.io.FileInputStream(file).use { fis ->
            val bis = java.io.BufferedInputStream(fis, 1 shl 16)
            // BOM 건너뛰기
            bis.mark(4)
            val h = ByteArray(3)
            val n = bis.read(h)
            val skip = when {
                n >= 3 && h[0] == 0xEF.toByte() && h[1] == 0xBB.toByte() && h[2] == 0xBF.toByte() -> 3
                n >= 2 && (h[0] == 0xFF.toByte() && h[1] == 0xFE.toByte() || h[0] == 0xFE.toByte() && h[1] == 0xFF.toByte()) -> 2
                else -> 0
            }
            bis.reset()
            if (skip > 0) bis.skip(skip.toLong())
            java.io.BufferedReader(java.io.InputStreamReader(bis, cs), 1 shl 16).use { r ->
                while (true) out += (r.readLine() ?: break)
            }
        }
        return out
    }

    /** 파일 인코딩 판별 (decodeText와 같은 순서, 단 파일을 조금씩 읽어서). */
    fun detectCharset(file: java.io.File): Charset {
        val head = ByteArray(4096)
        val hn = java.io.FileInputStream(file).use { it.read(head) }.coerceAtLeast(0)
        if (hn >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) return Charsets.UTF_8
        if (hn >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (hn >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        var evenZero = 0
        var oddZero = 0
        for (i in 0 until hn) if (head[i].toInt() == 0) if (i % 2 == 0) evenZero++ else oddZero++
        if (hn > 0 && (evenZero + oddZero) > hn / 5) return if (oddZero >= evenZero) Charsets.UTF_16LE else Charsets.UTF_16BE
        return if (isUtf8(file)) Charsets.UTF_8 else korean
    }

    /**
     * UTF-8인지 점수로 판단 (파일을 조금씩 읽으며).
     * 중간에 깨진 글자가 몇 개 섞인 UTF-8 파일도 UTF-8로 본다:
     * 올바른 UTF-8 글자가 깨진 자리보다 50배 넘게 많으면 UTF-8.
     * (옛 한글 방식 파일은 깨진 자리가 훨씬 많이 나온다)
     */
    private fun isUtf8(file: java.io.File): Boolean {
        var need = 0
        var valid = 0L
        var invalid = 0L
        val buf = ByteArray(1 shl 16)
        java.io.FileInputStream(file).use { fis ->
            while (true) {
                val n = fis.read(buf)
                if (n < 0) break
                for (i in 0 until n) {
                    val b = buf[i].toInt() and 0xFF
                    if (need > 0) {
                        if ((b and 0xC0) == 0x80) {
                            need--
                            if (need == 0) valid++
                            continue
                        }
                        invalid++
                        need = 0
                        // 이 바이트를 새 글자 시작으로 다시 본다
                    }
                    when {
                        b < 0x80 -> {}
                        b in 0xC2..0xDF -> need = 1
                        b in 0xE0..0xEF -> need = 2
                        b in 0xF0..0xF4 -> need = 3
                        else -> invalid++
                    }
                }
            }
        }
        return valid == 0L && invalid == 0L || valid > invalid * 50
    }

    private fun isUtf8Bytes(b: ByteArray): Boolean {
        var need = 0
        var valid = 0L
        var invalid = 0L
        for (x in b) {
            val v = x.toInt() and 0xFF
            if (need > 0) {
                if ((v and 0xC0) == 0x80) {
                    need--
                    if (need == 0) valid++
                    continue
                }
                invalid++
                need = 0
            }
            when {
                v < 0x80 -> {}
                v in 0xC2..0xDF -> need = 1
                v in 0xE0..0xEF -> need = 2
                v in 0xF0..0xF4 -> need = 3
                else -> invalid++
            }
        }
        return valid == 0L && invalid == 0L || valid > invalid * 50
    }

    /** XHTML·XML: 문서 머리의 encoding="..." 표시를 따르고, 없으면 UTF-8. */
    fun decodeXml(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) {
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        }
        if (b.size >= 2 && (b[0] == 0xFF.toByte() || b[0] == 0xFE.toByte())) return decodeText(b)
        val head = String(b, 0, minOf(b.size, 200), Charsets.ISO_8859_1)
        val declared = Regex("encoding\\s*=\\s*[\"']([\\w.-]+)[\"']").find(head)?.groupValues?.get(1)
        if (declared != null && !declared.equals("utf-8", ignoreCase = true)) {
            runCatching { return String(b, Charset.forName(declared)) }
        }
        return strictUtf8(b) ?: String(b, korean)
    }

    private fun strictUtf8(b: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
}
