package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.utils.GSON

private data class MultiKindsArgs(
    val isMultiKinds: Boolean = false,
    val kindTitles: List<String> = emptyList(),
    val kindUrls: List<String?> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCustomModuleDialog(
    sourceUrl: String = "",
    targetSetId: String = "",
    prefillTitle: String = "",
    prefillUrl: String = "",
    prefillType: String = "card",
    prefillArgs: String = "",
    prefillLayoutConfig: String = "",
    canSelectInfinite: Boolean = true,
    allKinds: List<ExploreKind> = emptyList(),
    prefillKindTitles: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onConfirm: (ModuleDef) -> Unit,
) {
    val parsedArgs = remember(prefillArgs) {
        kotlin.runCatching {
            GSON.fromJson(prefillArgs, MultiKindsArgs::class.java)
        }.getOrNull()
    }

    val initialKindTitles = remember {
        when {
            prefillKindTitles.isNotEmpty() -> prefillKindTitles.toMutableList()
            parsedArgs?.isMultiKinds == true -> parsedArgs.kindTitles.toMutableList()
            else -> mutableListOf()
        }
    }

    val initialKindUrls = remember {
        if (parsedArgs?.kindUrls != null) {
            parsedArgs.kindUrls.toMutableList()
        } else if (initialKindTitles.isNotEmpty() && prefillUrl.isNotBlank()) {
            mutableListOf(prefillUrl)
        } else {
            mutableListOf()
        }
    }

    var title by remember {
        mutableStateOf(
            prefillTitle.ifBlank {
                if (initialKindTitles.isNotEmpty()) initialKindTitles.joinToString("·") else ""
            }
        )
    }
    var url by remember { mutableStateOf(prefillUrl) }
    var type by remember { mutableStateOf(prefillType) }
    var args by remember {
        mutableStateOf(
            if (initialKindTitles.size >= 2) {
                GSON.toJson(MultiKindsArgs(isMultiKinds = true, kindTitles = initialKindTitles, kindUrls = initialKindUrls))
            } else {
                prefillArgs
            }
        )
    }
    var layoutConfig by remember { mutableStateOf(prefillLayoutConfig) }
    var selectedKindTitles: MutableList<String> by remember { mutableStateOf(initialKindTitles) }
    var selectedKindUrls: MutableList<String?> by remember { mutableStateOf(initialKindUrls) }
    var showKindSelect by remember { mutableStateOf(false) }

    val typeList = remember(canSelectInfinite) {
        HomepageModuleType.entries.filter {
            it != HomepageModuleType.Unknown && (canSelectInfinite || !HomepageViewModel.isInfinite(it.key, null))
        }
    }

    val bgColor = ThemeStore.backgroundColor(LocalContext.current)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = androidx.compose.ui.graphics.Color(bgColor),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(if (prefillTitle.isEmpty()) stringResource(R.string.hp_add_module) else stringResource(R.string.hp_edit_module)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.hp_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = typeList.find { it.key == type }?.title ?: type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.hp_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }, containerColor = androidx.compose.ui.graphics.Color(bgColor)) {
                        typeList.forEach { moduleType ->
                            DropdownMenuItem(
                                text = { Text(moduleType.title) },
                                onClick = {
                                    type = moduleType.key
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }

                Text(stringResource(R.string.hp_selected_categories), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selectedKindTitles.isEmpty()) {
                        Text(
                            stringResource(R.string.hp_no_category_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        selectedKindTitles.forEachIndexed { index, kindTitle ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(kindTitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(
                                        onClick = {
                                            selectedKindTitles = selectedKindTitles.toMutableList().apply { removeAt(index) }
                                            selectedKindUrls = selectedKindUrls.toMutableList().apply { removeAt(index) }
                                            if (selectedKindTitles.size >= 2) {
                                                args = GSON.toJson(MultiKindsArgs(isMultiKinds = true, kindTitles = selectedKindTitles, kindUrls = selectedKindUrls))
                                            } else {
                                                args = ""
                                                url = selectedKindUrls.firstOrNull() ?: url
                                            }
                                        },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (allKinds.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showKindSelect = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (selectedKindTitles.isEmpty()) stringResource(R.string.hp_select_category)
                            else stringResource(R.string.hp_modify_category)
                        )
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.hp_url)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (HomepageViewModel.isInfinite(type, null) && !canSelectInfinite) {
                    Text(stringResource(R.string.hp_infinite_conflict), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text(stringResource(R.string.hp_args)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = layoutConfig,
                    onValueChange = { layoutConfig = it },
                    label = { Text(stringResource(R.string.hp_layout_config)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalUrl = if (selectedKindTitles.size == 1) selectedKindUrls.firstOrNull() ?: url else url
                onConfirm(ModuleDef(
                    title = title,
                    url = finalUrl,
                    type = type,
                    args = args,
                    layoutConfig = layoutConfig,
                ))
            }) { Text(stringResource(R.string.hp_determine)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.hp_cancel)) }
        },
    )

    if (showKindSelect) {
        val initialSelectedKeys = selectedKindTitles.mapIndexed { index, title ->
            "${title}||${selectedKindUrls.getOrNull(index)}"
        }.toSet()
        ExploreKindSelectSheet(
            show = true,
            onDismissRequest = { showKindSelect = false },
            kinds = allKinds,
            initialSelectedTitles = selectedKindTitles.toSet(),
            initialSelectedKeys = initialSelectedKeys,
            onSelected = { kinds ->
                selectedKindTitles = kinds.map { it.title }.toMutableList()
                selectedKindUrls = kinds.map { it.url }.toMutableList()
                if (selectedKindTitles.size >= 2) {
                    title = selectedKindTitles.joinToString("·")
                    args = GSON.toJson(MultiKindsArgs(isMultiKinds = true, kindTitles = selectedKindTitles, kindUrls = selectedKindUrls))
                } else if (selectedKindTitles.size == 1) {
                    title = selectedKindTitles.first()
                    args = ""
                    url = selectedKindUrls.firstOrNull() ?: url
                }
                showKindSelect = false
            },
        )
    }
}
