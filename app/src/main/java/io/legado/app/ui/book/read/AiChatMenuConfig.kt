package io.legado.app.ui.book.read

import android.content.Context
import android.util.Log
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * AI 对话页面文本菜单项配置管理
 *
 * 功能说明：
 * 管理 AI 对话页面文本操作菜单项的显示/隐藏配置
 *
 * 菜单项：
 * - 复制
 * - 搜书
 * - 追问
 */
object AiChatMenuConfig {

    private const val TAG = "AiChatMenuConfig"

    /**
     * 菜单项信息
     */
    data class MenuItemInfo(
        val id: Int,
        val nameResId: Int,
        val defaultVisible: Boolean = true
    )

    /**
     * AI 对话页面所有可配置的菜单项
     */
    val ALL_MENU_ITEMS = listOf(
        MenuItemInfo(R.id.menu_copy, android.R.string.copy),
        MenuItemInfo(R.id.menu_search_content, R.string.search_book),
        MenuItemInfo(R.id.menu_ai_chat, R.string.follow_up_question)
    )

    /**
     * 获取所有菜单项列表
     */
    fun getAllMenuItems(): List<MenuItemInfo> = ALL_MENU_ITEMS

    /**
     * 获取隐藏的菜单项ID集合
     */
    fun getHiddenMenuItemIds(context: Context): Set<Int> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenAiChatMenuItems, "")
        Log.d(TAG, "getHiddenMenuItemIds: hiddenStr='$hiddenStr'")
        return if (hiddenStr.isNullOrEmpty()) {
            emptySet()
        } else {
            hiddenStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
    }

    /**
     * 设置隐藏的菜单项ID集合
     */
    fun setHiddenMenuItemIds(context: Context, ids: Set<Int>) {
        val hiddenStr = ids.joinToString(",")
        Log.d(TAG, "setHiddenMenuItemIds: ids=$ids, hiddenStr='$hiddenStr'")
        context.putPrefString(PreferKey.hiddenAiChatMenuItems, hiddenStr)
    }

    /**
     * 切换菜单项的显示/隐藏状态
     * @return 切换后的状态（true=显示，false=隐藏）
     */
    fun toggleMenuItem(context: Context, itemId: Int): Boolean {
        val hiddenIds = getHiddenMenuItemIds(context).toMutableSet()
        val isCurrentlyHidden = itemId in hiddenIds

        Log.d(TAG, "toggleMenuItem: itemId=$itemId, isCurrentlyHidden=$isCurrentlyHidden")

        if (isCurrentlyHidden) {
            hiddenIds.remove(itemId)
        } else {
            hiddenIds.add(itemId)
        }

        setHiddenMenuItemIds(context, hiddenIds)
        val newState = !isCurrentlyHidden
        Log.d(TAG, "toggleMenuItem: newState=$newState")
        return newState
    }

    /**
     * 检查菜单项是否被隐藏
     */
    fun isMenuItemHidden(context: Context, itemId: Int): Boolean {
        return itemId in getHiddenMenuItemIds(context)
    }

    /**
     * 重置为默认配置（所有菜单项都显示）
     */
    fun resetToDefault(context: Context) {
        Log.d(TAG, "resetToDefault")
        context.putPrefString(PreferKey.hiddenAiChatMenuItems, "")
    }

    // ==================== 其他应用菜单配置 ====================

    /**
     * 生成其他应用菜单项的唯一标识
     * 格式：包名/类名
     */
    fun getProcessTextItemKey(packageName: String, className: String): String {
        return "$packageName/$className"
    }

    /**
     * 获取隐藏的其他应用菜单项集合
     */
    fun getHiddenProcessTextItems(context: Context): Set<String> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenAiChatProcessTextItems, "")
        Log.d(TAG, "getHiddenProcessTextItems: hiddenStr='$hiddenStr'")
        return if (hiddenStr.isNullOrEmpty()) {
            emptySet()
        } else {
            hiddenStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    /**
     * 设置隐藏的其他应用菜单项集合
     */
    fun setHiddenProcessTextItems(context: Context, keys: Set<String>) {
        val hiddenStr = keys.joinToString(",")
        Log.d(TAG, "setHiddenProcessTextItems: keys=$keys, hiddenStr='$hiddenStr'")
        context.putPrefString(PreferKey.hiddenAiChatProcessTextItems, hiddenStr)
    }

    /**
     * 检查其他应用菜单项是否被隐藏
     */
    fun isProcessTextItemHidden(context: Context, packageName: String, className: String): Boolean {
        val key = getProcessTextItemKey(packageName, className)
        return key in getHiddenProcessTextItems(context)
    }

    /**
     * 重置其他应用菜单配置（全部显示）
     */
    fun resetProcessTextConfig(context: Context) {
        Log.d(TAG, "resetProcessTextConfig")
        context.putPrefString(PreferKey.hiddenAiChatProcessTextItems, "")
    }
}
