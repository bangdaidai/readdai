# AI 技能管理和快捷操作栏增强功能

## 修改概述

本次更新实现了两个主要功能：
1. **技能管理界面**：在 AI 设置中添加了完整的技能增删改查功能
2. **快捷操作栏支持 Skill**：快捷操作栏现在可以选择使用 Skill 或自定义文本

## 修改文件清单

### 1. 新增文件
- `app/src/main/res/layout/dialog_ai_skill.xml` - 技能编辑对话框布局

### 2. 修改文件
- `app/src/main/res/xml/pref_ai_settings.xml` - 添加技能管理入口
- `app/src/main/java/io/legado/app/ui/config/AiSettingsPreferenceFragment.kt` - 添加技能管理逻辑和更新快捷操作配置
- `app/src/main/java/io/legado/app/help/ai/AiAssistantConfigManager.kt` - 修改 QuickActionItem 数据结构支持 SKILL 类型
- `app/src/main/java/io/legado/app/ui/book/read/AiChatActivity.kt` - 更新快捷操作栏渲染逻辑支持 Skill 执行
- `app/src/main/res/layout/dialog_ai_quick_action_edit.xml` - 更新快捷操作编辑对话框支持类型选择
- `app/src/main/java/io/legado/app/help/ai/SkillManager.kt` - 添加 skill_theme_analysis 默认技能

## 功能说明

### 1. 技能管理界面

#### 访问路径
设置 → AI 设置 → 技能管理

#### 功能特性
- **查看所有技能**：显示内置技能和自定义技能（内置技能标注 [内置]）
- **添加技能**：创建新的自定义技能
  - 名称：技能的显示名称
  - 描述：简要说明技能用途
  - 触发词：用户输入的关键词
  - 指令：详细的系统提示词模板，支持变量如 {{bookName}}, {{chapterTitle}} 等
- **编辑技能**：修改现有技能（**内置技能也可以编辑和删除**）
- **删除技能**：删除任何技能（包括内置技能）
- **恢复默认**：重新添加所有缺失的内置技能

#### 内置技能说明
- **可编辑**：内置技能的名称、描述、触发词、指令都可以修改
- **可删除**：可以删除内置技能，但建议谨慎操作
- **可恢复**：删除后可以通过“恢复默认”按钮重新添加
- **标识**：内置技能在列表中会标注 [内置]

#### 技能与提示词的区别

**提示词（Prompt）**：
- 简单的文本模板
- 结构简单，只有一个内容字段
- 使用 `{variable}` 格式的变量
- 适合快速生成固定格式的提示
- 示例：`"请解释以下内容：{selectText}"`

**技能（Skill）**：
- 结构化的高级提示词系统
- 包含丰富的元数据（名称、描述、分类、图标、示例等）
- 使用 `{{variable}}` 格式的变量，支持更多上下文
- 有触发词，可以自动执行
- 支持变量自动替换（书名、章节、选中文字等）
- 适合复杂的、需要上下文的阅读辅助任务
- 示例：完整的系统提示词，包含角色定义、任务说明、输出格式要求等

**关系总结**：
- 技能是高级的、结构化的提示词
- 技能的 `instruction` 字段本质上就是一个复杂的提示词
- 技能比提示词更强大，支持自动执行和上下文注入
- 提示词适合简单场景，技能适合复杂场景

#### 技能变量
支持的变量包括：
- `{{bookName}}` - 书名
- `{{bookAuthor}}` - 作者
- `{{bookIntro}}` - 书籍简介
- `{{chapterTitle}}` - 章节标题
- `{{chapterContent}}` - 章节内容
- `{{selectedText}}` - 选中的文本
- `{{currentChapter}}` - 当前章节序号

### 2. 快捷操作栏支持 Skill

#### 配置方式
设置 → AI 设置 → AI助手配置 → 快捷操作栏配置

#### 两种类型
1. **Skill 类型**（推荐）
   - 从已存在的技能中选择
   - 点击后直接执行该技能
   - 自动获取技能的名称和触发词
   
