package io.legado.app.ui.widget.dialog

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.DialogBookTagSelectBinding
import io.legado.app.databinding.ItemBookTagManageBinding
import io.legado.app.databinding.ItemBookTagSelectBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookTagSelectDialog : BaseDialogFragment(R.layout.dialog_book_tag_select) {

    data class TagInfo(
        val name: String,
        val color: Int
    )

    private val binding by viewBinding(DialogBookTagSelectBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }

    override fun onStart() {
        super.onStart()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置标题栏背景色为主题色
        binding.toolBar.setBackgroundColor(primaryColor)

        // 获取标题栏文字图标颜色
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        
        // 设置标题文字颜色
        binding.tvTitle.setTextColor(titleBarTextIconColor)
        
        // 设置搜索框颜色
        binding.searchView.applyTint(titleBarTextIconColor)
        binding.searchView.isSubmitButtonEnabled = true
        binding.searchView.queryHint = getString(R.string.search_tag)

        // 设置背景色为主题自定义背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)
        binding.recyclerView.setBackgroundColor(backgroundColor)

        // 使用 FlexboxLayoutManager 以便标签可以灵活排列
        val layoutManager = FlexboxLayoutManager(requireContext()).apply {
            flexWrap = FlexWrap.WRAP
            flexDirection = FlexDirection.ROW
        }
        binding.recyclerView.layoutManager = layoutManager

        // 设置搜索监听器
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                filterTags(newText ?: "")
                return false
            }
        })

        binding.recyclerView.adapter = adapter
        initTagData()
    }

    private fun initTagData() {
        filterTags("")
    }

    private fun filterTags(query: String) {
        lifecycleScope.launch {
            val allTags = appDb.bookTagDao.getAll()
            val excludedTags = appDb.excludedTagDao.getAllSync()
            var filteredTags = allTags.filter {
                excludedTags.none { excluded -> excluded.name == it.name }
            }
            
            // 根据搜索关键词过滤标签
            if (query.isNotEmpty()) {
                filteredTags = filteredTags.filter {
                    it.name.contains(query, ignoreCase = true)
                }
            }
            
            // 在标签列表开头添加"创建新标签+"项
            val newTag = BookTag(name = "创建新标签+", color = primaryColor)
            val items = mutableListOf(newTag) + filteredTags
            adapter.updateItems(items)
        }
    }

    private inner class Adapter(context: Context) :
        RecyclerAdapter<BookTag, ItemBookTagSelectBinding>(context) {

        private val _data = MutableStateFlow(emptyList<BookTag>())

        init {
            lifecycleScope.launch {
                _data.collectLatest { data ->
                    super.setItems(data)
                }
            }
        }

        fun updateItems(items: List<BookTag>) {
            _data.value = items
        }

        override fun getViewBinding(parent: ViewGroup): ItemBookTagSelectBinding {
            return ItemBookTagSelectBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookTagSelectBinding,
            item: BookTag,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvTagName.text = item.name

                // 创建圆角背景并设置颜色
                val drawable = root.context.getDrawable(R.drawable.bg_tag_rounded)
                drawable?.setTint(item.color)
                tvTagName.background = drawable

                // 根据背景颜色亮度设置文字颜色
                tvTagName.setTextColor(
                    if (calculateLuminance(item.color) > 0.5) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                )

                root.setOnClickListener {
                    if (item.name == "创建新标签+") {
                        // 创建新标签
                        BookTagEditDialog.show(
                            fragmentManager = childFragmentManager,
                            bookUrl = this@BookTagSelectDialog.arguments?.getString("bookUrl"),
                            oldTagName = null,
                            callback = {
                                // 创建标签后刷新列表
                                this@BookTagSelectDialog.initTagData()
                            }
                        )
                    } else {
                        // 选择现有标签
                        val callbackId = this@BookTagSelectDialog.arguments?.getString("callbackId")
                        if (!callbackId.isNullOrEmpty()) {
                            callbacks[callbackId]?.invoke(item)
                            callbacks.remove(callbackId)
                        }
                        dismiss()
                    }
                }
            }
        }

        /**
         * 计算颜色亮度，替代ColorUtils.calculateLuminance
         */
        private fun calculateLuminance(color: Int): Float {
            val r = android.graphics.Color.red(color) / 255.0f
            val g = android.graphics.Color.green(color) / 255.0f
            val b = android.graphics.Color.blue(color) / 255.0f
            return (0.2126f * r + 0.7152f * g + 0.0722f * b)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBookTagSelectBinding) {
            // 布局中没有ivEdit元素，所以不需要处理
        }
    }

    companion object {
        // 使用静态Map存储回调，避免序列化问题
        private val callbacks = mutableMapOf<String, (BookTag) -> Unit>()
        private var callbackIdCounter = 0

        fun show(
            fragmentManager: FragmentManager,
            activity: Activity? = null,
            bookUrl: String? = null,
            callback: ((BookTag) -> Unit)? = null
        ) {
            val dialog = BookTagSelectDialog()
            val args = Bundle()

            // 生成唯一的回调ID
            val callbackId = if (callback != null) {
                val id = "callback_${++callbackIdCounter}"
                callbacks[id] = callback
                id
            } else null

            args.putString("bookUrl", bookUrl)
            args.putString("callbackId", callbackId)
            dialog.arguments = args

            dialog.show(fragmentManager, "bookTagSelectDialog")
        }
    }
}