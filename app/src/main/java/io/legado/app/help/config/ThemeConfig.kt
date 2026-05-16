package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.DefaultData
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.BookCover
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.stackBlur
import splitties.init.appCtx
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.toastOnUi
import java.io.FileOutputStream

@Keep
object ThemeConfig {
    const val configFileName = "themeConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    val configList: ArrayList<Config> by lazy {
        val cList = getConfigs() ?: DefaultData.themeConfigs
        ArrayList(cList)
    }

    private var needClearImg = true

    fun getTheme() = when {
        AppConfig.isEInkMode -> Theme.EInk
        AppConfig.isNightTheme -> Theme.Dark
        else -> Theme.Light
    }

    fun isDarkTheme(): Boolean {
        return getTheme() == Theme.Dark
    }

    fun applyDayNight(context: Context) {
        applyTheme(context)
        initNightMode()
        BookCover.upDefaultCover()
        postEvent(EventBus.RECREATE, "")
    }

    fun applyDayNightInit(context: Context) {
        applyTheme(context)
        initNightMode()
    }

    private fun initNightMode() {
        val targetMode =
            if (AppConfig.isNightTheme) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    /**
     * 获取链接获取图片文件名
     */
    private fun getUrlToFile(url: String): String {
        val suffix = when {
            url.contains(".9.png", ignoreCase = true) -> ".9.png"
            url.contains(".png", ignoreCase = true) -> ".png"
            url.contains(".gif", ignoreCase = true) -> ".gif"
            url.contains("webp", ignoreCase = true) -> ".webp"
            else -> ".jpg"
        }
        return MD5Utils.md5Encode16(url) + suffix
    }

    fun getBgImage(context: Context, metrics: DisplayMetrics): Drawable? {
        val themeMode = getTheme()
        val preferenceKey = when (themeMode) {
            Theme.Light -> PreferKey.bgImage
            Theme.Dark -> PreferKey.bgImageN
            else -> return  null
        }
        var path = context.getPrefString(preferenceKey)
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) {
            val name = getUrlToFile(path)
            val fileRoot = context.externalFiles
            val filePath = FileUtils.getPath(fileRoot, preferenceKey, name)
            if (!FileUtils.exist(filePath)) {
                appCtx.toastOnUi("未缓存在线背景图\n请重新应用主题")
                return null
            }
            path = filePath
        }
        if (path.endsWith(".9.png")) {
            val bgDrawable = BitmapUtils.decodeNinePatchDrawable(path)
            return bgDrawable
        }
        val bgImgBlu = when (themeMode) {
            Theme.Light -> context.getPrefInt(PreferKey.bgImageBlurring, 0)
            Theme.Dark -> context.getPrefInt(PreferKey.bgImageNBlurring, 0)
            else -> 0
        }
        val bgImage = BitmapUtils
            .decodeBitmap(path, metrics.widthPixels, metrics.heightPixels)
        if (bgImgBlu == 0) {
            return bgImage?.toDrawable(context.resources)
        }
        return bgImage?.stackBlur(bgImgBlu)?.toDrawable(context.resources)
    }

    fun upConfig() {
        addConfigs(getConfigs())
    }

    fun save() {
        val json = GSON.toJson(configList)
        FileUtils.delete(configFilePath)
        FileUtils.createFileIfNotExist(configFilePath).writeText(json)
    }

    fun delConfig(index: Int) {
        configList.removeAt(index)
        save()
    }

    fun addConfig(json: String): Boolean {
        GSON.fromJsonObject<Config>(json.trim { it < ' ' }).getOrNull()
            ?.let {
                if (validateConfig(it)) {
                    addConfig(it)
                    return true
                }
            }
        return false
    }

    fun addConfig(newConfig: Config) {
        if (!validateConfig(newConfig)) {
            return
        }
        var hasTheme = false
        configList.forEachIndexed { index, config ->
            if (newConfig.themeName == config.themeName) {
                configList[index] = newConfig
                hasTheme = true
                return@forEachIndexed
            }
        }
        if (!hasTheme) {
            configList.add(newConfig)
        }
        save()
    }

    fun addConfigs(newConfigs: List<Config>?) {
        val newConfigs = newConfigs?.filter{
            validateConfig(it)
        }
        if (newConfigs.isNullOrEmpty()) {
            return
        }
        newConfigs.forEach { newConfig ->
            val existingIndex = configList.indexOfFirst { it.themeName == newConfig.themeName }
            if (existingIndex != -1) {
                configList[existingIndex] = newConfig
            } else {
                configList.add(newConfig)
            }
        }
        save()
    }

    private fun validateConfig(config: Config): Boolean {
        try {
            config.primaryColor.toColorInt()
            config.accentColor.toColorInt()
            config.backgroundColor.toColorInt()
            config.bottomBackground.toColorInt()
            config.backgroundCard.toColorInt()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun getConfigs(): List<Config>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return GSON.fromJsonArray<Config>(json).getOrThrow()
            }.onFailure {
                it.printOnDebug()
            }
        }
        return null
    }

    fun applyConfig(
        context: Context,
        config: Config,
        switchNightMode: Boolean = true,
        notify: Boolean = true
    ) {
        try {
            if (needClearImg) {
                needClearImg = false
                clearBg(context)
            }
            val primary = config.primaryColor.toColorInt()
            val accent = config.accentColor.toColorInt()
            val background = config.backgroundColor.toColorInt()
            val bBackground = config.bottomBackground.toColorInt()
            val bCard = config.backgroundCard.toColorInt()
            val titleBarTextIcon = config.titleBarTextIconColor.toColorInt()
            val bottomNavIconUnselected = config.bottomNavIconUnselectedColor.toColorInt()
            val textPrimary = config.textPrimaryColor.toColorInt()
            val textSecondary = config.textSecondaryColor.toColorInt()
            val textOther = config.textOtherColor.toColorInt()
            val isNightTheme = config.isNightTheme
            val transparentNavBar = config.transparentNavBar
            val backgroundPath = config.backgroundImgPath

            if (backgroundPath != null && backgroundPath.startsWith("http")) {
                val fileRoot = context.externalFiles
                val preferenceKey = if (isNightTheme) {
                    PreferKey.bgImageN
                } else {
                    PreferKey.bgImage
                }
                val name = getUrlToFile(backgroundPath)
                val fileFold = File(fileRoot, preferenceKey)
                if (!fileFold.exists()) {
                    fileFold.mkdirs()
                }
                val fileImg = File(fileFold, name)
                if (!fileImg.exists()) {
                    appCtx.toastOnUi("下载背景图片中...")
                    Coroutine.async {
                        kotlin.runCatching {
                            val res = okHttpClient.newCallResponse(0) {
                                url(backgroundPath)
                            }
                            res.body.byteStream().use { inputStream ->
                                FileOutputStream(fileImg).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }.onSuccess {
                            appCtx.toastOnUi("背景图下载成功\n请重新应用主题")
                        }.onFailure {
                            appCtx.toastOnUi(it.localizedMessage)
                        }
                    }
                    return
                }
            }
            val backgroundBlur = config.backgroundImgBlur
            if (isNightTheme) {
                context.putPrefString(PreferKey.dNThemeName, config.themeName)
                context.putPrefInt(PreferKey.cNPrimary, primary)
                context.putPrefInt(PreferKey.cNAccent, accent)
                context.putPrefInt(PreferKey.cNBackground, background)
                context.putPrefInt(PreferKey.cNBBackground, bBackground)
                context.putPrefInt(PreferKey.cNBackgroundCard, bCard)
                context.putPrefInt(PreferKey.cNTitleBarTextIcon, titleBarTextIcon)
                context.putPrefInt(PreferKey.cNBottomNavIconUnselected, bottomNavIconUnselected)
                context.putPrefInt(PreferKey.cNTextPrimary, textPrimary)
                context.putPrefInt(PreferKey.cNTextSecondary, textSecondary)
                context.putPrefInt(PreferKey.cNTextOther, textOther)
                context.putPrefBoolean(PreferKey.tNavBarN, transparentNavBar)
                context.putPrefString(PreferKey.bgImageN, backgroundPath)
                context.putPrefInt(PreferKey.bgImageNBlurring, backgroundBlur)
            } else {
                context.putPrefString(PreferKey.dThemeName, config.themeName)
                context.putPrefInt(PreferKey.cPrimary, primary)
                context.putPrefInt(PreferKey.cAccent, accent)
                context.putPrefInt(PreferKey.cBackground, background)
                context.putPrefInt(PreferKey.cBBackground, bBackground)
                context.putPrefInt(PreferKey.cBackgroundCard, bCard)
                context.putPrefInt(PreferKey.cTitleBarTextIcon, titleBarTextIcon)
                context.putPrefInt(PreferKey.cBottomNavIconUnselected, bottomNavIconUnselected)
                context.putPrefInt(PreferKey.cTextPrimary, textPrimary)
                context.putPrefInt(PreferKey.cTextSecondary, textSecondary)
                context.putPrefInt(PreferKey.cTextOther, textOther)
                context.putPrefBoolean(PreferKey.tNavBar, transparentNavBar)
                context.putPrefString(PreferKey.bgImage, backgroundPath)
                context.putPrefInt(PreferKey.bgImageBlurring, backgroundBlur)
            }
            if (switchNightMode) {
                AppConfig.isNightTheme = isNightTheme
            }
            applyDayNight(context)
        } catch (e: Exception) {
            AppLog.put("设置主题出错\n$e", e, true)
        }
    }

    fun getDurConfig(context: Context): Config {
        val isNight = AppConfig.isNightTheme
        val name = if (isNight) {
            context.getPrefString(PreferKey.dNThemeName) ?: ""
        } else {
            context.getPrefString(PreferKey.dThemeName) ?: ""
        }
        return if (isNight) {
            getNightTheme(context, name)
        } else {
            getDayTheme(context, name)
        }
    }

    private fun getDayTheme(context: Context, name: String): Config {
        val primary =
            context.getPrefInt(PreferKey.cPrimary, context.getCompatColor(R.color.md_brown_500))
        val accent =
            context.getPrefInt(PreferKey.cAccent, context.getCompatColor(R.color.md_red_600))
        val background =
            context.getPrefInt(PreferKey.cBackground, context.getCompatColor(R.color.md_grey_100))
        val bBackground =
            context.getPrefInt(PreferKey.cBBackground, context.getCompatColor(R.color.md_grey_200))
        val bCard =
            context.getPrefInt(PreferKey.cBackgroundCard, context.getCompatColor(R.color.background_card))
        val divider =
            context.getPrefInt(PreferKey.cDivider, context.getCompatColor(R.color.divider))
        val titleBarTextIcon =
            context.getPrefInt(PreferKey.cTitleBarTextIcon, context.getCompatColor(R.color.white))
        val bottomNavIconUnselected =
            context.getPrefInt(PreferKey.cBottomNavIconUnselected, textSecondary)
        val textPrimary =
            context.getPrefInt(PreferKey.cTextPrimary, context.getCompatColor(R.color.primaryText))
        val textSecondary = context.getPrefInt(PreferKey.cTextSecondary, context.getCompatColor(R.color.secondaryText))
        val textOther = context.getPrefInt(PreferKey.cTextOther, context.getCompatColor(R.color.primaryText))
        val transparentNavBar = context.getPrefBoolean(PreferKey.tNavBar, false)
        val bgImgPath = context.getPrefString(PreferKey.bgImage)
        val bgImgBlur = context.getPrefInt(PreferKey.bgImageBlurring, 0)

        return Config(
            themeName = name,
            isNightTheme = false,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            backgroundCard = "#${bCard.hexString}",
            dividerColor = "#${divider.hexString}",
            titleBarTextIconColor = "#${titleBarTextIcon.hexString}",
            bottomNavIconUnselectedColor = "#${bottomNavIconUnselected.hexString}",
            textPrimaryColor = "#${textPrimary.hexString}",
            textSecondaryColor = "#${textSecondary.hexString}",
            textOtherColor = "#${textOther.hexString}",
            transparentNavBar = transparentNavBar,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur
        )
    }

    fun saveDayTheme(context: Context, name: String) {
        val config = getDayTheme(context, name)
        addConfig(config)
    }

    private fun getNightTheme(context: Context, name: String): Config {
        val primary =
            context.getPrefInt(
                PreferKey.cNPrimary,
                context.getCompatColor(R.color.md_blue_grey_600)
            )
        val accent =
            context.getPrefInt(
                PreferKey.cNAccent,
                context.getCompatColor(R.color.md_deep_orange_800)
            )
        val background =
            context.getPrefInt(PreferKey.cNBackground, context.getCompatColor(R.color.md_grey_900))
        val bBackground =
            context.getPrefInt(PreferKey.cNBBackground, context.getCompatColor(R.color.md_grey_850))
        val bCard =
            context.getPrefInt(PreferKey.cNBackgroundCard, context.getCompatColor(R.color.background_card))
        val divider =
            context.getPrefInt(PreferKey.cNDivider, context.getCompatColor(R.color.divider))
        val titleBarTextIcon =
            context.getPrefInt(PreferKey.cNTitleBarTextIcon, context.getCompatColor(R.color.white))
        val bottomNavIconUnselected =
            context.getPrefInt(PreferKey.cNBottomNavIconUnselected, textSecondary)
        val textPrimary =
            context.getPrefInt(PreferKey.cNTextPrimary, context.getCompatColor(R.color.primaryText))
        val textSecondary = context.getPrefInt(PreferKey.cNTextSecondary, context.getCompatColor(R.color.secondaryText))
        val textOther = context.getPrefInt(PreferKey.cNTextOther, context.getCompatColor(R.color.primaryText))
        val transparentNavBar = context.getPrefBoolean(PreferKey.tNavBarN, false)
        val bgImgPath = context.getPrefString(PreferKey.bgImageN)
        val bgImgBlur = context.getPrefInt(PreferKey.bgImageNBlurring, 0)
        return Config(
            themeName = name,
            isNightTheme = true,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            backgroundCard = "#${bCard.hexString}",
            dividerColor = "#${divider.hexString}",
            titleBarTextIconColor = "#${titleBarTextIcon.hexString}",
            bottomNavIconUnselectedColor = "#${bottomNavIconUnselected.hexString}",
            textPrimaryColor = "#${textPrimary.hexString}",
            textSecondaryColor = "#${textSecondary.hexString}",
            textOtherColor = "#${textOther.hexString}",
            transparentNavBar = transparentNavBar,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur
        )
    }

    fun saveNightTheme(context: Context, name: String) {
        val config = getNightTheme(context, name)
        addConfig(config)
    }

    fun getThemeConfig(context: Context, isNightTheme: Boolean): Config {
        return if (isNightTheme) {
            getNightTheme(context, "")
        } else {
            getDayTheme(context, "")
        }
    }

    /**
     * 更新主题
     */
    fun applyTheme(context: Context) = with(context) {
        val themeEditor = ThemeStore.editTheme(this)
        when {
            AppConfig.isEInkMode -> {
                themeEditor
                    .primaryColor(Color.WHITE)
                    .accentColor(Color.BLACK)
                    .backgroundColor(Color.WHITE)
                    .bottomBackground(Color.WHITE)
                    .backgroundCard(Color.WHITE)
                    .transparentNavBar(false)
                    .apply()
            }

            AppConfig.isNightTheme -> {
                val primary =
                    getPrefInt(PreferKey.cNPrimary, getCompatColor(R.color.md_blue_grey_600))
                val accent =
                    getPrefInt(PreferKey.cNAccent, getCompatColor(R.color.md_deep_orange_800))
                var background =
                    getPrefInt(PreferKey.cNBackground, getCompatColor(R.color.md_grey_900))
                if (ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.md_grey_900)
                    putPrefInt(PreferKey.cNBackground, background)
                }
                val bBackground =
                    getPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.md_grey_850))
                val bCard =
                    getPrefInt(PreferKey.cNBackgroundCard, getCompatColor(R.color.background_card))
                val divider =
                    getPrefInt(PreferKey.cNDivider, getCompatColor(R.color.divider))
                val titleBarTextIcon =
                    getPrefInt(PreferKey.cNTitleBarTextIcon, getCompatColor(R.color.white))
                val textPrimary =
                    getPrefInt(PreferKey.cNTextPrimary, getCompatColor(R.color.primaryText))
                val textSecondary =
                    getPrefInt(PreferKey.cNTextSecondary, getCompatColor(R.color.secondaryText))
                val transparentNavBar =
                    getPrefBoolean(PreferKey.tNavBarN, false)
                val textOther = getPrefInt(PreferKey.cNTextOther, getCompatColor(R.color.primaryText))
                themeEditor
                    .primaryColor(primary)
                    .accentColor(accent)
                    .backgroundColor(background)
                    .bottomBackground(bBackground)
                    .backgroundCard(bCard)
                    .dividerColor(divider)
                    .textColorPrimary(textPrimary)
                    .textColorPrimaryInverse(titleBarTextIcon)
                    .textColorSecondary(textSecondary)
                    .textColorOther(textOther)
                    .transparentNavBar(transparentNavBar)
                    .apply()
                // 更新颜色资源
                updateColorResources(this, textPrimary, textSecondary, textSecondary, textSecondary)
            }

            else -> {
                val primary =
                    getPrefInt(PreferKey.cPrimary, getCompatColor(R.color.md_brown_500))
                val accent =
                    getPrefInt(PreferKey.cAccent, getCompatColor(R.color.md_red_600))
                var background =
                    getPrefInt(PreferKey.cBackground, getCompatColor(R.color.md_grey_100))
                if (!ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.md_grey_100)
                    putPrefInt(PreferKey.cBackground, background)
                }
                val bBackground =
                    getPrefInt(PreferKey.cBBackground, getCompatColor(R.color.md_grey_200))
                val bCard =
                    getPrefInt(PreferKey.cBackgroundCard, getCompatColor(R.color.background_card))
                val divider =
                    getPrefInt(PreferKey.cDivider, getCompatColor(R.color.divider))
                val titleBarTextIcon =
                    getPrefInt(PreferKey.cTitleBarTextIcon, getCompatColor(R.color.white))
                val textPrimary =
                    getPrefInt(PreferKey.cTextPrimary, getCompatColor(R.color.primaryText))
                val textSecondary =
                    getPrefInt(PreferKey.cTextSecondary, getCompatColor(R.color.secondaryText))
                val transparentNavBar =
                    getPrefBoolean(PreferKey.tNavBar, false)
                val textOther = getPrefInt(PreferKey.cTextOther, getCompatColor(R.color.primaryText))
                themeEditor
                    .primaryColor(primary)
                    .accentColor(accent)
                    .backgroundColor(background)
                    .bottomBackground(bBackground)
                    .backgroundCard(bCard)
                    .dividerColor(divider)
                    .textColorPrimary(textPrimary)
                    .textColorPrimaryInverse(titleBarTextIcon)
                    .textColorSecondary(textSecondary)
                    .textColorOther(textOther)
                    .transparentNavBar(transparentNavBar)
                    .apply()
                // 更新颜色资源
                updateColorResources(this, textPrimary, textSecondary, textSecondary, textSecondary)
            }
        }
    }

    fun clearBg(context: Context) {
        val (nightConfigs, dayConfigs) = configList.partition { it.isNightTheme }
        val fileRoot = context.externalFiles
        val nightBackgroundImgPaths = nightConfigs.mapNotNull {
            val path = it.backgroundImgPath ?: return@mapNotNull null
            if (path.startsWith("http")) {
                val name = getUrlToFile(path)
                FileUtils.getPath(fileRoot, PreferKey.bgImageN, name)
            } else {
                path
            }
        }
        val dayBackgroundImgPaths = dayConfigs.mapNotNull {
            val path = it.backgroundImgPath ?: return@mapNotNull null
            if (path.startsWith("http")) {
                val name = getUrlToFile(path)
                FileUtils.getPath(fileRoot, PreferKey.bgImage, name)
            } else {
                path
            }
        }
        appCtx.externalFiles.getFile(PreferKey.bgImage).listFiles()?.forEach {
            if (!dayBackgroundImgPaths.contains(it.absolutePath)) {
                it.delete()
            }
        }
        appCtx.externalFiles.getFile(PreferKey.bgImageN).listFiles()?.forEach {
            if (!nightBackgroundImgPaths.contains(it.absolutePath)) {
                it.delete()
            }
        }
    }

    /**
     * 获取文字颜色
     */
    fun getTextColorPrimary(context: Context): Int {
        return if (AppConfig.isNightTheme) {
            context.getPrefInt(PreferKey.cNTextPrimary, context.getCompatColor(R.color.primaryText))
        } else {
            context.getPrefInt(PreferKey.cTextPrimary, context.getCompatColor(R.color.primaryText))
        }
    }

    /**
     * 获取次要文字颜色
     */
    fun getTextColorSecondary(context: Context): Int {
        return if (AppConfig.isNightTheme) {
            context.getPrefInt(PreferKey.cNTextSecondary, context.getCompatColor(R.color.secondaryText))
        } else {
            context.getPrefInt(PreferKey.cTextSecondary, context.getCompatColor(R.color.secondaryText))
        }
    }

    /**
     * 获取摘要文字颜色
     */
    fun getTextColorSummary(context: Context): Int {
        return getTextColorSecondary(context)
    }

    /**
     * 获取菜单文字颜色
     */
    fun getTextColorMenu(context: Context): Int {
        return getTextColorSecondary(context)
    }

    /**
     * 获取其他文字颜色
     */
    fun getTextColorOther(context: Context): Int {
        return if (AppConfig.isNightTheme) {
            context.getPrefInt(PreferKey.cNTextOther, context.getCompatColor(R.color.primaryText))
        } else {
            context.getPrefInt(PreferKey.cTextOther, context.getCompatColor(R.color.primaryText))
        }
    }

    /**
     * 更新颜色资源
     * 注意：此方法仅用于更新内存中的颜色值，不会修改资源文件
     */
    private fun updateColorResources(context: Context, textPrimary: Int, textSecondary: Int, textSummary: Int, textMenu: Int) {
        // 这里可以添加代码来更新内存中的颜色值
        // 由于 Android 不允许在运行时修改资源文件，我们只能通过其他方式来处理
    }

    @Keep
    data class Config(
        var themeName: String,
        var isNightTheme: Boolean,
        var primaryColor: String,
        var accentColor: String,
        var backgroundColor: String,
        var bottomBackground: String,
        var backgroundCard: String,
        var dividerColor: String,
        var titleBarTextIconColor: String,
        var bottomNavIconUnselectedColor: String,
        var textPrimaryColor: String,
        var textSecondaryColor: String,
        var textOtherColor: String,
        var transparentNavBar: Boolean,
        var backgroundImgPath: String?,
        var backgroundImgBlur: Int
    ) {

        override fun hashCode(): Int {
            return GSON.toJson(this).hashCode()
        }

        override fun equals(other: Any?): Boolean {
            other ?: return false
            if (other is Config) {
                return other.themeName == themeName
                        && other.isNightTheme == isNightTheme
                        && other.primaryColor == primaryColor
                        && other.accentColor == accentColor
                        && other.backgroundColor == backgroundColor
                        && other.bottomBackground == bottomBackground
                        && other.backgroundCard == backgroundCard
                        && other.dividerColor == dividerColor
                        && other.titleBarTextIconColor == titleBarTextIconColor
                        && other.textPrimaryColor == textPrimaryColor
                        && other.textSecondaryColor == textSecondaryColor
                        && other.textOtherColor == textOtherColor
                        && other.transparentNavBar == transparentNavBar
                        && other.backgroundImgPath == backgroundImgPath
                        && other.backgroundImgBlur == backgroundImgBlur
            }
            return false
        }

        fun toMap() = mapOf(
            "themeName" to themeName,
            "isNightTheme" to isNightTheme,
            "primaryColor" to primaryColor,
            "accentColor" to accentColor,
            "backgroundColor" to backgroundColor,
            "bottomBackground" to bottomBackground,
            "backgroundCard" to backgroundCard,
            "dividerColor" to dividerColor,
            "titleBarTextIconColor" to titleBarTextIconColor,
            "textPrimaryColor" to textPrimaryColor,
            "textSecondaryColor" to textSecondaryColor,
            "textOtherColor" to textOtherColor,
            "transparentNavBar" to transparentNavBar,
            "backgroundImgPath" to backgroundImgPath,
            "backgroundImgBlur" to backgroundImgBlur
        )
    }

}