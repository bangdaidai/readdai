@file:Suppress("unused")

package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
@ColorInt
fun Context.getPrimaryTextColor(dark: Boolean): Int {
    return io.legado.app.help.config.ThemeConfig.getTextColorPrimary(this)
}

@ColorInt
fun Context.getSecondaryTextColor(dark: Boolean): Int {
    return io.legado.app.help.config.ThemeConfig.getTextColorSecondary(this)
}

@ColorInt
fun Context.getPrimaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    }
}

@ColorInt
fun Context.getSecondaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_light
        )
    } else {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_dark
        )
    }
}

val Context.primaryColor: Int
    get() = ThemeStore.primaryColor(this)

val Context.primaryColorDark: Int
    get() = ThemeStore.primaryColorDark(this)

val Context.accentColor: Int
    get() = ThemeStore.accentColor(this)

val Context.backgroundColor: Int
    get() = ThemeStore.backgroundColor(this)

val Context.bottomBackground: Int
    get() = ThemeStore.bottomBackground(this)

val Context.backgroundCard: Int
    get() = ThemeStore.backgroundCard(this)

val Context.colorSurface: Int
    get() = ThemeStore.backgroundCard(this)

val Context.primaryTextColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorPrimary(this)

val Context.transparentNavBar: Boolean
    get() = ThemeStore.transparentNavBar(this)

val Context.secondaryTextColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorSecondary(this)

val Context.textSummaryColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorSummary(this)

val Context.textMenuColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorMenu(this)

val Context.textOtherColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorOther(this)

val Context.primaryDisabledTextColor: Int
    get() = getPrimaryDisabledTextColor(isDarkTheme)

val Context.secondaryDisabledTextColor: Int
    get() = getSecondaryDisabledTextColor(isDarkTheme)

val Fragment.primaryColor: Int
    get() = ThemeStore.primaryColor(requireContext())

val Fragment.primaryColorDark: Int
    get() = ThemeStore.primaryColorDark(requireContext())

val Fragment.accentColor: Int
    get() = ThemeStore.accentColor(requireContext())

val Fragment.backgroundColor: Int
    get() = ThemeStore.backgroundColor(requireContext())

val Fragment.bottomBackground: Int
    get() = ThemeStore.bottomBackground(requireContext())

val Fragment.backgroundCard: Int
    get() = ThemeStore.backgroundCard(requireContext())

val Fragment.primaryTextColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorPrimary(requireContext())

val Fragment.secondaryTextColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorSecondary(requireContext())

val Fragment.textSummaryColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorSummary(requireContext())

val Fragment.textMenuColor: Int
    get() = io.legado.app.help.config.ThemeConfig.getTextColorMenu(requireContext())

val Fragment.primaryDisabledTextColor: Int
    get() = requireContext().getPrimaryDisabledTextColor(isDarkTheme)

val Fragment.secondaryDisabledTextColor: Int
    get() = requireContext().getSecondaryDisabledTextColor(isDarkTheme)

val Context.buttonDisabledColor: Int
    get() = if (isDarkTheme) {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    }

val Context.isDarkTheme: Boolean
    get() = ColorUtils.isColorLight(ThemeStore.primaryColor(this))

val Fragment.isDarkTheme: Boolean
    get() = requireContext().isDarkTheme

val Context.elevation: Float
    @SuppressLint("PrivateResource")
    get() {
        return if (AppConfig.elevation < 0) {
            ThemeUtils.resolveFloat(
                this,
                android.R.attr.elevation,
                resources.getDimension(com.google.android.material.R.dimen.design_appbar_elevation)
            )
        } else {
            AppConfig.elevation.toFloat().dpToPx()
        }
    }

val Context.filletBackground: GradientDrawable
    get() {
        val background = GradientDrawable()
        background.cornerRadius = 3f.dpToPx()
        background.setColor(backgroundColor)
        return background
    }