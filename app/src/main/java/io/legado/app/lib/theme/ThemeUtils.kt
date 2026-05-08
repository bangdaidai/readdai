package io.legado.app.lib.theme

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes

/**
 * @author Aidan Follestad (afollestad)
 */
object ThemeUtils {

    @JvmOverloads
    fun resolveColor(context: Context, @AttrRes attr: Int, fallback: Int = 0): Int {
        // 特殊处理自定义属性，返回 ThemeStore 中的值
        try {
            // 尝试获取 backgroundCard 属性的资源ID
            val backgroundCardId = context.resources.getIdentifier("backgroundCard", "attr", context.packageName)
            if (backgroundCardId > 0 && attr == backgroundCardId) {
                return ThemeStore.backgroundCard(context)
            }
            // 尝试获取 textColorSecondary 属性的资源ID
            val textColorSecondaryId = context.resources.getIdentifier("textColorSecondary", "attr", context.packageName)
            if (textColorSecondaryId > 0 && attr == textColorSecondaryId) {
                return ThemeStore.textColorSecondary(context)
            }
            // 尝试获取 textColorPrimary 属性的资源ID
            val textColorPrimaryId = context.resources.getIdentifier("textColorPrimary", "attr", context.packageName)
            if (textColorPrimaryId > 0 && attr == textColorPrimaryId) {
                return ThemeStore.textColorPrimary(context)
            }
            // 尝试获取 textColorOther 属性的资源ID
            val textColorOtherId = context.resources.getIdentifier("textColorOther", "attr", context.packageName)
            if (textColorOtherId > 0 && attr == textColorOtherId) {
                return ThemeStore.textColorOther(context)
            }
            // 尝试获取 accentColor 属性的资源ID
            val accentColorId = context.resources.getIdentifier("accentColor", "attr", context.packageName)
            if (accentColorId > 0 && attr == accentColorId) {
                return ThemeStore.accentColor(context)
            }
            // 尝试获取 divider 属性的资源ID
            val dividerId = context.resources.getIdentifier("divider", "attr", context.packageName)
            if (dividerId > 0 && attr == dividerId) {
                return ThemeStore.dividerColor(context)
            }
        } catch (e: Exception) {
            // 忽略异常，继续处理
        }
        val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getColor(0, fallback)
        } catch (e: Exception) {
            fallback
        } finally {
            a.recycle()
        }
    }

    @JvmOverloads
    fun resolveFloat(context: Context, @AttrRes attr: Int, fallback: Float = 0.0f): Float {
        val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getFloat(0, fallback)
        } catch (e: Exception) {
            fallback
        } finally {
            a.recycle()
        }
    }

    fun resolveDrawable(context: Context, @AttrRes attr: Int): Drawable? {
        val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getDrawable(0)
        } finally {
            a.recycle()
        }
    }
}