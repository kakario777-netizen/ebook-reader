package com.kakario.ebookreader.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.book.BookCache
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.data.AppSettings
import com.kakario.ebookreader.data.ComicLayout
import com.kakario.ebookreader.data.ProgressStore
import com.kakario.ebookreader.model.BookKind
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.UserFacingException
import com.kakario.ebookreader.model.titleOf
import com.kakario.ebookreader.viewer.ComicBook
import com.kakario.ebookreader.viewer.PageKeys
import com.kakario.ebookreader.viewer.PageSource
import com.kakario.ebookreader.viewer.PdfBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 그림 페이지 책 보기 (만화 ZIP · PDF).
 *  - 만화: 설정에 따라 '두 장 · 가로 고정' 또는 '한 장 · 세로 고정'으로 화면을 강제로 돌린다.
 *  - PDF: 한 장씩, 화면 방향은 폰을 따른다.
 */
@Composable
fun ImageViewerScreen(
    origin: Origin,
    entry: FileEntry,
    nextEntry: FileEntry?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenNext: (FileEntry) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val isComic = entry.kind == BookKind.COMIC
    val maxSide = remember {
        val dm = context.resources.displayMetrics
        val longest = maxOf(dm.widthPixels, dm.heightPixels)
        if (isComic) (longest * 1.5f).roundToInt().coerceIn(1600, 3000)
        else (longest * 1.25f).roundToInt().coerceIn(1200, 2400)
    }
    val progress = remember { ProgressStore.get(context) }
    val book: PageSource = remember(origin, entry.path) {
        if (isComic) ComicBook(origin, entry, maxSide, settings.ftpConnections)
        else PdfBook(origin, entry, BookCache.get(context), maxSide)
    }
    DisposableEffect(book) {
        onDispose {
            book.close()
            progress.bump() // 목록 진행률 다시 그리기
        }
    }

    // ── 만화만: 화면 방향 강제 (두 장 = 가로, 한 장 = 세로). 닫으면 원래대로.
    val activity = remember { context.findActivity() }
    DisposableEffect(isComic, settings.comicLayout) {
        if (isComic) {
            activity?.requestedOrientation = when (settings.comicLayout) {
                ComicLayout.DOUBLE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ComicLayout.SINGLE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose {
            if (isComic) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var pageCount by remember(book) { mutableStateOf<Int?>(null) }
    var openError by remember(book) { mutableStateOf<String?>(null) }
    var downloaded by remember(book) { mutableLongStateOf(-1L) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(book, retry) {
        openError = null
        try {
            pageCount = book.open { done, _ -> downloaded = done }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserFacingException) {
            openError = e.message
        } catch (e: Exception) {
            openError = "열 수 없어요: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    ImmersiveMode()
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        val n = pageCount
        val err = openError
        when {
            err != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(err, color = Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row {
                    LineButton("다시 시도") { retry++ }
                    Spacer(Modifier.width(12.dp))
                    LineButton("닫기", onClick = onClose)
                }
            }

            n == null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spinner()
                Spacer(Modifier.height(14.dp))
                Text(titleOf(entry.name), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (downloaded >= 0 && entry.size > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "받는 중 ${(downloaded * 100 / entry.size).coerceIn(0, 100)}%",
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 다음 권으로 넘어가면 페이지 상태를 새로 시작하도록 책마다 따로 둔다
            else -> key(entry.path) {
                BookPager(
                    bookKey = progress.keyFor(origin.tag, entry),
                    entry = entry,
                    book = book,
                    pageCount = n,
                    double = isComic && settings.comicLayout == ComicLayout.DOUBLE_LANDSCAPE,
                    isComic = isComic,
                    nextEntry = nextEntry,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onOpenNext = onOpenNext,
                    onClose = onClose,
                )
            }
        }
    }
}

/** 한 화면에 보여줄 페이지 묶음. 두 장 보기는 왼쪽부터 2장씩 (표지 따로면 첫 장만 혼자). */
private fun buildSpreads(pageCount: Int, double: Boolean, coverAlone: Boolean): List<List<Int>> {
    if (!double) return (0 until pageCount).map { listOf(it) }
    val result = mutableListOf<List<Int>>()
    var i = 0
    if (coverAlone && pageCount > 0) {
        result += listOf(0)
        i = 1
    }
    while (i < pageCount) {
        result += if (i + 1 < pageCount) listOf(i, i + 1) else listOf(i)
        i += 2
    }
    return result
}

@Composable
private fun BookPager(
    bookKey: String,
    entry: FileEntry,
    book: PageSource,
    pageCount: Int,
    double: Boolean,
    isComic: Boolean,
    nextEntry: FileEntry?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenNext: (FileEntry) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val progress = remember { ProgressStore.get(context) }

    // 지금 보고 있는 페이지 번호 — 한 장/두 장을 바꿔도 이 페이지를 유지한다.
    var currentPage by remember {
        mutableIntStateOf(progress.get(bookKey).position.coerceIn(0, pageCount - 1))
    }
    var overlay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    key(double, settings.coverAlone) {
        val spreads = remember(pageCount) { buildSpreads(pageCount, double, settings.coverAlone) }
        val endIndex = spreads.size // 마지막 장 다음의 '다 읽었어요' 화면
        val pagerState = rememberPagerState(
            initialPage = spreads.indexOfFirst { currentPage in it }.coerceAtLeast(0)
        ) { spreads.size + 1 }
        var zoomed by remember { mutableStateOf(false) }

        LaunchedEffect(pagerState.currentPage) {
            val s = pagerState.currentPage
            zoomed = false
            if (s < spreads.size) {
                currentPage = spreads[s].first()
                val last = spreads[s].last()
                progress.set(bookKey, last, pageCount, done = last >= pageCount - 1)
                // 바로 다음 화면은 그림까지 준비, 그 뒤 페이지들은 원본을 미리 받아둔다
                spreads.getOrNull(s + 1)?.forEach { book.prepare(it) }
                for (i in last + 1..last + settings.prefetchPages) book.prefetch(i)
                spreads.getOrNull(s - 1)?.forEach { book.prefetch(it) }
            } else {
                progress.set(bookKey, pageCount - 1, pageCount, done = true)
            }
        }

        fun goToSpread(index: Int) {
            if (index !in 0..endIndex) return
            scope.launch {
                if (settings.slideAnimation) pagerState.animateScrollToPage(index)
                else pagerState.scrollToPage(index)
            }
        }

        // 볼륨 버튼 / 페이지 터너
        val goTo by rememberUpdatedState<(Int) -> Unit>({ goToSpread(it) })
        val volumeNow by rememberUpdatedState(settings.volumeKeys)
        DisposableEffect(pagerState) {
            val handler: (KeyEvent) -> Boolean = { e ->
                val dir = PageKeys.direction(e.keyCode, false, volumeNow)
                if (dir == 0) {
                    false
                } else {
                    if (e.action == KeyEvent.ACTION_DOWN) goTo(pagerState.currentPage + dir)
                    true
                }
            }
            PageKeys.handler = handler
            onDispose { if (PageKeys.handler === handler) PageKeys.handler = null }
        }

        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                // 효과 없음: 손가락으로 끌어도 화면이 따라오지 않고, 놓는 순간 바로 바뀐다
                userScrollEnabled = !zoomed && settings.slideAnimation,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (settings.slideAnimation || zoomed) Modifier
                        else Modifier.pointerInput(Unit) {
                            val threshold = 48.dp.toPx()
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onHorizontalDrag = { change, amount ->
                                    dx += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (kotlin.math.abs(dx) > threshold) {
                                        // 왼쪽으로 밀면 다음 장
                                        goToSpread(pagerState.currentPage + if (dx < 0) 1 else -1)
                                    }
                                },
                            )
                        }
                    ),
            ) { index ->
                if (index == endIndex) {
                    EndCard(
                        title = titleOf(entry.name),
                        nextTitle = nextEntry?.let { titleOf(it.name) },
                        onOpenNext = { nextEntry?.let(onOpenNext) },
                        onClose = onClose,
                        onBack = { goToSpread(endIndex - 1) },
                    )
                } else {
                    SpreadView(
                        book = book,
                        pages = spreads[index],
                        onZoomChange = { if (index == pagerState.currentPage) zoomed = it },
                        onTap = { x, width ->
                            when {
                                x < width / 3f -> goToSpread(pagerState.currentPage - 1)
                                x > width * 2f / 3f -> goToSpread(pagerState.currentPage + 1)
                                else -> overlay = !overlay
                            }
                        },
                    )
                }
            }

            if (settings.showInfoBar && !overlay && pagerState.currentPage < endIndex) {
                val last = spreads[pagerState.currentPage].last()
                val remaining = (pageCount - 1 - last).coerceAtLeast(0)
                val percent = ((last + 1) * 100 / pageCount.coerceAtLeast(1)).coerceIn(0, 100)
                InfoBar(
                    right = if (remaining == 0) "마지막 페이지 · $percent%" else "남은 ${remaining}쪽 · $percent%",
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            ImageOverlay(
                visible = overlay && pagerState.currentPage < endIndex,
                title = titleOf(entry.name),
                spread = spreads.getOrNull(pagerState.currentPage) ?: listOf(pageCount - 1),
                pageCount = pageCount,
                isComic = isComic,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onJump = { page ->
                    scope.launch { pagerState.scrollToPage(spreads.indexOfFirst { page in it }.coerceAtLeast(0)) }
                },
                onClose = onClose,
            )
        }
    }
}

/** 가운데를 탭하면 나오는 위·아래 메뉴 (흰 바탕, 선으로 구분). */
@Composable
private fun ImageOverlay(
    visible: Boolean,
    title: String,
    spread: List<Int>,
    pageCount: Int,
    isComic: Boolean,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onJump: (page: Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(Paper)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "닫기", tint = Ink)
                    }
                    Text(
                        title,
                        color = Ink,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Line()
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(Paper)) {
                Line()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val label = dragging?.let { "${it.roundToInt()}" }
                        ?: spread.joinToString("-") { "${it + 1}" }
                    Text("$label / $pageCount", color = Ink, style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = dragging ?: (spread.first() + 1).toFloat(),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { onJump(it.roundToInt() - 1) }
                            dragging = null
                        },
                        valueRange = 1f..pageCount.coerceAtLeast(2).toFloat(),
                        colors = LineSliderColors(),
                    )
                    if (isComic) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ComicLayout.entries.forEach { layout ->
                                LineButton(
                                    (if (settings.comicLayout == layout) "● " else "") + layout.label,
                                    bold = settings.comicLayout == layout,
                                ) { onSettingsChange(settings.copy(comicLayout = layout)) }
                            }
                        }
                        if (settings.comicLayout == ComicLayout.DOUBLE_LANDSCAPE) {
                            Spacer(Modifier.height(8.dp))
                            LineButton((if (settings.coverAlone) "✓ " else "") + "표지 따로") {
                                onSettingsChange(settings.copy(coverAlone = !settings.coverAlone))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun LineSliderColors() = SliderDefaults.colors(
    thumbColor = Ink,
    activeTrackColor = Ink,
    inactiveTrackColor = Ink.copy(alpha = 0.15f),
    activeTickColor = Ink,
    inactiveTickColor = Ink,
)

/** 마지막 장 다음 화면: 다음 권 열기 / 닫기. */
@Composable
fun EndCard(
    title: String,
    nextTitle: String?,
    onOpenNext: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("다 읽었어요", color = Ink, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            if (nextTitle != null) {
                Text("다음 권", color = Ink, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    nextTitle,
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                LineButton("다음 권 보기", bold = true, onClick = onOpenNext)
            } else {
                Text("이 폴더의 마지막 권이에요", color = Ink)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                LineButton("마지막 장으로", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                LineButton("목록으로", onClick = onClose)
            }
        }
    }
}

/** 한 화면(한 장 또는 두 장): 불러오기, 핀치/더블탭 확대, 탭 영역 넘기기. */
@Composable
private fun SpreadView(
    book: PageSource,
    pages: List<Int>,
    onZoomChange: (Boolean) -> Unit,
    onTap: (x: Float, width: Float) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        if (s <= 1f) return Offset.Zero
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(pages) {
                detectTapGestures(
                    onTap = { pos -> if (scale <= 1f) onTap(pos.x, size.width.toFloat()) },
                    onDoubleTap = { pos ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChange(false)
                        } else {
                            val s = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = s
                            offset = clamp((pos - center) * (1f - s), s)
                            onZoomChange(true)
                        }
                    },
                )
            }
            .pointerInput(pages) {
                // 두 손가락이면 확대/축소, 확대된 상태면 한 손가락으로 이동.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            var newOffset = if (centroid.isUsable()) {
                                centroid - center - (centroid - center - offset) * (newScale / scale)
                            } else {
                                offset
                            }
                            newOffset += pan
                            scale = newScale
                            offset = clamp(newOffset, newScale)
                            onZoomChange(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            if (pages.size == 1) {
                PageImage(book, pages[0], Alignment.Center, Modifier.weight(1f))
            } else {
                // 두 장: 왼쪽부터 읽는다 — 앞 페이지가 왼쪽. 가운데로 붙여서 펼친 책처럼.
                PageImage(book, pages[0], Alignment.CenterEnd, Modifier.weight(1f))
                PageImage(book, pages[1], Alignment.CenterStart, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PageImage(book: PageSource, index: Int, alignment: Alignment, modifier: Modifier) {
    var reload by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val bitmap by produceState<Bitmap?>(book.cached(index), index, reload) {
        if (value != null) return@produceState
        error = null
        try {
            value = book.page(index)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserFacingException) {
            error = e.message
        } catch (e: Exception) {
            error = "페이지를 불러오지 못했어요: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        val b = bitmap
        val err = error
        when {
            b != null -> Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )

            err != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(err, color = Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                LineButton("다시 시도") { reload++ }
            }

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner()
                Spacer(Modifier.height(8.dp))
                Text("${index + 1}", color = Ink)
            }
        }
    }
}

private fun Offset.isUsable(): Boolean = this != Offset.Unspecified && !x.isNaN() && !y.isNaN()
