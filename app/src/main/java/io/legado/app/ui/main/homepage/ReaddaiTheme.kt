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
    val colorScheme = remember(context) {
        val accent = Color(context.accentColor)
        val bg = Color(context.backgroundColor)
        val cardBg = Color(context.backgroundCard)
        val textPrimary = Color(context.primaryTextColor)
        val textSecondary = Color(context.secondaryTextColor)
        val divider = Color(context.dividerColor)
        val titleBarTextIcon = Color(ThemeStore.titleBarTextIconColor(context))

        defaultScheme.copy(
            primary = accent,
            onPrimary = titleBarTextIcon,
            primaryContainer = accent.copy(alpha = 0.25f),
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = titleBarTextIcon,
            tertiary = accent,
            onTertiary = titleBarTextIcon,
            tertiaryContainer = accent.copy(alpha = 0.2f),
            background = bg,
            onBackground = textPrimary,
            surface = bg,
            onSurface = textPrimary,
            surfaceVariant = cardBg,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = bg,
            surfaceContainerLow = cardBg,
            surfaceContainer = cardBg.copy(alpha = 0.95f),
            surfaceContainerHigh = cardBg.copy(alpha = 0.9f),
            surfaceContainerHighest = cardBg.copy(alpha = 0.85f),
            outline = divider,
            outlineVariant = divider.copy(alpha = 0.5f),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
