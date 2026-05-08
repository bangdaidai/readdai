package io.legado.app.help.ai

import android.content.Context

/**
 * AI功能初始化器
 * 在应用启动时调用
 */
object AiInitializer {

    /**
     * 初始化AI功能
     * 应该在Application.onCreate中调用
     */
    fun init(context: Context) {
        // 初始化对话历史存储
        AiHistoryStore.init(context)

        // 初始化数据库
        val db = AiDatabase.getInstance(context)
        
        // 初始化默认提示词（如果没有的话）
        kotlinx.coroutines.runBlocking {
            val prompts = db.aiDao().getAllPrompts()
            if (prompts.isEmpty()) {
                PromptManager(context).initDefaultPrompts()
            }
            
            // 初始化默认技能（如果没有的话）
            val skills = db.aiDao().getAllSkills()
            if (skills.isEmpty()) {
                SkillManager(context).initDefaultSkills()
            }
        }
    }
}

/**
 * AI配置管理器
 * 用于管理AI服务商的配置
 */
object AiConfigManager {

    private const val KEY_SELECTED_PROVIDER = "ai_selected_provider"
    private const val KEY_RPM_LIMIT = "ai_rpm_limit"
    private const val KEY_KEY_ROTATION = "ai_key_rotation"

    /**
     * 获取当前选中的服务商ID
     */
    fun getSelectedProviderId(context: Context): String? {
        val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_PROVIDER, null)
    }

    /**
     * 设置当前选中的服务商ID
     */
    fun setSelectedProviderId(context: Context, providerId: String) {
        val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_PROVIDER, providerId).apply()
    }

    /**
     * 获取每分钟请求限制
     */
    fun getRpmLimit(context: Context): Int {
        val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RPM_LIMIT, 60)
    }

    /**
     * 设置每分钟请求限制
     */
    fun setRpmLimit(context: Context, rpm: Int) {
        val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RPM_LIMIT, rpm).apply()
    }

    /**
     * 是否启用密钥轮换
     */
    fun isKeyRotationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_KEY_ROTATION, true)
    }

    /**
     * 设置密钥轮换
     */
    fun setKeyRotationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_KEY_ROTATION, enabled).apply()
    }
}
