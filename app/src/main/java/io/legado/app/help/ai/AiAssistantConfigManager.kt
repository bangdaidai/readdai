package io.legado.app.help.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * AI助手交互配置管理器
 * 管理空状态和快捷操作栏的自定义配置
 */
object AiAssistantConfigManager {
    
    private const val PREF_EMPTY_STATE_CONFIG = "ai_empty_state_config"
    private const val PREF_QUICK_ACTION_NO_SELECTION_CONFIG = "ai_quick_action_no_selection_config"  // 无选中文字
    private const val PREF_QUICK_ACTION_WITH_SELECTION_CONFIG = "ai_quick_action_with_selection_config"  // 有选中文字
    
    private val gson = Gson()
    
    /**
     * 空状态配置项
     */
    data class EmptyStateItem(
        val positionIndex: Int,          // 位置索引 0-3
        val type: ConfigType,            // SKILL 或 CUSTOM
        val skillId: String? = null,     // 如果type=SKILL
        val customName: String? = null,  // 如果type=CUSTOM，显示名称
        val customTrigger: String? = null, // 如果type=CUSTOM，触发词
        val customDescription: String? = null // 如果type=CUSTOM，描述
    )
    
    /**
     * 快捷操作配置项
     */
    data class QuickActionItem(
        val positionIndex: Int,          // 位置索引 0-3
        val type: ConfigType,            // SKILL 或 CUSTOM
        val skillId: String? = null,     // 如果type=SKILL
        val displayName: String? = null, // 如果type=CUSTOM，显示名称
        val triggerWord: String? = null  // 如果type=CUSTOM，触发词
    )
    
    /**
     * 配置类型
     */
    enum class ConfigType {
        SKILL,    // 使用Skill系统
        CUSTOM    // 自定义纯文本
    }
    
    /**
     * 获取空状态配置
     */
    fun getEmptyStateConfig(context: Context): List<EmptyStateItem> {
        val json = context.getPrefString(PREF_EMPTY_STATE_CONFIG, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<EmptyStateItem>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                getDefaultEmptyStateConfig()
            }
        } else {
            getDefaultEmptyStateConfig()
        }
    }
    
    /**
     * 保存空状态配置
     */
    fun saveEmptyStateConfig(context: Context, config: List<EmptyStateItem>) {
        val json = gson.toJson(config)
        context.putPrefString(PREF_EMPTY_STATE_CONFIG, json)
    }
    
    /**
     * 获取快捷操作栏配置
     * @param hasSelectedText 是否有选中的文字
     */
    fun getQuickActionBarConfig(context: Context, hasSelectedText: Boolean): List<QuickActionItem> {
        val prefKey = if (hasSelectedText) {
            PREF_QUICK_ACTION_WITH_SELECTION_CONFIG
        } else {
            PREF_QUICK_ACTION_NO_SELECTION_CONFIG
        }
        
        val json = context.getPrefString(prefKey, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<QuickActionItem>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                getDefaultQuickActionBarConfig(hasSelectedText)
            }
        } else {
            getDefaultQuickActionBarConfig(hasSelectedText)
        }
    }
    
    /**
     * 保存快捷操作栏配置
     * @param hasSelectedText 是否有选中的文字
     */
    fun saveQuickActionBarConfig(context: Context, config: List<QuickActionItem>, hasSelectedText: Boolean) {
        val prefKey = if (hasSelectedText) {
            PREF_QUICK_ACTION_WITH_SELECTION_CONFIG
        } else {
            PREF_QUICK_ACTION_NO_SELECTION_CONFIG
        }
        val json = gson.toJson(config)
        context.putPrefString(prefKey, json)
    }
    
    /**
     * 恢复默认空状态配置
     */
    fun restoreDefaultEmptyStateConfig(context: Context) {
        saveEmptyStateConfig(context, getDefaultEmptyStateConfig())
    }
    
    /**
     * 恢复默认快捷操作栏配置
     * @param hasSelectedText 是否有选中的文字
     */
    fun restoreDefaultQuickActionBarConfig(context: Context, hasSelectedText: Boolean) {
        saveQuickActionBarConfig(context, getDefaultQuickActionBarConfig(hasSelectedText), hasSelectedText)
    }
    
    /**
     * 获取默认空状态配置（4个位置）
     */
    private fun getDefaultEmptyStateConfig(): List<EmptyStateItem> {
        return listOf(
            EmptyStateItem(0, ConfigType.SKILL, skillId = "skill_summarize_chapter"),
            EmptyStateItem(1, ConfigType.SKILL, skillId = "skill_analyze_character"),
            EmptyStateItem(2, ConfigType.SKILL, skillId = "skill_theme_analysis"),
            EmptyStateItem(3, ConfigType.SKILL, skillId = "skill_recall")
        )
    }
    
    /**
     * 获取默认快捷操作栏配置（4个位置）
     * @param hasSelectedText 是否有选中的文字
     */
    private fun getDefaultQuickActionBarConfig(hasSelectedText: Boolean): List<QuickActionItem> {
        return if (hasSelectedText) {
            // 有选中文字：针对这段文字的操作
            listOf(
                QuickActionItem(0, ConfigType.CUSTOM, displayName = "解释这个", triggerWord = "解释这段话的意思"),
                QuickActionItem(1, ConfigType.CUSTOM, displayName = "为什么", triggerWord = "为什么会这样说？"),
                QuickActionItem(2, ConfigType.CUSTOM, displayName = "举个例子", triggerWord = "举个相关的例子"),
                QuickActionItem(3, ConfigType.CUSTOM, displayName = "换句话说", triggerWord = "用更简单的话重述")
            )
        } else {
            // 无选中文字：通用快捷操作
            listOf(
                QuickActionItem(0, ConfigType.SKILL, skillId = "skill_custom_qa"),
                QuickActionItem(1, ConfigType.SKILL, skillId = "skill_summarize_chapter"),
                QuickActionItem(2, ConfigType.SKILL, skillId = "skill_analyze_character"),
                QuickActionItem(3, ConfigType.SKILL, skillId = "skill_theme_analysis")
            )
        }
    }
}
