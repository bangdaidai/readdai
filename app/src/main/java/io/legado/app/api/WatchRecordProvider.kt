package io.legado.app.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
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

class WatchRecordProvider : ContentProvider() {

    private val TAG = "WatchRecordProvider"

    private enum class RequestCode {
        AddWatchRecord, GetWatchRecords, DeleteWatchRecords
    }

    private val postBodyKey = "json"
    private val authority = "io.legado.app.watchRecordProvider"

    private val sMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "watchRecord/add", RequestCode.AddWatchRecord.ordinal)
            addURI(authority, "watchRecords/query", RequestCode.GetWatchRecords.ordinal)
            addURI(authority, "watchRecords/delete", RequestCode.DeleteWatchRecords.ordinal)
        }
    }

    override fun onCreate(): Boolean {
        logToFile("========================================")
        logToFile("!!! PROVIDER ONCREATE CALLED !!!")
        logToFile("Context: $context")
        logToFile("Calling PID: ${Binder.getCallingPid()}")
        logToFile("My PID: ${Process.myPid()}")
        Log.e(TAG, "!!! PROVIDER ONCREATE CALLED !!!")
        return true
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        logToFile("========================================")
        logToFile("delete called")
        logToFile("URI: $uri")
        logToFile("Calling PID: ${Binder.getCallingPid()}")
        Log.d(TAG, "delete called")
        
        val matchResult = sMatcher.match(uri)
        logToFile("URI match result: $matchResult")
        
        if (matchResult < 0) {
            logToFile("ERROR: URI not matched!")
            return -1
        }
        
        when (RequestCode.entries[matchResult]) {
            RequestCode.DeleteWatchRecords -> {
                logToFile("Executing DeleteWatchRecords")
                runBlocking {
                    try {
                        appDb.readSessionDao.clear()
                        logToFile("All records cleared successfully")
                    } catch (e: Exception) {
                        logToFile("Clear failed: ${e.message}")
                        logToFile("Stack trace: ${e.stackTraceToString()}")
                    }
                }
            }
            else -> {
                logToFile("Unknown request code: $matchResult")
            }
        }
        return 0
    }

    override fun getType(uri: Uri): String? {
        logToFile("getType called: $uri")
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        logToFile("========================================")
        logToFile("!!! INSERT CALLED !!!")
        logToFile("URI: $uri")
        logToFile("Calling PID: ${Binder.getCallingPid()}")
        logToFile("My PID: ${Process.myPid()}")
        logToFile("Context is null: ${context == null}")
        Log.e(TAG, "!!! INSERT CALLED !!!")
        Log.d(TAG, "uri: $uri")

        // 检查URI匹配
        val matchResult = sMatcher.match(uri)
        logToFile("URI match result: $matchResult")
        if (matchResult < 0) {
            logToFile("ERROR: URI not matched! Authority might be wrong.")
            Log.e(TAG, "URI not matched!")
            return null
        }
        logToFile("Request code: ${RequestCode.entries[matchResult]}")

        if (values == null) {
            logToFile("ERROR: values is null!")
            Log.e(TAG, "values is null!")
            return null
        }

        logToFile("ContentValues keys: ${values.keySet().joinToString()}")

        val json = values.getAsString(postBodyKey)
        logToFile("JSON content: $json")
        Log.d(TAG, "json: $json")

        if (json.isNullOrEmpty()) {
            logToFile("ERROR: json is null or empty!")
            Log.e(TAG, "json is null or empty!")
            return null
        }

        try {
            logToFile("Parsing JSON with JsonParser...")
            
            // 使用JsonParser直接解析，不使用数据类（避免R8混淆问题）
            val jsonElement = JsonParser.parseString(json)
            val jsonObject = jsonElement.asJsonObject
            
            logToFile("Parsed JsonObject successfully")
            
            // 直接从JsonObject获取字段
            val bookName = jsonObject.get("bookName")?.asString ?: ""
            val author = jsonObject.get("author")?.asString ?: ""
            val bookUrl = jsonObject.get("bookUrl")?.asString ?: ""
            val coverUrl = jsonObject.get("coverUrl")?.asString ?: ""
            val duration = jsonObject.get("duration")?.asLong ?: 0L
            val startTime = jsonObject.get("startTime")?.asLong ?: 0L
            val endTime = jsonObject.get("endTime")?.asLong ?: 0L
            val episodeTitle = jsonObject.get("episodeTitle")?.asString ?: ""
            
            logToFile("Extracted fields:")
            logToFile("  bookName: $bookName")
            logToFile("  author: $author")
            logToFile("  duration: $duration")
            logToFile("  startTime: $startTime")
            logToFile("  endTime: $endTime")
            logToFile("  episodeTitle: $episodeTitle")

            val ctx = context
            if (ctx == null) {
                logToFile("ERROR: context is null!")
                throw Exception("context is null!")
            }
            logToFile("Context is available: $ctx")

            val deviceId = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()
            logToFile("Device ID obtained: $deviceId")

            val readSession = ReadSession(
                id = 0,
                bookName = bookName,
                author = author,
                bookUrl = bookUrl,
                coverUrl = coverUrl,
                duration = duration,
                startTime = startTime,
                endTime = endTime,
                type = BookType.video,
                durChapterTitle = episodeTitle,
                deviceId = deviceId
            )

            logToFile("Created ReadSession object")
            logToFile("  bookName: ${readSession.bookName}")
            logToFile("  type: ${readSession.type} (BookType.video = ${BookType.video})")
            Log.d(TAG, "Inserting to database...")

            // 检查数据库是否可用
            logToFile("Checking appDb...")
            try {
                val db = appDb
                logToFile("appDb instance: $db")
                
                // 使用 runBlocking 调用 suspend 函数
                logToFile("Calling insert in runBlocking...")
                runBlocking {
                    logToFile("Inside runBlocking, calling dao.insert...")
                    val insertedId = db.readSessionDao.insert(readSession)
                    logToFile("Insert returned ID: $insertedId")
                }
                logToFile("!!! INSERT SUCCESSFUL !!!")
                Log.d(TAG, "Insert successful!")
            } catch (e: Exception) {
                logToFile("!!! INSERT FAILED !!!")
                logToFile("Exception type: ${e.javaClass.simpleName}")
                logToFile("Exception message: ${e.message}")
                logToFile("Stack trace:\n${e.stackTraceToString()}")
                Log.e(TAG, "Insert failed: ${e.message}")
                e.printStackTrace()
                // 即使失败也返回URI，让TV知道调用已完成
            }

        } catch (e: Exception) {
            logToFile("!!! FAILED TO PROCESS !!!")
            logToFile("Exception type: ${e.javaClass.simpleName}")
            logToFile("Exception message: ${e.message}")
            logToFile("Stack trace:\n${e.stackTraceToString()}")
            Log.e(TAG, "Failed: ${e.message}")
            e.printStackTrace()
        }

        val resultUri = Uri.withAppendedPath(Uri.parse("content://$authority"), "watchRecord/add")
        logToFile("Returning URI: $resultUri")
        logToFile("========================================")
        return resultUri
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        logToFile("========================================")
        logToFile("query called")
        logToFile("URI: $uri")
        logToFile("Calling PID: ${Binder.getCallingPid()}")
        Log.d(TAG, "query called")
        
        val matchResult = sMatcher.match(uri)
        logToFile("URI match result: $matchResult")
        
        if (matchResult < 0) {
            logToFile("ERROR: URI not matched!")
            return null
        }
        
        return when (RequestCode.entries[matchResult]) {
            RequestCode.GetWatchRecords -> {
                logToFile("Executing GetWatchRecords")
                Log.d(TAG, "Getting all video records...")
                val sessions = runBlocking {
                    try {
                        appDb.readSessionDao.getAllByTypeSync(BookType.video)
                    } catch (e: Exception) {
                        logToFile("Query failed: ${e.message}")
                        logToFile("Stack trace: ${e.stackTraceToString()}")
                        emptyList<ReadSession>()
                    }
                }
                logToFile("Got ${sessions.size} records")
                Log.d(TAG, "Got ${sessions.size} records")
                SimpleCursor(sessions)
            }
            else -> {
                logToFile("Unknown request code in query: $matchResult")
                null
            }
        }
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        logToFile("update called (not implemented)")
        throw UnsupportedOperationException("Not yet implemented")
    }

    private class SimpleCursor(data: Any?) : MatrixCursor(arrayOf("result"), 1) {
        private val mData: String = Gson().toJson(data)
        init {
            addRow(arrayOf(mData))
        }
    }

    private fun logToFile(message: String) {
        try {
            val ctx = context
            if (ctx == null) {
                Log.e(TAG, "Cannot write log: context is null")
                return
            }
            
            val externalDir = ctx.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "Cannot write log: external files dir is null")
                return
            }
            
            val logFile = File(externalDir, "watch_record_provider.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            
            FileWriter(logFile, true).use { writer ->
                writer.appendLine("$timestamp: $message")
            }
            
            // 同时输出到Logcat
            Log.d(TAG, "[LOGFILE] $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
            e.printStackTrace()
        }
    }
}
