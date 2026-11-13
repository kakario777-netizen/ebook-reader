package com.kakario.ebookreader.book

import android.content.Context
import com.kakario.ebookreader.model.FileEntry
import java.io.File
import java.security.MessageDigest

/** 폰에 저장해 두는 TXT 정리본 (압축). 오래 안 연 것부터 지워 크기를 넘지 않게. */
class PreparedCache private constructor(context: Context) {
    private val dir = File(context.filesDir, "prepared").apply { mkdirs() }

    private fun file(tag: String, entry: FileEntry, code: String): File {
        val key = "$tag|${entry.path}|${entry.size}|${entry.modified}|$code"
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.txtp")
    }

    fun read(tag: String, entry: FileEntry, code: String): ByteArray? {
        val f = file(tag, entry, code)
        if (!f.exists()) return null
        f.setLastModified(System.currentTimeMillis())
        return runCatching { f.readBytes() }.getOrNull()
    }

    fun write(tag: String, entry: FileEntry, code: String, bytes: ByteArray) {
        runCatching {
            val f = file(tag, entry, code)
            val tmp = File(f.path + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(f)
            trim()
        }
    }

    private fun trim() {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        val limit = maxBytes
        if (limit <= 0) return
        var total = 0L
        for (f in files) {
            total += f.length()
            if (total > limit) f.delete()
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** 최대 용량 (설정에서 바꿈, 0 이하 = 제한 없음) */
        @Volatile var maxBytes: Long = 300L * 1024 * 1024
        @Volatile private var instance: PreparedCache? = null
        fun get(context: Context): PreparedCache =
            instance ?: synchronized(this) { instance ?: PreparedCache(context.applicationContext).also { instance = it } }
    }
}
