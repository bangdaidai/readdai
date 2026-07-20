package io.legado.app.ui.highlight.edit

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.widget.doAfterTextChanged
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.HighlightRule
import io.legado.app.databinding.DialogHighlightRuleEditBinding
import io.legado.app.help.highlight.HighlightRuleGroupStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.help.highlight.HighlightRuleBackgroundManager
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.book.read.config.HighlightRulePreview
import io.legado.app.ui.book.read.config.NineSliceEditorDialog
import java.io.File

class HighlightRuleEditDialog(
    private val sourceRule: HighlightRule? = null,
    private val defaultGroup: String? = null,
    private val defaultSampleText: String? = null,
    private val defaultPattern: String? = null,
    private val defaultScope: String? = null,
    private val onSave: (HighlightRule) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_highlight_rule_edit, true), ColorPickerDialogListener {

    private val binding by viewBinding(DialogHighlightRuleEditBinding::bind)
    private lateinit var editingRule: HighlightRule
    private var groupItems = listOf<String>()
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0

    private val selectBgImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            editingRule.bgColor = null
            val rawPath = RealPathUtil.getPath(requireContext(), uri) ?: uri.toString()
            val savedPath = HighlightRuleBackgroundManager.copyToInternal(requireContext(), rawPath)
            editingRule.bgImage = savedPath ?: rawPath
            binding.etBgImage.setText(savedPath ?: rawPath)
            updateBgPreview()
            updatePreview()
        }
    }
    private var isRegexMode = false

    companion object {
        // 适配方式：去掉「平铺」，保留 拉伸填充 / 居中裁剪 / 九宫格
        private val BG_FIT_OPTIONS = listOf("拉伸填充", "居中裁剪", "九宫格")
        private val BG_FIT_VALUES = listOf(1, 2, 3)

        fun newInstance(
            sampleText: String? = null,
            pattern: String? = null,
            scope: String? = null,
            sourceRule: HighlightRule? = null,
            defaultGroup: String? = null,
            onSave: (HighlightRule) -> Unit = {}
        ): HighlightRuleEditDialog {
            return HighlightRuleEditDialog(
                sourceRule = sourceRule,
                defaultGroup = defaultGroup,
                onSave = onSave,
                defaultSampleText = sampleText,
                defaultPattern = pattern,
                defaultScope = scope
            )
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        val baseRule = sourceRule?.copy() ?: HighlightRule(
            group = defaultGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP
        )
        editingRule = baseRule.copy(
            sampleText = defaultSampleText ?: baseRule.sampleText,
            pattern = defaultPattern ?: baseRule.pattern,
            scope = defaultScope ?: baseRule.scope
        )
        groupItems = HighlightRuleGroupStore.load(requireContext())
        setupViews()
        bindData()
        bindEvents()
        updatePreview()
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(io.legado.app.constant.EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                updatePreview()
            }
        }
    }

    private fun initTheme() {
        primaryTextColor = requireContext().getPrimaryTextColor(true)
        secondaryTextColor = requireContext().getSecondaryTextColor(true)
        accentColor = requireContext().accentColor

        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.btnSave.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        )
        binding.btnSave.setBackgroundColor(accentColor)

        binding.etPattern.setTextColor(primaryTextColor)
        binding.etPattern.setHintTextColor(secondaryTextColor)
        binding.etName.setTextColor(primaryTextColor)
        binding.etName.setHintTextColor(secondaryTextColor)
        binding.etTextColor.setTextColor(primaryTextColor)
        binding.etTextColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineColor.setTextColor(primaryTextColor)
        binding.etUnderlineColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineWidth.setTextColor(primaryTextColor)
        binding.etUnderlineOffset.setTextColor(primaryTextColor)
        binding.etSvgPath.setTextColor(primaryTextColor)
        binding.etSvgPath.setHintTextColor(secondaryTextColor)
        binding.etScope.setTextColor(primaryTextColor)
        binding.etScope.setHintTextColor(secondaryTextColor)
        binding.etExcludeScope.setTextColor(primaryTextColor)
        binding.etExcludeScope.setHintTextColor(secondaryTextColor)
        binding.etSampleText.setTextColor(primaryTextColor)
        binding.etSampleText.setHintTextColor(secondaryTextColor)
        binding.etSampleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        binding.tvPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        binding.tvBgImagePick.setTextColor(primaryTextColor)

        binding.tvRegexToggle.setTextColor(primaryTextColor)
        binding.tvWidthMinus.setTextColor(primaryTextColor)
        binding.tvWidthPlus.setTextColor(primaryTextColor)
        binding.tvOffsetMinus.setTextColor(primaryTextColor)
        binding.tvOffsetPlus.setTextColor(primaryTextColor)
    }

    private fun setupViews() {
        binding.spGroup.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            groupItems
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spTarget.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            listOf("作用于全部", "作用于标题", "作用于正文")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spUnderlineMode.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            listOf("无", "实线下划线", "虚线下划线", "波浪下划线", "标题强调条", "自定义SVG")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spBgImageFit.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            BG_FIT_OPTIONS
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
    }

    private fun bindData() {
        binding.switchEnable.isChecked = editingRule.enabled
        binding.switchBold.isChecked = editingRule.bold
        binding.etName.setText(editingRule.name)
        binding.etPattern.setText(editingRule.pattern)
        binding.etTextColor.setText(editingRule.textColor?.toHexColor().orEmpty())
        binding.etUnderlineColor.setText(editingRule.underlineColor?.toHexColor().orEmpty())
        binding.etUnderlineWidth.setText(editingRule.underlineWidth.toString())
        binding.etUnderlineOffset.setText(editingRule.underlineOffset.formatDistance())
        binding.etSvgPath.setText(editingRule.underlineSvgPath.orEmpty())
        binding.etScope.setText(editingRule.scope.orEmpty())
        binding.etExcludeScope.setText(editingRule.excludeScope.orEmpty())
        binding.etSampleText.setText(editingRule.sampleText.ifBlank { editingRule.normalizedSampleText() })
        binding.spBgImageFit.setSelection(
            BG_FIT_VALUES.indexOf(editingRule.bgImageFit).coerceAtLeast(0)
        )
        updateNineSliceVisible()
        initPaddingSeekBar(binding.sbBgPadStart, binding.tvBgPadStart, editingRule.bgPaddingStart)
        initPaddingSeekBar(binding.sbBgPadEnd, binding.tvBgPadEnd, editingRule.bgPaddingEnd)
        initPaddingSeekBar(binding.sbBgPadTop, binding.tvBgPadTop, editingRule.bgPaddingTop)
        initPaddingSeekBar(binding.sbBgPadBottom, binding.tvBgPadBottom, editingRule.bgPaddingBottom)
        if (!editingRule.bgImage.isNullOrBlank()) {
            binding.etBgImage.setText(editingRule.bgImage)
        } else if (editingRule.bgColor != null) {
            binding.etBgImage.setText(editingRule.bgColor!!.toHexColor())
        } else {
            binding.etBgImage.setText("")
        }
        updateBgPreview()
        binding.spUnderlineMode.setSelection(editingRule.underlineMode.coerceIn(0, 5))
        val groupIndex = groupItems.indexOf(editingRule.group).takeIf { it >= 0 } ?: 0
        binding.spGroup.setSelection(groupIndex)
        binding.spTarget.setSelection(editingRule.targetScope.coerceIn(0, 2))
        
        updateColorPreview(binding.viewTextColorPreview, editingRule.textColor)
        updateColorPreview(binding.viewUnderlineColorPreview, editingRule.underlineColor)
        
        updateSvgPathVisibility(editingRule.underlineMode)
    }

    private fun bindEvents() {
        binding.btnBack.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.btnSave.setOnClickListener {
            saveRule()
        }
        binding.llTextColor.setOnClickListener {
            showColorPicker(1, editingRule.textColor ?: Color.BLACK)
        }
        binding.llUnderlineColor.setOnClickListener {
            showColorPicker(2, editingRule.underlineColor ?: Color.BLACK)
        }
        binding.tvRegexToggle.setOnClickListener {
            isRegexMode = !isRegexMode
            updateRegexToggle()
        }
        binding.tvWidthMinus.setOnClickListener {
            adjustWidth(-0.5f)
        }
        binding.tvWidthPlus.setOnClickListener {
            adjustWidth(0.5f)
        }
        binding.tvOffsetMinus.setOnClickListener {
            adjustOffset(-1f)
        }
        binding.tvOffsetPlus.setOnClickListener {
            adjustOffset(1f)
        }
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            editingRule.enabled = isChecked
        }
        binding.switchBold.setOnCheckedChangeListener { _, isChecked ->
            editingRule.bold = isChecked
            updatePreview()
        }
        binding.etName.doAfterTextChanged {
            editingRule.name = it?.toString().orEmpty()
        }
        binding.etPattern.doAfterTextChanged {
            editingRule.pattern = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etTextColor.doAfterTextChanged {
            editingRule.textColor = parseColorOrNull(it?.toString().orEmpty())
            updateColorPreview(binding.viewTextColorPreview, editingRule.textColor)
            updatePreview()
        }
        binding.etUnderlineColor.doAfterTextChanged {
            editingRule.underlineColor = parseColorOrNull(it?.toString().orEmpty())
            updateColorPreview(binding.viewUnderlineColorPreview, editingRule.underlineColor)
            updatePreview()
        }
        binding.etUnderlineWidth.doAfterTextChanged {
            editingRule.underlineWidth = it?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f
            updatePreview()
        }
        binding.etUnderlineOffset.doAfterTextChanged {
            editingRule.underlineOffset = it?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f
            updatePreview()
        }
        binding.etSvgPath.doAfterTextChanged {
            editingRule.underlineSvgPath = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etSampleText.doAfterTextChanged {
            editingRule.sampleText = it?.toString().orEmpty()
            updatePreview()
        }
        binding.spBgImageFit.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.bgImageFit = BG_FIT_VALUES[position]
                    updateNineSliceVisible()
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        setupPaddingSeekBar(binding.sbBgPadStart, binding.tvBgPadStart) { v ->
            editingRule.bgPaddingStart = v
        }
        setupPaddingSeekBar(binding.sbBgPadEnd, binding.tvBgPadEnd) { v ->
            editingRule.bgPaddingEnd = v
        }
        setupPaddingSeekBar(binding.sbBgPadTop, binding.tvBgPadTop) { v ->
            editingRule.bgPaddingTop = v
        }
        setupPaddingSeekBar(binding.sbBgPadBottom, binding.tvBgPadBottom) { v ->
            editingRule.bgPaddingBottom = v
        }
        binding.etBgImage.doAfterTextChanged {
            val text = it?.toString().orEmpty()
            val color = parseColorOrNull(text)
            if (color != null) {
                editingRule.bgColor = color
                editingRule.bgImage = null
            } else if (text.isNotBlank()) {
                editingRule.bgImage = text
                editingRule.bgColor = null
            } else {
                editingRule.bgImage = null
                editingRule.bgColor = null
            }
            updateBgPreview()
            updateNineSliceVisible()
            updatePreview()
        }
        binding.viewBgImagePreview.setOnClickListener {
            showColorPicker(3, editingRule.bgColor ?: Color.BLACK)
        }
        binding.tvBgImagePick.setOnClickListener {
            editingRule.bgColor = null
            selectBgImage.launch {
                mode = HandleFileContract.IMAGE
                title = "选择背景图片"
            }
        }
        binding.btnNineSlice.setOnClickListener {
            NineSliceEditorDialog(
                bgImagePath = editingRule.bgImage.orEmpty(),
                initLeftX = editingRule.nineLeftX,
                initRightX = editingRule.nineRightX,
                initTopY = editingRule.nineTopY,
                initBottomY = editingRule.nineBottomY,
                onConfirm = { lx, rx, ty, by ->
                    editingRule.nineLeftX = lx
                    editingRule.nineRightX = rx
                    editingRule.nineTopY = ty
                    editingRule.nineBottomY = by
                    updatePreview()
                }
            ).show(childFragmentManager, "nineSlice")
        }
        binding.spUnderlineMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.underlineMode = position
                    updateSvgPathVisibility(position)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.spGroup.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.group = groupItems.getOrElse(position) { HighlightRuleGroupStore.DEFAULT_GROUP }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.spTarget.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.targetScope = position.coerceIn(0, 2)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun updateRegexToggle() {
        if (isRegexMode) {
            binding.tvRegexToggle.setTextColor(accentColor)
        } else {
            binding.tvRegexToggle.setTextColor(primaryTextColor)
        }
    }

    private fun adjustWidth(delta: Float) {
        val current = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull() ?: 1f
        val newValue = (current + delta).coerceIn(0.1f, 10f)
        binding.etUnderlineWidth.setText(String.format("%.1f", newValue))
    }

    private fun adjustOffset(delta: Float) {
        val current = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull() ?: 2f
        val newValue = (current + delta).coerceIn(0f, 20f)
        binding.etUnderlineOffset.setText(newValue.formatDistance())
    }

    private fun updateColorPreview(view: View, color: Int?) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(color ?: 0xFFDDDDDD.toInt())
            setStroke(2 * resources.displayMetrics.density.toInt(), 0xFFBBBBBB.toInt())
        }
        view.background = drawable
    }

    private fun updateSvgPathVisibility(mode: Int) {
        binding.llSvgPath.visibility = if (mode == 5) View.VISIBLE else View.GONE
    }

    private fun updateBgPreview() {
        val bgImage = editingRule.bgImage.orEmpty()
        if (bgImage.isNotBlank()) {
            val bitmap = HighlightRuleBackgroundManager.getBitmap(bgImage)
            if (bitmap != null) {
                val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                binding.viewBgImagePreview.background = drawable
            } else {
                updateColorPreview(binding.viewBgImagePreview, null)
            }
            return
        }
        val bgColor = editingRule.bgColor
        if (bgColor != null) {
            updateColorPreview(binding.viewBgImagePreview, bgColor)
            return
        }
        updateColorPreview(binding.viewBgImagePreview, null)
    }

    private fun saveRule() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val pattern = binding.etPattern.text?.toString()?.trim().orEmpty()
        val useProtagonist = editingRule.useProtagonist
        if (!useProtagonist && pattern.isBlank()) {
            toastOnUi(R.string.highlight_rule_pattern_required)
            return
        }
        if (!useProtagonist) {
            val regexError = validatePattern(pattern)
            if (regexError != null) {
                binding.tvPatternError.visibility = View.VISIBLE
                binding.tvPatternError.text = regexError
                return
            }
        }
        editingRule = editingRule.copy(
            id = editingRule.id.ifBlank { System.currentTimeMillis().toString() },
            name = name.ifBlank { pattern.ifBlank { editingRule.name } },
            pattern = pattern,
            sampleText = binding.etSampleText.text?.toString().orEmpty(),
            group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                HighlightRuleGroupStore.DEFAULT_GROUP
            },
            targetScope = binding.spTarget.selectedItemPosition.coerceIn(0, 2),
            enabled = binding.switchEnable.isChecked,
            bold = binding.switchBold.isChecked,
            textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty()),
            underlineMode = binding.spUnderlineMode.selectedItemPosition,
            underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty()),
            underlineWidth = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f,
            underlineOffset = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f,
            underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty().takeIf { binding.spUnderlineMode.selectedItemPosition == 5 }.orEmpty(),
            bgColor = parseColorOrNull(binding.etBgImage.text?.toString().orEmpty()),
            bgImage = binding.etBgImage.text?.toString().orEmpty().takeIf { it.isNotBlank() && parseColorOrNull(it) == null },
            bgImageFit = BG_FIT_VALUES[binding.spBgImageFit.selectedItemPosition],
            bgPaddingStart = (binding.sbBgPadStart.progress - 20).toFloat(),
            bgPaddingEnd = (binding.sbBgPadEnd.progress - 20).toFloat(),
            bgPaddingTop = (binding.sbBgPadTop.progress - 20).toFloat(),
            bgPaddingBottom = (binding.sbBgPadBottom.progress - 20).toFloat(),
            nineLeftX = editingRule.nineLeftX,
            nineRightX = editingRule.nineRightX,
            nineTopY = editingRule.nineTopY,
            nineBottomY = editingRule.nineBottomY,
            scope = binding.etScope.text?.toString()?.takeIf { it.isNotBlank() },
            excludeScope = binding.etExcludeScope.text?.toString()?.takeIf { it.isNotBlank() }
        )
        onSave(editingRule)
        dismissAllowingStateLoss()
    }

    private fun setupPaddingSeekBar(
        seekBar: SeekBar,
        tv: TextView,
        onChanged: (Float) -> Unit
    ) {
        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    val dp = (progress - 20).toFloat()
                    onChanged(dp)
                    tv.text = "${dp.toInt()}dp"
                    if (fromUser) updatePreview()
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            }
        )
    }

    private fun initPaddingSeekBar(seekBar: SeekBar, tv: TextView, valueDp: Float) {
        val progress = (valueDp + 20f).toInt().coerceIn(0, 50)
        seekBar.progress = progress
        tv.text = "${valueDp.toInt()}dp"
    }

    /** 仅当填充方式=九宫格(3) 且已选背景图时，显示「九宫格编辑」按钮 */
    private fun updateNineSliceVisible() {
        val visible = BG_FIT_VALUES.getOrNull(binding.spBgImageFit.selectedItemPosition) == 3
                && !editingRule.bgImage.isNullOrBlank()
        binding.btnNineSlice.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updatePreview() {
        val pattern = binding.etPattern.text?.toString().orEmpty()
        binding.tvPatternError.visibility = View.GONE
        val patternError = validatePattern(pattern)
        if (patternError != null && pattern.isNotBlank()) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = patternError
        }
        binding.tvPreview.text = HighlightRulePreview.build(
            editingRule.copy(
                pattern = pattern,
                sampleText = binding.etSampleText.text?.toString().orEmpty()
            )
        )
    }

    private fun validatePattern(pattern: String): String? {
        if (pattern.isBlank()) return null
        return kotlin.runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
    }

    private fun Float.formatDistance(): String {
        return if (this == this.toInt().toFloat()) {
            this.toInt().toString()
        } else {
            String.format("%.1f", this)
        }
    }

    private fun parseColorOrNull(value: String): Int? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return kotlin.runCatching {
            val normalized = if (text.startsWith("#")) text else "#$text"
            Color.parseColor(normalized)
        }.getOrNull()
    }

    private fun Int.toHexColor(): String = String.format("#%08X", this)

    private fun showColorPicker(dialogId: Int, currentColor: Int) {
        val dialog = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setColor(currentColor)
            .setShowAlphaSlider(true)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setDialogId(dialogId)
            .create()
        dialog.setColorPickerDialogListener(this)
        requireActivity().supportFragmentManager
            .beginTransaction()
            .add(dialog, "color_picker_$dialogId")
            .commitAllowingStateLoss()
    }

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        when (dialogId) {
            1 -> {
                editingRule.textColor = color
                binding.etTextColor.setText(color.toHexColor())
                updateColorPreview(binding.viewTextColorPreview, color)
                updatePreview()
            }
            2 -> {
                editingRule.underlineColor = color
                binding.etUnderlineColor.setText(color.toHexColor())
                updateColorPreview(binding.viewUnderlineColorPreview, color)
                updatePreview()
            }
            3 -> {
                editingRule.bgColor = color
                editingRule.bgImage = null
                binding.etBgImage.setText(color.toHexColor())
                updateBgPreview()
                updatePreview()
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
        // no-op
    }
}
