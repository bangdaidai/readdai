package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.HeatmapDayData
import io.legado.app.utils.dpToPx
import io.legado.app.utils.HeatmapCacheManager
import io.legado.app.utils.HeatmapUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 月度热力图视图（日历式｜正方形单元格｜圆角｜2dp 间距｜无滚动）
 */
class MonthlyHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView
    private val adapter: MonthlyHeatmapAdapter
    private val gridSpacing: Int

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // 缓存机制相关变量
    private var lastYear = -1
    private var lastMonth = -1
    private var lastCellSize = 0
    private var isInitialized = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_monthly_heatmap, this, true)
        recyclerView = findViewById(R.id.recyclerView)
        adapter = MonthlyHeatmapAdapter()

        gridSpacing = resources.getDimensionPixelSize(R.dimen.heatmap_grid_spacing)

        initRecyclerView()
        initLegendColors()
    }

    private fun initRecyclerView() {
        val layoutManager = GridLayoutManager(context, 7)
        recyclerView.apply {
            setHasFixedSize(true)
            this.layoutManager = layoutManager
            adapter = this@MonthlyHeatmapView.adapter
            addItemDecoration(MonthlyHeatmapGridSpacingDecoration(gridSpacing))
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

    /**
     * 设置年月并加载数据
     */
    fun setMonthData(year: Int, month: Int, dataList: List<HeatmapDayData>) {
        // 构建月份键
        val monthKey = String.format(Locale.getDefault(), "%04d-%02d", year, month)
        
        // 如果是同一年月且已初始化，则不需要重新生成数据
        if (lastYear == year && lastMonth == month && isInitialized) {
            return
        }
        
        lastYear = year
        lastMonth = month
        
        // 缓存数据
        HeatmapCacheManager.cacheMonthlyHeatmapData(monthKey, dataList)
        
        val calendarData = generateCalendarData(year, month, dataList)
        adapter.updateData(calendarData)
        updateCellSize()
    }
    
    /**
     * 尝试从缓存中加载数据
     */
    fun loadFromCache(year: Int, month: Int): Boolean {
        val monthKey = String.format(Locale.getDefault(), "%04d-%02d", year, month)
        val cachedData = HeatmapCacheManager.getMonthlyHeatmapData(monthKey)
        
        if (cachedData != null && lastYear == year && lastMonth == month && isInitialized) {
            // 如果数据已缓存且是相同的年月，且视图已初始化，则直接返回，不重新加载
            return true
        }
        
        if (cachedData != null) {
            lastYear = year
            lastMonth = month
            val heatmapData = generateCalendarData(year, month, cachedData)
            adapter.updateData(heatmapData)
            updateCellSize()
            return true
        }
        return false
    }

    /**
     * 生成 42 个格子（6×7）的日历数据
     * isCurrentMonth = true 表示属于当前年月
     */
    private fun generateCalendarData(year: Int, month: Int, rawData: List<HeatmapDayData>): List<HeatmapDayData> {
        val result = mutableListOf<HeatmapDayData>()

        // 构建日期 → 数据映射
        val dataMap = rawData.associateBy { it.date }

        // 创建目标月份日历
        val targetMonth = Calendar.getInstance().apply {
            set(year, month - 1, 1)
            firstDayOfWeek = Calendar.MONDAY // 严格以周一为起始
        }

        // 计算该月第一天是周几（1=周一 ~ 7=周日）
        val firstDayOfWeek = targetMonth.get(Calendar.DAY_OF_WEEK)
        val firstDayIndex = when (firstDayOfWeek) {
            Calendar.SUNDAY -> 6 // 周日 → 索引 6（第7列）
            else -> firstDayOfWeek - 2 // 周一(2) → 0，周二(3) → 1, ...
        }

        // 1. 前置空白天（上月）
        for (i in 0 until firstDayIndex) {
            result.add(HeatmapDayData("", 0, 0, 0, false))
        }

        // 2. 本月天数
        val daysInMonth = targetMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day)
            val data = dataMap[dateStr] ?: HeatmapDayData(dateStr, 0, 0, 0, true)
            result.add(data)
        }

        // 3. 后置空白天（下月），补足 42 格
        while (result.size < 42) {
            result.add(HeatmapDayData("", 0, 0, 0, false))
        }

        return result
    }

    private fun updateCellSize() {
        post {
            if (recyclerView.width <= 0) {
                // 如果宽度仍然为 0，延迟重试
                postDelayed({ updateCellSize() }, 50)
                return@post
            }
            // 7 列格子，每个格子左右各 spacing/2，总共 8 个 spacing/2 = 4 * spacing
            val totalSpacing = 8 * gridSpacing
            val cellSize = (recyclerView.width - totalSpacing) / 7
            
            // 只有当单元格尺寸发生变化时才更新
            if (cellSize != lastCellSize) {
                lastCellSize = cellSize
                adapter.setCellSize(cellSize)
                isInitialized = true
                
                // 高度计算：6 行格子，每个格子高度 = cellSize
                // 垂直方向：每个格子只设置顶部间距 spacing = 6dp
                // 第 1 行：top=6dp + 格子
                // 第 2 行：top=6dp + 格子
                // ...
                // 第 6 行：top=6dp + 格子
                // 总高度 = 6 * cellSize + 6 * spacing = 6 * cellSize + 6 * 6dp = 6 * cellSize + 36dp
                val totalVerticalSpacing = 6 * gridSpacing
                val newHeight = 6 * cellSize + totalVerticalSpacing
                val layoutParams = recyclerView.layoutParams
                layoutParams.height = newHeight
                recyclerView.layoutParams = layoutParams
                
                // 强制重新布局
                recyclerView.requestLayout()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCellSize()
    }
}