package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.components.image.CoilBookCover

data class BookRankingData(
    val bookName: String,
    val bookAuthor: String,
    val readTime: Long,
    val coverUrl: String = "",
    val sourceOrigin: String = ""
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

    val borderStroke = if (AppConfig.cardBorderWidth > 0) {
        BorderStroke((AppConfig.cardBorderWidth * 0.5).dp, Color(dividerColor))
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

                    CoilBookCover(
                        name = book.bookName,
                        author = book.bookAuthor.ifEmpty { null },
                        path = book.coverUrl.ifEmpty { null },
                        modifier = Modifier.width(40.dp),
                        radius = 4.dp,
                        sourceOrigin = book.sourceOrigin
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
