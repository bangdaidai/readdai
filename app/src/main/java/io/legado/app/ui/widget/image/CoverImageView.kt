package io.legado.app.ui.widget.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.constant.AppPattern
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray
import android.view.ViewOutlineProvider
import androidx.collection.LruCache
import androidx.core.graphics.createBitmap
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.lib.theme.backgroundColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("unused")
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    companion object {
        private val nameBitmapCache by lazy { LruCache<String, Bitmap>(33) }
        private const val TAG = "CoverImageView"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        
        /**
         * 写入日志到文件
         */
        fun logToFile(message: String) {
            try {
                val logFile = File(appCtx.filesDir, "cover_image_log.txt")
                val timestamp = dateFormat.format(Date())
                logFile.appendText("[$timestamp] $message\n")
            } catch (e: Exception) {
                Log.e(TAG, "写入日志失败", e)
            }
        }
    }
    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var currentJob: Job? = null
    var bitmapPath: String? = null
        private set
    private var name: String? = null
    private var author: String? = null
    private val drawBookName = BookCover.drawBookName
    private val drawBookAuthor by lazy { BookCover.drawBookAuthor }
    private var viewId = hashCode()  // 用于日志标识

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        if (params != null) {
            val width = params.width
            if (width >= 0) {
                params.height = width * 4 / 3
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        super.setLayoutParams(params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = measuredWidth * 4 / 3
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, 12f)
            }
        }
        clipToOutline = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawBookName) return
        // 如果已经设置了非默认图片（Glide加载成功），不再绘制文字封面
        if (drawable != null && drawable !== BookCover.defaultDrawable) {
            return
        }
        val currentName = this.name ?: return
        // 只要没有网络封面（无论是默认配置还是加载失败），都显示文字封面
        val currentAuthor = this.author
        val pathName = if (drawBookAuthor) {
            currentName + currentAuthor
        } else {
            currentName
        }
        val cacheBitmap = nameBitmapCache[pathName + width]
        if (cacheBitmap != null) {
            canvas.drawBitmap(cacheBitmap, 0f, 0f, null)
            return
        }
        drawNameAuthor(pathName, currentName, currentAuthor)
    }

    private fun drawNameAuthor(pathName: String, name: String, author: String?) {
        generateCoverAsync(pathName, name, author)
    }

    private fun generateCoverAsync(pathName: String, name: String, author: String?) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                if (width == 0) {
                    var attempts = 0
                    do {
                        delay(1L)
                        attempts++
                    } while (width == 0 && attempts < 2000)
                }
                ensureActive()
                val bitmap = generateCoverBitmap(name, author)
                ensureActive()
                nameBitmapCache.put(pathName + width, bitmap)
                postInvalidate()
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateCoverBitmap(name: String?, author: String?): Bitmap {
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        val bitmap = createBitmap(width, height)
        val bitmapCanvas = Canvas(bitmap)
        val backgroundColor = appCtx.backgroundColor
        val accentColor = appCtx.accentColor
        if (BookCover.drawNameAuthorHorizontal) {
            drawHorizontal(bitmapCanvas, name, author, backgroundColor, accentColor)
        } else {
            drawVertical(bitmapCanvas, name, author, backgroundColor, accentColor)
        }
        return bitmap
    }

    private fun drawVertical(bitmapCanvas: Canvas, name: String?, author: String?, backgroundColor: Int, accentColor: Int) {
        var startX = width * 0.2f
        var startY = viewHeight * 0.2f
        val namePaint = TextPaint().apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        name?.toStringArray()?.let { nameArr ->
            var line = 0
            namePaint.textSize = viewWidth / 7
            namePaint.strokeWidth = namePaint.textSize / 6
            nameArr.forEachIndexed { index, char ->
                namePaint.color = backgroundColor
                namePaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(char, startX, startY, namePaint)
                namePaint.color = accentColor
                namePaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(char, startX, startY, namePaint)
                startY += namePaint.textHeight
                if (startY > viewHeight * 0.9) {
                    if ((nameArr.size - index - 1) == 1) {
                        startY -= namePaint.textHeight / 5
                        namePaint.textSize = viewWidth / 9
                        return@forEachIndexed
                    }
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                } else if (startY > viewHeight * 0.8 && (nameArr.size - index - 1) > 2) {
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                }
            }
        }
        if (!drawBookAuthor) {
            return
        }
        val authorPaint = TextPaint(namePaint).apply {
            typeface = Typeface.DEFAULT
        }
        author?.toStringArray()?.let { authorArr ->
            authorPaint.textSize = viewWidth / 10
            authorPaint.strokeWidth = authorPaint.textSize / 5
            startX = width * 0.8f
            startY = viewHeight * 0.95f - authorArr.size * authorPaint.textHeight
            startY = maxOf(startY, viewHeight * 0.3f)
            authorArr.forEach {
                authorPaint.color = backgroundColor
                authorPaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(it, startX, startY, authorPaint)
                authorPaint.color = accentColor
                authorPaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(it, startX, startY, authorPaint)
                startY += authorPaint.textHeight
                if (startY > viewHeight * 0.95) {
                    return@let
                }
            }
        }
    }

    private fun drawHorizontal(bitmapCanvas: Canvas, name: String?, author: String?, backgroundColor: Int, accentColor: Int) {
        val namePaint = TextPaint().apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        var startY = viewHeight * 0.25f
        name?.let { nameStr ->
            namePaint.textSize = viewWidth / 8
            namePaint.strokeWidth = namePaint.textSize / 10
            val maxWidth = viewWidth * 0.8f
            val nameLines = splitTextHorizontally(nameStr, namePaint, maxWidth)
            nameLines.forEach { line ->
                namePaint.color = backgroundColor
                namePaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(line, viewWidth / 2, startY, namePaint)
                namePaint.color = accentColor
                namePaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(line, viewWidth / 2, startY, namePaint)
                startY += namePaint.textHeight * 1.2f
            }
        }
        if (!drawBookAuthor) {
            return
        }
        val authorPaint = TextPaint(namePaint).apply {
            typeface = Typeface.DEFAULT
            textSize = viewWidth / 12
            strokeWidth = textSize / 10
        }
        author?.let { authorStr ->
            startY = viewHeight * 0.85f
            val maxWidth = viewWidth * 0.8f
            val authorLines = splitTextHorizontally(authorStr, authorPaint, maxWidth)
            authorLines.forEach { line ->
                authorPaint.color = backgroundColor
                authorPaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(line, viewWidth / 2, startY, authorPaint)
                authorPaint.color = accentColor
                authorPaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(line, viewWidth / 2, startY, authorPaint)
                startY += authorPaint.textHeight * 1.3f
            }
        }
    }

    private fun splitTextHorizontally(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0f
        text.forEach { char ->
            val charWidth = paint.measureText(char.toString())
            if (currentWidth + charWidth > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
                currentWidth = 0f
            }
            currentLine.append(char)
            currentWidth += charWidth
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    fun setHeight(height: Int) {
        val width = height * 3 / 4
        minimumWidth = width
    }

    private val glideListener by lazy {
        object : RequestListener<Drawable> {

            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                logToFile("[View:$viewId] onLoadFailed: path=$bitmapPath, error=${e?.message}")
                currentJob?.cancel()
                currentJob = null
                // 加载失败时触发重绘，显示默认封面
                invalidate()
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                logToFile("[View:$viewId] onResourceReady: path=$bitmapPath, source=$dataSource")
                currentJob?.cancel()
                currentJob = null
                // 加载成功，不需要额外处理，Glide会自动设置图片
                return false
            }

        }
    }

    fun load(
        searchBook: SearchBook,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null
    ) {
        load(searchBook.coverUrl, searchBook.name, searchBook.author, loadOnlyWifi, searchBook.origin, fragment, lifecycle)
    }

    fun load(
        book: Book,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        onLoadFinish: (() -> Unit)? = null
    ) {
        load(book.getDisplayCover(), book.name, book.author, loadOnlyWifi, book.origin, fragment, lifecycle, onLoadFinish)
    }

    fun load(
        path: String? = null,
        name: String? = null,
        author: String? = null,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        onLoadFinish: (() -> Unit)? = null
    ) {
        val oldPath = bitmapPath
        val oldDrawable = drawable
        
        // 如果路径相同且已加载成功，跳过
        if (path == oldPath && oldDrawable != null && oldDrawable !== BookCover.defaultDrawable) {
            logToFile("[View:$viewId] SKIP: path=$path (same and loaded)")
            return
        }
        
        logToFile("[View:$viewId] LOAD: path=$path, name=$name, oldPath=$oldPath, oldDrawable=$oldDrawable")

        // 取消旧任务
        currentJob?.cancel()
        currentJob = null
        
        // 清空旧图片，避免View复用时显示上一本书的封面
        if (oldDrawable != null && oldDrawable !== BookCover.defaultDrawable) {
            setImageDrawable(null)
            logToFile("[View:$viewId] CLEAR: cleared old drawable")
        }

        val currentAuthor = author?.replace(AppPattern.bdRegex, "")?.trim()?.also {
            this.author = it
        }
        val currentName = name?.replace(AppPattern.bdRegex, "")?.trim()?.also {
            this.name = it
        }
        this.bitmapPath = path

        try {
            if (AppConfig.useDefaultCover) {
                logToFile("[View:$viewId] DEFAULT_COVER: useDefaultCover=true")
                ImageLoader.load(context, BookCover.defaultDrawable)
                    .centerCrop()
                    .into(this)
            } else {
                var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
                if (sourceOrigin != null) {
                    options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
                }
                // 优先使用 View 的 context，确保 Glide 请求和 View 生命周期绑定
                val ctx = context ?: appCtx
                val builder = if (fragment != null && lifecycle != null) {
                    ImageLoader.load(fragment, lifecycle, path)
                } else {
                    ImageLoader.load(ctx, path)
                }
                builder.apply(options)
                    .placeholder(BookCover.defaultDrawable)
                    .error(BookCover.defaultDrawable)
                    .listener(glideListener)
                    .let { req ->
                        if (onLoadFinish != null) {
                            req.addListener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable?>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    onLoadFinish.invoke()
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    onLoadFinish.invoke()
                                    return false
                                }
                            })
                        } else {
                            req
                        }
                    }
                    .centerCrop()
                    .into(this)
            }
        } catch (e: IllegalArgumentException) {
            logToFile("[View:$viewId] ERROR: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            logToFile("[View:$viewId] ERROR: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDetachedFromWindow() {
        logToFile("[View:$viewId] DETACHED: path=$bitmapPath")
        currentJob?.cancel()
        currentJob = null
        super.onDetachedFromWindow()
    }

}
