# dai411项目AI功能完整设计方案

## 基于项目现有架构的深度适配

本文档将AI功能完全融入dai411项目现有的阅读器和书库系统中，所有交互入口、菜单项、Activity都基于项目现有代码结构设计。

---

## 一、项目现有架构分析

### 1.1 文本选择菜单（TextActionMenu）

**文件位置**：`ui/book/read/TextActionMenu.kt`

**菜单定义**：`res/menu/content_select_action.xml`

**现有菜单项**：

```xml
<item android:id="@+id/menu_replace" android:title="@string/replace" />
<item android:id="@+id/menu_copy" android:title="@android:string/copy" />
<item android:id="@+id/menu_annotation" android:title="@string/annotation" />
<item android:id="@+id/menu_protagonist" android:title="主角" />
<item android:id="@+id/menu_aloud" android:title="@string/read_aloud" />
<item android:id="@+id/menu_dict" android:title="@string/dict" />
<item android:id="@+id/menu_search_content" android:title="@string/search_content" />
<item android:id="@+id/menu_browser" android:title="@string/browser" />
<item android:id="@+id/menu_share_str" android:title="@string/share" />
```

### 1.2 阅读底部菜单（ReadMenu）

**文件位置**：`ui/book/read/ReadMenu.kt`

**核心组件**：

| 组件ID | 功能 |
|--------|------|
| llCatalog | 目录 |
| llReadAloud | 朗读 |
| llFont | 字体设置 |
| llSetting | 阅读设置 |
| fabSearch | 搜索 |
| fabAutoPage | 自动翻页 |
| fabReplaceRule | 替换规则 |
| fabNightTheme | 夜间模式 |

---

## 二、场景与交互设计（完全适配现有代码）

### 场景1：长按文本 → AI解释分析

#### 实现方案

在现有`content_select_action.xml`菜单中添加AI子菜单：

```xml
<!-- 新增菜单项 -->
<item
    android:id="@+id/menu_ai"
    android:title="AI助手"
    app:showAsAction="never">
    <menu>
        <!-- 子菜单 -->
        <item android:id="@+id/menu_ai_explain" android:title="解释这段" />
        <item android:id="@+id/menu_ai_analyze" android:title="帮我分析" />
        <item android:id="@+id/menu_ai_continue" android:title="续写故事" />
        <item android:id="@+id/menu_ai_chat" android:title="更多问题" />
    </menu>
</item>
```

#### 代码修改

**文件**：`TextActionMenu.kt`

```kotlin
// 在 onMenuItemSelected 方法中添加
private fun onMenuItemSelected(item: MenuItemImpl) {
    when (item.itemId) {
        // ... 现有菜单项 ...
        
        // 新增AI菜单
        R.id.menu_ai_explain -> {
            // 调用AI解释选中文本
            callBack.onAiExplain(callBack.selectedText)
        }
        R.id.menu_ai_analyze -> {
            // 调用AI分析选中文本
            callBack.onAiAnalyze(callBack.selectedText)
        }
        R.id.menu_ai_continue -> {
            // 调用AI续写
            callBack.onAiContinue(callBack.selectedText)
        }
        R.id.menu_ai_chat -> {
            // 跳转到AI对话页面，附带选中文本作为上下文
            callBack.onAiChat(callBack.selectedText)
        }
    }
}

// 扩展 CallBack 接口
interface CallBack {
    // ... 现有方法 ...
    
    fun onAiExplain(text: String)
    fun onAiAnalyze(text: String)
    fun onAiContinue(text: String)
    fun onAiChat(text: String)
}
```

#### 效果预览

```
用户长按选中文本后弹出的菜单：

┌─────────────────────────────┐
│ [替换] [复制] [笔记] [主角] │
│ [朗读] [词典]              │
│ ─────────────────────────  │
│ [搜索内容] [浏览器] [分享]  │
│ ─────────────────────────  │
│ [📖 AI助手 ▼] ← 新增     │
└─────────────────────────────┘
        │
        ▼ 点击展开
┌─────────────────────────────┐
│ 解释这段                   │
│ 帮我分析                   │
│ 续写故事                   │
│ 更多问题 → (跳转到AI对话)  │
└─────────────────────────────┘
```

---

### 场景2：阅读底部菜单 → AI分析入口

#### 实现方案

在现有`ReadMenu.kt`组件中添加AI按钮：

**方案A：新增FloatingActionButton**

在底部菜单添加一个新的FAB按钮：

```kotlin
// 在 ReadMenu.kt 的初始化中添加
fabAi = FloatingActionButton(context).apply {
    imageResource = R.drawable.ic_ai  // 新增AI图标
    backgroundTintList = bottomBackgroundList
    setColorFilter(textColor)
    setOnClickListener { showAiMenu() }
}
binding.root.addView(fabAi)
```

