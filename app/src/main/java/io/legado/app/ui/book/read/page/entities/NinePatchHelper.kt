package io.legado.app.ui.book.read.page.entities

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min

/**
 * 真正的 9-slice 绘制：用 4 条线（leftX/rightX 两条竖线，topY/bottomY 两条横线，
 * 均为图片宽/高的归一化比例 0~1）把原图切成 3×3 共 9 块。
 *
 * - 四角：随整体等比缩放（contain），绝不变形；
 * - 可拉伸块（上/下中、左/右中、中心）：全方向拉伸（水平/垂直都拉伸填满目标矩形）；
 *   多余尺寸由中心区域吸收，四角随整体等比缩放、固定不变形。
 *
 * 注意：两条线重合（reeden 的十字中心切片，如 479.5/480.5、126.5/127.5 仅差 1px）仍视为可拉伸，
 *   多余尺寸由中心十字区域吸收，四角固定不变形，不会回退为整图等比铺满。
 *
 * 整体缩放 s = min(rectW/bw, rectH/bh)（contain，等比缩放到刚好放入目标框）：
 *   图片比框大则缩小（角块等比缩小、不溢出），图片比框小则放大，均不变形。
 */
object NinePatchHelper {

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paint: Paint,
        leftX: Float,
        rightX: Float,
        topY: Float,
        bottomY: Float
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

        // 整体等比缩放：先让整图等比缩放到目标框内（contain），再切九宫格。
        // 这样图片比框大时角块随之等比缩小不溢出，图片比框小时等比放大，均不变形。
        val s = min(rectW / bw, rectH / bh)

        // 源各列/行宽度（原图像素）
        val wLsrc = lxN * bw                      // 左角宽
        val wRsrc = (1f - rxN) * bw               // 右角宽
        val hTsrc = tyN * bh                      // 上角高
        val hBsrc = (1f - byN) * bh               // 下角高

        // 全方向拉伸（已移除「仅水平/仅垂直」模式）：角块随整体等比缩放 s 不变形，
        // 中段在水平/垂直方向均拉伸填满目标矩形，多余尺寸由中心区域吸收。
        val wScale = s
        val hScale = s

        val wL = wLsrc * wScale
        val wR = wRsrc * wScale
        val hT = hTsrc * hScale
        val hB = hBsrc * hScale
        val wM = (rectW - wL - wR).let { if (it > 0f) it else 0f }
        val hM = (rectH - hT - hB).let { if (it > 0f) it else 0f }

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
        // 两条线重合时中带 src 宽为 0，借 1px 作为可拉伸中心带，避免空白
        val sxRi = if (rxN > lxN) sxR.toInt().coerceAtLeast(0)
                   else (lxN * bw + 1f).toInt().coerceAtMost(bwI)
        val sxBii = if (byN > tyN) sxB.toInt().coerceAtLeast(0)
                    else (tyN * bh + 1f).toInt().coerceAtMost(bhI)

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
