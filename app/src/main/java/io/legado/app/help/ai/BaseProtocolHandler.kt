package io.legado.app.help.ai

import io.legado.app.help.ai.AiEntities.ChatMessage
import io.legado.app.help.ai.AiEntities.ChatTool
import io.legado.app.help.ai.AiEntities.StreamChunk
import io.legado.app.help.ai.AiEntities.StreamResponseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

abstract class BaseProtocolHandler : AiProtocolHandler {

    protected val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    protected suspend fun <T> executeWithRetry(
        provider: AiProviderEntity,
        block: suspend (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val keyRotator = KeyRotator(provider.getCurrentApiKey() ?: "")
            Result.success(retryWithBackoff(
                maxAttempts = 3,
                keyRotator = null,
                onRetry = { attempt, _, error ->
                    AiLogManager.log(
                        AiLogManager.LogLevel.WARNING,
                        "Handler",
                        "请求失败，第 ${attempt} 次重试: ${error.message}"
                    )
                    if (provider.getApiKeyList().size > 1) {
                        provider.advanceKeyIndex()
                    }
                }
            ) {
                block(provider.getCurrentApiKey() ?: "")
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected fun newRequest(
        url: String,
        body: String,
        headers: Map<String, String>
    ): Request {
        val builder = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        return builder.build()
    }

    protected fun buildChatUrl(provider: AiProviderEntity): String {
        val normalizedUrl = provider.apiUrl.trim().trimEnd('/')
        return when (provider.protocol) {
            "claude" -> "$normalizedUrl/v1/messages"
            "gemini" -> {
                val apiKey = provider.getCurrentApiKey() ?: ""
                "$normalizedUrl/v1beta/models/${provider.model}:streamGenerateContent?alt=sse&key=$apiKey"
            }
            else -> "$normalizedUrl/v1/chat/completions"
        }
    }

    protected fun openAiMessages(messages: List<ChatMessage>): JSONArray {
        val msgs = JSONArray()
        messages.forEach { msg ->
            msgs.put(JSONObject().apply {
                put("role", when (msg.type) {
                    "system" -> "system"
                    "ai" -> "assistant"
                    else -> "user"
                })
                put("content", msg.content)
            })
        }
        return msgs
    }

    protected suspend fun parseSseStream(
        inputStream: InputStream,
        onChunk: suspend (String) -> Unit
    ): String {
        val content = StringBuilder()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (currentLine.startsWith("data:")) {
                val data = currentLine.removePrefix("data:").trim()
                if (data == "[DONE]" || data.isEmpty()) continue

                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        if (delta != null) {
                            val contentText = delta.optString("content", "")
                            if (contentText.isNotEmpty()) {
                                content.append(contentText)
                                onChunk(contentText)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        reader.close()
        return content.toString()
    }

    protected fun Response.ensureSuccessful(): Response {
        if (!isSuccessful) {
            throw java.io.IOException("HTTP $code: ${message}")
        }
        return this
    }
}
