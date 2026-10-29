package com.kakario.ebookreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.book.BookCache
import com.kakario.ebookreader.data.AppSettings
import com.kakario.ebookreader.data.ComicLayout
import com.kakario.ebookreader.data.SettingsStore
import com.kakario.ebookreader.model.formatBytes

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val thumbs = LocalThumbnails.current
    val books = remember { BookCache.get(context) }
    var cacheVersion by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BrowserHeader(title = "설정", subtitle = null, onBack = onClose)
        Line()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            SectionTitle("만화 (ZIP)")
            ComicLayout.entries.forEach { layout ->
                LineRadio(layout.label, settings.comicLayout == layout) {
                    onChange(settings.copy(comicLayout = layout))
                }
            }
            Text(
                "만화를 열면 이 방식대로 화면을 강제로 돌려요. 목록·글자 책은 그대로예요.",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LineToggle(
                "표지 따로 보기",
                settings.coverAlone,
                sub = "두 장 보기에서 첫 장(표지)만 혼자 보여주고 다음부터 두 장씩",
            ) { onChange(settings.copy(coverAlone = it)) }
            LineToggle(
                "넘길 때 미끄러지는 효과",
                settings.slideAnimation,
                sub = "끄면 효과 없이 바로 바뀌어요",
            ) { onChange(settings.copy(slideAnimation = it)) }
            LineStepper(
                "FTP 동시 연결",
                "${settings.ftpConnections}개",
                onMinus = { onChange(settings.copy(ftpConnections = (settings.ftpConnections - 1).coerceAtLeast(1))) },
                onPlus = { onChange(settings.copy(ftpConnections = (settings.ftpConnections + 1).coerceAtMost(4))) },
            )
            LineStepper(
                "미리 받을 페이지",
                "${settings.prefetchPages}장",
                onMinus = { onChange(settings.copy(prefetchPages = (settings.prefetchPages - 2).coerceAtLeast(2))) },
                onPlus = { onChange(settings.copy(prefetchPages = (settings.prefetchPages + 2).coerceAtMost(12))) },
            )
            Spacer(Modifier.height(8.dp))
            Line()

            SectionTitle("글자 책 (TXT · EPUB)")
            LineStepper(
                "글자 크기",
                "${settings.fontSize}",
                onMinus = { onChange(settings.copy(fontSize = (settings.fontSize - 1).coerceAtLeast(SettingsStore.FONT_MIN))) },
                onPlus = { onChange(settings.copy(fontSize = (settings.fontSize + 1).coerceAtMost(SettingsStore.FONT_MAX))) },
            )
            LineStepper(
                "줄 간격",
                String.format("%.1f", settings.lineSpacing / 10f),
                onMinus = { onChange(settings.copy(lineSpacing = (settings.lineSpacing - 1).coerceAtLeast(SettingsStore.LINE_MIN))) },
                onPlus = { onChange(settings.copy(lineSpacing = (settings.lineSpacing + 1).coerceAtMost(SettingsStore.LINE_MAX))) },
            )
            LineStepper(
                "좌우 여백",
                "${settings.margin}",
                onMinus = { onChange(settings.copy(margin = (settings.margin - 2).coerceAtLeast(SettingsStore.MARGIN_MIN))) },
                onPlus = { onChange(settings.copy(margin = (settings.margin + 2).coerceAtMost(SettingsStore.MARGIN_MAX))) },
            )
            Spacer(Modifier.height(8.dp))
            Line()

            SectionTitle("공통")
            LineToggle("목록에 표지 보이기", settings.listCovers, sub = "끄면 글자만 보여요") {
                onChange(settings.copy(listCovers = it))
            }
            LineToggle("보는 중 시간·진행률 표시", settings.showInfoBar) {
                onChange(settings.copy(showInfoBar = it))
            }
            LineToggle("볼륨 버튼으로 넘기기", settings.volumeKeys, sub = "아래 = 다음, 위 = 이전") {
                onChange(settings.copy(volumeKeys = it))
            }
            Spacer(Modifier.height(8.dp))
            Line()

            SectionTitle("저장공간")
            val thumbBytes = remember(cacheVersion) { thumbs?.diskCacheBytes() ?: 0L }
            val bookBytes = remember(cacheVersion) { books.sizeBytes() }
            Text(
                "표지 ${formatBytes(thumbBytes)}  ·  FTP에서 받아둔 책 ${formatBytes(bookBytes)}",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                LineButton("모두 지우기") { confirmClear = true }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmClear) {
        LineDialog(
            title = "저장공간 비우기",
            onDismiss = { confirmClear = false },
            confirm = {
                DialogAction("지우기", bold = true) {
                    thumbs?.clear()
                    books.clear()
                    cacheVersion++
                    confirmClear = false
                }
            },
            dismiss = { DialogAction("취소") { confirmClear = false } },
        ) {
            Text("표지와 받아둔 책을 지워요.\n읽은 위치·즐겨찾기·서버 정보는 그대로예요.", color = Ink)
        }
    }
}
