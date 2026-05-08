package io.legado.app.ui.widget

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.HeatmapDayData
import io.legado.app.utils.HeatmapUtils

/**
 * 年度热力图适配器（1dp 间距优化版）
 * ✅ 无嵌套 ✅ 无动态 margin ✅ 高性能
 */
class YearlyHeatmapAdapter : RecyclerView.Adapter<YearlyHeatmapAdapter.ViewHolder>() {

    private var dataList = mutableListOf<HeatmapDayData>()
    private var cellSize = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = View(parent.context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataList[position], cellSize)
    }

    override fun getItemCount() = dataList.size

    fun updateData(newData: List<HeatmapDayData>) {
        dataList.clear()
        dataList.addAll(newData)
        notifyDataSetChanged()
        
        // 确保数据更新后立即刷新视图
        notifyDataSetChanged()
    }

    fun setCellSize(size: Int) {
        if (cellSize != size) {
            cellSize = size
            notifyItemRangeChanged(0, itemCount)
        }
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(data: HeatmapDayData, cellSize: Int) {
            val color = if (data.readMinutes < 0) {
                ContextCompat.getColor(view.context, R.color.heatmap_level_out_of_year)
            } else {
                HeatmapUtils.getHeatmapColor(view.context, data.readMinutes)
            }
            
            val cornerRadius = view.resources.getDimension(io.legado.app.R.dimen.heatmap_yearly_corner_radius)
            
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(color)
            drawable.cornerRadius = cornerRadius
            
            view.background = drawable
            view.alpha = if (data.readMinutes < 0) 0.6f else 1.0f
            
            val lp = view.layoutParams ?: ViewGroup.LayoutParams(cellSize, (cellSize * 2.5).toInt())
            lp.width = cellSize
            lp.height = (cellSize * 2.5).toInt()
            view.layoutParams = lp
            view.requestLayout()
        }
    }
}