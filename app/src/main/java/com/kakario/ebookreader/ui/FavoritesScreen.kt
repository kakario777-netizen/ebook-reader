package com.kakario.ebookreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.data.Favorite
import com.kakario.ebookreader.data.FtpServer
import com.kakario.ebookreader.data.ProgressStore
import com.kakario.ebookreader.data.SettingsStore
import com.kakario.ebookreader.data.ShelfBook
import com.kakario.ebookreader.model.titleOf
import com.kakario.ebookreader.update.Updater

/**
 * 첫 화면: 위쪽은 책장(꽂아 둔 책을 책 모양으로), 아래쪽은 즐겨찾기 폴더.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    servers: List<FtpServer>,
    shelf: Shelf,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    sort: com.kakario.ebookreader.data.ShelfSort = com.kakario.ebookreader.data.ShelfSort.ADDED,
    onSortChange: (com.kakario.ebookreader.data.ShelfSort) -> Unit = {},
    onCheckUpdate: () -> Unit,
    onOpen: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
    onOpenBook: (Origin, ShelfBook) -> Unit,
) {
    var removing by remember { mutableStateOf<Favorite?>(null) }
    var bookMenu by remember { mutableStateOf<ShelfBook?>(null) }
    val context = LocalContext.current
    val progress = remember { ProgressStore.get(context) }

    // 서버가 지워진 책은 보여주지 않는다
    val books0 = shelf.books.mapNotNull { b ->
        if (b.serverId == null) Origin(null) to b
        else servers.firstOrNull { it.id == b.serverId }?.let { Origin(it) to b }
    }
    val books = remember(books0, sort, progress.version) {
        when (sort) {
            com.kakario.ebookreader.data.ShelfSort.ADDED -> books0
            com.kakario.ebookreader.data.ShelfSort.NAME ->
                books0.sortedWith { a, b -> com.kakario.ebookreader.model.NaturalOrder.compare(a.second.name, b.second.name) }
            com.kakario.ebookreader.data.ShelfSort.RECENT -> books0.sortedByDescending { (o, b) ->
                progress.time(progress.keyFor(o.tag, b.toEntry()))
            }
        }
    }

    // 책장 칸: 책 먼저, 그다음 즐겨찾기 폴더 (폴더 모양 칸)
    val cells: List<Any> = remember(books, favorites) { books + favorites }
    val compact = columns >= 5
    Column(Modifier.fillMaxSize()) {
        BrowserHeader(
            title = "책장",
            subtitle = if (books.isEmpty()) null else "${books.size}권 · 폴더 ${favorites.size}개 · 길게 누르면 뺄 수 있어요",
            onBack = null,
            actions = {
                // 정렬: 누를 때마다 꽂은 순 → 최근 읽은 순 → 이름순
                LineButton(sort.label, modifier = Modifier.padding(end = 6.dp)) {
                    val all = com.kakario.ebookreader.data.ShelfSort.entries
                    onSortChange(all[(sort.ordinal + 1) % all.size])
                }
                // 칸 크기: 작게(한 줄에 더 많이) / 크게 — 줄 수는 화면에 맞춰 자동
                LineButton("작게", modifier = Modifier.padding(end = 6.dp), enabled = columns < SettingsStore.SHELF_MAX) {
                    onColumnsChange(columns + 1)
                }
                LineButton("크게", modifier = Modifier.padding(end = 12.dp), enabled = columns > SettingsStore.SHELF_MIN) {
                    onColumnsChange(columns - 1)
                }
            },
        )
        Line()
        if (cells.isEmpty()) {
            Text(
                "책장이 비어 있어요\nFTP·내 폰 목록에서 책을 길게 눌러 '책장에 꽂기'를 고르거나\n폴더 위 ☆를 누르면 여기에 모여요",
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 36.dp, horizontal = 24.dp),
            )
        } else {
            PagedGrid(
                items = cells,
                columns = columns,
                pageKey = sort,
                key = { c -> if (c is Favorite) "f" + c.id else "b" + (c as Pair<*, *>).let { (it.second as ShelfBook).id } },
                modifier = Modifier.weight(1f),
            ) { c ->
                if (c is Favorite) {
                    val server = servers.firstOrNull { it.id == c.serverId }
                    GridCell(
                        title = c.name,
                        bold = true,
                        onClick = { onOpen(c) },
                        onLongClick = { removing = c },
                    ) { m -> FolderTile(favorite = true, compact = compact, modifier = m) }
                } else {
                    val pair = c as Pair<*, *>
                    val origin = pair.first as Origin
                    val book = pair.second as ShelfBook
                    val entry = remember(book) { book.toEntry() }
                    val p = remember(progress.version, book.id) { progress.get(progress.keyFor(origin.tag, entry)) }
                    GridCell(
                        title = titleOf(book.name),
                        onClick = { onOpenBook(origin, book) },
                        onLongClick = { bookMenu = book },
                    ) { m ->
                        ShelfTile(
                            origin = origin,
                            entry = entry,
                            badge = when {
                                p.finished -> "완독"
                                !p.started -> "NEW"
                                else -> "${p.percent}%"
                            },
                            compact = compact,
                            modifier = m,
                        )
                    }
                }
            }
        }
        // 맨 아래 오른쪽: 버전·빌드 + 업데이트 확인
        val ver = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        }
        val build = remember { Updater.get(context).currentBuild }
        Line()
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("이북리더 v$ver · 빌드 #$build", color = Ink, style = MaterialTheme.typography.bodySmall)
            Text(
                "업데이트 확인",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onCheckUpdate).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }

    bookMenu?.let { b ->
        LineDialog(
            title = titleOf(b.name),
            onDismiss = { bookMenu = null },
            dismiss = { DialogAction("닫기") { bookMenu = null } },
        ) {
            Column {
                Text(b.path, color = Ink, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Line()
                MenuLine("책장에서 빼기") {
                    shelf.remove(b)
                    bookMenu = null
                }
            }
        }
    }

    removing?.let { fav ->
        LineDialog(
            title = "즐겨찾기 해제",
            onDismiss = { removing = null },
            confirm = {
                DialogAction("빼기", bold = true) {
                    onRemove(fav)
                    removing = null
                }
            },
            dismiss = { DialogAction("취소") { removing = null } },
        ) {
            Text("'${fav.name}' 폴더를 즐겨찾기에서 뺄까요?\n(실제 폴더나 파일은 지워지지 않아요)", color = Ink)
        }
    }
}
