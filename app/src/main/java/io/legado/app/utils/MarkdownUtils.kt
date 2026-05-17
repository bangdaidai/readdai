package io.legado.app.utils

import android.content.Context
import android.text.method.ArrowKeyMovementMethod
import android.text.method.MovementMethod
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

/**
 * Markdown 渲染工具类
 * 使用 Markwon 库渲染 AI 回复的 Markdown 内容
 */
object MarkdownUtils {
    
    private var markwon: Markwon? = null
    
    /**
     * 获取 Markwon 实例（单例）
     */
    fun getMarkwon(context: Context): Markwon {
        if (markwon == null) {
            markwon = Markwon.builder(context)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(context))
                .usePlugin(TablePlugin.create(context))
                .usePlugin(SoftBreakAddsNewLinePlugin.create())
                .build()
        }
        return markwon!!
    }
    
    /**
     * 在 TextView 中渲染 Markdown
     * @param selectable 是否启用文本选择（默认启用）
     */
    fun setMarkdown(textView: TextView, markdown: String, selectable: Boolean = true) {
        val context = textView.context
        getMarkwon(context).setMarkdown(textView, markdown)
        
        // ✅ 关键修复：Markwon 内部会设置 LinkMovementMethod，会破坏文本选择
        // 需要在 setMarkdown 之后重新设置正确的 MovementMethod
        if (selectable) {
            textView.setTextIsSelectable(true)
        }
    }
}
