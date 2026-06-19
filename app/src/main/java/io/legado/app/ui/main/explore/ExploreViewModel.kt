package io.legado.app.ui.main.explore

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.source.SourceHelp

class ExploreViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(bookSource: BookSourcePart) {
        execute {
            val minXh = appDb.bookSourceDao.minOrder
            bookSource.customOrder = minXh - 1
            appDb.bookSourceDao.upOrder(bookSource)
        }
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            SourceHelp.deleteBookSource(source.bookSourceUrl)
        }
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return SourceConfig.getBookshelfList().any { it == key || it == bookUrl }
    }

    fun addToBookshelf(book: SearchBook) {
        execute {
            kotlin.runCatching {
                val bookUrl = book.bookUrl
                val existedBook = appDb.bookDao.getBook(bookUrl)
                if (existedBook != null) {
                    existedBook.lastAccessTime = System.currentTimeMillis()
                    appDb.bookDao.update(existedBook)
                } else {
                    val newBook = book.toBook()
                    newBook.addToShelf = true
                    appDb.bookDao.insert(newBook)
                }
            }
        }
    }

}