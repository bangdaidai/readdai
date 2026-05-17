package io.legado.app.help.ai

import android.content.Context
import io.legado.app.utils.LogUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI日志管理器
 * 用于记录和查看AI相关功能的日志
 */
object AiLogManager {
    
    private const val LOG_DIR_NAME = "ai_logs"
    private const val LOG_FILE_NAME = "ai_log.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 初始化日志系统
     */
    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, LOG_FILE_NAME)
            
            // 检查日志文件大小，如果超过限制则清空
            if (logFile!!.exists() && logFile!!.length() > MAX_LOG_SIZE) {
                clearLogs()
            }
            
            LogUtils.d("AI日志系统", "初始化完成: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            LogUtils.e("AI日志系统", "初始化失败: ${e.message}")
        }
    }
    
    /**
     * 记录AI日志
     */
    @Synchronized
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (logFile == null) return
            
            val timestamp = dateFormat.format(Date())
            val logEntry = buildString {
                append("[$timestamp] ")
                append("[${level.name}] ")
                append("[$tag] ")
                append(message)
                if (throwable != null) {
                    append("\n")
                    append(throwable.stackTraceToString())
                }
                append("\n")
            }
            
            logFile!!.appendText(logEntry)
            
            // 同时在Logcat输出
            when (level) {
                LogLevel.DEBUG -> LogUtils.d(tag, message)
                LogLevel.INFO -> LogUtils.d(tag, message)
                LogLevel.WARNING -> LogUtils.d(tag, "WARNING: $message")
                LogLevel.ERROR -> LogUtils.e(tag, if (throwable != null) "$message\n${throwable.message}" else message)
            }
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "记录日志失败: ${e.message}")
        }
    }
    
    /**
     * 记录新的一轮对话，自动插入分割线
     */
    @Synchronized
    fun newConversation(title: String = "") {
        try {
            if (logFile == null) return
            
            val timestamp = dateFormat.format(Date())
            val separator = buildString {
                append("\n")
                append("═".repeat(60))
                append("\n")
                append("  🗨️ 新对话轮次")
                if (title.isNotEmpty()) append(" - $title")
                append("\n")
                append("  $timestamp")
                append("\n")
                append("═".repeat(60))
                append("\n\n")
            }
            
            logFile!!.appendText(separator)
            LogUtils.d("AiChat", "=== 新对话轮次: $title ===")
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "插入分割线失败: ${e.message}")
        }
    }
    
    /**
     * 获取所有日志内容
     */
    fun getLogs(): String {
        return try {
            if (logFile?.exists() == true) {
                logFile!!.readText()
            } else {
                "暂无日志"
            }
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }
    
    /**
     * 获取最近的N行日志
     */
    fun getRecentLogs(lineCount: Int = 100): String {
        return try {
            if (logFile?.exists() == true) {
                val allLines = logFile!!.readLines()
                val recentLines = allLines.takeLast(lineCount)
                recentLines.joinToString("\n")
            } else {
                "暂无日志"
            }
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        try {
            logFile?.writeText("")
            LogUtils.d("AiLogManager", "日志已清空")
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "清空日志失败: ${e.message}")
        }
    }
    
    /**
     * 获取日志文件大小
     */
    fun getLogFileSize(): Long {
        return try {
            logFile?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 删除日志文件
     */
    fun deleteLogFile() {
        try {
            logFile?.delete()
            logFile = null
            LogUtils.d("AiLogManager", "日志文件已删除")
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "删除日志文件失败: ${e.message}")
        }
    }
    
    /**
     * 日志级别
     */
    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
