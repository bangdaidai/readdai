package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.toastOnUi

class AiChatMenuConfigDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { MenuItemAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        toolBar.setTitle(R.string.ai_chat_menu_config)
        toolBar.inflateMenu(R.menu.text_menu_config)
        toolBar.setNavigationOnClickListener { dismiss() }
        toolBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.menu_reset) {
                AiChatMenuConfig.resetToDefault(requireContext())
                adapter.setCheckedIds(emptySet())
                adapter.notifyDataSetChanged()
                toastOnUi(getString(R.string.reset_default))
                true
            } else {
                false
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val menuItems = AiChatMenuConfig.getAllMenuItems()
        adapter.setItems(menuItems)
        adapter.setCheckedIds(AiChatMenuConfig.getHiddenMenuItemIds(requireContext()))
    }

    override fun dismiss() {
        AiChatMenuConfig.setHiddenMenuItemIds(requireContext(), adapter.getCheckedIds())
        super.dismiss()
    }

}
