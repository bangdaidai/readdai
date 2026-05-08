package io.legado.app.service

import android.content.Context
import android.content.Intent
import io.legado.app.base.BaseService
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.help.book.ReadingMemoryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 数据同步服务，更新阅读记忆
 */
class DataSyncService : BaseService() {

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, DataSyncService::class.java)
            context.startService(intent)
        }
        
        /**
         * 同步阅读会话，更新阅读记忆
         */
        suspend fun syncReadRecord(readSession: ReadSession, book: Book) {
            withContext(Dispatchers.IO) {
                // 直接更新阅读记忆
                ReadingMemoryHelper.createReadingMemory(book)
            }
        }
        
        /**
         * 初始化同步，更新所有阅读记忆
         */
        suspend fun initSync() {
            withContext(Dispatchers.IO) {
                // 更新所有书籍的阅读记忆
                val allBooks = appDb.bookDao.all
                ReadingMemoryHelper.createReadingMemories(allBooks)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * 同步阅读会话
     */
    suspend fun syncReadRecord(readSession: ReadSession) {
        // 这个方法已经被静态方法取代，保留只是为了兼容性
    }



    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return android.app.Service.START_STICKY
    }
}