package io.legado.app.ui.widget.dialog

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogBookTagEditBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.TagColorUtils
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书籍标签编辑对话框
 */
class BookTagEditDialog : BaseDialogFragment(R.layout.dialog_book_tag_edit) {

    /**
     * 标签信息数据类
     */
    data class TagInfo(
        val name: String,
        val color: Int,
        val groupId: Long = 0
    )

    private val binding by viewBinding(DialogBookTagEditBinding::bind)
    private var currentColor = 0 // 初始化为0，将在onFragmentCreated中设置
    private var currentGroupId: Long = 0 // 当前分组ID，0表示未分组
    private var isProcessing = false // 防止重复提交的标志
    private var groups: List<io.legado.app.data.entities.BookTagGroup> = emptyList() // 分组列表

    override fun onStart() {
        super.onStart()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 调用父类方法，确保binding被正确初始化
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理回调，避免内存泄漏
        val callbackId = arguments?.getString("callbackId")
        if (!callbackId.isNullOrEmpty()) {
            callbacks.remove(callbackId)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(ThemeStore.primaryColor(requireContext()))
        // 设置标题栏文字颜色为主题自定义的标题栏文字图标颜色
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.setTitleTextColor(titleBarTextIconColor)
        binding.toolBar.setSubtitleTextColor(titleBarTextIconColor)

        // 设置对话框内容区域背景色为主题自定义的背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)
        // 设置编辑框和其容器的背景色为主题自定义的背景色
        // 找到 TextInputLayout 并设置背景色
        val textInputLayout = findParentTextInputLayout(binding.editTag)
        textInputLayout?.setBackgroundColor(backgroundColor)
        // 设置 TextInputLayout 的 box 背景色
        textInputLayout?.boxBackgroundColor = backgroundColor
        binding.editTag.setBackgroundColor(backgroundColor)
        // 设置编辑框的文字颜色，确保在深色背景下也能正常显示
        binding.editTag.setTextColor(ThemeStore.textColorPrimary(requireContext()))

        // 为分组选择的 AutoCompleteTextView 设置背景色
        val groupTextInputLayout = findParentTextInputLayout(binding.editGroup)
        groupTextInputLayout?.setBackgroundColor(backgroundColor)
        groupTextInputLayout?.boxBackgroundColor = backgroundColor
        binding.editGroup.setBackgroundColor(backgroundColor)
        binding.editGroup.setTextColor(ThemeStore.textColorPrimary(requireContext()))

        // 加载分组列表
        Coroutine.async {
            try {
                val groups = appDb.bookTagGroupDao.getAll()
                // 为分组下拉框设置适配器
                withContext(Dispatchers.Main) {
                    val groupNames = groups.map { it.name }
                    binding.editGroup.setFilterValues(groupNames)
                    // 设置下拉高度
                    binding.editGroup.dropDownHeight = (180 * requireContext().resources.displayMetrics.density).toInt()

                    // 添加焦点监听器，当获得焦点时显示下拉列表
                    binding.editGroup.setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus && groupNames.isNotEmpty()) {
                            binding.editGroup.showDropDown()
                        }
                    }
                }
            } catch (ex: Exception) {
                AppLog.put("加载分组失败", ex)
            }
        }

        // 获取参数
        val bookUrl = arguments?.getString("bookUrl")
        val oldTagName = arguments?.getString("oldTagName")

