package com.kakario.ebookreader

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.data.Favorite
import com.kakario.ebookreader.data.FavoriteStore
import com.kakario.ebookreader.data.FtpServer
import com.kakario.ebookreader.data.ServerStore
import com.kakario.ebookreader.data.SettingsStore
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.BookKind
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.folderName
import com.kakario.ebookreader.thumb.ThumbnailLoader
import com.kakario.ebookreader.ui.FavoritesScreen
import com.kakario.ebookreader.ui.FtpLocation
import com.kakario.ebookreader.ui.FtpTab
import com.kakario.ebookreader.ui.ImageViewerScreen
import com.kakario.ebookreader.ui.Ink
import com.kakario.ebookreader.ui.Line
import com.kakario.ebookreader.ui.LocalListCovers
import com.kakario.ebookreader.ui.LocalTab
import com.kakario.ebookreader.ui.LocalThumbnails
import com.kakario.ebookreader.ui.Paper
import com.kakario.ebookreader.ui.PaperColors
import com.kakario.ebookreader.ui.SettingsScreen
import com.kakario.ebookreader.ui.TextReaderScreen
import com.kakario.ebookreader.viewer.PageKeys
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/** 열려 있는 책과, 같은 폴더의 책 목록(다음 권 찾기용). */
data class OpenBook(
    val origin: Origin,
    val entry: FileEntry,
    val siblings: List<FileEntry>,
) {
    /** 같은 폴더에서 다음 순서의 같은 종류 책. */
    val next: FileEntry?
        get() {
            val i = siblings.indexOfFirst { it.path == entry.path }
            if (i < 0) return null
            return siblings.drop(i + 1).firstOrNull { it.kind == entry.kind }
        }
}

private val TABS = listOf("즐겨찾기", "FTP", "내 폰")
private const val TAB_FAVORITES = 0
private const val TAB_FTP = 1
private const val TAB_LOCAL = 2

