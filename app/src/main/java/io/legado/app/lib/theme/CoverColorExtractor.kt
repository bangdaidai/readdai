package io.legado.app.lib.theme

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CoverColorExtractor {

    private const val SAMPLING_SIZE = 64

    fun extractDominantColor(bitmap: Bitmap, fallbackColor: Int = Color.GRAY): Int {
        if (bitmap.width == 0 || bitmap.height == 0) {
            return fallbackColor
        }

        val scaledBitmap = if (bitmap.width > SAMPLING_SIZE || bitmap.height > SAMPLING_SIZE) {
            val scale = minOf(
                SAMPLING_SIZE.toFloat() / bitmap.width,
                SAMPLING_SIZE.toFloat() / bitmap.height
            )
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        return try {
            extractColorUsingKMeans(scaledBitmap)
        } catch (e: Exception) {
            extractAverageColor(scaledBitmap)
        } finally {
            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
        }
    }

    private fun extractColorUsingKMeans(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val clusters = mutableListOf<ColorCluster>()
        val sampleStep = maxOf(1, pixels.size / 512)

        repeat(5) {
            clusters.add(
                ColorCluster(
                    r = (Math.random() * 255).toInt(),
                    g = (Math.random() * 255).toInt(),
                    b = (Math.random() * 255).toInt()
                )
            )
        }

        repeat(10) {
            for (i in pixels.indices step sampleStep) {
                val pixel = pixels[i]
                if (Color.alpha(pixel) > 128) {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    val nearestCluster = clusters.minByOrNull { cluster ->
                        colorDistance(r, g, b, cluster.r, cluster.g, cluster.b)
                    }
                    nearestCluster?.addPixel(r, g, b)
                }
            }

            clusters.forEach { it.recalculateCenter() }
        }

        val dominantCluster = clusters.maxByOrNull { it.pixelCount } ?: return extractAverageColor(bitmap)
        return Color.rgb(dominantCluster.r, dominantCluster.g, dominantCluster.b)
    }

    private fun extractAverageColor(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        val step = maxOf(1, pixels.size / 256)
        for (i in pixels.indices step step) {
            val pixel = pixels[i]
            if (Color.alpha(pixel) > 128) {
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                count++
            }
        }

        return if (count > 0) {
            Color.rgb(
                (totalR / count).toInt().coerceIn(0, 255),
                (totalG / count).toInt().coerceIn(0, 255),
                (totalB / count).toInt().coerceIn(0, 255)
            )
        } else {
            Color.GRAY
        }
    }

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        return sqrt(
            (r1 - r2).toDouble().pow(2) +
                    (g1 - g2).toDouble().pow(2) +
                    (b1 - b2).toDouble().pow(2)
        )
    }

    private class ColorCluster(var r: Int, var g: Int, var b: Int) {
        private val pixels = mutableListOf<Triple<Int, Int, Int>>()
        var pixelCount = 0
            private set

        fun addPixel(r: Int, g: Int, b: Int) {
            pixels.add(Triple(r, g, b))
            pixelCount++
        }

        fun recalculateCenter() {
            if (pixels.isEmpty()) return
            var totalR = 0
            var totalG = 0
            var totalB = 0
            pixels.forEach { (pr, pg, pb) ->
                totalR += pr
                totalG += pg
                totalB += pb
            }
            r = totalR / pixels.size
            g = totalG / pixels.size
            b = totalB / pixels.size
            pixels.clear()
        }
    }

    fun darkenColor(color: Int, factor: Float = 0.6f): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun lightenColor(color: Int, factor: Float = 0.3f): Int {
        val w = (1f - factor) * 255
        val r = ((Color.red(color) * factor) + w).toInt().coerceIn(0, 255)
        val g = ((Color.green(color) * factor) + w).toInt().coerceIn(0, 255)
        val b = ((Color.blue(color) * factor) + w).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}