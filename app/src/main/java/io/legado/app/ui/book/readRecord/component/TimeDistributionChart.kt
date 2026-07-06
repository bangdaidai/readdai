package io.legado.app.ui.book.readRecord.component

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.lib.theme.ThemeStore

@Composable
fun TimeDistributionChart(
    title: String,
    data: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty() || data.all { it.second == 0L }) return

    val context = LocalContext.current
    val accentColor = Color(ThemeStore.accentColor(context))
    val textPrimary = Color(ThemeStore.textColorPrimary(context))
    val textSecondary = Color(ThemeStore.textColorSecondary(context))

    val maxTime = data.maxOf { it.second }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        data.forEach { (label, time) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(36.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 3.dp)
                ) {
                    val fraction = if (maxTime > 0) time.toFloat() / maxTime else 0f

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (fraction > 0f) {
                            val barWidth = size.width * fraction.coerceAtLeast(0.01f)
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2)
                            )
                        }
                    }
                }

                Text(
                    text = formatDurationShort(time),
                    color = textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.width(48.dp),
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatDurationShort(millis: Long): String {
    if (millis <= 0) return "0m"
    val totalMinutes = millis / (1000 * 60)
    return if (totalMinutes < 60) {
        "${totalMinutes}m"
    } else {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        if (mins == 0L) "${hours}h" else "${hours}h${mins}m"
    }
}
