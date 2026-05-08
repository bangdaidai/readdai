package io.legado.app.ui.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CoverCalendarGridSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % 7
        val row = position / 7
        // 左右间距各一半，确保格子居中对齐
        // 第一列左边有 spacing/2，最后一列右边有 spacing/2，中间列左右各 spacing/2
        outRect.left = spacing / 2
        outRect.right = spacing / 2
        outRect.top = 0
        outRect.bottom = if (row < 5) spacing else 0
    }
}
