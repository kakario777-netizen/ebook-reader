package com.kakario.ebookreader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.kakario.ebookreader.viewer.PageSpan
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kakario.ebookreader.book.BookCache
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.book.TextContent
import com.kakario.ebookreader.data.AppSettings
import com.kakario.ebookreader.data.ProgressStore
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.BookKind
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.UserFacingException
import com.kakario.ebookreader.model.titleOf
import com.kakario.ebookreader.viewer.PageKeys
import com.kakario.ebookreader.viewer.Paginator
import com.kakario.ebookreader.viewer.SectionPages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 지금 보는 곳: 몇 번째 구간의 몇 번째 페이지. */
private data class Loc(val section: Int, val page: Int)

/**
 * 글자 책 보기 (TXT · EPUB).
 * 흰 바탕에 검은 글자만. 탭 왼쪽/오른쪽 = 이전/다음 페이지, 가운데 = 메뉴.
 * 넘김 효과 없이 바로 바뀌고, 설정한 장 수마다 화면을 새로고침해 이잉크 잔상을 지운다.
 */
@Composable
fun TextReaderScreen(
    origin: Origin,
    entry: FileEntry,
    nextEntry: FileEntry?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenNext: (FileEntry) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val progress = remember { ProgressStore.get(context) }
    val cache = remember { BookCache.get(context) }

    var content by remember { mutableStateOf<TextContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloaded by remember { mutableLongStateOf(-1L) }
    var retry by remember { mutableIntStateOf(0) }

    // TXT는 '잘린 문장 잇기' 설정이 바뀌면 다시 읽는다
    val reflowKey = if (entry.kind == BookKind.TXT) "${settings.txtReflow}/${settings.txtChapters}" else null
    LaunchedEffect(retry, reflowKey) {
        error = null
        var ftp: FtpBrowser? = null
        try {
            val file = cache.fetch(
                origin, entry,
                ftp = { ftp ?: FtpBrowser(origin.server!!).also { ftp = it } },
                onProgress = { done, _ -> downloaded = done },
            )
            val c = withContext(Dispatchers.Default) {
                if (entry.kind == BookKind.EPUB) {
                    TextContent.loadEpub(file)
                        ?: throw UserFacingException("EPUB을 읽을 수 없어요.\n파일이 손상됐거나 형식이 맞지 않을 수 있어요.")
                } else {
                    TextContent.loadTxt(file, settings.txtReflow, settings.txtChapters)
                }
            }
            if (c.sections.isEmpty()) {
                c.close()
                throw UserFacingException("이 책에는 읽을 글자가 없어요.")
            }
            val old = content
            content = c
            old?.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserFacingException) {
            error = e.message
        } catch (e: OutOfMemoryError) {
            error = "책이 너무 커서 열 수 없어요."
        } catch (e: Exception) {
            error = "열 수 없어요: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            ftp?.let { f -> withContext(NonCancellable) { runCatching { f.close() } } }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            content?.close()
            progress.bump()
        }
    }

    ImmersiveMode()
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Paper)) {
        val c = content
        val err = error
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

            c == null -> Column(
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

            else -> key(c) { TextPages(
                content = c,
                origin = origin,
                entry = entry,
                bookKey = progress.keyFor(origin.tag, entry),
                title = c.title ?: titleOf(entry.name),
                fileTitle = titleOf(entry.name),
                nextEntry = nextEntry,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onOpenNext = onOpenNext,
                onClose = onClose,
            ) }
        }
    }
}

