package io.legado.app.help.ai

data class AiProviderPreset(
    val identifier: String,
    val title: String,
    val apiUrl: String,
    val protocol: String,
    val defaultModel: String,
    val description: String = ""
)

object AiProviderPresets {

    val ALL = listOf(
        DEEPSEEK,
        SILICONFLOW,
        ALIYUN_QWEN,
        XIAOMI_MIMO,
        MOONSHOT,
        ZHIPU,
        ANTHROPIC,
        GEMINI,
        OLLAMA,
        LM_STUDIO
    )

    fun find(identifier: String): AiProviderPreset? {
        return ALL.find { it.identifier == identifier }
    }
}

private val DEEPSEEK = AiProviderPreset(
    identifier = "deepseek",
    title = "DeepSeek",
    apiUrl = "https://api.deepseek.com",
    protocol = "openai",
    defaultModel = "deepseek-chat",
    description = "深度求索，国内优质大模型"
)

private val SILICONFLOW = AiProviderPreset(
    identifier = "siliconflow",
    title = "SiliconFlow",
    apiUrl = "https://api.siliconflow.cn",
    protocol = "openai",
    defaultModel = "Qwen/Qwen2.5-7B-Instruct",
    description = "硅基流动，开源模型聚合平台"
)

private val ALIYUN_QWEN = AiProviderPreset(
    identifier = "qwen",
    title = "阿里云百炼",
    apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    protocol = "openai",
    defaultModel = "qwen-plus",
    description = "阿里云通义千问"
)

private val XIAOMI_MIMO = AiProviderPreset(
    identifier = "mimo",
    title = "小米 MiMo",
    apiUrl = "https://api.minimax.chat/v1",
    protocol = "openai",
    defaultModel = "mimo-v1-5-pro",
    description = "小米 MiMo 大模型"
)

private val MOONSHOT = AiProviderPreset(
    identifier = "moonshot",
    title = "月之暗面",
    apiUrl = "https://api.moonshot.cn/v1",
    protocol = "openai",
    defaultModel = "moonshot-v1-8k",
    description = "Moonshot AI，Kimi 背后的模型"
)

private val ZHIPU = AiProviderPreset(
    identifier = "zhipu",
    title = "智谱 AI",
    apiUrl = "https://open.bigmodel.cn/api/paas/v4",
    protocol = "openai",
    defaultModel = "glm-4-flash",
    description = "智谱 AI，GLM 系列模型"
)

private val ANTHROPIC = AiProviderPreset(
    identifier = "anthropic",
    title = "Anthropic",
    apiUrl = "https://api.anthropic.com",
    protocol = "claude",
    defaultModel = "claude-3-5-sonnet-latest",
    description = "Claude 系列，擅长长上下文"
)

private val GEMINI = AiProviderPreset(
    identifier = "gemini",
    title = "Google Gemini",
    apiUrl = "https://generativelanguage.googleapis.com",
    protocol = "gemini",
    defaultModel = "gemini-2.0-flash",
    description = "Google Gemini 系列"
)

private val OLLAMA = AiProviderPreset(
    identifier = "ollama",
    title = "Ollama",
    apiUrl = "http://localhost:11434",
    protocol = "openai",
    defaultModel = "llama3.2",
    description = "本地运行大模型，需先启动 Ollama 服务"
)

private val LM_STUDIO = AiProviderPreset(
    identifier = "lmstudio",
    title = "LM Studio",
    apiUrl = "http://localhost:1234/v1",
    protocol = "openai",
    defaultModel = "",
    description = "本地运行大模型，需先启动 LM Studio 服务"
)
