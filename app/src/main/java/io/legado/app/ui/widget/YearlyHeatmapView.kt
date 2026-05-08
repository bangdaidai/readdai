package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.HeatmapDayData
import java.text.SimpleDateFormat
import java.util.*
import io.legado.app.ui.widget.HeatmapGridSpacingDecoration
import io.legado.app.utils.HeatmapCacheManager
import io.legado.app.utils.HeatmapUtils

/**
 * 年度热力图视图（最终稳定版｜1dp 间距｜2.5:1 比例｜无滚动）
 */
class YearlyHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var yearlyHeatmapRecyclerView: RecyclerView
    private val yearlyHeatmapAdapter: YearlyHeatmapAdapter
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var gridSpacing = 0
    private var lastYear = -1
    private var lastCellSize = 0
    private var isInitialized = false
    private var isDataLoaded = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_yearly_heatmap, this, true)

        yearlyHeatmapRecyclerView = findViewById(R.id.yearlyHeatmapRecyclerView)
        yearlyHeatmapAdapter = YearlyHeatmapAdapter()

        initializeDimensions()
        initializeRecyclerView()
        initLegendColors()
    }

    private fun initializeDimensions() {
        gridSpacing = resources.getDimensionPixelSize(R.dimen.heatmap_yearly_grid_spacing)
    }

    private fun initializeRecyclerView() {
        val layoutManager = GridLayoutManager(context, 53)
        yearlyHeatmapRecyclerView.apply {
            setHasFixedSize(true)
            this.layoutManager = layoutManager
            adapter = yearlyHeatmapAdapter
            addItemDecoration(HeatmapGridSpacingDecoration(gridSpacing, 53))
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
        }
    }

    private fun initLegendColors() {
        val colors = HeatmapUtils.getHeatmapColors(context)
        findViewById<View>(R.id.legendBox0).setBackgroundColor(colors[0])
        findViewById<View>(R.id.legendBox1).setBackgroundColor(colors[1])
        findViewById<View>(R.id.legendBox2).setBackgroundColor(colors[2])
        findViewById<View>(R.id.legendBox3).setBackgroundColor(colors[3])
        findViewById<View>(R.id.legendBox4).setBackgroundColor(colors[4])
        findViewById<View>(R.id.legendBox5).setBackgroundColor(colors[5])
    }

    fun setYearData(year: Int, data: List<HeatmapDayData>) {
        // 构建年份键
        val yearKey = year.toString()
        
        // 如果是同一年份且已初始化，则不需要重新生成数据
        if (lastYear == year && isInitialized && isDataLoaded) {
            return
        }
        
        lastYear = year
        isDataLoaded = true
        
        // 缓存数据
        HeatmapCacheManager.cacheYearlyHeatmapData(yearKey, data)
        
        val heatmapData = generateYearlyHeatmapData(year, data)
        yearlyHeatmapAdapter.updateData(heatmapData)
        
        // 确保高度自适应
        post {
            // 先请求RecyclerView重新计算布局
            yearlyHeatmapRecyclerView.apply {
                requestLayout()
                // 确保所有项目都被正确渲染
                adapter?.notifyDataSetChanged()
            }
            
            // 然后请求父容器重新布局
            requestLayout()
            // 强制重新测量和布局
            invalidate()
        }
    }
    
    /**
     * 尝试从缓存中加载数据
     */
    fun loadFromCache(year: Int): Boolean {
        val yearKey = year.toString()
        val cachedData = HeatmapCacheManager.getYearlyHeatmapData(yearKey)
        
        if (cachedData != null && lastYear == year && isInitialized && isDataLoaded) {
            // 如果数据已缓存且是相同的年份，且视图已初始化，则直接返回，不重新加载
            return true
        }
        
        if (cachedData != null) {
            lastYear = year
            isDataLoaded = true
            val heatmapData = generateYearlyHeatmapData(year, cachedData)
            yearlyHeatmapAdapter.updateData(heatmapData)
            
            // 确保高度自适应
            post {
                // 先请求RecyclerView重新计算布局
                yearlyHeatmapRecyclerView.apply {
                    requestLayout()
                    // 确保所有项目都被正确渲染
                    adapter?.notifyDataSetChanged()
                }
                
                // 然后请求父容器重新布局
                requestLayout()
                // 强制重新测量和布局
                invalidate()
            }
            return true
        }
        return false
    }

    private fun generateYearlyHeatmapData(year: Int, rawData: List<HeatmapDayData>): List<HeatmapDayData> {
        val result = mutableListOf<HeatmapDayData>()
        val dataMap = rawData.associateBy { it.date }

        val jan1 = Calendar.getInstance().apply { set(year, Calendar.JANUARY, 1) }
        val jan1Weekday = when (jan1.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 7
            else -> jan1.get(Calendar.DAY_OF_WEEK) - 1
        }
        val offsetDays = 1 - jan1Weekday // 对齐到该年第一个周一

        for (row in 0 until 7) {
            for (col in 0 until 53) {
                val dayIndex = col * 7 + row + offsetDays
                val targetDate = jan1.clone() as Calendar
                targetDate.add(Calendar.DAY_OF_YEAR, dayIndex)

                val dateStr = dateFormat.format(targetDate.time)
                val isInYear = targetDate.get(Calendar.YEAR) == year

                val data = if (isInYear) {
                    dataMap[dateStr] ?: HeatmapDayData(dateStr, 0, row + 1, 0, true)
                } else {
                    HeatmapDayData("", -1, row + 1, 0, false)
                }
                result.add(data)
            }
        }
        return result
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 确保单元格尺寸更新
        post {
            if (yearlyHeatmapRecyclerView.width > 0) {
                // 修复计算方式：考虑到53列，有52个间距
                val totalHorizontalSpacing = 52 * gridSpacing
                val cellWidth = (yearlyHeatmapRecyclerView.width - totalHorizontalSpacing) / 53
                
                // 只有当单元格尺寸发生变化时才更新
                if (cellWidth != lastCellSize) {
                    lastCellSize = cellWidth
                    yearlyHeatmapAdapter.setCellSize(cellWidth)
                    isInitialized = true
                    
                    // 在设置单元格尺寸后，立即请求重新计算RecyclerView的高度
                    // 确保RecyclerView能够正确显示所有行
                    yearlyHeatmapRecyclerView.apply {
                        // 强制重新测量和布局
                        requestLayout()
                        // 通知适配器数据已更改，确保所有项目都被正确渲染
                        adapter?.notifyDataSetChanged()
                    }
                }
                
                // 请求重新测量和布局，确保高度自适应
                requestLayout()
                // 强制重新测量和布局
                invalidate()
            } else {
                // 如果宽度仍然为0，延迟重试
                postDelayed({ 
                    if (yearlyHeatmapRecyclerView.width > 0) {
                        val totalHorizontalSpacing = 52 * gridSpacing
                        val cellWidth = (yearlyHeatmapRecyclerView.width - totalHorizontalSpacing) / 53
                        
                        if (cellWidth != lastCellSize) {
                            lastCellSize = cellWidth
                            yearlyHeatmapAdapter.setCellSize(cellWidth)
                            isInitialized = true
                            
                            // 在设置单元格尺寸后，立即请求重新计算RecyclerView的高度
                            yearlyHeatmapRecyclerView.apply {
                                requestLayout()
                                adapter?.notifyDataSetChanged()
                            }
                        }
                        requestLayout()
                        // 强制重新测量和布局
                        invalidate()
                    }
                }, 50)
            }
        }
    }
    
    /**
     * 强制刷新视图
     */
    fun forceRefresh() {
        post {
            // 先请求RecyclerView重新计算布局
            yearlyHeatmapRecyclerView.apply {
                requestLayout()
                // 确保所有项目都被正确渲染
                adapter?.notifyDataSetChanged()
            }
            
            // 然后请求父容器重新布局
            requestLayout()
            // 强制重新测量和布局
            invalidate()
            
            // 延迟再次刷新，确保布局完成
            postDelayed({
                yearlyHeatmapRecyclerView.apply {
                    requestLayout()
                    adapter?.notifyDataSetChanged()
                }
                requestLayout()
                invalidate()
            }, 100)
        }
    }
}