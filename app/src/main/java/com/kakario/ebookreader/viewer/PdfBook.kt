package com.kakario.ebookreader.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.kakario.ebookreader.book.BookCache
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.UserFacingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * PDF 한 권. 안드로이드 기본 PDF 그리기(PdfRenderer)를 쓴다 — 별도 라이브러리 없이 가볍게.
 * PdfRenderer는 파일이 폰 안에 통째로 있어야 하므로 FTP의 PDF는 먼저 받아둔다(한 번 받으면 재사용).
 */
class PdfBook(
    private val origin: Origin,
    val entry: FileEntry,
    private val cache: BookCache,
    /** 화면에 맞춰 그릴 긴 변 크기(px) */
    private val maxSide: Int,
) : PageSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ftp: FtpBrowser? = null
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    /** PdfRenderer는 한 번에 한 페이지만 열 수 있다 */
    private val renderLock = Mutex()

    private val bitmaps = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }
    private val inFlight = ConcurrentHashMap<Int, Deferred<Bitmap>>()
    private var count = 0

    override suspend fun open(onProgress: (done: Long, total: Long) -> Unit): Int {
        val file = cache.fetch(
            origin, entry,
            ftp = { ftp ?: FtpBrowser(origin.server!!).also { ftp = it } },
            onProgress = onProgress,
        )
        renderLock.withLock {
            if (renderer == null) {
                try {
                    val d = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    fd = d
                    renderer = PdfRenderer(d)
                } catch (e: SecurityException) {
                    throw UserFacingException("암호가 걸린 PDF는 열 수 없어요.")
                } catch (e: Exception) {
                    throw UserFacingException("PDF를 열 수 없어요.\n파일이 손상됐거나 지원하지 않는 형식일 수 있어요.")
                }
            }
            count = renderer!!.pageCount
        }
        if (count <= 0) throw UserFacingException("이 PDF에는 페이지가 없어요.")
        return count
    }

    override fun cached(index: Int): Bitmap? = bitmaps.get(index)

    override suspend fun page(index: Int): Bitmap {
        bitmaps.get(index)?.let { return it }
        val job = inFlight.getOrPut(index) {
            scope.async {
                try {
                    render(index).also { bitmaps.put(index, it) }
                } finally {
                    inFlight.remove(index)
                }
            }
        }
        return job.await()
    }

    private suspend fun render(index: Int): Bitmap = renderLock.withLock {
        val r = renderer ?: throw UserFacingException("PDF가 닫혔어요")
        if (index !in 0 until r.pageCount) throw UserFacingException("없는 페이지예요")
        val p = r.openPage(index)
        try {
            val scale = maxSide.toFloat() / maxOf(p.width, p.height).coerceAtLeast(1)
            val w = (p.width * scale).toInt().coerceAtLeast(1)
            val h = (p.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // PDF 바탕은 투명이라 순수 흰색으로 먼저 칠한다
            bmp.eraseColor(Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            p.close()
        }
    }

    override fun prefetch(index: Int) {
        // PDF는 받을 게 없다 — 바로 다음 장은 prepare가 미리 그린다 (메모리 절약)
    }

    override fun prepare(index: Int) {
        if (index !in 0 until count || bitmaps.get(index) != null || inFlight.containsKey(index)) return
        scope.launch { runCatching { page(index) } }
    }

    override fun close() {
        scope.cancel()
        val conn = ftp
        Thread {
            runBlocking {
                renderLock.withLock {
                    runCatching { renderer?.close() }
                    runCatching { fd?.close() }
                    renderer = null
                    fd = null
                }
                runCatching { conn?.close() }
            }
        }.start()
        bitmaps.evictAll()
    }
}
