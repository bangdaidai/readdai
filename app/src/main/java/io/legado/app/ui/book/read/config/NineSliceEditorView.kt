package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import io.legado.app.utils.dpToPx
import kotlin.math.abs

/**
 * 九宫格（9-slice）可视化编辑器：在原图上叠加 4 条可拖拽的线
 * （两根竖线 nineLeftX/nineRightX，两根横线 nineTopY/nineBottomY，均为 0~1 归一化），
 * 中间矩形即为「可拉伸区域」。拖动实时回调 onLineChanged。
 *
 * 默认四线重合于中点（0.5），且允许两根线重合（即某方向无可拉伸区）。
 */
class NineSliceEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private var leftX = 0.5f
    private var rightX = 0.5f
    private var topY = 0.5f
    private var bottomY = 0.5f

    var onLineChanged: ((Float, Float, Float, Float) -> Unit)? = null

    private var contentLeft = 0f
    private var contentTop = 0f
    private var contentW = 0f
    private var contentH = 0f

    // 0=leftX 1=rightX 2=topY 3=bottomY
    private var dragTarget = -1
    private val hitPx = 18f.dpToPx().toFloat()

    private val bitmapPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    private val linePaint = Paint().apply {
        color = 0xFF00C853.toInt()
        strokeWidth = 2f.dpToPx().toFloat()
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = 0xFF00C853.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f.dpToPx().toFloat()
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = 0xFF00C853.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setData(
        bitmap: Bitmap?,
        leftX: Float,
        rightX: Float,
        topY: Float,
        bottomY: Float
    ) {
        this.bitmap = bitmap
        this.leftX = leftX.coerceIn(0.02f, 0.98f)
        this.rightX = rightX.coerceIn(0.02f, 0.98f)
        this.topY = topY.coerceIn(0.02f, 0.98f)
        this.bottomY = bottomY.coerceIn(0.02f, 0.98f)
        computeContentRect()
        invalidate()
    }

    private fun computeContentRect() {
        val bm = bitmap ?: return
        val pad = 8f.dpToPx().toFloat()
        val availW = width - pad * 2
        val availH = height - pad * 2
        if (availW <= 0 || availH <= 0) return
        val scale = minOf(availW / bm.width, availH / bm.height)
        contentW = bm.width * scale
        contentH = bm.height * scale
        contentLeft = (width - contentW) / 2f
        contentTop = (height - contentH) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeContentRect()
    }

    private fun nxToPx(nx: Float) = contentLeft + nx * contentW
    private fun nyToPx(ny: Float) = contentTop + ny * contentH
    private fun pxToNx(px: Float) = ((px - contentLeft) / contentW).coerceIn(0.02f, 0.98f)
    private fun pxToNy(py: Float) = ((py - contentTop) / contentH).coerceIn(0.02f, 0.98f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bm = bitmap ?: return
        if (contentW <= 0 || contentH <= 0) computeContentRect()
        // 原图
        canvas.drawBitmap(bm, null, android.graphics.RectF(contentLeft, contentTop, contentLeft + contentW, contentTop + contentH), bitmapPaint)

        // 取两根线中较小/较大者，保证绘制顺序无关（允许重合、允许交叉）
        val lx = nxToPx(leftX.coerceAtMost(rightX))
        val rx = nxToPx(leftX.coerceAtLeast(rightX))
        val ty = nyToPx(topY.coerceAtMost(bottomY))
        val by = nyToPx(topY.coerceAtLeast(bottomY))

        // 可拉伸区（中间矩形）描绿边，提示拉伸范围
        canvas.drawRect(lx, ty, rx, by, borderPaint)

        // 4 条线
        canvas.drawLine(lx, contentTop, lx, contentTop + contentH, linePaint)
        canvas.drawLine(rx, contentTop, rx, contentTop + contentH, linePaint)
        canvas.drawLine(contentLeft, ty, contentLeft + contentW, ty, linePaint)
        canvas.drawLine(contentLeft, by, contentLeft + contentW, by, linePaint)

        // 拖拽手柄
        drawHandle(canvas, lx, contentTop + contentH / 2f)
        drawHandle(canvas, rx, contentTop + contentH / 2f)
        drawHandle(canvas, contentLeft + contentW / 2f, ty)
        drawHandle(canvas, contentLeft + contentW / 2f, by)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        val r = 7f.dpToPx().toFloat()
        canvas.drawCircle(cx, cy, r, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragTarget = pickTarget(event.x, event.y)
                return dragTarget != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragTarget == -1) return true
                when (dragTarget) {
                    0 -> leftX = pxToNx(event.x)
                    1 -> rightX = pxToNx(event.x)
                    2 -> topY = pxToNy(event.y)
                    3 -> bottomY = pxToNy(event.y)
                }
                onLineChanged?.invoke(leftX, rightX, topY, bottomY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragTarget = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pickTarget(x: Float, y: Float): Int {
        val lx = nxToPx(leftX.coerceAtMost(rightX))
        val rx = nxToPx(leftX.coerceAtLeast(rightX))
        val ty = nyToPx(topY.coerceAtMost(bottomY))
        val by = nyToPx(topY.coerceAtLeast(bottomY))
        // 横向线（竖线）看 x 距离；纵向线（横线）看 y 距离
        val dxL = abs(x - lx)
        val dxR = abs(x - rx)
        val dyT = abs(y - ty)
        val dyB = abs(y - by)
        val bestV = if (dxL <= dxR) Pair(0, dxL) else Pair(1, dxR)
        val bestH = if (dyT <= dyB) Pair(2, dyT) else Pair(3, dyB)
        // 仅当触摸点落在内容区域内才响应
        val inside = x in contentLeft..(contentLeft + contentW) && y in contentTop..(contentTop + contentH)
        if (!inside) return -1
        return if (bestV.second <= bestH.second) {
            if (bestV.second <= hitPx) bestV.first else -1
        } else {
            if (bestH.second <= hitPx) bestH.first else -1
        }
    }

    fun getLines() = arrayOf(leftX, rightX, topY, bottomY)
}
