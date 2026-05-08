package io.legado.app.ui.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 月度热力图 7×6 网格间距装饰器（6dp 间距）
 * 适配 42 个格子（6 行 × 7 列）
 * 所有格子间距均为 6dp
 */
class MonthlyHeatmapGridSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % 7   // 0~6 周一~周日
        val row = position / 7      // 0~5

        // 水平方向：每个格子左右各一半间距
        outRect.left = spacing / 2
        outRect.right = spacing / 2

        // 垂直方向：只设置顶部间距，不设置底部间距
        outRect.top = spacing
        outRect.bottom = 0
    }
}