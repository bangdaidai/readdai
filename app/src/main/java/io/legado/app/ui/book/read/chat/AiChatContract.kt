package io.legado.app.ui.book.read.chat

import androidx.compose.runtime.Stable
import io.legado.app.help.ai.AiChatSession
import io.legado.app.help.ai.ChatMessageItem
import io.legado.app.help.ai.ToolStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiChatUiState(
    val messages: ImmutableList<ChatMessageItem> = persistentListOf(),
    val streamingMessage: ChatMessageItem? = null,
    val isSending: Boolean = false,
    val currentSession: AiChatSession? = null,
    val conversations: ImmutableList<AiChatConversationUi> = persistentListOf(),
    val currentConversationId: String? = null,
    val deepThinkingEnabled: Boolean = false,
    val spoilerFreeEnabled: Boolean = false,
    val selectedQuote: String? = null,
    val quickActions: ImmutableList<QuickActionItemUi> = persistentListOf(),
    val suggestions: ImmutableList<SuggestionItemUi> = persistentListOf(),
    val bookInfo: BookInfoUi? = null,
    val errorMessage: String? = null
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

sealed interface AiChatIntent {
    data class SendMessage(val content: String) : AiChatIntent
    data object StopGenerating : AiChatIntent
    data class SetQuote(val text: String?) : AiChatIntent
    data class ToggleDeepThinking(val enabled: Boolean) : AiChatIntent
    data class ToggleSpoilerFree(val enabled: Boolean) : AiChatIntent
    data class ExecuteQuickAction(val item: QuickActionItemUi) : AiChatIntent
    data class ExecuteSuggestion(val item: SuggestionItemUi) : AiChatIntent
    data class NewConversation : AiChatIntent
    data class SelectConversation(val id: String) : AiChatIntent
    data class DeleteConversation(val id: String) : AiChatIntent
    data class RegenerateLastMessage : AiChatIntent
    data class CopyMessage(val content: String) : AiChatIntent
    data class ShareMessage(val content: String) : AiChatIntent
    data class DeleteMessage(val message: ChatMessageItem) : AiChatIntent
    data object ClearChat : AiChatIntent
    data object ExportChat : AiChatIntent
    data object OpenSettings : AiChatIntent
}

sealed interface AiChatEffect {
    data class ShowToast(val message: String) : AiChatEffect
    data class ShareText(val text: String) : AiChatEffect
    data object NavigateSettings : AiChatEffect
    data object Finish : AiChatEffect
}
