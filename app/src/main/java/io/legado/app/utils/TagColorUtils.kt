package io.legado.app.utils

import java.util.Random

/**
 * 标签颜色工具类
 * 用于生成标签的随机颜色
 */
object TagColorUtils {

    /**
     * 将RGB颜色转换为HSL颜色空间
     * @param color RGB颜色值
     * @return HSL数组，分别为色相(0-360)、饱和度(0-1)、亮度(0-1)
     */
    private fun rgbToHsl(color: Int): FloatArray {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        var h = 0f
        val s: Float
        val l = (max + min) / 2f

        if (max != min) {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

            h = when (max) {
                r -> (g - b) / d + if (g < b) 6f else 0f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }
            h /= 6f
        } else {
            s = 0f
        }

        return floatArrayOf(h * 360f, s, l)
    }

    /**
     * 将HSL颜色转换为RGB颜色
     * @param h 色相(0-360)
     * @param s 饱和度(0-1)
     * @param l 亮度(0-1)
     * @return RGB颜色值
     */
    private fun hslToRgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - Math.abs(2f * l - 1f)) * s
        val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255).toInt()
        val g = ((g1 + m) * 255).toInt()
        val b = ((b1 + m) * 255).toInt()

        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    /**
     * 生成随机颜色，确保在浅色和深色背景下都有良好的对比度
     * @param tagName 标签名称，用于生成随机种子，确保相同标签总是得到相同颜色
     * @return 随机颜色值
     */
    fun generateRandomColor(tagName: String): Int {
        // 使用标签名称的哈希值作为随机种子，确保相同标签总是得到相同颜色
        val hash = tagName.hashCode()
        val random = Random(hash.toLong())

        // 生成高饱和度和中等偏亮的颜色：亮度(l)在0.5-0.8之间，饱和度(s)在0.7-1.0之间
        val h = random.nextFloat() * 360f // 随机色相
        val s = 0.7f + random.nextFloat() * 0.3f // 饱和度0.7-1.0
        val l = 0.5f + random.nextFloat() * 0.3f // 亮度0.5-0.8

        return hslToRgb(h, s, l)
    }

    /**
     * 生成完全随机的中等偏亮颜色
     * @return 随机颜色值
     */
    fun generateRandomColor(): Int {
        val random = Random()

        // 生成高饱和度和中等偏亮的颜色：亮度(l)在0.5-0.8之间，饱和度(s)在0.7-1.0之间
        val h = random.nextFloat() * 360f // 随机色相
        val s = 0.7f + random.nextFloat() * 0.3f // 饱和度0.7-1.0
        val l = 0.5f + random.nextFloat() * 0.3f // 亮度0.5-0.8

        return hslToRgb(h, s, l)
    }
}