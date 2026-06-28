package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import android.net.Uri

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProtagonist
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.exception.NoBooksDirException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.BookInfoSyncHelper
import io.legado.app.help.book.TagManager
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.updateTo
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class BookInfoViewModel(application: Application) : BaseViewModel(application) {

    val bookData = MutableLiveData<Book>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    val customBtnListData = MutableLiveData<Boolean>()
    val waitDialogData = MutableLiveData<Boolean>()
    val abandonedWarningData = MutableLiveData<String>() // 用于传递弃文提示信息
    val actionLive = MutableLiveData<String>()

    // Web文件列表
    val webFiles = mutableListOf<WebFile>()
    var inBookshelf = false
    var hasCustomBtn = false
    var bookSource: BookSource? = null
    private var changeSourceCoroutine: Coroutine<*>? = null

    fun initData(intent: Intent) {
        val name = intent.getStringExtra("name") ?: ""
        val author = intent.getStringExtra("author") ?: ""
        val bookUrl = intent.getStringExtra("bookUrl") ?: ""
        appDb.bookDao.getBook(name, author)?.let {
            inBookshelf = !it.isNotShelf
            bookSource = if (it.isLocal) null else appDb.bookSourceDao.getBookSource(it.origin)
            hasCustomBtn = bookSource?.customButton == true
            upBook(it)
            return
        }
        if (bookUrl.isNotBlank()) {
            appDb.bookDao.getBook(bookUrl)?.let {
                inBookshelf = !it.isNotShelf
                bookSource = if (it.isLocal) null else appDb.bookSourceDao.getBookSource(it.origin)
                hasCustomBtn = bookSource?.customButton == true
                upBook(it)
                return
            }
            appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let {
                bookSource = if (it.isLocal) null else appDb.bookSourceDao.getBookSource(it.origin)
                hasCustomBtn = bookSource?.customButton == true
                upBook(it)
                return
            }
        }
        appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()?.let {
            bookSource = if (it.isLocal) null else appDb.bookSourceDao.getBookSource(it.origin)
            hasCustomBtn = bookSource?.customButton == true
            upBook(it)
            return
        }
    }

    fun upBook(intent: Intent) {
        execute {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            appDb.bookDao.getBook(name, author)?.let { book ->
                upBook(book)
            }
        }
    }

    private fun upBook(book: Book) {
        execute {
            // 检查书籍是否在书架上
            val dbBook = appDb.bookDao.getBook(book.name, book.author)
            inBookshelf = dbBook?.let { !it.isNotShelf } ?: false

            bookData.postValue(book)
            upCoverByRule(book)
            bookSource = if (book.isLocal) null else
                appDb.bookSourceDao.getBookSource(book.origin)?.also {
                    hasCustomBtn = it.customButton
                }
            if (book.tocUrl.isEmpty() && !book.isLocal) {
                loadBookInfo(book, runPreUpdateJs = inBookshelf)
            } else {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                if (chapterList.isNotEmpty()) {
                    chapterListData.postValue(chapterList)
                } else {
                    loadChapter(book, isFromBookInfo = true)
                }
            }
        }
    }

    private fun upCoverByRule(book: Book) {
        execute {
            if (book.coverUrl.isNullOrBlank() && book.customCoverUrl.isNullOrBlank()) {
                val coverUrl = BookCover.searchCover(book)
                if (coverUrl.isNullOrBlank()) {
                    return@execute
                }
                book.customCoverUrl = coverUrl
                bookData.postValue(book)
                if (inBookshelf) {
                    saveBook(book)
                }
            }
        }
    }

    fun refreshBook(book: Book) {
        executeLazy(executeContext = IO) {
            if (book.isLocal) {
                book.tocUrl = ""
                book.getRemoteUrl()?.let {
                    val bookWebDav = AppWebDav.defaultBookWebDav
                        ?: throw NoStackTraceException("webDav没有配置")
                    val remoteBook = bookWebDav.getRemoteBook(it)
                    if (remoteBook == null) {
                        book.origin = BookType.localTag
                    } else if (remoteBook.lastModify > book.lastCheckTime) {
                        val uri = bookWebDav.downloadRemoteBook(remoteBook)
                        book.bookUrl = if (uri.isContentScheme()) uri.toString() else uri.path!!
                        book.lastCheckTime = remoteBook.lastModify
                    }
                }
            } else {
                val bs = bookSource ?: return@executeLazy
                if (book.originName != bs.bookSourceName) {
                    book.originName = bs.bookSourceName
                }
            }
        }.onError {
            when (it) {
                is ObjectNotFoundException -> {
                    book.origin = BookType.localTag
                }

                else -> {
                    AppLog.put("下载远程书籍<${book.name}>失败", it)
                }
            }
        }.onFinally {
            loadBookInfo(book, false)
        }.start()
    }

    fun loadBookInfo(
        book: Book,
        canReName: Boolean = true,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope
    ) {
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            bookData.postValue(book)
            loadChapter(book)
        } else {
            val bookSource = bookSource ?: let {
                chapterListData.postValue(emptyList())
                context.toastOnUi(R.string.error_no_source)
                return
            }
            WebBook.getBookInfo(scope, bookSource, book, canReName = canReName)
                .onSuccess(IO) {
                    val dbBook = appDb.bookDao.getBook(book.name, book.author)
                    if (!inBookshelf && dbBook != null && !dbBook.isNotShelf && dbBook.origin == book.origin) {
                        /**
                         * book 来自搜索时(inBookshelf == false)，搜索的书名不存在于书架，但是加载详情后，书名更新，存在同名书籍
                         * 此时 book 的数据会与数据库中的不同，需要更新 #3652 #4619
                         * book 加载详情后虽然书名作者相同，但是又可能不是数据库中(书源不同)的那本书 #3149
                         */
                        dbBook.updateTo(it)
                        inBookshelf = true
                    }
                    bookData.postValue(it)
                    if (inBookshelf) {
                        it.save()
                    }
                    if (it.isWebFile) {
                        loadWebFile(it)
                    } else {
                        loadChapter(it, runPreUpdateJs, isFromBookInfo = true)
                    }
                }.onError {
                    AppLog.put("获取书籍信息失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_book_info)
                }
        }
    }

    fun loadChapter(
        book: Book,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
        isFromBookInfo: Boolean = false
    ) {
        if (book.isLocal) {
            execute(scope) {
                LocalBook.getChapterList(book).let {
                    appDb.bookDao.update(book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                    bookData.postValue(book)
                    chapterListData.postValue(it)
                }
            }.onError {
                context.toastOnUi("LoadTocError:${it.localizedMessage}")
            }
        } else {
            val bookSource = bookSource ?: let {
                chapterListData.postValue(emptyList())
                context.toastOnUi(R.string.error_no_source)
                return
            }
            val oldBook = book.copy()
            WebBook.getChapterList(scope, bookSource, book, runPreUpdateJs, isFromBookInfo = isFromBookInfo)
                .onSuccess(IO) {
                    if (inBookshelf) {
                        book.removeType(BookType.updateError)
                        appDb.bookDao.replace(oldBook, book)
                        /**
                         * runPreUpdateJs 有可能会修改 book 的 bookUrl
                         */
                        if (oldBook.bookUrl != book.bookUrl) {
                            BookHelp.updateCacheFolder(oldBook, book)
                        }
                        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                        appDb.bookChapterDao.insert(*it.toTypedArray())
                        ReadBook.onChapterListUpdated(book)
                    }
                    bookData.postValue(book)
                    chapterListData.postValue(it)
                }.onError {
                    chapterListData.postValue(emptyList())
                    AppLog.put("获取目录失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_chapter_list)
                }
        }
    }


    fun loadGroup(groupId: Long, success: ((groupNames: String?) -> Unit)) {
        execute {
            appDb.bookGroupDao.getGroupNames(groupId).joinToString(",")
        }.onSuccess {
            success.invoke(it)
        }
    }

    // 简化loadWebFile方法，移除对webFiles的依赖
    private fun loadWebFile(book: Book) {
        execute {
            webFiles.clear()
            val fileNameNoExtension = if (book.author.isBlank()) book.name
            else "${book.name} 作者：${book.author}"
            book.downloadUrls!!.map {
                val analyzeUrl = AnalyzeUrl(
                    it, source = bookSource,
                    coroutineContext = coroutineContext
                )
                var mFileName = UrlUtil.getFileName(analyzeUrl)
                    ?: fileNameNoExtension
                analyzeUrl.type?.let { suffix ->
                    mFileName += ".${suffix}"
                }
                WebFile(it, mFileName)
            }
        }.onError {
            context.toastOnUi("LoadWebFileError\n${it.localizedMessage}")
        }.onSuccess {
            webFiles.addAll(it)
            book.latestChapterTitle = "已下载"
            bookData.postValue(book)
            chapterListData.postValue(emptyList())
        }
    }

    /* 导入或者下载在线文件 */
    fun <T> importOrDownloadWebFile(webFile: WebFile, success: ((T) -> Unit)?) {
        bookSource ?: return
        execute {
            waitDialogData.postValue(true)
            if (webFile.isSupported) {
                val book = LocalBook.importFileOnLine(
                    webFile.url,
                    bookData.value!!.getExportFileName(webFile.suffix),
                    bookSource
                )
                changeToLocalBook(book)
            } else {
                LocalBook.saveBookFile(
                    webFile.url,
                    bookData.value!!.getExportFileName(webFile.suffix),
                    bookSource
                )
            }
        }.onSuccess {
            @Suppress("unchecked_cast")
            success?.invoke(it as T)
        }.onError {
            when (it) {
                is NoBooksDirException -> actionLive.postValue("selectBooksDir")
                else -> {
                    AppLog.put("ImportWebFileError\n${it.localizedMessage}", it)
                    context.toastOnUi("ImportWebFileError\n${it.localizedMessage}")
                    webFiles.remove(webFile)
                }
            }
        }.onFinally {
            waitDialogData.postValue(false)
        }
    }

    fun getArchiveFilesName(archiveFileUri: Uri, onSuccess: (List<String>) -> Unit) {
        execute {
            ArchiveUtils.getArchiveFilesName(archiveFileUri) {
                AppPattern.bookFileRegex.matches(it)
            }
        }.onError {
            AppLog.put("getArchiveEntriesName Error:\n${it.localizedMessage}", it)
            context.toastOnUi("getArchiveEntriesName Error:\n${it.localizedMessage}")
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun importArchiveBook(
        archiveFileUri: Uri,
        archiveEntryName: String,
        success: ((Book) -> Unit)? = null
    ) {
        execute {
            val suffix = archiveEntryName.substringAfterLast(".")
            LocalBook.importArchiveFile(
                archiveFileUri,
                bookData.value!!.getExportFileName(suffix)
            ) {
                it.contains(archiveEntryName)
            }.first()
        }.onSuccess {
            val book = changeToLocalBook(it)
            success?.invoke(book)
        }.onError {
            AppLog.put("importArchiveBook Error:\n${it.localizedMessage}", it)
            context.toastOnUi("importArchiveBook Error:\n${it.localizedMessage}")
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            bookSource = source
            val oldBook = bookData.value
            // 检查新书源是否属于正版分组
            val isOfficialSource = source.bookSourceGroup?.contains("正版") == true
            val newBook = oldBook?.migrateTo(book, toc, isOfficialSource)

            if (inBookshelf && newBook != null) {
                // 检查新书源是否属于正版分组
                val isOfficialSource = source.bookSourceGroup?.contains("正版") == true

                // 根据书源分组决定是否更新书籍信息
                if (isOfficialSource) {
                    // 正版书源：使用新书源的书籍基本信息
                    context.toastOnUi("已更换为正版书源，书籍信息已更新")
                } else {
                    // 非正版书源：保留原有的书籍基本信息
                    context.toastOnUi("已更换为非正版书源，已保留原有的书名、作者、封面、分类、简介等信息")
                }

                newBook.removeType(BookType.updateError)

                // 保存旧书的阅读记录和标签关联关系
                val oldMemory = oldBook?.let { appDb.readingMemoryDao.getByBookUrl(it.bookUrl) }
                val oldTagRelations = oldBook?.let { appDb.bookTagRelationDao.getRelationsByBook(it.bookUrl) } ?: emptyList()
                val oldProtagonists = oldBook?.let { appDb.bookProtagonistDao.getByBook(it.bookUrl) } ?: emptyList()

                oldBook?.delete()
                appDb.bookDao.insert(newBook)
                appDb.bookChapterDao.insert(*toc.toTypedArray())

                // 迁移书籍标签关联关系到新书
                if (oldTagRelations.isNotEmpty()) {
                    val newTagRelations = oldTagRelations.map { relation ->
                        relation.copy(bookUrl = newBook.bookUrl)
                    }
                    appDb.bookTagRelationDao.insertAll(newTagRelations)
                }

                // 迁移主角名到新书
                if (oldProtagonists.isNotEmpty()) {
                    val newProtagonists = oldProtagonists.map { protagonist ->
                        BookProtagonist(
                            bookUrl = newBook.bookUrl,
                            name = protagonist.name,
                            isCustom = protagonist.isCustom,
                            createTime = protagonist.createTime,
                            updateTime = System.currentTimeMillis()
                        )
                    }
                    appDb.bookProtagonistDao.insertAll(newProtagonists)
                }

                // 同步更新我的阅读记录
                if (oldMemory != null) {
                    // 如果存在旧书的阅读记录，迁移到新书
                    val newMemory = oldMemory.copy(bookUrl = newBook.bookUrl)
                    // 根据书源分组决定是否更新阅读记录的字数和分类
                    if (isOfficialSource) {
                        // 正版书源：更新阅读记录的字数和分类信息
                        newMemory.wordCount = newBook.wordCount
                        newMemory.kind = newBook.kind
                    } else {
                        // 非正版书源：保留原有阅读记录的字数和分类信息
                        // 不做任何修改，直接使用旧的阅读记录
                    }
                    appDb.readingMemoryDao.insert(newMemory)
                } else {
                    // 否则获取或创建新书的阅读记录
                    var memory = appDb.readingMemoryDao.getByBookUrl(newBook.bookUrl)
                    if (memory == null) {
                        val newMemory = ReadingMemory(bookUrl = newBook.bookUrl, type = newBook.type)
                        // 初始化阅读记录的字数和分类信息
                        newMemory.wordCount = newBook.wordCount
                        newMemory.kind = newBook.kind
                        appDb.readingMemoryDao.insert(newMemory)
                    } else {
                        // 更新阅读记录的字数和分类信息（仅当是正版书源时）
                        if (isOfficialSource) {
                            val updatedMemory = memory.copy(
                                wordCount = newBook.wordCount,
                                kind = newBook.kind
                            )
                            appDb.readingMemoryDao.update(updatedMemory)
                        }
                    }
                }
            }
            bookData.postValue(book)
            chapterListData.postValue(toc)

            // 书源改变时更新标签
            if (newBook != null) {
                TagManager.updateTagsOnSourceChange(newBook)
            }
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun changeToLocal(localBook: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            val oldBook = bookData.value
            val newBook = oldBook?.migrateTo(localBook, toc, false)

            if (inBookshelf && newBook != null) {
                newBook.removeType(BookType.updateError)

                val oldMemory = oldBook?.let { appDb.readingMemoryDao.getByBookUrl(it.bookUrl) }
                val oldTagRelations = oldBook?.let { appDb.bookTagRelationDao.getRelationsByBook(it.bookUrl) } ?: emptyList()
                val oldProtagonists = oldBook?.let { appDb.bookProtagonistDao.getByBook(it.bookUrl) } ?: emptyList()

                oldBook?.delete()
                appDb.bookDao.insert(newBook)
                appDb.bookChapterDao.insert(*toc.toTypedArray())

                if (oldTagRelations.isNotEmpty()) {
                    val newTagRelations = oldTagRelations.map { relation ->
                        relation.copy(bookUrl = newBook.bookUrl)
                    }
                    appDb.bookTagRelationDao.insertAll(newTagRelations)
                }

                if (oldProtagonists.isNotEmpty()) {
                    val newProtagonists = oldProtagonists.map { protagonist ->
                        BookProtagonist(
                            bookUrl = newBook.bookUrl,
                            name = protagonist.name,
                            isCustom = protagonist.isCustom,
                            createTime = protagonist.createTime,
                            updateTime = System.currentTimeMillis()
                        )
                    }
                    appDb.bookProtagonistDao.insertAll(newProtagonists)
                }

                if (oldMemory != null) {
                    val newMemory = oldMemory.copy(bookUrl = newBook.bookUrl)
                    appDb.readingMemoryDao.insert(newMemory)
                } else {
                    var memory = appDb.readingMemoryDao.getByBookUrl(newBook.bookUrl)
                    if (memory == null) {
                        val newMemory = ReadingMemory(bookUrl = newBook.bookUrl, type = newBook.type)
                        newMemory.wordCount = newBook.wordCount
                        newMemory.kind = newBook.kind
                        appDb.readingMemoryDao.insert(newMemory)
                    }
                }
            }
            bookData.postValue(newBook ?: localBook)
            chapterListData.postValue(toc)

            if (newBook != null) {
                TagManager.updateTagsOnSourceChange(newBook)
            }
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, localBook.bookUrl)
        }
    }

    fun topBook() {
        execute {
            bookData.value?.let { book ->
                val minOrder = appDb.bookDao.minOrder
                book.order = minOrder - 1
                book.durChapterTime = System.currentTimeMillis()
                appDb.bookDao.update(book)
            }
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        execute {
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.book?.isSameNameAuthor(book) == true) {
                ReadBook.book = book
            } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                AudioPlay.book = book
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun saveChapterList(success: (() -> Unit)?) {
        execute {
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)?) { //点击书架按钮或在加分组时触发
        execute {
            bookData.value?.let { book ->
                // 检查是否存在相同作者和书名的弃文记录
                val abandonedMemories = appDb.readingMemoryDao.getAbandonedByBook(book.name, book.author)
                if (abandonedMemories.isNotEmpty()) {
                    // 获取最新的弃文记录
                    val latestAbandonedMemory = abandonedMemories.maxByOrNull { it.updateTime } ?: abandonedMemories[0]
                    // 将弃文记录的updateTime转换为日期格式，用于提示
                    val abandonedDate = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(latestAbandonedMemory.updateTime),
                        java.time.ZoneId.systemDefault()
                    ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))

                    // 使用LiveData通知UI层显示弃文提示
                    val warningMessage = "该书籍曾于${abandonedDate}被标记为弃文"
                    abandonedWarningData.postValue(warningMessage)
                }

                book.removeType(BookType.notShelf)
                if (book.order == 0) {
                    book.order = appDb.bookDao.minOrder - 1
                }
                appDb.bookDao.getBook(book.name, book.author)?.let {
                    book.durChapterIndex = it.durChapterIndex
                    book.durChapterPos = it.durChapterPos
                    book.durChapterTitle = it.durChapterTitle
                }
                if (ReadBook.book?.isSameNameAuthor(book) == true) {
                    ReadBook.book = book
                } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                    AudioPlay.book = book
                }

                // 自动设置阅读状态和分组
                // 如果用户没有手动修改过阅读状态，则根据阅读进度自动设置
                if (!book.userModifiedReadingStatus) {
                    val readingStatus = io.legado.app.help.book.ReadingProgressHelper.calculateReadingStatus(book)
                    // 不重置group字段，保留原有的分组信息
                    book.setReadingStatus(readingStatus)
                }

                book.save()
                SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, bookSource, book)

                // 创建我的阅读记录
                createReadingMemory(book)
            }
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
            inBookshelf = true
        }.onSuccess {
            success?.invoke()
        }
    }

    fun getBook(toastNull: Boolean = true): Book? {
        val book = bookData.value
        if (toastNull && book == null) {
            context.toastOnUi("book is null")
        }
        return book
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let { book ->
                // 在删除书籍前创建阅读记忆，保留阅读记录和封面信息
                createReadingMemory(book)

                // 执行删除逻辑
                book.delete()
                inBookshelf = false
                if (book.isLocal) {
                    LocalBook.deleteBook(book, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    /**
     * 删除书籍并将其标记为弃文
     */
    fun delBookAndMarkAsAbandoned(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let { book ->
                // 在删除书籍前创建阅读记忆，并将其标记为弃文
                createReadingMemory(book, markAsAbandoned = true)

                // 执行删除逻辑
                book.delete()
                inBookshelf = false
                if (book.isLocal) {
                    LocalBook.deleteBook(book, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    /**
     * 从书架删除书籍，但保留阅读记忆
     * 这个方法用于替代delBook，当用户只想从书架删除书籍但保留阅读记忆时使用
     */
    fun removeFromBookshelf(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let { book ->
                // 在删除书籍前创建阅读记忆，保留阅读记录和封面信息
                createReadingMemory(book)

                // 从书架中移除书籍，但保留阅读记忆
                book.delete()
                inBookshelf = false
                if (book.isLocal) {
                    LocalBook.deleteBook(book, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    /**
     * 从书架删除书籍，不保留阅读记忆
     */
    fun delBookWithoutMemory(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let { book ->
                // 直接删除书籍，不创建阅读记忆
                book.delete()
                inBookshelf = false
                if (book.isLocal) {
                    LocalBook.deleteBook(book, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    /**
     * 创建我的阅读记录
     */
    private fun createReadingMemory(book: Book, markAsAbandoned: Boolean = false) {
        execute {
            // 检查是否已存在相同书籍的我的阅读记录
            var existingMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)

            // 如果没有找到匹配bookUrl的记忆，尝试查找相同书名和作者的记忆
            if (existingMemory == null) {
                val memoriesByBook = appDb.readingMemoryDao.getByBook(book.name, book.author)
                if (memoriesByBook.isNotEmpty()) {
                    // 使用最新的记忆（按更新时间降序排列，第一个是最新的）
                    existingMemory = memoriesByBook.first()

                    // 删除其他重复的记忆，只保留最新的一个
                    if (memoriesByBook.size > 1) {
                        for (i in 1 until memoriesByBook.size) {
                            appDb.readingMemoryDao.deleteById(memoriesByBook[i].id)
                        }
                    }
                }
            }

            // 获取书评和书摘数量
            val reviews = appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
            val reviewCount = reviews.size

            // 获取书摘数量 - 使用书名和作者查询
            val annotations = appDb.bookAnnotationDao.getByBook(book.name, book.author)
            val annotationCount = annotations.size

            // 获取总章节数
            val totalChapterNum = appDb.bookChapterDao.getChapterCount(book.bookUrl)

            // 计算阅读进度
            // 检查是否真正阅读过这本书
            val hasRead = book.durChapterTime > 0 || book.durChapterPos > 0 || book.durChapterIndex > 0
            val progress = if (hasRead && totalChapterNum > 0) {
                ((book.durChapterIndex + 1).toFloat() / totalChapterNum.toFloat()) * 100
            } else {
                0f
            }

            // 获取第一条书评内容
            val firstReviewContent = if (reviews.isNotEmpty()) {
                reviews.first().reviewContent
            } else {
                null
            }

            // 计算阅读状态
            val readingStatus = if (markAsAbandoned) {
                io.legado.app.constant.ReadingStatus.ABANDONED
            } else if (book.readingStatus == io.legado.app.constant.ReadingStatus.ABANDONED.value) {
                io.legado.app.constant.ReadingStatus.ABANDONED
            } else if (progress > 0f || book.durChapterTime > 0L) {
                io.legado.app.constant.ReadingStatus.READING
            } else {
                io.legado.app.constant.ReadingStatus.PENDING
            }

            if (existingMemory == null) {
                // 获取书籍的阅读时长
                val readTime = try {
                    // 先尝试使用book.name查询阅读时长
                    val timeByName = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
                    if (timeByName > 0L) {
                        timeByName
                    } else {
                        // 如果使用book.name查询不到结果，尝试使用bookUrl查询
                        val timeByUrl = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl)
                        timeByUrl ?: 0L
                    }
                } catch (e: Exception) {
                    0L
                }

                // 创建我的阅读记录
                val readingMemory = io.legado.app.data.entities.ReadingMemory(
                    id = "memory_${System.currentTimeMillis()}_${(0..999).random()}",
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    bookAuthor = book.author,
                    wordCount = book.wordCount,
                    kind = book.kind,
                    coverUrl = book.getDisplayCover(), // 使用getDisplayCover获取封面URL，包含用户自定义封面
                    intro = book.intro,
                    rating = book.rating,
                    totalChapterNum = totalChapterNum,
                    durChapterIndex = book.durChapterIndex,
                    durChapterTitle = book.durChapterTitle,
                    durChapterPos = book.durChapterPos,
                    progress = progress,
                    readTime = readTime,
                    annotationCount = annotationCount,
                    readingStatus = readingStatus,
                    type = book.type,
                    createTime = System.currentTimeMillis(),
                    updateTime = System.currentTimeMillis()
                )

                // 保存我的阅读记录
                appDb.readingMemoryDao.insert(readingMemory)
            } else {
                // 获取书籍的阅读时长
                val readTime = try {
                    // 先尝试使用book.name查询阅读时长
                    val timeByName = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
                    if (timeByName > 0L) {
                        timeByName
                    } else {
                        // 如果使用book.name查询不到结果，尝试使用bookUrl查询
                        val timeByUrl = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl)
                        timeByUrl ?: 0L
                    }
                } catch (e: Exception) {
                    0L
                }

                // 更新现有的我的阅读记录
                val updatedMemory = existingMemory.copy(
                    bookUrl = book.bookUrl, // 确保更新bookUrl
                    bookName = book.name,
                    bookAuthor = book.author,
                    wordCount = book.wordCount,
                    kind = book.kind,
                    coverUrl = book.getDisplayCover(),
                    intro = book.intro,
                    rating = book.rating,
                    totalChapterNum = totalChapterNum,
                    durChapterIndex = book.durChapterIndex,
                    durChapterTitle = book.durChapterTitle,
                    durChapterPos = book.durChapterPos,
                    progress = progress,
                    readTime = readTime,
                    annotationCount = annotationCount,
                    readingStatus = readingStatus,
                    updateTime = System.currentTimeMillis()
                )

                // 保存更新后的阅读记录
                appDb.readingMemoryDao.update(updatedMemory)
            }
        }
    }

    fun clearCache() {
        execute {
            BookHelp.clearCache(bookData.value!!)
            if (ReadBook.book?.bookUrl == bookData.value!!.bookUrl) {
                ReadBook.clearTextChapter()
            }
            if (ReadManga.book?.bookUrl == bookData.value!!.bookUrl) {
                ReadManga.clearMangaChapter()
            }
            SourceCallBack.callBackBook(SourceCallBack.CLICK_CLEAR_CACHE, bookSource, bookData.value!!)
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }.onError {
            context.toastOnUi("清理缓存出错\n${it.localizedMessage}")
        }
    }

    fun upEditBook() {
        bookData.value?.let {
            appDb.bookDao.getBook(it.bookUrl)?.let { book ->
                bookData.postValue(book)
            }
        }
    }

    private fun changeToLocalBook(localBook: Book): Book {
        return LocalBook.mergeBook(localBook, bookData.value).let {
            bookData.postValue(it)
            loadChapter(it)
            inBookshelf = true
            it
        }
    }

    fun onButtonClick(activity: AppCompatActivity, name: String, click: String) {
        val source = bookSource ?: return
        val book = bookData.value ?: return
        execute {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(click) {
                    put("result", null)
                    put("java", java)
                    put("book", book)
                }
            }
        }.onError {
            AppLog.put("${source.bookSourceName}: ${it.localizedMessage}", it)
            context.toastOnUi("$name click error\n${it.localizedMessage}")
        }
    }

    data class WebFile(
        val url: String,
        val name: String,
    ) {

        override fun toString(): String {
            return name
        }

        // 后缀
        val suffix: String = UrlUtil.getSuffix(name)

        // txt epub umd pdf等文件
        val isSupported: Boolean = AppPattern.bookFileRegex.matches(name)

        // 压缩包形式的txt epub umd pdf文件
        val isSupportDecompress: Boolean = AppPattern.archiveFileRegex.matches(name)

    }

}