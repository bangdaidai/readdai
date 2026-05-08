# AI助手自定义配置功能 - 实现总结

## 📋 概述

本次优化为Dai项目的AI阅读助手添加了完整的用户自定义配置功能，允许用户在设置中自定义"空状态（试试这样问）"和"快捷操作栏"的显示内容。

---

## 🎯 核心设计原则

1. **固定布局数量**：避免UI溢出或截断问题
   - 空状态：固定4个卡片位置
   - 快捷操作栏：固定4个Chip位置（两套配置）
   - **与输入框左右对齐，无需滚动**

2. **智能场景切换**：根据是否有选中文字动态切换快捷操作栏内容

3. **完全可自定义**：支持Skill类型和Custom类型两种模式

4. **一键恢复默认**：随时可以恢复到预设配置

---

## 📁 新增文件

### 1. AiAssistantConfigManager.kt
**路径**: `app/src/main/java/io/legado/app/help/ai/AiAssistantConfigManager.kt`

**功能**:
- 管理空状态配置（4个位置）
- 管理快捷操作栏配置（两套各4个位置）
- 使用SharedPreferences持久化存储
- 提供默认配置和恢复功能

**关键数据结构**:
```kotlin
data class EmptyStateItem(
    val positionIndex: Int,          // 0-3
    val type: ConfigType,            // SKILL or CUSTOM
    val skillId: String? = null,
    val customName: String? = null,
    val customTrigger: String? = null,
    val customDescription: String? = null
)

data class QuickActionItem(
    val positionIndex: Int,          // 0-4
    val displayName: String,
    val triggerWord: String
)

enum class ConfigType {
    SKILL,    // 使用Skill系统
    CUSTOM    // 自定义纯文本
}
```

**配置文件Key**:
```kotlin
PREF_EMPTY_STATE_CONFIG = "ai_empty_state_config"
PREF_QUICK_ACTION_NO_SELECTION_CONFIG = "ai_quick_action_no_selection_config"
PREF_QUICK_ACTION_WITH_SELECTION_CONFIG = "ai_quick_action_with_selection_config"
```

---

### 2. dialog_ai_assistant_config_item.xml
**路径**: `app/src/main/res/layout/dialog_ai_assistant_config_item.xml`

**用途**: 空状态配置项编辑对话框

**UI组件**:
- RadioGroup: 选择Skill/Custom类型
- Spinner: Skill列表下拉选择（Skill类型时显示）
- EditText: 名称、触发词、描述（Custom类型时显示）

---

### 3. dialog_ai_quick_action_edit.xml
**路径**: `app/src/main/res/layout/dialog_ai_quick_action_edit.xml`

**用途**: 快捷操作项编辑对话框

**UI组件**:
- EditText: 显示名称
- EditText: 触发词

---

## 🔧 修改文件

### 1. AiChatActivity.kt
**路径**: `app/src/main/java/io/legado/app/ui/book/read/AiChatActivity.kt`

**主要修改**:

#### a) setupSuggestChips()
- **之前**: 从`skillManager.getSkillsForEntrance("empty_state")`获取技能
- **现在**: 从`AiAssistantConfigManager.getEmptyStateConfig()`获取配置
- **新增逻辑**:
  ```kotlin
  when (item.type) {
      ConfigType.SKILL -> {
          val skill = allSkills.find { it.id == item.skillId }
          executeSkillDirectly(skill.id, displayText)
      }
      ConfigType.CUSTOM -> {
          binding.editText.setText(displayText)
          sendMessage()  // 自动发送
      }
  }
  ```

#### b) updateQuickActionBar(hasSelectedText: Boolean)
- **之前**: 硬编码两套配置（有/无选中文字）
- **现在**: 从配置管理器加载
  ```kotlin
  val config = AiAssistantConfigManager.getQuickActionBarConfig(this, hasSelectedText)
  quickActionItems = config.map { item ->
      QuickActionItem(item.displayName, item.triggerWord)
  }
  ```

#### c) 引用管理联动
在以下方法中添加快捷操作栏更新调用：
- `addQuote()`: 切换到有选中文字模式
- `removeQuote()`: 根据剩余数量自动切换
- `clearAllQuotes()`: 切换回无选中文字模式

