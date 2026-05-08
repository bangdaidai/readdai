package io.legado.app.help.ai.langchain4j

import dev.langchain4j.agent.tool.Tool
import io.legado.app.help.ai.AiToolContext
import io.legado.app.help.ai.AiToolRegistry

/**
 * LangChain4j通用Tool执行器
 * 通过一个带@Tool注解的方法，动态调用dai的所有Tool
 */
class LangChain4jUniversalToolExecutor(
    private val context: AiToolContext
) {
    
    /**
     * 通用Tool执行方法
     * LangChain4j会识别这个@Tool注解，并将其暴露给AI模型
     * 
     * @param toolName Tool的名称（如"list_books"、"reading_history"等）
     * @param arguments Tool的参数（JSON格式）
     * @return Tool执行结果的JSON字符串
     */
    @Tool("Execute a tool by name with the given arguments. Available tools: list_books (get user's bookshelf), reading_history (get reading history). Use this to fetch real data when user asks about their books or reading progress.")
    fun executeToolByName(
        toolName: String,
        arguments: String
    ): String {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "ToolExecutor",
            "执行Tool: name=$toolName, args=$arguments"
        )
        
        return try {
            // 查找对应的Tool定义
            val toolDefinition = AiToolRegistry.getDefinitions().find { it.id == toolName }
                ?: return """{"status": "error", "message": "Tool not found: $toolName"}"""
            
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "ToolExecutor",
                "找到Tool定义: ${toolDefinition.displayNameBuilder()}"
            )
            
            // 构建Tool实例（使用AiToolRegistry的buildTools方法）
            val tools = AiToolRegistry.buildTools(setOf(toolName))
            val tool = tools.firstOrNull()
                ?: return """{"status": "error", "message": "Failed to create tool: $toolName"}"""
            
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "ToolExecutor",
                "Tool实例创建成功"
            )
            
            // 解析参数
            val argsMap = parseJsonArguments(arguments)
            
            // 执行Tool（使用runBlocking调用suspend函数）
            val result = kotlinx.coroutines.runBlocking {
                tool.execute(argsMap)
            }
            
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "ToolExecutor",
                "Tool执行完成: status=${result.status}"
            )
            
            // 返回结果
            when (result.status) {
                "ok" -> {
                    val dataJson = if (result.data is String) {
                        result.data
                    } else {
                        toJsonString(result.data)
                    }
                    """{"status": "success", "data": $dataJson}"""
                }
                else -> {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                        "ToolExecutor",
                        "Tool执行失败: ${result.message}"
                    )
                    """{"status": "error", "message": "${result.message ?: "Unknown error"}"}"""
                }
            }
        } catch (e: Exception) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                "ToolExecutor",
                "Tool执行异常: ${e.message}",
                e
            )
            """{"status": "error", "message": "${e.message ?: "Tool execution failed"}"}"""
        }
    }
    
    /**
     * 解析JSON参数字符串为Map
     */
    private fun parseJsonArguments(jsonString: String): Map<String, Any> {
        return try {
            val jsonObject = org.json.JSONObject(jsonString)
            val map = mutableMapOf<String, Any>()
            
            jsonObject.keys().forEach { key ->
                val value = jsonObject.get(key)
                map[key] = when (value) {
                    is org.json.JSONObject -> value.toString()
                    is org.json.JSONArray -> value.toString()
                    else -> value
                }
            }
            
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 将对象转换为JSON字符串
     */
    private fun toJsonString(data: Any?): String {
        return try {
            if (data is String) {
                "\"${data.replace("\"", "\\\"")}\""
            } else {
                org.json.JSONObject.wrap(data)?.toString() ?: "null"
            }
        } catch (e: Exception) {
            "\"$data\""
        }
    }
}
