package io.legado.app.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.graphics.Color
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.TitleBar
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBackgroundTint
import io.legado.app.utils.applyOpenTint
import io.legado.app.constant.EventBus
import io.legado.app.utils.observeEvent
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.windowSize
import androidx.core.graphics.drawable.toDrawable


abstract class BaseActivity<VB : ViewBinding>(
    val fullScreen: Boolean = true,
    private val theme: Theme = Theme.Auto,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true,
    private val showOpenMenuIcon: Boolean = true
) : AppCompatActivity() {

    protected abstract val binding: VB

    val isInMultiWindow: Boolean
        @SuppressLint("ObsoleteSdkInt")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isInMultiWindowMode
            } else {
                false
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(newBase))
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        if (AppConst.menuViewNames.contains(name) && parent?.parent is FrameLayout) {
            (parent.parent as View).setBackgroundColor(backgroundColor)
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        setContentView(binding.root)
        // 统一处理输入法遮挡问题（仅针对有输入框的页面）
        setupImePadding()
        upBackgroundImage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            findViewById<TitleBar>(R.id.title_bar)
                ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
        observeLiveBus()
        onActivityCreated(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        setupSystemBar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindow, fullScreen)
        setupSystemBar()
    }

    abstract fun onActivityCreated(savedInstanceState: Bundle?)

    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val bool = onCompatCreateOptionsMenu(menu)
        menu.applyTint(this, toolBarTheme)
        // 更新 TitleBar 中的其他图标的颜色
        findViewById<TitleBar>(R.id.title_bar)?.let {
            val textColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this)
            it.setColorFilter(textColor)
        }
        return bool
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.applyOpenTint(this, showOpenMenuIcon)
        return super.onMenuOpened(featureId, menu)
    }

    open fun onCompatCreateOptionsMenu(menu: Menu) = super.onCreateOptionsMenu(menu)

    final override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            supportFinishAfterTransition()
            return true
        }
        return onCompatOptionsItemSelected(item)
    }

    open fun onCompatOptionsItemSelected(item: MenuItem) = super.onOptionsItemSelected(item)

    open fun initTheme() {
        when (theme) {
            Theme.Transparent -> setTheme(R.style.AppTheme_Transparent)
            Theme.Dark -> {
                setTheme(R.style.AppTheme_Dark)
               window.decorView.applyBackgroundTint(backgroundColor)
            }

            Theme.Light -> {
                setTheme(R.style.AppTheme_Light)
               window.decorView.applyBackgroundTint(backgroundColor)
            }

            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(R.style.AppTheme_Light)
                } else {
                    setTheme(R.style.AppTheme_Dark)
                }
               window.decorView.applyBackgroundTint(backgroundColor)
            }
        }
    }

    open fun upBackgroundImage() {
        if (imageBg) {
            try {
                val drawable = ThemeConfig.getBgImage(this, windowManager.windowSize)
                if (drawable != null) {
                    window.decorView.background = drawable
                } else {
                    // 没有背景图片时，设置用户自定义的背景色
                    window.decorView.setBackgroundColor(backgroundColor)
                }
            } catch (_: OutOfMemoryError) {
                toastOnUi("背景图片太大,内存溢出")
            } catch (e: Exception) {
                AppLog.put("加载背景出错\n${e.localizedMessage}", e)
            }
        }
    }

    open fun setupSystemBar() {
        if (fullScreen && !isInMultiWindow) {
            fullScreen()
        }
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        
        // 先设置状态栏颜色
        if (fullScreen) {
            if (isTransparentStatusBar) {
                window.statusBarColor = Color.TRANSPARENT
            } else {
                window.statusBarColor = resources.getColor(R.color.status_bar_bag)
            }
        } else {
            window.statusBarColor = statusBarColor
        }
        
        // 根据toolBarTheme或自动计算设置文字颜色
        val isLightBar = if (toolBarTheme == Theme.Auto) {
            ColorUtils.isColorLight(statusBarColor)
        } else {
            toolBarTheme == Theme.Light
        }
        setLightStatusBar(isLightBar)
        
        upNavigationBarColor()
    }

    open fun upNavigationBarColor() {
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }

    open fun observeLiveBus() {
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<String>(EventBus.THEME_CHANGED) {
            recreate()
        }
    }

    /**
     * 统一处理输入法遮挡问题
     * 子类可以重写此方法来自定义行为，或返回 false 禁用此功能
     */
    open fun shouldHandleImePadding(): Boolean {
        // 默认不处理，子类根据需要启用
        return false
    }

    private fun setupImePadding() {
        if (!shouldHandleImePadding()) {
            return
        }

        val contentView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            // 只获取 IME（输入法）的高度
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            
            // 只设置底部 padding，不影响其他方向
            // 使用 updatePadding 而不是 setPadding，避免覆盖原有值
            view.updatePadding(bottom = imeHeight)
            
            insets
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            false
        }
    }

    override fun finish() {
        currentFocus?.hideSoftInput()
        super.finish()
    }
}