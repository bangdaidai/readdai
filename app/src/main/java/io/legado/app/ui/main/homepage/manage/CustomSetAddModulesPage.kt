package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel

@Composable
fun CustomSetAddModulesPage(
    setUrl: String,
    allJoinedModules: List<HomepageModuleManageUi>,
    sourceNames: Map<String, String>,
    onToggleModuleToSet: (HomepageModuleManageUi, inCurrentSet: Boolean, isBlocked: Boolean) -> Unit,
) {
    val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
    val initialJoined = allJoinedModules
        .filter { it.customSetId == setId }
        .associateBy({ it.moduleKey }, { it.id })
    var joinedInCurrent by remember(initialJoined) { mutableStateOf(initialJoined) }

    val hasInfiniteInCurrentSet = remember(allJoinedModules) {
        allJoinedModules.any {
            it.customSetId == setId && HomepageViewModel.isInfinite(it.type, it.layoutConfig)
        }
    }

    val grouped = remember(allJoinedModules) {
        allJoinedModules
            .distinctBy { it.sourceUrl to it.moduleKey }
            .groupBy { it.sourceUrl }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (sourceUrl, modules) ->
            item(key = "header_$sourceUrl") {
                Text(
                    sourceNames[sourceUrl] ?: sourceUrl,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(modules, key = { it.id }) { module ->
                val instanceIdInCurrentSet = joinedInCurrent[module.moduleKey]
                val inCurrentSet = instanceIdInCurrentSet != null
                val isInfinite = HomepageViewModel.isInfinite(module.type, module.layoutConfig)
                val isBlocked = !inCurrentSet && isInfinite && hasInfiniteInCurrentSet

                Card(
                    modifier = Modifier.fillMaxWidth().then(
                        if (!isBlocked) Modifier.clickable {
                            onToggleModuleToSet(module, inCurrentSet, isBlocked)
                            if (inCurrentSet) {
                                joinedInCurrent = joinedInCurrent - module.moduleKey
                            } else if (!isBlocked) {
                                joinedInCurrent = joinedInCurrent + (module.moduleKey to "temp_${module.id}")
                            }
                        } else Modifier
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (inCurrentSet) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(module.title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                module.moduleKey + if (isBlocked) " (无限流冲突)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (inCurrentSet) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
