package com.kakario.ebookreader.book

import android.content.Context
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * FTP에 있는 PDF·EPUB·TXT를 폰에 받아두는 곳.
 * 이 세 종류는 파일이 통째로 있어야 빠르게 열리므로 한 번 받아두고 다시 쓴다.
 * (만화 ZIP은 받지 않고 페이지별로 스트리밍)
 * 오래 안 연 파일부터 지워서 전체 용량을 MAX_BYTES 아래로 유지한다.
 */
class BookCache private constructor(context: Context) {
    private val dir = File(context.cacheDir, "books").apply { mkdirs() }
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun fileFor(origin: Origin, entry: FileEntry): File {
        val key = "${origin.tag}|${entry.path}|${entry.size}|${entry.modified}"
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, hash + "." + entry.name.substringAfterLast('.', "bin").lowercase())
    }

    /** 이미 받아둔 파일이 있으면 바로 돌려준다 (없으면 null). */
    fun cachedFile(origin: Origin, entry: FileEntry): File? {
        if (origin.isLocal) return File(entry.path)
        val f = fileFor(origin, entry)
        return if (f.exists() && f.length() == entry.size) f else null
    }

    /**
     * 읽을 수 있는 폰 안 파일을 돌려준다.
     * 내 폰 책은 원래 파일 그대로, FTP 책은 받아둔 사본 (없으면 지금 받음).
     */
    suspend fun fetch(
        origin: Origin,
        entry: FileEntry,
        ftp: () -> FtpBrowser,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): File {
        if (origin.isLocal) return File(entry.path)
        val target = fileFor(origin, entry)
        val lock = locks.getOrPut(target.name) { Mutex() }
        return lock.withLock {
            if (target.exists() && target.length() == entry.size) {
                target.setLastModified(System.currentTimeMillis())
                return@withLock target
            }
            // 한 파일 한도 = 설정한 '받아둔 책 최대' (무제한이면 없음), 그리고 남은 저장공간
            val limit = maxBytes
            if (limit > 0 && entry.size > limit) {
                throw UserFacingException("파일(${entry.size / (1 shl 20)}MB)이 '받아둔 책 최대'(${limit / (1 shl 20)}MB)보다 커요.\n설정 → 저장공간에서 늘려 주세요.")
            }
            val free = dir.usableSpace
            if (free in 1 until entry.size + (64L shl 20)) {
                throw UserFacingException("저장공간이 모자라요 (남은 공간 ${free / (1 shl 20)}MB, 필요 ${entry.size / (1 shl 20)}MB).")
            }
            val tmp = File(dir, target.name + ".part")
            ftp().download(entry.path, tmp) { onProgress(it, entry.size) }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw UserFacingException("받은 파일을 저장하지 못했어요. 저장공간을 확인해 주세요.")
            }
            withContext(Dispatchers.IO) { trim(keep = target) }
            target
        }
    }

    private fun trim(keep: File) {
        val files = dir.listFiles()?.filter { !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        val limit = maxBytes
        if (limit <= 0 || total <= limit) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit) break
            if (f == keep) continue
            total -= f.length()
            f.delete()
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** 최대 용량 (설정에서 바꿈, 0 이하 = 제한 없음) */
        @Volatile var maxBytes: Long = 800L * 1024 * 1024

        @Volatile private var instance: BookCache? = null

        fun get(context: Context): BookCache =
            instance ?: synchronized(this) {
                instance ?: BookCache(context.applicationContext).also { instance = it }
            }
    }
}
