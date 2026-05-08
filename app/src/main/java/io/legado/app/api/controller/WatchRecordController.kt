package io.legado.app.api.controller

import android.util.Log
import io.legado.app.api.ReturnData
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.File
import java.util.UUID

object WatchRecordController {

    private val TAG = "WatchRecordController"
    private val logFile = File(appCtx.getExternalFilesDir(null), "watch_record_sync.log")

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logFile.appendText("${System.currentTimeMillis()}: $msg\n")
    }

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

    fun addWatchRecord(postData: String?): ReturnData {
        log("=== addWatchRecord called ===")
        log("postData: $postData")

        val returnData = ReturnData()
        GSON.fromJsonObject<WatchRecordData>(postData).getOrNull()?.let { record ->
            log("Parsed record: bookName=${record.bookName}, duration=${record.duration}")

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
                deviceId = getDeviceId()
            )

            log("readSession created: $readSession")

            runBlocking {
                kotlin.runCatching {
                    log("Inserting to database...")
                    appDb.readSessionDao.insert(readSession)
                    log("Insert successful!")
                    returnData.setData("success")
                }.onFailure {
                    log("Insert failed: ${it.message}")
                    it.printStackTrace()
                    returnData.setErrorMsg(it.message ?: "保存失败")
                }
            }
            return returnData
        }

        log("Failed to parse record data")
        return returnData.setErrorMsg("格式不对")
    }

    fun getWatchRecords(): ReturnData {
        val returnData = ReturnData()
        runBlocking {
            kotlin.runCatching {
                val records = appDb.readSessionDao.getAllByTypeSync(BookType.video)
                returnData.setData(records)
            }.onFailure {
                returnData.setErrorMsg(it.message ?: "查询失败")
            }
        }
        return returnData
    }

    fun deleteAllWatchRecords(): ReturnData {
        val returnData = ReturnData()
        runBlocking {
            kotlin.runCatching {
                appDb.readSessionDao.clear()
                returnData.setData("success")
            }.onFailure {
                returnData.setErrorMsg(it.message ?: "删除失败")
            }
        }
        return returnData
    }

    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            appCtx.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }
}