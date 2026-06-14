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
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(if (prefillTitle.isEmpty()) "添加模块" else "编辑模块") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                )

                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = typeList.find { it.key == type }?.title ?: type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        typeList.forEach { moduleType ->
                            DropdownMenuItem(
                                text = { Text(moduleType.title) },
                                onClick = { type = moduleType.key; typeExpanded = false },
                            )
                        }
                    }
                }

                if (HomepageViewModel.isInfinite(type, null) && !canSelectInfinite) {
                    Text("该集合已存在无限流模块", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text("Args (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("布局配置", style = MaterialTheme.typography.labelMedium)

                OutlinedTextField(
                    value = layoutConfig,
                    onValueChange = { layoutConfig = it },
                    label = { Text("LayoutConfig (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ModuleDef(title = title, url = url, type = type, args = args, layoutConfig = layoutConfig))
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        },
    )
}
