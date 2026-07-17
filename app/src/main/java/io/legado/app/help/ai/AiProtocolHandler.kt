package io.legado.app.help.ai

import io.legado.app.help.ai.AiEntities.ChatMessage
import io.legado.app.help.ai.AiEntities.ChatTool
import io.legado.app.help.ai.AiEntities.StreamChunk
import io.legado.app.help.ai.AiEntities.StreamResponseResult

interface AiProtocolHandler {

    val protocols: Set<String>

    suspend fun chat(
        provider: AiProviderEntity,
        messages: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ): Result<String>

    suspend fun chatNoStream(
        provider: AiProviderEntity,
        messages: List<ChatMessage>
    ): Result<String>

    suspend fun chatWithTools(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>,
        onChunk: suspend (StreamChunk) -> Unit
    ): Result<StreamResponseResult>
}
