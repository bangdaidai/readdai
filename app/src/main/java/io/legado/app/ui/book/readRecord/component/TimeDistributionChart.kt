package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
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
    val cardBg = Color(ThemeStore.backgroundCard(context))
    val textPrimary = Color(ThemeStore.textColorPrimary(context))
    val textSecondary = Color(ThemeStore.textColorSecondary(context))
    val dividerColor = Color(ThemeStore.dividerColor(context))

    val maxTime = data.maxOf { it.second }
    val chartHeight = 140.dp
    val dataCount = data.size
    val xLabelFontSize = when {
        dataCount > 20 -> 7.sp
        dataCount > 12 -> 8.sp
        else -> 10.sp
    }

    val borderStroke = if (AppConfig.cardBorderWidth > 0) {
        BorderStroke((AppConfig.cardBorderWidth * 0.5).dp, dividerColor)
    } else {
        null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = borderStroke
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Leaderboard,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart area - no Y-axis, bars from left edge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    val w = size.width
                    val h = size.height
                    val barCount = data.size
                    val slotWidth = w / barCount
                    val barWidth = slotWidth * 0.55f
                    val barOffsetX = (slotWidth - barWidth) / 2f

                    if (maxTime > 0L) {
                        data.forEachIndexed { index, (_, time) ->
                            if (time > 0L) {
                                val barHeight = (time.toFloat() / maxTime) * h
                                val x = index * slotWidth + barOffsetX
                                val y = h - barHeight
                                drawRoundRect(
                                    color = accentColor,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(3f, 3f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // X-axis labels
                Row(modifier = Modifier.fillMaxWidth()) {
                    val labelStep = when {
                        dataCount > 20 -> 4
                        dataCount > 12 -> 2
                        else -> 1
                    }
                    data.forEachIndexed { index, (label, _) ->
                        if (index % labelStep == 0) {
                            Text(
                                text = label,
                                color = textSecondary,
                                fontSize = xLabelFontSize,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
