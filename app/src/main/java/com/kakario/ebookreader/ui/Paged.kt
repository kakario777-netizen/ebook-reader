package com.kakario.ebookreader.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
