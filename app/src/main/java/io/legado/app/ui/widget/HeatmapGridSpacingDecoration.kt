package io.legado.app.ui.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 年度热力图专用网格间距装饰器（1dp 间距版）
 *
 * ✅ 53列 × 7行，所有格子间距均为1dp
 * ✅ 边界对称，性能最优
 */
class HeatmapGridSpacingDecoration(
    private val spacing: Int,
    private val spanCount: Int
) : RecyclerView.ItemDecoration() {

    init {
        require(spacing >= 0) { "spacing must be >= 0" }
        require(spanCount > 0) { "spanCount must be > 0" }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            outRect.setEmpty()
            return
        }

        val column = position % spanCount
        val row = position / spanCount

        // 水平方向：除了最后一列，每个格子右边都有1dp间距
        outRect.left = 0
        outRect.right = if (column < spanCount - 1) spacing else 0

        // 垂直方向：除了最后一行，每个格子底部都有1dp间距
        val rowCount = 7
        outRect.top = 0
        outRect.bottom = if (row < rowCount - 1) spacing else 0
    }
}