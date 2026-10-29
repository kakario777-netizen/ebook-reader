package com.kakario.ebookreader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 이북리더의 모든 화면은 순수 흰색(#FFFFFF) 바탕에 순수 검정(#000000) 글자·선만 쓴다.
 * 회색 판, 그림자, 색 칠한 버튼 없이 1px 선과 글자로만 구분한다.
 */
val Paper = Color.White
val Ink = Color.Black

val PaperColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Paper,
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Paper,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Ink,
    surfaceTint = Paper,
    surfaceBright = Paper,
    surfaceDim = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainerLowest = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    outline = Ink,
    outlineVariant = Ink,
    error = Ink,
    onError = Paper,
    scrim = Color(0x33000000),
)

/** 1px 가로선. */
@Composable
fun Line(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Dp.Hairline, color = Ink)
}

/** 얇은 선으로 둘러싼 글자 버튼. */
@Composable
fun LineButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text,
        color = Ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(1.dp, if (enabled) Ink else Ink.copy(alpha = 0.25f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** 선 없는 글자 버튼 (머리줄 오른쪽 등). */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = Ink,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .widthIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

/** 켬/끔: 오른쪽에 선으로 그린 네모(켜면 ✓). */
@Composable
fun LineToggle(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, color = Ink, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(22.dp).border(1.dp, Ink), contentAlignment = Alignment.Center) {
            if (checked) Text("✓", color = Ink, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 여러 개 중 하나 고르기: ● / ○ */
@Composable
fun LineRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "●" else "○", color = Ink, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 숫자 조절: 이름   −  값  + */
@Composable
fun LineStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        LineButton("−", onClick = onMinus, modifier = Modifier.width(48.dp))
        Text(
            value,
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        LineButton("+", onClick = onPlus, modifier = Modifier.width(48.dp))
    }
}

/** 설정 화면의 묶음 제목. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Ink,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    )
}

/** 흰 바탕 + 검은 테두리 대화창. */
@Composable
fun LineDialog(
    title: String?,
    onDismiss: () -> Unit,
    confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, Ink),
        shape = RectangleShape,
        containerColor = Paper,
        titleContentColor = Ink,
        textContentColor = Ink,
        tonalElevation = 0.dp,
        title = if (title != null) {
            { Text(title, style = MaterialTheme.typography.titleMedium) }
        } else {
            null
        },
        text = content,
        confirmButton = { confirm?.invoke() },
        dismissButton = dismiss,
    )
}

/** 대화창 아래쪽 버튼 줄에 쓰는 글자 버튼. */
@Composable
fun DialogAction(text: String, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = Ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** 대화창 안의 메뉴 한 줄. */
@Composable
fun MenuLine(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) Ink else Ink.copy(alpha = 0.3f),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
    )
}

/** 얇은 검은 원으로 도는 불러오는 중 표시. */
@Composable
fun Spinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(28.dp),
        color = Ink,
        strokeWidth = 1.5.dp,
        trackColor = Color.Transparent,
    )
}

/** 오류 문구 + 다시 시도. */
@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, textAlign = TextAlign.Center, color = Ink)
        Spacer(Modifier.height(16.dp))
        LineButton("다시 시도", onClick = onRetry)
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
