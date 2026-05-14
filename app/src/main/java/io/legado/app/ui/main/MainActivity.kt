@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.animation.ValueAnimator
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.NavigationBarIconConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.ui.book.read.AiChatActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.bookshelf.style2.BookshelfFragment2
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.main.rss.RssFragment
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.isCreated
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.ColorUtils
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import androidx.core.view.get
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.about.UpdateDialog
import kotlin.time.Duration.Companion.hours

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener,
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private val EXIT_INTERVAL = 2000L
    private val realPositions = arrayOf(idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null
    
    // Bottom navigation config signature - match archive implementation
    private var bottomNavigationConfigSignature: String? = null
    
    // LiquidGlass initialization state - match archive implementation
    private var liquidGlassReady = false
    
    // Track if we should cancel pending LiquidGlass tasks
    private var shouldCancelLiquidGlassTasks = false
    
    // Bottom navigation indicator animator - match archive implementation
    private val bottomIndicatorAnimator by lazy {
        ValueAnimator().apply {
            duration = 320L
            interpolator = OvershootInterpolator(0.55f)
        }
    }
    
    // Bottom glass pulse interpolator - match archive implementation
    private val bottomGlassPulseInterpolator by lazy { AccelerateDecelerateInterpolator() }
    
    // Track bound LiquidGlassView IDs to prevent duplicate binding - match archive
    private val boundLiquidGlassViewIds = hashSetOf<Int>()
    
    // Hide indicator runnable - match archive implementation
    private val hideBottomIndicatorRunnable = Runnable {
        binding.root.findViewById<View>(R.id.bottom_navigation_indicator_container)?.animate()
            ?.alpha(0f)
            ?.scaleX(0.88f)
            ?.scaleY(0.88f)
            ?.setDuration(220L)
            ?.setInterpolator(bottomGlassPulseInterpolator)
            ?.start()
    }

    override fun setupSystemBar() {
        super.setupSystemBar()
        if (AppConfig.isMainTransparentStatusBar) {
            hideMainStatusBar()
        } else {
            showMainStatusBar()
        }
    }

    private fun hideMainStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun showMainStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        // Apply immersive status bar effect - match archive implementation
        setStatusBarColorAuto(
            io.legado.app.lib.theme.ThemeStore.statusBarColor(this, AppConfig.isTransparentStatusBar),
            AppConfig.isTransparentStatusBar,
            fullScreen
        )
    }

    /**
     * Get the current BottomNavigationView based on layout mode
     */
    private val currentBottomNav: io.legado.app.lib.theme.view.ThemeBottomNavigationVIew
        get() = if (AppConfig.bottomBarLayoutMode == "floating") {
            binding.bottomNavigationViewFloating
        } else {
            binding.bottomNavigationView
        }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                binding.viewPagerMain.currentItem = 0
                return@addCallback
            }
            (fragmentMap[getFragmentId(0)] as? BookshelfFragment2)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (BaseReadAloudService.pause) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新 - 跳过首次进入的帮助信息
            if (!LocalConfig.isFirstOpenApp) {
                upVersion()
            }
            //设置本地密码 - 不自动弹出
            //setLocalPassword()
            notifyAppCrash()
            //备份同步
            backupSync()
            //设置回调
            viewModel.setActivityCallback(this@MainActivity)
            //自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                binding.viewPagerMain.postDelayed(2000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBottomNavigationConfig()
    }

    /**
     * 重置底部导航栏的选中状态到当前页面
     */
    private fun resetBottomNavSelection() {
        val fragmentId = getFragmentId(pagePosition)
        val menuItemId = when (fragmentId) {
            idBookshelf1, idBookshelf2 -> R.id.menu_bookshelf
            idExplore -> R.id.menu_discovery
            idRss -> R.id.menu_rss
            idMy -> R.id.menu_my_config
            else -> R.id.menu_bookshelf
        }
        binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
        binding.bottomNavigationViewFloating.menu.findItem(menuItemId)?.isChecked = true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(0, false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_ai_read -> {
                startActivity(Intent(this@MainActivity, AiChatActivity::class.java))
            }

            R.id.menu_rss ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return true
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        // Initialize ViewPager - use single ViewPager like archive
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        
        // Initialize both BottomNavigationViews
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        bottomNavigationViewFloating.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationViewFloating.setOnNavigationItemReselectedListener(this@MainActivity)
        
        // Apply layout mode - match archive implementation
        applyBottomLayoutMode()
        
        // Schedule LiquidGlass setup - match archive
        scheduleLiquidGlassSetup()
        contentContainer.doOnPreDraw {
            liquidGlassReady = true
            scheduleLiquidGlassSetup(delayMillis = 32L)
        }
        
        // Update indicator position after layout - match archive
        bottomNavigationViewFloating.doOnLayout {
            val initialItemId = getBottomNavigationItemId(pagePosition)
            updateFloatingIndicatorPosition(initialItemId)
        }
        
        // Handle window insets for floating mode - match archive implementation
        bottomNavigationGlass.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val height = windowInsets.navigationBarHeight
            // Add system navigation bar height plus extra spacing like archive (14.dpToPx())
            view.bottomPadding = height + 14f.dpToPx().toInt()
            windowInsets.inset(0, 0, 0, height)
        }
    }
    
    /**
     * Refresh bottom navigation config - EXACT match with archive
     */
    private fun refreshBottomNavigationConfig() {
        // Always apply layout mode first to ensure correct visibility and padding
        applyBottomLayoutMode()
        
        // Check icon config signature
        val signature = NavigationBarIconConfig.currentSignature(AppConfig.isNightTheme)
        if (bottomNavigationConfigSignature == signature) {
            // Icon config unchanged, but still need to update LiquidGlass for floating mode
            if (AppConfig.bottomBarLayoutMode == "floating") {
                scheduleLiquidGlassSetup()
                binding.bottomNavigationViewFloating.doOnLayout {
                    val initialItemId = getBottomNavigationItemId(pagePosition)
                    updateFloatingIndicatorPosition(initialItemId)
                }
            }
            return
        }
        bottomNavigationConfigSignature = signature
        NavigationBarIconConfig.applyCurrentBottomConfig(AppConfig.isNightTheme)
        applyBottomNavigationIcons()
        scheduleLiquidGlassSetup()
        binding.bottomNavigationViewFloating.doOnLayout {
            val initialItemId = getBottomNavigationItemId(pagePosition)
            updateFloatingIndicatorPosition(initialItemId)
        }
    }
    
    /**
     * Apply bottom layout mode - match archive implementation
     */
    private fun applyBottomLayoutMode() = binding.run {
        val floatingMode = AppConfig.bottomBarLayoutMode == "floating"
        
        // Enable/disable swipe based on mode
        viewPagerMain.swipeEnabled = !floatingMode
        
        if (floatingMode) {
            // Floating mode: add bottom padding to prevent content from being obscured by floating capsule
            val barHeight = resources.getDimensionPixelSize(R.dimen.main_bottom_bar_height)
            val bottomMargin = resources.getDimensionPixelSize(R.dimen.main_bottom_controls_bottom_padding)
            val totalPadding = barHeight + bottomMargin
            
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                totalPadding
            )
            
            // Floating mode: completely hide classic bottom navigation view, no background/elevation
            bottomNavigationView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            bottomNavigationView.alpha = 0f
            bottomNavigationView.visibility = android.view.View.GONE
            bottomNavigationView.elevation = 0f
        } else {
            // Classic mode: clear bottom padding
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                0
            )
            
            // Classic mode: restore bottom navigation view
            bottomNavigationView.visibility = android.view.View.VISIBLE
            bottomNavigationView.alpha = 1f
        }
        
        // Show/hide bottom navigation views
        bottomNavigationGlass.isVisible = floatingMode
        
        if (floatingMode) {
            // Floating mode: setup LiquidGlass and indicator
            shouldCancelLiquidGlassTasks = false
            bottomIndicatorAnimator.cancel()
            
            // Hide indicator initially while setting up
            bottomNavigationIndicatorContainer.isVisible = false
            setupLiquidGlass()
            applyBottomNavigationIcons() // Apply icons for floating mode
            
            // Show indicator after a delay to ensure everything is set up properly
            bottomNavigationViewFloating.doOnLayout {
                val initialItemId = getBottomNavigationItemId(pagePosition)
                binding.root.postDelayed({
                    updateFloatingIndicatorPosition(initialItemId)
                    bottomNavigationIndicatorContainer.isVisible = true
                }, 300L)  // Increased delay to ensure LiquidGlass effect is applied
            }
        } else {
            // Classic mode: hide floating elements and cancel any pending LiquidGlass tasks
            shouldCancelLiquidGlassTasks = true
            bottomIndicatorAnimator.cancel()
            bottomNavigationIndicatorContainer.isVisible = false
            
            applyClassicModeStyle()
            applyBottomNavigationIcons() // Apply icons for classic mode
        }
    }
    
    /**
     * Apply bottom navigation icons - match archive implementation
     */
    private fun applyBottomNavigationIcons() = binding.run {
        // Always apply icons (custom or default)
        val hasCustomIcons = NavigationBarIconConfig.applyTo(
            bottomNavigationView.menu,
            this@MainActivity,
            AppConfig.isNightTheme
        )
        NavigationBarIconConfig.applyTo(
            bottomNavigationViewFloating.menu,
            this@MainActivity,
            AppConfig.isNightTheme
        )
        
        // Check if tint is enabled in the current config
        val currentEntry = NavigationBarIconConfig.currentEntry(AppConfig.isNightTheme)
        val enableTint = currentEntry.config.enableTint
        
        // Apply tint only if enabled
        if (enableTint) {
            bottomNavigationView.restoreThemeIconTint()
            bottomNavigationViewFloating.restoreThemeIconTint()
        } else {
            // Disable automatic tint when tint is not enabled
            bottomNavigationView.itemIconTintList = null
            bottomNavigationViewFloating.itemIconTintList = null
        }
    }
    
    /**
     * Schedule LiquidGlass setup with optional delay - EXACT match with archive
     */
    private fun scheduleLiquidGlassSetup(delayMillis: Long = 0L) {
        val action = {
            if (!isFinishing) {
                setupLiquidGlass()
            }
        }
        if (delayMillis > 0L) {
            binding.bottomNavigationGlass.postDelayed(action, delayMillis)
        } else {
            binding.bottomNavigationGlass.post(action)
        }
    }

    /**
     * Setup LiquidGlass - EXACT match with archive implementation
     */
    private fun setupLiquidGlass() {
        // Only apply in floating mode
        if (AppConfig.bottomBarLayoutMode != "floating") return
        
        val liquidGlassView = binding.root.findViewById<LiquidGlassView>(R.id.bottom_navigation_glass_view)
        val indicatorGlassView = binding.root.findViewById<LiquidGlassView>(R.id.bottom_navigation_indicator_glass_view)
        val shellOverlay = binding.root.findViewById<View>(R.id.bottom_navigation_shell_overlay)
        val backgroundView = binding.root.findViewById<View>(R.id.bottom_navigation_background)
        val indicatorContainer = binding.root.findViewById<View>(R.id.bottom_navigation_indicator_container)
        
        when (AppConfig.bottomBarEffectMode) {
            "solid" -> {
                // Solid mode: hide liquid glass, show solid background with capsule shape - match archive
                liquidGlassView?.visibility = View.GONE
                indicatorGlassView?.visibility = View.GONE
                shellOverlay?.visibility = View.VISIBLE
                indicatorContainer?.isVisible = true
                indicatorContainer?.alpha = 1f
                indicatorContainer?.scaleX = 1f
                indicatorContainer?.scaleY = 1f
                
                // Use factory method to create drawable - match archive implementation
                val cornerRadius = resources.getDimension(R.dimen.main_bottom_bar_corner_radius)
                shellOverlay?.background = createSolidBottomShellDrawable(cornerRadius)
                
                // Setup indicator overlay
                val indicatorOverlay = binding.root.findViewById<View>(R.id.bottom_navigation_indicator_overlay)
                indicatorOverlay?.background = createSolidBottomIndicatorDrawable()
                
                // Hide background view - NOT used in archive, shell_overlay handles everything
                backgroundView?.visibility = View.GONE
                
                // BottomNavigationView must be transparent
                binding.bottomNavigationViewFloating.setBackgroundColor(Color.TRANSPARENT)
                
                // Update indicator position - match archive
                val initialItemId = getBottomNavigationItemId(pagePosition)
                updateFloatingIndicatorPosition(initialItemId)
            }
            "frosted", "glass" -> {
                // Frosted/Glass mode: use LiquidGlassView with real blur effect - match archive
                indicatorContainer?.isVisible = true
                indicatorContainer?.alpha = 0f
                indicatorContainer?.scaleX = 0.82f
                indicatorContainer?.scaleY = 0.82f
                
                liquidGlassView?.visibility = View.VISIBLE
                indicatorGlassView?.visibility = View.VISIBLE
                shellOverlay?.visibility = View.VISIBLE
                backgroundView?.visibility = View.VISIBLE
                
                // BottomNavigationView must be transparent - match archive
                binding.bottomNavigationViewFloating.setBackgroundColor(Color.TRANSPARENT)
                
                // Check if ready to setup LiquidGlass - match archive
                if (!liquidGlassReady || !binding.contentContainer.isLaidOut || !binding.bottomNavigationGlass.isLaidOut) {
                    binding.contentContainer.doOnPreDraw {
                        liquidGlassReady = true
                        scheduleLiquidGlassSetup(delayMillis = 32L)
                    }
                    return
                }
                
                // Calculate glass level for shell overlay - match archive
                val isFrosted = AppConfig.bottomBarEffectMode == "frosted"
                val glassLevel = if (isFrosted) {
                    AppConfig.frostedGlassLevel / 100f
                } else {
                    AppConfig.liquidGlassLevel / 100f
                }
                
                val frostedMode = isFrosted
                val blurRadius = if (frostedMode) {
                    (10f + glassLevel * 24f).dpToPx()
                } else {
                    (5f + glassLevel * 14f).dpToPx()
                }
                val tintAlpha = if (frostedMode) {
                    0.12f + glassLevel * 0.18f
                } else {
                    0.05f + glassLevel * 0.10f
                }
                val dispersion = if (frostedMode) {
                    (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f)
                } else {
                    0.46f + glassLevel * 0.32f
                }
                val refractionHeight = if (frostedMode) {
                    (12f + glassLevel * 10f).dpToPx()
                } else {
                    (18f + glassLevel * 14f).dpToPx()
                }
                val refractionOffset = if (frostedMode) {
                    (36f + glassLevel * 18f).dpToPx()
                } else {
                    (72f + glassLevel * 34f).dpToPx()
                }
                
                // Setup shell overlays with gradient drawable - match archive implementation
                val cornerRadius = resources.getDimension(R.dimen.main_bottom_bar_corner_radius)
                shellOverlay?.background = createLiquidGlassShellDrawable(glassLevel, cornerRadius, false, false)
                
                val indicatorOverlay = binding.root.findViewById<View>(R.id.bottom_navigation_indicator_overlay)
                val indicatorCornerRadius = resources.getDimension(R.dimen.main_bottom_indicator_height) / 2f
                indicatorOverlay?.background = createLiquidGlassShellDrawable(glassLevel, indicatorCornerRadius, true, true)
                
                // Setup main bottom navigation LiquidGlassView - match archive
                val bottomBarCornerRadius = resources.getDimension(R.dimen.main_bottom_bar_corner_radius)
                liquidGlassView?.let { glass ->
                    setupLiquidGlassView(
                        liquidGlassView = glass,
                        cornerRadius = bottomBarCornerRadius,
                        refractionHeight = refractionHeight,
                        refractionOffset = refractionOffset,
                        blurRadius = blurRadius,
                        dispersion = dispersion,
                        tintAlpha = tintAlpha,
                        elasticEnabled = true,
                        touchEffectEnabled = true
                    )
                }
                
                // Setup indicator LiquidGlassView with adjusted parameters - match archive
                val bottomIndicatorCornerRadius = resources.getDimension(R.dimen.main_bottom_indicator_height) / 2f
                indicatorGlassView?.let { indicatorGlass ->
                    setupLiquidGlassView(
                        liquidGlassView = indicatorGlass,
                        cornerRadius = bottomIndicatorCornerRadius,
                        refractionHeight = (refractionHeight * 0.9f).coerceAtLeast(16f.dpToPx()),
                        refractionOffset = (refractionOffset * 0.72f).coerceAtLeast(46f.dpToPx()),
                        blurRadius = (blurRadius * 0.78f).coerceAtLeast(5f.dpToPx()),
                        dispersion = (dispersion + 0.08f).coerceAtMost(1f),
                        tintAlpha = (tintAlpha + 0.05f).coerceAtMost(0.28f),
                        elasticEnabled = true,
                        touchEffectEnabled = true
                    )
                }
            }
        }
    }
    
    /**
     * Update floating mode indicator position to follow selected item - EXACT match with archive
     */
    private fun updateFloatingIndicatorPosition(menuItemId: Int) {
        if (AppConfig.bottomBarLayoutMode != "floating") return
        
        val bottomNav = binding.bottomNavigationViewFloating
        val indicatorContainer = binding.root.findViewById<ViewGroup>(R.id.bottom_navigation_indicator_container)
        
        // Get the menu view (first child of BottomNavigationView)
        val menuView = bottomNav.getChildAt(0) as? ViewGroup ?: return
        
        // Find the selected item view
        val itemView = findBottomNavigationItemView(menuView, menuItemId) ?: return
        
        // CRITICAL: Ensure itemView has valid dimensions before calculating position
        // If width is 0 or not laid out, delay the calculation
        if (!itemView.isLaidOut || itemView.width == 0) {
            // Post a delayed task to retry after layout is complete
            binding.root.postDelayed({
                updateFloatingIndicatorPosition(menuItemId)
            }, 50L)
            return
        }
        
        // Calculate target width - match archive's logic
        val targetWidth = kotlin.math.min(
            resources.getDimensionPixelSize(R.dimen.main_bottom_indicator_width),
            (itemView.width - 16f.dpToPx().toInt()).coerceAtLeast(42f.dpToPx().toInt())
        )
        
        // Update indicator width
        indicatorContainer.layoutParams = indicatorContainer.layoutParams.apply {
            width = targetWidth
        }
        
        // Calculate target X position - match archive's calculation
        val baseX = bottomNav.x + menuView.x + itemView.x
        val targetX = baseX + (itemView.width - targetWidth) / 2f
        
        // Animate indicator to new position with overshoot effect - use ValueAnimator like archive
        if (!indicatorContainer.isLaidOut) {
            indicatorContainer.x = targetX
            playBottomNavigationIndicatorAnimation(animate = false)
            return
        }
        
        val startX = indicatorContainer.x
        bottomIndicatorAnimator.cancel()
        bottomIndicatorAnimator.removeAllUpdateListeners()
        bottomIndicatorAnimator.setFloatValues(startX, targetX)
        bottomIndicatorAnimator.addUpdateListener { animator ->
            indicatorContainer.x = animator.animatedValue as Float
        }
        bottomIndicatorAnimator.start()
        playBottomNavigationIndicatorAnimation(animate = true)
    }
    
    /**
     * Play bottom navigation indicator animation - EXACT match with archive
     */
    private fun playBottomNavigationIndicatorAnimation(animate: Boolean) {
        if (AppConfig.bottomBarLayoutMode != "floating") return
        if (AppConfig.isEInkMode) return
        
        val indicator = binding.root.findViewById<View>(R.id.bottom_navigation_indicator_container) ?: return
        
        // Remove any pending hide callbacks - match archive implementation
        indicator.removeCallbacks(hideBottomIndicatorRunnable)
        indicator.animate().cancel()
        indicator.isVisible = true
        
        if (!animate) {
            indicator.alpha = 1f
            indicator.scaleX = 1f
            indicator.scaleY = 1f
        } else {
            indicator.alpha = 0.94f
            indicator.scaleX = 0.90f
            indicator.scaleY = 1.08f
            indicator.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280L)
                .setInterpolator(OvershootInterpolator(0.78f))
                .start()
            
            // Animate bottom navigation glass container - pulse effect like archive
            binding.bottomNavigationGlass.animate()
                .scaleX(1.01f)
                .scaleY(1.02f)
                .setDuration(120L)
                .withEndAction {
                    binding.bottomNavigationGlass.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220L)
                        .setInterpolator(bottomGlassPulseInterpolator)
                        .start()
                }
                .start()
        }
        
        // Schedule hide indicator after delay - match archive
        indicator.postDelayed(hideBottomIndicatorRunnable, 780L)
    }
    
    /**
     * Find bottom navigation item view by menu item ID - EXACT match with archive
     */
    private fun findBottomNavigationItemView(menuView: ViewGroup, itemId: Int): View? {
        for (index in 0 until menuView.childCount) {
            val child = menuView.getChildAt(index)
            if (child.id == itemId && child.visibility == View.VISIBLE) {
                return child
            }
        }
        var visibleIndex = 0
        for (index in 0 until menuView.childCount) {
            val child = menuView.getChildAt(index)
            if (child.visibility == View.VISIBLE) {
                if (visibleIndex == pagePosition) return child
                visibleIndex++
            }
        }
        return null
    }
    
    /**
     * Apply classic mode style - reference dai411 project
     */
    private fun applyClassicModeStyle() = binding.run {
        // Apply e-ink border if needed
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
            bottomNavigationView.alpha = 1.0f
            bottomNavigationView.elevation = 0f
        } else if (AppConfig.immNavigationBar) {
            // Immersive mode: use page background color to blend in
            val bgColor = io.legado.app.lib.theme.ThemeStore.backgroundColor(this@MainActivity)
            bottomNavigationView.setBackgroundColor(bgColor)
            bottomNavigationView.alpha = 1.0f
            // In immersive mode, no elevation is needed as it blends with the background
            bottomNavigationView.elevation = 0f
        } else {
            // Classic mode: use theme's bottom navigation bar color
            val navBgColor = io.legado.app.lib.theme.ThemeStore.bottomBackground(this@MainActivity)
            bottomNavigationView.setBackgroundColor(navBgColor)
            bottomNavigationView.alpha = 1.0f
            // Apply default elevation for shadow effect
            bottomNavigationView.elevation = resources.getDimension(R.dimen.main_bottom_bar_elevation)
        }
        
        // Force invalidate to ensure background is redrawn
        bottomNavigationView.invalidate()
        
        // CRITICAL: Always re-apply immersive navigation bar padding to ensure it works correctly
        // This must be called every time classic mode is applied, not just once in initView
        bottomNavigationView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val height = windowInsets.navigationBarHeight
            view.bottomPadding = height
            windowInsets.inset(0, 0, 0, height)
        }
        
        // Force request apply window insets to ensure the listener is triggered
        bottomNavigationView.requestApplyInsets()
    }
    
    /**
     * Setup LiquidGlassView - EXACT match with archive
     */
    private fun setupLiquidGlassView(
        liquidGlassView: LiquidGlassView,
        cornerRadius: Float,
        refractionHeight: Float,
        refractionOffset: Float,
        blurRadius: Float,
        dispersion: Float,
        tintAlpha: Float,
        elasticEnabled: Boolean,
        touchEffectEnabled: Boolean,
    ) {
        if (boundLiquidGlassViewIds.add(liquidGlassView.id)) {
            liquidGlassView.bind(binding.contentContainer)
        }
        liquidGlassView.setCornerRadius(cornerRadius)
        liquidGlassView.setRefractionHeight(refractionHeight)
        liquidGlassView.setRefractionOffset(refractionOffset)
        liquidGlassView.setDispersion(dispersion)
        liquidGlassView.setBlurRadius(blurRadius)
        liquidGlassView.setTintAlpha(tintAlpha)
        liquidGlassView.setTintColorRed(0.70f)
        liquidGlassView.setTintColorGreen(0.79f)
        liquidGlassView.setTintColorBlue(0.86f)
        liquidGlassView.setDraggableEnabled(false)
        liquidGlassView.setElasticEnabled(elasticEnabled)
        liquidGlassView.setTouchEffectEnabled(touchEffectEnabled)
        liquidGlassView.isClickable = false
        liquidGlassView.isFocusable = false
        liquidGlassView.invalidate()
    }
    
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
    
    /**
     * Create solid bottom shell drawable - match archive implementation
     */
    private fun createSolidBottomShellDrawable(cornerRadius: Float, oval: Boolean = false): android.graphics.drawable.GradientDrawable {
        val baseColor = getBottomBackgroundColor()
        val alpha = (AppConfig.liquidGlassLevel / 100f).coerceIn(0f, 1f)
        val strokeColor = ColorUtils.withAlpha(
            if (ColorUtils.isColorLight(baseColor)) Color.BLACK else Color.WHITE,
            0.10f
        )
        return android.graphics.drawable.GradientDrawable().apply {
            shape = if (oval) android.graphics.drawable.GradientDrawable.OVAL else android.graphics.drawable.GradientDrawable.RECTANGLE
            if (!oval) {
                this.cornerRadius = cornerRadius
            }
            setColor(ColorUtils.withAlpha(baseColor, alpha))
            setStroke(1f.dpToPx().toInt(), strokeColor)
        }
    }
    
    /**
     * Create solid bottom indicator drawable - match archive implementation
     */
    private fun createSolidBottomIndicatorDrawable(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(primaryColor)
        }
    }
    
    /**
     * Create liquid glass shell drawable - match archive implementation
     */
    private fun createLiquidGlassShellDrawable(
        glassLevel: Float,
        cornerRadius: Float,
        oval: Boolean,
        selected: Boolean
    ): android.graphics.drawable.GradientDrawable {
        val baseColor = getBottomBackgroundColor()
        val isLight = ColorUtils.isColorLight(baseColor)
        val surfaceColor = if (isLight) {
            ColorUtils.blendColors(baseColor, Color.WHITE, 0.72f)
        } else {
            ColorUtils.blendColors(baseColor, Color.BLACK, 0.24f)
        }
        val startAlpha = (0.32f + glassLevel * 0.44f).coerceIn(0f, 0.86f)
        val centerAlpha = (0.24f + glassLevel * 0.38f).coerceIn(0f, 0.74f)
        val endAlpha = (0.18f + glassLevel * 0.32f).coerceIn(0f, 0.66f)
        val selectedBoost = if (selected) 0.08f else 0f
        val strokeAlpha = (0.22f + glassLevel * 0.22f + selectedBoost).coerceIn(0f, 0.58f)
        return android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, startAlpha + selectedBoost),
                ColorUtils.withAlpha(surfaceColor, centerAlpha + selectedBoost),
                ColorUtils.withAlpha(surfaceColor, endAlpha + selectedBoost)
            )
        ).apply {
            shape = if (oval) android.graphics.drawable.GradientDrawable.OVAL else android.graphics.drawable.GradientDrawable.RECTANGLE
            if (!oval) {
                setCornerRadius(cornerRadius)
            }
            setStroke(1f.dpToPx().toInt(), ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }

    private fun getBottomBackgroundColor(): Int {
        // Use theme's bottom navigation bar color, same as classic mode
        return io.legado.app.lib.theme.ThemeStore.bottomBackground(this)
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@sc
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            if (AppConfig.autoUpdateVariant) {
                if (LocalConfig.lastCheckUpdate + 24.hours.inWholeMilliseconds < System.currentTimeMillis()) {
                    AppUpdate.giteeUpdate.check(lifecycleScope)
                        .onSuccess {
                            showDialogFragment(
                                UpdateDialog(it)
                            )
                        }
                    LocalConfig.lastCheckUpdate = System.currentTimeMillis()
                }
            }
            block.resume(null)
            return@sc
        }
        LocalConfig.versionCode = appInfo.versionCode
        block.resume(null)
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@sc
        }
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all pending LiquidGlass tasks to prevent NPE
        shouldCancelLiquidGlassTasks = true
        bottomIndicatorAnimator.cancel()
        
        // Clear bound LiquidGlassView IDs to allow re-binding on recreate
        boundLiquidGlassViewIds.clear()
        
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            if (onUpBooksBadgeView == null) {
                onUpBooksBadgeView = currentBottomNav.addBadgeView(0)
            }
            onUpBooksBadgeView!!.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                if (it) {
                    bottomNavigationView.menu.clear()
                    bottomNavigationView.inflateMenu(R.menu.main_bnv)
                    bottomNavigationViewFloating.menu.clear()
                    bottomNavigationViewFloating.inflateMenu(R.menu.main_bnv)
                    onUpBooksBadgeView = null
                }
                upBottomMenu()
                if (it) {
                    binding.viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<Boolean>(EventBus.NAVIGATION_BAR_CHANGED) {
            refreshBottomNavigationConfig()
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        
        // Update menu items for both bottom navigation views
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
            menu.findItem(R.id.menu_ai_read).isVisible = true
            menu.findItem(R.id.menu_rss).isVisible = showRss
        }
        binding.bottomNavigationViewFloating.menu.let { menu ->
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
            menu.findItem(R.id.menu_ai_read).isVisible = true
            menu.findItem(R.id.menu_rss).isVisible = showRss
        }
        
        // Apply icons and tint
        NavigationBarIconConfig.applyTo(
            binding.bottomNavigationView.menu,
            this,
            AppConfig.isNightTheme
        )
        NavigationBarIconConfig.applyTo(
            binding.bottomNavigationViewFloating.menu,
            this,
            AppConfig.isNightTheme
        )
        
        // Check if tint is enabled in the current config
        val currentEntry = NavigationBarIconConfig.currentEntry(AppConfig.isNightTheme)
        val enableTint = currentEntry.config.enableTint
        
        // Apply tint only if enabled
        if (enableTint) {
            binding.bottomNavigationView.restoreThemeIconTint()
            binding.bottomNavigationViewFloating.restoreThemeIconTint()
        } else {
            // Disable automatic tint when tint is not enabled
            binding.bottomNavigationView.itemIconTintList = null
            binding.bottomNavigationViewFloating.itemIconTintList = null
        }
        
        var index = 0
        realPositions[index] = idBookshelf
        index++
        if (showDiscovery) {
            realPositions[index] = idExplore
            index++
        }
        if (showRss) {
            realPositions[index] = idRss
            index++
        }
        realPositions[index] = idMy
        bottomMenuCount = index + 1
        adapter.notifyDataSetChanged()
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
    }
    
    /**
     * Get bottom navigation menu item ID from page position - EXACT match with archive
     */
    private fun getBottomNavigationItemId(position: Int): Int {
        val fragmentId = getFragmentId(position)
        return when (fragmentId) {
            idBookshelf1, idBookshelf2 -> R.id.menu_bookshelf
            idExplore -> R.id.menu_discovery
            idRss -> R.id.menu_rss
            idMy -> R.id.menu_my_config
            else -> R.id.menu_bookshelf
        }
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            val fragmentId = getFragmentId(position)
            val menuItemId = when (fragmentId) {
                idBookshelf1, idBookshelf2 -> R.id.menu_bookshelf
                idExplore -> R.id.menu_discovery
                idRss -> R.id.menu_rss
                idMy -> R.id.menu_my_config
                else -> R.id.menu_bookshelf
            }
            // Update both bottom navigation views
            binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
            binding.bottomNavigationViewFloating.menu.findItem(menuItemId)?.isChecked = true
            
            // Update floating mode indicator position if in floating mode
            if (AppConfig.bottomBarLayoutMode == "floating") {
                // Use postDelayed to ensure smooth animation during page switch
                binding.root.postDelayed({
                    updateFloatingIndicatorPosition(menuItemId)
                }, 30L)
            }
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf1 && any is BookshelfFragment1)
                || (fragmentId == idBookshelf2 && any is BookshelfFragment2)
                || (fragmentId == idExplore && any is ExploreFragment)
                || (fragmentId == idRss && any is RssFragment)
                || (fragmentId == idMy && any is MyFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idBookshelf2 -> BookshelfFragment2(position)
                idExplore -> ExploreFragment(position)
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }

    }

    override fun openImportUi(type:Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source)
            )
        }
    }

}