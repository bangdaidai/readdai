package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.legado.app.constant.BookType
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.components.image.CoilBookCover

data class SummaryCardData(
    val title: String,
    val bookType: Int = BookType.text,
    val bookCount: Int,
    val totalTimeMillis: Long,
    val bookCovers: List<BookCoverData>
)

data class BookCoverData(
    val bookName: String,
    val coverUrl: String
)

@Composable
fun ReadingSummaryCard(
    title: String,
    bookType: Int = BookType.text,
    bookCount: Int,
    totalTimeMillis: Long,
    bookCovers: List<BookCoverData>,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val cardBgColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(context)
    val primaryColor = io.legado.app.lib.theme.ThemeStore.accentColor(context)
    val textColorPrimary = io.legado.app.lib.theme.ThemeStore.textColorPrimary(context)
    val textColorSecondary = io.legado.app.lib.theme.ThemeStore.textColorSecondary(context)
    val dividerColor = io.legado.app.lib.theme.ThemeStore.dividerColor(context)

    val hours = totalTimeMillis / (1000 * 60 * 60)
    val minutes = (totalTimeMillis / (1000 * 60)) % 60
    val hourStr = if (hours == 1L) "小时" else "小时"
    val minuteStr = if (minutes == 1L) "分钟" else "分钟"
    val timeString = if (hours > 0) "${hours}$hourStr${minutes}$minuteStr" else "${minutes}$minuteStr"

    val (actionText, measureWord) = when {
        bookType and BookType.audio != 0 -> "已听" to "部"
        bookType and BookType.video != 0 -> "已看" to "部"
        else -> "已读" to "本"
    }

    val bgColor = Color(cardBgColor)
    val isDarkBackground = bgColor.luminance() < 0.18f
    val shape = RoundedCornerShape(16.dp)
    val cardColor = if (isDarkBackground) {
        lerp(bgColor, bgColor.copy(alpha = 0.9f), 0.72f)
    } else {
        bgColor
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .clickable(onClick = onClick)
    val cardBorder = if (AppConfig.showRecordCardBorder) {
        BorderStroke(2.dp, Color(dividerColor))
    } else {
        null
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(textColorSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = actionText,
                        fontSize = 16.sp,
                        color = Color(textColorPrimary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = " $bookCount ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(primaryColor),
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                    Text(
                        text = measureWord,
                        fontSize = 16.sp,
                        color = Color(textColorPrimary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "总时长 $timeString",
                    fontSize = 14.sp,
                    color = Color(textColorSecondary)
                )
            }

            if (bookCovers.isNotEmpty()) {
                BookStackView(
                    bookCovers = bookCovers.take(5)
                )
            }
        }
    }
}

@Composable
fun BookStackView(
    bookCovers: List<BookCoverData>
) {
    val xOffsetStep = 12.dp
    val stackWidth = 48.dp + (xOffsetStep * (bookCovers.size - 1).coerceAtLeast(0))

    Box(
        modifier = Modifier
            .width(stackWidth)
            .height(72.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        bookCovers.forEachIndexed { index, bookCover ->
            Box(
                modifier = Modifier
                    .padding(start = xOffsetStep * index)
                    .zIndex(index.toFloat())
                    .rotate(if (index % 2 == 0) 3f else -3f)
            ) {
                CoilBookCover(
                    name = bookCover.bookName,
                    author = null,
                    path = bookCover.coverUrl.ifEmpty { null },
                    modifier = Modifier.width(48.dp),
                    radius = 4.dp
                )
            }
        }
    }
}