class MainActivity : ComponentActivity() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 상태바·내비게이션바도 흰 바탕 + 검은 아이콘
        val bars = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        val serverStore = ServerStore(applicationContext)
        val favoriteStore = FavoriteStore(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        val thumbnails = ThumbnailLoader.get(applicationContext)

        setContent {
            var settings by remember { mutableStateOf(settingsStore.load()) }
            val changeSettings: (com.kakario.ebookreader.data.AppSettings) -> Unit = {
                settings = it
                settingsStore.save(it)
            }

            MaterialTheme(colorScheme = PaperColors) {
              CompositionLocalProvider(
                LocalThumbnails provides thumbnails,
                LocalListCovers provides settings.listCovers,
              ) {
                var settingsOpen by remember { mutableStateOf(false) }
                var openBook by remember { mutableStateOf<OpenBook?>(null) }
                // 책을 보는 동안엔 표지 만들기를 멈춰 보는 쪽에 속도를 양보
                LaunchedEffect(openBook != null) { thumbnails.paused.value = openBook != null }
                val servers = remember { mutableStateListOf<FtpServer>().apply { addAll(serverStore.load()) } }
                val favorites = remember { mutableStateListOf<Favorite>().apply { addAll(favoriteStore.load()) } }
                var tab by remember { mutableIntStateOf(TAB_FAVORITES) }

                // 각 탭의 현재 위치 — 탭을 옮겨 다녀도 그대로 남는다.
                var ftpLocation by remember { mutableStateOf<FtpLocation?>(null) }
                var localPath by remember { mutableStateOf<String?>(null) }

                // 열려 있는 서버의 FTP 연결 (서버가 바뀌거나 닫히면 끊는다)
                val openServer = ftpLocation?.server
                val ftpBrowser = remember(openServer) { openServer?.let { FtpBrowser(it) } }
                DisposableEffect(ftpBrowser) {
                    onDispose { ftpBrowser?.let { b -> GlobalScope.launch { b.close() } } }
                }

                fun isFavorite(serverId: Long?, path: String) =
                    favorites.any { it.matches(serverId, path) }

                fun toggleFavorite(serverId: Long?, path: String) {
                    val existing = favorites.firstOrNull { it.matches(serverId, path) }
                    if (existing != null) {
                        favorites.remove(existing)
                    } else {
                        favorites.add(Favorite(System.currentTimeMillis(), serverId, path, folderName(path)))
                    }
                    favoriteStore.save(favorites)
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Paper)
                ) {
                    if (settingsOpen) {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            SettingsScreen(
                                settings = settings,
                                onChange = changeSettings,
                                onClose = { settingsOpen = false },
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            TabBar(
                                selected = tab,
                                onSelect = { tab = it },
                                onSettings = { settingsOpen = true },
                            )
                            Line()
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (tab) {
                                    TAB_FAVORITES -> FavoritesScreen(
                                        favorites = favorites,
                                        servers = servers,
                                        onOpen = { fav ->
                                            if (fav.isLocal) {
                                                localPath = fav.path
                                                tab = TAB_LOCAL
                                            } else {
                                                val server = servers.firstOrNull { it.id == fav.serverId }
                                                if (server != null) {
                                                    // 같은 서버가 이미 열려 있으면 연결을 재사용, root도 유지
                                                    val keepRoot = ftpLocation?.takeIf { it.server.id == server.id }?.root
                                                    ftpLocation = FtpLocation(server, fav.path, keepRoot)
                                                    tab = TAB_FTP
                                                }
                                            }
                                        },
                                        onRemove = { fav ->
                                            favorites.removeAll { it.id == fav.id }
                                            favoriteStore.save(favorites)
                                        },
                                    )

                                    TAB_FTP -> FtpTab(
                                        servers = servers,
                                        location = ftpLocation,
                                        browser = ftpBrowser,
                                        onLocationChange = { ftpLocation = it },
                                        onOpenBook = { entry, siblings ->
                                            ftpLocation?.let { openBook = OpenBook(Origin(it.server), entry, siblings) }
                                        },
                                        onSaveServer = { server ->
                                            val idx = servers.indexOfFirst { it.id == server.id }
                                            if (idx >= 0) servers[idx] = server else servers.add(server)
                                            serverStore.save(servers)
                                        },
                                        onDeleteServer = { server ->
                                            servers.removeAll { it.id == server.id }
                                            serverStore.save(servers)
                                            if (favorites.removeAll { it.serverId == server.id }) {
                                                favoriteStore.save(favorites)
                                            }
                                        },
                                        isFavorite = { id, path -> isFavorite(id, path) },
                                        onToggleFavorite = { id, path -> toggleFavorite(id, path) },
                                    )

                                    TAB_LOCAL -> LocalTab(
                                        path = localPath,
                                        onPathChange = { localPath = it },
                                        onOpenBook = { entry, siblings ->
                                            openBook = OpenBook(Origin(null), entry, siblings)
                                        },
                                        isFavorite = { isFavorite(null, it) },
                                        onToggleFavorite = { toggleFavorite(null, it) },
                                    )
                                }
                            }
                        }
                    }

                    // 책은 목록 위에 덮어서 띄운다 (닫으면 보던 목록 그대로)
                    openBook?.let { book ->
                        key(book.entry.path) {
                            when (book.entry.kind) {
                                BookKind.COMIC, BookKind.PDF -> ImageViewerScreen(
                                    origin = book.origin,
                                    entry = book.entry,
                                    nextEntry = book.next,
                                    settings = settings,
                                    onSettingsChange = changeSettings,
                                    onOpenNext = { next -> openBook = book.copy(entry = next) },
                                    onClose = { openBook = null },
                                )

                                BookKind.TXT, BookKind.EPUB -> TextReaderScreen(
                                    origin = book.origin,
                                    entry = book.entry,
                                    nextEntry = book.next,
                                    settings = settings,
                                    onSettingsChange = changeSettings,
                                    onOpenNext = { next -> openBook = book.copy(entry = next) },
                                    onClose = { openBook = null },
                                )

                                null -> LaunchedEffect(Unit) { openBook = null }
                            }
                        }
                    }
                }
              }
            }
        }
    }

    /** 뷰어가 열려 있으면 볼륨 버튼·페이지 터너 입력을 먼저 페이지 넘기기에 쓴다. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = PageKeys.handler
        if (handler != null && handler(event)) return true
        return super.dispatchKeyEvent(event)
    }
}

/** 맨 위 탭: 글자만, 고른 탭은 굵게 + 아래 밑줄. 오른쪽 끝 설정. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Box(
                    Modifier
                        .width(24.dp)
                        .height(1.5.dp)
                        .background(if (on) Ink else Paper)
                )
            }
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = Ink)
        }
    }
}
