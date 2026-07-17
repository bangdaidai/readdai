package io.legado.app.ui.book.read.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.backgroundCard
import io.legado.app.lib.theme.dividerColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.main.homepage.ReaddaiTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel = viewModel(),
    bookUrl: String?,
    bookTitle: String?,
    author: String?,
    chapterTitle: String?,
    chapterContent: String?,
    selectedText: String?,
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.init(bookUrl, bookTitle, author, chapterTitle, chapterContent, selectedText)
    }

    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AiChatEffect.ShowToast -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is AiChatEffect.ShowSnackbar -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is AiChatEffect.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, effect.text)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享到"))
                }
                AiChatEffect.NavigateSettings -> onOpenSettings()
                AiChatEffect.Finish -> onFinish()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ReaddaiTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                RecentChatsDrawer(
                    conversations = uiState.conversations,
                    onNewChat = {
                        viewModel.onIntent(AiChatIntent.NewConversation)
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = {
                        viewModel.onIntent(AiChatIntent.SelectConversation(it))
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = {
                        viewModel.onIntent(AiChatIntent.DeleteConversation(it))
                    }
                )
            }
        ) {
            val hasBgImage = remember(context) {
                try {
                    ThemeConfig.getBgImage(context, context.resources.displayMetrics) != null
                } catch (_: Exception) { false }
            }
            val pageBgColor = Color(context.backgroundColor)
            val effectiveBgColor = if (hasBgImage) Color.Transparent else pageBgColor

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(effectiveBgColor)
            ) {
                val systemBottomPadding = maxOf(
                    WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
                val topContentPadding = 88.dp
                val bottomContentPadding = 60.dp + systemBottomPadding

                val isNearBottom by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()
                        lastVisible == null || lastVisible.index >= info.totalItemsCount - 3
                    }
                }
                val isAtBottom by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()
                        lastVisible == null || lastVisible.index >= info.totalItemsCount - 1
                    }
                }

                var shouldStickToBottom by remember { mutableStateOf(true) }

                LaunchedEffect(uiState.messages.size, uiState.isSending) {
                    shouldStickToBottom = true
                }

                LaunchedEffect(listState) {
                    snapshotFlow { listState.isScrollInProgress to isNearBottom }
                        .collectLatest { (isScrolling, nearBottom) ->
                            if (isScrolling) {
                                shouldStickToBottom = nearBottom
                            }
                        }
                }

                LaunchedEffect(shouldStickToBottom, uiState.messages.size, uiState.isSending) {
                    if (shouldStickToBottom && !listState.isScrollInProgress && uiState.messages.isNotEmpty()) {
                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                        if (lastIndex >= 0) {
                            listState.animateScrollToItem(lastIndex)
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = topContentPadding + 8.dp,
                        end = 8.dp,
                        bottom = bottomContentPadding + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.messages.isEmpty() && uiState.suggestions.isNotEmpty()) {
                        item {
                            EmptyChatHint(
                                suggestions = uiState.suggestions,
                                onExecute = { viewModel.onIntent(AiChatIntent.ExecuteSuggestion(it)) }
                            )
                        }
                    }
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageBubble(
                            message = message,
                            isStreaming = false,
                            onCopy = { viewModel.onIntent(AiChatIntent.CopyMessage(it)) },
                            onShare = { viewModel.onIntent(AiChatIntent.ShareMessage(it)) },
                            onDelete = { viewModel.onIntent(AiChatIntent.DeleteMessage(message)) },
                            onRegenerate = { viewModel.onIntent(AiChatIntent.RegenerateLastMessage) }
                        )
                    }
                    uiState.streamingMessage?.let { msg ->
                        item(key = "streaming_message") {
                            ChatMessageBubble(
                                message = msg,
                                isStreaming = true,
                                onCopy = { viewModel.onIntent(AiChatIntent.CopyMessage(it)) },
                                onShare = { viewModel.onIntent(AiChatIntent.ShareMessage(it)) },
                                onDelete = { viewModel.onIntent(AiChatIntent.DeleteMessage(msg)) },
                                onRegenerate = { viewModel.onIntent(AiChatIntent.RegenerateLastMessage) }
                            )
                        }
                    }
                    item(key = "bottom_anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        if (!isAtBottom && uiState.messages.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color(context.backgroundCard))
                                            .clickable {
                                                scope.launch {
                                                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                                                    if (lastIndex >= 0) {
                                                        listState.animateScrollToItem(lastIndex)
                                                    }
                                                    shouldStickToBottom = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = Color(context.primaryTextColor)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            AnimatedVisibility(
                                visible = uiState.selectedQuote?.isNotBlank() == true,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                QuoteBar(
                                    text = uiState.selectedQuote ?: "",
                                    onRemove = { viewModel.onIntent(AiChatIntent.SetQuote(null)) }
                                )
                            }

                            ChatInputBar(
                                value = draft,
                                isSending = uiState.isSending,
                                deepThinkingEnabled = uiState.deepThinkingEnabled,
                                spoilerFreeEnabled = uiState.spoilerFreeEnabled,
                                onValueChange = { draft = it },
                                onSend = {
                                    val text = draft
                                    draft = ""
                                    viewModel.onIntent(AiChatIntent.SendMessage(text))
                                },
                                onStop = { viewModel.onIntent(AiChatIntent.StopGenerating) },
                                onToggleDeepThinking = { viewModel.onIntent(AiChatIntent.ToggleDeepThinking(it)) },
                                onToggleSpoilerFree = { viewModel.onIntent(AiChatIntent.ToggleSpoilerFree(it)) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(context.backgroundCard))
                                .clickable(onClick = onFinish),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "返回",
                                tint = Color(context.primaryTextColor)
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Surface(
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(50),
                                color = Color(context.backgroundCard)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .padding(horizontal = 12.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = uiState.conversationTitle.ifBlank { "新对话" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(context.primaryTextColor),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = uiState.modelName.ifBlank { "默认模型" },
                                        fontSize = 11.sp,
                                        color = Color(context.secondaryTextColor),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(context.backgroundCard))
                                    .clickable { scope.launch { drawerState.open() } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "历史",
                                    tint = Color(context.primaryTextColor)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(context.backgroundCard))
                                    .clickable { viewModel.onIntent(AiChatIntent.NewConversation) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "新建",
                                    tint = Color(context.primaryTextColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatHint(
    suggestions: List<SuggestionItemUi>,
    onExecute: (SuggestionItemUi) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AI阅读助手",
                fontSize = 20.sp,
                color = Color(context.secondaryTextColor),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(24.dp))
            suggestions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(context.backgroundCard))
                                .clickable { onExecute(item) }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = item.displayText,
                                fontSize = 13.sp,
                                color = Color(context.primaryTextColor)
                            )
                        }
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    isSending: Boolean,
    deepThinkingEnabled: Boolean,
    spoilerFreeEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleDeepThinking: (Boolean) -> Unit,
    onToggleSpoilerFree: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isKeyboardVisible =
        WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val horizontalPadding by animateDpAsState(
        targetValue = if (isKeyboardVisible) 16.dp else 46.dp,
        animationSpec = tween(durationMillis = 250),
        label = "AiChatInputHorizontalPadding"
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (isKeyboardVisible) 16.dp else 32.dp,
        animationSpec = tween(durationMillis = 250),
        label = "AiChatInputBottomPadding"
    )

    var showOptions by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                bottom = bottomPadding
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color(context.backgroundCard)
    ) {
        Column {
            AnimatedVisibility(
                visible = showOptions,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OptionChip(
                        text = "深度思考",
                        iconRes = R.drawable.ic_brain,
                        selected = deepThinkingEnabled,
                        onClick = { onToggleDeepThinking(!deepThinkingEnabled) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OptionChip(
                        text = "防剧透",
                        iconRes = R.drawable.ic_visibility_off,
                        selected = spoilerFreeEnabled,
                        onClick = { onToggleSpoilerFree(!spoilerFreeEnabled) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (showOptions) Color(context.accentColor).copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable { showOptions = !showOptions },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "选项",
                        tint = if (deepThinkingEnabled || spoilerFreeEnabled) Color(context.accentColor)
                        else Color(context.secondaryTextColor)
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !isSending,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 160.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = Color(context.primaryTextColor)
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "输入消息...",
                                    fontSize = 15.sp,
                                    color = Color(context.secondaryTextColor)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (isSending) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止",
                            tint = Color(context.accentColor)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (value.isNotBlank()) Modifier.clickable(onClick = onSend)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (value.isNotBlank()) Color(context.accentColor)
                            else Color(context.secondaryTextColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteBar(
    text: String,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = Color(context.accentColor)
    val semiAccent = accentColor.copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(semiAccent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "引用: ${text.take(50)}${if (text.length > 50) "..." else ""}",
            fontSize = 13.sp,
            color = Color(context.primaryTextColor),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除引用",
                modifier = Modifier.size(16.dp),
                tint = Color(context.primaryTextColor)
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun OptionChip(
    text: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = Color(context.accentColor)
    val bgColor = if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (selected) accentColor else Color(context.secondaryTextColor)
    val borderColor = if (selected) accentColor.copy(alpha = 0.5f) else Color(context.dividerColor)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = textColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentChatsDrawer(
    conversations: List<AiChatConversationUi>,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    val context = LocalContext.current
    var conversationToDelete by remember { mutableStateOf<AiChatConversationUi?>(null) }

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = Color(context.backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史对话",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(context.primaryTextColor),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onNewChat),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建",
                    tint = Color(context.primaryTextColor)
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无历史记录",
                            color = Color(context.secondaryTextColor),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            items(conversations, key = { it.id }) { conversation ->
                val isSelected = conversation.isSelected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Color(context.accentColor).copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable { onSelectConversation(conversation.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                fontSize = 14.sp,
                                maxLines = 2,
                                color = if (isSelected) Color(context.accentColor)
                                else Color(context.primaryTextColor)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatRelativeTime(conversation.updatedAt),
                                fontSize = 12.sp,
                                color = Color(context.secondaryTextColor)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { conversationToDelete = conversation },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(16.dp),
                                tint = Color(context.secondaryTextColor)
                            )
                        }
                    }
                }
            }
        }
    }

    if (conversationToDelete != null) {
        androidx.compose.material3.AlertDialog(
            containerColor = Color(context.backgroundCard),
            onDismissRequest = { conversationToDelete = null },
            title = {
                Text(
                    text = "删除",
                    color = Color(context.primaryTextColor)
                )
            },
            text = {
                Text(
                    text = "确定删除此对话？",
                    color = Color(context.primaryTextColor)
                )
            },
            confirmButton = {
                Text(
                    text = "删除",
                    color = Color(context.accentColor),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            conversationToDelete?.let { onDeleteConversation(it.id) }
                            conversationToDelete = null
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    color = Color(context.secondaryTextColor),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { conversationToDelete = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        days < 30 -> "${days / 7}周前"
        else -> {
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
