package io.legado.app.ui.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.ui.widget.text.AccentTextView
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.AiApiClient
import io.legado.app.help.ai.AiDao
import io.legado.app.help.ai.AiDatabase
import io.legado.app.help.ai.AiProviderEntity
import io.legado.app.help.ai.AiPromptEntity
import io.legado.app.help.ai.AiServiceOptions
import io.legado.app.help.ai.PromptManager
import io.legado.app.help.ai.TestConnectionResult
import io.legado.app.help.ai.rag.EmbeddingService
import io.legado.app.help.ai.rag.VectorConfig
import io.legado.app.help.ai.rag.VectorConfigManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.launch
import splitties.init.appCtx

/**
 * 工具信息数据类
 */
data class ToolInfo(
    val id: String,
    val name: String,
    val description: String
)

class AiSettingsFragment : BaseDialogFragment(R.layout.fragment_ai_settings) {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton

    private lateinit var aiDao: AiDao
    private lateinit var promptManager: PromptManager
    private lateinit var prefs: ReadBookConfig
    private val appPrefs get() = AppConfig

    private var currentTab = 0

    // 判断是否作为独立页面运行（通过ConfigActivity）
    private val isStandalone: Boolean
        get() = activity is ConfigActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_settings, container, false)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        aiDao = AiDatabase.getInstance(requireContext()).aiDao()
        promptManager = PromptManager(requireContext())
        prefs = ReadBookConfig

        initViews()

        // 从intent获取默认Tab
        activity?.intent?.getIntExtra("tab", -1)?.let { tab ->
            if (tab in 0 until tabLayout.tabCount) {
                tabLayout.selectTab(tabLayout.getTabAt(tab))
            }
        }

        loadData()
    }

    private fun initViews() {
        toolbar = view?.findViewById(R.id.toolbar)!!
        tabLayout = view?.findViewById(R.id.tab_layout)!!
        recyclerView = view?.findViewById(R.id.recycler_view)!!
        fabAdd = view?.findViewById(R.id.fab_add)!!

        // 根据运行模式设置导航按钮行为
        if (isStandalone) {
            // 独立页面模式：隐藏返回按钮，使用Activity的标题栏
            toolbar.navigationIcon = null
            // 从intent获取标题
            activity?.intent?.getStringExtra("title")?.let {
                toolbar.title = it
            }
        } else {
            // 对话框模式：显示关闭按钮
            toolbar.setNavigationOnClickListener { dismiss() }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateFabVisibility()
                loadData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        fabAdd.setOnClickListener {
            when (currentTab) {
                0 -> showAddProviderDialog()
                1 -> showAddPromptDialog()
            }
        }
    }

    private fun updateFabVisibility() {
        fabAdd.visibility = if (currentTab == 0 || currentTab == 1) View.VISIBLE else View.GONE
    }

    private fun loadData() {
        lifecycleScope.launch {
            when (currentTab) {
                0 -> loadProviders()
                1 -> loadPrompts()
                2 -> loadGlobalSettings()
                3 -> loadTools()
                4 -> loadCache()
                5 -> loadVectorSettings()
            }
        }
    }

    private suspend fun loadProviders() {
        val providers = aiDao.getAllProviders()
        recyclerView.adapter = ProviderAdapter(providers)
    }

    private suspend fun loadPrompts() {
        val prompts = promptManager.getAllPrompts()
        recyclerView.adapter = PromptAdapter(prompts)
    }

    private fun loadGlobalSettings() {
        val settingsView = layoutInflater.inflate(R.layout.item_ai_global_settings, null)

        val spinnerDisplayMode = settingsView.findViewById<Spinner>(R.id.spinner_display_mode)
        val spinnerPanelPosition = settingsView.findViewById<Spinner>(R.id.spinner_panel_position)
        val etRpm = settingsView.findViewById<io.legado.app.lib.theme.view.ThemeEditText>(R.id.et_rpm)
        val seekbarTemperature = settingsView.findViewById<SeekBar>(R.id.seekbar_temperature)
        val tvTemperature = settingsView.findViewById<TextView>(R.id.tv_temperature)
        val seekbarMaxTokens = settingsView.findViewById<SeekBar>(R.id.seekbar_max_tokens)
        val tvMaxTokens = settingsView.findViewById<TextView>(R.id.tv_max_tokens)
        val seekbarWindowSize = settingsView.findViewById<SeekBar>(R.id.seekbar_window_size)
        val tvWindowSize = settingsView.findViewById<TextView>(R.id.tv_window_size)
        val switchAutoSummary = settingsView.findViewById<SwitchMaterial>(R.id.switch_auto_summary)

        val displayModes = arrayOf("自适应", "分屏", "弹窗")
        val panelPositions = arrayOf("底部", "右侧")

        spinnerDisplayMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, displayModes)
        spinnerPanelPosition.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, panelPositions)

        val displayMode = appCtx.getPrefString(PreferKey.aiChatDisplayMode, "adaptive")
        val panelPosition = appCtx.getPrefString(PreferKey.aiPanelPosition, "bottom")
        val rpm = appCtx.getPrefInt(PreferKey.aiRpm, 0)
        val temperature = appCtx.getPrefInt(PreferKey.aiTemperature, 5) / 10f
        val maxTokens = appCtx.getPrefInt(PreferKey.aiMaxTokens, 4096)
        val windowSize = appCtx.getPrefInt(PreferKey.aiSlidingWindowSize, 8)
        val autoSummary = appCtx.getPrefBoolean(PreferKey.aiAutoSummaryPreviousContent, false)

        spinnerDisplayMode.setSelection(when (displayMode) {
            "split" -> 1
            "popup" -> 2
            else -> 0
        })
        spinnerPanelPosition.setSelection(if (panelPosition == "right") 1 else 0)
        etRpm.setText(if (rpm > 0) rpm.toString() else "")

        seekbarTemperature.progress = (temperature * 10).toInt()
        tvTemperature.text = String.format("%.1f", temperature)
        seekbarMaxTokens.progress = maxTokens
        tvMaxTokens.text = maxTokens.toString()
        seekbarWindowSize.progress = windowSize
        tvWindowSize.text = windowSize.toString()
        switchAutoSummary.isChecked = autoSummary

        seekbarTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTemperature.text = String.format("%.1f", progress / 10f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarMaxTokens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvMaxTokens.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarWindowSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvWindowSize.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        settingsView.findViewById<View>(R.id.btn_save_global)?.setOnClickListener {
            val selectedDisplayMode = when (spinnerDisplayMode.selectedItemPosition) {
                1 -> "split"
                2 -> "popup"
                else -> "adaptive"
            }
            val selectedPanelPosition = if (spinnerPanelPosition.selectedItemPosition == 1) "right" else "bottom"
            val rpmValue = etRpm.text.toString().toIntOrNull() ?: 0
            val temperatureValue = seekbarTemperature.progress / 10f
            val maxTokensValue = seekbarMaxTokens.progress
            val windowSizeValue = seekbarWindowSize.progress
            val autoSummaryValue = switchAutoSummary.isChecked

            appCtx.putPrefString(PreferKey.aiChatDisplayMode, selectedDisplayMode)
            appCtx.putPrefString(PreferKey.aiPanelPosition, selectedPanelPosition)
            appCtx.putPrefInt(PreferKey.aiRpm, rpmValue)
            appCtx.putPrefInt(PreferKey.aiTemperature, (temperatureValue * 10).toInt())
            appCtx.putPrefInt(PreferKey.aiMaxTokens, maxTokensValue)
            appCtx.putPrefInt(PreferKey.aiSlidingWindowSize, windowSizeValue)
            appCtx.putPrefBoolean(PreferKey.aiAutoSummaryPreviousContent, autoSummaryValue)

            Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
        }

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(settingsView) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = 1
        }
    }

    private fun loadTools() {
        val toolsView = layoutInflater.inflate(R.layout.item_ai_tools, null)
        val layoutTools = toolsView.findViewById<LinearLayout>(R.id.layout_tools)
        val btnResetTools = toolsView.findViewById<MaterialButton>(R.id.btn_reset_tools)

        // 获取当前启用的工具ID列表
        val enabledToolIds = appCtx.getPrefString(PreferKey.aiEnabledToolIds, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: mutableSetOf()

        // 清空现有视图
        layoutTools.removeAllViews()

        // 定义可用的工具列表（完整版）
        val availableTools = listOf(
            // 阅读分析类
            ToolInfo("current_book_info", "当前书籍信息", "获取当前阅读书籍的基本信息（标题、作者、简介、进度）"),
            ToolInfo("current_chapter", "当前章节内容", "获取当前阅读章节的完整文本内容"),
            ToolInfo("book_toc", "书籍目录", "获取书籍的完整章节目录结构"),
            ToolInfo("search_content", "搜索内容", "在书籍中按关键词搜索相关内容"),
            ToolInfo("reading_progress", "阅读进度", "获取当前阅读到第几章/进度百分比"),
            ToolInfo("book_notes", "书籍笔记", "获取书籍中的笔记记录和划线高亮"),
            ToolInfo("reading_history", "阅读历史", "获取最近阅读的书籍列表和时间"),
            ToolInfo("list_books", "列出书籍", "获取书架上的书籍列表，支持分类筛选"),
            ToolInfo("search_all_notes", "搜索所有笔记", "在所有书籍中搜索笔记和高亮内容"),

            // 深度分析类
            ToolInfo("extract_entities", "提取实体", "从书中提取人物、地点、组织等命名实体"),
            ToolInfo("analyze_arguments", "论证分析", "分析作者的论点、论据和逻辑结构"),
            ToolInfo("find_quotes", "查找引用", "在书中查找名言、精彩段落和难忘句子"),
            ToolInfo("compare_sections", "章节比较", "比较书中两个章节的主题、论点或写作风格"),

            // 标签管理类
            ToolInfo("tags_list", "标签列表", "获取用户创建的所有标签列表"),
            ToolInfo("book_tags", "书籍标签", "获取当前书籍的所有标签"),
            ToolInfo("apply_book_tags", "应用书籍标签", "为书籍添加或移除标签，支持批量操作"),
            ToolInfo("manage_tags", "管理标签", "创建、删除、重命名标签"),

            // 书架管理类
            ToolInfo("bookshelf_lookup", "书架查询", "获取书架上的书籍列表和分组信息"),
            ToolInfo("bookshelf_organize", "书架整理", "AI规划书架分组重组方案"),

            // 引用系统类
            ToolInfo("add_quote", "添加引用", "在回答中标注书籍原文出处"),

            // RAG向量化类
            ToolInfo("rag_search", "RAG搜索", "语义搜索已向量化的书籍内容（需先向量化）"),
            ToolInfo("rag_toc", "RAG目录", "获取已向量化书籍的章节结构"),
            ToolInfo("rag_context", "RAG上下文", "获取特定章节周围的上下文内容"),
            ToolInfo("vectorization_status", "向量化状态", "检查当前书籍是否已向量化"),
            ToolInfo("summarize_content", "内容摘要", "对书籍内容进行摘要总结（章节/全书）"),

            // 阅读统计类
            ToolInfo("reading_stats", "阅读统计", "获取用户阅读统计数据（阅读时长、书籍数量、阅读天数等）"),
            ToolInfo("book_read_time_rank", "阅读时长排行", "获取用户读书时长排行榜，分析阅读偏好")
        )

        // 如果没有保存过配置，默认全部启用
        if (enabledToolIds.isEmpty()) {
            enabledToolIds.addAll(availableTools.map { it.id })
        }

        // 按分类显示工具
        val categories = mapOf(
            "📖 阅读分析" to listOf("current_book_info", "current_chapter", "book_toc", "search_content", "reading_progress", "book_notes", "reading_history", "list_books", "search_all_notes"),
            "📝 深度分析" to listOf("extract_entities", "analyze_arguments", "find_quotes", "compare_sections"),
            "🏷️ 标签管理" to listOf("tags_list", "book_tags", "apply_book_tags", "manage_tags"),
            "📚 书架整理" to listOf("bookshelf_lookup", "bookshelf_organize"),
            "🔗 引用系统" to listOf("add_quote"),
            "🔮 RAG向量化" to listOf("rag_search", "rag_toc", "rag_context", "vectorization_status", "summarize_content"),
            "📊 阅读统计" to listOf("reading_stats", "book_read_time_rank")
        )

        for ((categoryName, toolIds) in categories) {
            // 添加分类标题
            val categoryHeader = TextView(requireContext()).apply {
                text = categoryName
                textSize = 16f
                setTextColor(resources.getColor(R.color.primary, null))
                setPadding(0, 24, 0, 8)
            }
            layoutTools.addView(categoryHeader)

            // 添加该分类下的工具
            for (toolId in toolIds) {
                val tool = availableTools.find { it.id == toolId } ?: continue
                val toolView = layoutInflater.inflate(R.layout.item_ai_tool_switch, null)
                val switchTool = toolView.findViewById<SwitchMaterial>(R.id.switch_tool)
                val tvToolName = toolView.findViewById<io.legado.app.ui.widget.text.PrimaryTextView>(R.id.tv_tool_name)
                val tvToolDesc = toolView.findViewById<io.legado.app.ui.widget.text.SecondaryTextView>(R.id.tv_tool_desc)

                tvToolName.text = tool.name
                tvToolDesc.text = tool.description
                switchTool.isChecked = enabledToolIds.contains(tool.id)

                switchTool.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        enabledToolIds.add(tool.id)
                    } else {
                        enabledToolIds.remove(tool.id)
                    }
                    appCtx.putPrefString(PreferKey.aiEnabledToolIds, enabledToolIds.joinToString(","))
                }

                layoutTools.addView(toolView)
            }
        }

        // 重置按钮
        btnResetTools.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("重置工具配置")
                .setMessage("确定要重置所有工具为默认配置吗？")
                .setPositiveButton("重置") { _, _ ->
                    enabledToolIds.clear()
                    enabledToolIds.addAll(availableTools.map { it.id })
                    appCtx.putPrefString(PreferKey.aiEnabledToolIds, enabledToolIds.joinToString(","))
                    loadTools()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(toolsView) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = 1
        }
    }

    private fun loadCache() {
        lifecycleScope.launch {
            val cacheCount = aiDao.getRecallCacheCount()
            val cacheView = layoutInflater.inflate(R.layout.item_ai_cache, null)

            val tvCacheSize = cacheView.findViewById<TextView>(R.id.tv_cache_size)
            val seekBar = cacheView.findViewById<SeekBar>(R.id.seekbar_max_cache)
            val tvMaxCache = cacheView.findViewById<TextView>(R.id.tv_max_cache)
            val btnClearCache = cacheView.findViewById<View>(R.id.btn_clear_cache)

            val maxCache = appCtx.getPrefInt(PreferKey.aiMaxCacheCount, 100)
            tvCacheSize.text = "当前缓存: $cacheCount 条"
            tvMaxCache.text = "最大缓存: $maxCache 条"
            seekBar.progress = maxCache
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = if (progress < 1) 1 else progress
                    tvMaxCache.text = "最大缓存: $value 条"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val value = if ((seekBar?.progress ?: 0) < 1) 1 else seekBar?.progress ?: 100
                    appCtx.putPrefInt(PreferKey.aiMaxCacheCount, value)
                }
            })

            btnClearCache.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("清除缓存")
                    .setMessage("确定要清除所有AI缓存吗？")
                    .setPositiveButton("清除") { _, _ ->
                        lifecycleScope.launch {
                            aiDao.clearRecallCache()
                            Toast.makeText(requireContext(), "缓存已清除", Toast.LENGTH_SHORT).show()
                            loadData()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    return object : RecyclerView.ViewHolder(cacheView) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                override fun getItemCount() = 1
            }
        }
    }

    private fun showAddProviderDialog() {
        val builtinOptions = AiServiceOptions.defaultServices
        val builtinNames = builtinOptions.map { it.title }

        AlertDialog.Builder(requireContext())
            .setTitle("选择服务商")
            .setItems(builtinNames.toTypedArray()) { _, which ->
                val option = builtinOptions[which]
                val intent = Intent(requireContext(), AiProviderDetailActivity::class.java).apply {
                    putExtra("identifier", option.identifier)
                    putExtra("title", option.title)
                    putExtra("defaultUrl", option.defaultUrl)
                    putExtra("defaultModel", option.defaultModel)
                    putExtra("isNew", true)
                    putExtra("isCustom", option.isCustom)
                }
                startActivity(intent)
            }
            .show()
    }

    private fun showAddPromptDialog() {
        showEditPromptDialog(null)
    }

    private fun showEditPromptDialog(prompt: AiPromptEntity?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_ai_prompt, null)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etContent = dialogView.findViewById<EditText>(R.id.et_content)
        val btnDelete = dialogView.findViewById<AccentTextView>(R.id.btn_delete)
        val btnCancel = dialogView.findViewById<AccentTextView>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<AccentTextView>(R.id.btn_save)

        val isEditing = prompt != null
        val title = if (isEditing) "编辑提示词" else "添加提示词"

        if (isEditing) {
            etName.setText(prompt!!.name)
            etContent.setText(prompt.content)
            btnDelete.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val content = etContent.text.toString()

            if (name.isBlank() || content.isBlank()) {
                Toast.makeText(requireContext(), "名称和内容不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val updatedPrompt = if (isEditing) {
                    prompt!!.copy(
                        name = name,
                        content = content,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    AiPromptEntity(
                        id = "custom_${System.currentTimeMillis()}",
                        name = name,
                        content = content,
                        showIn = "quick_bar",
                        sortOrder = 100,
                        isEnabled = true,
                        isBuiltin = false
                    )
                }
                promptManager.savePrompt(updatedPrompt)
                loadPrompts()
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("删除提示词")
                .setMessage("确定要删除 \"${prompt!!.name}\" 吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        promptManager.deletePrompt(prompt.id)
                        loadPrompts()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }

    inner class ProviderAdapter(private val providers: List<AiProviderEntity>) :
        RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {

        private val expandedPositions = mutableSetOf<Int>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_title)
            val tvProviderType: TextView = view.findViewById(R.id.tv_provider_type)
            val tvUrl: TextView = view.findViewById(R.id.tv_url)
            val tvModel: TextView = view.findViewById(R.id.tv_model)
            val tvKeyStatus: TextView = view.findViewById(R.id.tv_key_status)
            val tvActive: TextView = view.findViewById(R.id.tv_active)
            val layoutExpanded: View = view.findViewById(R.id.layout_expanded)
            val ivExpand: ImageView = view.findViewById(R.id.iv_expand)
            val btnSetActive: MaterialButton = view.findViewById(R.id.btn_set_active)
            val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete)
            val switchEnabled: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.switch_enabled)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_provider_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val provider = providers[position]
            val isExpanded = expandedPositions.contains(position)

            holder.tvTitle.text = provider.title
            holder.tvProviderType.text = "${provider.protocol.uppercase()} • ${provider.getApiKeyList().size} Keys"
            holder.tvUrl.text = provider.apiUrl
            holder.tvModel.text = provider.model

            holder.layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.ivExpand.rotation = if (isExpanded) 180f else 0f

            if (!provider.hasValidKey()) {
                holder.tvKeyStatus.visibility = View.VISIBLE
                holder.tvKeyStatus.text = "无有效API Key"
            } else {
                holder.tvKeyStatus.visibility = View.GONE
            }

            holder.tvActive.visibility = if (provider.isDefault) View.VISIBLE else View.GONE
            holder.btnSetActive.visibility = if (provider.isDefault) View.GONE else View.VISIBLE
            holder.btnDelete.visibility = if (provider.isDefault) View.GONE else View.VISIBLE

            holder.switchEnabled.isChecked = provider.enabled

            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    val updated = provider.copy(enabled = isChecked, updatedAt = System.currentTimeMillis())
                    aiDao.insertProvider(updated)
                }
            }

            holder.btnSetActive.setOnClickListener {
                setAsDefault(provider)
            }

            holder.btnDelete.setOnClickListener {
                deleteProvider(provider)
            }

            holder.itemView.setOnClickListener {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
            }

            holder.itemView.setOnLongClickListener {
                showProviderOptionsDialog(provider)
                true
            }
        }

        override fun getItemCount() = providers.size
    }

    private fun showProviderOptionsDialog(provider: AiProviderEntity) {
        val options = arrayOf("设为默认", "编辑", "测试连接", "获取模型列表", "删除")

        AlertDialog.Builder(requireContext())
            .setTitle(provider.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setAsDefault(provider)
                    1 -> editProvider(provider)
                    2 -> testConnection(provider)
                    3 -> fetchModels(provider)
                    4 -> deleteProvider(provider)
                }
            }
            .show()
    }

    private fun editProvider(provider: AiProviderEntity) {
        val intent = Intent(requireContext(), AiProviderDetailActivity::class.java).apply {
            putExtra("identifier", provider.identifier)
        }
        startActivity(intent)
    }

    /**
     * 测试AI服务商连接 - 完整实现
     */
    private fun testConnection(provider: AiProviderEntity) {
        if (!provider.hasValidKey()) {
            Toast.makeText(requireContext(), "请先配置API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = AlertDialog.Builder(requireContext())
            .setMessage("正在测试连接...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                val client = AiApiClient(provider)
                val result = client.testConnection()

                loadingDialog.dismiss()

                result.onSuccess { testResult ->
                    showTestResultDialog(provider, testResult)
                }.onFailure { error ->
                    AlertDialog.Builder(requireContext())
                        .setTitle("连接失败")
                        .setMessage("错误: ${error.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                AlertDialog.Builder(requireContext())
                    .setTitle("连接失败")
                    .setMessage("错误: ${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    /**
     * 获取模型列表 - 完整实现
     */
    private fun fetchModels(provider: AiProviderEntity) {
        if (!provider.hasValidKey()) {
            Toast.makeText(requireContext(), "请先配置API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = AlertDialog.Builder(requireContext())
            .setMessage("正在获取模型列表...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                val client = AiApiClient(provider)
                val result = client.fetchModels()

                loadingDialog.dismiss()

                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("模型列表")
                            .setMessage("未找到可用模型")
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        showModelsDialog(provider, models)
                    }
                }.onFailure { error ->
                    AlertDialog.Builder(requireContext())
                        .setTitle("获取失败")
                        .setMessage("错误: ${error.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                AlertDialog.Builder(requireContext())
                    .setTitle("获取失败")
                    .setMessage("错误: ${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    /**
     * 显示测试结果对话框
     */
    private fun showTestResultDialog(provider: AiProviderEntity, result: TestConnectionResult) {
        val message = buildString {
            append("连接状态: ${if (result.success) "✓ 成功" else "✗ 失败"}\n\n")
            append(result.message)
            if (result.modelCount > 0) {
                append("\n\n可用模型数量: ${result.modelCount}")
            }
            if (result.availableModels.isNotEmpty()) {
                append("\n\n可用模型:\n")
                result.availableModels.take(10).forEach { model ->
                    append("• $model\n")
                }
                if (result.availableModels.size > 10) {
                    append("... 等${result.availableModels.size}个模型")
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("测试结果 - ${provider.title}")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setNeutralButton("设为当前模型") { _, _ ->
                if (result.availableModels.isNotEmpty()) {
                    showSelectModelDialog(provider, result.availableModels)
                }
            }
            .show()
    }

    /**
     * 显示模型列表对话框
     */
    private fun showSelectModelDialog(provider: AiProviderEntity, models: List<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle("选择模型")
            .setItems(models.toTypedArray()) { _, which ->
                val selectedModel = models[which]
                lifecycleScope.launch {
                    val updated = provider.copy(model = selectedModel, updatedAt = System.currentTimeMillis())
                    aiDao.insertProvider(updated)
                    Toast.makeText(requireContext(), "已设置为: $selectedModel", Toast.LENGTH_SHORT).show()
                    loadProviders()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showModelsDialog(provider: AiProviderEntity, models: List<String>) {
        val modelsText = models.joinToString("\n")

        AlertDialog.Builder(requireContext())
            .setTitle("${provider.title} - 可用模型 (${models.size})")
            .setMessage(modelsText)
            .setPositiveButton("确定", null)
            .setNeutralButton("选择模型") { _, _ ->
                showSelectModelDialog(provider, models)
            }
            .show()
    }

    private fun setAsDefault(provider: AiProviderEntity) {
        lifecycleScope.launch {
            val allProviders = aiDao.getAllProviders()
            allProviders.forEach { p ->
                val updated = p.copy(
                    isDefault = p.identifier == provider.identifier,
                    updatedAt = System.currentTimeMillis()
                )
                aiDao.insertProvider(updated)
            }
            loadProviders()
        }
    }

    private fun deleteProvider(provider: AiProviderEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除")
            .setMessage("确定要删除 ${provider.title} 吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    aiDao.deleteProvider(provider.identifier)
                    loadProviders()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class PromptAdapter(private val prompts: List<AiPromptEntity>) :
        RecyclerView.Adapter<PromptAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val tvContent: TextView = view.findViewById(R.id.tv_content)
            val tvShowIn: TextView = view.findViewById(R.id.tv_show_in)
            val switchEnabled: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.switch_enabled)
            val btnEdit: View = view.findViewById(R.id.btn_edit)
            val btnMoveUp: View = view.findViewById(R.id.btn_move_up)
            val btnMoveDown: View = view.findViewById(R.id.btn_move_down)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_prompt, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prompt = prompts[position]
            holder.tvName.text = prompt.name
            holder.tvContent.text = prompt.content.take(50) + "..."
            holder.tvShowIn.text = "显示位置: ${prompt.showIn}"
            holder.switchEnabled.isChecked = prompt.isEnabled

            // 显示编辑按钮（所有提示词都可以编辑）
            holder.btnEdit.visibility = View.VISIBLE

            holder.btnMoveUp.isEnabled = position > 0
            holder.btnMoveUp.alpha = if (position > 0) 1f else 0.3f
            holder.btnMoveDown.isEnabled = position < prompts.size - 1
            holder.btnMoveDown.alpha = if (position < prompts.size - 1) 1f else 0.3f

            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    promptManager.setPromptEnabled(prompt.id, isChecked)
                    loadPrompts()
                }
            }

            holder.btnEdit.setOnClickListener {
                showEditPromptDialog(prompt)
            }

            holder.btnMoveUp.setOnClickListener {
                if (position > 0) {
                    lifecycleScope.launch {
                        promptManager.movePrompt(prompt.id, true)
                        loadPrompts()
                    }
                }
            }

            holder.btnMoveDown.setOnClickListener {
                if (position < prompts.size - 1) {
                    lifecycleScope.launch {
                        promptManager.movePrompt(prompt.id, false)
                        loadPrompts()
                    }
                }
            }
        }

        override fun getItemCount() = prompts.size
    }

    /**
     * 加载向量模型设置
     */
    private fun loadVectorSettings() {
        val vectorView = layoutInflater.inflate(R.layout.item_ai_vector_settings, null)

        val switchEnabled = vectorView.findViewById<SwitchMaterial>(R.id.switch_vector_enabled)
        val spinnerProvider = vectorView.findViewById<Spinner>(R.id.spinner_provider)
        val spinnerModel = vectorView.findViewById<Spinner>(R.id.spinner_model)
        val etApiKey = vectorView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_api_key)
        val etBaseUrl = vectorView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_base_url)
        val seekbarChunkSize = vectorView.findViewById<SeekBar>(R.id.seekbar_chunk_size)
        val tvChunkSize = vectorView.findViewById<TextView>(R.id.tv_chunk_size)
        val seekbarChunkOverlap = vectorView.findViewById<SeekBar>(R.id.seekbar_chunk_overlap)
        val tvChunkOverlap = vectorView.findViewById<TextView>(R.id.tv_chunk_overlap)
        val btnTestConnection = vectorView.findViewById<MaterialButton>(R.id.btn_test_connection)
        val tvTestResult = vectorView.findViewById<TextView>(R.id.tv_test_result)
        val btnSave = vectorView.findViewById<MaterialButton>(R.id.btn_save)

        val config = VectorConfigManager.getConfig()

        switchEnabled.isChecked = config.enabled
        etApiKey.setText(config.apiKey)
        etBaseUrl.setText(config.baseUrl)
        seekbarChunkSize.progress = config.chunkSize
        tvChunkSize.text = config.chunkSize.toString()
        seekbarChunkOverlap.progress = config.chunkOverlap
        tvChunkOverlap.text = config.chunkOverlap.toString()

        val providers = VectorConfigManager.getProviders()
        val providerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, providers.map { it.second })
        spinnerProvider.adapter = providerAdapter

        var selectedProvider = config.modelProvider
        val providerIndex = providers.indexOfFirst { it.first == config.modelProvider }
        if (providerIndex >= 0) spinnerProvider.setSelection(providerIndex)

        spinnerProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedProvider = providers[position].first
                val models = VectorConfigManager.getModelsByProvider(selectedProvider)
                val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, models.map { it.second })
                spinnerModel.adapter = modelAdapter

                val modelIndex = models.indexOfFirst { it.first == config.modelName }
                if (modelIndex >= 0) spinnerModel.setSelection(modelIndex)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val initialModels = VectorConfigManager.getModelsByProvider(config.modelProvider)
        val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, initialModels.map { it.second })
        spinnerModel.adapter = modelAdapter

        val modelIndex = initialModels.indexOfFirst { it.first == config.modelName }
        if (modelIndex >= 0) spinnerModel.setSelection(modelIndex)

        seekbarChunkSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvChunkSize.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarChunkOverlap.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvChunkOverlap.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnTestConnection.setOnClickListener {
            tvTestResult.text = "正在测试..."
            tvTestResult.setTextColor(resources.getColor(android.R.color.darker_gray, null))

            lifecycleScope.launch {
                try {
                    val models = VectorConfigManager.getModelsByProvider(selectedProvider)
                    val selectedModel = models.getOrNull(spinnerModel.selectedItemPosition)?.first ?: ""

                    val testConfig = VectorConfig(
                        enabled = true,
                        modelProvider = selectedProvider,
                        modelName = selectedModel,
                        apiKey = etApiKey.text.toString(),
                        baseUrl = etBaseUrl.text.toString().ifBlank { "https://api.openai.com/v1" },
                        batchSize = 20,
                        chunkSize = seekbarChunkSize.progress,
                        chunkOverlap = seekbarChunkOverlap.progress
                    )

                    val service = EmbeddingService(testConfig)
                    val result = service.testConnection()

                    result.onSuccess {
                        tvTestResult.text = "✅ 连接成功！模型: $selectedModel"
                        tvTestResult.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    }.onFailure { error ->
                        tvTestResult.text = "❌ 连接失败: ${error.message}"
                        tvTestResult.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    }
                } catch (e: Exception) {
                    tvTestResult.text = "❌ 测试异常: ${e.message}"
                    tvTestResult.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                }
            }
        }

        btnSave.setOnClickListener {
            val models = VectorConfigManager.getModelsByProvider(selectedProvider)
            val selectedModel = models.getOrNull(spinnerModel.selectedItemPosition)?.first ?: ""

            val newConfig = VectorConfig(
                enabled = switchEnabled.isChecked,
                modelProvider = selectedProvider,
                modelName = selectedModel,
                apiKey = etApiKey.text.toString(),
                baseUrl = etBaseUrl.text.toString().ifBlank { "https://api.openai.com/v1" },
                batchSize = 20,
                chunkSize = seekbarChunkSize.progress,
                chunkOverlap = seekbarChunkOverlap.progress
            )

            VectorConfigManager.saveConfig(newConfig)
            Toast.makeText(requireContext(), "向量模型配置已保存", Toast.LENGTH_SHORT).show()
        }

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(vectorView) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun getItemCount() = 1
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
