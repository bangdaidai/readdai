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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SummaryCardData(
    val title: String,
    val readTypeText: String,
    val bookCount: Int,
    val totalTime: String,
    val bookNames: List<String>
)

@Composable
fun ReadingSummaryCard(
    title: String,
    bookCount: Int,
    totalTime: String,
    readTypeText: String = "阅读",
    bookNames: List<String> = emptyList(),
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(context)
    val primaryColor = io.legado.app.lib.theme.ThemeStore.accentColor(context)
    val textColorPrimary = io.legado.app.lib.theme.ThemeStore.textColorPrimary(context)
    val textColorSecondary = io.legado.app.lib.theme.ThemeStore.textColorSecondary(context)

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
                    fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = androidx.compose.ui.graphics.Color(primaryColor),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "已$readTypeText ",
                        fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = androidx.compose.ui.graphics.Color(textColorPrimary)
                    )
                    Text(
                        text = "$bookCount",
                        fontSize = androidx.compose.ui.unit.TextUnit(48f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = androidx.compose.ui.graphics.Color(primaryColor),
                        fontWeight = FontWeight.Bold,
                        lineHeight = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Text(
                        text = " 本书",
                        fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = androidx.compose.ui.graphics.Color(textColorPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "共$readTypeText $totalTime",
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = androidx.compose.ui.graphics.Color(textColorSecondary)
                )
            }

            if (bookNames.isNotEmpty()) {
                BookStackView(bookNames = bookNames)
            }
        }
    }
}

@Composable
fun BookStackView(bookNames: List<String>) {
    Box(contentAlignment = Alignment.CenterEnd) {
        bookNames.reversed().forEachIndexed { index, bookName ->
            var coverUrl by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(bookName) {
                withContext(Dispatchers.IO) {
                    val book = appDb.bookDao.findByName(bookName).firstOrNull()
                    coverUrl = book?.coverUrl
                }
            }

            BookCoverItem(
                coverUrl = coverUrl ?: "",
                bookName = bookName,
                index = index,
                total = bookNames.size
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
    val overlapDp = 12.dp
    val width = 32.dp
    val height = 42.dp

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
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (index < total - 1) {
                    Modifier.padding(end = overlapDp * (total - 1 - index))
                } else {
                    Modifier
                }
            )
    )
}
