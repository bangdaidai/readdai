package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.rag.EmbeddingService
import io.legado.app.help.ai.rag.VectorConfig
import io.legado.app.help.ai.rag.VectorConfigManager
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 向量设置Fragment
 */
class VectorSettingsFragment : Fragment() {

    private lateinit var switchEnabled: SwitchMaterial
    private lateinit var spinnerProvider: Spinner
    private lateinit var spinnerModel: Spinner
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var seekbarChunkSize: SeekBar
    private lateinit var tvChunkSize: TextView
    private lateinit var seekbarChunkOverlap: SeekBar
    private lateinit var tvChunkOverlap: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnTest: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vector_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        loadSettings()
    }

    private fun initViews(view: View) {
        switchEnabled = view.findViewById(R.id.switch_vector_enabled)
        spinnerProvider = view.findViewById(R.id.spinner_provider)
        spinnerModel = view.findViewById(R.id.spinner_model)
        etApiKey = view.findViewById(R.id.et_api_key)
        etBaseUrl = view.findViewById(R.id.et_base_url)
        seekbarChunkSize = view.findViewById(R.id.seekbar_chunk_size)
        tvChunkSize = view.findViewById(R.id.tv_chunk_size)
        seekbarChunkOverlap = view.findViewById(R.id.seekbar_chunk_overlap)
        tvChunkOverlap = view.findViewById(R.id.tv_chunk_overlap)
        btnSave = view.findViewById(R.id.btn_save)
        btnTest = view.findViewById(R.id.btn_test)

        // 设置提供商列表
        val providers = VectorConfigManager.getProviders()
        val providerNames = providers.map { it.second }
        spinnerProvider.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            providerNames
        )

        // 提供商选择监听
        spinnerProvider.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateModelsSpinner()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // SeekBar监听
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

        // 保存按钮
        btnSave.setOnClickListener { saveSettings() }

        // 测试按钮
        btnTest.setOnClickListener { testConnection() }
    }

    private fun updateModelsSpinner() {
        val providers = VectorConfigManager.getProviders()
        val selectedProvider = providers[spinnerProvider.selectedItemPosition].first
        val models = VectorConfigManager.getModelsByProvider(selectedProvider)
        val modelNames = models.map { it.second }
        
        spinnerModel.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            modelNames
        )
    }

    private fun loadSettings() {
        val config = VectorConfigManager.getConfig()

        switchEnabled.isChecked = config.enabled
        etApiKey.setText(config.apiKey)
        etBaseUrl.setText(config.baseUrl)
        
        // 设置提供商
        val providers = VectorConfigManager.getProviders()
        val providerIndex = providers.indexOfFirst { it.first == config.modelProvider }
        if (providerIndex >= 0) {
            spinnerProvider.setSelection(providerIndex)
        }
        
        // 更新模型列表后设置模型
        updateModelsSpinner()
        val models = VectorConfigManager.getModelsByProvider(config.modelProvider)
        val modelIndex = models.indexOfFirst { it.first == config.modelName }
        if (modelIndex >= 0) {
            spinnerModel.setSelection(modelIndex)
        }
        
        // 设置参数
        seekbarChunkSize.progress = config.chunkSize
        tvChunkSize.text = config.chunkSize.toString()
        seekbarChunkOverlap.progress = config.chunkOverlap
        tvChunkOverlap.text = config.chunkOverlap.toString()
    }

    private fun saveSettings() {
        val providers = VectorConfigManager.getProviders()
        val models = VectorConfigManager.getModelsByProvider(providers[spinnerProvider.selectedItemPosition].first)

        val config = VectorConfig(
            enabled = switchEnabled.isChecked,
            modelProvider = providers[spinnerProvider.selectedItemPosition].first,
            modelName = models[spinnerModel.selectedItemPosition].first,
            apiKey = etApiKey.text.toString(),
            baseUrl = etBaseUrl.text.toString(),
            chunkSize = seekbarChunkSize.progress,
            chunkOverlap = seekbarChunkOverlap.progress,
            batchSize = 20
        )

        VectorConfigManager.saveConfig(config)
        Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        if (etApiKey.text.isNullOrBlank()) {
            Toast.makeText(requireContext(), "请输入API Key", Toast.LENGTH_SHORT).show()
            return
        }

        btnTest.isEnabled = false
        btnTest.text = "测试中..."

        val config = VectorConfig(
            enabled = true,
            modelProvider = VectorConfigManager.getProviders()[spinnerProvider.selectedItemPosition].first,
            modelName = VectorConfigManager.getModelsByProvider(
                VectorConfigManager.getProviders()[spinnerProvider.selectedItemPosition].first
            )[spinnerModel.selectedItemPosition].first,
            apiKey = etApiKey.text.toString(),
            baseUrl = etBaseUrl.text.toString()
        )

        val service = EmbeddingService(config)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val result = service.testConnection()
                withContext(Dispatchers.Main) {
                    btnTest.isEnabled = true
                    btnTest.text = "测试连接"

                    if (result.isSuccess) {
                        Toast.makeText(requireContext(), "连接成功!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "连接失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnTest.isEnabled = true
                    btnTest.text = "测试连接"
                    Toast.makeText(requireContext(), "测试失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
