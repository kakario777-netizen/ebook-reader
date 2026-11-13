package com.kakario.ebookreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.data.ProgressStore
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.UserFacingException
import com.kakario.ebookreader.model.formatBytes
import com.kakario.ebookreader.model.titleOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val entries: List<FileEntry>) : LoadState
    data class Failed(val message: String) : LoadState
}

/** 폴더 목록 모양: 표지 격자인지, 한 줄 칸 수. */
data class BrowseLayout(
    val grid: Boolean,
    val columns: Int,
    val sort: com.kakario.ebookreader.data.ListSort = com.kakario.ebookreader.data.ListSort.NAME,
    val desc: Boolean = false,
    val foldersTop: Boolean = true,
    val onChange: (BrowseLayout) -> Unit = {},
)

val LocalBrowseLayout = compositionLocalOf { BrowseLayout(true, 5) }

/** 정렬: 기준·순서대로, 폴더는 위 또는 아래에 모아서. */
fun sortEntries(list: List<FileEntry>, lay: BrowseLayout): List<FileEntry> {
    val byKey: Comparator<FileEntry> = when (lay.sort) {
        com.kakario.ebookreader.data.ListSort.NAME -> Comparator { a, b -> com.kakario.ebookreader.model.NaturalOrder.compare(a.name, b.name) }
        com.kakario.ebookreader.data.ListSort.DATE -> compareBy<FileEntry> { it.modified }
        com.kakario.ebookreader.data.ListSort.SIZE -> compareBy<FileEntry> { if (it.isDirectory) 0L else it.size }
        com.kakario.ebookreader.data.ListSort.TYPE -> compareBy<FileEntry> { it.kind?.ordinal ?: -1 }
    }.thenComparator { a, b -> com.kakario.ebookreader.model.NaturalOrder.compare(a.name, b.name) }
    val ordered = if (lay.desc) byKey.reversed() else byKey
    val (dirs, files) = list.partition { it.isDirectory }
    val d = dirs.sortedWith(ordered)
    val f = files.sortedWith(ordered)
    return if (lay.foldersTop) d + f else f + d
}

/** 목록에 표지를 작게 보여줄지 (설정). */
val LocalListCovers = compositionLocalOf { true }

/**
 * FTP와 내 폰이 함께 쓰는 폴더 목록 화면 — 글자 위주, 줄 사이 1px 선.
 * 지금 보고 있는 폴더(path)는 바깥에서 관리해서, 탭을 옮겨 다녀도 위치가 유지된다.
 */
