package io.legado.app.ui.book.annotation

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.DialogAnnotationBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookAnnotationDialog() : BaseDialogFragment(R.layout.dialog_annotation, true) {

    constructor(annotation: BookAnnotation, editPos: Int = -1) : this() {
        arguments = Bundle().apply {
            putInt("editPos", editPos)
            putParcelable("annotation", annotation)
        }
    }

    private val binding by viewBinding(DialogAnnotationBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        // 标题已在布局中设置为"书摘"，无需再次设置
        val arguments = arguments ?: let {
            dismiss()
            return
        }
        // 为对话框内容区域设置背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        binding.vwBg.setBackgroundColor(backgroundColor)

        @Suppress("DEPRECATION")
        val annotation = arguments.getParcelable<BookAnnotation>("annotation")
        annotation ?: let {
            dismiss()
            return
        }
        val editPos = arguments.getInt("editPos", -1)
        binding.tvFooterLeft.visible(true)
        binding.run {
            tvChapterName.text = annotation.chapterName
            editBookText.setText(annotation.bookText)
            editContent.setText(annotation.content)
            // 设置输入框文字颜色为主题文字颜色
            editBookText.setTextColor(ThemeStore.textColorPrimary(requireContext()))
            editContent.setTextColor(ThemeStore.textColorPrimary(requireContext()))
            tvCancel.setOnClickListener {
                dismiss()
            }
            tvOk.setOnClickListener {
                annotation.bookText = editBookText.text?.toString() ?: ""
                annotation.content = editContent.text?.toString() ?: ""
                lifecycleScope.launch {
                    withContext(IO) {
                        // 检查是否是编辑模式
                        if (editPos >= 0) {
                            // 编辑模式，使用update方法
                            appDb.bookAnnotationDao.update(annotation)
                        } else {
                            // 新增模式，使用insert方法
                            appDb.bookAnnotationDao.insert(annotation)
                        }
                        // 更新阅读记忆中的书摘数量
                        val memories = appDb.readingMemoryDao.getByBook(annotation.bookName, annotation.bookAuthor)
                        memories.forEach { memory ->
                            val annotationCount = appDb.bookAnnotationDao.getByBook(annotation.bookName, annotation.bookAuthor).size
                            val updatedMemory = memory.copy(
                                annotationCount = annotationCount,
                                updateTime = System.currentTimeMillis()
                            )
                            appDb.readingMemoryDao.update(updatedMemory)
                        }
                    }
                    dismiss()
                }
            }
            tvFooterLeft.setOnClickListener {
                lifecycleScope.launch {
                    withContext(IO) {
                        appDb.bookAnnotationDao.delete(annotation)
                        // 更新阅读记忆中的书摘数量
                        val memories = appDb.readingMemoryDao.getByBook(annotation.bookName, annotation.bookAuthor)
                        memories.forEach { memory ->
                            val annotationCount = appDb.bookAnnotationDao.getByBook(annotation.bookName, annotation.bookAuthor).size
                            val updatedMemory = memory.copy(
                                annotationCount = annotationCount,
                                updateTime = System.currentTimeMillis()
                            )
                            appDb.readingMemoryDao.update(updatedMemory)
                        }
                    }
                    dismiss()
                }
            }
        }
    }

    companion object {
        fun newInstance(annotation: BookAnnotation? = null): BookAnnotationDialog {
            return if (annotation != null) {
                BookAnnotationDialog(annotation)
            } else {
                BookAnnotationDialog()
            }
        }
    }
}