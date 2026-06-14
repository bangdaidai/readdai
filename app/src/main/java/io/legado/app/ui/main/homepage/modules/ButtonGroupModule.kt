package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.utils.GSON

@Composable
fun ButtonGroupModule(
    kinds: List<ExploreKind>,
    sourceUrl: String,
    globalId: String,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
    icon: String? = null,
    layoutConfig: String? = null,
) {
    if (kinds.isEmpty()) return

    val maxColumns = 5
    val total = kinds.size
    val numRows = (total + maxColumns - 1) / maxColumns
    val actualColumns = (total + numRows - 1) / numRows

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.chunked(actualColumns).forEach { rowKinds ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowKinds.forEach { kind ->
                    Card(
                        onClick = { viewModel.onKindUrlClick(sourceUrl, kind.url ?: "", kind.title) },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Text(
                            kind.title,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
                if (rowKinds.size < actualColumns) {
                    repeat(actualColumns - rowKinds.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