@Composable
fun FolderBrowser(
    title: String,
    path: String,
    origin: Origin,
    load: suspend (String) -> List<FileEntry>,
    onUp: () -> Unit,
    onOpenDir: (String) -> Unit,
    /** 책을 열 때: 누른 책과, 같은 폴더의 책 목록(다음 권 찾기용). */
    onOpenBook: (entry: FileEntry, siblings: List<FileEntry>) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (path: String) -> Unit,
    /** 파일 삭제 (길게 누르기 메뉴). */
    delete: suspend (FileEntry) -> Unit,
) {
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val progressStore = remember { ProgressStore.get(context) }
    val thumbs = LocalThumbnails.current
    val shelf = LocalShelf.current
    var menuFor by remember { mutableStateOf<FileEntry?>(null) }
    var viewMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val currentLoad by rememberUpdatedState(load)
    val currentDelete by rememberUpdatedState(delete)
    val listCache = remember { com.kakario.ebookreader.data.ListCache.get(context) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(path, reloadKey) {
        // 1) 저장해 둔 목록이 있으면 바로 보여주고
        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { listCache.read(origin.tag, path) }
        state = if (saved != null) LoadState.Loaded(saved) else LoadState.Loading
        // 2) 그동안 새로 받아서 바뀐 게 있으면 고친다
        refreshing = true
        try {
            val fresh = currentLoad(path)
            if (saved == null || fresh != saved) state = LoadState.Loaded(fresh)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { listCache.write(origin.tag, path, fresh) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = (e as? UserFacingException)?.message
                ?: "예상치 못한 오류: ${e.message ?: e.javaClass.simpleName}"
            if (saved == null) state = LoadState.Failed(msg)
            else toastLater(scope, snackbar, "새 목록을 받지 못해 저장된 목록을 보여줘요")
        } finally {
            refreshing = false
        }
    }

    BackHandler(onBack = onUp)

    fun toast(msg: String) = scope.toast(snackbar, msg)

    fun toggle(p: String) {
        val wasOn = isFavorite(p)
        onToggleFavorite(p)
        toast(if (wasOn) "즐겨찾기에서 뺐어요" else "즐겨찾기에 추가했어요")
    }

    val loaded = (state as? LoadState.Loaded)?.entries
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BrowserHeader(
                title = title,
                subtitle = loaded?.let { list ->
                    val books = list.count { it.isBook }
                    val dirs = list.count { it.isDirectory }
                    buildString {
                        append("책 ${books}권")
                        if (dirs > 0) append(" · 폴더 ${dirs}개")
                        append("  ·  ")
                        append(path)
                        if (refreshing) append("  ·  확인 중…")
                    }
                } ?: path,
                onBack = onUp,
                actions = {
                    TextAction("보기·정렬") { viewMenu = true }
                    TextAction(if (isFavorite(path)) "★" else "☆") { toggle(path) }
                    IconAction(Icons.Outlined.Refresh, "새로고침") { reloadKey++ }
                },
            )
            Line()

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    LoadState.Loading -> Spinner(Modifier.align(Alignment.Center))

                    is LoadState.Failed -> ErrorView(
                        message = s.message,
                        onRetry = { reloadKey++ },
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is LoadState.Loaded -> {
                        val layNow = LocalBrowseLayout.current
                        val visible = remember(s.entries, layNow.sort, layNow.desc, layNow.foldersTop) {
                            sortEntries(s.entries.filter { it.isDirectory || it.isBook }, layNow)
                        }
                        if (visible.isEmpty()) {
                            Text(
                                "이 폴더에는 하위 폴더나 책 파일이 없어요\n(TXT · EPUB · PDF · ZIP)",
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                color = Ink,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            val books = visible.filter { it.isBook }
                            val lay = LocalBrowseLayout.current
                            if (lay.grid) {
                                BookGrid(
                                    origin = origin,
                                    visible = visible,
                                    columns = lay.columns,
                                    isFavorite = isFavorite,
                                    onOpenDir = onOpenDir,
                                    onOpenBook = { onOpenBook(it, books) },
                                    onLongClick = { menuFor = it },
                                )
                            } else {
                              val listState = remember(path) { androidx.compose.foundation.lazy.LazyListState() }
                              PagedArea(rememberPageInfo(listState)) {
                              LazyColumn(Modifier.fillMaxSize(), state = listState, userScrollEnabled = false) {
                                items(visible, key = { it.path }) { entry ->
                                    if (entry.isDirectory) {
                                        FolderRow(
                                            entry = entry,
                                            favorite = isFavorite(entry.path),
                                            onClick = { onOpenDir(entry.path) },
                                            onLongClick = { menuFor = entry },
                                        )
                                    } else {
                                        BookRow(
                                            origin = origin,
                                            entry = entry,
                                            onClick = { onOpenBook(entry, books) },
                                            onLongClick = { menuFor = entry },
                                        )
                                    }
                                    Line()
                                }
                            }
                              }
                            }
                        }
                    }
                }
            }
        }
        LineSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // ── 보기·정렬
    if (viewMenu) {
        val lay = LocalBrowseLayout.current
        LineDialog(
            title = "보기 · 정렬",
            onDismiss = { viewMenu = false },
            dismiss = { DialogAction("닫기") { viewMenu = false } },
        ) {
            Column {
                LineChoices("보기", listOf(true, false), lay.grid, { if (it) "표지 격자" else "줄 목록" }) {
                    lay.onChange(lay.copy(grid = it))
                }
                if (lay.grid) {
                    LineStepper(
                        "한 줄 칸 수",
                        "${lay.columns}",
                        onMinus = { lay.onChange(lay.copy(columns = (lay.columns - 1).coerceAtLeast(com.kakario.ebookreader.data.SettingsStore.SHELF_MIN))) },
                        onPlus = { lay.onChange(lay.copy(columns = (lay.columns + 1).coerceAtMost(com.kakario.ebookreader.data.SettingsStore.SHELF_MAX))) },
                    )
                }
                LineChoices("정렬 기준", com.kakario.ebookreader.data.ListSort.entries, lay.sort, { it.label }) {
                    lay.onChange(lay.copy(sort = it))
                }
                LineChoices("순서", listOf(false, true), lay.desc, {
                    when (lay.sort) {
                        com.kakario.ebookreader.data.ListSort.DATE -> if (it) "최신 먼저" else "오래된 먼저"
                        com.kakario.ebookreader.data.ListSort.SIZE -> if (it) "큰 것 먼저" else "작은 것 먼저"
                        else -> if (it) "거꾸로 (ㅎ→ㄱ)" else "차례대로 (ㄱ→ㅎ)"
                    }
                }) { lay.onChange(lay.copy(desc = it)) }
                LineChoices("폴더 위치", listOf(true, false), lay.foldersTop, { if (it) "위로" else "아래로" }) {
                    lay.onChange(lay.copy(foldersTop = it))
                }
            }
        }
    }

    // ── 길게 누르기 메뉴
    menuFor?.let { target ->
        LineDialog(
            title = target.name,
            onDismiss = { menuFor = null },
            dismiss = { DialogAction("닫기") { menuFor = null } },
        ) {
            Column {
                if (target.isBook) {
                    val p = remember(target, progressStore.version) {
                        progressStore.get(progressStore.keyFor(origin.tag, target))
                    }
                    InfoLine("형식", target.kind?.label ?: "")
                    InfoLine("용량", formatBytes(target.size))
                    if (p.started) InfoLine("읽은 곳", if (p.finished) "다 읽음" else "${p.percent}%")
                    Spacer(Modifier.height(8.dp))
                    Line()
                    if (shelf != null) {
                        val onShelf = shelf.contains(origin, target)
                        MenuLine(if (onShelf) "책장에서 빼기" else "책장에 꽂기 (첫 화면)") {
                            menuFor = null
                            val now = shelf.toggle(origin, target)
                            toast(if (now) "책장에 꽂았어요 — 첫 화면에서 바로 열 수 있어요" else "책장에서 뺐어요")
                        }
                    }
                    if (target.hasCover) {
                        MenuLine("표지 다시 만들기") {
                            menuFor = null
                            thumbs?.invalidate(origin, target)
                            toast("표지를 다시 만들어요")
                        }
                    }
                    MenuLine("읽은 위치 지우기", enabled = p.started) {
                        menuFor = null
                        confirm = Confirm(target, ConfirmAction.RESET, 1)
                    }
                    MenuLine("파일 삭제") {
                        menuFor = null
                        confirm = Confirm(target, ConfirmAction.DELETE, 1)
                    }
                } else {
                    val fav = isFavorite(target.path)
                    MenuLine(if (fav) "☆  즐겨찾기에서 빼기" else "★  즐겨찾기에 추가") {
                        menuFor = null
                        toggle(target.path)
                    }
                }
            }
        }
    }

    // ── 두 번 확인
    confirm?.let { c ->
        val isDelete = c.action == ConfirmAction.DELETE
        val dialogTitle = when {
            isDelete && c.step == 1 -> "파일을 삭제할까요?"
            isDelete -> "정말 삭제할까요?"
            c.step == 1 -> "읽은 위치를 지울까요?"
            else -> "정말 지울까요?"
        }
        val body = when {
            isDelete && c.step == 1 -> "'${c.entry.name}'\n파일을 ${if (origin.server != null) "서버" else "폰"}에서 지워요."
            isDelete -> "삭제한 파일은 되돌릴 수 없어요."
            c.step == 1 -> "'${c.entry.name}'\n처음부터 읽게 돼요."
            else -> "읽던 위치 기록이 사라져요."
        }
        LineDialog(
            title = dialogTitle,
            onDismiss = { confirm = null },
            confirm = {
                DialogAction(
                    when {
                        c.step == 1 -> "다음"
                        isDelete -> "네, 삭제할게요"
                        else -> "네, 지울게요"
                    },
                    bold = c.step == 2,
                ) {
                    if (c.step == 1) {
                        confirm = c.copy(step = 2)
                        return@DialogAction
                    }
                    confirm = null
                    val key = progressStore.keyFor(origin.tag, c.entry)
                    if (isDelete) {
                        scope.launch {
                            try {
                                currentDelete(c.entry)
                                progressStore.clear(key)
                                toast("삭제했어요")
                                reloadKey++
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                toast(e.message ?: "삭제하지 못했어요")
                            }
                        }
                    } else {
                        progressStore.clear(key)
                        toast("읽은 위치를 지웠어요")
                    }
                }
            },
            dismiss = { DialogAction("취소") { confirm = null } },
        ) {
            Text(body, color = Ink)
        }
    }
}

private fun toastLater(scope: CoroutineScope, host: SnackbarHostState, msg: String) = scope.toast(host, msg)

/** 표지 격자: 폴더는 위에 한 줄씩, 책은 책장처럼 칸으로. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGrid(
    origin: Origin,
    visible: List<FileEntry>,
    columns: Int,
    isFavorite: (String) -> Boolean,
    onOpenDir: (String) -> Unit,
    onOpenBook: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { ProgressStore.get(ctx) }
    val version = store.version
    val compact = columns >= 5
    val gridState = remember(visible.firstOrNull()?.path?.let { parentOf(it) }) { androidx.compose.foundation.lazy.grid.LazyGridState() }
    PagedArea(rememberPageInfo(gridState)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        userScrollEnabled = false,
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(visible, key = { it.path }, span = { e -> GridItemSpan(if (e.isDirectory) maxLineSpan else 1) }) { entry ->
            if (entry.isDirectory) {
                Column {
                    FolderRow(
                        entry = entry,
                        favorite = isFavorite(entry.path),
                        onClick = { onOpenDir(entry.path) },
                        onLongClick = { onLongClick(entry) },
                    )
                    Line()
                }
            } else {
                val p = remember(version, entry.path, entry.size) { store.get(store.keyFor(origin.tag, entry)) }
                Column(
                    Modifier
                        .combinedClickable(onClick = { onOpenBook(entry) }, onLongClick = { onLongClick(entry) })
                        .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 8.dp else 12.dp),
                ) {
                    ShelfTile(
                        origin = origin,
                        entry = entry,
                        badge = when {
                            p.finished -> "완독"
                            !p.started -> "NEW"
                            else -> "${p.percent}%"
                        },
                        compact = compact,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        titleOf(entry.name),
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    }
}

private fun parentOf(p: String) = p.substringBeforeLast('/')

private enum class ConfirmAction { DELETE, RESET }
private data class Confirm(val entry: FileEntry, val action: ConfirmAction, val step: Int)

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(entry: FileEntry, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.name + "/",
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (favorite) Text("★", color = Ink, modifier = Modifier.padding(start = 8.dp))
        Text("›", color = Ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(origin: Origin, entry: FileEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ProgressStore.get(ctx) }
    val version = store.version // 뷰어를 닫으면 바뀌어서 다시 그린다
    val p = remember(version, entry.path, entry.size) { store.get(store.keyFor(origin.tag, entry)) }
    val covers = LocalListCovers.current
    val onShelf = LocalShelf.current?.contains(origin, entry) == true

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = if (covers) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (covers) {
            BookFace(origin, entry, Modifier.size(width = 44.dp, height = 60.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                titleOf(entry.name),
                color = Ink,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(entry.kind?.label ?: "")
                    append(" · ")
                    append(formatBytes(entry.size))
                    if (p.started) append(if (p.finished) "  ·  다 읽음" else "  ·  ${p.percent}%")
                    if (onShelf) append("  ·  책장")
                },
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (p.started && !p.finished) {
            // 읽은 만큼 채워지는 아주 얇은 막대
            Box(Modifier.padding(start = 12.dp).size(width = 36.dp, height = 4.dp).border(1.dp, Ink)) {
                Box(Modifier.fillMaxWidth(p.fraction).height(4.dp).background(Ink))
            }
        }
    }
}

/** 탭 아래에 들어가는 얇은 제목줄 (뒤로 · 제목/경로 · 버튼들). */
@Composable
fun BrowserHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack)
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** 아래에 잠깐 뜨는 알림 — 흰 바탕에 검은 테두리. */
@Composable
fun LineSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Text(
            data.visuals.message,
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .background(Paper)
                .border(1.dp, Ink)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

fun CoroutineScope.toast(host: SnackbarHostState, msg: String) {
    launch {
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(msg)
    }
}
