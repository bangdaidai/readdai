package io.legado.app.ui.book.read.page.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * 真正的 9-slice 绘制：用 4 条线（leftX/rightX 两条竖线，topY/bottomY 两条横线，
 * 均为图片宽/高的归一化比例 0~1）把原图切成 3×3 共 9 块。
 *
 * - 四角：固定大小（统一缩放，绝不变形）；
 * - 可拉伸块（上/下中、左/右中、中心）：按 nineStretchMode 在「允许且确实画了可拉伸区」的方向拉伸；
 *   不允许该方向、或该方向两条线重合（无可拉伸区）时，整图按「铺满目标」比例绘制，无空白。
 *
 * 退化处理（某方向两条线重合 → 该方向无可拉伸区）：
 *   该方向整图按 fitScale（rect/bmp）铺满目标，角块按比例展开铺满整行/列，既无空白也不变形。
 *
 * nineStretchMode（均为用户在编辑器里自定义选择）：
 *   0 = 全方向拉伸（中段在水平/垂直都拉伸填满目标矩形）
 *   1 = 仅水平拉伸（垂直方向整图铺满目标高，水平方向中段拉伸填满）
 *   2 = 仅垂直拉伸（水平方向整图铺满目标宽，垂直方向中段拉伸填满）
 *
 * scale：用户在规则里设置的背景图整体缩放系数。
 */
object NinePatchHelper {

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        scale: Float,
        paint: Paint,
        leftX: Float,
        rightX: Float,
        topY: Float,
        bottomY: Float,
        stretchMode: Int
    ) {
        val rectW = right - left
        val rectH = bottom - top
        if (rectW <= 0f || rectH <= 0f) return

        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0f || bh <= 0f) return

        // 归一化线位置，各自夹紧范围；并保证 leftX<=rightX、topY<=bottomY（允许重合）
        val lx0 = leftX.coerceIn(0.02f, 0.98f)
        val rx0 = rightX.coerceIn(0.02f, 0.98f)
        val ty0 = topY.coerceIn(0.02f, 0.98f)
        val by0 = bottomY.coerceIn(0.02f, 0.98f)
        val lxN = lx0.coerceAtMost(rx0)
        val rxN = lx0.coerceAtLeast(rx0)
        val tyN = ty0.coerceAtMost(by0)
        val byN = ty0.coerceAtLeast(by0)

        val s = scale.coerceIn(0.1f, 5f)
        val stretchX = stretchMode != 2
        val stretchY = stretchMode != 1

        // 整图铺满目标的比例（退化时用于无可拉伸区的方向）
        val fitScaleX = rectW / bw
        val fitScaleY = rectH / bh

        // 源各列/行宽度（原图像素）
        val wLsrc = lxN * bw                      // 左角宽
        val wRsrc = (1f - rxN) * bw               // 右角宽
        val wMsrc = (rxN - lxN) * bw              // 中间列宽（可拉伸区）
        val hTsrc = tyN * bh                      // 上角高
        val hBsrc = (1f - byN) * bh               // 下角高
        val hMsrc = (byN - tyN) * bh              // 中间行高（可拉伸区）

        // 某方向是否真正可拉伸：模式允许 且 该方向确实画了可拉伸区（src 宽 > 1px）
        val horizStretch = stretchX && wMsrc > 1f
        val vertStretch = stretchY && hMsrc > 1f

        // 角块统一缩放：可拉伸方向用用户 scale s（固定角块不变形），
        // 不可拉伸方向用 fitScale 铺满目标（整图等比适配该方向，无空白）。
        val wScale = if (horizStretch) s else fitScaleX
        val hScale = if (vertStretch) s else fitScaleY

        val wL = wLsrc * wScale
        val wR = wRsrc * wScale
        val hT = hTsrc * hScale
        val hB = hBsrc * hScale
        // 可拉伸方向：中段填满剩余（放下则裁切，保持角块不变形）；不可拉伸方向：中段为 0（已被 fitScale 铺满）
        val wM = if (horizStretch) {
            val v = rectW - wL - wR
            if (v > 0f) v else 0f
        } else 0f
        val hM = if (vertStretch) {
            val v = rectH - hT - hB
            if (v > 0f) v else 0f
        } else 0f

        val x0 = left
        val x1 = left + wL
        val x2 = left + wL + wM
        val x3 = right
        val y0 = top
        val y1 = top + hT
        val y2 = top + hT + hM
        val y3 = bottom

        val sxLi = wLsrc.toInt().coerceAtLeast(0)
        val sxR = rxN * bw
        val sxTi = hTsrc.toInt().coerceAtLeast(0)
        val sxB = byN * bh
        val bwI = bw.toInt()
        val bhI = bh.toInt()
        val sxRi = sxR.toInt().coerceAtLeast(0)
        val sxBii = sxB.toInt().coerceAtLeast(0)

        val srcRects = arrayOf(
            Rect(0, 0, sxLi, sxTi),
            Rect(sxLi, 0, sxRi, sxTi),
            Rect(sxRi, 0, bwI, sxTi),
            Rect(0, sxTi, sxLi, sxBii),
            Rect(sxLi, sxTi, sxRi, sxBii),
            Rect(sxRi, sxTi, bwI, sxBii),
            Rect(0, sxBii, sxLi, bhI),
            Rect(sxLi, sxBii, sxRi, bhI),
            Rect(sxRi, sxBii, bwI, bhI)
        )

        val dstRects = arrayOf(
            RectF(x0, y0, x1, y1),
            RectF(x1, y0, x2, y1),
            RectF(x2, y0, x3, y1),
            RectF(x0, y1, x1, y2),
            RectF(x1, y1, x2, y2),
            RectF(x2, y1, x3, y2),
            RectF(x0, y2, x1, y3),
            RectF(x1, y2, x2, y3),
            RectF(x2, y2, x3, y3)
        )

        // 裁切到目标矩形：避免极小高亮框里角块相互覆盖
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        for (i in 0 until 9) {
            val src = srcRects[i]
            if (src.width() <= 0 || src.height() <= 0) continue // 退化方向的中段块无需绘制
            canvas.drawBitmap(bitmap, src, dstRects[i], paint)
        }
        canvas.restore()
    }
}
