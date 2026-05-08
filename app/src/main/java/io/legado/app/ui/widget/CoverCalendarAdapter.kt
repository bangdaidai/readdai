package io.legado.app.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.CoverCalendarDayData
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.image.CoverImageView

class CoverCalendarAdapter : RecyclerView.Adapter<CoverCalendarAdapter.ViewHolder>() {

    private var dataList = listOf<CoverCalendarDayData>()
    private var cellWidth = 0
    private var cellHeight = 0
    var onDayClickListener: ((CoverCalendarDayData) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cover_calendar_day, parent, false)
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataList[position])
    }

    override fun getItemCount() = dataList.size

    fun updateData(newData: List<CoverCalendarDayData>) {
        dataList = newData
        notifyDataSetChanged()
    }

    fun setCellSize(width: Int, height: Int) {
        if (cellWidth != width || cellHeight != height) {
            cellWidth = width
            cellHeight = height
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverView: CoverImageView = itemView.findViewById(R.id.iv_cover)
        private val tvDay: TextView = itemView.findViewById(R.id.tv_day)

        fun bind(data: CoverCalendarDayData) {
            val lp = itemView.layoutParams
            if (lp != null) {
                lp.width = cellWidth
                lp.height = cellHeight
                itemView.layoutParams = lp
            }

            val isCurrentMonth = data.isCurrentMonth
            itemView.alpha = 1.0f

            if (data.dayOfMonth > 0) {
                tvDay.visibility = View.VISIBLE
                tvDay.text = data.dayOfMonth.toString()
                val textColor = if (isCurrentMonth) {
                    io.legado.app.lib.theme.ThemeStore.textColorPrimary(itemView.context)
                } else {
                    io.legado.app.lib.theme.ThemeStore.textColorSecondary(itemView.context)
                }
                tvDay.setTextColor(textColor)
            } else {
                tvDay.visibility = View.GONE
            }

            if (isCurrentMonth && data.coverUrl.isNotEmpty()) {
                coverView.visibility = View.VISIBLE
                coverView.load(data.coverUrl, data.bookName, null)
                tvDay.visibility = View.GONE
            } else {
                coverView.visibility = View.GONE
                coverView.setImageDrawable(null)
            }

            itemView.setOnClickListener {
                if (isCurrentMonth && data.dayOfMonth > 0) {
                    onDayClickListener?.invoke(data)
                }
            }
        }
    }
}
