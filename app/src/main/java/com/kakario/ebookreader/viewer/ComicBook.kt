package com.kakario.ebookreader.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.RangeSource
import com.kakario.ebookreader.model.UserFacingException
import com.kakario.ebookreader.book.FileRange
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.book.ZipReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 열어 놓은 만화책(ZIP) 하나 (FTP는 스트리밍, 내 폰은 파일에서 바로).
 * ZIP을 통째로 받지 않고, 볼 페이지(이미지)만 그때그때 읽어서 보여준다.
 *
 * FTP 속도 개선:
 *  - 이 책 전용 FTP 연결을 여러 개(connections) 열어 여러 페이지를 동시에 받는다.
 *  - 그중 1개는 항상 '지금 보려는 페이지'용으로 비워두고, 나머지로 앞 페이지들을 미리 받는다.
 *  - 미리 받은 페이지는 압축된 원본(JPEG 등) 그대로 보관해서 메모리를 적게 쓰고,
 *    화면에 띄울 때 그림으로 바꾼다.
 */
class ComicBook(
    private val origin: Origin,
    val entry: FileEntry,
    /** 화면에 맞춰 줄일 최대 크기(px). 너무 큰 원본으로 메모리가 터지는 것을 막는다. */
    private val maxSide: Int,
    connections: Int = 3,
) : PageSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── FTP 연결 묶음
    private val connectionCount = if (origin.server != null) connections.coerceIn(1, 4) else 0
    private val allConnections: List<FtpBrowser> =
        origin.server?.let { s -> List(connectionCount) { FtpBrowser(s) } } ?: emptyList()
    private val pool = Channel<FtpBrowser>(Channel.UNLIMITED).apply {
        allConnections.forEach { trySend(it) }
    }
    /** 미리 받기가 연결을 다 차지하지 못하게 — 1개는 지금 볼 페이지용으로 남겨둔다. */
    private val prefetchGate = Semaphore((connectionCount - 1).coerceAtLeast(1))

    // ── 내 폰 파일
    private val localLock = Any()
    private var localFile: FileRange? = null

    /** 페이지 하나: nest가 -1이면 바깥 ZIP의 그림, 아니면 nests[nest] 안쪽 ZIP의 그림 (2중 압축). */
    private class Page(val nest: Int, val entry: ZipReader.Entry)

    /** 안쪽 ZIP: 저장만 됐으면 바깥 파일의 start 위치에서 바로 읽고, 압축됐으면 풀어 둔 임시 파일에서 읽는다. */
    private class Nest(val start: Long, val size: Long, val file: FileRange?, val temp: File?)

    private var pages: List<Page> = emptyList()
    private val nests = ArrayList<Nest>()
    private val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
    val pageCount: Int get() = pages.size

    private val heap = Runtime.getRuntime().maxMemory()

    /** 화면에 띄울 그림 (현재 화면 근처 몇 장). */
    private val bitmaps = object : LruCache<Int, Bitmap>((heap / 6).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    /** 미리 받아둔 압축 원본. */
    private val raws = object : LruCache<Int, ByteArray>(
        (heap / 6).coerceAtMost(160L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: Int, value: ByteArray): Int = value.size
    }

    private val rawInFlight = ConcurrentHashMap<Int, Deferred<ByteArray>>()
    private val bmpInFlight = ConcurrentHashMap<Int, Deferred<Bitmap>>()

    /** 목차를 읽어 페이지 목록을 만든다. 페이지 수를 돌려준다. */
    override suspend fun open(onProgress: (done: Long, total: Long) -> Unit): Int {
        val entries = withSource { ZipReader.listEntries(it) }
            ?: throw UserFacingException("ZIP 파일을 읽을 수 없어요.\n파일이 손상됐거나 ZIP 형식이 아닐 수 있어요.")
        val list = ArrayList<Page>()
        ZipReader.imagePages(entries).forEach { list += Page(-1, it) }
        // 2중 압축: ZIP 안의 ZIP들도 이름순으로 이어서 본다
        for (inner in ZipReader.innerArchives(entries)) {
            val nest = runCatching { openNest(inner) }.getOrNull() ?: continue
            val innerEntries = runCatching { withNest(nest) { ZipReader.listEntries(it) } }.getOrNull() ?: continue
            val idx = nests.size
            nests += nest
            ZipReader.imagePages(innerEntries).forEach { list += Page(idx, it) }
        }
        pages = list
        if (pages.isEmpty()) throw UserFacingException("이 ZIP 안에는 볼 수 있는 이미지가 없어요.")
        return pages.size
    }

    override fun cached(index: Int): Bitmap? = bitmaps.get(index)

    /** 화면에 띄울 페이지. 같은 페이지를 여러 곳에서 동시에 요청해도 한 번만 처리한다. */
    override suspend fun page(index: Int): Bitmap {
        bitmaps.get(index)?.let { return it }
        val job = bmpInFlight.getOrPut(index) {
            scope.async {
                try {
                    val bytes = raw(index, prefetch = false)
                    val bmp = withContext(Dispatchers.Default) { decode(bytes) }
                        ?: throw UserFacingException("이미지를 표시할 수 없어요")
                    bitmaps.put(index, bmp)
                    bmp
                } finally {
                    bmpInFlight.remove(index)
                }
            }
        }
        return job.await()
    }

    /** 앞으로 볼 페이지를 미리 받아둔다 (실패해도 조용히 넘어감). */
    override fun prefetch(index: Int) {
        if (index !in pages.indices) return
        if (bitmaps.get(index) != null || raws.get(index) != null || rawInFlight.containsKey(index)) return
        scope.launch { runCatching { raw(index, prefetch = true) } }
    }

    /** 바로 다음에 볼 페이지는 그림으로까지 바꿔둔다 (넘길 때 깜빡임 없이). */
    override fun prepare(index: Int) {
        if (index !in pages.indices || bitmaps.get(index) != null || bmpInFlight.containsKey(index)) return
        scope.launch { runCatching { page(index) } }
    }

    private suspend fun raw(index: Int, prefetch: Boolean): ByteArray {
        raws.get(index)?.let { return it }
        val job = rawInFlight.getOrPut(index) {
            scope.async {
                try {
                    val pg = pages.getOrNull(index) ?: throw UserFacingException("없는 페이지예요")
                    val e = pg.entry
                    val bytes = if (pg.nest >= 0) {
                        val nest = nests[pg.nest]
                        if (prefetch && nest.file == null) prefetchGate.withPermit { withNest(nest) { ZipReader.extract(it, e) } }
                        else withNest(nest) { ZipReader.extract(it, e) }
                    } else if (prefetch) {
                        prefetchGate.withPermit { withSource { ZipReader.extract(it, e) } }
                    } else {
                        withSource { ZipReader.extract(it, e) }
                    } ?: throw UserFacingException("이 페이지를 읽을 수 없어요 (지원하지 않는 압축 방식)")
                    raws.put(index, bytes)
                    bytes
                } finally {
                    rawInFlight.remove(index)
                }
            }
        }
        return job.await()
    }

    private suspend fun openNest(inner: ZipReader.Entry): Nest? {
        if (inner.method == 0) {
            val start = withSource { ZipReader.dataStart(it, inner) } ?: return null
            return Nest(start, inner.compressedSize, null, null)
        }
        // 압축된 안쪽 ZIP은 임시 파일로 풀어 둔다
        val tmp = File.createTempFile("nest", ".zip", tempDir)
        val ok = withSource { ZipReader.extractToFile(it, inner, tmp) }
        if (!ok) {
            tmp.delete()
            return null
        }
        return Nest(0, tmp.length(), FileRange(tmp), tmp)
    }

    private suspend fun <T> withNest(nest: Nest, block: (RangeSource) -> T): T {
        nest.file?.let { f -> return withContext(Dispatchers.IO) { block(f) } }
        return withSource { block(ZipReader.OffsetSource(it, nest.start, nest.size)) }
    }

    private suspend fun <T> withSource(block: (RangeSource) -> T): T {
        if (connectionCount > 0) {
            val conn = pool.receive()
            try {
                return conn.useFile(entry.path, entry.size, block)
            } finally {
                pool.trySend(conn)
            }
        }
        return withContext(Dispatchers.IO) {
            synchronized(localLock) {
                val f = localFile ?: FileRange(File(entry.path)).also { localFile = it }
                block(f)
            }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = ImageDecode.decode(bytes, maxSide)

    override fun close() {
        scope.cancel()
        pool.close()
        val conns = allConnections
        val file = localFile
        localFile = null
        val nestList = nests.toList()
        Thread {
            runCatching { file?.close() }
            nestList.forEach { n -> runCatching { n.file?.close() }; runCatching { n.temp?.delete() } }
            runBlocking { conns.forEach { runCatching { it.close() } } }
        }.start()
        bitmaps.evictAll()
        raws.evictAll()
    }
}
