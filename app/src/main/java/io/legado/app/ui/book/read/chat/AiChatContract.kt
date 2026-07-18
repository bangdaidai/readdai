package io.legado.app.ui.book.read.chat

import androidx.compose.runtime.Stable
import io.legado.app.help.ai.AiChatSession
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.help.ai.ChatMessageItem
import io.legado.app.help.ai.ToolStep

@Stable
data class AiPendingToolConfirmation(
    val toolName: String,
    val toolInput: String,
    val messageId: String
)

@Stable
data class AiChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val streamingMessage: ChatMessageItem? = null,
    val isSending: Boolean = false,
    val currentSession: AiChatSession? = null,
    val conversations: List<AiChatConversationUi> = emptyList(),
    val currentConversationId: String? = null,
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.default,
    val deepThinkingEnabled: Boolean = false,
    val spoilerFreeEnabled: Boolean = false,
    val pendingToolConfirmation: AiPendingToolConfirmation? = null,
    val conversationTitle: String = "",
    val providerName: String = "",
    val modelName: String = "",
    val initiallyPositionedConversationId: String? = null,
    val selectedQuote: String? = null,
    val quickActions: List<QuickActionItemUi> = emptyList(),
    val suggestions: List<SuggestionItemUi> = emptyList(),
    val bookInfo: BookInfoUi? = null,
    val errorMessage: String? = null,
    val availableProviders: List<ProviderModelUi> = emptyList()
)

@Stable
data class AiChatConversationUi(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isSelected: Boolean = false
)

@Stable
data class QuickActionItemUi(
    val displayName: String,
    val triggerWord: String,
    val type: Int = 0,
    val skillId: String? = null
)

@Stable
data class SuggestionItemUi(
    val displayText: String,
    val triggerWord: String,
    val type: Int = 0,
    val skillId: String? = null
)

@Stable
data class BookInfoUi(
    val bookUrl: String? = null,
    val bookTitle: String? = null,
    val author: String? = null,
    val chapterTitle: String? = null,
    val chapterContent: String? = null
)

@Stable
data class ProviderModelUi(
    val identifier: String,
    val title: String,
    val model: String,
    val availableModels: List<String> = emptyList()
)

sealed interface AiChatIntent {
    data class SendMessage(val content: String) : AiChatIntent
    object StopGenerating : AiChatIntent
    data class SetQuote(val text: String?) : AiChatIntent
    data class ToggleDeepThinking(val enabled: Boolean) : AiChatIntent
    data class ToggleSpoilerFree(val enabled: Boolean) : AiChatIntent
    data class ExecuteQuickAction(val item: QuickActionItemUi) : AiChatIntent
    data class ExecuteSuggestion(val item: SuggestionItemUi) : AiChatIntent
    object NewConversation : AiChatIntent
    data class SelectConversation(val id: String) : AiChatIntent
    data class DeleteConversation(val id: String) : AiChatIntent
    data class SwitchBranch(val messageId: String, val direction: Int) : AiChatIntent
    data class RegenerateMessage(val messageId: String) : AiChatIntent
    object RegenerateLastMessage : AiChatIntent
    data class CopyMessage(val content: String) : AiChatIntent
    data class ShareMessage(val content: String) : AiChatIntent
    data class DeleteMessage(val message: ChatMessageItem) : AiChatIntent
    object ClearChat : AiChatIntent
    object ExportChat : AiChatIntent
    object OpenSettings : AiChatIntent
    data class SelectProvider(val identifier: String, val model: String? = null) : AiChatIntent
}

sealed interface AiChatEffect {
    data class ShowToast(val message: String) : AiChatEffect
    data class ShowSnackbar(val message: String) : AiChatEffect
    data class ShareText(val text: String) : AiChatEffect
    object NavigateSettings : AiChatEffect
    object Finish : AiChatEffect
}
