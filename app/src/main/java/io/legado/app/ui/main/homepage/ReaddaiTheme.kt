package io.legado.app.ui.main.homepage

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.backgroundCard
import io.legado.app.lib.theme.dividerColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor

@Composable
fun ReaddaiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val defaultScheme = MaterialTheme.colorScheme
    val accentColor = Color(context.accentColor)
    val backgroundColor = Color(context.backgroundColor)
    val cardBackground = Color(context.backgroundCard)
    val textColorPrimary = Color(context.primaryTextColor)
    val textColorSecondary = Color(context.secondaryTextColor)
    val dividerColor = Color(context.dividerColor)
    val titleBarTextIconColor = Color(ThemeStore.titleBarTextIconColor(context))

    val colorScheme = remember(accentColor, backgroundColor, cardBackground, textColorPrimary, textColorSecondary, dividerColor, titleBarTextIconColor) {
        defaultScheme.copy(
            primary = accentColor,
            onPrimary = titleBarTextIconColor,
            primaryContainer = accentColor.copy(alpha = 0.25f),
            onPrimaryContainer = accentColor,
            secondary = accentColor,
            onSecondary = titleBarTextIconColor,
            tertiary = accentColor,
            onTertiary = titleBarTextIconColor,
            tertiaryContainer = accentColor.copy(alpha = 0.2f),
            background = backgroundColor,
            onBackground = textColorPrimary,
            surface = backgroundColor,
            onSurface = textColorPrimary,
            surfaceVariant = cardBackground,
            onSurfaceVariant = textColorSecondary,
            surfaceContainerLowest = backgroundColor,
            surfaceContainerLow = backgroundColor,
            surfaceContainer = backgroundColor,
            surfaceContainerHigh = cardBackground,
            surfaceContainerHighest = cardBackground,
            outline = dividerColor,
            outlineVariant = dividerColor.copy(alpha = 0.5f),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
