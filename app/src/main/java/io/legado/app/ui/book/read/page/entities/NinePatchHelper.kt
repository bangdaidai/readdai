package io.legado.app.ui.book.read.page.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * 真正的 9-slice 绘制：用 4 条线（leftX/rightX 两条竖线，topY/bottomY 两条横线，
 * 均为图片宽/高的归一化比例 0~1）把原图切成 3×3 共 9 块。
 * - 四角：固定大小（按用户缩放 s），绝不变形；
 * - 上/下中间、左/右中间：按 stretchMode 在允许方向上拉伸；
 * - 中心：按允许方向拉伸填充。
 *
 * nineStretchMode: 0=全方向拉伸、1=仅水平拉伸、2=仅垂直拉伸。
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

        // 归一化线位置，各自限制在安全范围，并保证 leftX<rightX、topY<bottomY
        val lx0 = leftX.coerceIn(0.02f, 0.98f)
        val rx0 = rightX.coerceIn(0.02f, 0.98f)
        val ty0 = topY.coerceIn(0.02f, 0.98f)
        val by0 = bottomY.coerceIn(0.02f, 0.98f)
        val (lxN, rxN) = if (lx0 > rx0) rx0 to lx0 else lx0 to rx0
        val (tyN, byN) = if (ty0 > by0) by0 to ty0 else ty0 to by0

        // 源（原图像素）边界
        val sxL = lxN * bw
        val sxR = rxN * bw
        val sxT = tyN * bh
        val sxB = byN * bh

        val s = scale.coerceIn(0.1f, 5f)
        val stretchX = stretchMode != 2
        val stretchY = stretchMode != 1

        // 拉伸轴：角块随用户缩放 s 放大/缩小；非拉伸轴：整体等比 fit（不带 s，避免溢出）
        // 角块固定尺寸（按用户缩放 s 放大/缩小），中段为中间列/行的源尺寸。
        // 注意：右角宽 = 图宽 - 右线位置，下角高 = 图高 - 底线位置，
        // 中段宽 = 右线 - 左线，中段高 = 底线 - 顶线，不能写成 bw-sxL-sxR。
        val wLsrc = if (stretchX) sxL * s else sxL
        val wRsrc = (bw - sxR) * if (stretchX) s else 1f
        val wMsrc = (sxR - sxL) * if (stretchX) s else 1f
        val hTsrc = if (stretchY) sxT * s else sxT
        val hBsrc = (bh - sxB) * if (stretchY) s else 1f
        val hMsrc = (sxB - sxT) * if (stretchY) s else 1f

        val (wL, wM, wR) = layoutAxis(wLsrc, wRsrc, wMsrc, rectW, stretchX)
        val (hT, hM, hB) = layoutAxis(hTsrc, hBsrc, hMsrc, rectH, stretchY)

        val x0 = left
        val x1 = left + wL
        val x2 = left + wL + wM
        val x3 = right
        val y0 = top
        val y1 = top + hT
        val y2 = top + hT + hM
        val y3 = bottom

        val sxLi = sxL.toInt().coerceAtLeast(0)
        val sxRi = sxR.toInt().coerceAtLeast(0)
        val sxTi = sxT.toInt().coerceAtLeast(0)
        val sxBii = sxB.toInt().coerceAtLeast(0)
        val bwI = bw.toInt()
        val bhI = bh.toInt()

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

        // 裁切到目标矩形：避免极小高亮时角块溢出相互覆盖
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        for (i in 0 until 9) {
            canvas.drawBitmap(bitmap, srcRects[i], dstRects[i], paint)
        }
        canvas.restore()
    }

    /**
     * 计算某一轴（水平或垂直）三段目标尺寸 [边0, 中段, 边1]。
     * - stretchAllowed=true：中段吸收 (target - 两边和)；若 target 放不下两边则等比缩小两边。
     * - stretchAllowed=false：整体等比 fit 到 target（不拉伸，仅缩放）。
     */
    private fun layoutAxis(
        srcFixed0: Float,
        srcFixed1: Float,
        srcStretch: Float,
        target: Float,
        stretchAllowed: Boolean
    ): FloatArray {
        return if (stretchAllowed) {
            val fixedSum = srcFixed0 + srcFixed1
            if (target >= fixedSum) {
                floatArrayOf(srcFixed0, target - fixedSum, srcFixed1)
            } else if (fixedSum <= 0f) {
                floatArrayOf(0f, target, 0f)
            } else {
                // 目标放不下两侧角块：保持角块固定尺寸，中段收缩为负（重叠），
                // 由外部 clipRect 裁切，避免角块被压缩变形（标准 9-patch 行为）。
                floatArrayOf(srcFixed0, target - fixedSum, srcFixed1)
            }
        } else {
            val total = srcFixed0 + srcStretch + srcFixed1
            if (total <= 0f) {
                floatArrayOf(0f, target, 0f)
            } else {
                val k = target / total
                floatArrayOf(srcFixed0 * k, srcStretch * k, srcFixed1 * k)
            }
        }
    }
}
