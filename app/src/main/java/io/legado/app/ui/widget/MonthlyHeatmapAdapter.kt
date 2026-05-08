package io.legado.app.ui.widget

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.HeatmapDayData
import io.legado.app.utils.HeatmapUtils

class MonthlyHeatmapAdapter : RecyclerView.Adapter<MonthlyHeatmapAdapter.ViewHolder>() {

    private var dataList = listOf<HeatmapDayData>()
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
        dataList = newData
        notifyDataSetChanged()
        
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
            val color = HeatmapUtils.getHeatmapColor(view.context, data.readMinutes)
            val cornerRadius = view.resources.getDimension(io.legado.app.R.dimen.heatmap_corner_radius)
            
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(color)
            drawable.cornerRadius = cornerRadius
            
            view.background = drawable
            view.alpha = if (!data.isCurrentMonth) 0.4f else 1.0f
            
            val lp = view.layoutParams ?: ViewGroup.LayoutParams(cellSize, cellSize)
            lp.width = cellSize
            lp.height = cellSize
            view.layoutParams = lp
        }
    }
}