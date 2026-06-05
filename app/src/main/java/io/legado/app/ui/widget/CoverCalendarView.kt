package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.CoverCalendarDayData
import io.legado.app.utils.dpToPx
import java.util.Calendar
import java.util.Locale

class CoverCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerLayout: LinearLayout
    private val tvMonthYear: TextView
    private val btnPrev: AppCompatImageView
    private val btnNext: AppCompatImageView
    private val weekdaysLayout: LinearLayout
    private val recyclerView: RecyclerView
    private val adapter = CoverCalendarAdapter()

    private var currentYear = 0
    private var currentMonth = 0
    private val gridSpacing = 8.dpToPx()
    private var lastCellSize = 0
    private var isInitialized = false

    var onDateClickListener: ((year: Int, month: Int, dayOfMonth: Int) -> Unit)? = null
    var onMonthClickListener: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_cover_calendar, this, true)

        headerLayout = findViewById(R.id.calendar_header)
        tvMonthYear = findViewById(R.id.tv_month_year)
        btnPrev = findViewById(R.id.btn_prev_month)
        btnNext = findViewById(R.id.btn_next_month)
        weekdaysLayout = findViewById(R.id.weekdays_layout)
        recyclerView = findViewById(R.id.recyclerView)

        initHeader()
        initWeekdays()
        initRecyclerView()
        adapter.onDayClickListener = { data ->
            if (data.isCurrentMonth) {
                onDateClickListener?.invoke(currentYear, currentMonth, data.dayOfMonth)
            }
        }
    }

    private fun initHeader() {
        btnPrev.setOnClickListener { goPrevMonth() }
        btnNext.setOnClickListener { goNextMonth() }
        tvMonthYear.setOnClickListener { onMonthClickListener?.invoke() }
        // 设置箭头颜色与月份文字颜色一致
        val monthTextColor = io.legado.app.lib.theme.ThemeStore.textColorPrimary(context)
        btnPrev.setColorFilter(monthTextColor)
        btnNext.setColorFilter(monthTextColor)
    }

    private fun initWeekdays() {
        val weekdays = arrayOf("一", "二", "三", "四", "五", "六", "日")
        weekdays.forEach { day ->
            val tv = TextView(context).apply {
                text = day
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(io.legado.app.lib.theme.ThemeStore.textColorSecondary(context))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            weekdaysLayout.addView(tv)
        }
    }

    private fun initRecyclerView() {
        val layoutManager = GridLayoutManager(context, 7)
        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@CoverCalendarView.adapter
            addItemDecoration(CoverCalendarGridSpacingDecoration(gridSpacing))
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
        }
    }

    fun setMonthData(year: Int, month: Int, dataList: List<CoverCalendarDayData>) {
        currentYear = year
        currentMonth = month
        tvMonthYear.text = String.format(Locale.getDefault(), "%04d-%02d", year, month)
        val calendarData = generateCalendarData(year, month, dataList)
        adapter.updateData(calendarData)
        updateCellSize()
    }

    fun goToMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        tvMonthYear.text = String.format(Locale.getDefault(), "%04d-%02d", year, month)
    }

    fun getCurrentYear(): Int = currentYear
    fun getCurrentMonth(): Int = currentMonth

    private fun goPrevMonth() {
        val cal = Calendar.getInstance().apply {
            set(currentYear, currentMonth - 1, 1)
            add(Calendar.MONTH, -1)
        }
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1
        tvMonthYear.text = String.format(Locale.getDefault(), "%04d-%02d", currentYear, currentMonth)
        onMonthChanged?.invoke(currentYear, currentMonth)
    }

    private fun goNextMonth() {
        val cal = Calendar.getInstance().apply {
            set(currentYear, currentMonth - 1, 1)
            add(Calendar.MONTH, 1)
        }
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1
        tvMonthYear.text = String.format(Locale.getDefault(), "%04d-%02d", currentYear, currentMonth)
        onMonthChanged?.invoke(currentYear, currentMonth)
    }

    var onMonthChanged: ((year: Int, month: Int) -> Unit)? = null

    private fun generateCalendarData(
        year: Int,
        month: Int,
        rawData: List<CoverCalendarDayData>
    ): List<CoverCalendarDayData> {
        val result = mutableListOf<CoverCalendarDayData>()
        val dataMap = rawData.associateBy { it.date }
        val targetMonth = Calendar.getInstance().apply {
            set(year, month - 1, 1)
            firstDayOfWeek = Calendar.MONDAY
        }
        val firstDayOfWeek = targetMonth.get(Calendar.DAY_OF_WEEK)
        val firstDayIndex = when (firstDayOfWeek) {
            Calendar.SUNDAY -> 6
            else -> firstDayOfWeek - 2
        }
        val daysInMonth = targetMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val prevMonthDays = firstDayIndex
        val totalCells = 42
        val nextMonthDays = totalCells - daysInMonth - prevMonthDays
        for (i in 0 until prevMonthDays) {
            val prevMonth = Calendar.getInstance().apply {
                set(year, month - 1, 1)
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, i + 1)
            }
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                prevMonth.get(Calendar.YEAR),
                prevMonth.get(Calendar.MONTH) + 1,
                i + 1)
            result.add(CoverCalendarDayData(dateStr, 0, "", "", false))
        }
        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day)
            val data = dataMap[dateStr] ?: CoverCalendarDayData(dateStr, day, "", "", true)
            result.add(data)
        }
        for (i in 0 until nextMonthDays) {
            val nextMonth = Calendar.getInstance().apply {
                set(year, month - 1, 1)
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, i + 1)
            }
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                nextMonth.get(Calendar.YEAR),
                nextMonth.get(Calendar.MONTH) + 1,
                i + 1)
            result.add(CoverCalendarDayData(dateStr, 0, "", "", false))
        }
        return result
    }

    private fun updateCellSize() {
        post {
            if (recyclerView.width <= 0) {
                postDelayed({ updateCellSize() }, 50)
                return@post
            }
            // 每个格子左右各有 spacing/2 的外边距，所以总间距是 7 * spacing
            val totalSpacing = 7 * gridSpacing
            val availableWidth = recyclerView.width - totalSpacing
            val cellWidth = availableWidth / 7
            val cellHeight = (cellWidth * 4 / 3).toInt()
            if (cellWidth > 0 && kotlin.math.abs(cellWidth - lastCellSize) > 1) {
                lastCellSize = cellWidth
                adapter.setCellSize(cellWidth, cellHeight)
                isInitialized = true
                val totalVerticalSpacing = 5 * gridSpacing
                val newHeight = 6 * cellHeight + totalVerticalSpacing
                val layoutParams = recyclerView.layoutParams
                layoutParams.height = newHeight
                recyclerView.layoutParams = layoutParams
                recyclerView.requestLayout()
            }
        }
    }

    fun refreshLayout() {
        lastCellSize = 0
        updateCellSize()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && w != oldw) {
            updateCellSize()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            post { updateCellSize() }
        }
    }
}
