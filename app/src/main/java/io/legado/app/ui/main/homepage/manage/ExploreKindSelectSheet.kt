package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreKindSelectSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    kinds: List<ExploreKind>,
    onSelected: (List<ExploreKind>) -> Unit,
    multiple: Boolean = false,
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
        calculateKindRows(filteredKinds, 6)
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.hp_search_category)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
            )

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(kindRows) { rowItems ->
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowItems.forEach { kind ->
                            val isSelected = kind.title in selectedTitles
                            KindChip(
                                title = kind.title,
                                isSelected = isSelected,
                                onClick = {
                                    if (multiple) {
                                        selectedTitles = if (isSelected) {
                                            selectedTitles.toMutableSet().apply { remove(kind.title) }
                                        } else {
                                            selectedTitles.toMutableSet().apply { add(kind.title) }
                                        }
                                    } else {
                                        onSelected(listOf(kind))
                                        onDismissRequest()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (multiple) {
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
}

@Composable
private fun KindChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun calculateKindRows(
    kinds: List<ExploreKind>,
    maxSpan: Int
): List<List<ExploreKind>> {
    val rows = mutableListOf<MutableList<ExploreKind>>()
    var currentRow = mutableListOf<ExploreKind>()
    var currentSpan = 0

    fun fillCurrentRowTail() {
        if (currentRow.isEmpty()) return
        val remain = maxSpan - currentSpan
        if (remain > 0 && currentRow.size == 1) {
            // single item fills the row
        }
    }

    kinds.forEach { kind ->
        val style = kind.style()
        val span = when {
            style.layout_wrapBefore || style.layout_flexBasisPercent >= 1.0f -> maxSpan
            style.layout_flexBasisPercent > 0 -> (maxSpan * style.layout_flexBasisPercent).roundToInt().coerceIn(1, maxSpan)
            style.layout_flexGrow > 0f -> 3
            else -> 2
        }
        if ((style.layout_wrapBefore && currentRow.isNotEmpty()) || (currentSpan + span > maxSpan)) {
            fillCurrentRowTail()
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
        currentRow.add(kind)
        currentSpan += span
        if (currentSpan >= maxSpan) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
    }
    if (currentRow.isNotEmpty()) {
        fillCurrentRowTail()
        rows.add(currentRow)
    }
    return rows
}
