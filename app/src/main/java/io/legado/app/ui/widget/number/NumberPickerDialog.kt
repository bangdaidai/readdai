package io.legado.app.ui.widget.number

import android.content.Context
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.applyTint
import io.legado.app.utils.hideSoftInput

class NumberPickerDialog(private val context: Context, private val isDecimalMode: Boolean = false) {
    private val builder = AlertDialog.Builder(context)
    private var numberPicker: NumberPicker? = null
    private var maxValue: Int? = null
    private var minValue: Int? = null
    private var value: Int? = null

    init {
        builder.setView(R.layout.dialog_number_picker)
    }

    fun setTitle(title: String): NumberPickerDialog {
        builder.setTitle(title)
        return this
    }

    fun setMaxValue(value: Int): NumberPickerDialog {
        maxValue = value
        return this
    }

    fun setMinValue(value: Int): NumberPickerDialog {
        minValue = value
        return this
    }

    fun setValue(value: Int): NumberPickerDialog {
        this.value = value
        return this
    }

    fun setCustomButton(textId: Int, listener: (() -> Unit)?): NumberPickerDialog {
        builder.setNeutralButton(textId) { _, _ ->
            numberPicker?.let {
                it.clearFocus()
                it.hideSoftInput()
                listener?.invoke()
            }
        }
        return this
    }

    fun show(callBack: ((value: Int) -> Unit)?) {
        builder.setPositiveButton(R.string.ok) { _, _ ->
            numberPicker?.let { np ->
                np.clearFocus()
                np.hideSoftInput()
                callBack?.invoke(np.value)
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.show().applyTint()

        // 使用主题背景色
        val backgroundColor = ThemeStore.backgroundColor(context)

        // 设置对话框的背景色
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))

        // 设置内容区域背景色
        val contentView = dialog.findViewById<android.view.View>(android.R.id.content)
        contentView?.setBackgroundColor(backgroundColor)

        // 设置 NumberPicker 组件及其父级布局
        numberPicker = dialog.findViewById(R.id.number_picker)
        numberPicker?.let { np ->
            // 将 NumberPicker 的背景设置为透明
            np.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 同时设置其父级LinearLayout的背景色为主题背景色
            val parentLayout = np.parent as? android.widget.LinearLayout
            parentLayout?.setBackgroundColor(backgroundColor)

            // 设置文字颜色
            try {
                val selectorWheelPaintField = np.javaClass.getDeclaredField("mSelectorWheelPaint")
                selectorWheelPaintField.isAccessible = true
                val paint = selectorWheelPaintField.get(np) as android.graphics.Paint
                paint.color = ThemeStore.textColorPrimary(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 设置数值范围和当前值
            minValue?.let { np.minValue = it }
            maxValue?.let { np.maxValue = it }
            value?.let { np.value = it }

            // 处理小数模式
            if (isDecimalMode) {
                np.displayedValues = Array(maxValue!! - minValue!! + 1) { i ->
                    ((minValue!! + i) / 10.0).toString()
                }
            }
        }
    }
}