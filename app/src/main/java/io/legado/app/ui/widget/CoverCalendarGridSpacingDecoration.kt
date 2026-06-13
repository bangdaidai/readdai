package io.legado.app.ui.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CoverCalendarGridSpacingDecoration(private val spacing: Int, private val rowCountProvider: () -> Int) {

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
        outRect.left = spacing / 2
        outRect.right = spacing / 2
        outRect.top = 0
        val lastRow = rowCountProvider() - 1
        outRect.bottom = if (row < lastRow) spacing else 0
    }
}
