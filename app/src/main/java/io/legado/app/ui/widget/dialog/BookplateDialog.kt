package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Toast
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

object BookplateDialog {

    fun show(context: android.content.Context, bitmap: Bitmap, fileName: String) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightMetrics.heightPixels
        val dialogWidth = (screenWidth * 0.9f).toInt()

        val dialog = Dialog(context).apply {
            setCanceledOnTouchOutside(false)
            setCancelable(true)
        }

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isLongClickable = true
            setOnLongClickListener {
                saveToGallery(bitmap, fileName)
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                true
            }
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(
                imageView,
                FrameLayout.LayoutParams(dialogWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
            )
        }

        dialog.setContentView(scrollView)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0xCC000000.toInt()))
            // 使用 WRAP_CONTENT 让对话框高度自适应内容
            setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }

        dialog.show()
    }

    private fun saveToGallery(bitmap: Bitmap, fileName: String) {
        try {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val legReadDir = File(imagesDir, "LegRead")
            if (!legReadDir.exists()) legReadDir.mkdirs()

            val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(legReadDir, "${safeName}_${System.currentTimeMillis()}.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = android.net.Uri.fromFile(file)
            appCtx.sendBroadcast(intent)
        } catch (e: Exception) {
            Toast.makeText(appCtx, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
