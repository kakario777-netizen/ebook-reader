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
import androidx.compose.runtime.SideEffect
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
import com.kakario.ebookreader.data.ShelfStore
import com.kakario.ebookreader.data.SettingsStore
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.BookKind
import com.kakario.ebookreader.model.FileEntry
import com.kakario.ebookreader.model.folderName
import com.kakario.ebookreader.thumb.ThumbnailLoader
import androidx.compose.runtime.collectAsState
import com.kakario.ebookreader.ui.AppFonts
import com.kakario.ebookreader.ui.EinkDevice
import com.kakario.ebookreader.ui.UpdateDialog
import com.kakario.ebookreader.update.Updater
import com.kakario.ebookreader.ui.FavoritesScreen
import com.kakario.ebookreader.ui.FtpLocation
import com.kakario.ebookreader.ui.FtpTab
import com.kakario.ebookreader.ui.IconAction
import com.kakario.ebookreader.ui.ImageViewerScreen
import com.kakario.ebookreader.ui.InkTheme
import com.kakario.ebookreader.ui.LocalRefresher
import com.kakario.ebookreader.ui.LocalShelf
import com.kakario.ebookreader.ui.RefreshOverlay
import com.kakario.ebookreader.ui.Refresher
import com.kakario.ebookreader.ui.Shelf
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

