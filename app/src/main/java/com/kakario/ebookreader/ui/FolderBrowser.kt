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
    var menuFor by remember { mutableStateOf<FileEntry?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val currentLoad by rememberUpdatedState(load)
    val currentDelete by rememberUpdatedState(delete)
    LaunchedEffect(path, reloadKey) {
        state = LoadState.Loading
        state = try {
            LoadState.Loaded(currentLoad(path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserFacingException) {
            LoadState.Failed(e.message ?: "알 수 없는 오류")
        } catch (e: Exception) {
            LoadState.Failed("예상치 못한 오류: ${e.message ?: e.javaClass.simpleName}")
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
                    }
                } ?: path,
                onBack = onUp,
                actions = {
                    TextAction(if (isFavorite(path)) "★" else "☆") { toggle(path) }
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "새로고침", tint = Ink)
                    }
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
                        val visible = s.entries.filter { it.isDirectory || it.isBook }
                        if (visible.isEmpty()) {
                            Text(
                                "이 폴더에는 하위 폴더나 책 파일이 없어요\n(TXT · EPUB · PDF · ZIP)",
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                color = Ink,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            val books = visible.filter { it.isBook }
                            LazyColumn(Modifier.fillMaxSize()) {
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
        LineSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
            fontWeight = FontWeight.Medium,
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

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = if (covers) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (covers) {
            SmallCover(origin, entry, Modifier.size(width = 42.dp, height = 58.dp))
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로", tint = Ink)
            }
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
