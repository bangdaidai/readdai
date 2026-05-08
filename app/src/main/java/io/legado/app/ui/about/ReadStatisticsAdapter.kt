package io.legado.app.ui.about

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadStatistics
import io.legado.app.databinding.ItemReadStatisticsBinding
import io.legado.app.lib.theme.ThemeStore
import kotlinx.coroutines.runBlocking



/**
 * 阅读统计适配器
 */
class ReadStatisticsAdapter(context: Context) : RecyclerAdapter<ReadStatistics, ItemReadStatisticsBinding>(context) {

    // 当前阅读类型，null 表示全部
    var currentReadType: Int? = null
    
    // 当前统计类型，0:总计，1:每日，2:每月，3:每年
    var currentType: Int = 0

    override fun getViewBinding(parent: ViewGroup): ItemReadStatisticsBinding {
        return ItemReadStatisticsBinding.inflate(LayoutInflater.from(context), parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemReadStatisticsBinding,
        item: ReadStatistics,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            // 隐藏空数据视图
            llEmpty.visibility = View.GONE

            // 获取主题自定义的其他文字颜色
            val otherColor = ThemeStore.textColorOther(context)
            
            // 判断是否是"全部"类型（仅在总计统计且没有筛选类型时显示书影音三列布局）
            val isAllType = currentType == 0 && currentReadType == null && item.date.isEmpty()
            
            if (isAllType) {
                // 显示书影音三列布局
                llBookMediaStats.visibility = View.VISIBLE
                
                // 隐藏整个传统统计数据布局容器
                llDataStats.visibility = View.GONE
                
                // 加载书影音数据
                Thread {
                    val textCount = runBlocking { appDb.readSessionDao.getBookCountByType(BookType.text) }
                    val videoCount = runBlocking { appDb.readSessionDao.getBookCountByType(BookType.video) }
                    val audioCount = runBlocking { appDb.readSessionDao.getBookCountByType(BookType.audio) }
                    
                    binding.root.post {
                        tvBookMediaBookCount.text = "${textCount}"
                        tvBookMediaBookCount.setTextColor(otherColor)
                        tvBookMediaVideoCount.text = "${videoCount}"
                        tvBookMediaVideoCount.setTextColor(otherColor)
                        tvBookMediaAudioCount.text = "${audioCount}"
                        tvBookMediaAudioCount.setTextColor(otherColor)
                    }
                }.start()
            } else {
                // 显示传统的统计数据
                llBookMediaStats.visibility = View.GONE
                llDataStats.visibility = View.VISIBLE
                
                // 设置简短标签
                tvBookCount.text = "读过"
                tvFinishedBookCount.text = "读完"
                tvReadDays.text = "阅读"
                tvReviewCount.text = "笔记"
                tvAbandonedBookCount.text = "弃文"
                tvTotalWords.text = "读了"
                
                // 设置数据值，只显示数字，单位由布局文件单独显示
                tvBookCountValue.text = "${item.bookCount}"
                tvBookCountValue.setTextColor(otherColor)
                tvFinishedBookCountValue.text = "${item.finishedBookCount}"
                tvFinishedBookCountValue.setTextColor(otherColor)
                tvReviewCountValue.text = "${item.reviewCount}"
                tvReviewCountValue.setTextColor(otherColor)
                tvReadDaysValue.text = "${item.readDays}"
                tvReadDaysValue.setTextColor(otherColor)
                tvAbandonedBookCountValue.text = "${item.abandonedBookCount}"
                tvAbandonedBookCountValue.setTextColor(otherColor)
                tvTotalWordsValue.text = "${(item.totalWords / 10000).toInt()}"
                tvTotalWordsValue.setTextColor(otherColor)
            }
            
            // 处理阅读时间，分开显示天、小时、分钟
            val (days, hours, minutes) = calculateTimeComponents(item.totalTime)
            
            // 设置天数
            if (days > 0) {
                tvReadDaysValueLarge.visibility = View.VISIBLE
                tvReadDaysUnit.visibility = View.VISIBLE
                tvReadDaysValueLarge.text = "${days}"
                tvReadDaysValueLarge.setTextColor(otherColor)
            } else {
                tvReadDaysValueLarge.visibility = View.GONE
                tvReadDaysUnit.visibility = View.GONE
            }
            
            // 设置小时
            if (days > 0 || hours > 0) {
                tvReadHoursValue.visibility = View.VISIBLE
                tvReadHoursUnit.visibility = View.VISIBLE
                tvReadHoursValue.text = "${hours}"
                tvReadHoursValue.setTextColor(otherColor)
            } else {
                tvReadHoursValue.visibility = View.GONE
                tvReadHoursUnit.visibility = View.GONE
            }
            
            // 设置分钟
            tvReadMinutesValue.visibility = View.VISIBLE
            tvReadMinutesUnit.visibility = View.VISIBLE
            tvReadMinutesValue.text = "${minutes}"
            tvReadMinutesValue.setTextColor(otherColor)
            
            // 原来的时间显示组件已移除
            
            // 处理始于日期标签，仅在总计统计时显示
            if (item.date.isEmpty()) {
                // 获取第一条阅读记录的时间
                Thread {
                    val firstRecord = kotlinx.coroutines.runBlocking {
                        appDb.readSessionDao.getAllSync().minByOrNull { it.startTime }
                    }
                    binding.root.post {
                        if (firstRecord != null && firstRecord.startTime > 0) {
                            val sdf = java.text.SimpleDateFormat("yyyy 年 MM 月 dd 日", java.util.Locale.getDefault())
                            val startDate = sdf.format(java.util.Date(firstRecord.startTime))
                            tvStartDate.text = "始于$startDate"
                            tvStartDate.visibility = View.VISIBLE
                            tvStartDate.setTextColor(otherColor)
                        } else {
                            tvStartDate.visibility = View.GONE
                        }
                    }
                }.start()
            } else {
                tvStartDate.visibility = View.GONE
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemReadStatisticsBinding) {
        // 如果需要点击事件，可以在这里添加
    }
    
    /**
     * 计算时间的天、小时、分钟
     */
    private fun calculateTimeComponents(mss: Long): Triple<Long, Long, Long> {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        return Triple(days, hours, minutes)
    }
}