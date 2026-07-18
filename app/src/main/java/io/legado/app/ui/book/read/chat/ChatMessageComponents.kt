package io.legado.app.ui.book.read.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.ai.AiMessagePart
import io.legado.app.help.ai.ChatMessageItem
import io.legado.app.help.ai.ToolStep
import io.legado.app.help.ai.ToolStepStatus
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundCard
import io.legado.app.lib.theme.dividerColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.MarkdownUtils

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
        if (isUser) {
            UserMessageBubble(content = message.content)
        } else {
            AssistantMessageBubble(
                message = message,
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
    message: ChatMessageItem,
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
    val content = message.content

    Column(modifier = Modifier.fillMaxWidth(0.92f)) {
        message.assistantLabel?.let {
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
            val parts = message.parts
            if (parts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var toolGroup = mutableListOf<AiMessagePart.Tool>()
                    parts.forEachIndexed { index, part ->
                        if (part is AiMessagePart.Tool) {
                            toolGroup.add(part)
                        }
                        val isLast = index == parts.lastIndex
                        val nextIsNotTool = !isLast && parts[index + 1] !is AiMessagePart.Tool
                        if (part !is AiMessagePart.Tool || isLast || nextIsNotTool) {
                            if (toolGroup.isNotEmpty()) {
                                ToolStepsCard(steps = toolGroup.map {
                                    ToolStep(id = it.toolCallId, name = it.toolName, input = it.input, output = it.output, status = it.status)
                                })
                                toolGroup.clear()
                            }
                        }
                        when (part) {
                            is AiMessagePart.Text -> {
                                if (part.text.isNotBlank()) {
                                    MarkdownText(
                                        text = part.text,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                            is AiMessagePart.Reasoning -> {
                                if (part.text.isNotBlank()) {
                                    ReasoningCard(content = part.text, isStreaming = isStreaming)
                                }
                            }
                            is AiMessagePart.BookResult -> {
                                Text(
                                    text = "📚 ${part.name}",
                                    fontSize = 14.sp,
                                    color = Color(context.primaryTextColor)
                                )
                            }
                            is AiMessagePart.Tool -> { }
                        }
                    }
                }
            } else {
                if (message.reasoningContent?.isNotBlank() == true) {
                    ReasoningCard(content = message.reasoningContent, isStreaming = isStreaming)
                }
                if (message.toolSteps.isNotEmpty()) {
                    ToolStepsCard(steps = message.toolSteps)
                }
                if (content.isBlank()) {
                    if (isStreaming) {
                        Text(text = "思考中...", color = Color(context.secondaryTextColor), fontSize = 14.sp)
                    }
                } else {
                    MarkdownText(text = content, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        if (!isStreaming) {
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
    isStreaming: Boolean = false,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (content.isBlank()) return
    var expanded by remember { mutableStateOf(initiallyExpanded || isStreaming) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(context.dividerColor)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(context.secondaryTextColor)
                )
                Text(
                    text = if (isStreaming) "深度思考中..." else "思考过程",
                    color = Color(context.primaryTextColor),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = Color(context.secondaryTextColor)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 0.dp)
                        .padding(bottom = 12.dp),
                    color = Color(context.secondaryTextColor),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEach { step ->
            ToolStepRow(step = step)
        }
    }
}

@Composable
private fun ToolStepRow(step: ToolStep) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    val hasContent = step.output?.isNotBlank() == true || step.input?.isNotBlank() == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(context.backgroundCard)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(context.dividerColor))
    ) {
        Column(        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasContent) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(context.backgroundCard)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(context.accentColor)
                )
            }
            Text(
                text = step.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(context.primaryTextColor),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            val statusText = when (step.status) {
                ToolStepStatus.PENDING -> "等待中"
                ToolStepStatus.RUNNING -> "运行中"
                ToolStepStatus.SUCCESS -> "已完成"
                ToolStepStatus.FAILED -> "失败"
            }
            Text(
                text = statusText,
                fontSize = 11.sp,
                color = Color(context.secondaryTextColor)
            )
            if (hasContent) {
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
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                step.input?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "输入",
                        fontSize = 11.sp,
                        color = Color(context.secondaryTextColor),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = it.take(300),
                        fontSize = 11.sp,
                        color = Color(context.secondaryTextColor),
                        maxLines = 6
                    )
                }
                step.output?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = "输出",
                        fontSize = 11.sp,
                        color = Color(context.secondaryTextColor),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = it.take(400),
                        fontSize = 11.sp,
                        color = Color(context.secondaryTextColor),
                        maxLines = 8
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = Color(context.primaryTextColor).toArgb()
    val markwon = remember { MarkdownUtils.getMarkwon(context) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, text)
        },
        modifier = modifier
    )
}
