package io.legado.app.help.book

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookplateLogger {

    private const val MAX_ENTRIES = 200
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logs = mutableListOf<String>()

    fun log(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val entry = "[$time] [$tag] $message"
        synchronized(logs) {
            logs.add(entry)
            if (logs.size > MAX_ENTRIES) {
                logs.removeAt(0)
            }
        }
        android.util.Log.d("Bookplate", entry)
    }

    fun getAll(): String = synchronized(logs) {
        logs.joinToString("\n")
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
    }

    fun dump(): String {
        val sb = StringBuilder()
        sb.appendLine("========== Bookplate Log ==========")
        sb.appendLine("Total entries: ${logs.size}")
        sb.appendLine("===================================")
        sb.appendLine()
        sb.append(getAll())
        return sb.toString()
    }
}
