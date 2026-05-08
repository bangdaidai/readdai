package io.legado.app.utils

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R

/**
 * RecyclerView的emptyView扩展属性
 * 当RecyclerView没有数据时显示的视图
 */
var RecyclerView.emptyView: View?
    get() = getTag(R.id.recycler_view_empty_view) as? View
    set(value) {
        val oldEmptyView = emptyView
        if (oldEmptyView === value) return
        
        // 移除旧的观察者
        if (oldEmptyView != null) {
            adapter?.unregisterAdapterDataObserver(emptyObserver)
        }
        
        // 设置新的空视图
        if (value != null) {
            setTag(R.id.recycler_view_empty_view, value)
            adapter?.registerAdapterDataObserver(emptyObserver)
            checkIfEmpty()
        } else {
            setTag(R.id.recycler_view_empty_view, null)
        }
    }

private val RecyclerView.emptyObserver: RecyclerView.AdapterDataObserver
    get() = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            checkIfEmpty()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            checkIfEmpty()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            checkIfEmpty()
        }
    }

private fun RecyclerView.checkIfEmpty() {
    if (emptyView != null) {
        val emptyViewVisible = adapter == null || adapter!!.itemCount == 0
        emptyView?.visibility = if (emptyViewVisible) View.VISIBLE else View.GONE
        visibility = if (emptyViewVisible) View.GONE else View.VISIBLE
    }
}