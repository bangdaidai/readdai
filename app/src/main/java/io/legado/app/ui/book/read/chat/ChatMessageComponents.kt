package io.legado.app.ui.book.read.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.ai.ChatMessageItem
import io.legado.app.help.ai.ToolStep
import io.legado.app.help.ai.ToolStepStatus
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundCard
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.MarkdownUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun ChatMessageBubble(
    message: ChatMessageItem,
    isStreaming: Boolean = false,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onSwitchBranch: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (message.reasoningContent?.isNotBlank() == true) {
            ReasoningCard(
                content = message.reasoningContent,
                initiallyExpanded = message.isReasoningExpanded,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(bottom = 4.dp)
            )
        }

        if (message.toolSteps.isNotEmpty()) {
            ToolStepsCard(
                steps = message.toolSteps,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(bottom = 4.dp)
            )
        }

        if (isUser) {
            UserMessageBubble(content = message.content)
        } else {
            AssistantMessageBubble(
                content = message.content,
                assistantLabel = message.assistantLabel,
                isStreaming = isStreaming,
                totalBranches = message.totalBranches,
                branchIndex = message.branchIndex,
                onCopy = { onCopy(message.content) },
                onShare = { onShare(message.content) },
                onDelete = onDelete,
                onRegenerate = onRegenerate,
                onSwitchBranch = onSwitchBranch
            )
        }
    }
}

@Composable
private fun UserMessageBubble(content: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .background(
                color = Color(context.accentColor),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = content,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun AssistantMessageBubble(
    content: String,
    assistantLabel: String?,
    isStreaming: Boolean,
    totalBranches: Int,
    branchIndex: Int,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onSwitchBranch: ((Int) -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth(0.92f)) {
        assistantLabel?.let {
            Text(
                text = it,
                color = Color(context.secondaryTextColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            if (content.isBlank()) {
                if (isStreaming) {
                    Text(
                        text = "思考中...",
                        color = Color(context.secondaryTextColor),
                        fontSize = 14.sp
                    )
                }
            } else {
                MarkdownText(
                    text = content,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (totalBranches > 1 && onSwitchBranch != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(enabled = branchIndex > 0) {
                                onSwitchBranch(-1)
                            },
                        tint = if (branchIndex > 0) Color(context.secondaryTextColor)
                               else Color(context.secondaryTextColor).copy(alpha = 0.3f)
                    )
                    Text(
                        text = "${branchIndex + 1}/$totalBranches",
                        color = Color(context.secondaryTextColor),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(enabled = branchIndex < totalBranches - 1) {
                                onSwitchBranch(1)
                            },
                        tint = if (branchIndex < totalBranches - 1) Color(context.secondaryTextColor)
                               else Color(context.secondaryTextColor).copy(alpha = 0.3f)
                    )
                }
            }
            TextButtonSmall(text = "复制", onClick = onCopy)
            TextButtonSmall(text = "分享", onClick = onShare)
            TextButtonSmall(text = "重写", onClick = onRegenerate)
            TextButtonSmall(text = "删除", onClick = onDelete)
        }
    }
}

@Composable
private fun TextButtonSmall(
    text: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        color = Color(context.secondaryTextColor),
        fontSize = 12.sp
    )
}

@Composable
fun ReasoningCard(
    content: String,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    val context = LocalContext.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(context.backgroundCard)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                    tint = Color(context.accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "深度思考",
                    color = Color(context.accentColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    color = Color(context.secondaryTextColor),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun ToolStepsCard(
    steps: List<ToolStep>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(context.backgroundCard)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "调用工具",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(context.primaryTextColor),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            steps.forEach { step ->
                ToolStepItem(step = step)
            }
        }
    }
}

@Composable
private fun ToolStepItem(step: ToolStep) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolStatusDot(status = step.status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = step.name,
                fontSize = 13.sp,
                color = Color(context.primaryTextColor),
                modifier = Modifier.weight(1f)
            )
            if (step.output?.isNotBlank() == true || step.input?.isNotBlank() == true) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = Color(context.secondaryTextColor)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                step.input?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "输入: ${it.take(200)}",
                        fontSize = 12.sp,
                        color = Color(context.secondaryTextColor),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                step.output?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "结果: ${it.take(300)}",
                        fontSize = 12.sp,
                        color = Color(context.secondaryTextColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolStatusDot(status: ToolStepStatus) {
    val color = when (status) {
        ToolStepStatus.PENDING -> Color.Gray
        ToolStepStatus.RUNNING -> Color(0xFF4CAF50)
        ToolStepStatus.SUCCESS -> Color(0xFF2196F3)
        ToolStepStatus.FAILED -> Color(0xFFF44336)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color = color, shape = RoundedCornerShape(4.dp))
    )
}

@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        text = text,
        modifier = modifier,
        color = Color(context.primaryTextColor),
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}
