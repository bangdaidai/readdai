package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.main.homepage.SearchBookCover
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridRankingModule(
    books: List<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    if (books.isEmpty()) return
    val limitedBooks = books.take(20)
    val pages = limitedBooks.chunked(rows)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(end = 100.dp),
        pageSpacing = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) { pageIndex ->
        val page = pages[pageIndex]
        Column(modifier = Modifier.fillMaxWidth()) {
            for ((rowIndex, item) in page.withIndex()) {
                val rank = pageIndex * rows + rowIndex + 1
                Row(
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { onClick(item.book, null) },
                        onLongClick = onLongClick?.let { cb -> { cb(item.book, null) } }
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchBookCover(book = item.book, contentDescription = null, modifier = Modifier.width(50.dp).height(70.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    Text(
                        "$rank", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black,
                        fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
                        color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(32.dp), textAlign = TextAlign.Center,
                    )
                    Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                        Text(item.book.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        val sub = buildString {
                            append(item.book.kind?.split(",")?.firstOrNull() ?: "")
                            if (item.book.author.isNotBlank()) { if (isNotEmpty()) append(" · "); append(item.book.author) }
                        }
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            repeat(rows - page.size) { Spacer(modifier = Modifier.height(76.dp)) }
        }
    }
}
