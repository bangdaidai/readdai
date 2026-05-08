package io.legado.app.ui.book.annotation

import androidx.lifecycle.ViewModel
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.utils.GSON

class BookAnnotationViewModel : ViewModel() {
    
    /**
     * 导出单个书摘为JSON格式
     */
    fun exportAnnotation(annotation: BookAnnotation): String {
        // 创建包含书名、作者、章节名、原文和书摘内容的JSON对象
        val exportData = mapOf(
            "bookName" to annotation.bookName,
            "bookAuthor" to annotation.bookAuthor,
            "chapterName" to annotation.chapterName,
            "bookText" to annotation.bookText,
            "content" to annotation.content,
            "time" to annotation.time
        )
        return GSON.toJson(exportData)
    }
    
    /**
     * 导出单个书摘为Markdown格式
     */
    fun exportAnnotationMd(annotation: BookAnnotation): String {
        val sb = StringBuilder()
        // 添加书名和作者
        sb.appendLine("# ${annotation.bookName}")
        sb.appendLine()
        if (annotation.bookAuthor.isNotEmpty()) {
            sb.appendLine("**作者**: ${annotation.bookAuthor}")
            sb.appendLine()
        }
        // 添加章节名
        if (annotation.chapterName.isNotEmpty()) {
            sb.appendLine("## 章节: ${annotation.chapterName}")
            sb.appendLine()
        }
        // 添加原文引用
        if (annotation.bookText.isNotEmpty()) {
            sb.appendLine("### 原文引用")
            sb.appendLine()
            sb.appendLine("> ${annotation.bookText}")
            sb.appendLine()
        }
        // 添加书摘内容
        if (annotation.content.isNotEmpty()) {
            sb.appendLine("### 书摘内容")
            sb.appendLine()
            sb.appendLine(annotation.content)
            sb.appendLine()
        }
        // 添加时间戳
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("*创建时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(annotation.time))}*")
        return sb.toString()
    }
}