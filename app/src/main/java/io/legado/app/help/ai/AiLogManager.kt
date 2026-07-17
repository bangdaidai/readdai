package io.legado.app.help.ai

import android.content.Context
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue

object AiLogManager {

    private const val LOG_DIR_NAME = "ai_logs"
    private const val LOG_FILE_NAME = "ai_log.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024
    private const val FLUSH_INTERVAL_MS = 500L
    private const val FLUSH_BATCH_SIZE = 50

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val logQueue = LinkedBlockingQueue<String>()
    private val logScope = CoroutineScope(Dispatchers.IO + Job())
    private var flushJob: Job? = null

    var minLogLevel: LogLevel = LogLevel.DEBUG

    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, LOG_FILE_NAME)

            if (logFile!!.exists() && logFile!!.length() > MAX_LOG_SIZE) {
                clearLogs()
            }

            startFlushLoop()

            LogUtils.d("AI日志系统", "初始化完成: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            LogUtils.e("AI日志系统", "初始化失败: ${e.message}")
        }
    }

    private fun startFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = logScope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flushQueue()
            }
        }
    }

    private fun flushQueue() {
        if (logQueue.isEmpty() || logFile == null) return
        try {
            val batch = mutableListOf<String>()
            while (batch.size < FLUSH_BATCH_SIZE && logQueue.isNotEmpty()) {
                logQueue.poll()?.let { batch.add(it) }
            }
            if (batch.isNotEmpty()) {
                logFile!!.appendText(batch.joinToString(""))
            }
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "flush日志失败: ${e.message}")
        }
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (level.ordinal < minLogLevel.ordinal) return

        try {
            when (level) {
                LogLevel.DEBUG -> LogUtils.d(tag, message)
                LogLevel.INFO -> LogUtils.d(tag, message)
                LogLevel.WARNING -> LogUtils.d(tag, "WARNING: $message")
                LogLevel.ERROR -> LogUtils.e(tag, if (throwable != null) "$message\n${throwable.message}" else message)
            }

            if (logFile == null) return

            val timestamp = dateFormat.format(java.util.Date())
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

            logQueue.offer(logEntry)

            if (level == LogLevel.ERROR || logQueue.size >= FLUSH_BATCH_SIZE) {
                logScope.launch { flushQueue() }
            }
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "记录日志失败: ${e.message}")
        }
    }

    fun newConversation(title: String = "") {
        try {
            if (logFile == null) return

            val timestamp = dateFormat.format(java.util.Date())
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

            logQueue.offer(separator)
            logScope.launch { flushQueue() }

            LogUtils.d("AiChat", "=== 新对话轮次: $title ===")
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "插入分割线失败: ${e.message}")
        }
    }

    fun getLogs(): String {
        flushQueueSync()
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

    fun getRecentLogs(lineCount: Int = 100): String {
        flushQueueSync()
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

    private fun flushQueueSync() {
        try {
            val batch = mutableListOf<String>()
            while (batch.size < FLUSH_BATCH_SIZE * 2 && logQueue.isNotEmpty()) {
                logQueue.poll()?.let { batch.add(it) }
            }
            if (batch.isNotEmpty() && logFile != null) {
                logFile!!.appendText(batch.joinToString(""))
            }
        } catch (_: Exception) {
        }
    }

    fun clearLogs() {
        logQueue.clear()
        try {
            logFile?.writeText("")
            LogUtils.d("AiLogManager", "日志已清空")
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "清空日志失败: ${e.message}")
        }
    }

    fun getLogFileSize(): Long {
        return try {
            logFile?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun deleteLogFile() {
        logQueue.clear()
        try {
            logFile?.delete()
            logFile = null
            LogUtils.d("AiLogManager", "日志文件已删除")
        } catch (e: Exception) {
            LogUtils.e("AiLogManager", "删除日志文件失败: ${e.message}")
        }
    }

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
