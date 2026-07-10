package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreKindSelectSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    kinds: List<ExploreKind>,
    onSelected: (List<ExploreKind>) -> Unit,
    initialSelectedTitles: Set<String> = emptySet(),
) {
    if (!show) return

    var selectedTitles by remember(initialSelectedTitles, show) {
        mutableStateOf(initialSelectedTitles.toMutableSet())
    }
    var query by remember { mutableStateOf("") }

    val filteredKinds = remember(query, kinds) {
        if (query.isBlank()) kinds
        else kinds.filter { kind ->
            kind.title.contains(query, ignoreCase = true) ||
                    (kind.url?.contains(query, ignoreCase = true) == true)
        }
    }

    val kindRows = remember(filteredKinds) {
        calculateExploreKindRows(filteredKinds, 12)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.hp_select_category),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            CompactSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.hp_search_category),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        kindRows.forEach { rowItems ->
                            val totalSpan = rowItems.sumOf { it.second }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { (kind, span) ->
                                    val isSelected = kind.title in selectedTitles
                                    KindChip(
                                        title = kind.title,
                                        isSelected = isSelected,
                                        modifier = Modifier.weight(span.toFloat()),
                                        onClick = {
                                            selectedTitles = if (isSelected) {
                                                selectedTitles.toMutableSet().apply { remove(kind.title) }
                                            } else {
                                                selectedTitles.toMutableSet().apply { add(kind.title) }
                                            }
                                        },
                                    )
                                }
                                if (totalSpan < 12) {
                                    Spacer(modifier = Modifier.weight((12 - totalSpan).toFloat()))
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.hp_cancel))
                    }
                    if (selectedTitles.isNotEmpty()) {
                        TextButton(onClick = {
                            val selectedKinds = kinds.filter { it.title in selectedTitles }
                            onSelected(selectedKinds)
                            onDismissRequest()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.padding(start = 4.dp))
                            Text(stringResource(R.string.hp_determine))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(start = 2.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
