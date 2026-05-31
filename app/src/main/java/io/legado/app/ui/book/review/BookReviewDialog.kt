package io.legado.app.ui.book.review

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.utils.postEvent
import android.content.Context
import androidx.core.content.ContextCompat
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookReview
import io.legado.app.databinding.DialogBookReviewBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookReviewDialog() : BaseDialogFragment(R.layout.dialog_book_review, true) {

    constructor(bookReview: BookReview, editPos: Int = -1, showRating: Boolean = false, initialRating: Float = 0f) : this() {
        arguments = Bundle().apply {
            putInt("editPos", editPos)
            putParcelable("bookReview", bookReview)
            putBoolean("showRating", showRating)
            putFloat("initialRating", initialRating)
        }
    }

    private val binding by viewBinding(DialogBookReviewBinding::bind)
    
    // 添加回调接口，用于通知Activity刷新数据
    interface OnReviewSavedListener {
        fun onReviewSaved()
    }
    
    private var listener: OnReviewSavedListener? = null
    
    fun setOnReviewSavedListener(listener: OnReviewSavedListener) {
        this.listener = listener
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        // 标题已在布局中设置为"书评"，无需再次设置
        val arguments = arguments ?: let {
            dismiss()
            return
        }
        // 为对话框内容区域设置背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        binding.vwBg.setBackgroundColor(backgroundColor)

        @Suppress("DEPRECATION")
        val bookReview = arguments.getParcelable<BookReview>("bookReview")
        bookReview ?: let {
            dismiss()
            return
        }
        val editPos = arguments.getInt("editPos", -1)
        val showRating = arguments.getBoolean("showRating", false)
        val initialRating = arguments.getFloat("initialRating", 0f)
        
        binding.tvDelete.visible(true)
        binding.run {
            tvBookName.text = "${bookReview.bookName} - ${bookReview.bookAuthor}"
            etReviewContent.setText(bookReview.reviewContent)
            // 设置输入框背景为透明，使用卡片背景色
            etReviewContent.setBackgroundResource(android.R.color.transparent)
            // 设置输入框文字颜色为主题文字颜色
            etReviewContent.setTextColor(ThemeStore.textColorPrimary(requireContext()))
            
            // 处理评分组件
            if (showRating) {
                layoutRating.visibility = View.VISIBLE
                ratingBar.rating = initialRating
            } else {
                layoutRating.visibility = View.GONE
            }
            
            tvCancel.setOnClickListener {
                dismiss()
            }
            tvOk.setOnClickListener {
                val updatedReview = bookReview.copy(
                    reviewContent = etReviewContent.text?.toString() ?: "",
                    updateTime = System.currentTimeMillis()
                )
                lifecycleScope.launch {
                    withContext(IO) {
                        // 检查是否是编辑模式
                        if (editPos >= 0) {
                            // 编辑模式，使用update方法
                            appDb.bookReviewDao.update(updatedReview)
                        } else {
                            // 新增模式，使用insert方法
                            appDb.bookReviewDao.insert(updatedReview)
                        }
                        // 如果显示了评分组件，同步更新 Book 表的评分
                        if (showRating) {
                            val book = appDb.bookDao.getBook(updatedReview.bookUrl)
                            if (book != null) {
                                book.rating = ratingBar.rating
                                book.userModifiedRating = true
                                appDb.bookDao.update(book)
                            }
                        }
                    }
                    // 发送书评更新事件，让书架刷新显示
                    postEvent(EventBus.BOOK_REVIEW_UPDATED, updatedReview.bookUrl)
                    listener?.onReviewSaved()
                    dismiss()
                }
            }
            tvDelete.setOnClickListener {
                lifecycleScope.launch {
                    withContext(IO) {
                        appDb.bookReviewDao.delete(bookReview)
                        // 删除书评时无需同步其他表，书评统一存于 BookReview 表
                    }
                    // 发送书评更新事件，让书架刷新显示
                    postEvent(EventBus.BOOK_REVIEW_UPDATED, bookReview.bookUrl)
                    listener?.onReviewSaved()
                    dismiss()
                }
            }
        }
    }

    companion object {
        fun newInstance(bookReview: BookReview? = null): BookReviewDialog {
            return if (bookReview != null) {
                BookReviewDialog(bookReview)
            } else {
                BookReviewDialog()
            }
        }
    }
}