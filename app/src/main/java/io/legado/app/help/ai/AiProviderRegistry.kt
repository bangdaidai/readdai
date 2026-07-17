package io.legado.app.help.ai

internal object AiProviderRegistry {

    private val handlers: Map<String, AiProtocolHandler> by lazy {
        val allHandlers = listOf(
            OpenAiChatHandler(),
            AnthropicHandler(),
            GeminiHandler()
        )
        allHandlers.flatMap { handler ->
            handler.protocols.map { it to handler }
        }.toMap()
    }

    fun handlerFor(protocol: String): AiProtocolHandler {
        return handlers[protocol]
            ?: handlers["openai"]!!
    }
}
