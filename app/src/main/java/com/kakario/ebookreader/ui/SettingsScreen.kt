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
import com.kakario.ebookreader.data.LightRefresh
import com.kakario.ebookreader.data.SettingsStore
import com.kakario.ebookreader.model.formatBytes
import com.kakario.ebookreader.update.Updater

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        color = Ink,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val thumbs = LocalThumbnails.current
    val refresher = LocalRefresher.current
    val books = remember { BookCache.get(context) }
    var cacheVersion by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val s = settings

    Column(Modifier.fillMaxSize()) {
        BrowserHeader(title = "설정", subtitle = null, onBack = onClose)
        Line()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ───────── 화면 새로고침
            SectionTitle("화면 새로고침 (잔상 지우기)")
            Text(
                "이잉크 화면에 전 페이지 글자가 흐릿하게 남을 때, 화면 전체를 잠깐 칠했다가 다시 그려 지워요.",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            SubTitle("① 매 페이지 — 가볍게")
            LineChoices(
                label = null,
                options = LightRefresh.entries,
                selected = s.lightRefresh,
                text = { it.label },
            ) { onChange(s.copy(lightRefresh = it)) }
            Text(
                "글자 줄만: 앞뒤 페이지의 글자 부분만 아주 짧게 검게 칠했다 지워요. 빠르고 글자가 진하게 남아요.",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            LineChoices(
                label = "시간",
                options = SettingsStore.LIGHT_MS_OPTIONS,
                selected = s.lightMs,
                text = { "${it}ms" },
            ) { onChange(s.copy(lightMs = it)) }

            SubTitle("② 여러 페이지마다 — 전체를 진하게")
            LineChoices(
                label = null,
                options = SettingsStore.FULL_EVERY_OPTIONS,
                selected = s.fullEvery,
                text = { if (it == 0) "끔" else "${it}장" },
            ) { onChange(s.copy(fullEvery = it)) }
            LineChoices(
                label = "검게 칠하는 시간",
                options = SettingsStore.FULL_MS_OPTIONS,
                selected = s.fullMs,
                text = { String.format("%.1f초", it / 1000f) },
            ) { onChange(s.copy(fullMs = it)) }
            LineToggle(
                "기기 고화질 모드 쓰기 (오닉스·sam)",
                s.deviceRefresh,
                sub = if (EinkDevice.supported) "이 기기는 지원해요 — 기기 기본 뷰어처럼 Regal 모드로 그리고, 전체 새로고침도 기기가 해요"
                else "이 기기에서는 쓸 수 없어요 — 앱이 직접 깜빡여 새로고침해요",
            ) { onChange(s.copy(deviceRefresh = it)) }
            LineToggle(
                "목록·화면이 바뀔 때도",
                s.refreshOnScreens,
                sub = "탭을 옮기거나 폴더·책을 열고 닫을 때",
            ) { onChange(s.copy(refreshOnScreens = it)) }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                LineButton("지금 한 번 해보기") { refresher?.now() }
            }
            Spacer(Modifier.height(8.dp))
            Line()

            // ───────── 글자 책
            SectionTitle("글자 책 (TXT · EPUB)")
            FontOptions(s, onChange)
            Spacer(Modifier.height(8.dp))
            Line()

            // ───────── 만화
            SectionTitle("만화 (ZIP)")
            ComicLayout.entries.forEach { layout ->
                LineRadio(layout.label, s.comicLayout == layout) {
                    onChange(s.copy(comicLayout = layout))
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
                s.coverAlone,
                sub = "두 장 보기에서 첫 장(표지)만 혼자 보여주고 다음부터 두 장씩",
            ) { onChange(s.copy(coverAlone = it)) }
            LineToggle(
                "두 번 톡 쳐서 확대",
                s.doubleTapZoom,
                sub = "끄면 두 번 톡이 그냥 넘기기로 동작해요 (두 손가락 확대는 언제나 돼요)",
            ) { onChange(s.copy(doubleTapZoom = it)) }
            LineToggle(
                "넘길 때 미끄러지는 효과",
                s.slideAnimation,
                sub = "이잉크에서는 끄는 게 좋아요 (끄면 바로 바뀜)",
            ) { onChange(s.copy(slideAnimation = it)) }
            LineStepper(
                "FTP 동시 연결",
                "${s.ftpConnections}개",
                onMinus = { onChange(s.copy(ftpConnections = (s.ftpConnections - 1).coerceAtLeast(1))) },
                onPlus = { onChange(s.copy(ftpConnections = (s.ftpConnections + 1).coerceAtMost(4))) },
            )
            LineStepper(
                "미리 받을 페이지",
                "${s.prefetchPages}장",
                onMinus = { onChange(s.copy(prefetchPages = (s.prefetchPages - 2).coerceAtLeast(2))) },
                onPlus = { onChange(s.copy(prefetchPages = (s.prefetchPages + 2).coerceAtMost(12))) },
            )
            Spacer(Modifier.height(8.dp))
            Line()

            // ───────── 화면 전체
            SectionTitle("화면 전체")
            LineChoices(
                label = "앱 글자 굵기",
                options = listOf(0, 1, 2, 3),
                selected = s.uiWeight,
                text = { listOf("보통", "조금", "굵게", "아주")[it] },
            ) { onChange(s.copy(uiWeight = it)) }
            LineStepper(
                "앱 글자 크기",
                "${s.uiScale}%",
                onMinus = { onChange(s.copy(uiScale = (s.uiScale - 5).coerceAtLeast(SettingsStore.UI_SCALE_MIN))) },
                onPlus = { onChange(s.copy(uiScale = (s.uiScale + 5).coerceAtMost(SettingsStore.UI_SCALE_MAX))) },
            )
            LineStepper(
                "책장 한 줄 권수",
                "${s.shelfColumns}권",
                onMinus = { onChange(s.copy(shelfColumns = (s.shelfColumns - 1).coerceAtLeast(SettingsStore.SHELF_MIN))) },
                onPlus = { onChange(s.copy(shelfColumns = (s.shelfColumns + 1).coerceAtMost(SettingsStore.SHELF_MAX))) },
            )
            LineToggle("목록에 표지 보이기", s.listCovers, sub = "끄면 글자만 보여요") {
                onChange(s.copy(listCovers = it))
            }
            LineToggle("보는 중 시간·진행률 표시", s.showInfoBar) {
                onChange(s.copy(showInfoBar = it))
            }
            LineToggle("볼륨 버튼으로 넘기기", s.volumeKeys, sub = "아래 = 다음, 위 = 이전") {
                onChange(s.copy(volumeKeys = it))
            }
            Spacer(Modifier.height(8.dp))
            Line()

            // ───────── 저장공간
            SectionTitle("앱 정보")
            Text(
                "이북리더 빌드 #${Updater.get(context).currentBuild}  ·  켤 때마다 새 버전을 자동으로 확인해요",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                LineButton("새 버전 확인") { Updater.get(context).check(quiet = false) }
            }
            Spacer(Modifier.height(8.dp))
            Line()

            SectionTitle("저장공간")
            val thumbBytes = remember(cacheVersion) { thumbs?.diskCacheBytes() ?: 0L }
            val bookBytes = remember(cacheVersion) { books.sizeBytes() }
            val prepCache = remember { com.kakario.ebookreader.book.PreparedCache.get(context) }
            val prepBytes = remember(cacheVersion) { prepCache.sizeBytes() }
            Text(
                "표지 ${formatBytes(thumbBytes)}  ·  FTP에서 받아둔 책 ${formatBytes(bookBytes)}  ·  TXT 정리본 ${formatBytes(prepBytes)}",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            val sizeOptions = listOf(100, 300, 500, 1000, 2000, 5000, 0)
            val sizeText: (Int) -> String = { mb ->
                when {
                    mb == 0 -> "무제한"
                    mb >= 1000 -> "${mb / 1000}GB"
                    else -> "${mb}MB"
                }
            }
            LineChoices(
                label = "받아둔 책 최대",
                options = (sizeOptions + s.bookCacheMb).distinct().sortedBy { if (it == 0) Int.MAX_VALUE else it },
                selected = s.bookCacheMb,
                text = sizeText,
            ) { onChange(s.copy(bookCacheMb = it)) }
            LineChoices(
                label = "TXT 정리본 최대",
                options = (sizeOptions + s.preparedCacheMb).distinct().sortedBy { if (it == 0) Int.MAX_VALUE else it },
                selected = s.preparedCacheMb,
                text = sizeText,
            ) { onChange(s.copy(preparedCacheMb = it)) }
            Text(
                "넘치면 오래 안 연 것부터 지워요. 정리본은 서버에도 있어서 지워져도 다시 받아 바로 열려요.",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
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
                    com.kakario.ebookreader.book.PreparedCache.get(context).clear()
                    cacheVersion++
                    confirmClear = false
                }
            },
            dismiss = { DialogAction("취소") { confirmClear = false } },
        ) {
            Text("표지·받아둔 책·TXT 정리본을 지워요.\n읽은 위치·책장·즐겨찾기·서버 정보는 그대로예요.", color = Ink)
        }
    }
}
