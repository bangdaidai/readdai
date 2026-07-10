package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.homepage.HomepageSourceManageUi

@Composable
fun BrowseSourcesPage(
    browseSources: List<HomepageSourceManageUi>,
    browseGroups: List<String>,
    browseGroupFilter: String,
    onGroupFilterChange: (String) -> Unit,
    getSourceModules: (String, String?) -> List<io.legado.app.ui.main.homepage.HomepageModuleManageUi>,
    onSelectSource: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val sources = remember(browseSources, query) {
        val distinct = browseSources.distinctBy { it.sourceUrl }
        if (query.isBlank()) distinct
        else distinct.filter {
            it.sourceName.contains(query, ignoreCase = true) ||
                    it.sourceUrl.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.hp_search_source),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.padding(start = 8.dp))
            var showGroupMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showGroupMenu = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.hp_filter_group),
                        modifier = Modifier.size(20.dp),
                        tint = if (browseGroupFilter.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showGroupMenu,
                    onDismissRequest = { showGroupMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hp_all_groups)) },
                        onClick = { onGroupFilterChange(""); showGroupMenu = false },
                    )
                    browseGroups.forEach { group ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    group,
                                    color = if (group == browseGroupFilter) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (group == browseGroupFilter) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { onGroupFilterChange(group); showGroupMenu = false },
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources, key = { it.sourceUrl }) { source ->
                val moduleCount = getSourceModules(source.sourceUrl, null).size
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectSource(source.sourceUrl) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                source.sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.hp_module_count, moduleCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
