package io.legado.app.ui.book.read.config

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.RegexColorRule
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.provider.TextChapterLayout
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi

class RegexColorConfigDialog : BottomSheetDialogFragment(R.layout.dialog_regex_color_config),
    FontSelectDialog.CallBack {

    private var _binding: View? = null
    private val binding get() = _binding!!
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddRule: MaterialButton
    private lateinit var adapter: RegexColorRuleAdapter
    private var editingRulePosition = -1

    companion object {
        const val REGEX_RULE_COLOR = 7900
        var pendingColorPosition = -1
    }

    override val curFontPath: String
        get() = if (editingRulePosition in ReadBookConfig.regexColorRules.indices) {
            ReadBookConfig.regexColorRules[editingRulePosition].fontPath
        } else ""

    override fun selectFont(path: String) {
        if (editingRulePosition in ReadBookConfig.regexColorRules.indices) {
            ReadBookConfig.regexColorRules[editingRulePosition].fontPath = path
            notifyConfigChanged()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflater.inflate(R.layout.dialog_regex_color_config, container, false)
        return binding!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = binding.findViewById(R.id.recyclerView)
        btnAddRule = binding.findViewById(R.id.btn_add_rule)
        
        adapter = RegexColorRuleAdapter(
            onDeleteClick = { position -> deleteRule(position) },
            onColorClick = { position -> showColorPicker(position) },
            onFontClick = { position -> showFontSelect(position) }
        )
        
        initView()
        initViewEvent()
    }

    private fun initView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.setItems(ReadBookConfig.regexColorRules)
    }

    private fun initViewEvent() {
        btnAddRule.setOnClickListener {
            showAddRuleDialog()
        }
    }

    private fun showAddRuleDialog() {
        val defaultPatterns = listOf(
            "\u201C匹配内容\u201D" to "\u201C.+?\u201D",
            "《匹配内容》" to "《.+?》",
            "\"匹配内容\"" to "\".+?\""
        )
        val displayItems = defaultPatterns.map { it.first } + "自定义规则"
        
        AlertDialog.Builder(requireContext())
            .setTitle("添加正则规则")
            .setItems(displayItems.toTypedArray()) { _, i ->
                if (i < defaultPatterns.size) {
                    val (name, pattern) = defaultPatterns[i]
                    addRule(name, pattern)
                } else {
                    showCustomRuleDialog()
                }
            }
            .show()
    }

    private fun showCustomRuleDialog() {
        val editText = EditText(context).apply {
            hint = "输入正则表达式，如：\\u201C.+?\\u201D"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("自定义正则规则")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val pattern = editText.text.toString().trim()
                if (pattern.isNotEmpty()) {
                    addRule(pattern, pattern)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addRule(name: String, pattern: String) {
        val rule = RegexColorRule(name, pattern, ReadBookConfig.durConfig.curTextAccentColor())
        ReadBookConfig.regexColorRules.add(rule)
        notifyConfigChanged()
    }

    private fun deleteRule(position: Int) {
        if (position >= 0 && position < ReadBookConfig.regexColorRules.size) {
            ReadBookConfig.regexColorRules.removeAt(position)
            notifyConfigChanged()
        }
    }

    private fun showColorPicker(position: Int) {
        if (position !in ReadBookConfig.regexColorRules.indices) return
        editingRulePosition = position
        pendingColorPosition = position
        val rule = ReadBookConfig.regexColorRules[position]
        val colorValue = rule.color or 0xFF000000.toInt()
        ColorPickerDialog.newBuilder()
            .setColor(colorValue)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(REGEX_RULE_COLOR)
            .show(requireActivity())
    }

    private fun showFontSelect(position: Int) {
        if (position !in ReadBookConfig.regexColorRules.indices) return
        editingRulePosition = position
        FontSelectDialog().show(childFragmentManager, "regexFontSelect")
    }

    fun onColorSelected(color: Int) {
        if (editingRulePosition in ReadBookConfig.regexColorRules.indices) {
            ReadBookConfig.regexColorRules[editingRulePosition].color = color
            notifyConfigChanged()
        }
    }

    private fun notifyConfigChanged() {
        ReadBookConfig.saveRegexColorRules()
        TextChapterLayout.invalidateRegexCache()
        adapter.setItems(ReadBookConfig.regexColorRules)
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class RegexColorRuleAdapter(
    private val onDeleteClick: ((Int) -> Unit)? = null,
    private val onColorClick: ((Int) -> Unit)? = null,
    private val onFontClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<RegexColorRuleAdapter.ViewHolder>() {

    private var items: List<RegexColorRule> = emptyList()

    fun setItems(items: List<RegexColorRule>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_regex_color_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRuleName = itemView.findViewById<TextView>(R.id.tv_rule_name)
        val tvRulePattern = itemView.findViewById<TextView>(R.id.tv_rule_pattern)
        val btnSelectFont = itemView.findViewById<MaterialButton>(R.id.btn_select_font)
        val btnSelectColor = itemView.findViewById<MaterialButton>(R.id.btn_select_color)
        val btnDelete = itemView.findViewById<ImageButton>(R.id.btn_delete)

        fun bind(item: RegexColorRule, position: Int) {
            tvRuleName.text = item.name
            tvRulePattern.text = item.pattern
            btnSelectColor.setBackgroundColor(item.color or 0xFF000000.toInt())
            btnSelectFont.setOnClickListener {
                onFontClick?.invoke(position)
            }
            btnSelectColor.setOnClickListener {
                onColorClick?.invoke(position)
            }
            btnDelete.setOnClickListener {
                onDeleteClick?.invoke(position)
            }
        }
    }
}
