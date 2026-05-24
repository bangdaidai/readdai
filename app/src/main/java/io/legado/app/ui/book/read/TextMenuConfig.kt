package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

object TextMenuConfig {

    data class MenuItemInfo(
        val id: Int,
        val nameResId: Int,
        val defaultVisible: Boolean = true
    )

    val ALL_MENU_ITEMS = listOf(
        MenuItemInfo(R.id.menu_replace, R.string.replace),
        MenuItemInfo(R.id.menu_copy, android.R.string.copy),
        MenuItemInfo(R.id.menu_annotation, R.string.annotation),
        MenuItemInfo(R.id.menu_protagonist, R.string.protagonist),
        MenuItemInfo(R.id.menu_aloud, R.string.read_aloud),
        MenuItemInfo(R.id.menu_dict, R.string.dict),
        MenuItemInfo(R.id.menu_ai_explain, R.string.ai_explain),
        MenuItemInfo(R.id.menu_ai_analyze, R.string.ai_analyze),
        MenuItemInfo(R.id.menu_ai_chat, R.string.ai_chat),
        MenuItemInfo(R.id.menu_search_content, R.string.search_content),
        MenuItemInfo(R.id.menu_browser, R.string.browser),
        MenuItemInfo(R.id.menu_share_str, R.string.share)
    )

    fun getAllMenuItems(): List<MenuItemInfo> = ALL_MENU_ITEMS

    fun getHiddenMenuItemIds(context: Context): Set<Int> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenTextMenuItems, "")
        return if (hiddenStr.isNullOrEmpty()) {
            emptySet()
        } else {
            hiddenStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
    }

    fun setHiddenMenuItemIds(context: Context, ids: Set<Int>) {
        val hiddenStr = ids.joinToString(",")
        context.putPrefString(PreferKey.hiddenTextMenuItems, hiddenStr)
    }

    fun toggleMenuItem(context: Context, itemId: Int): Boolean {
        val hiddenIds = getHiddenMenuItemIds(context).toMutableSet()
        val isCurrentlyHidden = itemId in hiddenIds
        if (isCurrentlyHidden) {
            hiddenIds.remove(itemId)
        } else {
            hiddenIds.add(itemId)
        }
        setHiddenMenuItemIds(context, hiddenIds)
        return !isCurrentlyHidden
    }

    fun isMenuItemHidden(context: Context, itemId: Int): Boolean {
        return itemId in getHiddenMenuItemIds(context)
    }

    fun resetToDefault(context: Context) {
        context.putPrefString(PreferKey.hiddenTextMenuItems, "")
    }

    fun getProcessTextItemKey(packageName: String, className: String): String {
        return "$packageName/$className"
    }

    fun getHiddenProcessTextItems(context: Context): Set<String> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenProcessTextItems, "")
        return if (hiddenStr.isNullOrEmpty()) {
            emptySet()
        } else {
            hiddenStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    fun setHiddenProcessTextItems(context: Context, keys: Set<String>) {
        val hiddenStr = keys.joinToString(",")
        context.putPrefString(PreferKey.hiddenProcessTextItems, hiddenStr)
    }

    fun isProcessTextItemHidden(context: Context, packageName: String, className: String): Boolean {
        val key = getProcessTextItemKey(packageName, className)
        return key in getHiddenProcessTextItems(context)
    }

    fun resetProcessTextConfig(context: Context) {
        context.putPrefString(PreferKey.hiddenProcessTextItems, "")
    }
}
