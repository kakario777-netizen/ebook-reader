package com.kakario.ebookreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.thumb.ThumbnailLoader

/** 앱 어디서나 같은 표지 로더를 쓰도록 전달. */
val LocalThumbnails = compositionLocalOf<ThumbnailLoader?> { null }

/**
 * 목록의 작은 표지. 표지가 없거나 만드는 중에는 선 네모 안에 형식 이름(TXT·PDF…)만.
 * 화면 밖으로 스크롤되면 만들기를 자동으로 멈춘다.
 */
@Composable
fun SmallCover(origin: Origin, entry: FileEntry, modifier: Modifier = Modifier) {
    val loader = LocalThumbnails.current
    val key = loader?.keyFor(origin, entry)
    val generation = loader?.generation ?: 0
    val bitmap by produceState<Bitmap?>(initialValue = key?.let { loader?.cached(it) }, key, generation) {
        if (loader == null || key == null || !entry.hasCover) return@produceState
        val fresh = loader.cached(key) ?: loader.load(origin, entry)
        if (fresh != null || loader.cached(key) == null) value = fresh
    }

    Box(modifier.border(1.dp, Ink), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(entry.kind?.label ?: "", color = Ink, style = MaterialTheme.typography.labelSmall)
        }
    }
}
