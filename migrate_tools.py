import re

# 读取文件
with open(r'd:\desktop\personal\com\dai411\app\src\main\java\io\legado\app\help\ai\AiTools.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 定义需要迁移的工具列表及其配置
tools_to_migrate = [
    ('BookshelfLookupTool', '书架查询', '获取书架上的书籍列表，可以按分组筛选。当用户询问有哪些书、查看书架时使用。', 3000),
    ('BookshelfOrganizeTool', '书架整理', '规划书架分组重组方案，需要用户确认。当用户要求整理书架、分类书籍时使用。', 8000),
    ('CompareSectionsTool', '对比章节', '对比书中不同章节或段落的内容。当用户询问两个章节的区别、比较不同部分时使用。', 15000),
    ('RagSearchTool', 'RAG搜索', '在向量化的书籍内容中进行搜索。当用户询问书中的特定内容、查找相关信息时使用。', 15000),
    ('RagTocTool', 'RAG目录', '获取向量化书籍的目录结构。当用户询问书籍结构、章节组织时使用。', 5000),
    ('RagContextTool', 'RAG上下文', '获取向量化搜索的上下文信息。当需要理解搜索结果的背景时使用。', 10000),
    ('VectorizationStatusTool', '向量化状态', '检查书籍向量化处理的状态和进度。当用户询问向量化进度、是否完成时使用。', 5000),
    ('SummarizeContentTool', '总结内容', '总结书籍或章节的主要内容。当用户询问这本书讲了什么、需要摘要时使用。', 30000),
    ('ReadingStatsTool', '阅读统计', '获取用户的阅读统计数据，包括阅读时长、频率等。当用户询问阅读习惯、统计信息时使用。', 5000),
    ('BookReadTimeRankTool', '阅读时长排行', '获取书籍阅读时长排行榜。当用户询问哪本书读得最多、阅读偏好时使用。', 5000),
]

# 对每个工具进行迁移
for tool_name, name, description, timeout in tools_to_migrate:
    # 匹配工具类定义
    pattern = rf'(class {tool_name}\s*\(\s*private val context: AiToolContext\s*\))\s*:\s*AiTool\s*\{{'
    
    replacement = rf'''\1 : BaseTool(
    id = "{tool_name[0].lower() + tool_name[1:].replace('Tool', '').lower()}",
    name = "{name}",
    description = "{description}",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf<String, Any>()
    ),
    timeout = {timeout}  // {timeout//1000}秒超时
) {{
    override suspend fun run(input: Map<String, Any>): ToolResult {{'''
    
    # 替换类定义
    content = re.sub(pattern, replacement, content, flags=re.MULTILINE | re.DOTALL)
    
    # 替换 execute 为 run
    content = re.sub(
        rf'(class {tool_name}.*?)(override suspend fun execute\(input: Map<String, Any>\): ToolResult)',
        lambda m: m.group(1) + 'override suspend fun run(input: Map<String, Any>): ToolResult',
        content,
        flags=re.MULTILINE | re.DOTALL
    )

# 写入文件
with open(r'd:\desktop\personal\com\dai411\app\src\main\java\io\legado\app\help\ai\AiTools.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Migration completed!")
