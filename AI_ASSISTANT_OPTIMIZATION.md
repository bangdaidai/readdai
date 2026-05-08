# AI助手交互优化说明

## 优化内容

### 1. 空状态（试试这样问）- 固定4个卡片

**配置入口**: `empty_state`

**默认内容**:
1. 总结本章 - 总结本章的核心内容
2. 人物关系 - 分析主要人物的关系  
3. 主题解读 - 这本书想表达什么主题？
4. 前情回顾 - 回顾之前的关键情节

**特点**:
- 仅在无对话历史时显示
- 基于Skill系统，可配置
- 点击直接执行并输出结果

---

### 2. 快捷操作栏 - 固定5个Chip，始终显示

**核心改进**: 快捷操作栏**始终显示**，但内容根据是否有选中文字动态切换

#### **场景A: 无选中文字时**
显示通用快捷操作：
1. 智能问答 - 回答关于本书的问题
2. 总结本章 - 总结本章的核心内容
3. 人物分析 - 分析主要人物的关系
4. 主题解读 - 这本书想表达什么主题？
5. 前情回顾 - 回顾之前的关键情节

#### **场景B: 有选中文字时**
显示针对选中文字的操作：
1. 解释这个 - 解释这段话的意思
2. 为什么 - 为什么会这样说？
3. 举个例子 - 举个相关的例子
4. 换句话说 - 用更简单的话重述
5. 深层含义 - 这段话有什么深层含义？

**点击行为**: 
- 填充触发词到输入框
- **不自动发送**，允许用户编辑
- 光标定位到末尾

---

## 代码修改要点

### 1. 新增数据结构

```kotlin
data class QuickActionItem(
    val displayName: String,  // Chip显示的文字
    val triggerWord: String   // 填入输入框的触发词
)
```

### 2. 核心方法

#### `initQuickActionBar()`
- 初始化快捷操作栏
- 默认显示无选中文字时的通用操作

#### `updateQuickActionBar(hasSelectedText: Boolean)`
- 根据是否有选中文字更新快捷操作栏内容
- 有选中文字 → 显示针对性操作
- 无选中文字 → 显示通用操作

#### `renderQuickActionBar()`
- 渲染快捷操作栏到UI
- 创建5个Chip按钮
- 绑定点击事件（填充文本，不自动发送）

### 3. 引用管理联动

在以下方法中调用 `updateQuickActionBar()`:
- `addQuote()` - 添加引用后 → 切换到"有选中文字"模式
- `removeQuote()` - 移除引用后 → 根据剩余引用数量决定模式
- `clearAllQuotes()` - 清空引用后 → 切换到"无选中文字"模式

---

## 用户体验流程

### 流程1: 首次进入（无选中文字）
```
1. 看到空状态的4个"试试这样问"卡片
2. 看到快捷操作栏的5个通用操作
3. 点击任意卡片或Chip开始对话
```

### 流程2: 阅读中选中文字
```
1. 在阅读页选中一段文字
2. 点击"Ask AI"进入AI助手
3. 引用Chip自动显示选中的文字
4. 快捷操作栏自动切换为"针对选中文字"的5个操作
5. 点击"解释这个" → 输入框填充"解释这段话的意思"
6. 用户可补充或直接发送
```

### 流程3: 移除引用
```
1. 点击引用Chip的关闭按钮
2. 引用被移除
3. 快捷操作栏自动切换回"通用操作"模式
```

---

## 优势总结

### ✅ 始终可见的快捷操作
- 用户随时可以看到可用的快捷操作
- 不会因为隐藏而感到困惑

### ✅ 智能上下文感知
- 有选中文字 → 提供针对性帮助
- 无选中文字 → 提供通用功能

### ✅ 固定数量，布局稳定
- 空状态固定4个卡片
- 快捷操作栏固定5个Chip
- UI不会因配置变化而抖动

### ✅ 灵活可配置
- 未来可在设置中自定义每个位置的内容
- 支持Skill模板或纯文本指令

### ✅ 符合用户直觉
- 选中文字后，自然想知道"这是什么意思"、"为什么这样说"
- 无选中文字时，想要"总结"、"分析"等整体性功能

---

## 后续优化方向

✅ **已完成：用户自定义配置功能**

### 1. 配置管理架构

**核心文件**: `AiAssistantConfigManager.kt`

- ✅ 支持空状态配置（4个位置）
- ✅ 支持快捷操作栏配置（两套：无选中文字5个 + 有选中文字5个）
- ✅ 使用 SharedPreferences 持久化存储
- ✅ 支持 Skill 类型和 Custom 类型
- ✅ 提供恢复默认功能

### 2. 配置入口

**路径**: AI设置 → AI助手配置

三个配置项：
1. **空状态配置（试试这样问）** - 4个卡片位置
2. **快捷操作栏 - 无选中文字** - 4个Chip位置
3. **快捷操作栏 - 有选中文字** - 4个Chip位置

### 3. 空状态配置界面

**功能**:
- 点击任意位置可编辑
- 支持两种类型：
  - **Skill类型**: 从现有Skill列表中选择
  - **Custom类型**: 自定义显示名称、触发词、描述
- 一键恢复默认

**UI布局**: 
```xml
dialog_ai_assistant_config_item.xml
- RadioGroup: 选择Skill/Custom类型
- Spinner: Skill列表（Skill类型时显示）
- EditText: 名称、触发词、描述（Custom类型时显示）
```

