package com.kakario.ebookreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.thumb.ThumbnailLoader

/** 앱 어디서나 같은 표지 로더를 쓰도록 전달. */
val LocalThumbnails = compositionLocalOf<ThumbnailLoader?> { null }

/** 표지 그림을 불러온다 (없으면 null). 화면 밖으로 스크롤되면 만들기를 멈춘다. */
@Composable
fun rememberCover(origin: Origin, entry: FileEntry): Bitmap? {
    val loader = LocalThumbnails.current
    val key = loader?.keyFor(origin, entry)
    val generation = loader?.generation ?: 0
    val bitmap by produceState<Bitmap?>(initialValue = key?.let { loader?.cached(it) }, key, generation) {
        if (loader == null || key == null || !entry.hasCover) return@produceState
        val fresh = loader.cached(key) ?: loader.load(origin, entry)
        if (fresh != null || loader.cached(key) == null) value = fresh
    }
    return bitmap
}

/**
 * 책 모양 표지 — 검은 선으로 그린 양장본.
 *  - 왼쪽: 책등 접히는 홈 (세로 두 줄)
 *  - 오른쪽·아래: 겹쳐 보이는 종이 결
 *  - 표지 그림이 있으면 그림, 없으면 테두리 안에 제목과 형식(TXT 등)
 * title이 null이면 목록용 작은 크기 (제목 없이 형식만).
 */
@Composable
fun BookFace(origin: Origin, entry: FileEntry, modifier: Modifier = Modifier, title: String? = null, compact: Boolean = false) {
    val cover = rememberCover(origin, entry)
    val big = title != null
    val edge: Dp = if (big) 5.dp else 3.dp
    val spine: Dp = if (big) 9.dp else 5.dp

    Box(modifier) {
        // 표지 뒤로 겹쳐 보이는 종이 묶음
        Canvas(Modifier.matchParentSize()) {
            val e = edge.toPx()
            val s = 1.dp.toPx()
            val w = size.width
            val h = size.height
            for (i in 1..3) {
                val d = e * i / 3f
                val a = if (i == 3) 1f else 0.6f
                // 오른쪽 세로 종이 결
                drawLine(Ink.copy(alpha = a), Offset(w - e + d, d + s), Offset(w - e + d, h - e + d), strokeWidth = s)
                // 아래쪽 가로 종이 결
                drawLine(Ink.copy(alpha = a), Offset(d + s, h - e + d), Offset(w - e + d, h - e + d), strokeWidth = s)
            }
        }

        // 표지
        Box(
            Modifier
                .padding(end = edge, bottom = edge)
                .fillMaxSize()
                .background(Paper)
                .border(if (big) 1.5.dp else 1.dp, Ink),
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (big) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = spine + 6.dp, end = 7.dp, top = 12.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 제목 칸: 위아래 두 줄 장식선 사이
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        title.orEmpty(),
                        color = Ink,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink))
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
                    Spacer(Modifier.weight(1f))
                    Text(
                        entry.kind?.label ?: "",
                        color = Ink,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                    )
                }
            } else {
                Text(
                    entry.kind?.label ?: "",
                    color = Ink,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.Center).padding(start = spine - 2.dp),
                )
            }

            // 책등 홈: 진한 줄 + (그림 위에서도 보이게) 흰 줄
            Canvas(Modifier.matchParentSize()) {
                val x = spine.toPx()
                val s = 1.dp.toPx()
                if (cover != null) {
                    drawLine(Paper.copy(alpha = 0.75f), Offset(x + s * 1.2f, 0f), Offset(x + s * 1.2f, size.height), strokeWidth = s)
                }
                drawLine(Ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = s)
                drawLine(Ink.copy(alpha = 0.5f), Offset(x - s * 2f, 0f), Offset(x - s * 2f, size.height), strokeWidth = s * 0.6f)
            }
        }
    }
}
