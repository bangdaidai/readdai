package io.legado.app.ui.book.read.chat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.help.ai.AiAssistantConfigManager
import io.legado.app.help.ai.AiChatSession
import io.legado.app.help.ai.AiHistoryStore
import io.legado.app.help.ai.AiService
import io.legado.app.help.ai.AiToolContext
import io.legado.app.help.ai.AiTools
import io.legado.app.help.ai.ChatMessageItem
import io.legado.app.help.ai.ChatResult
import io.legado.app.help.ai.PromptManager
import io.legado.app.help.ai.ReadingContext
import io.legado.app.help.ai.ReadingContextService
import io.legado.app.help.ai.SkillManager
import io.legado.app.help.ai.ToolStep
import io.legado.app.help.ai.ToolStepStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class AiChatViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private lateinit var aiService: AiService
    private lateinit var promptManager: PromptManager
    private lateinit var skillManager: SkillManager

    private var currentJob: Job? = null
    private var bookInfo: BookInfoUi? = null
    private var streamingIndex: Int = -1

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiChatEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiChatEffect> = _effects.asSharedFlow()

    fun init(
        bookUrl: String?,
        bookTitle: String?,
        author: String?,
        chapterTitle: String?,
        chapterContent: String?,
        selectedText: String?
    ) {
        bookInfo = BookInfoUi(
            bookUrl = bookUrl,
            bookTitle = bookTitle,
            author = author,
            chapterTitle = chapterTitle,
            chapterContent = chapterContent
        )

        aiService = AiService.getInstance(context)
        promptManager = PromptManager(context)
        skillManager = SkillManager(context)

        viewModelScope.launch {
            AiHistoryStore.init(context)
            aiService.init()

            if (bookUrl != null) {
                val book = appDb.bookDao.getBook(bookUrl)
                val chapter = book?.let {
                    appDb.bookChapterDao.getChapter(it.bookUrl, it.durChapterIndex)
                }
                aiService.setToolContext(book, chapter, chapterContent)
            } else {
                val emptyContext = AiToolContext(
                    currentBook = null,
                    currentChapter = null,
                    chapterContent = null,
                    bookUrl = "",
                    appDatabase = appDb,
                    appContext = context
                )
                AiTools.registerAll(emptyContext)
            }

            loadQuickActions()
            loadSuggestions()
            createNewSession()

            if (!selectedText.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(selectedQuote = selectedText)
            }
        }
    }

    fun onIntent(intent: AiChatIntent) {
        when (intent) {
            is AiChatIntent.SendMessage -> sendMessage(intent.content)
            AiChatIntent.StopGenerating -> stopGenerating()
            is AiChatIntent.SetQuote -> setQuote(intent.text)
            is AiChatIntent.ToggleDeepThinking -> toggleDeepThinking(intent.enabled)
            is AiChatIntent.ToggleSpoilerFree -> toggleSpoilerFree(intent.enabled)
            is AiChatIntent.ExecuteQuickAction -> executeQuickAction(intent.item)
            is AiChatIntent.ExecuteSuggestion -> executeSuggestion(intent.item)
            AiChatIntent.NewConversation -> createNewSession()
            is AiChatIntent.SelectConversation -> selectConversation(intent.id)
            is AiChatIntent.DeleteConversation -> deleteConversation(intent.id)
            AiChatIntent.RegenerateLastMessage -> regenerateLastMessage()
            is AiChatIntent.CopyMessage -> copyToClipboard(intent.content)
            is AiChatIntent.ShareMessage -> shareMessage(intent.content)
            is AiChatIntent.DeleteMessage -> deleteMessage(intent.message)
            AiChatIntent.ClearChat -> clearChat()
            AiChatIntent.ExportChat -> exportChat()
            AiChatIntent.OpenSettings -> openSettings()
        }
    }

    private fun sendMessage(rawContent: String) {
        val content = rawContent.trim()
        if (content.isBlank() || _uiState.value.isSending) return

        val quote = _uiState.value.selectedQuote
        val fullContent = buildString {
            append(content)
            if (!quote.isNullOrBlank()) {
                append("：")
                append(quote)
            }
        }

        currentJob?.cancel()

        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(ChatMessageItem("user", fullContent))
        val aiIndex = currentMessages.size
        currentMessages.add(ChatMessageItem("ai", "", toolSteps = emptyList()))
        streamingIndex = aiIndex

        _uiState.value = _uiState.value.copy(
            messages = currentMessages.toPersistentList(),
            isSending = true
        )

        currentJob = viewModelScope.launch {
            try {
                val session = _uiState.value.currentSession ?: run {
                    createNewSessionInternal()
                    _uiState.value.currentSession
                }

                aiService.chat(content, session).collectLatest { result ->
                    handleChatResult(result)
                }

                saveCurrentSession()
            } catch (e: Exception) {
                _effects.tryEmit(AiChatEffect.ShowToast("发送失败: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
                streamingIndex = -1
            }
        }
    }

    private fun handleChatResult(result: ChatResult) {
        val currentMessages = _uiState.value.messages.toMutableList()
        if (streamingIndex < 0 || streamingIndex >= currentMessages.size) return

        val current = currentMessages[streamingIndex]

        when (result) {
            is ChatResult.Chunk -> {
                val newContent = if (current.content.isEmpty()) {
                    result.content
                } else {
                    current.content + result.content
                }
                currentMessages[streamingIndex] = current.copy(content = newContent)
            }
            is ChatResult.ReasoningChunk -> {
                val newReasoning = (current.reasoningContent ?: "") + result.content
                currentMessages[streamingIndex] = current.copy(reasoningContent = newReasoning)
            }
            is ChatResult.ToolCall -> {
                val formattedArgs = try {
                    JSONObject(result.arguments).toString(2)
                } catch (e: Exception) {
                    result.arguments
                }
                val updatedSteps = current.toolSteps.toMutableList()
                updatedSteps.add(
                    ToolStep(
                        name = result.name,
                        status = ToolStepStatus.PENDING,
                        input = formattedArgs
                    )
                )
                currentMessages[streamingIndex] = current.copy(toolSteps = updatedSteps)
            }
            is ChatResult.ToolStart -> {
                val updatedSteps = current.toolSteps.toMutableList()
                val idx = updatedSteps.indexOfFirst { it.name == result.name }
                if (idx >= 0) {
                    updatedSteps[idx] = updatedSteps[idx].copy(status = ToolStepStatus.RUNNING)
                    currentMessages[streamingIndex] = current.copy(toolSteps = updatedSteps)
                }
            }
            is ChatResult.ToolResult -> {
                val updatedSteps = current.toolSteps.toMutableList()
                val idx = updatedSteps.indexOfFirst { it.name == result.name }
                if (idx >= 0) {
                    updatedSteps[idx] = updatedSteps[idx].copy(
                        status = ToolStepStatus.SUCCESS,
                        output = result.result
                    )
                    currentMessages[streamingIndex] = current.copy(toolSteps = updatedSteps)
                }
            }
            is ChatResult.ToolStepUpdate -> {
                val updatedSteps = current.toolSteps.toMutableList()
                val idx = updatedSteps.indexOfFirst { it.name == result.step.name }
                if (idx >= 0) {
                    updatedSteps[idx] = result.step
                } else {
                    updatedSteps.add(result.step)
                }
                currentMessages[streamingIndex] = current.copy(toolSteps = updatedSteps)
            }
            is ChatResult.Success -> {
                if (current.content.isEmpty() && result.content.isNotEmpty()) {
                    currentMessages[streamingIndex] = current.copy(content = result.content)
                }
            }
            is ChatResult.Error -> {
                currentMessages[streamingIndex] = current.copy(
                    content = current.content.ifEmpty { "错误: ${result.message}" }
                )
                _effects.tryEmit(AiChatEffect.ShowToast(result.message))
            }
            else -> Unit
        }

        _uiState.value = _uiState.value.copy(messages = currentMessages.toPersistentList())
    }

    private fun stopGenerating() {
        currentJob?.cancel()
        aiService.cancelCurrentRequest()

        val currentMessages = _uiState.value.messages.toMutableList()
        if (streamingIndex in currentMessages.indices) {
            val current = currentMessages[streamingIndex]
            if (current.content.isEmpty()) {
                currentMessages[streamingIndex] = current.copy(content = "[请求已取消]")
            }
        }
        _uiState.value = _uiState.value.copy(
            messages = currentMessages.toPersistentList(),
            isSending = false
        )
        streamingIndex = -1
    }

    private fun setQuote(text: String?) {
        _uiState.value = _uiState.value.copy(selectedQuote = text)
    }

    private fun toggleDeepThinking(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(deepThinkingEnabled = enabled)
    }

    private fun toggleSpoilerFree(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(spoilerFreeEnabled = enabled)
    }

    private fun loadQuickActions() {
        viewModelScope.launch {
            val config = AiAssistantConfigManager.getQuickActionBarConfig(context, false)
            val allSkills = skillManager.getAllSkills()
            val items = config.mapNotNull { item ->
                when (item.type) {
                    AiAssistantConfigManager.ConfigType.SKILL -> {
                        val skill = allSkills.find { it.id == item.skillId }
                        skill?.let {
                            QuickActionItemUi(
                                displayName = it.name,
                                triggerWord = it.triggerWord,
                                type = 1,
                                skillId = it.id
                            )
                        }
                    }
                    AiAssistantConfigManager.ConfigType.CUSTOM -> {
                        QuickActionItemUi(
                            displayName = item.displayName ?: item.triggerWord ?: "",
                            triggerWord = item.triggerWord ?: ""
                        )
                    }
                    else -> null
                }
            }.take(4)
            _uiState.value = _uiState.value.copy(quickActions = items.toPersistentList())
        }
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            val config = AiAssistantConfigManager.getEmptyStateConfig(context)
            val allSkills = skillManager.getAllSkills()
            val items = config.mapNotNull { item ->
                when (item.type) {
                    AiAssistantConfigManager.ConfigType.SKILL -> {
                        val skill = allSkills.find { it.id == item.skillId }
                        skill?.let {
                            SuggestionItemUi(
                                displayText = it.triggerWord.ifBlank { it.name },
                                triggerWord = it.triggerWord,
                                type = 1,
                                skillId = it.id
                            )
                        }
                    }
                    AiAssistantConfigManager.ConfigType.CUSTOM -> {
                        SuggestionItemUi(
                            displayText = item.customTrigger ?: item.customName ?: "",
                            triggerWord = item.customTrigger ?: ""
                        )
                    }
                    else -> null
                }
            }.take(4)
            _uiState.value = _uiState.value.copy(suggestions = items.toPersistentList())
        }
    }

    private fun executeQuickAction(item: QuickActionItemUi) {
        if (item.type == 1 && item.skillId != null) {
            executeSkillDirectly(item.skillId, item.triggerWord)
        } else {
            _effects.tryEmit(AiChatEffect.ShowToast("填入触发词: ${item.triggerWord}"))
        }
    }

    private fun executeSuggestion(item: SuggestionItemUi) {
        if (item.type == 1 && item.skillId != null) {
            executeSkillDirectly(item.skillId, item.displayText)
        } else {
            sendMessage(item.triggerWord)
        }
    }

    private fun executeSkillDirectly(skillId: String, userQuestion: String) {
        currentJob?.cancel()

        val currentMessages = _uiState.value.messages.toMutableList()
        val quote = _uiState.value.selectedQuote
        val fullMsg = buildString {
            append(userQuestion)
            if (!quote.isNullOrBlank()) {
                append("：")
                append(quote)
            }
        }
        currentMessages.add(ChatMessageItem("user", fullMsg))
        val aiIndex = currentMessages.size
        currentMessages.add(ChatMessageItem("ai", ""))
        streamingIndex = aiIndex
        _uiState.value = _uiState.value.copy(
            messages = currentMessages.toPersistentList(),
            isSending = true
        )

        currentJob = viewModelScope.launch {
            try {
                val skill = skillManager.getSkill(skillId) ?: return@launch
                val readingContext = ReadingContextService.getContext()
                val realBookTitle = readingContext?.bookTitle?.takeIf { it.isNotBlank() }
                    ?: bookInfo?.bookTitle ?: ""
                val realAuthor = readingContext?.author?.takeIf { it.isNotBlank() }
                    ?: bookInfo?.author ?: ""
                val realChapterTitle = readingContext?.currentChapter?.title?.takeIf { it.isNotBlank() }
                    ?: bookInfo?.chapterTitle ?: ""
                val realChapterContent = readingContext?.surroundingText?.takeIf { it.isNotBlank() }
                    ?: bookInfo?.chapterContent ?: ""

                val bookIntro = try {
                    val url = readingContext?.bookId?.takeIf { it.isNotBlank() }
                    if (!url.isNullOrBlank()) {
                        appDb.bookDao.getBook(url)?.intro ?: ""
                    } else ""
                } catch (e: Exception) {
                    ""
                }

                val previousContent = try {
                    val url = readingContext?.bookId?.takeIf { it.isNotBlank() }
                    if (!url.isNullOrBlank()) {
                        val curIdx = readingContext.currentChapter?.index ?: 0
                        if (curIdx > 0) {
                            val prev = appDb.bookChapterDao.getChapter(url, curIdx - 1)
                            val book = appDb.bookDao.getBook(url)
                            if (prev != null && book != null) {
                                io.legado.app.help.book.BookHelp.getContent(book, prev)?.take(3000) ?: ""
                            } else ""
                        } else ""
                    } else ""
                } catch (e: Exception) {
                    ""
                }

                val maxLen = 8000
                val chapterContentTruncated = if (realChapterContent.length > maxLen) {
                    realChapterContent.take(maxLen) + "\n...[内容已截断]"
                } else realChapterContent

                val variables = mutableMapOf(
                    "bookName" to realBookTitle,
                    "bookAuthor" to realAuthor,
                    "bookIntro" to bookIntro,
                    "chapterTitle" to realChapterTitle,
                    "selectedText" to (quote ?: ""),
                    "question" to userQuestion,
                    "concept" to (quote ?: ""),
                    "currentChapter" to realChapterTitle,
                    "previousContent" to previousContent,
                    "chapterContent" to chapterContentTruncated,
                    "content" to chapterContentTruncated,
                    "contextText" to (quote ?: realChapterContent.take(2000)),
                    "surroundingText" to realChapterContent
                )

                aiService.executeSkill(skill, variables).collectLatest { result ->
                    handleChatResult(result)
                }
                saveCurrentSession()
            } catch (e: Exception) {
                _effects.tryEmit(AiChatEffect.ShowToast("技能执行失败: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
                streamingIndex = -1
            }
        }
    }

    private fun createNewSessionInternal(): AiChatSession {
        val session = AiChatSession(
            id = java.util.UUID.randomUUID().toString(),
            serviceId = "default",
            model = "default",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = mutableListOf()
        )
        _uiState.value = _uiState.value.copy(
            currentSession = session,
            currentConversationId = session.id
        )
        return session
    }

    private fun createNewSession() {
        val session = createNewSessionInternal()
        _uiState.value = _uiState.value.copy(
            messages = emptyList<ChatMessageItem>().toPersistentList(),
            isSending = false
        )
        streamingIndex = -1
        refreshConversations()
        _effects.tryEmit(AiChatEffect.ShowToast("已新建对话"))
    }

    private fun selectConversation(id: String) {
        viewModelScope.launch {
            val sessions = AiHistoryStore.readHistory()
            val session = sessions.find { it.id == id }
            if (session != null) {
                val messages = session.messages.map { msg ->
                    ChatMessageItem(
                        role = if (msg.type == "human") "user" else "ai",
                        content = msg.content,
                        reasoningContent = "",
                        toolSteps = msg.toolSteps,
                        isExpanded = true,
                        isReasoningExpanded = false
                    )
                }
                _uiState.value = _uiState.value.copy(
                    currentSession = session,
                    currentConversationId = session.id,
                    messages = messages.toPersistentList(),
                    isSending = false
                )
                streamingIndex = -1
                _effects.tryEmit(AiChatEffect.ShowToast("已加载历史对话"))
            }
        }
    }

    private fun deleteConversation(id: String) {
        viewModelScope.launch {
            AiHistoryStore.removeSession(id)
            refreshConversations()
            if (_uiState.value.currentConversationId == id) {
                createNewSession()
            }
        }
    }

    private fun refreshConversations() {
        viewModelScope.launch {
            val sessions = AiHistoryStore.readHistory()
            val currentId = _uiState.value.currentConversationId
            val conversations = sessions.map { session ->
                val firstUserMsg = session.messages.firstOrNull { it.type == "human" }?.content ?: "空对话"
                val title = if (firstUserMsg.length > 30) {
                    firstUserMsg.take(30) + "..."
                } else firstUserMsg
                AiChatConversationUi(
                    id = session.id,
                    title = title,
                    updatedAt = session.updatedAt,
                    isSelected = session.id == currentId
                )
            }
            _uiState.value = _uiState.value.copy(conversations = conversations.toPersistentList())
        }
    }

    private fun saveCurrentSession() {
        viewModelScope.launch {
            val session = _uiState.value.currentSession ?: return@launch
            val msgs = _uiState.value.messages.map { item ->
                io.legado.app.help.ai.ChatMessage(
                    type = if (item.role == "user") "human" else "ai",
                    content = item.content,
                    toolSteps = item.toolSteps
                )
            }
            val updatedSession = session.copy(
                title = session.title.ifBlank {
                    msgs.firstOrNull { it.type == "human" }?.content?.take(30) ?: "新对话"
                },
                messages = msgs.toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            AiHistoryStore.upsertSession(updatedSession)
            _uiState.value = _uiState.value.copy(currentSession = updatedSession)
            refreshConversations()
        }
    }

    private fun regenerateLastMessage() {
        val messages = _uiState.value.messages
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserIndex >= 0) {
            val newMessages = messages.take(lastUserIndex).toMutableList()
            _uiState.value = _uiState.value.copy(messages = newMessages.toPersistentList())
            val lastUserMsg = messages[lastUserIndex]
            sendMessage(lastUserMsg.content)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI回复", text)
        clipboard.setPrimaryClip(clip)
        _effects.tryEmit(AiChatEffect.ShowToast("已复制"))
    }

    private fun shareMessage(text: String) {
        _effects.tryEmit(AiChatEffect.ShareText(text))
    }

    private fun deleteMessage(message: ChatMessageItem) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.remove(message)
        _uiState.value = _uiState.value.copy(messages = currentMessages.toPersistentList())
    }

    private fun clearChat() {
        _uiState.value = _uiState.value.copy(messages = emptyList<ChatMessageItem>().toPersistentList())
        _effects.tryEmit(AiChatEffect.ShowToast("已清空当前对话"))
    }

    private fun exportChat() {
        _effects.tryEmit(AiChatEffect.ShowToast("导出功能开发中..."))
    }

    private fun openSettings() {
        _effects.tryEmit(AiChatEffect.NavigateSettings)
    }

    fun onDestroy() {
        currentJob?.cancel()
        if (_uiState.value.isSending) {
            aiService.cancelCurrentRequest()
        }
    }
}
