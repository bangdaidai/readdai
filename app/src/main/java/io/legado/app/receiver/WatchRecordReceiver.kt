package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadSession
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 接收TV应用发送的观看记录的广播接收器
 * 作为ContentProvider的备用方案
 */
class WatchRecordReceiver : BroadcastReceiver() {

    private val TAG = "WatchRecordReceiver"

    data class WatchRecordData(
        val bookName: String = "",
        val author: String = "",
        val bookUrl: String = "",
        val coverUrl: String = "",
        val duration: Long = 0L,
        val startTime: Long = 0L,
        val endTime: Long = 0L,
        val episodeTitle: String = ""
    )

    override fun onReceive(context: Context, intent: Intent) {
        logToFile(context, "========================================")
        logToFile(context, "!!! BROADCAST RECEIVED !!!")
        logToFile(context, "Action: ${intent.action}")
        logToFile(context, "From: ${intent.`package`}")
        Log.e(TAG, "!!! BROADCAST RECEIVED !!!")

        if (intent.action != "io.legado.app.action.ADD_WATCH_RECORD") {
            logToFile(context, "ERROR: Unknown action: ${intent.action}")
            return
        }

        val json = intent.getStringExtra("json")
        if (json.isNullOrEmpty()) {
            logToFile(context, "ERROR: json is null or empty!")
            return
        }

        logToFile(context, "JSON content: $json")

        try {
            val record = Gson().fromJson(json, WatchRecordData::class.java)
            logToFile(context, "Parsed record: bookName=${record.bookName}, duration=${record.duration}")

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()

            val readSession = ReadSession(
                id = 0,
                bookName = record.bookName,
                author = record.author,
                bookUrl = record.bookUrl,
                coverUrl = record.coverUrl,
                duration = record.duration,
                startTime = record.startTime,
                endTime = record.endTime,
                type = BookType.video,
                durChapterTitle = record.episodeTitle,
                deviceId = deviceId
            )

            logToFile(context, "Inserting to database...")
            runBlocking {
                try {
                    val insertedId = appDb.readSessionDao.insert(readSession)
                    logToFile(context, "!!! INSERT SUCCESSFUL, ID: $insertedId !!!")
                    Log.e(TAG, "Insert successful, ID: $insertedId")
                } catch (e: Exception) {
                    logToFile(context, "!!! INSERT FAILED: ${e.message} !!!")
                    logToFile(context, "Stack trace: ${e.stackTraceToString()}")
                    Log.e(TAG, "Insert failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logToFile(context, "!!! FAILED TO PROCESS: ${e.message} !!!")
            logToFile(context, "Stack trace: ${e.stackTraceToString()}")
            Log.e(TAG, "Failed: ${e.message}")
        }

        logToFile(context, "========================================")
    }

    private fun logToFile(context: Context, message: String) {
        try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "Cannot write log: external files dir is null")
                return
            }

            val logFile = File(externalDir, "watch_record_receiver.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())

            FileWriter(logFile, true).use { writer ->
                writer.appendLine("$timestamp: $message")
            }

            Log.d(TAG, "[LOGFILE] $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }
}
