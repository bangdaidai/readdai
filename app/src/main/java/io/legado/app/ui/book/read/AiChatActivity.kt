package io.legado.app.ui.book.read

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityAiChatBinding
import io.legado.app.help.ai.AiApiClient
import io.legado.app.help.ai.AiChatSession
import io.legado.app.help.ai.AiService
import io.legado.app.help.ai.AiHistoryStore
import io.legado.app.help.ai.ChatMessage
import io.legado.app.help.ai.ChatResult
import io.legado.app.help.ai.PromptManager
import io.legado.app.help.ai.SkillManager
import io.legado.app.help.ai.ReasoningParser
import io.legado.app.help.ai.ToolStep
import io.legado.app.help.ai.ToolStepStatus
import io.legado.app.help.ai.AiToolContext
import io.legado.app.help.ai.AiTools
import io.legado.app.help.ai.ReadingContextService
import io.legado.app.help.ai.ReadingContext
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.utils.MarkdownUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AI对话页面
 * 参考ReadAny的ChatPage设计
 * 完整支持: 请求取消、多引用、模型选择、会话历史
 */
class AiChatActivity : BaseActivity<ActivityAiChatBinding>() {

    override val binding by viewBinding(ActivityAiChatBinding::inflate)

    private lateinit var aiService: AiService
    private lateinit var promptManager: PromptManager
    private lateinit var skillManager: SkillManager
    private lateinit var adapter: ChatAdapter
    private var currentSession: AiChatSession? = null
    private var historyDialog: AlertDialog? = null  // 保存历史对话框引用

    private var bookUrl: String? = null
    private var bookTitle: String? = null
    private var author: String? = null
    private var chapterTitle: String? = null
    private var chapterContent: String? = null
    private var selectedQuote: String? = null  // 单引用文本
    private var deepThinkingEnabled: Boolean = false
    private var spoilerFreeEnabled: Boolean = false
    private var isRequestActive: Boolean = false
    
    // 快捷操作栏配置（选中文字时显示）
    private var quickActionItems: List<QuickActionItem> = emptyList()

    // 当前使用的模型
    private var currentModel: String = "default"

    private val messages = mutableListOf<ChatMessageItem>()
    private var streamingPosition: Int = -1 // 当前流式输出的消息位置
    
    // 快捷操作项
    data class QuickActionItem(
        val displayName: String,   // Chip显示的文字
        val triggerWord: String,   // 填入输入框的触发词
        val type: io.legado.app.help.ai.AiAssistantConfigManager.ConfigType = io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.CUSTOM,
        val skillId: String? = null  // 如果type=SKILL
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookUrl = intent.getStringExtra("bookUrl")
        bookTitle = intent.getStringExtra("bookTitle")
        author = intent.getStringExtra("author")
        chapterTitle = intent.getStringExtra("chapterTitle")
        chapterContent = intent.getStringExtra("chapterContent")
        selectedQuote = intent.getStringExtra("selectedText")

        // 初始化AI服务（只创建一次实例）
        aiService = AiService(this)
        promptManager = PromptManager(this)
        skillManager = SkillManager(this)

        initViews()
        
        // 异步初始化AI服务内部状态
        lifecycleScope.launch {
            aiService.init()
            
            // 传递当前书籍信息给AI服务（如果有）
            if (bookUrl != null) {
                val book = appDb.bookDao.getBook(bookUrl!!)
                val chapter = book?.let {
                    appDb.bookChapterDao.getChapter(it.bookUrl, it.durChapterIndex)
                }
                aiService.setToolContext(book, chapter, chapterContent)
            } else {
                // 如果没有阅读上下文，仍然注册全局Tool（书架查询、阅读历史等）
                // 创建一个空的context，让Tool可以访问数据库
                val emptyContext = AiToolContext(
                    currentBook = null,
                    currentChapter = null,
                    chapterContent = null,
                    bookUrl = "",
                    appDatabase = appDb,
                    appContext = this@AiChatActivity
                )
                AiTools.registerAll(emptyContext)
            }
        }
        
        // 创建新会话
        createNewSession()
    }

    override fun shouldHandleImePadding(): Boolean {
        // AI聊天页面需要处理输入法遮挡
        return true
    }

    private fun updateAdapter() {
        adapter = ChatAdapter(this, messages, 
            onItemLongClick = { message, isLongClick ->
                if (isLongClick) {
                    showMessageOptions(message)
                }
            },
            onCopyClick = { content ->
                copyToClipboard(content)
            },
            onRegenerateClick = {
                regenerateLastMessage()
            },
            isStreamingPosition = streamingPosition
        )
        binding.recyclerView.adapter = adapter
    }

