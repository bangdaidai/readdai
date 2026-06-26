package io.legado.app.ui.widget.components.image

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceHelp
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.BookCover
import io.legado.app.utils.getPrefString
import splitties.init.appCtx

@Composable
fun CoilBookCover(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    radius: Dp = 4.dp,
    showLoadingPlaceholder: Boolean = true,
    sourceOrigin: String = "",
) {
    val context = LocalContext.current
    val isNight = AppConfig.isNightTheme
    val useDefault = AppConfig.useDefaultCover
    val finalPath = if (useDefault) null else path
    val headers = remember(sourceOrigin) {
        val map = mutableMapOf<String, String>()
        if (sourceOrigin.isNotBlank()) {
            val source = SourceHelp.getSource(sourceOrigin)
            if (source != null) {
                try {
                    val analyzedUrl = AnalyzeUrl("", source = source)
                    map.putAll(analyzedUrl.headerMap)
                } catch (_: Exception) {}
            }
        }
        map
    }

    val defaultCoverPath = remember(name, author, path, isNight) {
        getDefaultCoverPath(name ?: author ?: path ?: "")
    }
    val hasCustomDefault = !defaultCoverPath.isNullOrBlank()

    var isLoading by remember(finalPath) { mutableStateOf(finalPath != null) }
    var loadFailed by remember(finalPath) { mutableStateOf(false) }

    val shape = RoundedCornerShape(radius)

    Box(
        modifier = modifier
            .aspectRatio(5f / 7f, false)
            .clip(shape)
    ) {
        if (hasCustomDefault) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(defaultCoverPath)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEEEEEE))
            )
        }

        if (finalPath != null && !loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(finalPath)
                    .crossfade(true)
                    .apply {
                        headers.forEach { (k, v) -> setHeader(k, v) }
                    }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    isLoading = false
                    loadFailed = false
                },
                onError = {
                    isLoading = false
                    loadFailed = true
                }
            )
        }

        val showTextOverlay = finalPath == null || (isLoading || loadFailed)
        if (showTextOverlay && showLoadingPlaceholder) {
            if (!hasCustomDefault && finalPath != null && !isLoading && !loadFailed) {
                val iconColor = ThemeStore.textColorSecondary(context)
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = Color(iconColor),
                    modifier = Modifier
                        .fillMaxSize(0.35f)
                        .align(Alignment.Center)
                )
            }
            if (isLoading || loadFailed || finalPath == null) {
                CoverTextOverlay(
                    name = name,
                    author = author,
                    isNight = isNight
                )
            }
        }
    }
}

private fun getDefaultCoverPath(seed: String): String? {
    val isNight = AppConfig.isNightTheme
    val pathStr = if (isNight) {
        appCtx.getPrefString("defaultCoverDark", null)
    } else {
        appCtx.getPrefString("defaultCover", null)
    } ?: return null
    val paths = pathStr.split(",").filter { it.isNotBlank() }
    if (paths.isEmpty()) return null
    val index = if (seed.isEmpty()) 0 else Math.abs(seed.hashCode()) % paths.size
    return paths[index]
}

private fun isLatinBasedText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val latinRatio = text.count { it in 'A'..'Z' || it in 'a'..'z' }.toFloat() / text.length
    return latinRatio > 0.3f
}

@Composable
private fun CoverTextOverlay(
    name: String?,
    author: String?,
    isNight: Boolean
) {
    val showName = if (isNight) BookCover.drawBookName else BookCover.drawBookName
    val showAuthor = if (isNight) BookCover.drawBookAuthor else BookCover.drawBookAuthor

    if (!showName && !showAuthor) return

    val context = LocalContext.current
    val accentColor = ThemeStore.accentColor(context)
    val isHorizontal = BookCover.drawNameAuthorHorizontal || isLatinBasedText(name)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val viewWidth = size.width
        val viewHeight = size.height

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            if (showName && !name.isNullOrBlank()) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = viewWidth / 8f
                    color = accentColor
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.argb(100, 0, 0, 0))
                }

                if (isHorizontal) {
                    val maxWidth = (viewWidth * 0.8f).toInt()
                    val textPaintStroke = TextPaint(paint).apply {
                        textAlign = Paint.Align.LEFT
                        style = Paint.Style.STROKE
                        strokeWidth = paint.textSize / 12
                        color = android.graphics.Color.WHITE
                        clearShadowLayer()
                    }
                    val strokeLayout = StaticLayout.Builder
                        .obtain(name, 0, name.length, textPaintStroke, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                    val textPaintFill = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
                    val fillLayout = StaticLayout.Builder
                        .obtain(name, 0, name.length, textPaintFill, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                    nativeCanvas.withSave {
                        val textX = (viewWidth - maxWidth) / 2f
                        val textY = viewHeight * 0.08f
                        translate(textX, textY)
                        strokeLayout.draw(this)
                        fillLayout.draw(this)
                    }
                } else {
                    var startX = viewWidth * 0.16f
                    var startY = viewHeight * 0.16f
                    val fm = paint.fontMetrics
                    val charHeight = fm.bottom - fm.top
                    name.forEach { char ->
                        val strokePaint = Paint(paint).apply {
                            color = android.graphics.Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = paint.textSize / 10
                            clearShadowLayer()
                        }
                        nativeCanvas.drawText(char.toString(), startX, startY, strokePaint)
                        nativeCanvas.drawText(char.toString(), startX, startY, paint)
                        startY += charHeight
                        if (startY > viewHeight * 0.8f) {
                            startX += paint.textSize * 1.2f
                            startY = viewHeight * 0.2f
                        }
                    }
                }
            }

            if (showAuthor && !author.isNullOrBlank()) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    textSize = viewWidth / 12f
                    color = accentColor
                    setShadowLayer(4f, 1f, 1f, android.graphics.Color.argb(100, 0, 0, 0))
                }
                if (isHorizontal) {
                    val authorText = TextUtils.ellipsize(author, TextPaint(paint), viewWidth * 0.9f, TextUtils.TruncateAt.END)
                    val strokePaint = Paint(paint).apply {
                        color = android.graphics.Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = paint.textSize / 10
                        clearShadowLayer()
                    }
                    nativeCanvas.drawText(authorText.toString(), viewWidth / 2, viewHeight * 0.75f, strokePaint)
                    nativeCanvas.drawText(authorText.toString(), viewWidth / 2, viewHeight * 0.75f, paint)
                } else {
                    val startX = viewWidth * 0.84f
                    val fm = paint.fontMetrics
                    val charHeight = fm.bottom - fm.top
                    var startY = viewHeight * 0.16f - (author.length * charHeight)
                    startY = startY.coerceAtLeast(viewHeight * 0.2f)
                    author.forEach { char ->
                        nativeCanvas.drawText(char.toString(), startX, startY, paint)
                        startY += charHeight
                    }
                }
            }
        }
    }
}
