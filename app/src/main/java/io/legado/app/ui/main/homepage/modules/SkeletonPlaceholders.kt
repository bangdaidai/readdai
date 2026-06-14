package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import io.legado.app.domain.model.HomepageModuleType

@Composable
private fun rememberShimmerBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) {
        listOf(
            colorScheme.surfaceContainerHighest,
            colorScheme.surfaceContainerHigh,
            colorScheme.surfaceContainerHighest,
        )
    }
    return Brush.linearGradient(
        colors = colors,
        start = Offset(offset * 300f, 0f),
        end = Offset(offset * 300f + 300f, 0f),
    )
}

@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(rememberShimmerBrush())
    )
}

@Composable
fun WaterfallSkeletonItem(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                cornerRadius = 16.dp,
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp))
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonBox(modifier = Modifier.width(40.dp).height(18.dp), cornerRadius = 4.dp)
                    SkeletonBox(modifier = Modifier.width(52.dp).height(18.dp), cornerRadius = 4.dp)
                }
            }
        }
    }
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor),
        content = content,
    )
}

@Composable
fun GridSkeletonItem(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().aspectRatio(5f / 7f),
            cornerRadius = 4.dp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp))
    }
}

@Composable
fun GridSkeletonModule(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 2,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(columns) {
                    GridSkeletonItem(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BannerSkeletonModule(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(itemCount) {
            SkeletonBox(
                modifier = Modifier.width(96.dp).aspectRatio(5f / 7f),
                cornerRadius = 12.dp,
            )
        }
    }
}

@Composable
fun CardSkeletonItem(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().aspectRatio(5f / 7f),
            cornerRadius = 0.dp,
        )
        Column(modifier = Modifier.padding(8.dp)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(4.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
        }
    }
}

@Composable
fun CardSkeletonModule(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(itemCount) {
            CardSkeletonItem()
        }
    }
}

@Composable
fun RankingSkeletonModule(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    Card(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            repeat(rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    SkeletonBox(modifier = Modifier.width(52.dp).aspectRatio(5f / 7f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GridRankingSkeletonModule(
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    Card(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            repeat(rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(modifier = Modifier.width(48.dp).aspectRatio(5f / 7f))
                    Spacer(modifier = Modifier.width(8.dp))
                    SkeletonBox(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HomepageModuleSkeleton(
    type: HomepageModuleType,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 2,
) {
    when (type) {
        HomepageModuleType.Waterfall -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { WaterfallSkeletonItem(modifier = Modifier.fillMaxWidth()) }
            }
        }
        HomepageModuleType.InfiniteGrid -> GridSkeletonModule(modifier, columns, rows)
        HomepageModuleType.Grid -> GridSkeletonModule(modifier, columns, rows)
        HomepageModuleType.Banner -> BannerSkeletonModule(modifier)
        HomepageModuleType.Card -> CardSkeletonModule(modifier)
        HomepageModuleType.Ranking -> RankingSkeletonModule(modifier)
        HomepageModuleType.GridRanking -> GridRankingSkeletonModule(modifier)
        HomepageModuleType.ButtonGroup -> {}
        HomepageModuleType.Unknown -> {}
    }
}
