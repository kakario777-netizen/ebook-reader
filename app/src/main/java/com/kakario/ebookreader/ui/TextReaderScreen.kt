package com.kakario.ebookreader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
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
import com.kakario.ebookreader.data.SettingsStore
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
 * 넘김 효과 없이 바로 바뀐다 (가볍고 잔상 없이).
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

    LaunchedEffect(retry) {
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
                    TextContent.loadTxt(file)
                }
            }
            if (c.sections.isEmpty()) {
                c.close()
                throw UserFacingException("이 책에는 읽을 글자가 없어요.")
            }
            content = c
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

            else -> TextPages(
                content = c,
                bookKey = progress.keyFor(origin.tag, entry),
                title = c.title ?: titleOf(entry.name),
                fileTitle = titleOf(entry.name),
                nextEntry = nextEntry,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onOpenNext = onOpenNext,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun TextPages(
    content: TextContent,
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
    val scope = rememberCoroutineScope()
    val total = content.totalChars.coerceAtLeast(1)

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val topPad = with(density) { 34.dp.roundToPx() }
    val bottomPad = with(density) { (if (settings.showInfoBar) 34.dp else 26.dp).roundToPx() }
    val marginPx = with(density) { settings.margin.dp.roundToPx() }

    val style = remember(settings.fontSize, settings.lineSpacing) {
        TextStyle(
            color = Ink,
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.lineSpacing / 10f).em,
            textAlign = TextAlign.Justify,
            lineBreak = LineBreak.Simple,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }
    val paginator = remember(viewSize, style, marginPx, bottomPad) {
        if (viewSize.width <= 0 || viewSize.height <= 0) {
            null
        } else {
            Paginator(
                content = content,
                measurer = TextMeasurer(fontResolver, density, layoutDirection, cacheSize = 0),
                style = style,
                widthPx = viewSize.width - marginPx * 2,
                heightPx = viewSize.height - topPad - bottomPad,
            )
        }
    }

    // 책 처음부터 센 글자 위치 — 글자 크기·화면 방향이 바뀌어도 이 위치에서 이어 읽는다
    var position by remember { mutableIntStateOf(progress.get(bookKey).position.coerceIn(0, total - 1)) }
    var loc by remember { mutableStateOf<Loc?>(null) }
    var atEnd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }

    fun isLast(p: Paginator, l: Loc): Boolean {
        if (l.section < p.sectionCount - 1) return false
        val sp = p.cached(l.section) ?: return false
        return l.page >= sp.count - 1
    }

    fun show(p: Paginator, l: Loc) {
        val sp = p.cached(l.section) ?: return
        loc = l
        position = content.sections[l.section].start + sp.startOffset(l.page)
        progress.set(bookKey, position, total, done = isLast(p, l))
        // 앞뒤 구간은 미리 나눠둔다 (넘길 때 기다리지 않게)
        scope.launch {
            runCatching {
                if (l.section + 1 < p.sectionCount) p.get(l.section + 1)
                if (l.section > 0) p.get(l.section - 1)
            }
        }
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
        scope.launch {
            runCatching {
                if (s + 1 < p.sectionCount) p.get(s + 1)
                if (s > 0) p.get(s - 1)
            }
        }
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
            if (!forward) atEnd = false
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
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    val w = size.width
                    when {
                        pos.x < w / 3f -> nav(false)
                        pos.x > w * 2f / 3f -> nav(true)
                        else -> overlay = !overlay
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
                    onDragEnd = { if (kotlin.math.abs(dx) > threshold) nav(dx < 0) },
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

            is SectionPages.Text -> {
                val span = sp.pages[l!!.page.coerceIn(0, sp.pages.size - 1)]
                Canvas(Modifier.fillMaxSize()) {
                    val pageHeight = span.bottom - span.top
                    clipRect(top = topPad.toFloat(), bottom = topPad + pageHeight + 1f) {
                        drawText(
                            textLayoutResult = sp.layout,
                            topLeft = Offset(marginPx.toFloat(), topPad - span.top),
                        )
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

        TextOverlay(
            visible = overlay && !atEnd,
            title = title,
            fraction = position.toFloat() / total,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onJump = { f ->
                val pg = paginator
                if (pg != null) scope.launch { goToPosition(pg, (f * total).toInt().coerceIn(0, total - 1)) }
            },
            onClose = onClose,
        )
    }
}

/** 가운데를 탭하면 나오는 위·아래 메뉴: 진행 막대, 글자 크기·줄 간격·여백. */
@Composable
private fun TextOverlay(
    visible: Boolean,
    title: String,
    fraction: Float,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onJump: (Float) -> Unit,
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
                    LineStepper(
                        "글자 크기",
                        "${settings.fontSize}",
                        onMinus = {
                            onSettingsChange(settings.copy(fontSize = (settings.fontSize - 1).coerceAtLeast(SettingsStore.FONT_MIN)))
                        },
                        onPlus = {
                            onSettingsChange(settings.copy(fontSize = (settings.fontSize + 1).coerceAtMost(SettingsStore.FONT_MAX)))
                        },
                    )
                    LineStepper(
                        "줄 간격",
                        String.format("%.1f", settings.lineSpacing / 10f),
                        onMinus = {
                            onSettingsChange(settings.copy(lineSpacing = (settings.lineSpacing - 1).coerceAtLeast(SettingsStore.LINE_MIN)))
                        },
                        onPlus = {
                            onSettingsChange(settings.copy(lineSpacing = (settings.lineSpacing + 1).coerceAtMost(SettingsStore.LINE_MAX)))
                        },
                    )
                    LineStepper(
                        "좌우 여백",
                        "${settings.margin}",
                        onMinus = {
                            onSettingsChange(settings.copy(margin = (settings.margin - 2).coerceAtLeast(SettingsStore.MARGIN_MIN)))
                        },
                        onPlus = {
                            onSettingsChange(settings.copy(margin = (settings.margin + 2).coerceAtMost(SettingsStore.MARGIN_MAX)))
                        },
                    )
                }
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
