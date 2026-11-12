package com.kakario.ebookreader.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면 아래 작은 글씨: 왼쪽 시간·배터리, 가운데 장 제목 등, 오른쪽 진행 정보. */
@Composable
fun InfoBar(right: String, modifier: Modifier = Modifier, center: String = "") {
    val context = LocalContext.current
    val format = remember {
        SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "a h:mm", Locale.KOREA)
    }
    // 시간·배터리: 분이 바뀔 때마다 갱신 (이잉크라 자주 다시 그리지 않음)
    val state by produceState(format.format(Date()) to batteryLevel(context)) {
        while (true) {
            value = format.format(Date()) to batteryLevel(context)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val style = MaterialTheme.typography.labelSmall
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(state.first, color = Ink, style = style)
            Spacer(Modifier.width(6.dp))
            BatteryIcon(state.second)
        }
        Text(
            center,
            color = Ink,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(2.2f),
        )
        Text(right, color = Ink, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** 선으로 그린 작은 배터리 (남은 만큼 채움). */
@Composable
private fun BatteryIcon(level: Int) {
    Canvas(Modifier.size(width = 20.dp, height = 10.dp)) {
        val s = 1.dp.toPx()
        val bodyW = size.width - 2.5f * s
        drawRect(Ink, topLeft = Offset(s / 2, s / 2), size = Size(bodyW - s, size.height - s), style = Stroke(s))
        drawRect(Ink, topLeft = Offset(bodyW, size.height * 0.3f), size = Size(2f * s, size.height * 0.4f))
        if (level >= 0) {
            val inner = (bodyW - 4 * s) * (level.coerceIn(0, 100) / 100f)
            drawRect(Ink, topLeft = Offset(2 * s, 2 * s), size = Size(inner, size.height - 4 * s))
        }
    }
}

private fun batteryLevel(context: android.content.Context): Int {
    val i = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        ?: return -1
    val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
    return if (level < 0 || scale <= 0) -1 else level * 100 / scale
}

/** 보는 동안 상태바·내비게이션바를 숨기고 화면이 꺼지지 않게 한다. */
@Composable
fun ImmersiveMode() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}
