package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import io.legado.app.utils.setLayout
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

object BookplateDialog {

    fun show(context: android.content.Context, bitmap: Bitmap, fileName: String) {
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

        dialog.setContentView(
            imageView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.window?.setBackgroundDrawable(ColorDrawable(0xCC000000.toInt()))
        dialog.setLayout(0.9f, 0f)
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
