package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import io.legado.app.data.appDb
import io.legado.app.lib.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BookRankingData(
    val bookName: String,
    val bookAuthor: String,
    val readTime: Long,
    val coverUrl: String = ""
)

/**
 * 完全复刻 MD364 的 TopReadingListCard
 */
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(cardColor)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            // 标题行：图标 + "阅读时长榜"
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
            
            // 书籍列表
            topBooks.forEachIndexed { index, book ->
                var coverUrl by remember { mutableStateOf(book.coverUrl) }
                
                LaunchedEffect(book.bookName, book.bookAuthor) {
                    if (coverUrl.isEmpty()) {
                        withContext(Dispatchers.IO) {
                            val session = appDb.readSessionDao.getSessionsByBook(book.bookName).firstOrNull()
                            if (session?.coverUrl?.isNotEmpty() == true) {
                                coverUrl = session.coverUrl
                            } else {
                                val bookEntity = appDb.bookDao.findByName(book.bookName).firstOrNull()
                                coverUrl = bookEntity?.coverUrl ?: ""
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookClick(book.bookName, book.bookAuthor) }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 排名数字
                    Text(
                        text = "${index + 1}",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .width(24.dp)
                            .padding(end = 12.dp),
                        textAlign = TextAlign.Center,
                        color = if (index < 3) Color(primaryColor) else Color(textColorSecondary)
                    )
                    
                    // 封面
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(coverUrl)
                                .placeholder(io.legado.app.R.drawable.ic_book)
                                .error(io.legado.app.R.drawable.ic_book)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = book.bookName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(40.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // 信息列
                    Column(modifier = Modifier.weight(1f)) {
                        // 阅读时长 (Primary色)
                        Text(
                            modifier = Modifier.padding(end = 8.dp),
                            text = formatDuration(book.readTime),
                            fontSize = 12.sp,
                            color = Color(primaryColor)
                        )
                        // 书名 (Bold)
                        Text(
                            text = book.bookName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(textColorPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 作者 (Secondary色)
                        Text(
                            text = book.bookAuthor,
                            fontSize = 12.sp,
                            color = Color(textColorSecondary)
                        )
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
