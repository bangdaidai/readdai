package io.legado.app.ui.book.read.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.backgroundCard
import io.legado.app.lib.theme.dividerColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.main.homepage.ReaddaiTheme
import kotlinx.coroutines.launch

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
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.init(bookUrl, bookTitle, author, chapterTitle, chapterContent, selectedText)
    }

    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AiChatEffect.ShowToast -> {
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
                HistoryDrawer(
                    conversations = uiState.conversations,
                    onSelect = { id ->
                        scope.launch { drawerState.close() }
                        viewModel.onIntent(AiChatIntent.SelectConversation(id))
                    },
                    onDelete = { id ->
                        viewModel.onIntent(AiChatIntent.DeleteConversation(id))
                    },
                    onNewChat = {
                        scope.launch { drawerState.close() }
                        viewModel.onIntent(AiChatIntent.NewConversation)
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = Color(context.backgroundColor),
                topBar = {
                    ChatTopBar(
                        onBack = onFinish,
                        onOpenHistory = { scope.launch { drawerState.open() } },
                        onNewChat = { viewModel.onIntent(AiChatIntent.NewConversation) },
                        onClearChat = { viewModel.onIntent(AiChatIntent.ClearChat) },
                        onExport = { viewModel.onIntent(AiChatIntent.ExportChat) },
                        onOpenSettings = onOpenSettings
                    )
                },
                bottomBar = {
                    ChatInputBar(
                        state = uiState,
                        onSend = { viewModel.onIntent(AiChatIntent.SendMessage(it)) },
                        onStop = { viewModel.onIntent(AiChatIntent.StopGenerating) },
                        onToggleDeepThinking = { viewModel.onIntent(AiChatIntent.ToggleDeepThinking(it)) },
                        onToggleSpoilerFree = { viewModel.onIntent(AiChatIntent.ToggleSpoilerFree(it)) },
                        onRemoveQuote = { viewModel.onIntent(AiChatIntent.SetQuote(null)) },
                        onQuickAction = { viewModel.onIntent(AiChatIntent.ExecuteQuickAction(it)) }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (uiState.messages.isEmpty() && uiState.suggestions.isNotEmpty()) {
                        EmptyStateSuggestions(
                            suggestions = uiState.suggestions,
                            onExecute = { viewModel.onIntent(AiChatIntent.ExecuteSuggestion(it)) }
                        )
                    } else {
                        MessageList(
                            state = uiState,
                            onCopy = { viewModel.onIntent(AiChatIntent.CopyMessage(it)) },
                            onShare = { viewModel.onIntent(AiChatIntent.ShareMessage(it)) },
                            onDelete = { viewModel.onIntent(AiChatIntent.DeleteMessage(it)) },
                            onRegenerate = { viewModel.onIntent(AiChatIntent.RegenerateLastMessage) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewChat: () -> Unit,
    onClearChat: () -> Unit,
    onExport: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val titleBarColor = Color(ThemeStore.titleBarTextIconColor(context))

    TopAppBar(
        title = {
            Text(
                text = "AI阅读助手",
                color = titleBarColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = titleBarColor
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "历史",
                    tint = titleBarColor
                )
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建",
                    tint = titleBarColor
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = titleBarColor
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = Color(context.backgroundCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("清空对话", color = Color(context.primaryTextColor)) },
                        onClick = {
                            showMenu = false
                            onClearChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出对话", color = Color(context.primaryTextColor)) },
                        onClick = {
                            showMenu = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("设置", color = Color(context.primaryTextColor)) },
                        onClick = {
                            showMenu = false
                            onOpenSettings()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(context.primaryColor)
        )
    )
}

@Composable
private fun MessageList(
    state: AiChatUiState,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (io.legado.app.help.ai.ChatMessageItem) -> Unit,
    onRegenerate: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.count(), state.isSending) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.count() - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = false
    ) {
        items(
            items = state.messages,
            key = { it.id }
        ) { message ->
            val isLast = message === state.messages.lastOrNull()
            ChatMessageBubble(
                message = message,
                isStreaming = state.isSending && isLast && message.role == "ai",
                onCopy = onCopy,
                onShare = onShare,
                onDelete = { onDelete(message) },
                onRegenerate = onRegenerate
            )
        }
    }
}

@Composable
private fun EmptyStateSuggestions(
    suggestions: List<SuggestionItemUi>,
    onExecute: (SuggestionItemUi) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AI阅读助手",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(context.primaryTextColor)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "可以帮你总结、翻译、分析小说内容",
            fontSize = 14.sp,
            color = Color(context.secondaryTextColor)
        )
        Spacer(modifier = Modifier.height(24.dp))

        suggestions.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onExecute(item) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(context.backgroundCard)
                        )
                    ) {
                        Text(
                            text = item.displayText,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            color = Color(context.primaryTextColor)
                        )
                    }
                }
                if (rowItems.count() < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChatInputBar(
    state: AiChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onToggleDeepThinking: (Boolean) -> Unit,
    onToggleSpoilerFree: (Boolean) -> Unit,
    onRemoveQuote: () -> Unit,
    onQuickAction: (QuickActionItemUi) -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(TextFieldValue()) }

    val accentColor = Color(context.accentColor)
    val cardBg = Color(context.backgroundCard)
    val dividerColor = Color(context.dividerColor)
    val textPrimary = Color(context.primaryTextColor)
    val textSecondary = Color(context.secondaryTextColor)

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(
                visible = state.quickActions.isNotEmpty() && !state.isSending,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.quickActions.take(4).forEach { item ->
                        Chip(
                            text = item.displayName,
                            onClick = { onQuickAction(item) },
                            cardBg = cardBg,
                            dividerColor = dividerColor,
                            textColor = textPrimary
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor)
            ) {
                Column {
                    AnimatedVisibility(
                        visible = state.selectedQuote?.isNotBlank() == true,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        QuoteBar(
                            text = state.selectedQuote ?: "",
                            onRemove = onRemoveQuote,
                            accentColor = accentColor,
                            textPrimary = textPrimary
                        )
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "输入消息...",
                                color = textSecondary,
                                fontSize = 15.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = accentColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                        maxLines = 5,
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (input.text.isNotBlank()) {
                                    onSend(input.text)
                                    input = TextFieldValue()
                                }
                            }
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Send
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OptionChip(
                            text = "深度思考",
                            iconRes = R.drawable.ic_brain,
                            selected = state.deepThinkingEnabled,
                            onClick = { onToggleDeepThinking(!state.deepThinkingEnabled) },
                            accentColor = accentColor,
                            textSecondary = textSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OptionChip(
                            text = "防剧透",
                            iconRes = R.drawable.ic_visibility_off,
                            selected = state.spoilerFreeEnabled,
                            onClick = { onToggleSpoilerFree(!state.spoilerFreeEnabled) },
                            accentColor = accentColor,
                            textSecondary = textSecondary
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        if (state.isSending) {
                            IconButton(onClick = onStop) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "停止",
                                    tint = accentColor
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (input.text.isNotBlank()) {
                                        onSend(input.text)
                                        input = TextFieldValue()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "发送",
                                    tint = accentColor
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
private fun QuoteBar(
    text: String,
    onRemove: () -> Unit,
    accentColor: Color,
    textPrimary: Color
) {
    val context = LocalContext.current
    val semiAccent = accentColor.copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(semiAccent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "引用: ${text.take(50)}${if (text.length > 50) "..." else ""}",
            fontSize = 13.sp,
            color = textPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除引用",
                modifier = Modifier.size(16.dp),
                tint = textPrimary
            )
        }
    }
}

@Composable
private fun OptionChip(
    text: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    textSecondary: Color
) {
    val context = LocalContext.current
    val bgColor = if (selected) accentColor else Color.Transparent
    val textColor = if (selected) Color.White else textSecondary
    val borderColor = if (selected) accentColor else textSecondary

    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(999.dp))
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

@Composable
private fun Chip(
    text: String,
    onClick: () -> Unit,
    cardBg: Color,
    dividerColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .background(cardBg, RoundedCornerShape(12.dp))
            .border(1.dp, dividerColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = textColor
        )
    }
}

@Composable
private fun HistoryDrawer(
    conversations: List<AiChatConversationUi>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit
) {
    val context = LocalContext.current

    ModalDrawerSheet(
        drawerContainerColor = Color(context.backgroundColor),
        drawerContentColor = Color(context.primaryTextColor)
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
                fontWeight = FontWeight.Bold,
                color = Color(context.primaryTextColor),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建",
                    tint = Color(context.accentColor)
                )
            }
        }

        Divider(color = Color(context.dividerColor))

        if (conversations.isEmpty()) {
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(conversations) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (conv.isSelected) Color(context.accentColor).copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(conv.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv.title,
                                fontSize = 14.sp,
                                color = Color(context.primaryTextColor),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatTimestamp(conv.updatedAt),
                                fontSize = 12.sp,
                                color = Color(context.secondaryTextColor)
                            )
                        }
                        IconButton(onClick = { onDelete(conv.id) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(18.dp),
                                tint = Color(context.secondaryTextColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
        else -> {
            val date = java.util.Date(timestamp)
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT,
                java.util.Locale.getDefault()
            ).format(date)
        }
    }
}
