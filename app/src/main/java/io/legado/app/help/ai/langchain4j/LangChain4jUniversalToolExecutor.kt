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
    @Tool("""
Execute a tool by name with the given arguments.

Available tools and their parameters:
- list_books: Get user's bookshelf. Parameters: {"keyword": "search keyword", "status": "reading|completed|paused", "maxItems": 20}
- reading_history: Get reading history. Parameters: {"bookTitle": "optional filter", "maxItems": 20}
- search_web_tavily: Search the web for real-time information. Parameters: {"query": "search query string", "topic": "general|news|finance", "maxResults": 5}
- rag_search: Semantic search in vectorized books. REQUIRED parameter: {"query": "search query string"}. Optional: {"topK": 5, "mode": "hybrid|vector|bm25"}
- rag_toc: Get table of contents of vectorized book. No parameters needed.
- rag_context: Get chapter context. Parameters: {"chapterIndex": number, "range": 2}
- get_current_book_info: Get current book info. No parameters needed.
- current_chapter: Get current chapter content. No parameters needed.
- book_toc: Get book table of contents. No parameters needed.
- search_content: Search in current book. Parameters: {"keyword": "search keyword"}
- extract_entities: Extract entities from text. No parameters needed.
- analyze_arguments: Analyze arguments. No parameters needed.
- find_quotes: Find quotes. Parameters: {"quoteType": "inspiring|romantic|philosophical"}
- compare_sections: Compare chapters. Parameters: {"chapterIndex1": number, "chapterIndex2": number}
- vectorization_status: Check vectorization status. No parameters needed.
- tags_list: List all tags. Parameters: {"searchKeyword": "optional"}
- book_tags: Get book tags. No parameters needed.
- apply_book_tags: Apply tags to book. Parameters: {"tagIds": [number], "action": "add|remove"}
- manage_tags: Manage tags. Parameters: {"action": "create|delete|rename", "tagName": "string", "newName": "optional"}
- add_quote: Add citation. Parameters: {"citationIndex": number, "chapterTitle": "string", "chapterIndex": number, "cfi": "string", "quotedText": "string", "reasoning": "string"}
- MCP tools: Extended tools via MCP servers (if MCP servers are configured)

IMPORTANT: Always use the correct parameter names as specified above. For rag_search, you MUST use "query" parameter, not "argo" or other names.
Example for rag_search: {"query": "童话保质期的结局是什么", "topK": 5}
Example for search_web_tavily: {"query": "今天天气怎么样", "maxResults": 3}
""")
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
