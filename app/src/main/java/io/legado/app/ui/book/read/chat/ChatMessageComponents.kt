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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.LaunchedEffect
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
import android.content.Intent
import android.text.Spannable
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
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
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity

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
            .fillMaxWidth(0.8f)
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

    Column(modifier = Modifier.fillMaxWidth()) {
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
                                    SelectionContainer {
                                        MarkdownText(
                                            text = part.text,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            is AiMessagePart.Reasoning -> {
                                if (part.text.isNotBlank()) {
                                    ReasoningCard(content = part.text, isStreaming = isStreaming, initiallyExpanded = !isStreaming)
                                }
                            }
                            is AiMessagePart.BookResult -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(context.backgroundCard))
                                        .clickable {
                                            if (part.bookUrl.isNotBlank()) {
                                                val intent = Intent(context, BookInfoActivity::class.java)
                                                intent.putExtra("bookUrl", part.bookUrl)
                                                intent.putExtra("name", part.name)
                                                intent.putExtra("author", part.author)
                                                intent.putExtra("origin", part.origin)
                                                context.startActivity(intent)
                                            } else {
                                                val intent = Intent(context, SearchActivity::class.java)
                                                intent.putExtra("key", part.name)
                                                context.startActivity(intent)
                                            }
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📚",
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = part.name,
                                            fontSize = 14.sp,
                                            color = Color(context.primaryTextColor),
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (!part.author.isNullOrBlank()) {
                                            Text(
                                                text = part.author,
                                                fontSize = 12.sp,
                                                color = Color(context.secondaryTextColor)
                                            )
                                        }
                                        if (!part.intro.isNullOrBlank()) {
                                            Text(
                                                text = part.intro,
                                                fontSize = 11.sp,
                                                color = Color(context.secondaryTextColor),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                            is AiMessagePart.Tool -> { }
                        }
                    }
                }
            } else {
                var hasContentAbove = false
                if (message.reasoningContent?.isNotBlank() == true) {
                    ReasoningCard(content = message.reasoningContent, isStreaming = isStreaming)
                    hasContentAbove = true
                }
                if (message.toolSteps.isNotEmpty()) {
                    if (hasContentAbove) Spacer(modifier = Modifier.height(8.dp))
                    ToolStepsCard(steps = message.toolSteps)
                    hasContentAbove = true
                }
                if (content.isBlank()) {
                    if (isStreaming) {
                        if (hasContentAbove) Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "思考中...", color = Color(context.secondaryTextColor), fontSize = 14.sp)
                    }
                } else {
                    if (hasContentAbove) Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        MarkdownText(text = content, modifier = Modifier.padding(vertical = 2.dp))
                    }
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
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            expanded = true
        }
    }
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp)) {
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
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
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
                            text = it,
                            fontSize = 11.sp,
                            color = Color(context.secondaryTextColor)
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
                            text = it,
                            fontSize = 11.sp,
                            color = Color(context.secondaryTextColor)
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

    Box(modifier = modifier.animateContentSize()) {
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(textColor)
                    movementMethod = LinkMovementMethod.getInstance()
                }
            },
            update = { textView ->
                textView.setTextColor(textColor)
                val contentChanged = textView.text?.toString() != text
                if (contentChanged) {
                    markwon.setMarkdown(textView, text)
                    applyBookTitleClickableSpans(textView, context)
                    textView.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            textView.viewTreeObserver.removeOnPreDrawListener(this)
                            var root: ViewGroup? = textView.parent as? ViewGroup
                            while (root?.parent is ViewGroup) {
                                root = root.parent as ViewGroup
                            }
                            root?.let { traverseAndApplyBookTitleSpans(it, context) }
                            return true
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun traverseAndApplyBookTitleSpans(view: View, context: android.content.Context) {
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is TextView) {
                applyBookTitleClickableSpans(child, context)
            }
            traverseAndApplyBookTitleSpans(child, context)
        }
    }
}

private fun applyBookTitleClickableSpans(textView: TextView, context: android.content.Context) {
    val spannable = textView.text as? Spannable ?: return
    val bookTitlePattern = Regex("《([^》]+)》")
    val matches = bookTitlePattern.findAll(spannable.toString())
    var changed = false
    matches.forEach { matchResult ->
        val bookName = matchResult.groupValues[1]
        val start = matchResult.range.first
        val end = matchResult.range.last + 1
        val clickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(context, SearchActivity::class.java)
                intent.putExtra("key", bookName)
                context.startActivity(intent)
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
            }
        }
        spannable.setSpan(
            clickSpan,
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        changed = true
    }
    if (changed) {
        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
    }
}
