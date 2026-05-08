package io.legado.app.ui.book.read

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.BookReadTimeRank

/**
 * 阅读时长排行TOP10适配器
 */
class ReadTimeTop10Adapter(context: Context) : RecyclerView.Adapter<ReadTimeTop10Adapter.ViewHolder>() {

    private val inflater = LayoutInflater.from(context)
    private var dataList = emptyList<BookReadTimeRank>()
    
    companion object {
        private const val TYPE_EMPTY = 0
        private const val TYPE_DATA = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (dataList.isEmpty()) TYPE_EMPTY else TYPE_DATA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = if (viewType == TYPE_EMPTY) {
            LayoutInflater.from(parent.context).inflate(R.layout.item_empty_read_time_top10, parent, false)
        } else {
            LayoutInflater.from(parent.context).inflate(R.layout.item_read_time_top10, parent, false)
        }
        return if (viewType == TYPE_EMPTY) {
            EmptyViewHolder(view)
        } else {
            DataViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_DATA) {
            val item = dataList[position]
            (holder as DataViewHolder).bind(item, position + 1)
        }
        // 空数据视图不需要绑定
    }

    override fun getItemCount(): Int = if (dataList.isEmpty()) 1 else dataList.size

    fun setData(data: List<BookReadTimeRank>) {
        dataList = data
        notifyDataSetChanged()
    }

    open class ViewHolder(view: View, val viewType: Int) : RecyclerView.ViewHolder(view) {
        open fun bind(item: BookReadTimeRank, rank: Int) {}
    }

    class DataViewHolder(view: View) : ViewHolder(view, TYPE_DATA) {
        private val tvRank: TextView = view.findViewById(R.id.tvRank)
        private val tvBookName: TextView = view.findViewById(R.id.tvBookName)
        private val tvReadTime: TextView = view.findViewById(R.id.tvReadTime)

        override fun bind(item: BookReadTimeRank, rank: Int) {
            tvRank.text = rank.toString()
            tvBookName.text = item.bookName
            tvReadTime.text = formatReadTime(item.readTime)
        }
        
        private fun formatReadTime(mss: Long): String {
            val days = mss / (1000 * 60 * 60 * 24)
            val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
            val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
            val d = if (days > 0) "${days}天" else ""
            val h = if (hours > 0) "${hours}小时" else ""
            val m = if (minutes > 0) "${minutes}分钟" else ""
            var time = "$d$h$m"
            if (time.isBlank()) {
                time = "0分钟"
            }
            return time
        }
    }

    class EmptyViewHolder(view: View) : ViewHolder(view, TYPE_EMPTY)
}