---

### 2. AiSettingsPreferenceFragment.kt
**路径**: `app/src/main/java/io/legado/app/ui/config/AiSettingsPreferenceFragment.kt`

**主要修改**:

#### a) onPreferenceTreeClick()
添加新的配置入口：
```kotlin
"ai_assistant_config" -> showAssistantConfigDialog()
```

#### b) showAssistantConfigDialog()
显示三个配置选项：
1. 空状态配置（试试这样问）
2. 快捷操作栏 - 无选中文字
3. 快捷操作栏 - 有选中文字

#### c) showEmptyStateConfigDialog()
- 加载当前配置
- 显示4个位置的列表
- 点击位置弹出编辑对话框
- 提供"恢复默认"按钮

#### d) showEditEmptyStateItemDialog()
- 支持Skill/Custom类型切换
- Skill类型：从下拉列表选择
- Custom类型：填写名称、触发词、描述
- 输入验证（Custom类型时名称和触发词不能为空）

#### e) showQuickActionBarConfigDialog(hasSelectedText: Boolean)
- 根据hasSelectedText加载对应配置
- 显示5个位置的列表
- 点击位置弹出编辑对话框
- 提供"恢复默认"按钮

#### f) showEditQuickActionItemDialog(...)
- 编辑显示名称和触发词
- 输入验证（不能为空）
- 保存时传入hasSelectedText参数

---

### 3. pref_ai_settings.xml
**路径**: `app/src/main/res/xml/pref_ai_settings.xml`

**新增配置项**:
```xml
<!-- AI助手配置 - 点击弹出配置对话框 -->
<io.legado.app.lib.prefs.Preference
    android:key="ai_assistant_config"
    android:title="AI助手配置"
    android:summary="自定义空状态和快捷操作栏"
    app:iconSpaceReserved="false" />
```

---

## 🎨 用户体验流程

### 场景1: 配置空状态

1. 用户进入 **设置 → AI设置 → AI助手配置**
2. 点击 **"空状态配置（试试这样问）"**
3. 看到4个位置的列表
4. 点击任意位置进行编辑
5. 选择类型：
   - **Skill类型**: 从下拉列表选择现有Skill
   - **Custom类型**: 填写名称、触发词、描述
6. 点击保存
7. 下次打开AI对话页面（无历史消息）时，看到更新后的4个卡片

### 场景2: 配置快捷操作栏（无选中文字）

1. 用户进入 **设置 → AI设置 → AI助手配置**
2. 点击 **“快捷操作栏 - 无选中文字”**
3. 看到4个位置的列表
4. 点击任意位置编辑显示名称和触发词
5. 点击保存
6. 在AI对话页面（未选中文字），看到更新后的4个Chip按钮，与输入框左右对齐

### 场景3: 配置快捷操作栏（有选中文字）

1. 用户进入 **设置 → AI设置 → AI助手配置**
2. 点击 **“快捷操作栏 - 有选中文字”**
3. 看到4个位置的列表
4. 点击任意位置编辑
5. 点击保存
6. 在阅读页面选中文字后进入AI对话，看到更新后的4个Chip按钮，与输入框左右对齐

---

## 🔄 数据流

```
用户配置操作
    ↓
AiSettingsPreferenceFragment (编辑对话框)
    ↓
AiAssistantConfigManager (saveXXXConfig)
    ↓
SharedPreferences (持久化存储)
    ↓
用户打开AI对话页面
    ↓
AiChatActivity (setupSuggestChips / updateQuickActionBar)
    ↓
AiAssistantConfigManager (getXXXConfig)
    ↓
SharedPreferences (读取配置)
    ↓
渲染到UI (空状态卡片 / 快捷操作Chip)
    ↓
用户点击
    ↓
执行对应操作 (Skill执行 / 文本填充)
```

---

## ✅ 功能清单

### 空状态配置
- [x] 固定4个位置
- [x] 支持Skill类型（从现有Skill选择）
- [x] 支持Custom类型（自定义名称、触发词、描述）
- [x] 一键恢复默认
- [x] 输入验证
- [x] 持久化存储

