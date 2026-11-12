package com.kakario.ebookreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kakario.ebookreader.book.Reflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.data.AppSettings
import com.kakario.ebookreader.data.SettingsStore

/** 글자 책 글꼴 세부 설정 — 설정 화면과 책 보는 중 메뉴에서 같이 쓴다. */
@Composable
fun FontOptions(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val s = settings
    Column {
        LineToggle(
            "글자 또렷하게 (흑백)",
            s.crispText,
            sub = "글자 가장자리의 회색을 없애 순수 검정으로 그려요. 이잉크에서 글자가 뭉개지거나 계단처럼 깨지는 걸 막아요",
        ) { onChange(s.copy(crispText = it)) }
        if (s.crispText) {
            LineChoices(
                label = "또렷하게 진하기",
                options = listOf(1, 2, 3, 4, 5),
                selected = s.crispLevel,
                text = { "$it" },
            ) { onChange(s.copy(crispLevel = it)) }
        }
        LineChoices(
            label = "TXT 잘린 문장 잇기",
            options = Reflow.Mode.entries,
            selected = s.txtReflow,
            text = { it.label },
        ) { onChange(s.copy(txtReflow = it)) }
        LineChoices(
            label = "TXT 보기",
            options = listOf(false, true),
            selected = s.sentencePerLine,
            text = { if (it) "문장마다 줄바꿈" else "기본 보기" },
        ) { onChange(s.copy(sentencePerLine = it)) }
        Text(
            "줄마다 엔터로 끊어 둔 TXT를 알아보고 문장을 다시 이어요. 자동은 잘린 파일일 때만 이어요.",
            color = Ink,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        LineToggle(
            "TXT 장 제목 자동으로 찾기",
            s.txtChapters,
            sub = "제1장·12화·프롤로그 같은 줄을 찾아 새 페이지 맨 위에 크게 보여주고 목차를 만들어요 (파일은 그대로)",
        ) { onChange(s.copy(txtChapters = it)) }

        // 글꼴: 기본 버튼 + 다른 글꼴 목록(샘플 문장으로 보고 고르기)
        var picker by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text("글꼴", color = Ink, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val isDefault = s.readerFont == "noto-sans-kr"
                LineButton("기본 글꼴", bold = isDefault, modifier = Modifier.weight(1f)) {
                    onChange(s.copy(readerFont = "noto-sans-kr"))
                }
                LineButton(
                    if (isDefault) "다른 글꼴 고르기 ›" else AppFonts.info(s.readerFont).name + " ›",
                    bold = !isDefault,
                    modifier = Modifier.weight(1.4f),
                ) { picker = true }
            }
        }
        if (picker) {
            FontPicker(
                selected = s.readerFont,
                onSelect = { onChange(s.copy(readerFont = it)) },
                onClose = { picker = false },
            )
        }

        LineStepper(
            "글자 크기",
            "${s.fontSize}",
            onMinus = { onChange(s.copy(fontSize = (s.fontSize - 1).coerceAtLeast(SettingsStore.FONT_MIN))) },
            onPlus = { onChange(s.copy(fontSize = (s.fontSize + 1).coerceAtMost(SettingsStore.FONT_MAX))) },
        )
        LineStepper(
            "굵기",
            SettingsStore.weightName(s.fontWeight),
            onMinus = { onChange(s.copy(fontWeight = (s.fontWeight - 100).coerceAtLeast(SettingsStore.WEIGHT_MIN))) },
            onPlus = { onChange(s.copy(fontWeight = (s.fontWeight + 100).coerceAtMost(SettingsStore.WEIGHT_MAX))) },
        )
        LineStepper(
            "획 두껍게 보정",
            if (s.strokeBoost == 0) "끔" else "${s.strokeBoost}",
            onMinus = { onChange(s.copy(strokeBoost = (s.strokeBoost - 1).coerceAtLeast(0))) },
            onPlus = { onChange(s.copy(strokeBoost = (s.strokeBoost + 1).coerceAtMost(SettingsStore.STROKE_MAX))) },
        )
        LineStepper(
            "줄 간격",
            String.format("%.1f배", s.lineSpacing / 10f),
            onMinus = { onChange(s.copy(lineSpacing = (s.lineSpacing - 1).coerceAtLeast(SettingsStore.LINE_MIN))) },
            onPlus = { onChange(s.copy(lineSpacing = (s.lineSpacing + 1).coerceAtMost(SettingsStore.LINE_MAX))) },
        )
        LineStepper(
            "글자 간격",
            when {
                s.letterSpacing == 0 -> "기본"
                s.letterSpacing > 0 -> "+${s.letterSpacing}"
                else -> "${s.letterSpacing}"
            },
            onMinus = { onChange(s.copy(letterSpacing = (s.letterSpacing - 1).coerceAtLeast(SettingsStore.LETTER_MIN))) },
            onPlus = { onChange(s.copy(letterSpacing = (s.letterSpacing + 1).coerceAtMost(SettingsStore.LETTER_MAX))) },
        )
        LineStepper(
            "들여쓰기",
            if (s.indent == 0) "없음" else "${s.indent}칸",
            onMinus = { onChange(s.copy(indent = (s.indent - 1).coerceAtLeast(0))) },
            onPlus = { onChange(s.copy(indent = (s.indent + 1).coerceAtMost(SettingsStore.INDENT_MAX))) },
        )
        LineChoices(
            label = "정렬",
            options = listOf(true, false),
            selected = s.justify,
            text = { if (it) "양쪽 맞춤" else "왼쪽 맞춤" },
        ) { onChange(s.copy(justify = it)) }

        Column(Modifier.padding(top = 6.dp)) {
            MarginStepper("위 여백", s.marginTop) { onChange(s.copy(marginTop = it)) }
            MarginStepper("아래 여백", s.marginBottom) { onChange(s.copy(marginBottom = it)) }
            MarginStepper("왼쪽 여백", s.marginLeft) { onChange(s.copy(marginLeft = it)) }
            MarginStepper("오른쪽 여백", s.marginRight) { onChange(s.copy(marginRight = it)) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            LineButton("글자 설정 처음값으로") {
                val d = AppSettings()
                onChange(
                    s.copy(
                        readerFont = d.readerFont,
                        fontSize = d.fontSize,
                        fontWeight = d.fontWeight,
                        strokeBoost = d.strokeBoost,
                        lineSpacing = d.lineSpacing,
                        letterSpacing = d.letterSpacing,
                        indent = d.indent,
                        justify = d.justify,
                        marginTop = d.marginTop,
                        marginBottom = d.marginBottom,
                        marginLeft = d.marginLeft,
                        marginRight = d.marginRight,
                    )
                )
            }
        }
    }
}

@Composable
private fun MarginStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    LineStepper(
        label,
        "$value",
        onMinus = { onChange((value - 2).coerceAtLeast(0)) },
        onPlus = { onChange((value + 2).coerceAtMost(SettingsStore.MARGIN_MAX)) },
    )
}
