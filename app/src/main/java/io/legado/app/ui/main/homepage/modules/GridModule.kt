package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.main.homepage.GridBookItem

@Composable
fun GridModule(
    books: List<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    maxRows: Int? = null,
) {
    if (books.isEmpty()) return
    var rows = books.toList().chunked(columns)
    if (maxRows != null) rows = rows.take(maxRows)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((rowIndex, row) in rows.withIndex()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((columnIndex, item) in row.withIndex()) {
                    GridBookItem(
                        book = item.book,
                        shelfState = item.shelfState,
                        onClick = { onClick(item.book, null) },
                        onLongClick = onLongClick ?: { _, _ -> },
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
