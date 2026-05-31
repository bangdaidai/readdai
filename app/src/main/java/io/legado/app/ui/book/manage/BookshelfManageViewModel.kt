package io.legado.app.ui.book.manage

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.SourceCallBack
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File


class BookshelfManageViewModel(application: Application) : BaseViewModel(application) {
    var groupId: Long = -1L
    var groupName: String? = null
    val batchChangeSourceState = MutableLiveData<Boolean>()
    val batchChangeSourceProcessLiveData = MutableLiveData<String>()
    var batchChangeSourceCoroutine: Coroutine<Unit>? = null

    fun upCanUpdate(books: List<Book>, canUpdate: Boolean) {
        execute {
            val array = Array(books.size) {
                books[it].copy(canUpdate = canUpdate).apply {
                    if (!canUpdate) {
                        removeType(BookType.updateError)
                    }
                }
            }
            appDb.bookDao.update(*array)
        }
    }

    fun updateBook(vararg book: Book) {
        execute {
            appDb.bookDao.update(*book)
        }
    }

    fun deleteBook(books: List<Book>, deleteOriginal: Boolean = false) {
        execute {
            // 在删除书籍前创建阅读记忆，保留阅读记录和封面信息
            books.forEach { book ->
                createReadingMemory(book)
            }
            
            appDb.bookDao.delete(*books.toTypedArray())
            books.forEach {
                if (it.isLocal) {
                    LocalBook.deleteBook(it, deleteOriginal)
                } else {
                    val source = appDb.bookSourceDao.getBookSource(it.origin)
                    SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, source, it)
                }
            }
        }
    }
    
    /**
     * 创建我的阅读记录
     */
    private suspend fun createReadingMemory(book: Book) {
        // 检查是否已存在相同书籍的我的阅读记录
        val existingMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
        
        if (existingMemory == null) {
            // 获取书评和书摘数量
            val reviews = appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
            val reviewCount = reviews.size
            
            // 获取书摘数量 - 使用书名和作者查询
            val annotations = appDb.bookAnnotationDao.getByBook(book.name, book.author)
            val annotationCount = annotations.size
            
            // 获取实际章节数，始终使用实际章节大小
            val actualChapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
            
            // 计算阅读进度，使用统一的进度计算方法
            val progress = io.legado.app.help.book.ReadingProgressHelper.calculateReadingProgress(
                book.durChapterIndex,
                actualChapterSize
            )
            
            // 获取第一条书评内容
            val firstReviewContent = if (reviews.isNotEmpty()) {
                reviews.first().reviewContent
            } else {
                null
            }
            
            // 计算阅读状态
            val readingStatus = if (progress > 0f || book.durChapterTime > 0L) {
                io.legado.app.constant.ReadingStatus.READING
            } else {
                io.legado.app.constant.ReadingStatus.PENDING
            }
            
            // 创建我的阅读记录
            val readingMemory = io.legado.app.data.entities.ReadingMemory(
                id = "memory_${System.currentTimeMillis()}_${(0..999).random()}",
                bookUrl = book.bookUrl,
                bookName = book.name,
                bookAuthor = book.author,
                wordCount = book.wordCount,
                kind = book.kind,
                coverUrl = book.coverUrl,
                intro = book.intro,
                rating = book.rating,
                totalChapterNum = actualChapterSize,
                durChapterIndex = book.durChapterIndex,
                durChapterTitle = book.durChapterTitle,
                durChapterPos = book.durChapterPos,
                progress = progress,
                readTime = book.durChapterTime,
                annotationCount = annotationCount,
                readingStatus = readingStatus,
                type = book.type,
                createTime = System.currentTimeMillis(),
                updateTime = System.currentTimeMillis()
            )
            
            // 保存我的阅读记录
            appDb.readingMemoryDao.insert(readingMemory)
        }
    }

    fun saveAllUseBookSourceToFile(success: (file: File) -> Unit) {
        execute {
            val path = "${context.filesDir}/shareBookSource.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            val sources = appDb.bookDao.getAllUseBookSource()
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, sources)
            }
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun changeSource(books: List<Book>, source: BookSource) {
        batchChangeSourceCoroutine?.cancel()
        batchChangeSourceCoroutine = execute {
            val changeSourceDelay = AppConfig.batchChangeSourceDelay * 1000L
            books.forEachIndexed { index, book ->
                batchChangeSourceProcessLiveData.postValue("${index + 1} / ${books.size}")
                if (book.isLocal) return@forEachIndexed
                if (book.origin == source.bookSourceUrl) return@forEachIndexed
                val newBook = WebBook.preciseSearchAwait(source, book.name, book.author)
                    .onFailure {
                        AppLog.put("搜索书籍出错\n${it.localizedMessage}", it, true)
                    }.getOrNull() ?: return@forEachIndexed
                kotlin.runCatching {
                    if (newBook.tocUrl.isEmpty()) {
                        WebBook.getBookInfoAwait(source, newBook)
                    }
                }.onFailure {
                    AppLog.put("获取书籍详情出错\n${it.localizedMessage}", it, true)
                    return@forEachIndexed
                }
                WebBook.getChapterListAwait(source, newBook)
                    .onFailure {
                        AppLog.put("获取目录出错\n${it.localizedMessage}", it, true)
                    }.getOrNull()?.let { toc ->
                        // 检查新书源是否属于正版分组
                        val isOfficialSource = source.bookSourceGroup?.contains("正版") == true
                        val migratedBook = book.migrateTo(newBook, toc, isOfficialSource)
                        migratedBook.removeType(BookType.updateError)
                        // 保存旧书的阅读记录
                        val oldMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                        if (oldMemory != null) {
                            // 如果存在旧书的阅读记录，迁移到新书
                            val newMemory = oldMemory.copy(bookUrl = migratedBook.bookUrl)
                            // 根据书源分组决定是否更新阅读记录的字数和分类
                            if (isOfficialSource) {
                                // 正版书源：更新阅读记录的字数和分类信息
                                newMemory.wordCount = migratedBook.wordCount
                                newMemory.kind = migratedBook.kind
                            } else {
                                // 非正版书源：保留原有阅读记录的字数和分类信息
                                // 不做任何修改，直接使用旧的阅读记录
                            }
                            appDb.readingMemoryDao.insert(newMemory)
                        }
                        book.delete()
                        appDb.bookDao.insert(migratedBook)
                        appDb.bookChapterDao.insert(*toc.toTypedArray())
                    }
                delay(changeSourceDelay)
            }
        }.onStart {
            batchChangeSourceState.postValue(true)
        }.onFinally {
            batchChangeSourceState.postValue(false)
        }
    }

    fun clearCache(books: List<Book>) {
        execute {
            books.forEach {
                BookHelp.clearCache(it)
            }
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }
    }

}