@Composable
private fun TextPages(
    content: TextContent,
    origin: Origin,
    entry: FileEntry,
    bookKey: String,
    title: String,
    fileTitle: String,
    nextEntry: FileEntry?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenNext: (FileEntry) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val progress = remember { ProgressStore.get(context) }
    val refresher = LocalRefresher.current
    val shelf = LocalShelf.current
    val scope = rememberCoroutineScope()
    val total = content.totalChars.coerceAtLeast(1)

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // 여백: 아래쪽은 시간·진행률 줄 자리까지
    val topPx = with(density) { settings.marginTop.dp.roundToPx() }
    val bottomPx = with(density) { (settings.marginBottom + if (settings.showInfoBar) 22 else 0).dp.roundToPx() }
    val leftPx = with(density) { settings.marginLeft.dp.roundToPx() }
    val rightPx = with(density) { settings.marginRight.dp.roundToPx() }
    val strokePx = with(density) { (settings.strokeBoost * 0.22f).dp.toPx() }

    val style = remember(
        settings.fontSize, settings.lineSpacing, settings.readerFont, settings.fontWeight,
        settings.letterSpacing, settings.indent, settings.justify,
    ) {
        TextStyle(
            color = Ink,
            fontFamily = AppFonts.forReader(settings.readerFont),
            fontWeight = FontWeight(settings.fontWeight),
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.lineSpacing / 10f).em,
            letterSpacing = (settings.letterSpacing / 100f).em,
            textIndent = if (settings.indent > 0) TextIndent(firstLine = settings.indent.em) else TextIndent.None,
            textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            lineBreak = LineBreak.Simple,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }
    val paginator = remember(viewSize, style, leftPx, rightPx, topPx, bottomPx) {
        if (viewSize.width <= 0 || viewSize.height <= 0) {
            null
        } else {
            Paginator(
                content = content,
                measurer = TextMeasurer(fontResolver, density, layoutDirection, cacheSize = 0),
                style = style,
                widthPx = (viewSize.width - leftPx - rightPx).coerceAtLeast(40),
                heightPx = (viewSize.height - topPx - bottomPx).coerceAtLeast(40),
            )
        }
    }

    // 책 처음부터 센 글자 위치 — 글자 크기·화면 방향이 바뀌어도 이 위치에서 이어 읽는다
    var position by remember {
        val saved = progress.get(bookKey)
        // 잘린 문장 잇기를 켜고 끄면 글자 수가 달라진다 → 같은 비율 위치로 옮긴다
        val pos = if (saved.total > 0 && saved.total != total) {
            (saved.position.toLong() * total / saved.total).toInt()
        } else {
            saved.position
        }
        mutableIntStateOf(pos.coerceIn(0, total - 1))
    }
    var loc by remember { mutableStateOf<Loc?>(null) }
    var atEnd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }

    fun isLast(p: Paginator, l: Loc): Boolean {
        if (l.section < p.sectionCount - 1) return false
        val sp = p.cached(l.section) ?: return false
        return l.page >= sp.count - 1
    }

    fun prefetchAround(p: Paginator, s: Int) {
        // 앞뒤 구간은 미리 나눠둔다 (넘길 때 기다리지 않게)
        scope.launch {
            runCatching {
                if (s + 1 < p.sectionCount) p.get(s + 1)
                if (s > 0) p.get(s - 1)
            }
        }
    }

    /** 한 페이지에서 글자가 있는 줄들의 화면 위치 (가벼운 새로고침에 쓴다). */
    fun pageRects(p: Paginator, l: Loc?): List<Rect> {
        if (l == null) return emptyList()
        val pad = strokePx + 2f
        return when (val sp = p.cached(l.section)) {
            is SectionPages.Text -> {
                val span = sp.pages.getOrNull(l.page) ?: return emptyList()
                (span.firstLine..span.lastLine).mapNotNull { i ->
                    // 빈 줄은 건너뛴다
                    if (sp.layout.getLineRight(i) - sp.layout.getLineLeft(i) < 1f) return@mapNotNull null
                    Rect(
                        left = leftPx + sp.layout.getLineLeft(i) - pad,
                        top = topPx + sp.layout.getLineTop(i) - span.top,
                        right = leftPx + sp.layout.getLineRight(i) + pad,
                        bottom = topPx + sp.layout.getLineBottom(i) - span.top,
                    )
                }
            }
            is SectionPages.Image -> listOf(Rect(0f, 0f, viewSize.width.toFloat(), viewSize.height.toFloat()))
            null -> emptyList()
        }
    }

    fun show(p: Paginator, l: Loc) {
        val sp = p.cached(l.section) ?: return
        // 앞 페이지와 새 페이지 글자 줄을 함께 칠해야 앞 글자 흔적이 남지 않는다
        val rects = pageRects(p, loc) + pageRects(p, l)
        loc = l
        position = content.sections[l.section].start + sp.startOffset(l.page)
        progress.set(bookKey, position, total, done = isLast(p, l))
        refresher?.pageTurn(rects)
        prefetchAround(p, l.section)
    }

    /** 글자 위치로 바로 가기 (처음 열 때, 설정 바꿀 때, 막대로 옮길 때). */
    suspend fun goToPosition(p: Paginator, pos: Int) {
        val s = content.sectionAt(pos)
        val sp = p.get(s)
        val page = sp.pageFor(pos - content.sections[s].start)
        val l = Loc(s, page)
        loc = l
        progress.set(bookKey, pos, total, done = isLast(p, l))
        position = pos
        prefetchAround(p, s)
    }

    LaunchedEffect(paginator) {
        val p = paginator ?: return@LaunchedEffect
        goToPosition(p, position)
    }

    fun navigate(forward: Boolean) {
        val p = paginator ?: return
        val l = loc ?: return
        if (busy) return
        if (atEnd) {
            if (!forward) {
                atEnd = false
                refresher?.pageTurn(listOf(Rect(0f, 0f, viewSize.width.toFloat(), viewSize.height.toFloat())))
            }
            return
        }
        busy = true
        scope.launch {
            try {
                val sp = p.get(l.section)
                if (forward) {
                    when {
                        l.page + 1 < sp.count -> show(p, Loc(l.section, l.page + 1))
                        l.section + 1 < p.sectionCount -> {
                            p.get(l.section + 1)
                            show(p, Loc(l.section + 1, 0))
                        }
                        else -> {
                            atEnd = true
                            refresher?.pageTurn(listOf(Rect(0f, 0f, viewSize.width.toFloat(), viewSize.height.toFloat())))
                            progress.set(bookKey, position, total, done = true)
                        }
                    }
                } else {
                    when {
                        l.page > 0 -> show(p, Loc(l.section, l.page - 1))
                        l.section > 0 -> {
                            val prev = p.get(l.section - 1)
                            show(p, Loc(l.section - 1, prev.count - 1))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                busy = false
            }
        }
    }

    val nav by rememberUpdatedState<(Boolean) -> Unit>({ navigate(it) })
    val volumeNow by rememberUpdatedState(settings.volumeKeys)
    val overlayNow by rememberUpdatedState(overlay)
    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = { e ->
            val dir = PageKeys.direction(e.keyCode, false, volumeNow)
            if (dir == 0) {
                false
            } else {
                if (e.action == KeyEvent.ACTION_DOWN) nav(dir > 0)
                true
            }
        }
        PageKeys.handler = handler
        onDispose { if (PageKeys.handler === handler) PageKeys.handler = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    val w = size.width
                    when {
                        // 메뉴가 떠 있으면 어디를 눌러도 메뉴만 닫는다
                        overlayNow -> overlay = false
                        pos.x < w / 3f -> nav(false)
                        pos.x > w * 2f / 3f -> nav(true)
                        else -> overlay = true
                    }
                })
            }
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { change, amount ->
                        dx += amount
                        change.consume()
                    },
                    onDragEnd = { if (!overlayNow && kotlin.math.abs(dx) > threshold) nav(dx < 0) },
                )
            },
    ) {
        val p = paginator
        val l = loc
        val sp = if (p != null && l != null) p.cached(l.section) else null

        if (atEnd) {
            EndCard(
                title = fileTitle,
                nextTitle = nextEntry?.let { titleOf(it.name) },
                onOpenNext = { nextEntry?.let(onOpenNext) },
                onClose = onClose,
                onBack = { atEnd = false },
            )
        } else when (sp) {
            null -> Spinner(Modifier.align(Alignment.Center))

            is SectionPages.Text -> if (settings.crispText) {
                CrispTextPage(
                    pages = sp,
                    span = sp.pages[l!!.page.coerceIn(0, sp.pages.size - 1)],
                    size = viewSize,
                    leftPx = leftPx,
                    topPx = topPx,
                    strokePx = strokePx,
                    threshold = 85 + settings.crispLevel * 25,
                )
            } else {
                val span = sp.pages[l!!.page.coerceIn(0, sp.pages.size - 1)]
                Canvas(Modifier.fillMaxSize()) {
                    val pageHeight = span.bottom - span.top
                    val topLeft = Offset(leftPx.toFloat(), topPx - span.top)
                    // 이 페이지 줄들만 보이게 자른다 (앞뒤 페이지 글자가 비치지 않게)
                    clipRect(top = topPx - strokePx, bottom = topPx + pageHeight + strokePx) {
                        drawText(textLayoutResult = sp.layout, topLeft = topLeft)
                        if (strokePx > 0f) {
                            // 획 두껍게 보정: 같은 글자를 테두리로 한 번 더 칠한다
                            drawText(
                                textLayoutResult = sp.layout,
                                topLeft = topLeft,
                                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
            }

            is SectionPages.Image -> {
                val bitmap by produceState<Bitmap?>(null, sp.path, viewSize) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { decodeFit(content.imageBytes(sp.path), viewSize) }.getOrNull()
                    }
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp, bottom = 28.dp, start = 12.dp, end = 12.dp),
                    )
                }
            }
        }

        if (settings.showInfoBar && !overlay && !atEnd && sp != null) {
            val percent = (position.toLong() * 100 / total).toInt().coerceIn(0, 100)
            val inSection = if (sp.count > 1) "${(l?.page ?: 0) + 1}/${sp.count}  ·  " else ""
            InfoBar(
                right = "$inSection$percent%",
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (overlay && !atEnd) {
            val tocIndex = content.tocIndexAt(position)
            TextOverlay(
                title = content.toc.getOrNull(tocIndex)?.title ?: title,
                bookTitle = if (tocIndex >= 0) title else null,
                toc = content.toc,
                tocIndex = tocIndex,
                onToc = { i ->
                    val pg = paginator
                    val e = content.toc.getOrNull(i)
                    if (pg != null && e != null) scope.launch {
                        overlay = false
                        goToPosition(pg, e.position)
                        refresher?.now()
                    }
                },
                fraction = position.toFloat() / total,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onShelf = shelf?.let { s -> { s.toggle(origin, entry); Unit } },
                onShelfNow = shelf?.contains(origin, entry) == true,
                onRefresh = { refresher?.now() },
                onJump = { f ->
                    val pg = paginator
                    if (pg != null) scope.launch {
                        goToPosition(pg, (f * total).toInt().coerceIn(0, total - 1))
                        refresher?.now()
                    }
                },
                onClose = onClose,
            )
        }
    }
}

/** 가운데를 탭하면 나오는 위·아래 메뉴: 진행 막대, 글자 설정, 새로고침, 책장. */
@Composable
private fun TextOverlay(
    title: String,
    bookTitle: String?,
    toc: List<TextContent.TocEntry>,
    tocIndex: Int,
    onToc: (Int) -> Unit,
    fraction: Float,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onShelf: (() -> Unit)?,
    onShelfNow: Boolean,
    onRefresh: () -> Unit,
    onJump: (Float) -> Unit,
    onClose: () -> Unit,
) {
    var fontPanel by remember { mutableStateOf(false) }
    var tocOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Paper).blockTaps()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "닫기", onClose)
                Column(Modifier.weight(1f)) {
                    if (bookTitle != null) {
                        Text(bookTitle, color = Ink, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        title,
                        color = Ink,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onShelf != null) TextAction(if (onShelfNow) "★ 책장" else "☆ 책장", onClick = onShelf)
            }
            Line()
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Paper).blockTaps()) {
            Line()
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val shown = dragging ?: fraction
                Text("${(shown * 100).roundToInt()}%", color = Ink, style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = shown.coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let(onJump)
                        dragging = null
                    },
                    colors = LineSliderColors(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (toc.isNotEmpty()) {
                        LineButton("목차 ${toc.size}", modifier = Modifier.weight(1f)) { tocOpen = true }
                    }
                    LineButton(
                        if (fontPanel) "글자 설정 닫기" else "글자 설정",
                        bold = fontPanel,
                        modifier = Modifier.weight(1f),
                    ) { fontPanel = !fontPanel }
                    LineButton("화면 새로고침", modifier = Modifier.weight(1f), onClick = onRefresh)
                }
            }
        }
        if (fontPanel) {
            // 글자 설정: 책 위에 겹치지 않도록 화면 전체를 덮는 흰 설정 화면 (닫으면 바뀐 글자로 바로 보임)
            Column(Modifier.fillMaxSize().background(Paper).blockTaps()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "닫기") { fontPanel = false }
                    Text("글자 설정", color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Line()
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                ) {
                    FontOptions(settings, onSettingsChange)
                }
                Line()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                ) {
                    LineButton("닫고 책 보기", bold = true, modifier = Modifier.fillMaxWidth()) { fontPanel = false }
                }
            }
        }
        if (tocOpen) {
            TocPanel(toc, tocIndex, onPick = { tocOpen = false; onToc(it) }, onClose = { tocOpen = false })
        }
    }
}

