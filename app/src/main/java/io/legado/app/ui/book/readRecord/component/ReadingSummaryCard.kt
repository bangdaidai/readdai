package io.legado.app.ui.book.readRecord.component

import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import io.legado.app.data.appDb
import io.legado.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SummaryCardData(
    val title: String,
    val bookType: Int = io.legado.app.constant.BookType.text,
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
    bookType: Int = io.legado.app.constant.BookType.text,
    bookCount: Int,
    totalTimeMillis: Long,
    bookCovers: List<BookCoverData>,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(context)
    val primaryColor = io.legado.app.lib.theme.ThemeStore.accentColor(context)
    val textColorPrimary = io.legado.app.lib.theme.ThemeStore.textColorPrimary(context)
    val textColorSecondary = io.legado.app.lib.theme.ThemeStore.textColorSecondary(context)
    
    val hours = totalTimeMillis / (1000 * 60 * 60)
    val minutes = (totalTimeMillis / (1000 * 60)) % 60
    val timeString = if (hours > 0) {
        "${hours}小时${minutes}分钟"
    } else {
        "${minutes}分钟"
    }
    
    val (actionText, measureWord) = when {
        bookType and io.legado.app.constant.BookType.audio != 0 -> "已听" to "部"
        bookType and io.legado.app.constant.BookType.video != 0 -> "已看" to "部"
        else -> "已读" to "本"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(cardColor)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = androidx.compose.ui.graphics.Color(textColorSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = actionText,
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color(textColorPrimary)
                    )
                    Text(
                        text = " $bookCount ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(primaryColor)
                    )
                    Text(
                        text = measureWord,
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color(textColorPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "总时长 $timeString",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color(textColorSecondary)
                )
            }
            
            BookStackView(
                bookCovers = bookCovers
            )
        }
    }
}

@Composable
fun BookStackView(
    bookCovers: List<BookCoverData>
) {
    Box(contentAlignment = Alignment.CenterEnd) {
        bookCovers.reversed().forEachIndexed { index, bookCover ->
            BookCoverItem(
                coverUrl = bookCover.coverUrl,
                bookName = bookCover.bookName,
                index = index,
                total = bookCovers.size
            )
        }
    }
}

@Composable
fun BookCoverItem(
    coverUrl: String,
    bookName: String,
    index: Int,
    total: Int
) {
    Image(
        painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .placeholder(R.drawable.ic_book)
                .error(R.drawable.ic_book)
                .crossfade(true)
                .build()
        ),
        contentDescription = bookName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(32.dp)
            .padding(end = (index * 12).dp)
            .clip(RoundedCornerShape(4.dp))
    )
}

private fun formatMinutes(minutes: Long): String {
    if (minutes < 60) {
        return "${minutes}分钟"
    }
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (remainingMinutes > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${hours}小时"
    }
}
