package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.main.homepage.SearchBookCover

private const val INITIAL_COUNT = 5
private const val MAX_COUNT = 20

@Composable
fun RankingModule(
    books: List<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visibleCount by rememberSaveable { mutableIntStateOf(INITIAL_COUNT) }
    val displayBooks = books.take(visibleCount)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(modifier = Modifier.padding(top = 12.dp).animateContentSize()) {
            displayBooks.forEachIndexed { index, item ->
                RankingItem(
                    rank = index + 1,
                    book = item.book,
                    shelfState = item.shelfState,
                    onClick = { onClick(item.book, null) },
                    onLongClick = onLongClick,
                )
            }
            if (books.size > INITIAL_COUNT) {
                Box(
                    modifier = Modifier.fillMaxWidth().clickable {
                        visibleCount = if (visibleCount == INITIAL_COUNT) MAX_COUNT else INITIAL_COUNT
                    }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val isExpanded = visibleCount > INITIAL_COUNT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isExpanded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (isExpanded) stringResource(R.string.hp_collapse) else stringResource(R.string.hp_expand_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isExpanded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RankingItem(
    rank: Int,
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick?.let { cb -> { cb(book, null) } }
        ).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(42.dp).padding(start = 2.dp, end = 10.dp),
        )
        SearchBookCover(
            book = book,
            contentDescription = null,
            modifier = Modifier.width(40.dp).height(56.dp),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            val sub = buildString {
                append(book.kind?.split(",")?.firstOrNull() ?: "")
                if (book.author.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(book.author)
                }
            }
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
