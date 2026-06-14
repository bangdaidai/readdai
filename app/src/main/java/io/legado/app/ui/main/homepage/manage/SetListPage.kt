package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.main.homepage.HomepageSourceManageUi

@Composable
fun SetListPage(
    sets: List<HomepageSourceManageUi>,
    onToggleSet: (String, Boolean) -> Unit,
    onReorderSets: (List<String>) -> Unit,
    onSelectSet: (String) -> Unit,
    onRenameSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onCreateSet: () -> Unit,
    onBrowseSources: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sets.distinctBy { it.sourceUrl }, key = { it.sourceUrl }) { set ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clickable { onSelectSet(set.sourceUrl) },
                    ) {
                        Text(
                            set.sourceName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${set.moduleCount} 个模块",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRenameSet(set.sourceUrl) }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "重命名", modifier = Modifier.height(20.dp))
                    }
                    IconButton(onClick = { onDeleteSet(set.sourceUrl) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.height(20.dp))
                    }
                    Switch(
                        checked = set.isSelected,
                        onCheckedChange = { enabled -> onToggleSet(set.sourceUrl, enabled) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item(key = "create_set") {
            TextButton(onClick = onCreateSet, modifier = Modifier.fillMaxWidth()) {
                Text("创建新集合")
            }
        }

        item(key = "browse_sources") {
            TextButton(onClick = onBrowseSources, modifier = Modifier.fillMaxWidth()) {
                Text("浏览书源模块")
            }
        }
    }
}
