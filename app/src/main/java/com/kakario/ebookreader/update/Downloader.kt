package com.kakario.ebookreader.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 끊겨도 이어받는 파일 받기.
 * 받는 중 15초 동안 아무것도 안 오거나 연결이 끊기면, 받은 데까지는 두고
 * 'Range' 요청으로 나머지만 다시 받는다 (최대 30번).
 */
object Downloader {
    fun fetch(url: String, dest: File, expectedSize: Long, onProgress: (done: Long, total: Long) -> Unit) {
        dest.delete()
        var total = expectedSize
        var lastError: Exception? = null
        for (attempt in 1..30) {
            val done = if (dest.exists()) dest.length() else 0L
            if (total > 0 && done >= total) return
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ebook-reader-app")
                    if (done > 0) setRequestProperty("Range", "bytes=$done-")
                }
                val code = conn.responseCode
                val append = code == 206
                if (code !in 200..299) throw IOException("응답 $code")
                if (!append && done > 0) dest.delete() // 이어받기를 안 받아주면 처음부터
                val len = conn.contentLengthLong
                if (len > 0) total = if (append) done + len else len
                conn.inputStream.use { input ->
                    FileOutputStream(dest, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var got = if (append) done else 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            if (got - last >= 256 * 1024) {
                                last = got
                                onProgress(got, total)
                            }
                        }
                        onProgress(got, total)
                    }
                }
                if (total <= 0 || dest.length() >= total) return
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(if (attempt < 5) 1_000L else 3_000L)
            } finally {
                conn?.disconnect()
            }
        }
        throw IOException("여러 번 다시 받아도 끝까지 받지 못했어요 (${lastError?.message ?: "연결 끊김"})")
    }
}
