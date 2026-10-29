package com.kakario.ebookreader.book

import com.kakario.ebookreader.data.FtpServer
import com.kakario.ebookreader.model.RangeSource
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** 책이 어디에 있는지: FTP 서버 또는 내 폰(server == null). */
data class Origin(val server: FtpServer?) {
    val tag: String get() = server?.let { "ftp${it.id}" } ?: "local"
    val isLocal: Boolean get() = server == null
}

/** 폰 안 파일을 RangeSource로 쓴다. 다 쓰면 close. */
class FileRange(file: File) : RangeSource, Closeable {
    private val raf = RandomAccessFile(file, "r")
    override val size: Long = raf.length()

    @Synchronized
    override fun read(offset: Long, length: Int): ByteArray {
        val n = minOf(length.toLong(), size - offset).coerceAtLeast(0).toInt()
        val buf = ByteArray(n)
        raf.seek(offset)
        raf.readFully(buf)
        return buf
    }

    override fun close() {
        runCatching { raf.close() }
    }
}
