package io.legado.app.ui.book.read.config

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogNineSliceEditorBinding
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 九宫格（9-slice）可视化编辑弹窗：拖拽四条线定义可拉伸区域（全方向拉伸）。
 */
class NineSliceEditorDialog(
    private val bgImagePath: String,
    private val initLeftX: Float,
    private val initRightX: Float,
    private val initTopY: Float,
    private val initBottomY: Float,
    private val onConfirm: (leftX: Float, rightX: Float, topY: Float, bottomY: Float) -> Unit
) : BaseDialogFragment(R.layout.dialog_nine_slice_editor, true) {

    private val binding by viewBinding(DialogNineSliceEditorBinding::bind)

    private var currentLeftX = initLeftX.coerceIn(0.02f, 0.98f)
    private var currentRightX = initRightX.coerceIn(0.02f, 0.98f)
    private var currentTopY = initTopY.coerceIn(0.02f, 0.98f)
    private var currentBottomY = initBottomY.coerceIn(0.02f, 0.98f)

    override fun onFragmentCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val bitmap = TextLine.getBgBitmap(bgImagePath)
        binding.nineSliceView.setData(
            bitmap,
            currentLeftX,
            currentRightX,
            currentTopY,
            currentBottomY
        )
        binding.nineSliceView.onLineChanged = { lx, rx, ty, by ->
            currentLeftX = lx
            currentRightX = rx
            currentTopY = ty
            currentBottomY = by
        }



        binding.btnReset.setOnClickListener {
            currentLeftX = 0.5f
            currentRightX = 0.5f
            currentTopY = 0.5f
            currentBottomY = 0.5f
            binding.nineSliceView.setData(
                bitmap,
                currentLeftX,
                currentRightX,
                currentTopY,
                currentBottomY
            )
        }
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnOk.setOnClickListener {
            onConfirm(currentLeftX, currentRightX, currentTopY, currentBottomY)
            dismissAllowingStateLoss()
        }
    }
}
