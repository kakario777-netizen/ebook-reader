package com.kakario.ebookreader.thumb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.kakario.ebookreader.book.BookCache
import com.kakario.ebookreader.book.Epub
import com.kakario.ebookreader.book.FileRange
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.book.ZipReader
import com.kakario.ebookreader.data.FtpServer
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.BookKind
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.RangeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 목록에 작게 보여줄 표지.
 *  - ZIP: 첫 그림 / EPUB: 책이 알려준 표지 그림 → ZIP 전체를 받지 않고 필요한 부분만 읽는다
 *  - PDF: 첫 페이지를 그린다 (FTP는 파일을 받아야 해서 MAX_FTP_PDF 이하만, 받은 파일은 열 때 재사용)
 * 메모리 → 디스크 캐시(앱을 다시 켜도 유지) → 실제로 만들기 순서로 찾는다.
 * 화면에 보이는 줄만 만들고, 스크롤로 지나가면 만들다가도 멈춘다.
 */
class ThumbnailLoader private constructor(context: Context) {
    private val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val books = BookCache.get(context)
    private val sync = com.kakario.ebookreader.data.ServerSync.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 표지를 못 찾은 항목 — 앱을 켜 있는 동안 다시 시도하지 않는다. */
    private val misses = ConcurrentHashMap.newKeySet<String>()

    private class Work(val job: Deferred<Bitmap?>, val waiters: AtomicInteger = AtomicInteger(0))
    private val inFlight = ConcurrentHashMap<String, Work>()

    private val ftpConnections = ConcurrentHashMap<FtpServer, FtpBrowser>()
    private val localGate = Semaphore(2)

    /** 다시 만들기를 하면 올라가는 번호 — 화면의 표지들이 새로 그려지게 하는 신호. */
    var generation by mutableIntStateOf(0)
        private set

    /** 책을 보는 동안에는 표지 만들기를 잠시 멈춘다 (보는 쪽에 속도 양보). */
    val paused = MutableStateFlow(false)

    fun cached(key: String): Bitmap? = memory.get(key)

    fun keyFor(origin: Origin, entry: FileEntry): String =
        "${origin.tag}:${entry.path}:${entry.size}:${entry.modified}"

    suspend fun load(origin: Origin, entry: FileEntry): Bitmap? {
        if (!entry.hasCover) return null
        val key = keyFor(origin, entry)
        memory.get(key)?.let { return it }
        if (key in misses) return null
        val work = inFlight.compute(key) { _, old ->
            old ?: Work(scope.async {
                try {
                    produce(key, origin, entry)
                } finally {
                    inFlight.remove(key)
                }
            })
        }!!
        work.waiters.incrementAndGet()
        try {
            return work.job.await()
        } finally {
            // 아무도 기다리지 않으면(스크롤로 지나감) 만들기를 멈춘다
            if (work.waiters.decrementAndGet() == 0 && !work.job.isCompleted) {
                work.job.cancel()
                inFlight.remove(key, work)
            }
        }
    }

    private suspend fun produce(key: String, origin: Origin, entry: FileEntry): Bitmap? {
        readDisk(key)?.let {
            memory.put(key, it)
            return it
        }
        paused.first { !it }
        // FTP 책: 다른 기기(코믹뷰어 등)가 서버에 올려 둔 표지가 있으면 그걸 쓴다
        origin.server?.let { srv ->
            sync.readThumb(srv, entry)?.let { bytes ->
                scaleDown(bytes)?.let { bmp ->
                    writeDisk(key, bmp)
                    memory.put(key, bmp)
                    return bmp
                }
            }
        }
        val bmp = try {
            when (entry.kind) {
                BookKind.COMIC -> withSource(origin, entry) { src ->
                    ZipReader.listEntries(src)?.let { ZipReader.imagePages(it).firstOrNull() }
                        ?.let { ZipReader.extract(src, it) }
                }?.let { scaleDown(it) }

                BookKind.EPUB -> withSource(origin, entry) { Epub.coverBytes(it) }?.let { scaleDown(it) }

                BookKind.PDF -> pdfCover(origin, entry)

                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 접속 오류 등 — 이번엔 실패로 두고, 다음에 화면을 다시 열면 재시도
            return null
        }
        if (bmp == null) {
            misses += key
        } else {
            writeDisk(key, bmp)
            memory.put(key, bmp)
            // 이 기기에서 만든 표지는 서버에도 올려 다른 기기가 쓰게 한다
            origin.server?.let { srv ->
                val out = java.io.ByteArrayOutputStream()
                if (bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)) sync.uploadThumb(srv, entry, out.toByteArray())
            }
        }
        return bmp
    }