**方案B：在现有按钮区域添加点击区域**

修改现有按钮的点击区域，例如在llSetting旁边添加AI入口。

#### 代码修改

**文件**：`ReadMenu.kt`

```kotlin
// 新增AI菜单显示方法
private fun showAiMenu() {
    val popup = PopupMenu(context, binding.fabAi) // 或其他锚点
    popup.menuInflater.inflate(R.menu.read_ai_menu, popup.menu)
    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.menu_ai_summary -> {
                callBack.onAiSummaryChapter()
                true
            }
            R.id.menu_ai_book_summary -> {
                callBack.onAiSummaryBook()
                true
            }
            R.id.menu_ai_chat -> {
                callBack.onAiChat()
                true
            }
            R.id.menu_ai_recall -> {
                callBack.onAiRecall()
                true
            }
            else -> false
        }
    }
    popup.show()
}
```

**新增菜单文件**：`res/menu/read_ai_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/menu_ai_summary"
        android:title="章节摘要" />
    <item
        android:id="@+id/menu_ai_book_summary"
        android:title="全书总结" />
    <item
        android:id="@+id/menu_ai_recall"
        android:title="前情回顾" />
    <item
        android:id="@+id/menu_ai_chat"
        android:title="智能问答" />
</menu>
```

#### CallBack接口扩展

```kotlin
interface CallBack {
    // ... 现有方法 ...
    
    fun onAiSummaryChapter()
    fun onAiSummaryBook()
    fun onAiRecall()
    fun onAiChat()
}
```

#### 效果预览

```
阅读底部菜单区域：

┌──────────────────────────────────────────────┐
│  ☀️  │ ◀️ │ 🔊 │ 🔤 │ ⚙️ │ 🔍 │ ⏩ │ 🌙  │
│      │    │    │    │    │    │    │     │
│ 亮度 │ 目录│ 朗读│字体│设置│搜索│自动│夜间│
└──────────────────────────────────────────────┘

                              ┌──────┐
建议在此处添加 →              │ 📖AI │ ← 新增按钮
                              └──────┘

点击后弹出：
┌──────────────────────────┐
│ 📝 章节摘要              │
│ 📚 全书总结              │
│ 🔄 前情回顾              │
│ 💬 智能问答              │
└──────────────────────────┘
```

---

### 场景3：退出阅读 → AI自动生成章节总结

#### 实现方案

在`ReadBookActivity`的生命周期方法中检测退出：

**文件**：`ReadBookActivity.kt`

```kotlin
// 检测退出阅读
override fun onBackPressed() {
    // 判断是否显示AI总结
    if (shouldShowAiSummary()) {
        showAiSummaryDialog()
    } else {
        super.onBackPressed()
    }
}

private fun shouldShowAiSummary(): Boolean {
    val readDuration = System.currentTimeMillis() - readStartTime
    return readDuration > 2 * 60 * 1000 // 阅读超过2分钟
}

private fun showAiSummaryDialog() {
    // 显示总结确认对话框
    alert {
        title = "生成章节总结"
        message = "是否生成当前章节的AI总结？"
        positiveButton("生成") {
            // 后台生成总结
            generateChapterSummary()
            super.onBackPressed()
        }
        negativeButton("暂不") {
            super.onBackPressed()
        }
        neutralButton("不再提醒") {
            Prefs.setShowAiSummaryTip(false)
            super.onBackPressed()
        }
    }
}

private fun generateChapterSummary() {
    viewModelScope.launch {
        val summary = aiService.generateChapterSummary(
            bookUrl = currentBookUrl,
            chapterIndex = currentChapterIndex
        )
        // 保存到笔记
        saveToNote(summary)
        // 发送通知
        showNotification("章节总结已生成")
    }
}
```

#### 效果预览

```
用户点击返回时弹出：

┌────────────────────────────────────┐
│                                    │
│        📖 章节总结                  │
│                                    │
│  你已阅读当前章节超过2分钟          │
│                                    │
│  是否生成章节AI总结？              │
│                                    │
│  ────────────────────────────    │
│                                    │
│   [暂不生成]  [生成总结]          │
│                                    │
│   ☑ 下次不再提醒                   │
│                                    │
└────────────────────────────────────┘
```

---

### 场景4：进入阅读 → 前情提要弹窗

#### 实现方案

在`ReadBookActivity.onResume()`或书籍打开时检测：

