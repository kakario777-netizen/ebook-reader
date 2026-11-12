package com.kakario.ebookreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.update.Updater

/** 선으로만 그린 진행 막대 (이잉크에서 움직이는 효과 없이). */
@Composable
private fun LineProgress(percent: Int) {
    Box(Modifier.fillMaxWidth().height(10.dp).border(1.dp, Ink)) {
        Box(Modifier.fillMaxWidth((percent.coerceIn(0, 100)) / 100f).fillMaxHeight().background(Ink))
    }
}

/** 업데이트 알림·받는 중·설치 창 — 흰 바탕, 검은 선. */
@Composable
fun UpdateDialog(state: Updater.State, updater: Updater) {
    when (state) {
        is Updater.State.Available -> LineDialog(
            title = "새 버전이 있어요",
            onDismiss = { updater.dismiss() },
            confirm = { DialogAction("업데이트", bold = true) { updater.download(state.release) } },
            dismiss = { DialogAction("나중에") { updater.dismiss() } },
        ) {
            Text("지금 빌드 #${state.current} → 새 빌드 #${state.release.build}\n받아서 바로 설치할까요?", color = Ink)
        }

        is Updater.State.Downloading -> LineDialog(title = "받는 중 ${state.percent}%", onDismiss = {}) {
            Column {
                // 이잉크라 1%마다 다시 그리지 않고 5% 단위로만
                LineProgress(state.percent / 5 * 5)
            }
        }

        is Updater.State.Ready -> LineDialog(
            title = "설치 준비 완료",
            onDismiss = { updater.dismiss() },
            confirm = { DialogAction("설치", bold = true) { updater.install(state.file) } },
            dismiss = { DialogAction("닫기") { updater.dismiss() } },
        ) {
            Column {
                Text("'설치'를 누르면 앱 안에서 바로 업데이트돼요. 앱이 잠깐 꺼졌다가 다시 켜져요.", color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "처음 한 번만 '이 출처 허용'을 켜고 '업데이트할까요?'에 답하면, 다음부터는 묻지 않고 바로 업데이트돼요." +
                        (updater.lastError?.let { "\n(지난 시도 오류: $it)" } ?: ""),
                    color = Ink,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Updater.State.Installing -> LineDialog(title = "업데이트 설치 중…", onDismiss = {}) {
            Text("끝나면 앱이 잠깐 꺼졌다가 다시 켜져요.\n안 켜지면 '업데이트 완료' 알림을 눌러 주세요.", color = Ink)
        }

        is Updater.State.UpToDate -> LineDialog(
            title = "최신 버전이에요",
            onDismiss = { updater.dismiss() },
            confirm = { DialogAction("확인") { updater.dismiss() } },
        ) {
            Text("지금 빌드 #${state.build}", color = Ink)
        }

        is Updater.State.Failed -> LineDialog(
            title = "업데이트",
            onDismiss = { updater.dismiss() },
            confirm = { DialogAction("확인") { updater.dismiss() } },
        ) {
            Text(state.message, color = Ink)
        }

        Updater.State.Checking -> LineDialog(title = null, onDismiss = { updater.dismiss() }) {
            Text("새 버전 확인하는 중…", color = Ink)
        }

        Updater.State.Idle -> Unit
    }
}