### 4. 快捷操作栏配置界面

**功能**:
- 分别配置“无选中文字”和“有选中文字”两套内容
- 每个位置可自定义：
  - 显示名称（Chip上显示的文字）
  - 触发词（填入输入框的内容）
- 一键恢复默认
- **固定4个位置，与输入框左右对齐，无需滚动**

**UI布局**:
```xml
dialog_ai_quick_action_edit.xml
- EditText: 显示名称
- EditText: 触发词
```

### 5. AiChatActivity集成

**修改点**:

1. **setupSuggestChips()**:
   - 从配置管理器加载空状态配置
   - 根据配置类型（Skill/Custom）执行不同逻辑
   - Skill类型：调用 executeSkillDirectly()
   - Custom类型：填充文本并自动发送

2. **updateQuickActionBar()**:
   - 根据 hasSelectedText 参数加载对应配置
   - 动态切换显示内容

3. **引用管理联动**:
   - addQuote/removeQuote/clearAllQuotes 中调用 updateQuickActionBar()
   - 自动切换快捷操作栏内容

### 6. 数据流

```
用户配置 → AiAssistantConfigManager → SharedPreferences
                                    ↓
                          AiChatActivity 读取配置
                                    ↓
                          渲染到 UI（空状态/快捷操作栏）
                                    ↓
                          用户点击 → 执行对应操作
```

### 7. 预设内容

**空状态（4个位置）**:
1. 总结本章 - Skill: skill_summarize_chapter
2. 人物关系 - Skill: skill_analyze_character
3. 主题解读 - Skill: skill_theme_analysis
4. 前情回顾 - Skill: skill_recall

**快捷操作栏 - 无选中文字（4个位置）**:
1. 智能问答 → "回答关于本书的问题"
2. 总结本章 → "总结本章的核心内容"
3. 人物分析 → "分析主要人物的关系"
4. 主题解读 → "这本书想表达什么主题？"

**快捷操作栏 - 有选中文字（4个位置）**:
1. 解释这个 → "解释这段话的意思"
2. 为什么 → "为什么会这样说？"
3. 举个例子 → "举个相关的例子"
4. 换句话说 → "用更简单的话重述"

---

## 技术细节

### 配置文件 Key

```kotlin
PREF_EMPTY_STATE_CONFIG = "ai_empty_state_config"
PREF_QUICK_ACTION_NO_SELECTION_CONFIG = "ai_quick_action_no_selection_config"
PREF_QUICK_ACTION_WITH_SELECTION_CONFIG = "ai_quick_action_with_selection_config"
```

### 数据结构

```kotlin
// 空状态配置项
data class EmptyStateItem(
    val positionIndex: Int,          // 0-3
    val type: ConfigType,            // SKILL or CUSTOM
    val skillId: String? = null,
    val customName: String? = null,
    val customTrigger: String? = null,
    val customDescription: String? = null
)

// 快捷操作配置项
data class QuickActionItem(
    val positionIndex: Int,          // 0-4
    val displayName: String,
    val triggerWord: String
)
```

### 关键代码片段

**加载空状态配置**:
```kotlin
val config = AiAssistantConfigManager.getEmptyStateConfig(context)
config.take(4).forEachIndexed { index, item ->
    when (item.type) {
        ConfigType.SKILL -> {
            val skill = allSkills.find { it.id == item.skillId }
            // 执行Skill
        }
        ConfigType.CUSTOM -> {
            // 填充自定义文本并发送
        }
    }
}
```

**加载快捷操作配置**:
```kotlin
val config = AiAssistantConfigManager.getQuickActionBarConfig(context, hasSelectedText)
quickActionItems = config.map { item ->
    QuickActionItem(item.displayName, item.triggerWord)
}
renderQuickActionBar()
```

---

## 总结

✅ **已完成的功能**:
1. 空状态自定义配置（4个位置，支持Skill/Custom类型）
2. 快捷操作栏自定义配置（两套各4个位置）
3. 配置管理器和持久化存储
4. 设置界面和编辑对话框
5. AiChatActivity集成和动态切换
6. 引用管理联动
7. **固定布局，与输入框左右对齐，无需滚动**

🎯 **核心优势**:
- 固定布局数量，避免UI溢出
- 智能场景切换（有/无选中文字）
- 用户完全可自定义
- 一键恢复默认
- 支持Skill系统和纯文本两种模式

---

## 技术细节

### Skill入口配置
- 空状态使用: `skillManager.getSkillsForEntrance("empty_state")`
- 需要在Skill的`showIn`字段中添加`"empty_state"`标识

### 配置文件 Key
```kotlin
PREF_EMPTY_STATE_CONFIG = "ai_empty_state_config"
PREF_QUICK_ACTION_NO_SELECTION_CONFIG = "ai_quick_action_no_selection_config"
PREF_QUICK_ACTION_WITH_SELECTION_CONFIG = "ai_quick_action_with_selection_config"
```

---

**完成时间**: 2026-05-05
**修改文件**: 
- `AiChatActivity.kt`
- `AiAssistantConfigManager.kt` (新增)
- `AiSettingsPreferenceFragment.kt`
- `dialog_ai_assistant_config_item.xml` (新增)
- `dialog_ai_quick_action_edit.xml` (新增)
- `pref_ai_settings.xml`

**影响范围**: AI助手交互逻辑、用户配置系统
