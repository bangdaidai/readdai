package io.legado.app.ui.main.homepage

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.model.BookCover
import io.legado.app.model.webBook.WebBook

@Composable
fun GlideBookCover(
    coverUrl: String?,
    sourceOrigin: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var drawable by remember { mutableStateOf<Drawable?>(null) }
    val key = remember(coverUrl, sourceOrigin) { "$coverUrl|$sourceOrigin" }

    DisposableEffect(key) {
        val glide = BookCover.load(context, coverUrl, sourceOrigin = sourceOrigin)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: com.bumptech.glide.request.transition.Transition<in Drawable>?) {
                    drawable = resource
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    drawable = placeholder
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    drawable = errorDrawable
                }
            })

        onDispose {
            try {
                com.bumptech.glide.Glide.with(context).clear(glide)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    val currentDrawable = drawable
    Box(modifier = modifier) {
        if (currentDrawable != null) {
            val bitmap = remember(currentDrawable) {
                runCatching { currentDrawable.toBitmap().asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
        }
    }
}

@Composable
fun SearchBookCover(
    book: io.legado.app.data.entities.SearchBook,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    GlideBookCover(
        coverUrl = book.coverUrl,
        sourceOrigin = book.origin,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
