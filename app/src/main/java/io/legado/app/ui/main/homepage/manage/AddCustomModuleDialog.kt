package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.main.homepage.HomepageViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    onDismissRequest: () -> Unit,
    onConfirm: (ModuleDef) -> Unit,
) {
    var title by remember { mutableStateOf(prefillTitle) }
    var url by remember { mutableStateOf(prefillUrl) }
    var type by remember { mutableStateOf(prefillType) }
    var args by remember { mutableStateOf(prefillArgs) }
    var layoutConfig by remember { mutableStateOf(prefillLayoutConfig) }

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
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.hp_url)) },
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
                                onClick = { type = moduleType.key; typeExpanded = false },
                            )
                        }
                    }
                }

                if (HomepageViewModel.isInfinite(type, null) && !canSelectInfinite) {
                    Text(stringResource(R.string.hp_infinite_conflict), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text(stringResource(R.string.hp_args)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.hp_layout_config), style = MaterialTheme.typography.labelMedium)

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
                onConfirm(ModuleDef(title = title, url = url, type = type, args = args, layoutConfig = layoutConfig))
            }) { Text(stringResource(R.string.hp_determine)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.hp_cancel)) }
        },
    )
}
