package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogNavigationBarManageBinding
import io.legado.app.databinding.ItemNavigationBarIconBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarIconConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NavigationBarManageDialog : BottomSheetDialogFragment() {

    private var _binding: DialogNavigationBarManageBinding? = null
    private val binding get() = _binding!!

    private val adapter = IconAdapter()
    private var currentEntry: NavigationBarIconConfig.Entry? = null

    private val selectIcon = registerForActivityResult(HandleFileContract()) { result ->
        val request = pendingIconRequest ?: return@registerForActivityResult
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    NavigationBarIconConfig.saveIconToPackage(
                        requireContext(),
                        uri,
                        request.entry,
                        request.item.key,
                        request.selected,
                        resources.getDimensionPixelSize(R.dimen.main_bottom_nav_icon_size)
                    )
                }
            }.onSuccess {
                currentEntry = it
                notifyIfApplied(it)
                adapter.notifyDataSetChanged()
                toastOnUi(R.string.success)
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.navigation_icon_decode_failed))
            }
        }
        pendingIconRequest = null
    }

    private var pendingIconRequest: IconRequest? = null

    data class IconRequest(
        val entry: NavigationBarIconConfig.Entry,
        val item: NavigationBarIconConfig.NavItem,
        val selected: Boolean,
        val code: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNavigationBarManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Layout mode selector
        binding.textLayoutMode.text = getLayoutModeLabel(AppConfig.bottomBarLayoutMode)
        binding.cardLayoutMode.setOnClickListener {
            showLayoutModeSelector()
        }

        // Effect mode selector - only visible in floating mode
        val isFloatingMode = AppConfig.bottomBarLayoutMode == "floating"
        binding.cardEffectMode.visibility = if (isFloatingMode) View.VISIBLE else View.GONE
        
        if (isFloatingMode) {
            binding.textEffectMode.text = getEffectModeLabel(AppConfig.bottomBarEffectMode)
            binding.cardEffectMode.setOnClickListener {
                showEffectModeSelector()
            }
        }

        // Opacity control - only visible in floating mode with glass/frosted effects
        val showOpacity = isFloatingMode && AppConfig.bottomBarEffectMode != "solid"
        binding.seekBarContainer.visibility = if (showOpacity) View.VISIBLE else View.GONE
        
        if (showOpacity) {
            binding.seekBarOpacity.max = 100
            binding.seekBarOpacity.progress = if (AppConfig.bottomBarEffectMode == "frosted") {
                AppConfig.frostedGlassLevel
            } else {
                AppConfig.liquidGlassLevel
            }
            binding.textOpacityValue.text = "${binding.seekBarOpacity.progress}%"
            binding.seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.textOpacityValue.text = "$progress%"
                        if (AppConfig.bottomBarEffectMode == "frosted") {
                            AppConfig.frostedGlassLevel = progress
                        } else {
                            AppConfig.liquidGlassLevel = progress
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    notifyChanged()
                }
            })
        }

        loadCurrentEntry()
    }

    private fun showLayoutModeSelector() {
        val modes = listOf(
            getString(R.string.bottom_bar_layout_classic),
            getString(R.string.bottom_bar_layout_floating)
        )
        val currentIndex = if (AppConfig.bottomBarLayoutMode == "floating") 1 else 0
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.bottom_bar_layout_mode)
            .setSingleChoiceItems(modes.toTypedArray(), currentIndex) { dialog, which ->
                AppConfig.bottomBarLayoutMode = if (which == 1) "floating" else "classic"
                binding.textLayoutMode.text = getLayoutModeLabel(AppConfig.bottomBarLayoutMode)
                
                // Update UI visibility based on mode
                val isFloatingMode = AppConfig.bottomBarLayoutMode == "floating"
                binding.cardEffectMode.visibility = if (isFloatingMode) View.VISIBLE else View.GONE
                
                if (isFloatingMode) {
                    binding.textEffectMode.text = getEffectModeLabel(AppConfig.bottomBarEffectMode)
                    val showOpacity = AppConfig.bottomBarEffectMode != "solid"
                    binding.seekBarContainer.visibility = if (showOpacity) View.VISIBLE else View.GONE
                } else {
                    binding.seekBarContainer.visibility = View.GONE
                }
                
                notifyChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun showEffectModeSelector() {
        val modes = listOf(
            getString(R.string.bottom_bar_effect_solid),
            getString(R.string.bottom_bar_effect_glass),
            getString(R.string.bottom_bar_effect_frosted)
        )
        val currentIndex = when (AppConfig.bottomBarEffectMode) {
            "solid" -> 0
            "frosted" -> 2
            else -> 1
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.bottom_bar_effect_mode)
            .setSingleChoiceItems(modes.toTypedArray(), currentIndex) { dialog, which ->
                AppConfig.bottomBarEffectMode = when (which) {
                    0 -> "solid"
                    2 -> "frosted"
                    else -> "glass"
                }
                binding.textEffectMode.text = getEffectModeLabel(AppConfig.bottomBarEffectMode)
                
                // Update opacity visibility and value based on effect mode
                val showOpacity = AppConfig.bottomBarEffectMode != "solid"
                binding.seekBarContainer.visibility = if (showOpacity) View.VISIBLE else View.GONE
                
                if (showOpacity) {
                    val progress = if (AppConfig.bottomBarEffectMode == "frosted") {
                        AppConfig.frostedGlassLevel
                    } else {
                        AppConfig.liquidGlassLevel
                    }
                    binding.seekBarOpacity.progress = progress
                    binding.textOpacityValue.text = "$progress%"
                }
                
                notifyChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun getLayoutModeLabel(mode: String): String {
        return when (mode) {
            "floating" -> getString(R.string.bottom_bar_layout_floating)
            else -> getString(R.string.bottom_bar_layout_classic)
        }
    }

    private fun getEffectModeLabel(mode: String): String {
        return when (mode) {
            "solid" -> getString(R.string.bottom_bar_effect_solid)
            "frosted" -> getString(R.string.bottom_bar_effect_frosted)
            else -> getString(R.string.bottom_bar_effect_glass)
        }
    }

    private fun loadCurrentEntry() {
        lifecycleScope.launch {
            val isNight = AppConfig.isNightTheme
            currentEntry = NavigationBarIconConfig.currentEntry(isNight)
            adapter.notifyDataSetChanged()
        }
    }

    private fun notifyIfApplied(entry: NavigationBarIconConfig.Entry) {
        if (entry.dirName == NavigationBarIconConfig.activeDirName(entry.config.isNightMode)) {
            NavigationBarIconConfig.apply(entry)
            postEvent(EventBus.NAVIGATION_BAR_CHANGED, entry.config.isNightMode)
        }
    }

    private fun notifyChanged() {
        postEvent(EventBus.NAVIGATION_BAR_CHANGED, AppConfig.isNightTheme)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun show(fragmentManager: FragmentManager, tag: String?) {
            val dialog = NavigationBarManageDialog()
            dialog.show(fragmentManager, tag)
        }
    }

    private inner class IconAdapter : RecyclerView.Adapter<IconViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val binding = ItemNavigationBarIconBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return IconViewHolder(binding)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val item = NavigationBarIconConfig.items[position]
            holder.bind(item)
        }

        override fun getItemCount(): Int = NavigationBarIconConfig.items.size
    }

    private inner class IconViewHolder(private val binding: ItemNavigationBarIconBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NavigationBarIconConfig.NavItem) {
            val entry = currentEntry ?: return
            binding.textName.setText(item.titleRes)

            val normalDrawable = NavigationBarIconConfig.previewDrawable(
                requireContext(), entry, item, false
            )
            val selectedDrawable = NavigationBarIconConfig.previewDrawable(
                requireContext(), entry, item, true
            )

            binding.iconNormal.setImageDrawable(normalDrawable)
            binding.iconSelected.setImageDrawable(selectedDrawable)

            binding.btnSelectNormal.setOnClickListener {
                pendingIconRequest = IconRequest(entry, item, false, 0)
                selectIcon.launch {
                    mode = HandleFileContract.IMAGE
                    requestCode = item.hashCode() * 2
                }
            }

            binding.btnSelectSelected.setOnClickListener {
                pendingIconRequest = IconRequest(entry, item, true, 0)
                selectIcon.launch {
                    mode = HandleFileContract.IMAGE
                    requestCode = item.hashCode() * 2 + 1
                }
            }

            binding.btnClearNormal.setOnClickListener {
                lifecycleScope.launch {
                    kotlin.runCatching {
                        withContext(Dispatchers.IO) {
                            NavigationBarIconConfig.clearIcon(entry, item.key, false)
                        }
                    }.onSuccess {
                        currentEntry = it
                        notifyIfApplied(it)
                        adapter.notifyDataSetChanged()
                    }
                }
            }

            binding.btnClearSelected.setOnClickListener {
                lifecycleScope.launch {
                    kotlin.runCatching {
                        withContext(Dispatchers.IO) {
                            NavigationBarIconConfig.clearIcon(entry, item.key, true)
                        }
                    }.onSuccess {
                        currentEntry = it
                        notifyIfApplied(it)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }
}