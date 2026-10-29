package com.kakario.ebookreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.data.Favorite
import com.kakario.ebookreader.data.FtpServer

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    servers: List<FtpServer>,
    onOpen: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
    var removing by remember { mutableStateOf<Favorite?>(null) }

    Column(Modifier.fillMaxSize()) {
        BrowserHeader(
            title = "즐겨찾기",
            subtitle = if (favorites.isEmpty()) null else "누르면 열리고, 길게 누르면 뺄 수 있어요",
            onBack = null,
        )
        Line()
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "즐겨찾기한 폴더가 없어요\nFTP나 내 폰 탭에서 폴더 위 ☆를 누르면\n여기에 모여서 바로 열 수 있어요",
                    color = Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(favorites, key = { it.id }) { fav ->
                    val server = servers.firstOrNull { it.id == fav.serverId }
                    val source = if (fav.isLocal) "내 폰" else "FTP · " + (server?.name ?: "알 수 없는 서버")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onOpen(fav) }, onLongClick = { removing = fav })
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                fav.name,
                                color = Ink,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "$source  ·  ${fav.path}",
                                color = Ink,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("›", color = Ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
                    }
                    Line()
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
