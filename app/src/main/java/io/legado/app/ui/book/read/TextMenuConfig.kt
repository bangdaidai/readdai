package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString

object TextMenuConfig {

    const val DEFAULT_VISIBLE_COUNT = 7
    const val MIN_VISIBLE_COUNT = 3
    const val MAX_VISIBLE_COUNT = 15

    data class MenuItemInfo(
        val id: Int,
        val nameResId: Int,
        val defaultVisible: Boolean = true
    )

    val ALL_MENU_ITEMS = listOf(
        MenuItemInfo(R.id.menu_replace, R.string.replace),
        MenuItemInfo(R.id.menu_copy, android.R.string.copy),
        MenuItemInfo(R.id.menu_bookmark, R.string.bookmark),
        MenuItemInfo(R.id.menu_annotation, R.string.annotation),
        MenuItemInfo(R.id.menu_highlight_rule, R.string.highlight),
        MenuItemInfo(R.id.menu_protagonist, R.string.protagonist),
        MenuItemInfo(R.id.menu_aloud, R.string.read_aloud),
        MenuItemInfo(R.id.menu_dict, R.string.dict),
        MenuItemInfo(R.id.menu_web_search, R.string.web_search),
        MenuItemInfo(R.id.menu_ai_explain, R.string.ai_explain),
        MenuItemInfo(R.id.menu_ai_analyze, R.string.ai_analyze),
        MenuItemInfo(R.id.menu_ai_chat, R.string.ai_chat),
        MenuItemInfo(R.id.menu_search_content, R.string.search_content),
        MenuItemInfo(R.id.menu_browser, R.string.browser),
        MenuItemInfo(R.id.menu_share_str, R.string.share)
    )

    fun getAllMenuItems(): List<MenuItemInfo> = ALL_MENU_ITEMS

    fun getDefaultMenuTitle(context: Context, item: MenuItemInfo): String {
        return context.getString(item.nameResId)
    }

    fun getCustomMenuTitles(context: Context): Map<Int, String> {
        val json = context.getPrefString(PreferKey.textMenuCustomTitles)
        return GSON.fromJsonObject<Map<String, String>>(json).getOrNull()
            ?.mapNotNull { (key, value) ->
                key.toIntOrNull()?.let { id -> id to value }
            }
            ?.toMap()
            ?: emptyMap()
    }

    fun getCustomMenuTitle(context: Context, itemId: Int): String? {
        return getCustomMenuTitles(context)[itemId]?.takeIf { it.isNotBlank() }
    }

    fun getMenuTitle(context: Context, item: MenuItemInfo): String {
        return getCustomMenuTitle(context, item.id) ?: getDefaultMenuTitle(context, item)
    }

    fun setCustomMenuTitle(context: Context, itemId: Int, title: String?) {
        val titles = getCustomMenuTitles(context).toMutableMap()
        val normalizedTitle = title?.trim().orEmpty()
        if (normalizedTitle.isBlank()) {
            titles.remove(itemId)
        } else {
            titles[itemId] = normalizedTitle
        }
        context.putPrefString(PreferKey.textMenuCustomTitles, GSON.toJson(titles))
    }

    fun getTextMenuVisibleCount(context: Context): Int {
        return context.getPrefInt(
            PreferKey.textMenuVisibleCount,
            DEFAULT_VISIBLE_COUNT
        ).coerceIn(MIN_VISIBLE_COUNT, MAX_VISIBLE_COUNT)
    }

    fun setTextMenuVisibleCount(context: Context, count: Int) {
        context.putPrefInt(
            PreferKey.textMenuVisibleCount,
            count.coerceIn(MIN_VISIBLE_COUNT, MAX_VISIBLE_COUNT)
        )
    }

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

    fun isMenuItemHidden(context: Context, itemId: Int): Boolean {
        return itemId in getHiddenMenuItemIds(context)
    }

    fun toggleMenuItem(context: Context, itemId: Int): Boolean {
        val hiddenIds = getHiddenMenuItemIds(context).toMutableSet()
        if (itemId in hiddenIds) {
            hiddenIds.remove(itemId)
        } else {
            hiddenIds.add(itemId)
        }
        setHiddenMenuItemIds(context, hiddenIds)
        return itemId !in hiddenIds
    }

    fun resetToDefault(context: Context) {
        context.putPrefString(PreferKey.hiddenTextMenuItems, "")
        context.putPrefString(PreferKey.textMenuCustomTitles, "")
        setTextMenuVisibleCount(context, DEFAULT_VISIBLE_COUNT)
    }

    fun getProcessTextItemKey(packageName: String, className: String): String {
        return "$packageName/$className"
    }

    fun getCustomProcessTextTitles(context: Context): Map<String, String> {
        val json = context.getPrefString(PreferKey.processTextCustomTitles)
        return GSON.fromJsonObject<Map<String, String>>(json).getOrNull() ?: emptyMap()
    }

    fun getCustomProcessTextTitle(context: Context, key: String): String? {
        return getCustomProcessTextTitles(context)[key]?.takeIf { it.isNotBlank() }
    }

    fun setCustomProcessTextTitle(context: Context, key: String, title: String?) {
        val titles = getCustomProcessTextTitles(context).toMutableMap()
        val normalizedTitle = title?.trim().orEmpty()
        if (normalizedTitle.isBlank()) {
            titles.remove(key)
        } else {
            titles[key] = normalizedTitle
        }
        context.putPrefString(PreferKey.processTextCustomTitles, GSON.toJson(titles))
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
        context.putPrefString(PreferKey.processTextCustomTitles, "")
    }
}
