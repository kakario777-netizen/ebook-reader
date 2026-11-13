package com.kakario.ebookreader.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 쪽 넘기기에 필요한 것: 지금 쪽 / 전체 쪽 계산. */
class PageInfo(val state: ScrollableState, val pages: () -> Pair<Int, Int>)

@Composable
fun rememberPageInfo(state: LazyGridState): PageInfo = remember(state) {
    PageInfo(state) {
        val li = state.layoutInfo
        val per = li.visibleItemsInfo.size.coerceAtLeast(1)
        val total = ((li.totalItemsCount + per - 1) / per).coerceAtLeast(1)
        val cur = if (!state.canScrollForward) total else (state.firstVisibleItemIndex / per + 1).coerceIn(1, total)
        cur to total
    }
}

@Composable
fun rememberPageInfo(state: LazyListState): PageInfo = remember(state) {
    PageInfo(state) {
        val li = state.layoutInfo
        val per = li.visibleItemsInfo.size.coerceAtLeast(1)
        val total = ((li.totalItemsCount + per - 1) / per).coerceAtLeast(1)
        val cur = if (!state.canScrollForward) total else (state.firstVisibleItemIndex / per + 1).coerceIn(1, total)
        cur to total
    }
}

/**
 * 이잉크용 쪽 넘김 목록: 미끄러지는 스크롤 대신 한 화면씩 '뚝' 넘긴다.
 *  - 아래 ‹ 이전 · 쪽수 · 다음 › 버튼
 *  - 화면을 위/왼쪽으로 쓸면 다음 쪽, 아래/오른쪽으로 쓸면 이전 쪽
 * content 안의 목록은 userScrollEnabled = false 로 둔다.
 */
@Composable
fun PagedArea(info: PageInfo, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(0) }
    val pages by remember(info) { derivedStateOf { info.pages() } }

    fun turn(dir: Int) {
        if (height <= 0) return
        scope.launch { info.state.scrollBy(dir * (height - 8f)) }
    }

    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .pointerInput(info) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y },
                        onDragEnd = {
                            val limit = 40.dp.toPx()
                            val main = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) dx else dy
                            if (kotlin.math.abs(main) > limit) turn(if (main < 0) 1 else -1)
                        },
                    )
                },
        ) { content() }
        if (pages.second > 1) {
            Line()
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("‹  이전", Modifier.weight(1f)) { turn(-1) }
                Text(
                    "${pages.first} / ${pages.second}",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextAction("다음  ›", Modifier.weight(1f)) { turn(1) }
            }
        }
    }
}

/** 화면을 쓸어서 쪽 넘기기 (위·왼쪽 = 다음, 아래·오른쪽 = 이전). */
fun Modifier.swipePages(key: Any?, onTurn: (Int) -> Unit): Modifier = pointerInput(key) {
    var dx = 0f
    var dy = 0f
    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f },
        onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y },
        onDragEnd = {
            val limit = 40.dp.toPx()
            val main = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) dx else dy
            if (kotlin.math.abs(main) > limit) onTurn(if (main < 0) 1 else -1)
        },
    )
}

/** 아래 쪽 넘김 줄: ‹ 이전 · n / m · 다음 › */
@Composable
fun PageBar(page: Int, total: Int, onTurn: (Int) -> Unit) {
    Column {
        Line()
        Row(Modifier.fillMaxWidth().height(PAGE_BAR_H - 1.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("‹  이전", Modifier.weight(1f)) { onTurn(-1) }
            Text(
                "$page / $total",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextAction("다음  ›", Modifier.weight(1f)) { onTurn(1) }
        }
    }
}

private val PAGE_BAR_H = 48.dp

/**
 * 화면에 딱 맞는 격자: 한 줄 칸 수(columns)만 정하면 칸 모양(표지 비율 + 제목 두 줄)에 맞춰
 * 이 화면에 들어가는 줄 수를 계산해서, 잘리는 줄 없이 꽉 채운다. 남는 높이는 줄마다 고르게 나눈다.
 * 넘치면 쪽으로 나누고 아래에 쪽 넘김 줄이 자동으로 생긴다.
 */
@Composable
fun <T> PagedGrid(
    items: List<T>,
    columns: Int,
    pageKey: Any?,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    cell: @Composable (T) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = columns.coerceAtLeast(1)
        val cellW = maxWidth / cols
        val compact = cols >= 5
        val padH = if (compact) 6.dp else 10.dp
        val padV = if (compact) 6.dp else 10.dp
        val natural = (cellW - padH * 2) / 0.72f + GRID_TITLE_H + padV * 2
        fun rowsFor(h: androidx.compose.ui.unit.Dp) = (h / natural).toInt().coerceAtLeast(1)
        val needBar = items.size > rowsFor(maxHeight) * cols
        val avail = if (needBar) maxHeight - PAGE_BAR_H else maxHeight
        val rows = rowsFor(avail)
        val per = rows * cols
        val total = ((items.size + per - 1) / per).coerceAtLeast(1)
        var page by remember(pageKey) { mutableIntStateOf(0) }
        if (page >= total) page = total - 1
        val turn: (Int) -> Unit = { d -> page = (page + d).coerceIn(0, total - 1) }

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().height(avail).swipePages(total) { turn(it) }) {
                val start = page * per
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        for (c in 0 until cols) {
                            val item = items.getOrNull(start + r * cols + c)
                            Box(Modifier.weight(1f).fillMaxSize().padding(horizontal = padH, vertical = padV)) {
                                if (item != null) androidx.compose.runtime.key(key(item)) { cell(item) }
                            }
                        }
                    }
                }
            }
            if (needBar) PageBar(page + 1, total, turn)
        }
    }
}

/** 격자 칸 아래 제목 자리 (두 줄). */
val GRID_TITLE_H = 40.dp

/** 격자 칸 하나: 위에 표지 모양(비율 유지, 칸 안에서 최대), 아래 제목 두 줄. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GridCell(
    title: String,
    bold: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    tile: @Composable (Modifier) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            tile(Modifier.aspectRatio(0.72f, matchHeightConstraintsFirst = true))
        }
        Text(
            title,
            color = Ink,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(GRID_TITLE_H).padding(top = 6.dp),
        )
    }
}
