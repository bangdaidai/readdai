package io.legado.app.ui.main.explore

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
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
        return appDb.bookDao.getBook(bookUrl) != null ||
               (author.isNotBlank() && appDb.bookDao.getBook(name, author) != null) ||
               appDb.bookDao.getBook(name, "") != null
    }

    fun addToBookshelf(book: SearchBook) {
        execute {
            kotlin.runCatching {
                appDb.bookDao.insert(book.toBook())
            }
        }
    }

}