    private suspend fun pdfCover(origin: Origin, entry: FileEntry): Bitmap? {
        val file = books.cachedFile(origin, entry)
            ?: if (origin.server != null && entry.size <= MAX_FTP_PDF) {
                books.fetch(origin, entry, ftp = { ftp(origin.server) })
            } else {
                return null
            }
        return localGate.withPermit {
            withContext<Bitmap?>(Dispatchers.IO) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val r = PdfRenderer(fd)
                    try {
                        if (r.pageCount == 0) return@withContext null
                        val page = r.openPage(0)
                        try {
                            val w = TARGET_WIDTH
                            val h = (page.height.toLong() * w / page.width.coerceAtLeast(1)).toInt().coerceIn(1, w * 3)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        } finally {
                            page.close()
                        }
                    } finally {
                        r.close()
                    }
                }
            }
        }
    }

    /** 주어진 항목의 표지를 지우고 다시 만들게 한다. */
    fun invalidate(origin: Origin, entry: FileEntry) {
        val key = keyFor(origin, entry)
        memory.remove(key)
        misses.remove(key)
        fileFor(key).delete()
        generation++
    }

    private suspend fun <T> withSource(origin: Origin, entry: FileEntry, block: (RangeSource) -> T): T {
        val server = origin.server
        if (server != null) return ftp(server).useFile(entry.path, entry.size, block)
        return localGate.withPermit {
            withContext(Dispatchers.IO) {
                FileRange(File(entry.path)).use { block(it) }
            }
        }
    }

    private fun ftp(server: FtpServer): FtpBrowser =
        ftpConnections.getOrPut(server) { FtpBrowser(server) }

    /** 원본 그림을 목록용 크기로 줄인다. */
    private fun scaleDown(raw: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            raw, 0, raw.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: return null

        if (decoded.width <= TARGET_WIDTH) return decoded
        val h = (decoded.height.toLong() * TARGET_WIDTH / decoded.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, TARGET_WIDTH, h, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.jpg")
    }

    private fun readDisk(key: String): Bitmap? {
        val f = fileFor(key)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(
            f.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
        )
    }

    private fun writeDisk(key: String, bmp: Bitmap) {
        try {
            fileFor(key).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (_: Exception) {
        }
    }

    /** 설정 화면에서 쓰는 캐시 정보/삭제. */
    fun diskCacheBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        memory.evictAll()
        misses.clear()
        dir.listFiles()?.forEach { it.delete() }
        generation++
    }

    fun shutdown() {
        val conns = ftpConnections.values.toList()
        ftpConnections.clear()
        Thread {
            runBlocking { conns.forEach { runCatching { it.close() } } }
        }.start()
    }

    companion object {
        /** 표지 폭 — 서버에 올려 코믹뷰어와 같이 쓰므로 긴 변 약 360px */
        private const val TARGET_WIDTH = 250
        /** FTP의 PDF는 이 크기 이하만 표지를 만든다 (표지 하나 때문에 큰 파일을 받지 않도록) */
        private const val MAX_FTP_PDF = 40L * 1024 * 1024

        @Volatile private var instance: ThumbnailLoader? = null

        fun get(context: Context): ThumbnailLoader =
            instance ?: synchronized(this) {
                instance ?: ThumbnailLoader(context.applicationContext).also { instance = it }
            }
    }
}
