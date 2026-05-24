package io.legado.app.ui.book.read

import android.content.Context
import android.util.Log
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * 文本菜单项配置管理
 *
 * 功能说明：
 * 管理文本操作菜单项的显示/隐藏配置
 *
 * 使用方法：
 * 1. 获取所有菜单项：TextMenuConfig.getAllMenuItems()
 * 2. 获取隐藏的菜单项ID：TextMenuConfig.getHiddenMenuItemIds(context)
 * 3. 设置隐藏的菜单项：TextMenuConfig.setHiddenMenuItemIds(context, ids)
 * 4. 切换菜单项显示状态：TextMenuConfig.toggleMenuItem(context, itemId)
 */
object TextMenuConfig {

    private const val TAG = "TextMenuConfig"

    /**
     * 菜单项信息
     * @param id 菜单项ID
     * @param name 菜单项名称资源ID
     * @param defaultVisible 默认是否可见
     */
    data class MenuItemInfo(
        val id: Int,
        val nameResId: Int,
        val defaultVisible: Boolean = true
    )

    /**
     * 所有可配置的菜单项
     * 注意：这个列表的顺序决定了菜单项的显示顺序
     */
    val ALL_MENU_ITEMS = listOf(
        // 阅读相关功能
        MenuItemInfo(R.id.menu_replace, R.string.replace),
        MenuItemInfo(R.id.menu_copy, android.R.string.copy),
        MenuItemInfo(R.id.menu_annotation, R.string.annotation),
        MenuItemInfo(R.id.menu_protagonist, R.string.protagonist),
        MenuItemInfo(R.id.menu_aloud, R.string.read_aloud),
        MenuItemInfo(R.id.menu_dict, R.string.dict),
        // AI 相关功能
        MenuItemInfo(R.id.menu_ai_explain, R.string.ai_explain),
        MenuItemInfo(R.id.menu_ai_analyze, R.string.ai_analyze),
        MenuItemInfo(R.id.menu_ai_chat, R.string.ai_chat),
        // 其他功能
        MenuItemInfo(R.id.menu_search_content, R.string.search_content),
        MenuItemInfo(R.id.menu_browser, R.string.browser),
        MenuItemInfo(R.id.menu_share_str, R.string.share)
    )

    /**
     * 获取所有菜单项列表
     */
    fun getAllMenuItems(): List<MenuItemInfo> = ALL_MENU_ITEMS

    /**
     * 获取隐藏的菜单项ID集合
     */
    fun getHiddenMenuItemIds(context: Context): Set<Int> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenTextMenuItems, "")
        Log.d(TAG, "getHiddenMenuItemIds: hiddenStr='$hiddenStr'")
        return if (hiddenStr.isNullOrEmpty()) {
            emptySet()
        } else {
            val ids = hiddenStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            Log.d(TAG, "getHiddenMenuItemIds: ids=$ids")
            ids
        }
    }

    /**
     * 设置隐藏的菜单项ID集合
     */
    fun setHiddenMenuItemIds(context: Context, ids: Set<Int>) {
        val hiddenStr = ids.joinToString(",")
        Log.d(TAG, "setHiddenMenuItemIds: key=${PreferKey.hiddenTextMenuItems}, ids=$ids, hiddenStr='$hiddenStr'")
        context.putPrefString(PreferKey.hiddenTextMenuItems, hiddenStr)
        // 立即验证是否保存成功
        val savedStr = context.getPrefString(PreferKey.hiddenTextMenuItems, "NOT_FOUND")
        Log.d(TAG, "setHiddenMenuItemIds: 验证保存结果, savedStr='$savedStr'")
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
        context.putPrefString(PreferKey.hiddenTextMenuItems, "")
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
        val hiddenStr = context.getPrefString(PreferKey.hiddenProcessTextItems, "")
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
        context.putPrefString(PreferKey.hiddenProcessTextItems, hiddenStr)
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
        context.putPrefString(PreferKey.hiddenProcessTextItems, "")
    }
}
