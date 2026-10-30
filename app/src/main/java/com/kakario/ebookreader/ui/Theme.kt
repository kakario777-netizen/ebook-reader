package com.kakario.ebookreader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 이북리더의 모든 화면은 순수 흰색(#FFFFFF) 바탕에 순수 검정(#000000) 글자·선만 쓴다.
 * 회색 판, 그림자, 색 칠한 버튼 없이 1px 선과 글자로만 구분한다.
 * 이잉크 화면에 맞춰 누를 때 번지는 물결 효과, 당겨지는 효과, 계속 도는 표시도 없앤다.
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

/**
 * 앱 전체 글자: 기본보다 굵게(weightBoost 단계 × 100) 그리고 scale만큼 크게.
 * 이잉크에서 가는 회색빛 글자가 되지 않도록 진하게 쓴다.
 */
fun inkTypography(weightBoost: Int, scale: Float): Typography {
    val t = Typography()
    fun TextStyle.ink(base: Int) = copy(
        fontWeight = FontWeight((base + weightBoost * 100).coerceIn(100, 900)),
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
        color = Ink,
    )
    return Typography(
        displayLarge = t.displayLarge.ink(400),
        displayMedium = t.displayMedium.ink(400),
        displaySmall = t.displaySmall.ink(400),
        headlineLarge = t.headlineLarge.ink(400),
        headlineMedium = t.headlineMedium.ink(400),
        headlineSmall = t.headlineSmall.ink(500),
        titleLarge = t.titleLarge.ink(500),
        titleMedium = t.titleMedium.ink(500),
        titleSmall = t.titleSmall.ink(500),
        bodyLarge = t.bodyLarge.ink(400),
        bodyMedium = t.bodyMedium.ink(400),
        bodySmall = t.bodySmall.ink(400),
        labelLarge = t.labelLarge.ink(500),
        labelMedium = t.labelMedium.ink(500),
        labelSmall = t.labelSmall.ink(500),
    )
}

/** 누를 때 아무 효과도 그리지 않는다 (이잉크 잔상 방지). */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InkTheme(weightBoost: Int, scalePercent: Int, content: @Composable () -> Unit) {
    val typography = remember(weightBoost, scalePercent) { inkTypography(weightBoost, scalePercent / 100f) }
    MaterialTheme(colorScheme = PaperColors, typography = typography) {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalRippleConfiguration provides null,
            LocalOverscrollConfiguration provides null,
            content = content,
        )
    }
}

/** 1px 가로선. */
@Composable
fun Line(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Dp.Hairline, color = Ink)
}

/** 아래로 전달되는 탭을 막는다 (메뉴 판의 빈 곳을 눌러도 페이지가 넘어가지 않게). */
fun Modifier.blockTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

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
        color = if (enabled) Ink else Ink.copy(alpha = 0.3f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (bold) FontWeight.Bold else null,
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(if (bold) 2.dp else 1.dp, if (enabled) Ink else Ink.copy(alpha = 0.25f))
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

/** 그림 버튼 (뒤로·설정·새로고침). */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Ink)
    }
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
        Box(Modifier.size(22.dp).border(1.5.dp, Ink), contentAlignment = Alignment.Center) {
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
            fontWeight = if (selected) FontWeight.Bold else null,
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
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        LineButton("−", onClick = onMinus, modifier = Modifier.width(48.dp))
        Text(
            value,
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp),
        )
        LineButton("+", onClick = onPlus, modifier = Modifier.width(48.dp))
    }
}

/** 몇 개 중 하나를 버튼 줄로 고르기 (고른 것은 굵은 테두리). */
@Composable
fun <T> LineChoices(
    label: String?,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        if (label != null) {
            Text(label, color = Ink, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                LineButton(text(o), bold = o == selected, modifier = Modifier.weight(1f)) { onSelect(o) }
            }
        }
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
        modifier = Modifier.border(1.5.dp, Ink),
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
        fontWeight = if (bold) FontWeight.Bold else null,
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

/**
 * 불러오는 중 표시. 이잉크는 계속 움직이는 그림이 잔상을 남기므로 도는 원 대신 글자만.
 */
@Composable
fun Spinner(modifier: Modifier = Modifier, text: String = "불러오는 중…") {
    Text(text, color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
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
