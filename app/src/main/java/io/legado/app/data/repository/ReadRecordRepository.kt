package io.legado.app.data.repository

import cn.hutool.core.date.DateUtil
import androidx.room.Transaction
import io.legado.app.data.entities.CoverCalendarDayData
import io.legado.app.data.entities.DailyLongestReadCover
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadSession
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.Locale

class ReadRecordRepository(
    private val sessionDao: io.legado.app.data.dao.ReadSessionDao
) {
    /**
     * 从阅读会话计算总阅读时长
     */
    fun getTotalReadTimeFromSessions(type: Int? = null): kotlinx.coroutines.flow.Flow<Long> {
        return kotlinx.coroutines.flow.flow {
            while (true) {
                val sessions = if (type != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val allSessions = sessionDao.getAllSync()
                        allSessions.filter { session ->
                            val sessionType = if (session.type == 0) {
                                // 尝试从 Book 中获取类型
                                val book = io.legado.app.data.appDb.bookDao.getBook(session.bookUrl)
                                book?.type ?: io.legado.app.constant.BookType.text
                            } else {
                                session.type
                            }
                            (sessionType and type) > 0
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        sessionDao.getAllSync()
                    }
                }
                emit(sessions.sumOf { it.duration })
                kotlinx.coroutines.delay(5000) // 每 5 秒更新一次，减少不必要的查询
            }
        }
    }

    /**
     * 获取所有阅读会话
     */
    fun getAllSessions(type: Int? = null): Flow<List<ReadSession>> {
        return kotlinx.coroutines.flow.flow {
            while (true) {
                val sessions = if (type != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val allSessions = sessionDao.getAllSync()
                        allSessions.filter { session ->
                            val sessionType = if (session.type == 0) {
                                // 尝试从 Book 中获取类型
                                val book = io.legado.app.data.appDb.bookDao.getBook(session.bookUrl)
                                book?.type ?: io.legado.app.constant.BookType.text
                            } else {
                                session.type
                            }
                            (sessionType and type) > 0
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        sessionDao.getAllSync()
                    }
                }
                emit(sessions)
                kotlinx.coroutines.delay(5000) // 每 5 秒更新一次，减少不必要的查询
            }
        }
    }

    /**
     * 保存一个完整的阅读会话
     */
    suspend fun saveReadSession(newSession: ReadSession) {
        sessionDao.insert(newSession)
    }

    @Transaction
    suspend fun deleteSession(session: ReadSession) {
        sessionDao.delete(session)
    }

    /**
     * 批量删除合并后的阅读会话
     * 根据合并记录的时间范围，删除该范围内的所有原始记录
     */
    @Transaction
    suspend fun deleteMergedSession(session: ReadSession) {
        sessionDao.deleteSessionsByTimeRange(session.bookName, session.startTime, session.endTime)
    }

    suspend fun deleteReadRecord(record: ReadRecord) {
        sessionDao.deleteByName(record.bookName)
    }

    suspend fun deleteDetail(detail: io.legado.app.data.entities.readRecord.ReadRecordDetail) {
        val sessions = sessionDao.getAll().filter {
            it.bookName == detail.bookName &&
            DateUtil.format(Date(it.endTime), "yyyy-MM-dd") == detail.date
        }
        sessions.forEach { sessionDao.delete(it) }
    }

    suspend fun getMonthlyCoverCalendarData(year: Int, month: Int, type: Int? = null): List<CoverCalendarDayData> {
         val monthStr = String.format(Locale.getDefault(), "%04d-%02d", year, month)
         val rawList = if (type != null) {
             sessionDao.getMonthlyDailyLongestReadCoversByType(monthStr, type)
         } else {
             sessionDao.getMonthlyDailyLongestReadCovers(monthStr)
         }
         return rawList.map { cover ->
             val dayOfMonth = cover.date.split("-").lastOrNull()?.toIntOrNull() ?: 0
             CoverCalendarDayData(cover.date, dayOfMonth, cover.coverUrl ?: "", cover.bookName)
         }
     }

    /**
     * 更新书籍名称
     */
    suspend fun updateBookName(oldBookName: String, newBookName: String) {
        sessionDao.updateBookName(oldBookName, newBookName)
    }

    /**
     * 更新书籍封面
     */
    suspend fun updateCoverUrl(bookName: String, coverUrl: String) {
        sessionDao.updateCoverUrl(bookName, coverUrl)
    }

    /**
     * 更新阅读会话的章节标题
     */
    suspend fun updateChapterTitle(bookName: String, chapterTitle: String, startTime: Long, endTime: Long) {
        sessionDao.updateChapterTitle(bookName, chapterTitle, startTime, endTime)
    }

    /**
     * 更新书籍的显示名称
     */
    suspend fun updateDisplayName(bookName: String, displayName: String) {
        sessionDao.updateDisplayName(bookName, displayName)
    }
}
