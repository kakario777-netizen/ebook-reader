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
        // 3) UTF-8로 깨짐 없이 읽히는지
        strictUtf8(b)?.let { return it }
        // 4) 옛 한글 방식
        return String(b, korean)
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
