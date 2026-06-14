package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.homepage.HomepageBookItemUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterfallItem(
    item: HomepageBookItemUi,
    onClick: () -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val book = item.book
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { cb(book, null) } }
            ),
        ) {
            AsyncImage(model = book.coverUrl, contentDescription = book.name, modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
                Text(book.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subTitle = buildString {
                    if (book.author.isNotBlank()) append(book.author)
                    val kind = book.kind?.split(",")?.firstOrNull()
                    if (!kind.isNullOrBlank()) { if (isNotEmpty()) append(" · "); append(kind) }
                }
                if (subTitle.isNotBlank()) {
                    Text(subTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                val intro = book.intro?.replace("\\s+".toRegex(), " ")
                if (!intro.isNullOrBlank()) {
                    Text(intro, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                val kinds = book.kind?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                if (kinds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        kinds.forEach { kind ->
                            AssistChip(onClick = {}, label = { Text(kind, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}
