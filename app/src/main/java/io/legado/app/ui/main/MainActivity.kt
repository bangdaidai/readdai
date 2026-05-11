@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.get
import androidx.core.view.postDelayed
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

    /**
     * Get the current ViewPager based on layout mode
     */
    private val currentPageViewPager: io.legado.app.ui.widget.LockableViewPager
        get() = if (AppConfig.bottomBarLayoutMode == "floating") {
            binding.viewPagerMainFloating
        } else {
            binding.viewPagerMain
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
                currentPageViewPager.currentItem = 0
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
            currentPageViewPager.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                currentPageViewPager.postDelayed(2000) {
                    viewModel.upAllBookToc()
                }
            }
            currentPageViewPager.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 确保底部导航栏的选中状态与当前页面一致
        resetBottomNavSelection()
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
                currentPageViewPager.setCurrentItem(0, false)

            R.id.menu_discovery ->
                currentPageViewPager.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_ai_read -> {
                startActivity(Intent(this@MainActivity, AiChatActivity::class.java))
            }

            R.id.menu_rss ->
                currentPageViewPager.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                currentPageViewPager.setCurrentItem(realPositions.indexOf(idMy), false)
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
        // Determine which layout to use
        val isFloatingMode = AppConfig.bottomBarLayoutMode == "floating"
        
        classicLayout.visibility = if (isFloatingMode) View.GONE else View.VISIBLE
        floatingLayout.visibility = if (isFloatingMode) View.VISIBLE else View.GONE
        
        // Initialize the appropriate ViewPager and BottomNavigationView
        val viewPager = if (isFloatingMode) viewPagerMainFloating else viewPagerMain
        val bottomNav = if (isFloatingMode) bottomNavigationViewFloating else bottomNavigationView
        
        viewPager.setEdgeEffectColor(primaryColor)
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = adapter
        viewPager.addOnPageChangeListener(PageChangeCallback())
        
        bottomNav.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNav.setOnNavigationItemReselectedListener(this@MainActivity)
        
        // Apply floating navigation bar style if in floating mode
        if (isFloatingMode) {
            updateBottomBarStyle()
        } else {
            // Classic mode: always use solid opaque background
            bottomNav.setBackgroundColor(getBottomBackgroundColor())
            bottomNav.alpha = 1.0f
            
            // Apply e-ink border if needed
            if (AppConfig.isEInkMode) {
                bottomNav.setBackgroundResource(R.drawable.bg_eink_border_top)
            }
            bottomNav.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
                val height = windowInsets.navigationBarHeight
                view.bottomPadding = height
                windowInsets.inset(0, 0, 0, height)
            }
        }
    }

    /**
     * Update bottom navigation bar style based on configuration
     */
    private fun updateBottomBarStyle() {
        // Only apply styles in floating mode
        if (AppConfig.bottomBarLayoutMode != "floating") return
        
        val liquidGlassView = binding.root.findViewById<LiquidGlassView>(R.id.bottom_navigation_liquid_glass)
        val backgroundView = binding.root.findViewById<View>(R.id.bottom_navigation_background)
        
        when (AppConfig.bottomBarEffectMode) {
            "solid" -> {
                // Solid mode: hide liquid glass, show solid background with capsule shape
                liquidGlassView?.visibility = View.GONE
                backgroundView?.visibility = View.VISIBLE
                // Create a capsule-shaped background with theme color
                val bgColor = getBottomBackgroundColor()
                val capsuleDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 28f.dpToPx().toFloat()
                    setColor(bgColor)
                }
                backgroundView?.background = capsuleDrawable
                backgroundView?.alpha = 1.0f // Fully opaque for solid mode
            }
            "frosted", "glass" -> {
                // Frosted/Glass mode: use LiquidGlassView with real blur effect
                liquidGlassView?.visibility = View.VISIBLE
                backgroundView?.visibility = View.VISIBLE  // Keep background visible for LiquidGlassView to work on
                
                liquidGlassView?.let { glass ->
                    setupLiquidGlassView(glass)
                }
            }
        }
    }
    
    /**
     * Setup LiquidGlassView with archive-style parameters
     */
    private fun setupLiquidGlassView(liquidGlassView: LiquidGlassView) {
        // Bind to content container for blur effect
        liquidGlassView.bind(binding.contentContainer)
        
        val effectMode = AppConfig.bottomBarEffectMode
        val isFrosted = effectMode == "frosted"
        val glassLevel = if (isFrosted) {
            AppConfig.frostedGlassLevel / 100f
        } else {
            AppConfig.liquidGlassLevel / 100f
        }
        
        // Calculate blur radius - frosted has stronger blur
        val blurRadius = if (isFrosted) {
            (10f + glassLevel * 24f).dpToPx()
        } else {
            (5f + glassLevel * 14f).dpToPx()
        }
        
        // Calculate tint alpha - frosted is more opaque
        val tintAlpha = if (isFrosted) {
            0.12f + glassLevel * 0.18f
        } else {
            0.05f + glassLevel * 0.10f
        }
        
        // Calculate dispersion - frosted has less dispersion
        val dispersion = if (isFrosted) {
            (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f)
        } else {
            0.46f + glassLevel * 0.32f
        }
        
        // Calculate refraction parameters
        val refractionHeight = if (isFrosted) {
            (12f + glassLevel * 10f).dpToPx()
        } else {
            (18f + glassLevel * 14f).dpToPx()
        }
        
        val refractionOffset = if (isFrosted) {
            (36f + glassLevel * 18f).dpToPx()
        } else {
            (72f + glassLevel * 34f).dpToPx()
        }
        
        // Configure LiquidGlassView
        liquidGlassView.setCornerRadius(28f.dpToPx())  // Match bg_bottom_nav_floating.xml corner radius
        liquidGlassView.setRefractionHeight(refractionHeight)
        liquidGlassView.setRefractionOffset(refractionOffset)
        liquidGlassView.setDispersion(dispersion)
        liquidGlassView.setBlurRadius(blurRadius)
        liquidGlassView.setTintAlpha(tintAlpha)
        
        // Set tint color based on theme
        if (AppConfig.isNightTheme) {
            liquidGlassView.setTintColorRed(0.10f)
            liquidGlassView.setTintColorGreen(0.10f)
            liquidGlassView.setTintColorBlue(0.10f)
        } else {
            liquidGlassView.setTintColorRed(0.95f)
            liquidGlassView.setTintColorGreen(0.95f)
            liquidGlassView.setTintColorBlue(0.95f)
        }
        
        liquidGlassView.setDraggableEnabled(false)
        
        // Enable clipping to ensure capsule shape
        liquidGlassView.clipToOutline = true
        liquidGlassView.setElasticEnabled(true)
        liquidGlassView.setTouchEffectEnabled(true)
        liquidGlassView.isClickable = false
        liquidGlassView.isFocusable = false
        liquidGlassView.invalidate()
    }
    
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun getBottomBackgroundColor(): Int {
        return if (AppConfig.isNightTheme) {
            android.graphics.Color.parseColor("#CC1A1A1A")
        } else {
            android.graphics.Color.parseColor("#CCFFFFFF")
        }
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
                    currentPageViewPager.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<Boolean>(EventBus.NAVIGATION_BAR_CHANGED) {
            upBottomMenu()
            updateBottomBarStyle()
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        
        // Switch between classic and floating layout
        if (AppConfig.bottomBarLayoutMode == "floating") {
            binding.bottomNavigationView.visibility = View.GONE
            binding.bottomNavigationViewFloating.visibility = View.VISIBLE
            binding.viewPagerMain.visibility = View.GONE
            binding.viewPagerMainFloating.visibility = View.VISIBLE
        } else {
            binding.bottomNavigationView.visibility = View.VISIBLE
            binding.bottomNavigationViewFloating.visibility = View.GONE
            binding.viewPagerMain.visibility = View.VISIBLE
            binding.viewPagerMainFloating.visibility = View.GONE
        }
        
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
                currentPageViewPager.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS) {
                currentPageViewPager.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> currentPageViewPager.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
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
            binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
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