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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SummaryCardData(
    val title: String,
    val readTypeText: String,
    val bookCount: Int,
    val totalTimeMillis: Long,
    val bookNamesForCover: List<Pair<String, String>>
)

@Composable
fun ReadingSummaryCard(
    title: String,
    bookCount: Int,
    totalTimeMillis: Long,
    bookNamesForCover: List<Pair<String, String>>,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(context)
    val primaryColor = io.legado.app.lib.theme.ThemeStore.accentColor(context)
    val textColorPrimary = io.legado.app.lib.theme.ThemeStore.textColorPrimary(context)
    val textColorSecondary = io.legado.app.lib.theme.ThemeStore.textColorSecondary(context)
    
    val totalDurationMinutes = totalTimeMillis / 60000

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

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(primaryColor)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "已读 ",
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color(textColorPrimary)
                    )
                    Text(
                        text = "$bookCount",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(textColorPrimary)
                    )
                    Text(
                        text = " 本书，时长 ${formatMinutes(totalDurationMinutes)}",
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color(textColorSecondary)
                    )
                }
            }
            
            BookStackView(
                bookNamesForCover = bookNamesForCover
            )
        }
    }
}

@Composable
fun BookStackView(
    bookNamesForCover: List<Pair<String, String>>
) {
    Box(contentAlignment = Alignment.CenterEnd) {
        bookNamesForCover.reversed().forEachIndexed { index, (name, author) ->
            var coverUrl by remember { mutableStateOf<String?>(null) }
            
            LaunchedEffect(name, author) {
                withContext(Dispatchers.IO) {
                    val book = appDb.bookDao.findByName(name).firstOrNull()
                        ?: appDb.bookDao.findByName(author).firstOrNull()
                    coverUrl = book?.coverUrl
                }
            }
            
            BookCoverItem(
                coverUrl = coverUrl ?: "",
                bookName = name,
                index = index,
                total = bookNamesForCover.size
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
