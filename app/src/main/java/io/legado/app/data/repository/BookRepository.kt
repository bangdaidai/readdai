package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository {

    suspend fun getBookCoverByName(bookName: String): String? {
        return withContext(Dispatchers.IO) {
            appDb.bookDao.findByName(bookName).firstOrNull()?.getDisplayCover()
        }
    }

    suspend fun getChapterTitle(bookName: String, chapterIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val book = appDb.bookDao.findByName(bookName).firstOrNull() ?: return@withContext null
            return@withContext appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.title
        }
    }

    suspend fun getSessionCover(bookName: String): String? {
        return withContext(Dispatchers.IO) {
            appDb.readSessionDao.getLatestByBookName(bookName)?.coverUrl?.ifEmpty { null }
        }
    }

}
