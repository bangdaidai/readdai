package io.legado.app.ui.book.annotation

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.DialogAnnotationBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.dialog.BookplateDialog
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookAnnotationDialog() : BaseDialogFragment(R.layout.dialog_annotation, true),
    Toolbar.OnMenuItemClickListener {

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
        binding.toolBar.setOnMenuItemClickListener(this)
        val arguments = arguments ?: let {
            dismiss()
            return
        }
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
                        if (editPos >= 0) {
                            appDb.bookAnnotationDao.update(annotation)
                        } else {
                            appDb.bookAnnotationDao.insert(annotation)
                        }
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

    override fun onMenuItemClick(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_bookplate -> {
                generateBookplate()
                return true
            }
        }
        return false
    }

    private fun generateBookplate() {
        @Suppress("DEPRECATION")
        val annotation = arguments?.getParcelable<BookAnnotation>("annotation") ?: return
        val currentText = binding.editBookText.text?.toString() ?: annotation.bookText

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val timeStr = dateFormat.format(Date(annotation.time))

        lifecycleScope.launch {
            val coverUrl = withContext(IO) {
                appDb.bookDao.findByName(annotation.bookName).firstOrNull()?.getDisplayCover() ?: ""
            }

            val variables = mapOf(
                "bookName" to annotation.bookName,
                "author" to annotation.bookAuthor,
                "chapterName" to annotation.chapterName,
                "bookText" to currentText,
                "content" to annotation.content,
                "time" to timeStr,
                "coverUrl" to coverUrl,
                "latestAnnotation" to currentText,
                "latestAnnotationNote" to annotation.content,
                "latestAnnotationChapter" to annotation.chapterName,
            )

        lifecycleScope.launch {
            val bitmap = withContext(IO) {
                BookplateGenerator.generateAnnotation(requireContext(), variables)
            }
            if (bitmap != null) {
                withContext(Main) {
                    val safeFileName = annotation.bookName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    BookplateDialog.show(requireContext(), bitmap, "书摘_$safeFileName")
                }
            } else {
                withContext(Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "生成失败: ${io.legado.app.help.book.BookplateHtmlRenderer.lastError ?: "未知错误"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
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