    private fun initViews() {
        binding.titleBar.title = "AI阅读助手"
        binding.titleBar.setNavigationOnClickListener { finish() }

        updateAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 初始化时发送按钮显示纸飞机图标
        binding.btnSend.setImageResource(R.drawable.ic_send)
        
        // 设置发送按钮背景色为主题强调色（参考深度思考按钮的实现）
        val accentColor = ThemeStore.accentColor(this)
        binding.btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        
        // 强制设置输入框容器背景色和边框色为主题色（确保生效）
        val backgroundCardColor = ThemeStore.backgroundCard(this)
        val dividerColor = ThemeStore.dividerColor(this)
        android.util.Log.d("AiChatActivity", "主题色 - backgroundCard: ${String.format("#%06X", 0xFFFFFF and backgroundCardColor)}, divider: ${String.format("#%06X", 0xFFFFFF and dividerColor)}")
        binding.inputLayout.setCardBackgroundColor(backgroundCardColor)
        binding.inputLayout.strokeWidth = 1.dpToPx()
        binding.inputLayout.strokeColor = dividerColor

        // 显示传递过来的引用内容
        val initialQuote = intent.getStringExtra("selectedText")
        if (!initialQuote.isNullOrBlank()) {
            setQuote(initialQuote)
        }

        // 旧版引用移除按钮（兼容）
        binding.btnRemoveQuote.setOnClickListener {
            clearQuote()
        }

        // 发送/取消按钮 - 点击发送或取消
        binding.btnSend.setOnClickListener {
            if (isRequestActive) {
                cancelCurrentRequest()
            } else {
                val text = binding.editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                }
            }
        }

        // 键盘发送
        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = binding.editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                }
                true
            } else {
                false
            }
        }

        setupSuggestChips()
        setupOptionSwitches()
        // 初始化选项开关样式
        updateOptionStyle(binding.layoutDeepThinking, false)
        updateOptionStyle(binding.layoutSpoilerFree, false)
        
        // 初始化空状态显示
        updateEmptyState()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_chat_menu, menu)
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_new_chat -> {
                // 新建对话
                createNewSession()
                messages.clear()
                streamingPosition = -1
                updateAdapter()
                updateEmptyState()
                Toast.makeText(this, "已新建对话", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_history -> {
                showHistorySidebar()
                true
            }
            R.id.menu_model_selector -> {
                showModelSelector()
                true
            }
            R.id.menu_export -> {
                exportChat()
                true
            }
            R.id.menu_clear -> {
                clearCurrentChat()
                true
            }
            R.id.menu_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ========== 引用管理 ==========
    
    private fun setQuote(text: String) {
        selectedQuote = text
        binding.layoutQuote.visibility = View.VISIBLE
        binding.tvQuoteText.text = text.take(50) + if (text.length > 50) "..." else ""
        
        // 设置引用框背景色为主题强调色半透明（30%透明度）
        val accentColor = ThemeStore.accentColor(this)
        val semiTransparentAccent = android.graphics.Color.argb(
            77, // 30% 透明度 (255 * 0.3 ≈ 77)
            android.graphics.Color.red(accentColor),
            android.graphics.Color.green(accentColor),
            android.graphics.Color.blue(accentColor)
        )
        val gradientDrawable = android.graphics.drawable.GradientDrawable()
        gradientDrawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        gradientDrawable.cornerRadius = 12f.dpToPx().toFloat()
        gradientDrawable.setColor(semiTransparentAccent)
        binding.layoutQuote.background = gradientDrawable
        
        updateQuickActionBar(hasSelectedText = true)
    }

    private fun clearQuote() {
        selectedQuote = null
        binding.layoutQuote.visibility = View.GONE
        updateQuickActionBar(hasSelectedText = false)
    }

    // ========== 模型选择器 ==========
    
    private var modelSelectorTvCurrent: TextView? = null

    private fun showModelSelector() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_model_selector, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        modelSelectorTvCurrent = dialogView.findViewById<TextView>(R.id.tv_current_model)
        val recyclerModels = dialogView.findViewById<RecyclerView>(R.id.recycler_models)
        
        modelSelectorTvCurrent?.text = currentModel

        recyclerModels.layoutManager = LinearLayoutManager(this)
        
        lifecycleScope.launch {
            val provider = aiService.getCurrentProvider()
            if (provider != null) {
                // 从Provider中获取已保存的模型列表
                val savedModels = provider.parseAvailableModels()
                val models = if (savedModels.isNotEmpty()) {
                    savedModels
                } else {
                    // 如果没有保存的模型列表，使用当前配置的模型
                    listOf(provider.model)
                }
                
                recyclerModels.adapter = object : RecyclerView.Adapter<ModelViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
                        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                        return ModelViewHolder(view)
                    }
                    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
                        holder.bind(models[position])
                    }
                    override fun getItemCount() = models.size
                }
                
                // 添加提示信息
                val hint = if (savedModels.isNotEmpty()) {
                    "$currentModel (共${savedModels.size}个模型)"
                } else {
                    "$currentModel (当前配置)"
                }
                modelSelectorTvCurrent?.text = hint
            } else {
                recyclerModels.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                        val textView = TextView(this@AiChatActivity).apply {
                            text = "请先在设置中配置AI服务商"
                            setPadding(32, 32, 32, 32)
                        }
                        return object : RecyclerView.ViewHolder(textView) {}
                    }
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                    override fun getItemCount() = 1
                }
            }
        }

        dialog.show()
    }

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)
        
        fun bind(model: String) {
            textView.text = model
            textView.setOnClickListener {
                currentModel = model
                modelSelectorTvCurrent?.text = model
                Toast.makeText(this@AiChatActivity, "已选择模型: $model", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========== 会话历史侧边栏 ==========

    private fun showHistorySidebar() {
        val sidebarView = layoutInflater.inflate(R.layout.layout_history_sidebar, null)
        val drawerLayout = androidx.drawerlayout.widget.DrawerLayout(this)

        val dialog = AlertDialog.Builder(this)
            .setView(sidebarView)
            .create()

        val btnClose = sidebarView.findViewById<ImageButton>(R.id.btn_close)
        val recyclerHistory = sidebarView.findViewById<RecyclerView>(R.id.recycler_history)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // 保存对话框引用
        historyDialog = dialog
        
        // 加载历史会话列表
        lifecycleScope.launch {
            val sessions = AiHistoryStore.readHistory()
            if (sessions.isEmpty()) {
                recyclerHistory.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                        val textView = TextView(this@AiChatActivity).apply {
                            text = "暂无历史记录"
                            setPadding(32, 32, 32, 32)
                            gravity = android.view.Gravity.CENTER
                        }
                        return object : RecyclerView.ViewHolder(textView) {}
                    }
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                    override fun getItemCount() = 1
                }
            } else {
                recyclerHistory.layoutManager = LinearLayoutManager(this@AiChatActivity)
                recyclerHistory.adapter = object : RecyclerView.Adapter<HistoryViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
                        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                        return HistoryViewHolder(view)
                    }
                    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
                        holder.bind(sessions[position])
                    }
                    override fun getItemCount() = sessions.size
                }
            }
        }

        dialog.show()
    }
    
    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)
        
        fun bind(session: AiChatSession) {
            // 获取第一条用户消息作为标题
            val firstUserMessage = session.messages.firstOrNull { it.type == "human" }?.content ?: "空对话"
            val title = if (firstUserMessage.length > 30) {
                firstUserMessage.take(30) + "..."
            } else {
                firstUserMessage
            }
            
            // 格式化时间
            val timeStr = formatTimestamp(session.updatedAt)
            textView.text = "$title\n$timeStr"
            textView.setPadding(32, 24, 32, 24)
            
            itemView.setOnClickListener {
                loadSession(session)
                // 关闭历史对话框
                historyDialog?.dismiss()
                historyDialog = null
            }
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            else -> {
                val date = java.util.Date(timestamp)
                android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", date).toString()
            }
        }
    }

    /**
     * 取消当前请求 - 完整实现
     */
    private fun cancelCurrentRequest() {
        if (isRequestActive) {
            aiService.cancelCurrentRequest()

            // 更新最后一条AI消息
            val lastMsg = messages.lastOrNull()
            if (lastMsg?.role == "ai" && lastMsg.content.isEmpty()) {
                messages[messages.size - 1] = ChatMessageItem(
                    "ai", 
                    "[请求已取消]",
                    reasoningContent = lastMsg.reasoningContent,
                    toolSteps = lastMsg.toolSteps
                )
                adapter.notifyItemChanged(messages.size - 1)
            }

            setRequestState(false)
            Toast.makeText(this, "请求已取消", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新请求状态UI
     */
    private fun setRequestState(active: Boolean) {
        isRequestActive = active
        if (active) {
            // 生成中：显示正方形图标，点击取消
            binding.btnSend.setImageResource(R.drawable.ic_stop)
            binding.btnSend.contentDescription = "取消"
            binding.editText.isEnabled = false
        } else {
            // 空闲：显示纸飞机图标，点击发送
            binding.btnSend.setImageResource(R.drawable.ic_send)
            binding.btnSend.contentDescription = "发送"
            binding.editText.isEnabled = true
        }
    }

    private fun setupSuggestChips() {
        lifecycleScope.launch {
            // 从配置管理器加载空状态配置
            val config = io.legado.app.help.ai.AiAssistantConfigManager.getEmptyStateConfig(this@AiChatActivity)
            val skillManager = io.legado.app.help.ai.SkillManager(this@AiChatActivity)
            val allSkills = skillManager.getAllSkills()
            
            val chipViews = listOf(
                binding.chipSuggest1,
                binding.chipSuggest2,
                binding.chipSuggest3,
                binding.chipSuggest4
            )

            // 隐藏所有chip
            chipViews.forEach { it.visibility = View.GONE }

            // 根据配置显示（最多4个）
            config.take(4).forEachIndexed { index, item ->
                chipViews[index].apply {
                    val displayText = when (item.type) {
                        io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.SKILL -> {
                            // 从Skill获取显示文本
                            val skill = allSkills.find { it.id == item.skillId }
                            skill?.triggerWord?.ifBlank { skill.name } ?: "未知"
                        }
                        io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.CUSTOM -> {
                            // 使用自定义触发词
                            item.customTrigger ?: item.customName ?: ""
                        }
                    }
                    
                    text = displayText
                    visibility = View.VISIBLE
                    setOnClickListener {
                        when (item.type) {
                            io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.SKILL -> {
                                executeSkillDirectly(item.skillId!!, displayText)
                            }
                            io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.CUSTOM -> {
                                // 自定义类型：填充到输入框并发送
                                binding.editText.setText(displayText)
                                sendMessage(displayText)
                            }
                        }
                    }
                }
            }
            
            // 初始化快捷操作栏配置（默认5个）
            initQuickActionBar()
        }
    }
    
    /**
     * 初始化快捷操作栏 - 固定5个位置
     * 根据是否有选中文字显示不同的操作
     */
    private fun initQuickActionBar() {
        // 默认配置：无选中文字时的通用操作
        updateQuickActionBar(hasSelectedText = false)
    }
    
    /**
     * 更新快捷操作栏内容
     * @param hasSelectedText 是否有选中的文字
     */
    private fun updateQuickActionBar(hasSelectedText: Boolean) {
        // 从配置管理器加载快捷操作配置（根据是否有选中文字）
        val config = io.legado.app.help.ai.AiAssistantConfigManager.getQuickActionBarConfig(this, hasSelectedText)
        val skillManager = io.legado.app.help.ai.SkillManager(this)
        
        lifecycleScope.launch {
            val allSkills = skillManager.getAllSkills()
            
            quickActionItems = config.map { item ->
                when (item.type) {
                    io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.SKILL -> {
                        // 从Skill获取显示文本和触发词
                        val skill = allSkills.find { it.id == item.skillId }
                        QuickActionItem(
                            displayName = skill?.name ?: "未知",
                            triggerWord = skill?.triggerWord ?: "",
                            type = io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.SKILL,
                            skillId = item.skillId
                        )
                    }
                    io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.CUSTOM -> {
                        // 使用自定义配置
                        QuickActionItem(
                            displayName = item.displayName ?: "",
                            triggerWord = item.triggerWord ?: "",
                            type = io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.CUSTOM
                        )
                    }
                }
            }
            
            // 渲染快捷操作栏
            renderQuickActionBar()
        }
    }
    
    /**
     * 渲染快捷操作栏到UI - 固定4个位置
     */
    private fun renderQuickActionBar() {
        val chipViews = listOf(
            binding.chipQuickAction1,
            binding.chipQuickAction2,
            binding.chipQuickAction3,
            binding.chipQuickAction4
        )
        
        // 隐藏所有chip
        chipViews.forEach { it.visibility = View.GONE }
        
        if (quickActionItems.isEmpty()) {
            binding.quickPromptsLayout.visibility = View.GONE
            return
        }
        
        binding.quickPromptsLayout.visibility = View.VISIBLE
        
        // 动态显示技能（最多4个）
        quickActionItems.take(4).forEachIndexed { index, item ->
            chipViews[index].apply {
                text = item.displayName
                visibility = View.VISIBLE
                
                // 强制设置主题色背景和边框（确保生效）
                val backgroundCardColor = ThemeStore.backgroundCard(this@AiChatActivity)
                val dividerColor = ThemeStore.dividerColor(this@AiChatActivity)
                android.util.Log.d("AiChatActivity", "快捷操作按钮 - backgroundCard: ${String.format("#%06X", 0xFFFFFF and backgroundCardColor)}, divider: ${String.format("#%06X", 0xFFFFFF and dividerColor)}")
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                drawable.cornerRadius = 12.dpToPx().toFloat()
                drawable.setColor(backgroundCardColor)
                drawable.setStroke(1.dpToPx(), dividerColor)
                background = drawable
                
                setOnClickListener {
                    when (item.type) {
                        io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.SKILL -> {
                            // Skill类型：直接执行
                            item.skillId?.let { skillId ->
                                executeSkillDirectly(skillId, item.triggerWord)
                            }
                        }
                        io.legado.app.help.ai.AiAssistantConfigManager.ConfigType.CUSTOM -> {
                            // 自定义类型：填充触发词到输入框，不自动发送
                            binding.editText.setText(item.triggerWord)
                            binding.editText.setSelection(item.triggerWord.length)
                            binding.editText.requestFocus()
                        }
                    }
                }
            }
        }
    }

    private fun setupOptionSwitches() {
        binding.layoutDeepThinking.setOnClickListener {
            deepThinkingEnabled = !deepThinkingEnabled
            updateOptionStyle(binding.layoutDeepThinking, deepThinkingEnabled)
        }

        binding.layoutSpoilerFree.setOnClickListener {
            spoilerFreeEnabled = !spoilerFreeEnabled
            updateOptionStyle(binding.layoutSpoilerFree, spoilerFreeEnabled)
        }
    }

    private fun updateOptionStyle(view: View, enabled: Boolean) {
        val context = view.context

        val gradientDrawable = android.graphics.drawable.GradientDrawable()
        gradientDrawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        gradientDrawable.cornerRadius = 999f.dpToPx().toFloat()

        if (enabled) {
            // 激活状态：使用主题强调色填充，同时保留边框
            val accentColor = ThemeStore.accentColor(context)
            gradientDrawable.setColor(accentColor)
            gradientDrawable.setStroke(1.dpToPx(), accentColor)
            
            // 更新图标和文字颜色为白色（在强调色背景上）
            val linearLayout = view as? LinearLayout
            linearLayout?.let {
                for (i in 0 until it.childCount) {
                    val child = it.getChildAt(i)
                    when (child) {
                        is ImageView -> {
                            child.setColorFilter(Color.WHITE)
                        }
                        is TextView -> {
                            child.setTextColor(Color.WHITE)
                        }
                    }
                }
            }
        } else {
            // 未激活状态：透明背景，只有边框
            gradientDrawable.setColor(Color.TRANSPARENT)
            val borderColor = ThemeStore.textColorSecondary(context)
            gradientDrawable.setStroke(1.dpToPx(), borderColor)
            
            // 恢复图标和文字颜色
            val linearLayout = view as? LinearLayout
            linearLayout?.let {
                for (i in 0 until it.childCount) {
                    val child = it.getChildAt(i)
                    when (child) {
                        is ImageView -> {
                            child.clearColorFilter()
                        }
                        is TextView -> {
                            child.setTextColor(borderColor)
                        }
                    }
                }
            }
        }
        view.background = gradientDrawable
    }

    private fun showMessageOptions(message: ChatMessageItem) {
        val options = arrayOf("复制", "转发到其他应用", "删除")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyToClipboard(message.content)
                    1 -> shareMessage(message.content)
                    2 -> deleteMessage(message)
                }
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI回复", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun shareMessage(content: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(shareIntent, "分享到"))
    }

    private fun deleteMessage(message: ChatMessageItem) {
        messages.remove(message)
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun showHistoryDialog() {
        lifecycleScope.launch {
            val sessions = AiHistoryStore.readHistory()
            if (sessions.isEmpty()) {
                Toast.makeText(this@AiChatActivity, "暂无历史记录", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val sessionNames = sessions.map { it.id.take(8) }.toTypedArray()
            AlertDialog.Builder(this@AiChatActivity)
                .setTitle("历史记录")
                .setItems(sessionNames) { _, which ->
                    loadSession(sessions[which])
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadSession(session: AiChatSession) {
        // 设置当前会话
        currentSession = session
        
        // 清空并加载消息
        messages.clear()
        session.messages.forEach { msg ->
            messages.add(ChatMessageItem(
                role = if (msg.type == "human") "user" else "ai", 
                content = msg.content,
                reasoningContent = "",
                toolSteps = emptyList(),
                isExpanded = true,  // 历史对话也默认展开
                isReasoningExpanded = false
            ))
        }
        
        // 重置流式位置
        streamingPosition = -1
        
        // 重新创建适配器以确保正确显示
        updateAdapter()
        
        // 更新空状态
        updateEmptyState()
        
        // 滚动到底部
        if (messages.isNotEmpty()) {
            binding.recyclerView.scrollToPosition(messages.size - 1)
        }
        
        // 显示提示
        Toast.makeText(this, "已加载历史对话", Toast.LENGTH_SHORT).show()
    }

    private fun initData() {
        aiService = AiService(this)
        promptManager = PromptManager(this)
        skillManager = SkillManager(this)

        lifecycleScope.launch {
            // 先初始化历史存储
            AiHistoryStore.init(this@AiChatActivity)
            
            aiService.init()

            // 传递当前书籍信息给AI服务（如果有）
            if (bookUrl != null) {
                val book = appDb.bookDao.getBook(bookUrl!!)
                val chapter = book?.let {
                    appDb.bookChapterDao.getChapter(it.bookUrl, it.durChapterIndex)
                }
                aiService.setToolContext(book, chapter, chapterContent)
            } else {
                // 如果没有阅读上下文，仍然注册全局Tool（书架查询、阅读历史等）
                // 创建一个空的context，让Tool可以访问数据库
                val emptyContext = AiToolContext(
                    currentBook = null,
                    currentChapter = null,
                    chapterContent = null,
                    bookUrl = "",
                    appDatabase = appDb,
                    appContext = this@AiChatActivity
                )
                AiTools.registerAll(emptyContext)
            }

            // loadQuickPrompts 已经被 initQuickActionBar 替代
            updateEmptyState()
        }
    }

    // loadQuickPrompts 方法已删除，功能由 initQuickActionBar 和 renderQuickActionBar 替代

    override fun onDestroy() {
        super.onDestroy()
        
        // 取消所有正在进行的AI请求
        if (isRequestActive) {
            aiService.cancelCurrentRequest()
        }
        
        // 关闭历史对话框（如果打开）
        historyDialog?.dismiss()
        historyDialog = null
        
        // 清理资源
        messages.clear()
        currentSession = null
        
        android.util.Log.d("AiChatActivity", "onDestroy: 资源已清理")
    }
    
    override fun onStop() {
        super.onStop()
        
        // 页面不可见时，不自动取消请求（让用户手动控制）
        // 如果希望后台继续生成，不要在这里取消
        // if (isRequestActive) {
        //     aiService.cancelCurrentRequest()
        //     setRequestState(false)
        // }
        
        android.util.Log.d("AiChatActivity", "onStop: 请求状态=${isRequestActive}")
    }
    
    override fun onPause() {
        super.onPause()
        
        // 页面暂停时不做特殊处理（会话已在发送消息时保存）
        
        android.util.Log.d("AiChatActivity", "onPause: 消息数=${messages.size}")
    }
    
    override fun onResume() {
        super.onResume()
        
        // 页面恢复时，重新加载会话（如果需要）
        android.util.Log.d("AiChatActivity", "onResume")
    }

    private fun executeSkillDirectly(skillId: String, userQuestion: String) {
        lifecycleScope.launch {
            val skill = skillManager.getSkill(skillId) ?: return@launch

            // 构建消息
            val messages = buildString {
                append(userQuestion)
                if (!selectedQuote.isNullOrBlank()) {
                    append("：")
                    append(selectedQuote)
                }
            }

            this@AiChatActivity.messages.add(ChatMessageItem("user", messages))
            this@AiChatActivity.messages.add(ChatMessageItem("ai", ""))
            
            // 设置流式位置
            streamingPosition = this@AiChatActivity.messages.size - 1
            updateAdapter()
            
            updateEmptyState()
            
            // 滚动到底部
            binding.recyclerView.post {
                binding.recyclerView.smoothScrollToPosition(this@AiChatActivity.messages.size - 1)
            }

            // 构建变量 - 优先从 ReadingContextService 获取实时上下文
            val readingContext = ReadingContextService.getContext()
            
            // 如果 ReadingContextService 有实时数据，使用它；否则回退到 Activity 参数
            val realBookTitle = readingContext?.bookTitle?.takeIf { it.isNotBlank() } ?: bookTitle ?: ""
            val realAuthor = readingContext?.author?.takeIf { it.isNotBlank() } ?: author ?: ""
            val realChapterTitle = readingContext?.currentChapter?.title?.takeIf { it.isNotBlank() } ?: chapterTitle ?: ""
            val realChapterContent: String = readingContext?.surroundingText?.takeIf { it.isNotBlank() } ?: chapterContent ?: ""
            
            // 获取书籍简介（从数据库）
            val bookIntro = try {
                val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() }
                if (!bookUrl.isNullOrBlank()) {
                    appDb.bookDao.getBook(bookUrl)?.intro ?: ""
                } else ""
            } catch (e: Exception) {
                ""
            }
            
            // 获取前情内容（真正的前文，不是当前章节）
            val previousContent = try {
                val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() }
                if (!bookUrl.isNullOrBlank()) {
                    val currentChapterIndex = readingContext.currentChapter?.index ?: 0
                    if (currentChapterIndex > 0) {
                        // 获取前一章的内容
                        val prevChapter = appDb.bookChapterDao.getChapter(bookUrl, currentChapterIndex - 1)
                        if (prevChapter != null) {
                            // 从缓存或文件中加载章节内容
                            val book = appDb.bookDao.getBook(bookUrl)
                            if (book != null) {
                                io.legado.app.help.book.BookHelp.getContent(book, prevChapter)?.take(3000) ?: ""
                            } else ""
                        } else ""
                    } else ""
                } else ""
            } catch (e: Exception) {
                io.legado.app.utils.LogUtils.e("AiChatActivity", "获取前情内容失败: ${e.message}")
                ""
            }
            
            val variables = mutableMapOf<String, String>(
                "bookName" to realBookTitle,
                "bookAuthor" to realAuthor,
                "bookIntro" to bookIntro,
                "chapterTitle" to realChapterTitle,
                "selectedText" to (selectedQuote ?: ""),
                "question" to userQuestion,
                "concept" to (selectedQuote ?: ""),
                // 前情回顾需要的变量
                "currentChapter" to realChapterTitle,
                "previousContent" to previousContent
            )

            // 添加章节内容（根据配置截断或完整传递）
            val maxChapterLength = 8000 // 8K字符，应该足够大多数场景
            variables["chapterContent"] = if (realChapterContent.length > maxChapterLength) {
                realChapterContent.take(maxChapterLength) + "\n...[内容已截断]"
            } else {
                realChapterContent
            }
            variables["content"] = variables["chapterContent"] ?: ""

            // 添加上下文文本（用于工具调用）
            variables["contextText"] = selectedQuote ?: realChapterContent.take(2000)
            variables["surroundingText"] = realChapterContent

            aiService.executeSkill(skill, variables).collectLatest { result ->
                when (result) {
                    is ChatResult.Chunk -> {
                        val lastMsg = this@AiChatActivity.messages.lastOrNull()
                        if (lastMsg?.role == "ai") {
                            val newContent = if (lastMsg.content.isEmpty()) {
                                result.content
                            } else {
                                lastMsg.content + result.content
                            }
                            this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1] =
                                ChatMessageItem(
                                    "ai", 
                                    newContent,
                                    reasoningContent = lastMsg.reasoningContent,
                                    toolSteps = lastMsg.toolSteps
                                )
                        } else {
                            this@AiChatActivity.messages.add(ChatMessageItem("ai", result.content))
                        }
                        adapter.notifyItemChanged(this@AiChatActivity.messages.size - 1)
                        binding.recyclerView.scrollToPosition(this@AiChatActivity.messages.size - 1)
                    }
                    is ChatResult.ReasoningChunk -> {
                        // 推理过程暂不显示
                    }
                    is ChatResult.ToolCall -> {
                        // 解析并显示工具调用
                        val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                        if (aiMsg != null) {
                            val updatedSteps = aiMsg.toolSteps.toMutableList()
                            // 解析arguments JSON为格式化字符串
                            val formattedArgs = try {
                                val argsJson = org.json.JSONObject(result.arguments)
                                argsJson.toString(2) // 格式化输出
                            } catch (e: Exception) {
                                result.arguments
                            }
                            updatedSteps.add(ToolStep(
                                name = result.name,
                                status = ToolStepStatus.PENDING,
                                input = formattedArgs
                            ))
                            this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                            adapter.notifyItemChanged(streamingPosition)
                            
                            // 滚动到底部
                            binding.recyclerView.post {
                                binding.recyclerView.smoothScrollToPosition(streamingPosition)
                            }
                        }
                    }
                    is ChatResult.ToolStart -> {
                        // 工具开始执行
                        val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                        if (aiMsg != null) {
                            val updatedSteps = aiMsg.toolSteps.toMutableList()
                            val existingIndex = updatedSteps.indexOfFirst { it.name == result.name }
                            if (existingIndex >= 0) {
                                updatedSteps[existingIndex] = updatedSteps[existingIndex].copy(status = ToolStepStatus.RUNNING)
                                this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                                adapter.notifyItemChanged(streamingPosition)
                            }
                        }
                    }
                    is ChatResult.ToolResult -> {
                        // 工具执行完成
                        val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                        if (aiMsg != null) {
                            val updatedSteps = aiMsg.toolSteps.toMutableList()
                            val existingIndex = updatedSteps.indexOfFirst { it.name == result.name }
                            if (existingIndex >= 0) {
                                updatedSteps[existingIndex] = updatedSteps[existingIndex].copy(
                                    status = ToolStepStatus.SUCCESS,
                                    output = result.result
                                )
                                this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                                adapter.notifyItemChanged(streamingPosition)
                            }
                        }
                    }
                    is ChatResult.ToolStepUpdate -> {
                        val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                        if (aiMsg != null) {
                            val updatedSteps = aiMsg.toolSteps.toMutableList()
                            val existingIndex = updatedSteps.indexOfFirst { it.name == result.step.name }
                            if (existingIndex >= 0) {
                                updatedSteps[existingIndex] = result.step
                            } else {
                                updatedSteps.add(result.step)
                            }
                            this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                            adapter.notifyItemChanged(streamingPosition)
                            
                            // 滚动到底部
                            binding.recyclerView.post {
                                binding.recyclerView.smoothScrollToPosition(streamingPosition)
                            }
                        }
                    }
                    is ChatResult.Success -> {
                        if (this@AiChatActivity.messages.lastOrNull()?.role == "ai") {
                            val existingMsg = this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1]
                            this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1] =
                                ChatMessageItem(
                                    "ai", 
                                    result.content,
                                    reasoningContent = existingMsg.reasoningContent,
                                    toolSteps = existingMsg.toolSteps
                                )
                        } else {
                            this@AiChatActivity.messages.add(ChatMessageItem("ai", result.content))
                        }
                        adapter.notifyDataSetChanged()
                        
                        // 参考 anx/readany：滚动到底部
                        binding.recyclerView.post {
                            binding.recyclerView.smoothScrollToPosition(this@AiChatActivity.messages.size - 1)
                        }
                        
                        // 关键：成功后重置请求状态和流式位置，按钮变回纸飞机
                        streamingPosition = -1
                        setRequestState(false)
                        
                        // 保存到历史
                        currentSession?.let { session ->
                            val updatedMessages = session.messages.toMutableList()
                            updatedMessages.add(ChatMessage("human", userQuestion))
                            updatedMessages.add(ChatMessage("ai", result.content))
                            val updatedSession = session.copy(
                                messages = updatedMessages,
                                model = currentModel,
                                updatedAt = System.currentTimeMillis(),
                                completed = true
                            )
                            lifecycleScope.launch {
                                AiHistoryStore.upsertSession(updatedSession)
                            }
                            currentSession = updatedSession
                        }
                    }
                    is ChatResult.Error -> {
                        if (this@AiChatActivity.messages.lastOrNull()?.role == "ai") {
                            val existingMsg = this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1]
                            this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1] =
                                ChatMessageItem(
                                    "ai", 
                                    "错误：${result.message}",
                                    reasoningContent = existingMsg.reasoningContent,
                                    toolSteps = existingMsg.toolSteps
                                )
                        } else {
                            this@AiChatActivity.messages.add(ChatMessageItem("ai", "错误：${result.message}"))
                        }
                        adapter.notifyDataSetChanged()
                        
                        // 保存错误信息到历史
                        currentSession?.let { session ->
                            val updatedMessages = session.messages.toMutableList()
                            // 注意：用户消息已经在 saveDraftSession 中添加过，这里只添加 AI 错误回复
                            updatedMessages.add(ChatMessage("ai", "错误：${result.message}"))
                            val updatedSession = session.copy(
                                messages = updatedMessages,
                                model = currentModel,
                                updatedAt = System.currentTimeMillis(),
                                completed = true  // 标记为完成（虽然是错误）
                            )
                            lifecycleScope.launch {
                                AiHistoryStore.upsertSession(updatedSession)
                            }
                            currentSession = updatedSession
                        }
                    }
                }
            }
        }
    }

    /**
     * 重新生成最后一条 AI 消息
     */
    private fun regenerateLastMessage() {
        if (isRequestActive) {
            Toast.makeText(this, "请等待当前请求完成", Toast.LENGTH_SHORT).show()
            return
        }

        // 找到最后一条用户消息
        val lastUserMessageIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserMessageIndex == -1) {
            Toast.makeText(this, "没有可重新生成的消息", Toast.LENGTH_SHORT).show()
            return
        }

        val userMessage = messages[lastUserMessageIndex]
        
        // 删除最后一条 AI 消息（如果存在）
        if (messages.size > lastUserMessageIndex + 1 && messages[lastUserMessageIndex + 1].role == "ai") {
            messages.removeAt(lastUserMessageIndex + 1)
        }
        
        // 重新发送用户消息
        sendMessage(userMessage.content, isRegenerate = true)
    }

    private fun sendMessage(content: String, isRegenerate: Boolean = false) {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "AiChat",
            "用户发送消息: length=${content.length}, isRegenerate=$isRegenerate"
        )
        
        // 如果有引用，以紧凑格式添加到消息中
        val fullMessage = buildString {
            append(content)
            if (!selectedQuote.isNullOrBlank()) {
                append("：")
                append(selectedQuote)
            }
        }

        messages.add(ChatMessageItem("user", fullMessage))
        messages.add(ChatMessageItem("ai", ""))

        // 设置流式位置
        streamingPosition = messages.size - 1
        updateAdapter()

        updateEmptyState()

        // 滚动到底部
        binding.recyclerView.post {
            binding.recyclerView.smoothScrollToPosition(messages.size - 1)
        }
        
        binding.editText.setText("")

        // 清除引用
        clearQuote()

        // 设置请求状态为活跃
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "AiChat",
            "设置请求状态为活跃，切换为停止图标"
        )
        setRequestState(true)
        
        // 立即保存草稿会话（参考 anx53）
        saveDraftSession(content)

        lifecycleScope.launch {
            // 不再每次都调用init()，LangChain4j使用无状态模式
            // init()只在Activity初始化或服务商配置变化时调用

            val messageWithOptions = buildString {
                append(content)
                if (spoilerFreeEnabled) {
                    append("\n\n[请注意：不要剧透后续情节]")
                }
                if (deepThinkingEnabled) {
                    append("\n\n[请深入思考后回答]")
                }
            }

            // 强制使用LangChain4j（参考anx/readany的实现）
            aiService.chat(messageWithOptions, currentSession).collectLatest { result ->
                    when (result) {
                        is ChatResult.Chunk -> {
                            // 检查 streamingPosition 是否有效
                            if (streamingPosition >= 0 && streamingPosition < messages.size) {
                                val existingMsg = messages[streamingPosition]
                                val newContent = if (existingMsg.content.isEmpty()) {
                                    result.content
                                } else {
                                    existingMsg.content + result.content
                                }
                                messages[streamingPosition] = ChatMessageItem(
                                    "ai", 
                                    newContent,
                                    reasoningContent = existingMsg.reasoningContent,
                                    toolSteps = existingMsg.toolSteps
                                )
                                adapter.notifyItemChanged(streamingPosition)
                                
                                // 滚动到底部
                                binding.recyclerView.post {
                                    binding.recyclerView.smoothScrollToPosition(messages.size - 1)
                                }
                                
                                // 每收到50个字符保存一次进度
                                if (messages[streamingPosition].content.length % 50 == 0) {
                                    saveStreamingProgress()
                                }
                            }
                        }
                        is ChatResult.ToolCall -> {
                            // 工具调用开始
                            val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                            if (aiMsg != null) {
                                val updatedSteps = aiMsg.toolSteps.toMutableList()
                                updatedSteps.add(ToolStep(
                                    name = result.name,
                                    status = ToolStepStatus.PENDING,
                                    input = result.arguments
                                ))
                                this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                                adapter.notifyItemChanged(streamingPosition)
                                
                                // 滚动到底部
                                binding.recyclerView.post {
                                    binding.recyclerView.smoothScrollToPosition(streamingPosition)
                                }
                            }
                        }
                        is ChatResult.ToolStart -> {
                            // 工具开始执行
                            val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                            if (aiMsg != null) {
                                val updatedSteps = aiMsg.toolSteps.toMutableList()
                                val existingIndex = updatedSteps.indexOfFirst { it.name == result.name }
                                if (existingIndex >= 0) {
                                    updatedSteps[existingIndex] = updatedSteps[existingIndex].copy(status = ToolStepStatus.RUNNING)
                                    this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                                    adapter.notifyItemChanged(streamingPosition)
                                }
                            }
                        }
                        is ChatResult.ToolResult -> {
                            // 工具执行完成
                            val aiMsg = this@AiChatActivity.messages.getOrNull(streamingPosition)
                            if (aiMsg != null) {
                                val updatedSteps = aiMsg.toolSteps.toMutableList()
                                val existingIndex = updatedSteps.indexOfFirst { it.name == result.name }
                                if (existingIndex >= 0) {
                                    updatedSteps[existingIndex] = updatedSteps[existingIndex].copy(
                                        status = ToolStepStatus.SUCCESS,
                                        output = result.result
                                    )
                                    this@AiChatActivity.messages[streamingPosition] = aiMsg.copy(toolSteps = updatedSteps)
                                    adapter.notifyItemChanged(streamingPosition)
                                }
                            }
                        }
                        is ChatResult.Success -> {
                // 关键：先重置流式位置，再更新UI
                streamingPosition = -1
                
                // AI回复完成，更新最后一条消息
                if (this@AiChatActivity.messages.lastOrNull()?.role == "ai") {
                    val existingMsg = this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1]
                    this@AiChatActivity.messages[this@AiChatActivity.messages.size - 1] =
                        ChatMessageItem(
                            "ai", 
                            result.content,
                            reasoningContent = existingMsg.reasoningContent,
                            toolSteps = existingMsg.toolSteps,
                            isExpanded = true  // 确保默认展开
                        )
                } else {
                    this@AiChatActivity.messages.add(ChatMessageItem("ai", result.content, isExpanded = true))
                }
                adapter.notifyDataSetChanged()
                
                // 滚动到底部
                binding.recyclerView.post {
                    binding.recyclerView.smoothScrollToPosition(this@AiChatActivity.messages.size - 1)
                }
                
                // 重置请求状态，按钮变回纸飞机
                setRequestState(false)
                
                // 保存到历史（用户消息已经在saveDraftSession中添加过了）
                currentSession?.let { session ->
                    val updatedMessages = session.messages.toMutableList()
                    // 移除可能重复添加的AI消息（如果有的话）
                    if (updatedMessages.isNotEmpty() && updatedMessages.last().type == "ai") {
                        updatedMessages.removeAt(updatedMessages.size - 1)
                    }
                    updatedMessages.add(ChatMessage("ai", result.content))
                    val updatedSession = session.copy(
                        messages = updatedMessages,
                        model = currentModel,
                        updatedAt = System.currentTimeMillis(),
                        completed = true
                    )
                    lifecycleScope.launch {
                        AiHistoryStore.upsertSession(updatedSession)
                    }
                    currentSession = updatedSession
                }
            }
                        is ChatResult.Error -> {
                            io.legado.app.help.ai.AiLogManager.log(
                                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                                "AiChat",
                                "LangChain4j错误: ${result.message}"
                            )
                            val existingMsg = messages[streamingPosition]
                            messages[streamingPosition] = ChatMessageItem(
                                "ai", 
                                "抱歉，处理请求时出错: ${result.message}",
                                reasoningContent = existingMsg.reasoningContent,
                                toolSteps = existingMsg.toolSteps
                            )
                            adapter.notifyItemChanged(streamingPosition)
                            
                            // 清除流式位置
                            streamingPosition = -1
                            updateAdapter()
                            
                            // 关键：错误时必须重置请求状态，否则按钮会一直是正方形
                            setRequestState(false)
                            
                            // 保存错误信息到历史
                            currentSession?.let { session ->
                                val updatedMessages = session.messages.toMutableList()
                                // 注意：用户消息已经在 saveDraftSession 中添加过，这里只添加 AI 错误回复
                                updatedMessages.add(ChatMessage("ai", "抱歉，处理请求时出错: ${result.message}"))
                                val updatedSession = session.copy(
                                    messages = updatedMessages,
                                    model = currentModel,
                                    updatedAt = System.currentTimeMillis(),
                                    completed = true  // 标记为完成（虽然是错误）
                                )
                                lifecycleScope.launch {
                                    AiHistoryStore.upsertSession(updatedSession)
                                }
                                currentSession = updatedSession
                            }
                        }
                        else -> {
                            // 忽略其他类型
                        }
                    }
                }
        }
    }

    private fun updateEmptyState() {
        if (messages.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 保存草稿会话（在发送消息时立即调用）
     * 参考 anx53 的实现：先保存草稿，再流式输出
     */
    private fun saveDraftSession(userMessage: String) {
        currentSession?.let { session ->
            val updatedMessages = session.messages.toMutableList()
            updatedMessages.add(ChatMessage("human", userMessage))
            val draftSession = session.copy(
                messages = updatedMessages,
                model = currentModel,
                updatedAt = System.currentTimeMillis(),
                completed = false
            )
            lifecycleScope.launch {
                AiHistoryStore.upsertSession(draftSession)
            }
            currentSession = draftSession
        }
    }
    
    /**
     * 保存流式输出进度（定期调用）
     */
    private fun saveStreamingProgress() {
        currentSession?.let { session ->
            val aiContent = messages.getOrNull(streamingPosition)?.content ?: ""
            if (aiContent.isEmpty()) return
            
            val updatedMessages = session.messages.toMutableList()
            // 移除最后一条 AI 消息（如果存在），然后添加最新的
            if (updatedMessages.isNotEmpty() && updatedMessages.last().type == "ai") {
                updatedMessages.removeAt(updatedMessages.size - 1)
            }
            updatedMessages.add(ChatMessage("ai", aiContent))
            
            val progressSession = session.copy(
                messages = updatedMessages,
                model = currentModel,
                updatedAt = System.currentTimeMillis(),
                completed = false
            )
            lifecycleScope.launch {
                AiHistoryStore.upsertSession(progressSession)
            }
            currentSession = progressSession
        }
    }

    /**
     * 创建新会话
     */
    private fun createNewSession() {
        currentSession = AiChatSession(
            id = "session_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}",
            serviceId = "default",
            model = currentModel,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = emptyList(),
            completed = false
        )
    }

    /**
     * 导出对话
     */
    private fun exportChat() {
        if (messages.isEmpty()) {
            Toast.makeText(this, "没有可导出的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("导出为文本", "导出为JSON")
        AlertDialog.Builder(this)
            .setTitle("导出对话")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAsText()
                    1 -> exportAsJson()
                }
            }
            .show()
    }

    private fun exportAsText() {
        val content = buildString {
            messages.forEach { msg ->
                append(if (msg.role == "user") "你: " else "AI: ")
                append(msg.content)
                append("\n\n")
            }
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI对话", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun exportAsJson() {
        val jsonMessages = messages.map { msg ->
            mapOf("role" to msg.role, "content" to msg.content)
        }
        val jsonArray = JSONArray()
        jsonMessages.forEach { msg ->
            jsonArray.put(JSONObject(msg))
        }
        val jsonString = jsonArray.toString()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI对话JSON", jsonString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "JSON已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    /**
     * 清除当前对话
     */
    private fun clearCurrentChat() {
        if (messages.isEmpty()) {
            Toast.makeText(this, "当前没有对话", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("清除对话")
            .setMessage("确定要清除当前对话吗？")
            .setPositiveButton("清除") { _, _ ->
                messages.clear()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开设置
     */
    private fun openSettings() {
        val intent = Intent(this, ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_SETTINGS)
        }
        startActivity(intent)
    }
}

/**
 * 聊天消息适配器
 */
class ChatAdapter(
    private val context: Context,
    private val messages: List<ChatMessageItem>,
    private val onItemLongClick: (ChatMessageItem, Boolean) -> Unit,
    private val onCopyClick: ((String) -> Unit)? = null,
    private val onRegenerateClick: (() -> Unit)? = null,
    private val isStreamingPosition: Int = -1 // 当前流式输出的消息位置
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentText: TextView = view.findViewById(R.id.tv_content)
        val tvCursor: TextView = view.findViewById(R.id.tv_cursor)
        val viewGradientMask: View = view.findViewById(R.id.view_gradient_mask)
        val btnExpandCollapse: ImageButton = view.findViewById(R.id.btn_expand_collapse)
        val layoutAiActions: LinearLayout = view.findViewById(R.id.layout_ai_actions)
        val btnCopy: ImageButton = view.findViewById(R.id.btn_copy)
        val btnRegenerate: ImageButton = view.findViewById(R.id.btn_regenerate)
        
        // 推理过程相关视图
        val layoutReasoning: LinearLayout = view.findViewById(R.id.layout_reasoning)
        val btnToggleReasoning: LinearLayout = view.findViewById(R.id.btn_toggle_reasoning)
        val tvReasoningContent: TextView = view.findViewById(R.id.tv_reasoning_content)
        val ivExpandIndicator: ImageView = view.findViewById(R.id.iv_expand_indicator)
        
        // 工具步骤相关视图
        val layoutToolSteps: LinearLayout = view.findViewById(R.id.layout_tool_steps)
        val toolStepsContainer: LinearLayout = view.findViewById(R.id.tool_steps_container)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        
        // 获取主题颜色（在方法开头定义，供AI和用户消息分支使用）
        val textColorPrimary = ThemeStore.textColorPrimary(context)
        
        // 复制功能
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI回复", text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // AI 消息使用 Markdown 渲染，用户消息使用纯文本
        if (message.role == "ai") {
            // AI 消息：先重置所有视图状态，防止复用问题
            holder.itemView.setBackgroundResource(0)
            holder.contentText.background = null
            holder.contentText.setTextColor(textColorPrimary)
            holder.contentText.maxLines = Int.MAX_VALUE
            holder.viewGradientMask.visibility = View.GONE
            holder.btnExpandCollapse.visibility = View.GONE
            holder.tvCursor.visibility = View.GONE
            holder.layoutReasoning.visibility = View.GONE
            holder.layoutToolSteps.visibility = View.GONE
            holder.layoutAiActions.visibility = View.GONE
            holder.btnRegenerate.visibility = View.GONE
            
            // AI 消息：设置固定的约束参数
            val contentParams = holder.contentText.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            contentParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            contentParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            contentParams.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            contentParams.topToBottom = R.id.layout_tool_steps
            contentParams.marginStart = 16.dpToPx()
            contentParams.marginEnd = 16.dpToPx()
            contentParams.marginTop = 0
            contentParams.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            contentParams.matchConstraintMinWidth = 0
            holder.contentText.layoutParams = contentParams
            
            // 渲染 Markdown
            MarkdownUtils.setMarkdown(holder.contentText, message.content)
            
            // 流式输出时显示光标动画
            val isStreaming = position == isStreamingPosition
            if (isStreaming && message.content.isEmpty()) {
                holder.tvCursor.visibility = View.VISIBLE
                holder.tvCursor.startAnimation(
                    android.view.animation.AnimationUtils.loadAnimation(
                        context,
                        R.anim.cursor_blink
                    )
                )
            } else {
                holder.tvCursor.visibility = View.GONE
                holder.tvCursor.clearAnimation()
            }
            
            // 显示推理过程（如果有）
            if (message.reasoningContent.isNotEmpty()) {
                holder.layoutReasoning.visibility = View.VISIBLE
                holder.tvReasoningContent.text = message.reasoningContent
                
                if (message.isReasoningExpanded) {
                    holder.tvReasoningContent.visibility = View.VISIBLE
                    holder.ivExpandIndicator.rotation = 180f
                } else {
                    holder.tvReasoningContent.visibility = View.GONE
                    holder.ivExpandIndicator.rotation = 0f
                }
                
                holder.btnToggleReasoning.setOnClickListener {
                    message.isReasoningExpanded = !message.isReasoningExpanded
                    notifyItemChanged(position)
                }
            }
            
            // 显示工具步骤（如果有）
            if (message.toolSteps.isNotEmpty()) {
                holder.layoutToolSteps.visibility = View.VISIBLE
                holder.toolStepsContainer.removeAllViews()
                
                val adapterContext = context
                message.toolSteps.forEach { step ->
                    val stepView = android.view.LayoutInflater.from(adapterContext)
                        .inflate(R.layout.item_tool_step, holder.toolStepsContainer, false)
                    
                    val layoutHeader = stepView.findViewById<LinearLayout>(R.id.layout_tool_header)
                    val ivIcon = stepView.findViewById<ImageView>(R.id.iv_tool_icon)
                    val tvName = stepView.findViewById<TextView>(R.id.tv_tool_name)
                    val ivExpand = stepView.findViewById<ImageView>(R.id.iv_expand)
                    val layoutContent = stepView.findViewById<LinearLayout>(R.id.layout_tool_content)
                    val layoutInput = stepView.findViewById<LinearLayout>(R.id.layout_tool_input)
                    val tvInput = stepView.findViewById<TextView>(R.id.tv_tool_input)
                    val layoutOutput = stepView.findViewById<LinearLayout>(R.id.layout_tool_output)
                    val tvOutput = stepView.findViewById<TextView>(R.id.tv_tool_output)
                    val layoutError = stepView.findViewById<LinearLayout>(R.id.layout_tool_error)
                    val tvError = stepView.findViewById<TextView>(R.id.tv_tool_error)
                    
                    tvName.text = step.name
                    
                    when (step.status) {
                        ToolStepStatus.PENDING -> {
                            ivIcon.setImageResource(R.drawable.ic_circle_outline)
                        }
                        ToolStepStatus.RUNNING -> {
                            ivIcon.setImageResource(R.drawable.ic_circle_running)
                        }
                        ToolStepStatus.SUCCESS -> {
                            ivIcon.setImageResource(R.drawable.ic_circle_success)
                        }
                        ToolStepStatus.FAILED -> {
                            ivIcon.setImageResource(R.drawable.ic_circle_failed)
                        }
                    }
                    
                    var isExpanded = step.output != null || step.input != null || step.error != null
                    
                    if (!step.input.isNullOrBlank()) {
                        layoutInput.visibility = View.VISIBLE
                        tvInput.text = step.input
                    } else {
                        layoutInput.visibility = View.GONE
                    }
                    
                    if (!step.output.isNullOrBlank()) {
                        layoutOutput.visibility = View.VISIBLE
                        tvOutput.text = step.output
                    } else {
                        layoutOutput.visibility = View.GONE
                    }
                    
                    if (!step.error.isNullOrBlank()) {
                        layoutError.visibility = View.VISIBLE
                        tvError.text = step.error
                    } else {
                        layoutError.visibility = View.GONE
                    }
                    
                    if (step.output != null || step.input != null || step.error != null) {
                        layoutContent.visibility = View.VISIBLE
                        ivExpand.rotation = 180f
                    } else {
                        layoutContent.visibility = View.GONE
                    }
                    
                    layoutHeader.setOnClickListener {
                        isExpanded = !isExpanded
                        if (isExpanded) {
                            layoutContent.visibility = View.VISIBLE
                            ivExpand.rotation = 180f
                        } else {
                            layoutContent.visibility = View.GONE
                            ivExpand.rotation = 0f
                        }
                    }
                    
                    holder.toolStepsContainer.addView(stepView)
                }
            }
            
            // 长文本折叠功能（超过 300 字符）
            if (message.content.length > 300) {
                holder.btnExpandCollapse.visibility = View.VISIBLE

                if (message.isExpanded) {
                    holder.contentText.maxLines = Int.MAX_VALUE
                    holder.viewGradientMask.visibility = View.GONE
                    holder.btnExpandCollapse.rotation = 180f
                } else {
                    holder.contentText.maxLines = 10
                    holder.viewGradientMask.visibility = View.VISIBLE
                    holder.btnExpandCollapse.rotation = 0f
                }
                
                holder.btnExpandCollapse.setOnClickListener {
                    message.isExpanded = !message.isExpanded
                    // 关键：调用 notifyItemChanged 强制重新绑定数据，防止 RecyclerView 复用导致的布局污染
                    notifyItemChanged(position)
                }
            }
            
            // 显示操作按钮（流式输出时隐藏）
            holder.layoutAiActions.visibility = if (isStreaming) View.GONE else View.VISIBLE
            
            // 复制按钮：所有AI消息都显示
            holder.btnCopy.visibility = if (isStreaming) View.GONE else View.VISIBLE
            
            // 展开/折叠按钮：只在长文本时显示
            if (message.content.length <= 300) {
                holder.btnExpandCollapse.visibility = View.GONE
            }
            
            // 只有最后一条 AI 消息显示“重新生成”按钮
            val isLastAiMessage = position == messages.lastIndex &&
                                  messages.indexOfLast { it.role == "ai" } == position
            holder.btnRegenerate.visibility = if (isLastAiMessage && !isStreaming) View.VISIBLE else View.GONE
            
            holder.btnCopy.setOnClickListener {
                onCopyClick?.invoke(message.content)
            }
            
            holder.btnRegenerate.setOnClickListener {
                onRegenerateClick?.invoke()
            }
            
        } else {
            // 用户消息：先重置所有视图状态，防止复用问题
            holder.itemView.setBackgroundResource(0)
            holder.contentText.maxLines = Int.MAX_VALUE
            holder.viewGradientMask.visibility = View.GONE
            holder.btnExpandCollapse.visibility = View.GONE
            holder.tvCursor.visibility = View.GONE
            holder.layoutReasoning.visibility = View.GONE
            holder.layoutToolSteps.visibility = View.GONE
            holder.layoutAiActions.visibility = View.GONE
            holder.btnRegenerate.visibility = View.GONE
            holder.btnCopy.visibility = View.GONE
            
            // 用户消息：设置内容和样式
            holder.contentText.text = message.content
            
            // 使用主题强调色作为气泡背景（半透明）
            val accentColor = ThemeStore.accentColor(context)
            
            // 创建半透明强调色（60% 不透明度）
            val semiTransparentAccent = android.graphics.Color.argb(
                153, // 60% 不透明度 (255 * 0.6 = 153)
                android.graphics.Color.red(accentColor),
                android.graphics.Color.green(accentColor),
                android.graphics.Color.blue(accentColor)
            )
            
            // 创建气泡背景 - 圆角矩形
            val gradientDrawable = android.graphics.drawable.GradientDrawable()
            gradientDrawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            gradientDrawable.cornerRadius = 16f.dpToPx().toFloat()
            gradientDrawable.setColor(semiTransparentAccent)
            holder.contentText.background = gradientDrawable  // 关键：背景设置在 contentText 上
            
            // 文字颜色为白色（在强调色背景上）
            holder.contentText.setTextColor(android.graphics.Color.WHITE)
            
            // 设置内边距 - 文字距离气泡边缘的距离
            holder.contentText.setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            
            // 用户消息：靠右对齐，重置所有约束参数
            val contentParams = holder.contentText.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            contentParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            contentParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            contentParams.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            contentParams.topToBottom = R.id.layout_tool_steps
            contentParams.marginStart = 0
            contentParams.marginEnd = 16.dpToPx()
            contentParams.marginTop = 0
            
            // 限制最大宽度为 85% - 参考 ReadAny: max-w-[85%]
            val displayMetrics = context.resources.displayMetrics
            val maxWidth = (displayMetrics.widthPixels * 0.85).toInt()
            contentParams.matchConstraintMaxWidth = maxWidth
            contentParams.width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            
            holder.contentText.layoutParams = contentParams
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(message, true)
            true
        }
    }

    override fun getItemCount() = messages.size
}

/**
 * 聊天消息数据类
 */
data class ChatMessageItem(
    val role: String,
    val content: String,
    val reasoningContent: String = "",  // 推理过程内容
    val toolSteps: List<ToolStep> = emptyList(),  // 工具步骤列表
    var isExpanded: Boolean = true,  // 长文本是否展开（默认展开）
    var isReasoningExpanded: Boolean = false  // 推理过程是否展开
)
