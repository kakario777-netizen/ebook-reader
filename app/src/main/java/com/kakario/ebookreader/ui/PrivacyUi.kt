package com.kakario.ebookreader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.data.Privacy

/**
 * 개인모드 버튼을 눌렀을 때의 흐름.
 *  - 잠김 + 코드 없음 → 새 코드 두 번 입력
 *  - 잠김 → 코드 입력해서 켜기
 *  - 켜짐 → 끄기(잠그기) / 코드 바꾸기
 */
@Composable
fun PrivacyFlow(onClose: () -> Unit) {
    var step by remember {
        mutableStateOf(
            when {
                Privacy.unlocked -> "menu"
                !Privacy.hasPin -> "new1"
                else -> "enter"
            }
        )
    }
    var first by remember { mutableStateOf("") }

    when (step) {
        "menu" -> LineDialog(
            title = "개인모드 켜짐",
            onDismiss = onClose,
            dismiss = { DialogAction("닫기", onClick = onClose) },
        ) {
            Column {
                Text(
                    "목록·책장에서 길게 눌러 '숨기기'로 지정한 것은 개인모드를 끄면 어디에도 안 보여요.",
                    color = Ink,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Line()
                MenuLine("개인모드 끄기 (숨기기)") {
                    Privacy.lock()
                    onClose()
                }
                MenuLine("숫자 코드 바꾸기") { step = "new1" }
            }
        }

        "enter" -> PinPad(title = "개인모드 숫자 코드", onCancel = onClose) { code ->
            if (Privacy.check(code)) {
                Privacy.unlock()
                onClose()
                null
            } else "코드가 달라요"
        }

        "new1" -> PinPad(title = "새 숫자 코드 4자리", onCancel = onClose) { code ->
            first = code
            step = "new2"
            null
        }

        "new2" -> PinPad(title = "한 번 더 입력", onCancel = onClose) { code ->
            if (code == first) {
                Privacy.setPin(code)
                Privacy.unlock()
                onClose()
                null
            } else {
                step = "new1"
                "두 번이 달라요. 처음부터 다시"
            }
        }
    }
}

/** 4자리 숫자판. onDone이 오류 문구를 돌려주면 지우고 다시 입력. */
@Composable
fun PinPad(title: String, onCancel: () -> Unit, onDone: (String) -> String?) {
    var code by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }

    fun press(d: String) {
        if (code.length >= 4) return
        code += d
        error = null
        if (code.length == 4) {
            val err = onDone(code)
            code = ""
            error = err
        }
    }

    LineDialog(title = title, onDismiss = onCancel) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .border(1.5.dp, Ink, CircleShape)
                            .then(if (i < code.length) Modifier.background(Ink, CircleShape) else Modifier)
                    )
                }
            }
            Text(
                error ?: " ",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "취소", "0", "지움")
            keys.chunked(3).forEach { row ->
                Row {
                    row.forEach { k ->
                        Text(
                            k,
                            color = Ink,
                            style = if (k.length == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(5.dp)
                                .width(76.dp)
                                .border(1.dp, Ink)
                                .clickable {
                                    when (k) {
                                        "취소" -> onCancel()
                                        "지움" -> code = code.dropLast(1)
                                        else -> press(k)
                                    }
                                }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