```kotlin
override fun onResume() {
    super.onResume()
    // 检查是否需要显示前情提要
    checkAndShowRecallDialog()
}

private fun checkAndShowRecallDialog() {
    val lastReadTime = getLastReadTime(currentBookUrl)
    val interval = System.currentTimeMillis() - lastReadTime
    
    // 超过30分钟且有缓存的前情提要
    if (interval > 30 * 60 * 1000 && hasCachedRecall(currentBookUrl)) {
        showRecallDialog()
    }
}

private fun showRecallDialog() {
    val recallContent = getCachedRecall(currentBookUrl)
    
    alert {
        title = "📖 上次阅读到：$lastChapterName"
        message = recallContent
        positiveButton("继续阅读") {
            // 关闭弹窗，进入阅读
        }
        negativeButton("跳过") {
            // 直接进入阅读
        }
    }
}
```

#### 效果预览

```
用户重新打开书籍时（间隔>30分钟）弹出：

┌────────────────────────────────────┐
│                                    │
│        🔄 温故而知新               │
│                                    │
│  ────────────────────────────    │
│                                    │
│  📍 上次阅读到：第15章 危机/45%   │
│  ⏰ 距上次阅读已过去2小时          │
│                                    │
│  ────────────────────────────    │
│                                    │
│  📝 前情提要：                    │
│                                    │
│  上一章讲到主角打开了神秘的箱子，  │
│  发现里面装着...（AI生成）       │
│                                    │
│  关键人物：                       │
│  • 主角 - 探险家                  │
│  • 教授 - 神秘导师                │
│                                    │
│  ────────────────────────────    │
│                                    │
│     [跳过]        [开始阅读]       │
│                                    │
└────────────────────────────────────┘
```

---

### 场景5：书籍详情页 → AI入口

#### 实现方案

在书籍详情页`BookInfoActivity`中添加AI按钮：

**文件**：`res/layout/activity_book_info.xml`

在现有按钮区域添加AI按钮：

```xml
<!-- 在开始阅读按钮旁边添加 -->
<Button
    android:id="@+id/btn_ai_chat"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="AI问答"
    android:drawableStart="@drawable/ic_ai" />
```

**文件**：`BookInfoActivity.kt`

```kotlin
btnAiChat.setOnClickListener {
    val intent = Intent(this, AiChatActivity::class.java).apply {
        putExtra("bookUrl", bookUrl)
    }
    startActivity(intent)
}
```

#### 效果预览

```
书籍详情页：

┌────────────────────────────────────┐
│ ← 返回              书籍详情      │
├────────────────────────────────────┤
│                                    │
│    ┌───────────────┐              │
│    │               │              │
│    │    封面      │              │
│    │               │              │
│    └───────────────┘              │
│                                    │
│    书名：xxx                      │
│    作者：xxx                      │
│    进度：45%                     │
│                                    │
│    [开始阅读]  [目录]             │
│                                    │
├────────────────────────────────────┤
│                                    │
│  ┌─────────────────────────────┐ │
│  │ 🤖 与这本书对话              │ │
│  │ 问AI关于这本书的问题         │ │
│  └─────────────────────────────┘ │
│                                    │
│  ┌─────────────────────────────┐ │
│  │ 📝 生成书籍总结              │ │
│  │ 快速了解本书主要内容         │ │
│  └─────────────────────────────┘ │
│                                    │
└────────────────────────────────────┘
```

---

## 三、功能模块详细设计

### 3.1 AI对话Activity

#### Activity配置

```kotlin
class AiChatActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_BOOK_URL = "bookUrl"
        const val EXTRA_CHAPTER_INDEX = "chapterIndex"
        const val EXTRA_SELECTED_TEXT = "selectedText"
        const val EXTRA_CHAT_SESSION_ID = "sessionId"
    }
    
    // 接收参数构建AI上下文
    private val bookUrl: String? by lazy { intent.getStringExtra(EXTRA_BOOK_URL) }
    private val selectedText: String? by lazy { intent.getStringExtra(EXTRA_SELECTED_TEXT) }
}
```

#### 布局设计

```
┌────────────────────────────────────────────┐
│ ← 返回              AI阅读助手        ⋮   │
├────────────────────────────────────────────┤
│                                            │
│  当前阅读：《书名》                        │
│  第15章 危机                              │
│                                            │
├────────────────────────────────────────────┤
│                                            │
│  ┌──────────────────────────────────────┐ │
│  │ AI                                    │ │
│  │ 你好！我是你的阅读助手。             │ │
│  │ 可以帮你解答阅读中的问题...          │ │
│  └──────────────────────────────────────┘ │
│                                            │
│  ┌──────────────────────────────────────┐ │
│  │ 用户                                  │ │
│  │ 这章主要讲了什么？                   │ │
│  └──────────────────────────────────────┘ │
│                                            │
│  ┌──────────────────────────────────────┐ │
│  │ AI                                    │ │
│  │ 本章主要讲述了...                     │ │
│  │ （流式输出中...）                    │ │
│  └──────────────────────────────────────┘ │
│                                            │
├────────────────────────────────────────────┤
│ [解释] [分析] [总结] [推测]               │
│ ┌──────────────────────────────────────┐ │
│ │ 你有什么问题？...                   │ │
│ └──────────────────────────────────────┘ │
│                              [发送]      │
└────────────────────────────────────────────┘
```