/**
 * 글자 페이지를 '순수 검정/흰색'으로 그린다.
 * 보통 글자는 가장자리가 회색으로 부드럽게 칠해지는데(안티앨리어싱), 이잉크가 빠른 화면 모드로
 * 그 회색을 점점이 흩뿌리면 글자가 뭉개지고 계단처럼 깨져 보인다.
 * 그래서 한 장을 그림으로 먼저 그린 뒤, 기준(threshold)보다 어두운 점은 검정, 나머지는 흰색으로 바꾼다.
 * 기준이 높을수록 회색 가장자리까지 검정이 되어 글자가 굵고 진해진다.
 */
@Composable
private fun CrispTextPage(
    pages: SectionPages.Text,
    span: PageSpan,
    size: IntSize,
    leftPx: Int,
    topPx: Int,
    strokePx: Float,
    threshold: Int,
) {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val image by produceState<ImageBitmap?>(null, pages, span, size, leftPx, topPx, strokePx, threshold) {
        if (size.width <= 0 || size.height <= 0) return@produceState
        // 1) 글자 그리기 (화면과 같은 곳에서)
        val img = ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(density, dir, androidx.compose.ui.graphics.Canvas(img), Size(size.width.toFloat(), size.height.toFloat())) {
            drawRect(Paper)
            val pageHeight = span.bottom - span.top
            val topLeft = Offset(leftPx.toFloat(), topPx - span.top)
            clipRect(top = topPx - strokePx, bottom = topPx + pageHeight + strokePx) {
                drawText(textLayoutResult = pages.layout, topLeft = topLeft)
                if (strokePx > 0f) {
                    drawText(
                        textLayoutResult = pages.layout,
                        topLeft = topLeft,
                        drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                    )
                }
            }
        }
        // 2) 회색 → 검정 또는 흰색
        value = withContext(Dispatchers.Default) {
            val bmp = img.asAndroidBitmap()
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val black = 0xFF000000.toInt()
            val white = 0xFFFFFFFF.toInt()
            val t = threshold * 1000
            for (i in px.indices) {
                val c = px[i]
                val lum = ((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114
                px[i] = if (lum < t) black else white
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            img
        }
    }
    image?.let { im ->
        Canvas(Modifier.fillMaxSize()) { drawImage(im, filterQuality = FilterQuality.None) }
    }
}

/** 목차: 찾아낸 장 제목 목록. 지금 읽는 장은 굵게 ▶ 표시. */
@Composable
private fun TocPanel(toc: List<TextContent.TocEntry>, current: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    Column(Modifier.fillMaxSize().background(Paper).blockTaps()) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "닫기", onClose)
            Text("목차", color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${toc.size}개", color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 16.dp))
        }
        Line()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(toc.size) { i ->
                val on = i == current
                Text(
                    (if (on) "▶  " else "") + toc[i].title,
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.Bold else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(i) }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                )
                Line()
            }
        }
    }
}

/** 책 안 그림을 화면 크기에 맞춰 줄여서 읽는다. */
private fun decodeFit(bytes: ByteArray?, view: IntSize): Bitmap? {
    if (bytes == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val target = maxOf(view.width, view.height, 800)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
