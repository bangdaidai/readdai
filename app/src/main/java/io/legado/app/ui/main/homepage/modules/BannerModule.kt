package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.homepage.HomepageBookItemUi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerModule(
    books: List<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(books, key = { index, item -> "${item.book.bookUrl}:$index" }) { index, item ->
            val book = item.book
            Column(
                modifier = Modifier.width(96.dp).combinedClickable(
                    onClick = { onClick(book, null) },
                    onLongClick = onLongClick?.let { cb -> { cb(book, null) } }
                ),
            ) {
                Box {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.name,
                        modifier = Modifier.width(96.dp).height(134.dp),
                        contentScale = ContentScale.Crop,
                    )
                    when (item.shelfState) {
                        BookShelfState.IN_SHELF -> Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) { Icon(Icons.Default.Check, null, modifier = Modifier.padding(2.dp)) }
                        BookShelfState.SAME_NAME_AUTHOR -> Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) { Icon(Icons.Default.Shuffle, null, modifier = Modifier.padding(2.dp)) }
                        else -> {}
                    }
                }
                Text(book.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
