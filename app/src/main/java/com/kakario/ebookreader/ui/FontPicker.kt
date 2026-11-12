package com.kakario.ebookreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val SAMPLE = "다람쥐 헌 쳇바퀴에 타고파.\n그는 오래된 문을 천천히 밀어 열었다. 1234 ABC"

/**
 * 글꼴 고르기: 화면 전체를 덮는 목록. 글꼴마다 샘플 문장을 그 글꼴로 보여준다.
 * 아직 안 받은 글꼴은 '받기'를 누르면 받은 뒤 샘플이 보이고, 누르면 바로 선택된다.
 */
@Composable
fun FontPicker(selected: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    // 받는 중인 글꼴: id → 진행 문구
    val busy = remember { mutableStateMapOf<String, String>() }
    val version = AppFonts.version

    fun fetch(id: String, thenSelect: Boolean) {
        if (busy.containsKey(id)) return
        busy[id] = "받는 중…"
        scope.launch {
            try {
                AppFonts.download(id) { done -> busy[id] = "받는 중 ${done / (1024 * 1024)}MB" }
                busy.remove(id)
                if (thenSelect) onSelect(id)
            } catch (e: CancellationException) {
                busy.remove(id)
                throw e
            } catch (e: Exception) {
                busy.remove(id)
                busy["$id!err"] = e.message ?: "오류"
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Paper)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "닫기", onClose)
                Text("글꼴 고르기", color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextAction("모두 받기") {
                    AppFonts.catalog.filter { !AppFonts.isReady(it.id) }.forEach { fetch(it.id, thenSelect = false) }
                }
            }
            Line()
            LazyColumn(Modifier.fillMaxSize()) {
                items(AppFonts.catalog, key = { it.id }) { f ->
                    val ready = remember(version, f.id) { AppFonts.isReady(f.id) }
                    val family = remember(version, f.id, ready) { if (ready) AppFonts.family(f.id) else null }
                    val on = f.id == selected
                    val status = busy[f.id]
                    val err = busy["${f.id}!err"]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                busy.remove("${f.id}!err")
                                if (ready) onSelect(f.id) else fetch(f.id, thenSelect = true)
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (on) "●" else "○", color = Ink, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                f.name,
                                color = Ink,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (on) FontWeight.Bold else null,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                when {
                                    status != null -> status
                                    f.builtIn -> f.kind
                                    ready -> "${f.kind} · 받음"
                                    else -> "${f.kind} · 받기"
                                },
                                color = Ink,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (family != null) {
                            Text(
                                SAMPLE,
                                color = Ink,
                                fontFamily = family,
                                fontSize = 19.sp,
                                lineHeight = 28.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                f.note + " — 누르면 받아서 샘플을 보여줘요",
                                color = Ink,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (err != null) {
                            Text("받지 못했어요: $err", color = Ink, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Line()
                }
            }
        }
    }
}
