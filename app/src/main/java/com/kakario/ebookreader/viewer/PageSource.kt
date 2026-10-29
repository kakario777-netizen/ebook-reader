package com.kakario.ebookreader.viewer

import android.graphics.Bitmap

/** 그림 페이지로 된 책 (만화 ZIP, PDF) — 뷰어는 이 모양만 알면 된다. */
interface PageSource {
    /** 책을 연다. FTP PDF처럼 먼저 받아야 하면 onProgress로 진행을 알린다. 페이지 수를 돌려준다. */
    suspend fun open(onProgress: (done: Long, total: Long) -> Unit): Int

    fun cached(index: Int): Bitmap?

    /** 화면에 띄울 페이지 그림. */
    suspend fun page(index: Int): Bitmap

    /** 앞으로 볼 페이지를 미리 받아둔다 (실패해도 조용히). */
    fun prefetch(index: Int)

    /** 바로 다음에 볼 페이지는 그림으로까지 만들어둔다. */
    fun prepare(index: Int)

    fun close()
}