### 快捷操作栏配置
- [x] 两套独立配置（有/无选中文字）
- [x] 每套固定4个位置
- [x] 自定义显示名称和触发词
- [x] 一键恢复默认
- [x] 输入验证
- [x] 持久化存储
- [x] **与输入框左右对齐，无需滚动**

### 集成与联动
- [x] AiChatActivity读取配置
- [x] 根据配置类型执行不同逻辑
- [x] 引用管理触发快捷操作栏切换
- [x] 动态更新UI

---

## 📊 预设配置

### 空状态（4个位置）
| 位置 | 类型 | 内容 |
|------|------|------|
| 1 | Skill | skill_summarize_chapter (总结本章) |
| 2 | Skill | skill_analyze_character (人物关系) |
| 3 | Skill | skill_theme_analysis (主题解读) |
| 4 | Skill | skill_recall (前情回顾) |

### 快捷操作栏 - 无选中文字（4个位置）
| 位置 | 显示名称 | 触发词 |
|------|----------|--------|
| 1 | 智能问答 | 回答关于本书的问题 |
| 2 | 总结本章 | 总结本章的核心内容 |
| 3 | 人物分析 | 分析主要人物的关系 |
| 4 | 主题解读 | 这本书想表达什么主题？ |

### 快捷操作栏 - 有选中文字（4个位置）
| 位置 | 显示名称 | 触发词 |
|------|----------|--------|
| 1 | 解释这个 | 解释这段话的意思 |
| 2 | 为什么 | 为什么会这样说？ |
| 3 | 举个例子 | 举个相关的例子 |
| 4 | 换句话说 | 用更简单的话重述 |

---

## 🧪 测试建议

详细测试指南请参考：[AI_ASSISTANT_CONFIG_TEST.md](./AI_ASSISTANT_CONFIG_TEST.md)

### 快速测试清单
- [ ] 能进入配置界面
- [ ] 能编辑空状态配置（Skill和Custom类型）
- [ ] 能编辑快捷操作栏配置（两套）
- [ ] 配置能正确保存到SharedPreferences
- [ ] 应用重启后配置仍然有效
- [ ] 空状态卡片显示配置的内容
- [ ] 快捷操作栏根据选中文字状态切换
- [ ] 恢复默认功能正常
- [ ] 输入验证阻止空文本

---

## 🚀 后续优化方向

### 短期优化
1. **配置导入/导出**: 允许用户备份和分享配置
2. **更多预设模板**: 
   - 学生模式
   - 技术读者模式
   - 外语学习者模式
3. **配置同步**: 支持云端同步配置

### 长期优化
1. **智能推荐**: 根据用户阅读习惯推荐配置
2. **A/B测试**: 测试不同配置的效果
3. **社区分享**: 用户可以分享自己的配置方案

---

## 📝 技术要点

### SharedPreferences使用
```kotlin
// 保存
context.putPrefString(PREF_KEY, gson.toJson(config))

// 读取
val json = context.getPrefString(PREF_KEY, null)
val config = gson.fromJson(json, type)
```

### Gson序列化
```kotlin
private val gson = Gson()
val type = object : TypeToken<List<EmptyStateItem>>() {}.type
val config: List<EmptyStateItem> = gson.fromJson(json, type)
```

### 动态UI更新
```kotlin
// 根据配置类型显示不同UI
when (item.type) {
    ConfigType.SKILL -> spinnerSkill.visibility = View.VISIBLE
    ConfigType.CUSTOM -> etName.visibility = View.VISIBLE
}
```

---

## 📌 注意事项

1. **固定数量**: UI布局固定为4个空状态卡片和5个快捷操作Chip，不允许超额配置
2. **输入验证**: Custom类型必须填写名称和触发词
3. **场景区分**: 快捷操作栏有两套独立配置，分别对应有/无选中文字的场景
4. **Skill依赖**: Skill类型的空状态配置依赖于Skill系统中已存在的Skill
5. **持久化**: 配置保存在SharedPreferences中，清除应用数据会丢失配置

---

**完成时间**: 2026-05-05  
**开发者**: Lingma AI Assistant  
**版本**: v1.0
