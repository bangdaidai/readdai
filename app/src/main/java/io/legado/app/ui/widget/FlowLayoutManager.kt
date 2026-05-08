package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

/**
 * 流式布局管理器，用于实现标签的自适应宽度排列
 */
class FlowLayoutManager : RecyclerView.LayoutManager() {
    
    private val mItemRects = mutableListOf<Rect>()
    private var mTotalHeight = 0
    
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        if (recycler == null || state == null) return
        
        // 分离所有子视图
        detachAndScrapAttachedViews(recycler)
        
        mItemRects.clear()
        mTotalHeight = 0
        
        val width = width - paddingLeft - paddingRight
        var currentLineWidth = 0
        var currentLineTop = paddingTop
        var maxLineHeight = 0
        
        for (i in 0 until itemCount) {
            // 获得视图
            val child = recycler.getViewForPosition(i)
            addView(child)
            
            // 测量视图
            measureChildWithMargins(child, 0, 0)
            
            // 获取视图尺寸
            val childWidth = getDecoratedMeasuredWidth(child)
            val childHeight = getDecoratedMeasuredHeight(child)
            
            // 计算边距
            val lp = child.layoutParams as RecyclerView.LayoutParams
            val leftMargin = lp.leftMargin
            val rightMargin = lp.rightMargin
            val topMargin = lp.topMargin
            val bottomMargin = lp.bottomMargin
            
            // 计算实际宽度和高度
            val actualWidth = childWidth + leftMargin + rightMargin
            val actualHeight = childHeight + topMargin + bottomMargin
            
            // 判断是否需要换行
            if (currentLineWidth + actualWidth > width) {
                // 换行
                currentLineTop += maxLineHeight
                currentLineWidth = 0
                maxLineHeight = 0
            }
            
            // 记录位置
            val left = paddingLeft + currentLineWidth + leftMargin
            val top = currentLineTop + topMargin
            val right = left + childWidth
            val bottom = top + childHeight
            
            mItemRects.add(Rect(left, top, right, bottom))
            
            // 更新当前行宽度和最大高度
            currentLineWidth += actualWidth
            maxLineHeight = Math.max(maxLineHeight, actualHeight)
        }
        
        // 计算总高度
        mTotalHeight = currentLineTop + maxLineHeight + paddingBottom
        
        // 调整滚动偏移量，确保不超过新的滚动范围
        val scrollHeight = Math.max(0, mTotalHeight - height)
        mScrollOffset = Math.min(mScrollOffset, scrollHeight)
        
        // 布局所有子视图
        layoutItems(recycler)
        
        // 应用滚动偏移量
        offsetChildrenVertical(-mScrollOffset)
    }
    
    private fun layoutItems(recycler: RecyclerView.Recycler) {
        // 布局所有已添加的子视图
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child != null && i < mItemRects.size) {
                val rect = mItemRects[i]
                layoutDecorated(child, rect.left, rect.top, rect.right, rect.bottom)
            }
        }
    }
    
    override fun canScrollVertically(): Boolean {
        return true
    }
    
    // 当前滚动偏移量
    private var mScrollOffset = 0
    
    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        if (dy == 0 || childCount == 0) return 0
        
        val scrollHeight = Math.max(0, mTotalHeight - height)
        var newScrollY = mScrollOffset + dy
        
        // 限制滚动范围
        newScrollY = Math.max(0, Math.min(newScrollY, scrollHeight))
        
        val consumed = newScrollY - mScrollOffset
        
        if (consumed != 0) {
            offsetChildrenVertical(-consumed)
            mScrollOffset = newScrollY
        }
        
        return consumed
    }
    
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        return mTotalHeight
    }
    
    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        return mScrollOffset
    }
    
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        return height
    }
}