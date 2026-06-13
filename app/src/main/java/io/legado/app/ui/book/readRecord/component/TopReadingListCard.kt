package io.legado.app.ui.book.readRecord.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.BookCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BookRankingData(
    val bookName: String,
    val bookAuthor: String,
    val readTime: Long,
    val coverUrl: String = ""
)

@Composable
fun TopReadingListCard(
    topBooks: List<BookRankingData>,
    onBookClick: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val cardColor = ThemeStore.backgroundCard(context)
    val primaryColor = ThemeStore.accentColor(context)
    val textColorPrimary = ThemeStore.textColorPrimary(context)
    val textColorSecondary = ThemeStore.textColorSecondary(context)
    val dividerColor = ThemeStore.dividerColor(context)

    val borderStroke = if (AppConfig.showCardBorder) {
        BorderStroke(0.5.dp, Color(dividerColor))
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(cardColor)
        ),
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
                    tint = Color(primaryColor),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "阅读时长榜",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(textColorPrimary)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            topBooks.forEachIndexed { index, book ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookClick(book.bookName, book.bookAuthor) }
                        .padding(vertical = 6.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 16.sp,
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.Center,
                        color = if (index < 3) Color(primaryColor) else Color(textColorSecondary)
                    )

                    BookCoverImage(
                        bookName = book.bookName,
                        coverUrl = book.coverUrl,
                        width = 40,
                        height = 56
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formatDuration(book.readTime),
                            fontSize = 12.sp,
                            color = Color(primaryColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = book.bookName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(textColorPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (book.bookAuthor.isNotEmpty()) {
                            Text(
                                text = book.bookAuthor,
                                fontSize = 12.sp,
                                color = Color(textColorSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCoverImage(
    bookName: String,
    coverUrl: String,
    width: Int = 40,
    height: Int = 56
) {
    val context = LocalContext.current
    val primaryColor = ThemeStore.accentColor(context)
    val backgroundColor = ThemeStore.backgroundCard(context)
    var coverBitmap by remember(coverUrl, bookName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(coverUrl, bookName) {
        withContext(Dispatchers.IO) {
            coverBitmap = runCatching {
                if (coverUrl.isNotEmpty()) {
                    ImageLoader.loadBitmap(context, coverUrl)
                        .submit()
                        .get()
                } else {
                    val defaultBitmap = BookCover.defaultDrawable.let { drawable ->
                        val bmp = Bitmap.createBitmap(width * 3, height * 3, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                    drawBookNameOnBitmap(defaultBitmap, bookName, primaryColor, backgroundColor)
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        val bitmap = coverBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = bookName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bookName,
                    fontSize = 7.sp,
                    color = Color(primaryColor),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 3f,
                            offset = Offset(1f, 1f)
                        )
                    )
                )
            }
        }
    }
}

private fun drawBookNameOnBitmap(
    bitmap: Bitmap,
    bookName: String,
    accentColor: Int,
    backgroundColor: Int
): Bitmap {
    val canvas = Canvas(bitmap)
    val namePaint = TextPaint().apply {
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val viewWidth = bitmap.width.toFloat()
    val viewHeight = bitmap.height.toFloat()
    var startX = viewWidth * 0.5f
    var startY = viewHeight * 0.2f
    namePaint.textSize = viewWidth / 7
    namePaint.strokeWidth = namePaint.textSize / 6
    bookName.forEachIndexed { index, char ->
        namePaint.color = backgroundColor
        namePaint.style = Paint.Style.STROKE
        canvas.drawText(char.toString(), startX, startY, namePaint)
        namePaint.color = accentColor
        namePaint.style = Paint.Style.FILL
        canvas.drawText(char.toString(), startX, startY, namePaint)
        startY += namePaint.textSize
        if (startY > viewHeight * 0.9) {
            startX += namePaint.textSize
            namePaint.textSize = viewWidth / 10
            startY = viewHeight * 0.2f
        }
    }
    return bitmap
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    if (totalMinutes < 60) {
        return "${totalMinutes}分钟"
    }
    val hours = totalMinutes / 60
    val remainingMinutes = totalMinutes % 60
    return if (remainingMinutes > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${hours}小时"
    }
}
