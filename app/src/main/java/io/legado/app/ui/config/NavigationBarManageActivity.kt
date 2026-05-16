package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.databinding.ItemThemePackageBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarIconConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NavigationBarManageActivity : BaseActivity<ActivityThemeManageBinding>() {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)

    private val adapter = Adapter()
    private var editingEntry: NavigationBarIconConfig.Entry? = null
    private var editingDialog: LinearLayout? = null
    private var pendingConfig: NavigationBarIconConfig.Config? = null
    private var pendingIconRequest: IconRequest? = null

    private val selectIcon = registerForActivityResult(HandleFileContract()) { result ->
        val request = pendingIconRequest?.takeIf { it.code == result.requestCode } ?: return@registerForActivityResult
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    NavigationBarIconConfig.saveIconToPackage(
                        this@NavigationBarManageActivity,
                        uri,
                        request.entry,
                        request.item.key,
                        request.selected,
                        resources.getDimensionPixelSize(R.dimen.main_bottom_nav_icon_size)
                    )
                }
            }.onSuccess {
                editingEntry = it
                pendingConfig = it.config.copy(icons = it.config.icons.toMutableMap())
                // Notify MainActivity to update bottom bar icons
                postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
                refreshEditDialog()
                toastOnUi(R.string.success)
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.navigation_icon_decode_failed))
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = getString(R.string.navigation_bar_manage)
        initView()
        loadPackages()
    }

    override fun observeLiveBus() {
        // No need to observe RECREATE event
    }

    private fun initView() = binding.run {
        // Setup global settings
        setupGlobalSettings()
        
        // Setup add button
        btnAdd.text = getString(R.string.theme_add)
        btnAdd.background = UiCorner.actionSelector(
            ContextCompat.getColor(this@NavigationBarManageActivity, R.color.background_card),
            ContextCompat.getColor(this@NavigationBarManageActivity, R.color.background_menu),
            UiCorner.actionRadius(this@NavigationBarManageActivity)
        )
        btnAdd.setOnClickListener {
            showAddDialog()
        }
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this@NavigationBarManageActivity)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        
        // Remove import menu item
        titleBar.toolbar.menu.clear()
    }

    private fun setupGlobalSettings() = binding.run {
        // Layout Mode - Segmented Button
        when (AppConfig.bottomBarLayoutMode) {
            "classic" -> toggleLayoutMode.check(R.id.btn_layout_classic)
            "floating" -> toggleLayoutMode.check(R.id.btn_layout_floating)
        }
        toggleLayoutMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btn_layout_classic -> "classic"
                R.id.btn_layout_floating -> "floating"
                else -> return@addOnButtonCheckedListener
            }
            AppConfig.bottomBarLayoutMode = newMode
            postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
        }
        
        // Effect Mode - Segmented Button
        when (AppConfig.bottomBarEffectMode) {
            "solid" -> toggleEffectMode.check(R.id.btn_effect_solid)
            "glass" -> toggleEffectMode.check(R.id.btn_effect_glass)
            "frosted" -> toggleEffectMode.check(R.id.btn_effect_frosted)
        }
        toggleEffectMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btn_effect_solid -> "solid"
                R.id.btn_effect_glass -> "glass"
                R.id.btn_effect_frosted -> "frosted"
                else -> return@addOnButtonCheckedListener
            }
            AppConfig.bottomBarEffectMode = newMode
            updateOpacityDisplay()
            postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
        }
        
        // Opacity - SeekBar
        updateOpacityDisplay()
        seekbarOpacity.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (AppConfig.bottomBarEffectMode == "frosted") {
                        AppConfig.frostedGlassLevel = progress
                    } else {
                        AppConfig.liquidGlassLevel = progress
                    }
                    updateOpacityDisplay()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
            }
        })
    }

    private fun updateOpacityDisplay() {
        val level = if (AppConfig.bottomBarEffectMode == "frosted") {
            AppConfig.frostedGlassLevel
        } else {
            AppConfig.liquidGlassLevel
        }
        binding.tvOpacity.text = "$level%"
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    NavigationBarIconConfig.loadEntries(false, includeRemote = false)
                }
            }.onSuccess { entries ->
                adapter.submit(entries, NavigationBarIconConfig.activeDirName(false))
            }.onFailure { e ->
                toastOnUi("加载配置失败: ${e.localizedMessage}")
            }
        }
    }

    private fun showEditDialog(entry: NavigationBarIconConfig.Entry?) {
        val base = entry ?: NavigationBarIconConfig.Entry(
            NavigationBarIconConfig.Config(
                name = nextPackageName(),
                isNightMode = false
            ),
            NavigationBarIconConfig.Source.LOCAL,
            ""
        )
        
        editingEntry = base
        pendingConfig = editingEntry!!.config.copy(icons = editingEntry!!.config.icons.toMutableMap())
        val root = buildEditView()
        editingDialog = root
        alert(R.string.navigation_bar_edit) {
            customView { root }
            okButton {
                saveEditingPackage()
            }
            cancelButton()
        }
    }

    private fun showAddDialog() {
        // Create new icon set
        val newEntry = NavigationBarIconConfig.Entry(
            NavigationBarIconConfig.Config(
                name = nextPackageName(),
                isNightMode = false
            ),
            NavigationBarIconConfig.Source.LOCAL,
            ""
        )
        showEditDialog(newEntry)
    }

    private fun buildEditView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
            
            // Add name input - use EditText for editable name
            val nameInput = android.widget.EditText(this@NavigationBarManageActivity).apply {
                tag = "name"
                setText(editingEntry?.config?.name ?: "")
                hint = getString(R.string.navigation_bar_name)
                setBackgroundResource(R.drawable.bg_book_info_intro_panel)
                setPadding(16.dp, 12.dp, 16.dp, 12.dp)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 16.dp
                }
            }
            addView(nameInput)
            
            // Add enable tint switch
            val tintLayout = LinearLayout(this@NavigationBarManageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 16.dp
                }
            }
            
            val tintLabel = TextView(this@NavigationBarManageActivity).apply {
                text = "启用图标着色"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val tintSwitch = io.legado.app.lib.theme.view.ThemeSwitch(this@NavigationBarManageActivity).apply {
                tag = "enable_tint"
                isChecked = editingEntry?.config?.enableTint ?: true
            }
            
            tintLayout.addView(tintLabel)
            tintLayout.addView(tintSwitch)
            addView(tintLayout)
            
            // Add icon editing rows for all navigation items
            NavigationBarIconConfig.items.forEach { item ->
                addView(iconRow(item))
            }
        }
    }

    private fun refreshEditDialog() {
        val root = editingDialog ?: return
        root.removeAllViews()
        buildEditView().let { rebuilt ->
            while (rebuilt.childCount > 0) {
                root.addView(rebuilt.getChildAt(0).also { rebuilt.removeView(it) })
            }
        }
    }

    private fun iconRow(item: NavigationBarIconConfig.NavItem): View {
        val entry = editingEntry ?: return View(this)
        
        // Inflate the XML layout
        val view = layoutInflater.inflate(R.layout.item_navigation_bar_icon, null)
        
        // Set the item name
        val textName = view.findViewById<TextView>(R.id.textName)
        textName.setText(item.titleRes)
        
        // Setup normal icon preview - load asynchronously in coroutine
        val iconNormal = view.findViewById<ImageView>(R.id.iconNormal)
        lifecycleScope.launch(Dispatchers.IO) {
            val drawable = NavigationBarIconConfig.previewDrawable(this@NavigationBarManageActivity, entry, item, false)
            withContext(Dispatchers.Main) {
                iconNormal.setImageDrawable(drawable)
            }
        }
        setupIconClick(iconNormal, entry, item, false)
        
        // Setup selected icon preview - load asynchronously in coroutine
        val iconSelected = view.findViewById<ImageView>(R.id.iconSelected)
        lifecycleScope.launch(Dispatchers.IO) {
            val drawable = NavigationBarIconConfig.previewDrawable(this@NavigationBarManageActivity, entry, item, true)
            withContext(Dispatchers.Main) {
                iconSelected.setImageDrawable(drawable)
            }
        }
        setupIconClick(iconSelected, entry, item, true)
        
        return view
    }
    
    private fun setupIconClick(iconView: ImageView, entry: NavigationBarIconConfig.Entry, item: NavigationBarIconConfig.NavItem, selected: Boolean) {
        val descRes = if (selected) R.string.navigation_icon_selected else R.string.navigation_icon_normal
        iconView.contentDescription = getString(descRes)
        
        iconView.setOnClickListener {
            selector(
                getString(descRes),
                listOf(getString(R.string.select_image), getString(R.string.delete))
            ) { _, index ->
                if (index == 0) {
                    val code = NavigationBarIconConfig.items.indexOf(item) * 2 + if (selected) 1 else 0
                    pendingIconRequest = IconRequest(code, entry, item, selected)
                    selectIcon.launch {
                        mode = HandleFileContract.FILE
                        requestCode = code
                        title = getString(R.string.navigation_icon_select_file)
                        allowExtensions = arrayOf("ico", "svg", "png", "jpg", "jpeg")
                    }
                } else {
                    editingEntry = NavigationBarIconConfig.clearIcon(entry, item.key, selected)
                    pendingConfig = editingEntry!!.config.copy(icons = editingEntry!!.config.icons.toMutableMap())
                    postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
                    refreshEditDialog()
                }
            }
        }
    }

    private fun saveEditingPackage() {
        val config = pendingConfig ?: return
        val name = editingDialog?.findViewWithTag<android.widget.EditText>("name")?.text?.toString()?.trim().orEmpty()
        val enableTint = editingDialog?.findViewWithTag<io.legado.app.lib.theme.view.ThemeSwitch>("enable_tint")?.isChecked ?: true
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    NavigationBarIconConfig.addOrUpdate(config.copy(name = name.ifBlank { nextPackageName() }, enableTint = enableTint), editingEntry)
                }
            }.onSuccess {
                // Notify MainActivity to update bottom bar icons
                postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
                toastOnUi(R.string.success)
                loadPackages()
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun applyPackage(entry: NavigationBarIconConfig.Entry) {
        val isNight = entry.config.isNightMode
        val dirName = entry.dirName
        try {
            NavigationBarIconConfig.apply(entry)
        } catch (e: Exception) {
            toastOnUi("应用失败: ${e.message}")
            return
        }
        postEvent(EventBus.NAVIGATION_BAR_CHANGED, isNight)
        loadPackages()
        toastOnUi("已应用: $dirName")
    }

    private fun showActions(entry: NavigationBarIconConfig.Entry) {
        val actions = buildList {
            add(NavAction.APPLY)
            // Allow editing for all configs (default config can be edited to become custom)
            add(NavAction.EDIT)
            // Only allow delete for non-default configs
            if (entry.dirName != NavigationBarIconConfig.DEFAULT_DIR_NAME) {
                add(NavAction.DELETE)
            }
        }
        selector(entry.config.name, actions.map { getString(it.titleRes) }) { _, index ->
            when (actions[index]) {
                NavAction.APPLY -> applyPackage(entry)
                NavAction.EDIT -> showEditDialog(entry)
                NavAction.DELETE -> confirmDelete(entry)
            }
        }
    }

    private fun confirmDelete(entry: NavigationBarIconConfig.Entry) {
        alert(getString(R.string.delete), "确定删除此图标套装？") {
            yesButton {
                lifecycleScope.launch {
                    kotlin.runCatching {
                        withContext(Dispatchers.IO) {
                            NavigationBarIconConfig.deleteLocal(entry)
                        }
                    }.onSuccess {
                        postEvent(EventBus.NAVIGATION_BAR_CHANGED, false)
                        toastOnUi(R.string.success)
                        loadPackages()
                    }.onFailure {
                        toastOnUi(it.localizedMessage)
                    }
                }
            }
            noButton()
        }
    }

    private fun nextPackageName(): String {
        val base = getString(R.string.navigation_bar_custom_name)
        // Get all used names from current list and also check existing configs
        val usedNames = adapter.items.map { it.config.name }.toSet()
        
        // Try base name first
        if (base !in usedNames) return base
        
        // Try numbered variants
        for (index in 2..999) {
            val name = "$base $index"
            if (name !in usedNames) return name
        }
        
        // Fallback with timestamp
        return "$base ${System.currentTimeMillis()}"
    }

    private fun effectModeLabel(value: String): String {
        return when (value) {
            "solid" -> getString(R.string.bottom_bar_effect_solid)
            "frosted" -> getString(R.string.bottom_bar_effect_frosted)
            else -> getString(R.string.bottom_bar_effect_glass)
        }
    }

    private fun layoutModeLabel(value: String): String {
        return when (value) {
            "floating" -> getString(R.string.bottom_bar_layout_floating)
            else -> getString(R.string.bottom_bar_layout_classic)
        }
    }

    private data class IconRequest(
        val code: Int,
        val entry: NavigationBarIconConfig.Entry,
        val item: NavigationBarIconConfig.NavItem,
        val selected: Boolean
    )

    private enum class NavAction(val titleRes: Int) {
        APPLY(R.string.theme_apply),
        EDIT(R.string.edit),
        DELETE(R.string.delete)
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {

        var items: List<NavigationBarIconConfig.Entry> = emptyList()
            private set
        private var activeDirName = NavigationBarIconConfig.DEFAULT_DIR_NAME

        fun submit(value: List<NavigationBarIconConfig.Entry>, activeDirName: String) {
            val old = items
            val oldActive = this.activeDirName
            items = value
            this.activeDirName = activeDirName
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = old.size
                override fun getNewListSize(): Int = value.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return old[oldItemPosition].dirName == value[newItemPosition].dirName &&
                        old[oldItemPosition].config.isNightMode == value[newItemPosition].config.isNightMode
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = old[oldItemPosition]
                    val newItem = value[newItemPosition]
                    val oldApplied = oldItem.dirName == oldActive
                    val newApplied = newItem.dirName == activeDirName
                    return oldItem == newItem && oldApplied == newApplied
                }
            }).dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ItemThemePackageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        inner class Holder(private val itemBinding: ItemThemePackageBinding) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(entry: NavigationBarIconConfig.Entry) = itemBinding.run {
                tvName.text = entry.config.name
                
                // 设置预览图 - 显示第一个图标作为预览
                val firstItem = NavigationBarIconConfig.items.firstOrNull()
                if (firstItem != null) {
                    ivPreview.setImageDrawable(NavigationBarIconConfig.previewDrawable(this@NavigationBarManageActivity, entry, firstItem, false))
                    cardPreview.visibility = View.VISIBLE
                } else {
                    cardPreview.visibility = View.GONE
                }
                
                btnApply.text = getString(if (entry.dirName == activeDirName) R.string.theme_applied_state else R.string.theme_apply)
                btnEdit.text = getString(R.string.edit)
                
                // 默认套装不能删除
                btnDelete.visibility = if (entry.dirName == NavigationBarIconConfig.DEFAULT_DIR_NAME) View.GONE else View.VISIBLE
                
                btnApply.setOnClickListener { applyPackage(entry) }
                btnEdit.setOnClickListener { showEditDialog(entry) }
                btnDelete.setOnClickListener { confirmDelete(entry) }
                root.setOnClickListener { showActions(entry) }
            }
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