### 3.2 AI设置Activity

#### 配置项

```kotlin
class AiSettingsActivity : AppCompatActivity() {
    // 配置项：
    // 1. 选择AI服务商（OpenAI/Claude/Gemini/DeepSeek/阿里云）
    // 2. 输入API Key
    // 3. 选择模型
    // 4. 设置请求限流（RPM）
    // 5. 管理对话历史
}
```

#### 布局层级

```
AI设置
├── 当前服务商：OpenAI ▼
├── API配置
│   ├── API Key：┌─────────────────────┐
│   └── 模型：   │ gpt-4o-mini      ▼ │
├── 请求设置
│   ├── 每分钟请求限制：[60]
│   └── 密钥轮换：[开启]
├── 对话管理
│   └── 清除对话历史
└── 关于
    └── 版本信息
```

### 3.3 数据模型（基于Room）

```kotlin
// AI服务商配置
@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean,
    val isBuiltin: Boolean,
    val sortOrder: Int
)

// AI对话会话
@Entity(tableName = "ai_chat_sessions")
data class AiChatSessionEntity(
    @PrimaryKey val id: String,
    val bookUrl: String?,
    val providerId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

// AI对话消息
@Entity(tableName = "ai_chat_messages")
data class AiChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,  // "user" / "assistant"
    val content: String,
    val createdAt: Long
)

// 前情提要缓存
@Entity(tableName = "ai_recall_cache")
data class AiRecallCacheEntity(
    @PrimaryKey val bookUrl: String,
    val content: String,
    val chapterIndex: Int,
    val createdAt: Long
)
```

---

## 四、现有代码集成点

### 4.1 需要修改的文件清单

| 文件 | 修改内容 | 优先级 |
|------|----------|--------|
| `content_select_action.xml` | 添加AI菜单项 | P0 |
| `TextActionMenu.kt` | 处理AI菜单点击 | P0 |
| `ReadMenu.kt` | 添加AI入口按钮 | P0 |
| `ReadBookActivity.kt` | 退出/进入检测逻辑 | P0 |
| `BookInfoActivity.kt` | 添加AI按钮 | P1 |
| `BookInfoActivity.kt` | 添加AI按钮 | P1 |

### 4.2 需要新增的文件

| 文件 | 说明 | 优先级 |
|------|------|--------|
| `AiChatActivity.kt` | AI对话页面 | P0 |
| `AiSettingsActivity.kt` | AI设置页面 | P0 |
| `AiService.kt` | AI服务层 | P0 |
| `AiApiClient.kt` | API客户端 | P0 |
| `read_ai_menu.xml` | 阅读菜单AI子菜单 | P0 |
| `ai_providers.xml` | AI服务商配置布局 | P0 |
| `AiProviderDao.kt` | Room DAO | P1 |
| `AiDatabase.kt` | 数据库扩展 | P1 |

---

## 五、场景触发条件汇总

| 场景 | 触发条件 | 入口位置 | 处理逻辑 |
|------|----------|----------|----------|
| 长按文本 | 用户选中文本后 | 文本选择菜单 | 调用AI解释/分析/续写 |
| 点击AI按钮 | 用户点击底部AI入口 | 阅读底部菜单 | 弹出AI功能选择 |
| 退出阅读 | 用户点击返回 | ReadBookActivity | 阅读>2分钟→弹窗询问 |
| 进入阅读 | 打开书籍 | ReadBookActivity | 间隔>30分钟→弹窗前情 |
| 书籍详情 | 点击AI按钮 | BookInfoActivity | 打开AI对话页面 |

---

## 六、与现有功能的冲突避免

### 6.1 菜单冲突

现有菜单已较丰富，AI入口采用**子菜单**形式，避免增加顶层菜单项数量。

### 6.2 性能考虑

- 前情提要使用**缓存机制**，避免每次进入都调用AI
- 退出时生成总结采用**后台处理**，不阻塞用户退出
- 对话使用**流式响应**，提升用户体验

### 6.3 兼容性考虑

- AI功能设为**可选**，不强制启用
- 无API Key时显示友好提示，引导用户配置
- 网络异常时提供离线提示

---

## 七、总结

本方案完全基于dai411项目现有架构设计：

1. **长按文本**：修改`content_select_action.xml`，添加AI子菜单
2. **阅读底部菜单**：在`ReadMenu.kt`中添加AI按钮和弹出菜单
3. **退出/进入**：在`ReadBookActivity`中添加检测逻辑
4. **书籍详情**：在`BookInfoActivity`中添加AI入口

所有入口都与现有功能自然融合，不破坏用户体验。