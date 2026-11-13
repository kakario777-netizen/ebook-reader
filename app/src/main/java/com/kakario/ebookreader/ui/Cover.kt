package com.kakario.ebookreader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * 책장 칸 (sam 기본 서재 모양):
 *  - 바깥 실선 테두리 안에 점선 테두리 (오른쪽 아래 모서리를 비스듬히 깎음)
 *  - 오른쪽 위: 아래가 둥근 탭에 진행률 (0% · 35% · 완독)
 *  - 왼쪽 아래: 오른쪽이 둥근 탭에 형식 (TXT · EPUB · PDF · ZIP)
 *  - 가운데: 표지 그림이 있으면 그림, 없으면 선으로 그린 펼친 책
 */
@Composable
fun ShelfTile(origin: Origin, entry: FileEntry, badge: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val cover = rememberCover(origin, entry)
    val labelStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    Box(modifier) {
        // 표지 그림 (점선 안쪽)
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().padding(if (compact) 5.dp else 8.dp),
            )
        }
        Canvas(Modifier.matchParentSize()) {
            val s = 1.dp.toPx()
            val w = size.width
            val h = size.height
            // 바깥 실선
            drawRect(Ink, topLeft = Offset(s / 2, s / 2), size = Size(w - s, h - s), style = Stroke(1.2f * s))
            // 안쪽 점선 (오른쪽 아래 모서리 깎음)
            val m = (if (compact) 4 else 7) * s
            val cut = (if (compact) 7 else 12) * s
            val path = Path().apply {
                moveTo(m, m)
                lineTo(w - m, m)
                lineTo(w - m, h - m - cut)
                lineTo(w - m - cut, h - m)
                lineTo(m, h - m)
                close()
            }
            drawPath(path, Ink, style = Stroke(0.8f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * s, 2 * s))))
            if (cover == null) {
                // 가운데 펼친 책
                val bw = w * 0.30f
                val bh = bw * 0.62f
                val cx = w / 2
                val cy = h / 2
                val left = Path().apply {
                    moveTo(cx, cy - bh / 2 + 2 * s)
                    quadraticBezierTo(cx - bw / 4, cy - bh / 2 - 2 * s, cx - bw / 2, cy - bh / 2)
                    lineTo(cx - bw / 2, cy + bh / 2)
                    quadraticBezierTo(cx - bw / 4, cy + bh / 2 - 2 * s, cx, cy + bh / 2 + 2 * s)
                    close()
                }
                val right = Path().apply {
                    moveTo(cx, cy - bh / 2 + 2 * s)
                    quadraticBezierTo(cx + bw / 4, cy - bh / 2 - 2 * s, cx + bw / 2, cy - bh / 2)
                    lineTo(cx + bw / 2, cy + bh / 2)
                    quadraticBezierTo(cx + bw / 4, cy + bh / 2 - 2 * s, cx, cy + bh / 2 + 2 * s)
                    close()
                }
                drawPath(left, Ink, style = Stroke(1.2f * s))
                drawPath(right, Ink, style = Stroke(1.2f * s))
                for (k in 1..4) {
                    val y = cy - bh / 2 + bh * k / 5.5f
                    drawLine(Ink, Offset(cx - bw * 0.40f, y), Offset(cx - bw * 0.10f, y), strokeWidth = s)
                    drawLine(Ink, Offset(cx + bw * 0.10f, y), Offset(cx + bw * 0.40f, y), strokeWidth = s)
                }
            }
        }
        // 오른쪽 위 진행률 탭 (아래가 둥근)
        Text(
            badge,
            color = Ink,
            style = labelStyle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = if (compact) 4.dp else 8.dp)
                .background(Paper, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .border(1.dp, Ink, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(horizontal = if (compact) 5.dp else 9.dp, vertical = if (compact) 1.dp else 3.dp),
        )
        // 왼쪽 아래 형식 탭 (오른쪽이 둥근)
        Text(
            entry.kind?.label ?: "",
            color = Ink,
            style = labelStyle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Paper, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, Ink, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .padding(horizontal = if (compact) 5.dp else 9.dp, vertical = if (compact) 1.dp else 3.dp),
        )
    }
}

/** 격자 보기의 폴더 칸: 책 칸과 같은 크기, 선으로 그린 폴더 모양. */
@Composable
fun FolderTile(favorite: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val s = 1.dp.toPx()
            val w = size.width
            val h = size.height
            // 바깥 실선 (책 칸과 같은 틀)
            drawRect(Ink, topLeft = Offset(s / 2, s / 2), size = Size(w - s, h - s), style = Stroke(1.2f * s))
            // 가운데 폴더: 탭이 달린 뒷판 + 앞판
            val fw = w * 0.56f
            val fh = fw * 0.72f
            val l = (w - fw) / 2
            val t = (h - fh) / 2
            val tabW = fw * 0.38f
            val tabH = fh * 0.14f
            val back = Path().apply {
                moveTo(l, t + tabH)
                lineTo(l, t + fh)
                lineTo(l + fw, t + fh)
                lineTo(l + fw, t + tabH)
                lineTo(l + tabW + tabH, t + tabH)
                lineTo(l + tabW, t)
                lineTo(l, t)
                close()
            }
            drawPath(back, Ink, style = Stroke(1.4f * s))
            drawLine(Ink, Offset(l, t + tabH * 2.2f), Offset(l + fw, t + tabH * 2.2f), strokeWidth = s)
        }
        if (favorite) {
            Text(
                "★",
                color = Ink,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(if (compact) 4.dp else 8.dp),
            )
        }
    }
}