private val TABS = listOf("책장", "FTP", "내 폰")
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
        AppFonts.init(applicationContext)
        val shelf = Shelf(ShelfStore(applicationContext))
        val refresher = Refresher()
        val updater = Updater.get(applicationContext)
        // 켤 때마다 조용히 새 버전 확인 (있을 때만 창이 뜸)
        if (savedInstanceState == null) updater.check(quiet = true)
        askNotificationPermission()

        com.kakario.ebookreader.data.Privacy.init(applicationContext)
        setContent {
            var settings by remember { mutableStateOf(settingsStore.load()) }
            val changeSettings: (com.kakario.ebookreader.data.AppSettings) -> Unit = {
                settings = it
                settingsStore.save(it)
            }

            SideEffect {
                refresher.update(settings)
                com.kakario.ebookreader.book.BookCache.maxBytes = settings.bookCacheMb.toLong() * 1024 * 1024
                com.kakario.ebookreader.book.PreparedCache.maxBytes = settings.preparedCacheMb.toLong() * 1024 * 1024
            }

            InkTheme(weightBoost = settings.uiWeight, scalePercent = settings.uiScale) {
              CompositionLocalProvider(
                LocalThumbnails provides thumbnails,
                LocalListCovers provides settings.listCovers,
                com.kakario.ebookreader.ui.LocalBrowseLayout provides com.kakario.ebookreader.ui.BrowseLayout(
                    grid = settings.listGrid,
                    columns = settings.listColumns,
                    sort = settings.listSort,
                    desc = settings.listDesc,
                    foldersTop = settings.foldersTop,
                    onChange = { l ->
                        changeSettings(
                            settings.copy(
                                listGrid = l.grid, listColumns = l.columns, listSort = l.sort,
                                listDesc = l.desc, foldersTop = l.foldersTop,
                            )
                        )
                    },
                ),
                LocalShelf provides shelf,
                LocalRefresher provides refresher,
              ) {
                var settingsOpen by remember { mutableStateOf(false) }
                var openBook by remember { mutableStateOf<OpenBook?>(null) }
                // 책을 보는 동안엔 표지 만들기를 멈춰 보는 쪽에 속도를 양보
                LaunchedEffect(openBook != null) { thumbnails.paused.value = openBook != null }
                val servers = remember { mutableStateListOf<FtpServer>().apply { addAll(serverStore.load()) } }
                // 서버 동기화: 서버 목록을 알려주고, 처음 한 번 폰에 있던 읽기 기록을 모두 올린다 (꺼도 이어서)
                val sync = remember { com.kakario.ebookreader.data.ServerSync.get(applicationContext) }
                LaunchedEffect(servers.toList()) {
                    sync.setServers(servers.toList())
                    sync.startInitialUpload()
                }
                val syncStatus by sync.status.collectAsState()
                val openScope = androidx.compose.runtime.rememberCoroutineScope()
                val favorites = remember { mutableStateListOf<Favorite>().apply { addAll(favoriteStore.load()) } }
                var tab by remember { mutableIntStateOf(TAB_FAVORITES) }

                // 각 탭의 현재 위치 — 탭을 옮겨 다녀도 그대로 남는다.
                var ftpLocation by remember { mutableStateOf<FtpLocation?>(null) }
                var localPath by remember { mutableStateOf<String?>(null) }
                var privacyOpen by remember { mutableStateOf(false) }
                // 개인모드를 끄면 숨긴 곳에 들어가 있던 화면에서 나온다
                LaunchedEffect(com.kakario.ebookreader.data.Privacy.unlocked, com.kakario.ebookreader.data.Privacy.hidden) {
                    val pv = com.kakario.ebookreader.data.Privacy
                    if (!pv.unlocked) {
                        ftpLocation?.let { loc ->
                            if (!pv.showServer(loc.server.id) || (loc.path != null && !pv.show("ftp${loc.server.id}", loc.path))) ftpLocation = null
                        }
                        localPath?.let { if (!pv.show("local", it)) localPath = null }
                    }
                }

                // 열려 있는 서버의 FTP 연결 (서버가 바뀌거나 닫히면 끊는다)
                val openServer = ftpLocation?.server
                val ftpBrowser = remember(openServer) { openServer?.let { FtpBrowser(it) } }
                DisposableEffect(ftpBrowser) {
                    onDispose { ftpBrowser?.let { b -> GlobalScope.launch { b.close() } } }
                }

                // 화면이 바뀌면(탭·폴더·책 열고 닫기·설정) 이잉크 새로고침
                var firstScreen by remember { mutableStateOf(true) }
                LaunchedEffect(tab, settingsOpen, ftpLocation?.server?.id, ftpLocation?.path, localPath, openBook?.entry?.path) {
                    if (firstScreen) firstScreen = false else refresher.screenChange()
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
                            syncStatus?.let { msg ->
                                Text(
                                    msg,
                                    color = Ink,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            TabBar(
                                selected = tab,
                                onSelect = { tab = it },
                                onSettings = { settingsOpen = true },
                                privacyOn = com.kakario.ebookreader.data.Privacy.unlocked,
                                onPrivacy = { privacyOpen = true },
                            )
                            if (privacyOpen) com.kakario.ebookreader.ui.PrivacyFlow(onClose = { privacyOpen = false })
                            Line()
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (tab) {
                                    TAB_FAVORITES -> FavoritesScreen(
                                        favorites = favorites,
                                        servers = servers,
                                        shelf = shelf,
                                        onCheckUpdate = { updater.check(quiet = false) },
                                        columns = settings.shelfColumns,
                                        onColumnsChange = { changeSettings(settings.copy(shelfColumns = it)) },
                                        sort = settings.shelfSort,
                                        onSortChange = { changeSettings(settings.copy(shelfSort = it)) },
                                        onOpenBook = { origin, b ->
                                            val e = b.toEntry()
                                            openScope.launch {
                                                origin.server?.let { sync.pullBeforeOpen(it, e) }
                                                openBook = OpenBook(origin, e, listOf(e))
                                            }
                                        },
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
                                            ftpLocation?.let { loc ->
                                                // 열기 전에 서버 기록 확인 (다른 기기에서 더 읽었으면 거기부터)
                                                openScope.launch {
                                                    sync.pullBeforeOpen(loc.server, entry)
                                                    openBook = OpenBook(Origin(loc.server), entry, siblings)
                                                }
                                            }
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
                                            shelf.removeServer(server.id)
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

                    // 오닉스 계열 기기면 앱 화면 전체를 기기 기본 뷰어처럼 고화질(Regal) 모드로
                    val rootView = androidx.compose.ui.platform.LocalView.current
                    DisposableEffect(rootView, settings.deviceRefresh) {
                        if (settings.deviceRefresh) EinkDevice.applyReadingMode(rootView) else EinkDevice.resetMode(rootView)
                        onDispose { EinkDevice.resetMode(rootView) }
                    }

                    // 맨 위: 이잉크 새로고침 깜빡임
                    RefreshOverlay(refresher)

                    // 업데이트 창 (어느 화면에서든)
                    val updateState by updater.state.collectAsState()
                    UpdateDialog(updateState, updater)
                }
              }
            }
        }
    }

    /** 업데이트 완료 알림을 띄우려면 안드로이드 13+에서 알림 허용이 필요하다. */
    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
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
private fun TabBar(selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit, privacyOn: Boolean, onPrivacy: () -> Unit) {
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
                    fontWeight = if (on) FontWeight.Black else null,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Box(
                    Modifier
                        .width(28.dp)
                        .height(2.5.dp)
                        .background(if (on) Ink else Paper)
                )
            }
        }
        Box(Modifier.weight(1f))
        // 개인모드: 켜져 있으면 굵게 + 밑줄
        Text(
            if (privacyOn) "개인모드 켜짐" else "개인모드",
            color = Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (privacyOn) FontWeight.Black else null,
            textDecoration = if (privacyOn) androidx.compose.ui.text.style.TextDecoration.Underline else null,
            modifier = Modifier.clickable(onClick = onPrivacy).padding(horizontal = 10.dp, vertical = 12.dp),
        )
        IconAction(Icons.Outlined.Settings, "설정", onSettings)
    }
}