2. **自定义类型**
   - 手动输入显示名称和触发词
   - 点击后将触发词填入输入框，用户可以编辑后再发送

#### 配置场景
- **无选中文字时**：默认使用 4 个 Skill（智能问答、总结本章、人物分析、主题解读）
- **有选中文字时**：默认使用 4 个自定义操作（解释这个、为什么、举个例子、换句话说）

#### UI 交互
- 固定 4 个位置，与输入框宽度对齐
- 根据是否有选中文字自动切换显示不同的操作
- 点击 Skill 类型的 chip 会立即执行
- 点击自定义类型的 chip 会填充文本到输入框

## 技术实现细节

### 数据结构变更

#### QuickActionItem（AiAssistantConfigManager.kt）
```kotlin
data class QuickActionItem(
    val positionIndex: Int,          // 位置索引 0-3
    val type: ConfigType,            // SKILL 或 CUSTOM
    val skillId: String? = null,     // 如果type=SKILL
    val displayName: String? = null, // 如果type=CUSTOM，显示名称
    val triggerWord: String? = null  // 如果type=CUSTOM，触发词
)
```

#### QuickActionItem（AiChatActivity.kt）
```kotlin
data class QuickActionItem(
    val displayName: String,   // Chip显示的文字
    val triggerWord: String,   // 填入输入框的触发词
    val type: ConfigType = ConfigType.CUSTOM,
    val skillId: String? = null
)
```

### 关键逻辑

1. **加载配置**：从 SharedPreferences 读取配置，根据类型解析为统一的 QuickActionItem
2. **渲染 UI**：根据配置动态显示最多 4 个 chip
3. **点击处理**：
   - Skill 类型：调用 `executeSkillDirectly()` 直接执行
   - Custom 类型：填充文本到输入框

## 默认配置

### 空状态配置（4个位置）
1. skill_summarize_chapter - 章节摘要
2. skill_analyze_character - 人物分析
3. skill_theme_analysis - 主题解读
4. skill_recall - 前情回顾

### 快捷操作栏 - 无选中文字（4个位置）
1. skill_custom_qa - 智能问答
2. skill_summarize_chapter - 总结本章
3. skill_analyze_character - 人物分析
4. skill_theme_analysis - 主题解读

### 快捷操作栏 - 有选中文字（4个位置）
1. 自定义 - 解释这个
2. 自定义 - 为什么
3. 自定义 - 举个例子
4. 自定义 - 换句话说

## 使用说明

### 创建自定义技能
1. 进入 AI 设置 → 技能管理
2. 点击"添加"按钮
3. 填写技能信息：
   - 名称：例如"情感分析"
   - 描述：例如"分析文本的情感倾向"
   - 触发词：例如"情感分析"
   - 指令：编写详细的提示词模板
4. 保存后即可在快捷操作栏中选择使用

### 配置快捷操作栏
1. 进入 AI 设置 → AI助手配置
2. 选择"快捷操作栏配置"
3. 选择"无选中文字"或"有选中文字"场景
4. 点击要编辑的位置
5. 选择类型（Skill 或 自定义）
6. 如果是 Skill 类型，从下拉列表选择
7. 如果是自定义类型，填写显示名称和触发词
8. 保存配置

## 注意事项

1. **内置技能可编辑**：内置技能的名称、描述、触发词、指令都可以修改
2. **内置技能可删除**：可以删除内置技能，删除后可通过“恢复默认”重新添加
3. **变量使用**：在技能指令中使用 `{{variable}}` 格式，系统会自动替换
4. **配置持久化**：所有配置保存在 SharedPreferences 中，卸载应用会清除
5. **默认配置恢复**：每个配置界面都有“恢复默认”按钮

## 测试建议

1. 测试技能管理的增删改查功能
2. 测试快捷操作栏在不同场景下的显示
3. 测试 Skill 类型和 Custom 类型的点击行为
4. 测试配置的保存和恢复
5. 测试变量替换是否正常工作