        // 如果是编辑模式，设置当前标签名、颜色和分组
        if (!oldTagName.isNullOrEmpty()) {
            binding.editTag.setText(oldTagName)
            // 获取现有标签的颜色和分组
            Coroutine.async {
                try {
                    val existingTag = appDb.bookTagDao.getTagByName(oldTagName)
                    if (existingTag != null) {
                        currentColor = existingTag.color
                        currentGroupId = existingTag.groupId
                        // 设置分组名称
                        withContext(Dispatchers.Main) {
                            if (isAdded && !requireActivity().isFinishing) {
                                try {
                                    if (currentGroupId > 0) {
                                        val group = appDb.bookTagGroupDao.getById(currentGroupId)
                                        if (group != null) {
                                            binding.editGroup.setText(group.name)
                                        }
                                    }
                                    updateColorPreview()
                                } catch (e: Exception) {
                                    AppLog.put("更新标签信息失败", e)
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    AppLog.put("获取标签信息失败", ex)
                }
            }
            // 显示删除按钮
            binding.btnDelete.visibility = View.VISIBLE
        } else {
            // 隐藏删除按钮
            binding.btnDelete.visibility = View.GONE
            // 为新标签生成随机颜色
            currentColor = TagColorUtils.generateRandomColor()
        }

        // 只有在非编辑模式下才初始化颜色预览
        if (oldTagName.isNullOrEmpty()) {
            updateColorPreview()
        }

        // 监听标签名称变化，更新预览
        binding.editTag.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                updateColorPreview()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 颜色选择按钮点击事件
        binding.btnSelectColor.setOnClickListener {
            // 使用主题设置中的颜色选择器
            val colorPickerDialog = io.legado.app.lib.prefs.ColorPreference.ColorPickerDialogCompat.newBuilder()
                .setDialogType(com.jaredrummler.android.colorpicker.ColorPickerDialog.TYPE_PRESETS)
                .setDialogTitle(R.string.select_color)
                .setColorShape(com.jaredrummler.android.colorpicker.ColorShape.CIRCLE)
                .setPresets(com.jaredrummler.android.colorpicker.ColorPickerDialog.MATERIAL_COLORS)
                .setAllowPresets(true)
                .setAllowCustom(true)
                .setShowAlphaSlider(false)
                .setShowColorShades(true)
                .setColor(currentColor)
                .create()

            colorPickerDialog.setColorPickerDialogListener(object : com.jaredrummler.android.colorpicker.ColorPickerDialogListener {
                override fun onColorSelected(dialogId: Int, color: Int) {
                    currentColor = color
                    updateColorPreview()
                }

                override fun onDialogDismissed(dialogId: Int) {
                    // 对话框关闭时的处理，这里不需要特殊处理
                }
            })

            colorPickerDialog.show(childFragmentManager, "tagColorPicker")
        }

        binding.btnConfirm.setOnClickListener {
            // 防止重复提交
            if (isProcessing) {
                return@setOnClickListener
            }

            val name = binding.editTag.text.toString()
            if (name.isBlank()) {
                requireContext().toastOnUi("标签名不能为空")
                return@setOnClickListener
            }

            // 设置处理标志，防止重复点击
            isProcessing = true

            // 处理分组
            Coroutine.async {
                try {
                    val groupName = binding.editGroup.text.toString().trim()
                    var groupId: Long = 0

                    if (groupName.isNotEmpty()) {
                        // 查找分组是否存在
                        var group = appDb.bookTagGroupDao.getByName(groupName)
                        if (group == null) {
                            // 创建新分组
                            val sortOrder = appDb.bookTagGroupDao.getMaxSortOrder() + 1
                            group = io.legado.app.data.entities.BookTagGroup(
                                name = groupName,
                                sortOrder = sortOrder
                            )
                            groupId = appDb.bookTagGroupDao.insert(group)
                        } else {
                            groupId = group.id
                        }
                    }

                    if (oldTagName.isNullOrBlank() || name != oldTagName) {
                        // 检查标签是否已存在
                        val existingTag = appDb.bookTagDao.getTagByName(name)
                        if (existingTag != null) {
                            requireContext().toastOnUi("标签已存在")
                            isProcessing = false // 重置处理标志
                            return@async
                        }

                        // 创建新标签或更新现有标签
                        val bookTag = if (oldTagName.isNullOrBlank()) {
                            // 创建新标签
                            io.legado.app.data.entities.BookTag(
                                name = name,
                                color = currentColor,
                                groupId = groupId
                            )
                        } else {
                            // 更新现有标签
                            val oldTag = appDb.bookTagDao.getTagByName(oldTagName)
                            oldTag?.copy(
                                name = name,
                                color = currentColor,
                                groupId = groupId,
                                updateTime = System.currentTimeMillis()
                            )
                        }

                        if (bookTag != null) {
                            if (oldTagName.isNullOrBlank()) {
                                // 创建新标签，使用IGNORE策略防止重复
                                val id = appDb.bookTagDao.insert(bookTag)
                                if (id == -1L) {
                                    // 插入失败，说明标签已存在
                                    requireContext().toastOnUi("标签已存在")
                                    isProcessing = false // 重置处理标志
                                    return@async
                                }
                            } else {
                                // 更新现有标签
                                appDb.bookTagDao.update(bookTag)
                            }
                        }
                    } else {
                        // 标签名称未更改，更新颜色和分组
                        val existingTag = appDb.bookTagDao.getTagByName(name)
                        if (existingTag != null) {
                            // 更新标签颜色和分组
                            val updatedTag = existingTag.copy(
                                color = currentColor,
                                groupId = groupId,
                                updateTime = System.currentTimeMillis()
                            )
                            appDb.bookTagDao.update(updatedTag)
                        }
                    }

                    // 获取回调并执行
                    val callbackId = arguments?.getString("callbackId")
                    if (!callbackId.isNullOrEmpty()) {
                        callbacks[callbackId]?.invoke(TagInfo(name, currentColor, groupId))
                        callbacks.remove(callbackId)
                    }
                    dismiss()
                } catch (ex: Exception) {
                    AppLog.put("保存标签失败", ex)
                    requireContext().toastOnUi("保存标签失败: ${ex.localizedMessage}")
                    isProcessing = false // 重置处理标志
                }
            }
        }
        binding.btnCancel.setOnClickListener {
            // 清理回调
            val callbackId = arguments?.getString("callbackId")
            if (!callbackId.isNullOrEmpty()) {
                callbacks.remove(callbackId)
            }
            dismiss()
        }

        // 删除按钮点击事件
        binding.btnDelete.setOnClickListener {
            if (!oldTagName.isNullOrEmpty()) {
                showDeleteConfirmDialog(oldTagName)
            }
        }
    }

    private fun updateColorPreview() {
        // 确保在主线程中更新 UI
        if (isAdded && !requireActivity().isFinishing) {
            try {
                binding.tagPreview.setCustomBackgroundColor(currentColor)
                val tagText = binding.editTag.text?.toString()?.ifEmpty { "标签预览" } ?: "标签预览"
                binding.tagPreview.text = tagText
                binding.colorHexValue.text = String.format("#%06X", 0xFFFFFF and currentColor)
            } catch (e: Exception) {
                // 捕获可能的异常，防止崩溃
                AppLog.put("更新标签预览失败", e)
            }
        }
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(tagName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("删除标签")
            .setMessage("确定要删除标签\"$tagName\"吗？\n删除后，所有使用该标签的书籍将移除此标签。")
            .setPositiveButton("删除") { _, _ ->
                deleteTag(tagName)
                dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 删除标签
     */
    private fun deleteTag(tagName: String) {
        Coroutine.async {
            try {
                // 删除标签本身
                appDb.bookTagDao.deleteByName(tagName)

                // 删除所有书籍与该标签的关联关系
                appDb.bookTagRelationDao.deleteRelationsByTagName(tagName)

                // 清理回调
                val callbackId = arguments?.getString("callbackId")
                if (!callbackId.isNullOrEmpty()) {
                    callbacks.remove(callbackId)
                }

                requireContext().toastOnUi("标签已删除")

            } catch (ex: Exception) {
                AppLog.put("删除标签失败", ex)
                requireContext().toastOnUi("删除标签失败: ${ex.localizedMessage}")
            }
        }
    }

    companion object {
        // 使用静态Map存储回调，避免序列化问题
        private val callbacks = mutableMapOf<String, (TagInfo) -> Unit>()
        private var callbackIdCounter = 0

        fun show(
            fragmentManager: FragmentManager,
            bookUrl: String? = null,
            oldTagName: String? = null,
            callback: ((TagInfo) -> Unit)? = null
        ) {
            val dialog = BookTagEditDialog()
            val args = Bundle()

            // 生成唯一的回调ID
            val callbackId = if (callback != null) {
                val id = "callback_${++callbackIdCounter}"
                callbacks[id] = callback
                id
            } else null

            args.putString("bookUrl", bookUrl)
            args.putString("oldTagName", oldTagName)
            args.putString("callbackId", callbackId)
            dialog.arguments = args

            dialog.show(fragmentManager, "bookTagEditDialog")
        }
    }
}