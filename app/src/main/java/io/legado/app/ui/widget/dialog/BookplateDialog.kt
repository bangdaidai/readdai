package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.view.Gravity
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
        val screenHeight = displayMetrics.heightPixels
        val dialogWidth = (screenWidth * 0.9f).toInt()
        val dialogHeight = (screenHeight * 0.8f).toInt()

        val dialog = Dialog(context).apply {
            setCanceledOnTouchOutside(false)
            setCancelable(true)
        }

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
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

        val imageHeight = (dialogWidth.toFloat() / bitmap.width * bitmap.height).toInt()

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(
                imageView,
                FrameLayout.LayoutParams(dialogWidth, imageHeight)
            )
        }

        dialog.setContentView(scrollView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0xCC000000.toInt()))
            setLayout(dialogWidth, dialogHeight)
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
