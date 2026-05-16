package io.legado.app.ui.config

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.AiApiClient
import io.legado.app.help.ai.AiAssistantConfigManager
import io.legado.app.help.ai.AiDao
import io.legado.app.help.ai.AiDatabase
import io.legado.app.help.ai.AiModelConfig
import io.legado.app.help.ai.AiPromptEntity
import io.legado.app.help.ai.AiProviderEntity
import io.legado.app.help.ai.AiServiceOptions
import io.legado.app.help.ai.PromptManager
import io.legado.app.help.ai.SkillManager
import io.legado.app.help.ai.TestConnectionResult
import io.legado.app.help.ai.rag.EmbeddingService
import io.legado.app.help.ai.rag.VectorConfig
import io.legado.app.help.ai.rag.VectorConfigManager
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.ui.config.AiProviderDetailActivity
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import kotlinx.coroutines.launch
import splitties.init.appCtx



/**
 * AI设置主页�?- PreferenceFragment风格
 * 简化设计：所有设置在同一页面，点击直接弹对话�? */
class AiSettingsPreferenceFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var aiDao: AiDao
    private lateinit var promptManager: PromptManager
    private lateinit var skillManager: SkillManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_ai_settings)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.ai_settings)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primaryText))

        aiDao = AiDatabase.getInstance(requireContext()).aiDao()
        promptManager = PromptManager(requireContext())
        skillManager = SkillManager(requireContext())

        // 更新summary
        updateSummary()
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateSummary() {
        // 聊天显示模式
        findPreference<Preference>("aiChatDisplayMode")?.let {
            val value = appCtx.getPrefString(PreferKey.aiChatDisplayMode, "adaptive")
            it.summary = when (value) {
                "split" -> "分屏"
                "popup" -> "弹窗"
                else -> "自适应"
            }
        }

        // 面板位置
        findPreference<Preference>("aiPanelPosition")?.let {
            val value = appCtx.getPrefString(PreferKey.aiPanelPosition, "bottom")
            it.summary = if (value == "right") "右侧" else "底部"
        }

        // 前情提要
        findPreference<SwitchPreference>("aiAutoSummaryPreviousContent")?.let {
            it.summary = if (it.isChecked) "打开书籍时显示AI生成的前情摘�? else "关闭"
        }

        // 向量模型状�?        findPreference<Preference>("ai_vector_settings")?.let {
            val config = VectorConfigManager.getConfig()
            it.summary = if (config.enabled) {
                "已启�? ${config.modelProvider} - ${config.modelName}"
            } else {
                "未启�?
            }
        }

        // MCP 服务器状�?        findPreference<Preference>("ai_mcp_server_manager")?.let {
            val servers = io.legado.app.help.config.AppConfig.aiMcpServers
            val enabledCount = servers.count { it.enabled }
            it.summary = if (servers.isEmpty()) {
                "暂无 MCP 服务器配�?
            } else {
                "已配�?${servers.size} 个服务器�?{enabledCount} 个启用）"
            }
        }

        // Tavily 状�?        findPreference<SwitchPreference>("aiTavilyEnabled")?.let {
            val apiKey = io.legado.app.help.config.AppConfig.aiTavilyApiKey.orEmpty()
            it.summary = if (apiKey.isBlank()) {
                "需要配�?API Key 才能使用"
            } else {
                "已配�?API Key，可以搜索实时网络信�?
            }
        }

        // Tavily 统一配置状�?        findPreference<Preference>("aiTavilyConfig")?.let {
            val apiKey = io.legado.app.help.config.AppConfig.aiTavilyApiKey.orEmpty()
            val topic = io.legado.app.help.config.AppConfig.aiTavilyTopic.orEmpty()
            val depth = io.legado.app.help.config.AppConfig.aiTavilySearchDepth.orEmpty()
            val maxResults = io.legado.app.help.config.AppConfig.aiTavilyMaxResults
            
            it.summary = if (apiKey.isBlank()) {
                "未配置（点击配置�?
            } else {
                "主题: ${getTopicLabel(topic)} | 深度: ${getDepthLabel(depth)} | 结果�? $maxResults"
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "ai_provider_manager" -> showProviderManagerDialog()
            "ai_prompt_manager" -> showPromptManagerDialog()
            "ai_skill_manager" -> showSkillManagerDialog()
            "ai_assistant_config" -> showAssistantConfigDialog()
            "ai_tool_settings" -> showToolSettingsDialog()
            "ai_vector_settings" -> showVectorSettingsDialog()
            "ai_cache_manager" -> showCacheManagerDialog()
            "ai_log_viewer" -> openAiLogViewer()
            "ai_mcp_server_manager" -> showMcpServerManagerDialog()
            "aiTavilyConfig" -> showTavilyConfigDialog()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateSummary()
    }

    // ==================== 服务商管�?====================

    private fun showProviderManagerDialog() {
        lifecycleScope.launch {
            val providers = aiDao.getAllProviders()
            val items = providers.map { provider ->
                val modelCount = aiDao.getModelsByProvider(provider.identifier).size
                val defaultMark = if (provider.isDefault) " �? else ""
                val modelInfo = if (modelCount > 0) " ($modelCount models)" else ""
                "${provider.title}$defaultMark$modelInfo"
            }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("服务商管�?)
                .setItems(items) { _, which ->
                    showProviderOptionsDialog(providers[which])
                }
                .setPositiveButton("添加") { _, _ ->
                    showAddProviderDialog()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun showProviderOptionsDialog(provider: AiProviderEntity) {
        val options = arrayOf("设为默认", "编辑", "管理模型", "测试连接", "获取模型列表", "删除")

        AlertDialog.Builder(requireContext())
            .setTitle(provider.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setAsDefault(provider)
                    1 -> editProvider(provider)
                    2 -> showManageModelsDialog(provider)
                    3 -> testConnection(provider)
                    4 -> fetchModels(provider)
                    5 -> deleteProvider(provider)
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
     * 显示模型管理对话框（参照archive项目�?     */
    private fun showManageModelsDialog(provider: AiProviderEntity) {
        lifecycleScope.launch {
            val models = aiDao.getModelsByProvider(provider.identifier)
            
            if (models.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("${provider.title} - 模型管理")
                    .setMessage("暂无模型配置\n\n请点击“获取模型列表”添加模�?)
                    .setPositiveButton("获取模型列表") { _, _ ->
                        fetchModels(provider)
                    }
                    .setNegativeButton("关闭", null)
                    .show()
                return@launch
            }
            
            val currentModelId = provider.model
            val items = models.map { model ->
                val isCurrent = model.modelId == currentModelId
                "${model.modelId}${if (isCurrent) " �? else ""}"
            }.toTypedArray()
            
            AlertDialog.Builder(requireContext())
                .setTitle("${provider.title} - 模型管理 (${models.size})")
                .setItems(items) { _, which ->
                    val model = models[which]
                    showModelOptionsDialog(provider, model)
                }
                .setPositiveButton("关闭", null)
                .show()
        }
    }
    
    /**
     * 显示单个模型的操作选项
     */
    private fun showModelOptionsDialog(provider: AiProviderEntity, model: AiModelConfig) {
        val isCurrent = model.modelId == provider.model
        val options = buildList {
            if (!isCurrent) add("设为当前模型")
            add("删除")
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle(model.modelId)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (!isCurrent) {
                            // 设为当前模型
                            lifecycleScope.launch {
                                val updated = provider.copy(
                                    model = model.modelId,
                                    updatedAt = System.currentTimeMillis()
                                )
                                aiDao.insertProvider(updated)
                                Toast.makeText(requireContext(), "已设置为当前模型", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    1 -> {
                        // 删除模型
                        confirmRemoveModel(provider, model)
                    }
                }
            }
            .show()
    }
    
    /**
     * 确认删除模型
     */
    private fun confirmRemoveModel(provider: AiProviderEntity, model: AiModelConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除模型")
            .setMessage("确定要删除模�?\"${model.modelId}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    aiDao.deleteModel(model.id)
                    
                    // 如果删除的是当前模型，清空当前模型设�?                    if (model.modelId == provider.model) {
                        val remainingModels = aiDao.getModelsByProvider(provider.identifier)
                        val updated = provider.copy(
                            model = remainingModels.firstOrNull()?.modelId ?: "",
                            updatedAt = System.currentTimeMillis()
                        )
                        aiDao.insertProvider(updated)
                    }
                    
                    Toast.makeText(requireContext(), "已删�?, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddProviderDialog() {
        val builtinOptions = AiServiceOptions.defaultServices
        val builtinNames = builtinOptions.map { it.title }

        AlertDialog.Builder(requireContext())
            .setTitle("选择服务�?)
            .setItems(builtinNames.toTypedArray()) { _, which ->
                val option = builtinOptions[which]
                startActivity(Intent(requireContext(), AiProviderDetailActivity::class.java).apply {
                    putExtra("identifier", option.identifier)
                    putExtra("title", option.title)
                    putExtra("defaultUrl", option.defaultUrl)
                    putExtra("defaultModel", option.defaultModel)
                    putExtra("isNew", true)
                    putExtra("isCustom", option.isCustom)
                })
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
            Toast.makeText(requireContext(), "已设为默�?, Toast.LENGTH_SHORT).show()
        }
    }

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
                            .setMessage("未找到可用模�?)
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        // 保存模型列表到Provider
                        val updatedProvider = provider.setAvailableModels(models)
                        aiDao.insertProvider(updatedProvider)
                        
                        Toast.makeText(requireContext(), "已获�?{models.size}个模�?, Toast.LENGTH_SHORT).show()
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

    private fun showTestResultDialog(provider: AiProviderEntity, result: TestConnectionResult) {
        val message = buildString {
            append("连接状�? ${if (result.success) "�?成功" else "�?失败"}\n\n")
            append(result.message)
            if (result.modelCount > 0) {
                append("\n\n可用模型数量: ${result.modelCount}")
            }
            if (result.availableModels.isNotEmpty()) {
                append("\n\n可用模型:\n")
                result.availableModels.take(10).forEach { model ->
                    append("�?$model\n")
                }
                if (result.availableModels.size > 10) {
                    append("... �?{result.availableModels.size}个模�?)
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

    private fun showModelsDialog(provider: AiProviderEntity, models: List<String>) {
        val modelsText = models.joinToString("\n")
        AlertDialog.Builder(requireContext())
            .setTitle("${provider.title} - 可用模型 (${models.size})")
            .setMessage(modelsText)
            .setPositiveButton("确定", null)
            .setNeutralButton("选择模型") { _, _ ->
                showSelectModelDialog(provider, models)
            }
            .setNegativeButton("添加全部") { _, _ ->
                // 添加所有模型到配置
                lifecycleScope.launch {
                    appendFetchedModels(provider.identifier, models)
                }
            }
            .show()
    }

    private fun showSelectModelDialog(provider: AiProviderEntity, models: List<String>) {
        val items = buildList {
            add("添加全部模型")
            addAll(models)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("选择要添加的模型")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    // 添加全部
                    lifecycleScope.launch {
                        appendFetchedModels(provider.identifier, models)
                    }
                } else {
                    // 添加单个模型
                    val selectedModelId = items[which]
                    lifecycleScope.launch {
                        appendFetchedModels(provider.identifier, listOf(selectedModelId))
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 添加获取到的模型到配置（参照archive项目�?     */
    private suspend fun appendFetchedModels(providerId: String, modelIds: List<String>) {
        val oldModels = aiDao.getModelsByProvider(providerId)
        val existingIds = oldModels.map { it.modelId }.toSet()
        
        val newModels = modelIds
            .distinct()
            .filterNot { it in existingIds }
            .map { modelId ->
                AiModelConfig(
                    id = "model_${providerId}_${modelId}_${System.currentTimeMillis()}",
                    providerId = providerId,
                    modelId = modelId
                )
            }
        
        if (newModels.isEmpty()) {
            Toast.makeText(requireContext(), "没有新模型可添加", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存新模�?        newModels.forEach { model ->
            aiDao.insertModel(model)
        }
        
        // 如果当前服务商没有选中模型，自动设置第一�?        val currentProvider = aiDao.getProvider(providerId)
        if (currentProvider != null && currentProvider.model.isBlank()) {
            val updated = currentProvider.copy(
                model = newModels.first().modelId,
                updatedAt = System.currentTimeMillis()
            )
            aiDao.insertProvider(updated)
        }
        
        Toast.makeText(requireContext(), "已添�?${newModels.size} 个模�?, Toast.LENGTH_SHORT).show()
    }

    private fun deleteProvider(provider: AiProviderEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除")
            .setMessage("确定要删�?${provider.title} 吗？\n这将同时删除该服务商下的所有模型配置�?)
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    // 先删除关联的模型
                    aiDao.deleteModelsByProvider(provider.identifier)
                    // 再删除服务商
                    aiDao.deleteProvider(provider.identifier)
                    Toast.makeText(requireContext(), "已删�?, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 提示词管�?====================

    private fun showPromptManagerDialog() {
        lifecycleScope.launch {
            val prompts = promptManager.getAllPrompts()
            val items = prompts.map { it.name }.toTypedArray()

            if (items.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("提示词管�?)
                    .setMessage("暂无提示�?)
                    .setPositiveButton("添加") { _, _ -> showAddPromptDialog() }
                    .setNegativeButton("关闭", null)
                    .show()
                return@launch
            }

            AlertDialog.Builder(requireContext())
                .setTitle("提示词管�?)
                .setItems(items) { _, which ->
                    showEditPromptDialog(prompts[which])
                }
                .setPositiveButton("添加") { _, _ -> showAddPromptDialog() }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun showAddPromptDialog() {
        showEditPromptDialog(null)
    }

    private fun showEditPromptDialog(prompt: AiPromptEntity?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_prompt, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etContent = dialogView.findViewById<EditText>(R.id.et_content)
        val btnDelete = dialogView.findViewById<io.legado.app.ui.widget.text.AccentTextView>(R.id.btn_delete)
        val btnCancel = dialogView.findViewById<io.legado.app.ui.widget.text.AccentTextView>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<io.legado.app.ui.widget.text.AccentTextView>(R.id.btn_save)

        val isEditing = prompt != null
        val title = if (isEditing) "编辑提示�? else "添加提示�?

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
                Toast.makeText(requireContext(), "名称和内容不能为�?, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "已保�?, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("删除提示�?)
                .setMessage("确定要删�?\"${prompt!!.name}\" 吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        promptManager.deletePrompt(prompt.id)
                        Toast.makeText(requireContext(), "已删�?, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }

    // ==================== 技能管�?====================

    private fun showSkillManagerDialog() {
        lifecycleScope.launch {
            val skills = skillManager.getAllSkills()
            val items = skills.map { "${it.name}${if (it.isBuiltin) " [内置]" else ""}" }.toTypedArray()

            if (items.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("技能管�?)
                    .setMessage("暂无技�?)
                    .setPositiveButton("添加") { _, _ -> showAddSkillDialog() }
                    .setNeutralButton("恢复默认") { _, _ -> restoreDefaultSkills() }
                    .setNegativeButton("关闭", null)
                    .show()
                return@launch
            }

            AlertDialog.Builder(requireContext())
                .setTitle("技能管�?)
                .setItems(items) { _, which ->
                    showEditSkillDialog(skills[which])
                }
                .setPositiveButton("添加") { _, _ -> showAddSkillDialog() }
                .setNeutralButton("恢复默认") { _, _ -> restoreDefaultSkills() }
                .setNegativeButton("关闭", null)
                .show()
        }
    }
    
    /**
     * 恢复默认技�?     */
    private fun restoreDefaultSkills() {
        AlertDialog.Builder(requireContext())
            .setTitle("恢复默认技�?)
            .setMessage("这将重新添加所有内置技能。\n\n注意：已存在的内置技能不会被覆盖，只会添加缺失的技能�?)
            .setPositiveButton("恢复") { _, _ ->
                lifecycleScope.launch {
                    skillManager.initDefaultSkills()
                    Toast.makeText(requireContext(), "已恢复默认技�?, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddSkillDialog() {
        showEditSkillDialog(null)
    }

    private fun showEditSkillDialog(skill: io.legado.app.help.ai.AiSkillEntity?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_skill, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etDescription = dialogView.findViewById<EditText>(R.id.et_description)
        val etTriggerWord = dialogView.findViewById<EditText>(R.id.et_trigger_word)
        val etInstruction = dialogView.findViewById<EditText>(R.id.et_instruction)
        val btnDelete = dialogView.findViewById<io.legado.app.ui.widget.text.AccentTextView>(R.id.btn_delete)
        val btnCancel = dialogView.findViewById<io.legado.app.ui.widget.text.AccentTextView>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<io.legado.app.ui.widget.text.AccentTextView>(R.id.btn_save)

        val isEditing = skill != null
        val title = if (isEditing) "编辑技�? else "添加技�?

        if (isEditing) {
            etName.setText(skill!!.name)
            etDescription.setText(skill.description)
            etTriggerWord.setText(skill.triggerWord)
            etInstruction.setText(skill.instruction)
            // 内置技能也可以删除和修改名�?            btnDelete.visibility = View.VISIBLE
            etName.isEnabled = true
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val triggerWord = etTriggerWord.text.toString().trim()
            val instruction = etInstruction.text.toString().trim()

            if (name.isBlank() || triggerWord.isBlank() || instruction.isBlank()) {
                Toast.makeText(requireContext(), "名称、触发词和指令不能为�?, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val updatedSkill = if (isEditing) {
                    skill!!.copy(
                        name = name,
                        description = description,
                        triggerWord = triggerWord,
                        instruction = instruction,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    io.legado.app.help.ai.AiSkillEntity(
                        id = "skill_custom_${System.currentTimeMillis()}",
                        name = name,
                        description = description,
                        triggerWord = triggerWord,
                        instruction = instruction,
                        category = "custom",
                        icon = null,
                        showIn = "quick_bar,text_menu",
                        sortOrder = 100,
                        isEnabled = true,
                        isBuiltin = false,
                        variables = "[]",
                        examples = "[]"
                    )
                }
                skillManager.saveSkill(updatedSkill)
                Toast.makeText(requireContext(), "已保�?, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            val warningMessage = if (skill!!.isBuiltin) {
                "确定要删除内置技�?\"${skill.name}\" 吗？\n\n注意：删除后可以通过“恢复默认”重新添加�?
            } else {
                "确定要删�?\"${skill.name}\" 吗？"
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle("删除技�?)
                .setMessage(warningMessage)
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        skillManager.deleteSkill(skill.id)
                        Toast.makeText(requireContext(), "已删�?, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }

    // ==================== 工具配置 ====================

    private fun showToolSettingsDialog() {
        val availableTools = listOf(
            // 阅读分析�?            Triple("current_book_info", "当前书籍信息", "获取当前阅读书籍的基本信息（标题、作者、简介、进度）"),
            Triple("current_chapter", "当前章节内容", "获取当前阅读章节的完整文本内�?),
            Triple("book_toc", "书籍目录", "获取书籍的完整章节目录结�?),
            Triple("search_content", "搜索内容", "在书籍中按关键词搜索相关内容"),
            Triple("reading_progress", "阅读进度", "获取当前阅读到第几章/进度百分�?),
            Triple("book_notes", "书籍笔记", "获取书籍中的笔记记录和划线高�?),
            Triple("reading_history", "阅读历史", "获取最近阅读的书籍列表和时�?),
            Triple("list_books", "列出书籍", "获取书架上的书籍列表，支持分类筛�?),
            Triple("search_all_notes", "搜索所有笔�?, "在所有书籍中搜索笔记和高亮内�?),

            // 深度分析�?            Triple("extract_entities", "提取实体", "从书中提取人物、地点、组织等命名实体"),
            Triple("analyze_arguments", "论证分析", "分析作者的论点、论据和逻辑结构"),
            Triple("find_quotes", "查找引用", "在书中查找名言、精彩段落和难忘句子"),
            Triple("compare_sections", "章节比较", "比较书中两个章节的主题、论点或写作风格"),

            // 标签管理�?            Triple("tags_list", "标签列表", "获取用户创建的所有标签列�?),
            Triple("book_tags", "书籍标签", "获取当前书籍的所有标�?),
            Triple("apply_book_tags", "应用书籍标签", "为书籍添加或移除标签，支持批量操�?),
            Triple("manage_tags", "管理标签", "创建、删除、重命名标签"),

            // 书架管理�?            Triple("bookshelf_lookup", "书架查询", "获取书架上的书籍列表和分组信�?),
            Triple("bookshelf_organize", "书架整理", "AI规划书架分组重组方案"),

            // 引用系统�?            Triple("add_quote", "添加引用", "在回答中标注书籍原文出处"),

            // RAG向量化类
            Triple("rag_search", "RAG搜索", "语义搜索已向量化的书籍内容（需先向量化�?),
            Triple("rag_toc", "RAG目录", "获取已向量化书籍的章节结�?),
            Triple("rag_context", "RAG上下�?, "获取特定章节周围的上下文内容"),
            Triple("vectorization_status", "向量化状�?, "检查当前书籍是否已向量�?),
            Triple("summarize_content", "内容摘要", "对书籍内容进行摘要总结（章�?全书�?),

            // 阅读统计�?            Triple("reading_stats", "阅读统计", "获取用户阅读统计数据（阅读时长、书籍数量、阅读天数等�?),
            Triple("book_read_time_rank", "阅读时长排行", "获取用户读书时长排行榜，分析阅读偏好")
        )

        val enabledToolIds = appCtx.getPrefString(PreferKey.aiEnabledToolIds, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: mutableSetOf()

        // 如果没有保存过配置，默认全部启用
        if (enabledToolIds.isEmpty()) {
            enabledToolIds.addAll(availableTools.map { it.first })
        }

        // 创建自定义适配�?        val adapter = object : android.widget.BaseAdapter() {
            private val checkedState = BooleanArray(availableTools.size) {
                enabledToolIds.contains(availableTools[it].first)
            }

            override fun getCount() = availableTools.size

            override fun getItem(position: Int) = availableTools[position]

            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_ai_tool, parent, false)
                val cbChecked = view.findViewById<android.widget.CheckBox>(R.id.cb_tool_checked)
                val tvName = view.findViewById<TextView>(R.id.tv_tool_name)
                val tvDesc = view.findViewById<TextView>(R.id.tv_tool_description)
                
                val tool = availableTools[position]
                tvName.text = tool.second
                tvDesc.text = tool.third
                
                // 设置选中状�?                cbChecked.isChecked = checkedState[position]
                
                // 点击整个列表项切换选中状�?                view.setOnClickListener {
                    checkedState[position] = !checkedState[position]
                    cbChecked.isChecked = checkedState[position]
                    
                    val toolId = tool.first
                    if (checkedState[position]) {
                        enabledToolIds.add(toolId)
                    } else {
                        enabledToolIds.remove(toolId)
                    }
                }
                
                return view
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("工具配置")
            .setAdapter(adapter, null)
            .setPositiveButton("保存") { _, _ ->
                appCtx.putPrefString(PreferKey.aiEnabledToolIds, enabledToolIds.joinToString(","))
                Toast.makeText(requireContext(), "已保�?, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("重置") { _, _ ->
                enabledToolIds.clear()
                enabledToolIds.addAll(availableTools.map { it.first })
                appCtx.putPrefString(PreferKey.aiEnabledToolIds, enabledToolIds.joinToString(","))
                Toast.makeText(requireContext(), "已重置为默认", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 缓存管理 ====================

    private fun showCacheManagerDialog() {
        lifecycleScope.launch {
            val cacheCount = aiDao.getRecallCacheCount()
            val maxCache = appCtx.getPrefInt(PreferKey.aiMaxCacheCount, 100)

            val dialogView = layoutInflater.inflate(R.layout.dialog_ai_cache, null)
            val tvCacheSize = dialogView.findViewById<TextView>(R.id.tv_cache_size)
            val seekBar = dialogView.findViewById<SeekBar>(R.id.seekbar_max_cache)
            val tvMaxCache = dialogView.findViewById<TextView>(R.id.tv_max_cache)

            tvCacheSize.text = "当前缓存: $cacheCount �?
            tvMaxCache.text = "最大缓�? $maxCache �?
            seekBar.progress = maxCache
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = if (progress < 1) 1 else progress
                    tvMaxCache.text = "最大缓�? $value �?
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val value = if ((seekBar?.progress ?: 0) < 1) 1 else seekBar?.progress ?: 100
                    appCtx.putPrefInt(PreferKey.aiMaxCacheCount, value)
                }
            })

            AlertDialog.Builder(requireContext())
                .setTitle("缓存管理")
                .setView(dialogView)
                .setPositiveButton("清除缓存") { _, _ ->
                    lifecycleScope.launch {
                        aiDao.clearRecallCache()
                        Toast.makeText(requireContext(), "缓存已清�?, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    // ==================== AI日志查看 ====================

    private fun openAiLogViewer() {
        val intent = android.content.Intent(requireContext(), io.legado.app.ui.ai.AiLogActivity::class.java)
        startActivity(intent)
    }

    // ==================== 向量模型配置 ====================

    private fun showVectorSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_vector_settings, null)

        val switchEnabled = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_vector_enabled)
        val spinnerProvider = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_provider)
        val spinnerModel = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_model)
        val etApiKey = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_api_key)
        val seekbarChunkSize = dialogView.findViewById<SeekBar>(R.id.seekbar_chunk_size)
        val tvChunkSize = dialogView.findViewById<TextView>(R.id.tv_chunk_size)
        val seekbarChunkOverlap = dialogView.findViewById<SeekBar>(R.id.seekbar_chunk_overlap)
        val tvChunkOverlap = dialogView.findViewById<TextView>(R.id.tv_chunk_overlap)
        val btnTestConnection = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_test_connection)
        val tvTestResult = dialogView.findViewById<TextView>(R.id.tv_test_result)

        val config = VectorConfigManager.getConfig()

        switchEnabled.isChecked = config.enabled
        etApiKey.setText(config.apiKey)
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
            tvTestResult.setTextColor(android.graphics.Color.GRAY)

            lifecycleScope.launch {
                try {
                    val models = VectorConfigManager.getModelsByProvider(selectedProvider)
                    val selectedModel = models.getOrNull(spinnerModel.selectedItemPosition)?.first ?: ""

                    val testConfig = VectorConfig(
                        enabled = true,
                        modelProvider = selectedProvider,
                        modelName = selectedModel,
                        apiKey = etApiKey.text.toString(),
                        baseUrl = "https://api.openai.com/v1",
                        batchSize = 20,
                        chunkSize = seekbarChunkSize.progress,
                        chunkOverlap = seekbarChunkOverlap.progress
                    )

                    val service = EmbeddingService(testConfig)
                    val result = service.testConnection()

                    result.onSuccess {
                        tvTestResult.text = "�?连接成功�?
                        tvTestResult.setTextColor(android.graphics.Color.GREEN)
                    }.onFailure { error ->
                        tvTestResult.text = "�?失败: ${error.message}"
                        tvTestResult.setTextColor(android.graphics.Color.RED)
                    }
                } catch (e: Exception) {
                    tvTestResult.text = "�?异常: ${e.message}"
                    tvTestResult.setTextColor(android.graphics.Color.RED)
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("向量模型配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val models = VectorConfigManager.getModelsByProvider(selectedProvider)
                val selectedModel = models.getOrNull(spinnerModel.selectedItemPosition)?.first ?: ""

                val newConfig = VectorConfig(
                    enabled = switchEnabled.isChecked,
                    modelProvider = selectedProvider,
                    modelName = selectedModel,
                    apiKey = etApiKey.text.toString(),
                    baseUrl = "https://api.openai.com/v1",
                    batchSize = 20,
                    chunkSize = seekbarChunkSize.progress,
                    chunkOverlap = seekbarChunkOverlap.progress
                )

                VectorConfigManager.saveConfig(newConfig)
                updateSummary()
                Toast.makeText(requireContext(), "已保�?, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("使用说明") { _, _ ->
                showVectorHelpDialog()
            }
            .show()
    }

    private fun showVectorHelpDialog() {
        val helpText = """
            📚 向量模型使用指南

            一、什么是向量化？
            向量化是将书籍内容转换为"向量"（数字数组），让AI能理解内容的语义�?
            二、使用步�?
            1️⃣ 配置向量模型
            �?选择提供商（OpenAI/SiliconFlow等）
            �?选择嵌入模型
            �?填入API Key
            �?点击"测试连接"
            �?保存配置

            2️⃣ 向量化书�?            在书籍详情页�?            菜单 �?"向量化书�?

            3️⃣ 使用RAG搜索
            向量化后，AI会自动使用语义搜索回答问�?        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("向量模型使用说明")
            .setMessage(helpText)
            .setPositiveButton("确定", null)
            .show()
    }

    // ==================== AI助手配置 ====================

    private fun showAssistantConfigDialog() {
        val options = arrayOf("空状态配置（试试这样问）", "快捷操作�?- 无选中文字", "快捷操作�?- 有选中文字")
        
        AlertDialog.Builder(requireContext())
            .setTitle("AI助手配置")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEmptyStateConfigDialog()
                    1 -> showQuickActionBarConfigDialog(hasSelectedText = false)
                    2 -> showQuickActionBarConfigDialog(hasSelectedText = true)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    /**
     * 空状态配置对话框
     */
    private fun showEmptyStateConfigDialog() {
        val context = requireContext()
        val config = AiAssistantConfigManager.getEmptyStateConfig(context)
        val skillManager = io.legado.app.help.ai.SkillManager(context)
        
        lifecycleScope.launch {
            val allSkills = skillManager.getAllSkills()
            val skillNames = allSkills.map { it.name }.toTypedArray()
            
            val items = config.mapIndexed { index, item ->
                val name = if (item.type == AiAssistantConfigManager.ConfigType.SKILL) {
                    allSkills.find { it.id == item.skillId }?.name ?: "未知"
                } else {
                    item.customName ?: "自定�?
                }
                "位置${index + 1}: $name"
            }.toTypedArray()
            
            AlertDialog(context)
                .setTitle("空状�?- 试试这样问（固定4个位置）")
                .setItems(items) { _, which ->
                    showEditEmptyStateItemDialog(context, config, which, allSkills)
                }
                .setPositiveButton("恢复默认") { _, _ ->
                    AiAssistantConfigManager.restoreDefaultEmptyStateConfig(context)
                    Toast.makeText(context, "已恢复默�?, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }
    
    /**
     * 编辑空状态单个位�?     */
    private fun showEditEmptyStateItemDialog(
        context: android.content.Context,
        config: List<AiAssistantConfigManager.EmptyStateItem>,
        positionIndex: Int,
        allSkills: List<io.legado.app.help.ai.AiSkillEntity>
    ) {
        val currentItem = config[positionIndex]
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_assistant_config_item, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radio_group_type)
        val spinnerSkill = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_skill)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etTrigger = dialogView.findViewById<EditText>(R.id.et_trigger)
        val etDesc = dialogView.findViewById<EditText>(R.id.et_description)
        
        // 设置当前类型
        if (currentItem.type == AiAssistantConfigManager.ConfigType.SKILL) {
            radioGroup.check(R.id.radio_skill)
        } else {
            radioGroup.check(R.id.radio_custom)
        }
        
        // 填充Skill列表
        val skillNames = allSkills.map { it.name }.toTypedArray()
        val skillAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, skillNames)
        spinnerSkill.adapter = skillAdapter
        
        // 如果是Skill类型，选中当前Skill
        if (currentItem.type == AiAssistantConfigManager.ConfigType.SKILL) {
            val skillIndex = allSkills.indexOfFirst { it.id == currentItem.skillId }
            if (skillIndex >= 0) spinnerSkill.setSelection(skillIndex)
        }
        
        // 如果是Custom类型，填充文�?        if (currentItem.type == AiAssistantConfigManager.ConfigType.CUSTOM) {
            etName.setText(currentItem.customName)
            etTrigger.setText(currentItem.customTrigger)
            etDesc.setText(currentItem.customDescription)
        }
        
        // 监听类型切换
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_skill) {
                spinnerSkill.visibility = View.VISIBLE
                etName.visibility = View.GONE
                etTrigger.visibility = View.GONE
                etDesc.visibility = View.GONE
            } else {
                spinnerSkill.visibility = View.GONE
                etName.visibility = View.VISIBLE
                etTrigger.visibility = View.VISIBLE
                etDesc.visibility = View.VISIBLE
            }
        }
        
        // 初始化显示状�?        if (currentItem.type == AiAssistantConfigManager.ConfigType.SKILL) {
            spinnerSkill.visibility = View.VISIBLE
            etName.visibility = View.GONE
            etTrigger.visibility = View.GONE
            etDesc.visibility = View.GONE
        } else {
            spinnerSkill.visibility = View.GONE
            etName.visibility = View.VISIBLE
            etTrigger.visibility = View.VISIBLE
            etDesc.visibility = View.VISIBLE
        }
        
        AlertDialog(context)
            .setTitle("编辑位置${positionIndex + 1}")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val isSkillType = radioGroup.checkedRadioButtonId == R.id.radio_skill
                
                val newItem = if (isSkillType) {
                    val selectedSkill = allSkills[spinnerSkill.selectedItemPosition]
                    AiAssistantConfigManager.EmptyStateItem(
                        positionIndex = positionIndex,
                        type = AiAssistantConfigManager.ConfigType.SKILL,
                        skillId = selectedSkill.id
                    )
                } else {
                    val name = etName.text.toString().trim()
                    val trigger = etTrigger.text.toString().trim()
                    val desc = etDesc.text.toString().trim()
                    
                    if (name.isEmpty() || trigger.isEmpty()) {
                        Toast.makeText(context, "名称和触发词不能为空", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    AiAssistantConfigManager.EmptyStateItem(
                        positionIndex = positionIndex,
                        type = AiAssistantConfigManager.ConfigType.CUSTOM,
                        customName = name,
                        customTrigger = trigger,
                        customDescription = desc
                    )
                }
                
                // 更新配置
                val newConfig = config.toMutableList()
                newConfig[positionIndex] = newItem
                AiAssistantConfigManager.saveEmptyStateConfig(context, newConfig)
                
                Toast.makeText(context, "已保�?, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 快捷操作栏配置对话框
     * @param hasSelectedText 是否有选中的文�?     */
    private fun showQuickActionBarConfigDialog(hasSelectedText: Boolean) {
        val context = requireContext()
        val config = AiAssistantConfigManager.getQuickActionBarConfig(context, hasSelectedText)
        
        val title = if (hasSelectedText) "快捷操作�?- 有选中文字（固�?个位置）" else "快捷操作�?- 无选中文字（固�?个位置）"
        
        lifecycleScope.launch {
            val skillManager = SkillManager(context)
            val allSkills = skillManager.getAllSkills()
            
            val items = config.mapIndexed { index, item ->
                val name = if (item.type == AiAssistantConfigManager.ConfigType.SKILL) {
                    allSkills.find { it.id == item.skillId }?.name ?: "未知"
                } else {
                    item.displayName ?: "自定�?
                }
                "位置${index + 1}: $name"
            }.toTypedArray()
            
            AlertDialog(context)
                .setTitle(title)
                .setItems(items) { _, which ->
                    showEditQuickActionItemDialog(context, config, which, hasSelectedText)
                }
                .setPositiveButton("恢复默认") { _, _ ->
                    AiAssistantConfigManager.restoreDefaultQuickActionBarConfig(context, hasSelectedText)
                    Toast.makeText(context, "已恢复默�?, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }
    
    /**
     * 编辑快捷操作单个位置
     * @param hasSelectedText 是否有选中的文�?     */
    private fun showEditQuickActionItemDialog(
        context: android.content.Context,
        config: List<AiAssistantConfigManager.QuickActionItem>,
        positionIndex: Int,
        hasSelectedText: Boolean
    ) {
        val currentItem = config[positionIndex]
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_quick_action_edit, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radio_group_type)
        val radioSkill = dialogView.findViewById<android.widget.RadioButton>(R.id.radio_skill)
        val radioCustom = dialogView.findViewById<android.widget.RadioButton>(R.id.radio_custom)
        val spinnerSkill = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_skill)
        val etDisplayName = dialogView.findViewById<EditText>(R.id.et_display_name)
        val etTriggerWord = dialogView.findViewById<EditText>(R.id.et_trigger_word)
        
        lifecycleScope.launch {
            val skillManager = SkillManager(context)
            val allSkills = skillManager.getAllSkills()
            val skillNames = allSkills.map { it.name }.toTypedArray()
            
            // 设置Spinner适配�?            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, skillNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSkill.adapter = adapter
            
            // 根据当前类型设置UI
            if (currentItem.type == AiAssistantConfigManager.ConfigType.SKILL) {
                radioSkill.isChecked = true
                spinnerSkill.visibility = View.VISIBLE
                etDisplayName.visibility = View.GONE
                etTriggerWord.visibility = View.GONE
                
                // 选中对应的Skill
                val skillIndex = allSkills.indexOfFirst { it.id == currentItem.skillId }
                if (skillIndex >= 0) {
                    spinnerSkill.setSelection(skillIndex)
                }
            } else {
                radioCustom.isChecked = true
                spinnerSkill.visibility = View.GONE
                etDisplayName.visibility = View.VISIBLE
                etTriggerWord.visibility = View.VISIBLE
                
                etDisplayName.setText(currentItem.displayName)
                etTriggerWord.setText(currentItem.triggerWord)
            }
            
            // 监听类型切换
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.radio_skill) {
                    spinnerSkill.visibility = View.VISIBLE
                    etDisplayName.visibility = View.GONE
                    etTriggerWord.visibility = View.GONE
                } else {
                    spinnerSkill.visibility = View.GONE
                    etDisplayName.visibility = View.VISIBLE
                    etTriggerWord.visibility = View.VISIBLE
                }
            }
            
            AlertDialog(context)
                .setTitle("编辑位置${positionIndex + 1}")
                .setView(dialogView)
                .setPositiveButton("保存") { _, _ ->
                    val isSkillType = radioGroup.checkedRadioButtonId == R.id.radio_skill
                    
                    val newItem = if (isSkillType) {
                        val selectedSkill = allSkills[spinnerSkill.selectedItemPosition]
                        AiAssistantConfigManager.QuickActionItem(
                            positionIndex = positionIndex,
                            type = AiAssistantConfigManager.ConfigType.SKILL,
                            skillId = selectedSkill.id
                        )
                    } else {
                        val displayName = etDisplayName.text.toString().trim()
                        val triggerWord = etTriggerWord.text.toString().trim()
                        
                        if (displayName.isEmpty() || triggerWord.isEmpty()) {
                            Toast.makeText(context, "显示名称和触发词不能为空", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        
                        AiAssistantConfigManager.QuickActionItem(
                            positionIndex = positionIndex,
                            type = AiAssistantConfigManager.ConfigType.CUSTOM,
                            displayName = displayName,
                            triggerWord = triggerWord
                        )
                    }
                    
                    // 更新配置
                    val newConfig = config.toMutableList()
                    newConfig[positionIndex] = newItem
                    AiAssistantConfigManager.saveQuickActionBarConfig(context, newConfig, hasSelectedText)
                    
                    Toast.makeText(context, "已保�?, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSummary()
    }

    // ==================== MCP 服务器管�?====================

    private fun showMcpServerManagerDialog() {
        val servers = io.legado.app.help.config.AppConfig.aiMcpServers
        
        if (servers.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("MCP 服务�?)
                .setMessage("暂无 MCP 服务器配�?)
                .setPositiveButton("添加") { _, _ -> showEditMcpServerDialog() }
                .setNegativeButton("关闭", null)
                .show()
            return
        }
        
        val items = servers.map { server ->
            "${server.name}${if (!server.enabled) " (已禁�?" else ""}"
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("MCP 服务器管�?)
            .setItems(items) { _, which ->
                showMcpServerOptionsDialog(servers[which])
            }
            .setPositiveButton("添加") { _, _ -> showEditMcpServerDialog() }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showMcpServerOptionsDialog(server: io.legado.app.ui.main.ai.AiMcpServerConfig) {
        val options = arrayOf(
            if (server.enabled) "禁用" else "启用",
            "编辑",
            "删除"
        )
        
        AlertDialog.Builder(requireContext())
            .setTitle(server.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleMcpServerEnabled(server)
                    1 -> showEditMcpServerDialog(server)
                    2 -> confirmDeleteMcpServer(server)
                }
            }
            .show()
    }
    
    private fun toggleMcpServerEnabled(server: io.legado.app.ui.main.ai.AiMcpServerConfig) {
        val servers = io.legado.app.help.config.AppConfig.aiMcpServers.toMutableList()
        val index = servers.indexOfFirst { it.id == server.id }
        if (index >= 0) {
            servers[index] = server.copy(enabled = !server.enabled)
            io.legado.app.help.config.AppConfig.aiMcpServers = servers
            Toast.makeText(requireContext(), "�?{if (server.enabled) "禁用" else "启用"}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showEditMcpServerDialog(server: io.legado.app.ui.main.ai.AiMcpServerConfig? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_mcp_server_edit, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_mcp_name)
        val etEndpoint = dialogView.findViewById<EditText>(R.id.et_mcp_endpoint)
        val etApiKey = dialogView.findViewById<EditText>(R.id.et_mcp_api_key)
        val switchEnabled = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_mcp_enabled)
        
        if (server != null) {
            etName.setText(server.name)
            etEndpoint.setText(server.endpoint)
            etApiKey.setText(server.apiKey)
            switchEnabled.isChecked = server.enabled
        } else {
            switchEnabled.isChecked = true
        }
        
        val title = if (server == null) "添加 MCP 服务�? else "编辑 MCP 服务�?
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val endpoint = etEndpoint.text.toString().trim()
                val apiKey = etApiKey.text.toString().trim()
                val enabled = switchEnabled.isChecked
                
                if (name.isEmpty() || endpoint.isEmpty()) {
                    Toast.makeText(requireContext(), "名称和端点不能为�?, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val servers = io.legado.app.help.config.AppConfig.aiMcpServers.toMutableList()
                val newServer = if (server != null) {
                    server.copy(name = name, endpoint = endpoint, apiKey = apiKey, enabled = enabled)
                } else {
                    io.legado.app.ui.main.ai.AiMcpServerConfig(
                        name = name,
                        endpoint = endpoint,
                        apiKey = apiKey,
                        enabled = enabled
                    )
                }
                
                if (server != null) {
                    val index = servers.indexOfFirst { it.id == server.id }
                    if (index >= 0) servers[index] = newServer
                } else {
                    servers.add(newServer)
                }
                
                io.legado.app.help.config.AppConfig.aiMcpServers = servers
                Toast.makeText(requireContext(), "已保�?, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun confirmDeleteMcpServer(server: io.legado.app.ui.main.ai.AiMcpServerConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除 MCP 服务�?)
            .setMessage("确定要删�?\"${server.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                val servers = io.legado.app.help.config.AppConfig.aiMcpServers.filterNot { it.id == server.id }
                io.legado.app.help.config.AppConfig.aiMcpServers = servers
                Toast.makeText(requireContext(), "已删�?, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== Tavily 统一配置 ====================

    /**
     * 获取搜索主题的中文标�?     */
    private fun getTopicLabel(topic: String): String {
        return when (topic) {
            "news" -> "新闻"
            "finance" -> "金融"
            else -> "通用"
        }
    }

    /**
     * 获取搜索深度的中文标�?     */
    private fun getDepthLabel(depth: String): String {
        return when (depth) {
            "advanced" -> "高级"
            "ultra-fast" -> "超快�?
            else -> "基础"
        }
    }

    /**
     * 显示统一�?Tavily 配置对话�?     */
    private fun showTavilyConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tavily_config, null)
        
        // 初始化控�?        val etApiKey = dialogView.findViewById<EditText>(R.id.et_tavily_api_key)
        val etBaseUrl = dialogView.findViewById<EditText>(R.id.et_tavily_base_url)
        val etTopic = dialogView.findViewById<AutoCompleteTextView>(R.id.et_tavily_topic)
        val etSearchDepth = dialogView.findViewById<AutoCompleteTextView>(R.id.et_tavily_search_depth)
        val etMaxResults = dialogView.findViewById<EditText>(R.id.et_tavily_max_results)
        
        // 加载当前配置
        etApiKey.setText(io.legado.app.help.config.AppConfig.aiTavilyApiKey.orEmpty())
        etBaseUrl.setText(io.legado.app.help.config.AppConfig.aiTavilyBaseUrl.orEmpty())
        etTopic.setText(getTopicLabel(io.legado.app.help.config.AppConfig.aiTavilyTopic.orEmpty()))
        etSearchDepth.setText(getDepthLabel(io.legado.app.help.config.AppConfig.aiTavilySearchDepth.orEmpty()))
        etMaxResults.setText(io.legado.app.help.config.AppConfig.aiTavilyMaxResults.toString())
        
        // 设置下拉选项
        val topicOptions = listOf("通用", "新闻", "金融")
        val topicAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, topicOptions)
        etTopic.setAdapter(topicAdapter)
        
        val depthOptions = listOf("基础", "高级", "超快�?)
        val depthAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, depthOptions)
        etSearchDepth.setAdapter(depthAdapter)
        
        // 点击下拉框时显示选项
        etTopic.setOnClickListener {
            etTopic.showDropDown()
        }
        
        etSearchDepth.setOnClickListener {
            etSearchDepth.showDropDown()
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Tavily 联网搜索配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                // 验证 API Key
                val apiKey = etApiKey.text.toString().trim()
                if (apiKey.isEmpty()) {
                    Toast.makeText(requireContext(), "API Key 不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 验证 Base URL
                val baseUrl = etBaseUrl.text.toString().trim()
                if (baseUrl.isEmpty()) {
                    Toast.makeText(requireContext(), "Base URL 不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 验证最大结果数
                val maxResultsText = etMaxResults.text.toString().trim()
                val maxResults = maxResultsText.toIntOrNull()
                if (maxResults == null || maxResults < 1 || maxResults > 10) {
                    Toast.makeText(requireContext(), "最大结果数必须�?1-10 之间", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 转换主题为英文�?                val topicValue = when (etTopic.text.toString()) {
                    "新闻" -> "news"
                    "金融" -> "finance"
                    else -> "general"
                }
                
                // 转换深度为英文�?                val depthValue = when (etSearchDepth.text.toString()) {
                    "高级" -> "advanced"
                    "超快�? -> "ultra-fast"
                    else -> "basic"
                }
                
                // 保存配置
                io.legado.app.help.config.AppConfig.aiTavilyApiKey = apiKey
                io.legado.app.help.config.AppConfig.aiTavilyBaseUrl = baseUrl
                io.legado.app.help.config.AppConfig.aiTavilyTopic = topicValue
                io.legado.app.help.config.AppConfig.aiTavilySearchDepth = depthValue
                io.legado.app.help.config.AppConfig.aiTavilyMaxResults = maxResults
                
                updateSummary()
                Toast.makeText(requireContext(), "配置已保�?, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复默认") { _, _ ->
                etApiKey.setText("")
                etBaseUrl.setText("https://api.tavily.com")
                etTopic.setText("通用")
                etSearchDepth.setText("基础")
                etMaxResults.setText("5")
            }
            .show()
    }
}