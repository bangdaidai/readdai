package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogNineSliceEditorBinding
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 九宫格（9-slice）可视化编辑弹窗：拖拽四条线定义可拉伸区域，并选择拉伸方向。
 */
class NineSliceEditorDialog(
    private val bgImagePath: String,
    private val initLeftX: Float,
    private val initRightX: Float,
    private val initTopY: Float,
    private val initBottomY: Float,
    private val initStretchMode: Int,
    private val onConfirm: (leftX: Float, rightX: Float, topY: Float, bottomY: Float, stretchMode: Int) -> Unit
) : BaseDialogFragment(R.layout.dialog_nine_slice_editor, true) {

    private val binding by viewBinding(DialogNineSliceEditorBinding::bind)

    private var currentLeftX = initLeftX.coerceIn(0.02f, 0.98f)
    private var currentRightX = initRightX.coerceIn(0.02f, 0.98f)
    private var currentTopY = initTopY.coerceIn(0.02f, 0.98f)
    private var currentBottomY = initBottomY.coerceIn(0.02f, 0.98f)
    private var currentStretchMode = initStretchMode.coerceIn(0, 2)

    override fun onFragmentCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val bitmap = TextLine.getBgBitmap(bgImagePath)
        binding.nineSliceView.setData(
            bitmap,
            currentLeftX,
            currentRightX,
            currentTopY,
            currentBottomY,
            currentStretchMode
        )
        binding.nineSliceView.onLineChanged = { lx, rx, ty, by, _ ->
            currentLeftX = lx
            currentRightX = rx
            currentTopY = ty
            currentBottomY = by
        }

        val modes = listOf("全部", "水平", "垂直")
        binding.spStretchMode.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            modes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spStretchMode.setSelection(currentStretchMode)
        binding.spStretchMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    v: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    currentStretchMode = position
                    binding.nineSliceView.stretchMode = position
                    binding.nineSliceView.invalidate()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        binding.btnReset.setOnClickListener {
            currentLeftX = 1f / 3f
            currentRightX = 2f / 3f
            currentTopY = 1f / 3f
            currentBottomY = 2f / 3f
            binding.nineSliceView.setData(
                bitmap,
                currentLeftX,
                currentRightX,
                currentTopY,
                currentBottomY,
                currentStretchMode
            )
        }
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnOk.setOnClickListener {
            onConfirm(currentLeftX, currentRightX, currentTopY, currentBottomY, currentStretchMode)
            dismissAllowingStateLoss()
        }
    }
}
