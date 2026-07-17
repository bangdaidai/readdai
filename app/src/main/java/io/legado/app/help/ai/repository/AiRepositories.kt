package io.legado.app.help.ai.repository

import io.legado.app.help.ai.AiApiClient
import io.legado.app.help.ai.AiChatSession
import io.legado.app.help.ai.AiHistoryStore
import io.legado.app.help.ai.AiProviderEntity
import io.legado.app.help.ai.ChatMessage
import io.legado.app.help.ai.ChatTool
import io.legado.app.help.ai.StreamChunk
import io.legado.app.help.ai.StreamResponseResult
import io.legado.app.help.ai.gateway.AiChatGateway
import io.legado.app.help.ai.gateway.AiHistoryGateway
import io.legado.app.help.ai.gateway.AiProviderGateway
import splitties.init.appCtx

class AiChatRepository : AiChatGateway {

    private fun client(provider: AiProviderEntity) = AiApiClient(provider)

    override suspend fun generate(
        provider: AiProviderEntity,
        messages: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ): Result<String> {
        return client(provider).chat(messages, onChunk)
    }

    override suspend fun generateNoStream(
        provider: AiProviderEntity,
        messages: List<ChatMessage>
    ): Result<String> {
        return client(provider).chatNoStream(messages)
    }

    override suspend fun generateWithTools(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>,
        onChunk: suspend (StreamChunk) -> Unit
    ): Result<StreamResponseResult> {
        return client(provider).chatWithTools(messages, tools, onChunk)
    }
}

class AiProviderRepository : AiProviderGateway {

    private val aiDao by lazy {
        io.legado.app.help.ai.AiDatabase.getInstance(appCtx).aiDao()
    }

    override suspend fun getAll(): List<AiProviderEntity> {
        return aiDao.getAllProviders()
    }

    override suspend fun getById(id: String): AiProviderEntity? {
        return aiDao.getProvider(id)
    }

    override suspend fun getDefault(): AiProviderEntity? {
        return aiDao.getDefaultProvider()
    }

    override suspend fun save(provider: AiProviderEntity) {
        aiDao.insertProvider(provider)
    }

    override suspend fun delete(id: String) {
        aiDao.deleteProvider(id)
    }
}

class AiHistoryRepository : AiHistoryGateway {

    override suspend fun getSessions(): List<AiChatSession> {
        return AiHistoryStore.readHistory()
    }

    override suspend fun saveSession(session: AiChatSession) {
        AiHistoryStore.upsertSession(session)
    }

    override suspend fun deleteSession(id: String) {
        AiHistoryStore.removeSession(id)
    }

    override suspend fun clear() {
        AiHistoryStore.clear()
    }
}

object AiRepositories {
    val chat by lazy { AiChatRepository() }
    val provider by lazy { AiProviderRepository() }
    val history by lazy { AiHistoryRepository() }
}
