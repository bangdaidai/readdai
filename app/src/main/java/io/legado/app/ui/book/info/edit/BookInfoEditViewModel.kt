package io.legado.app.ui.book.info.edit

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.model.ReadBook

class BookInfoEditViewModel(application: Application) : BaseViewModel(application) {
    var book: Book? = null
    val bookData = MutableLiveData<Book>()

    fun loadBook(bookUrl: String) {
        execute {
            book = appDb.bookDao.getBook(bookUrl)
            if (book == null) {
                val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                if (memory != null) {
                    book = Book(
                        bookUrl = memory.bookUrl,
                        name = memory.bookName,
                        author = memory.bookAuthor,
                        coverUrl = memory.coverUrl,
                        intro = memory.intro
                    )
                }
            }
            book?.let {
                bookData.postValue(it)
            }
        }
    }

    fun saveBook(book: Book, success: (() -> Unit)?) {
        execute {
            val bookUrl = book.bookUrl
            val wasInBookshelf = appDb.bookDao.getBook(bookUrl) != null
            
            // 保存原始书籍信息，用于比较
            val oldBook = bookData.value?.copy()
            
            if (ReadBook.book?.bookUrl == book.bookUrl) {
                ReadBook.book = book
            }
            
            if (wasInBookshelf) {
                // 书籍在书架上，正常更新
                appDb.bookDao.update(book)
            } else {
                // 书籍不在书架上，尝试从阅读记忆获取并更新
                val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                if (memory != null) {
                    val updatedMemory = memory.copy(
                        bookName = book.name,
                        bookAuthor = book.author,
                        coverUrl = book.getDisplayCover(),
                        intro = book.getDisplayIntro(),
                        updateTime = System.currentTimeMillis()
                    )
                    appDb.readingMemoryDao.update(updatedMemory)
                }
            }
            
            // 更新LiveData
            bookData.postValue(book)
            
            // 如果书籍名称或作者发生变化，同步更新所有相关的书摘
            oldBook?.let { old ->
                if (old.name != book.name || old.author != book.author) {
                    // 获取所有相关的书摘
                    val annotations = appDb.bookAnnotationDao.getByBook(old.name, old.author)
                    
                    // 更新每个书摘的书籍名称和作者
                    annotations.forEach { annotation ->
                        val updatedAnnotation = annotation.copy(
                            bookName = book.name,
                            bookAuthor = book.author
                        )
                        appDb.bookAnnotationDao.update(updatedAnnotation)
                    }
                }
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            if (it is SQLiteConstraintException) {
                AppLog.put("书籍信息保存失败，存在相同书名作者书籍\n$it", it, true)
            } else {
                AppLog.put("书籍信息保存失败\n$it", it, true)
            }
        }
    }
}