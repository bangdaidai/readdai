package io.legado.app.ui.config

import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityAiProviderDetailBinding
import io.legado.app.help.ai.AiApiClient
import io.legado.app.help.ai.AiApiKey
import io.legado.app.help.ai.AiDao
import io.legado.app.help.ai.AiDatabase
import io.legado.app.help.ai.AiProviderEntity
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AiProviderDetailActivity : BaseActivity<ActivityAiProviderDetailBinding>() {

    override val binding by viewBinding(ActivityAiProviderDetailBinding::inflate)

    private lateinit var aiDao: AiDao
    private var providerIdentifier: String = ""
    private var isNewProvider: Boolean = false
    private var currentProvider: AiProviderEntity? = null
    private var apiKeys: MutableList<AiApiKey> = mutableListOf()
    private lateinit var apiKeyAdapter: ApiKeyAdapter

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        aiDao = AiDatabase.getInstance(this).aiDao()
        isNewProvider = intent.getBooleanExtra("isNew", false)
        val isCustomProvider = intent.getBooleanExtra("isCustom", false)

        providerIdentifier = if (isNewProvider && isCustomProvider) {
            "custom_${System.currentTimeMillis()}"
        } else {
            intent.getStringExtra("identifier") ?: ""
        }

        initViews()
        loadData()
    }

    override fun shouldHandleImePadding(): Boolean {
        // 服务商配置页面有多个输入框，需要处理输入法遮挡
        return true
    }

    private fun initViews() {
        binding.titleBar.setNavigationOnClickListener {
            finish()
        }
        binding.titleBar.title = if (isNewProvider) "添加服务商" else "编辑服务商"
        if (isNewProvider) {
            val title = intent.getStringExtra("title") ?: ""
            val defaultUrl = intent.getStringExtra("defaultUrl") ?: ""
            val defaultModel = intent.getStringExtra("defaultModel") ?: ""
            binding.etName.setText(title)
            binding.etApiUrl.setText(defaultUrl)
            binding.etModel.setText(defaultModel)
        }

        apiKeyAdapter = ApiKeyAdapter(apiKeys) { position ->
            apiKeys.removeAt(position)
            apiKeyAdapter.notifyDataSetChanged()
        }
        binding.rvApiKeys.layoutManager = LinearLayoutManager(this)
        binding.rvApiKeys.adapter = apiKeyAdapter

        binding.btnFetchModels.setOnClickListener { fetchModels() }
        binding.btnAddKey.setOnClickListener { addApiKey() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveProvider() }

        // Apply theme accent color to buttons
        binding.btnFetchModels.setTextColor(accentColor)
        binding.btnAddKey.setTextColor(accentColor)
        binding.btnTestConnection.setTextColor(accentColor)
        binding.btnSave.setTextColor(accentColor)

        // Apply theme accent color to toggle groups (segmented buttons)
        // 选中时：强调色边框 + 强调色文字
        
        // Protocol toggle group
        binding.toggleProtocol.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val hint = when (checkedId) {
                    R.id.btn_claude -> "例如: https://api.anthrop.com/v1"
                    R.id.btn_gemini -> "例如: https://generativelanguage.googleapis.com/v1beta"
                    else -> "例如: https://api.openai.com/v1 或 https://api.longcat.chat/openai/v1/chat/completions"
                }
                binding.tilApiUrl.helperText = hint
                
                binding.toggleProtocol.findViewById<MaterialButton>(checkedId)?.apply {
                    strokeColor = android.content.res.ColorStateList.valueOf(accentColor)
                    setTextColor(accentColor)
                }
            }
        }
        
        // Reasoning toggle group
        binding.toggleReasoning.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                binding.toggleReasoning.findViewById<MaterialButton>(checkedId)?.apply {
                    strokeColor = android.content.res.ColorStateList.valueOf(accentColor)
                    setTextColor(accentColor)
                }
            }
        }
        
        // Apply theme accent color to TextInputLayout focused border
        // ✅ 关键修复：使用 boxStrokeColor 替代 boxStrokeColorStateList（兼容旧版本 Material Design）
        
        // Name input
        binding.etName.parent?.let {
            if (it is com.google.android.material.textfield.TextInputLayout) {
                it.boxStrokeColor = accentColor
            }
        }
        
        // API URL input
        binding.tilApiUrl.boxStrokeColor = accentColor
        
        // Model input
        binding.etModel.parent?.let {
            if (it is com.google.android.material.textfield.TextInputLayout) {
                it.boxStrokeColor = accentColor
            }
        }
    }

    private fun loadData() {
        if (isNewProvider) {
            apiKeys.add(AiApiKey(id = UUID.randomUUID().toString(), key = "", enabled = true))
            apiKeyAdapter.notifyDataSetChanged()
            return
        }

        lifecycleScope.launch {
            val provider = aiDao.getProvider(providerIdentifier)
            provider?.let {
                currentProvider = it
                binding.etName.setText(it.title)
                binding.etApiUrl.setText(it.apiUrl)
                binding.etModel.setText(it.model)

                when (it.protocol) {
                    "claude" -> binding.toggleProtocol.check(R.id.btn_claude)
                    "gemini" -> binding.toggleProtocol.check(R.id.btn_gemini)
                    else -> binding.toggleProtocol.check(R.id.btn_openai)
                }

                when (it.reasoningEffort) {
                    "low" -> binding.toggleReasoning.check(R.id.btn_low)
                    "medium" -> binding.toggleReasoning.check(R.id.btn_medium)
                    "high" -> binding.toggleReasoning.check(R.id.btn_high)
                    else -> binding.toggleReasoning.check(R.id.btn_auto)
                }

                apiKeys.clear()
                apiKeys.addAll(it.getApiKeyList())
                if (apiKeys.isEmpty()) {
                    apiKeys.add(AiApiKey(id = UUID.randomUUID().toString(), key = "", enabled = true))
                }
                apiKeyAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun fetchModels() {
        val provider = buildCurrentProvider()
        if (!provider.hasValidKey()) {
            Toast.makeText(this, "请先添加有效的API Key", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnFetchModels.isEnabled = false
        binding.btnFetchModels.text = "获取中..."

        lifecycleScope.launch {
            val client = AiApiClient(provider)
            val result = client.fetchModels()

            result.onSuccess { models ->
                if (models.isEmpty()) {
                    Toast.makeText(this@AiProviderDetailActivity, "未找到可用模型", Toast.LENGTH_SHORT).show()
                } else {
                    showModelSelectionDialog(models)
                }
            }.onFailure { e ->
                Toast.makeText(this@AiProviderDetailActivity, "获取失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            binding.btnFetchModels.isEnabled = true
            binding.btnFetchModels.text = "获取模型"
        }
    }

    private fun showModelSelectionDialog(models: List<String>) {
        val modelsArray = models.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择模型")
            .setItems(modelsArray) { _, which ->
                binding.etModel.setText(modelsArray[which])
            }
            .show()
    }

    private fun addApiKey() {
        apiKeys.add(AiApiKey(id = UUID.randomUUID().toString(), key = "", enabled = true))
        apiKeyAdapter.notifyItemInserted(apiKeys.size - 1)
    }

    private fun testConnection() {
        val provider = buildCurrentProvider()
        if (!provider.hasValidKey()) {
            Toast.makeText(this, "请先添加有效的API Key", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "测试中..."

        lifecycleScope.launch {
            val client = AiApiClient(provider)
            val result = client.testConnection()

            result.onSuccess {
                Toast.makeText(this@AiProviderDetailActivity, "连接成功!", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@AiProviderDetailActivity, "连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            binding.btnTestConnection.isEnabled = true
            binding.btnTestConnection.text = "测试连接"
        }
    }

    private fun buildCurrentProvider(): AiProviderEntity {
        val protocol = when (binding.toggleProtocol.checkedButtonId) {
            R.id.btn_claude -> "claude"
            R.id.btn_gemini -> "gemini"
            else -> "openai"
        }

        val reasoning = when (binding.toggleReasoning.checkedButtonId) {
            R.id.btn_low -> "low"
            R.id.btn_medium -> "medium"
            R.id.btn_high -> "high"
            else -> "auto"
        }

        val isCustomProvider = intent.getBooleanExtra("isCustom", false)

        return AiProviderEntity(
            identifier = providerIdentifier,
            title = binding.etName.text.toString(),
            apiUrl = binding.etApiUrl.text.toString(),
            model = binding.etModel.text.toString(),
            protocol = protocol,
            reasoningEffort = reasoning,
            enabled = true,
            isBuiltin = !isCustomProvider,
            isDefault = currentProvider?.isDefault ?: false,
            keyIndex = currentProvider?.keyIndex ?: 0,
            createdAt = currentProvider?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ).setApiKeyList(apiKeys)
    }

    fun saveProvider() {
        val name = binding.etName.text.toString()

        if (name.isBlank()) {
            Toast.makeText(this, "请填写服务商名称", Toast.LENGTH_SHORT).show()
            return
        }

        val provider = buildCurrentProvider()

        lifecycleScope.launch {
            aiDao.insertProvider(provider)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AiProviderDetailActivity, "保存成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class ApiKeyAdapter(
        private val keys: MutableList<AiApiKey>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<ApiKeyAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val etLabel: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.et_label)
            val etKey: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.et_key)
            val switchEnabled: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.switch_enabled)
            val btnDelete: View = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_ai_api_key, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val key = keys[position]
            holder.etLabel.setText(key.label ?: "")
            holder.etKey.setText(key.key)
            holder.switchEnabled.isChecked = key.enabled

            // Apply theme accent color to switch
            val accentColorStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    accentColor,
                    android.graphics.Color.parseColor("#80808080") // Unchecked track color
                )
            )
            holder.switchEnabled.trackTintList = accentColorStateList
            holder.switchEnabled.thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)

            // Apply theme accent color to TextInputLayout focused border
            // ✅ 关键修复：使用 boxStrokeColor 替代 boxStrokeColorStateList（兼容旧版本 Material Design）
            
            holder.etLabel.parent?.let {
                if (it is com.google.android.material.textfield.TextInputLayout) {
                    it.boxStrokeColor = accentColor
                }
            }
            
            holder.etKey.parent?.let {
                if (it is com.google.android.material.textfield.TextInputLayout) {
                    it.boxStrokeColor = accentColor
                }
            }

            // Apply theme accent color to delete button
            if (holder.btnDelete is android.widget.Button) {
                (holder.btnDelete as android.widget.Button).setTextColor(accentColor)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        updateKey(pos, holder)
                    }
                }
            }

            holder.etLabel.addTextChangedListener(textWatcher)
            holder.etKey.addTextChangedListener(textWatcher)

            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    keys[pos] = key.copy(enabled = isChecked)
                }
            }

            holder.btnDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (keys.size > 1) {
                        onDelete(pos)
                    } else {
                        Toast.makeText(this@AiProviderDetailActivity, "至少保留一个Key", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private fun updateKey(position: Int, holder: ViewHolder) {
            keys[position] = keys[position].copy(
                label = holder.etLabel.text.toString().ifBlank { null },
                key = holder.etKey.text.toString()
            )
        }

        override fun getItemCount() = keys.size
    }
}
