package io.legado.app.utils

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.accentColor

object HeatmapUtils {

    private var cachedAccentColor: Int = 0
    private var cachedColors: IntArray? = null

    fun getHeatmapColors(context: Context): IntArray {
        val currentAccent = context.accentColor
        if (cachedAccentColor == currentAccent && cachedColors != null) {
            return cachedColors!!
        }
        cachedAccentColor = currentAccent
        cachedColors = generateHeatmapColors(currentAccent)
        return cachedColors!!
    }

    private fun generateHeatmapColors(accentColor: Int): IntArray {
        return intArrayOf(
            blendWithLightGray(accentColor, 0.01f), // Level 0: 1% 强调色 - 未读
            blendWithLightGray(accentColor, 0.20f), // Level 1: 20% 强调色 - <30 分钟
            blendWithLightGray(accentColor, 0.40f), // Level 2: 40% 强调色 - 30-60 分钟
            blendWithLightGray(accentColor, 0.60f), // Level 3: 60% 强调色 - 1-3 小时
            blendWithLightGray(accentColor, 0.80f), // Level 4: 80% 强调色 - 3-6 小时
            blendWithLightGray(accentColor, 1.00f)  // Level 5: 100% 强调色 - >6 小时
        )
    }

    private fun blendWithWhite(color: Int, ratio: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (255 + (r - 255) * ratio).toInt().coerceIn(0, 255),
            (255 + (g - 255) * ratio).toInt().coerceIn(0, 255),
            (255 + (b - 255) * ratio).toInt().coerceIn(0, 255)
        )
    }

    private fun blendWithLightGray(color: Int, ratio: Float): Int {
        val baseGray = 248 // #F8F8F8 的 RGB 值
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (baseGray + (r - baseGray) * ratio).toInt().coerceIn(0, 255),
            (baseGray + (g - baseGray) * ratio).toInt().coerceIn(0, 255),
            (baseGray + (b - baseGray) * ratio).toInt().coerceIn(0, 255)
        )
    }

    fun getHeatmapColor(context: Context, readMinutes: Int): Int {
        val colors = getHeatmapColors(context)
        return when {
            readMinutes == 0 -> colors[0]
            readMinutes < 30 -> colors[1]
            readMinutes < 60 -> colors[2]
            readMinutes < 180 -> colors[3]
            readMinutes < 360 -> colors[4]
            else -> colors[5]
        }
    }

    fun getHeatmapColorRes(readMinutes: Int): Int {
        return when {
            readMinutes == 0 -> R.color.heatmap_level_0
            readMinutes < 30 -> R.color.heatmap_level_1
            readMinutes < 60 -> R.color.heatmap_level_2
            readMinutes < 180 -> R.color.heatmap_level_3
            readMinutes < 360 -> R.color.heatmap_level_4
            else -> R.color.heatmap_level_5
        }
    }
    
    /**
     * 格式化阅读时长显示文本
     * @param readMinutes 阅读时长（分钟）
     * @return 格式化后的文本
     */
    fun formatReadTime(readMinutes: Int): String {
        return when {
            readMinutes == 0 -> "未读"
            readMinutes < 60 -> "${readMinutes}分钟"
            readMinutes < 1440 -> {
                val hours = readMinutes / 60
                val minutes = readMinutes % 60
                if (minutes == 0) "${hours}小时" else "${hours}小时${minutes}分钟"
            }
            else -> {
                val days = readMinutes / 1440
                val hours = (readMinutes % 1440) / 60
                if (hours == 0) "${days}天" else "${days}天${hours}小时"
            }
        }
    }
}