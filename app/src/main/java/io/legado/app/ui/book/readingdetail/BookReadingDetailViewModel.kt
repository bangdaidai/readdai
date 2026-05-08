/*
 * BookReadingDetailViewModel 已被 BookReadingMemory 模块取代
 * 保留此文件仅作为备份，实际功能已迁移到 ReadingMemoryDetailViewModel
 */
package io.legado.app.ui.book.readingdetail

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.BookReview
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.help.book.BookInfoSyncHelper
import io.legado.app.help.book.ReadingProgressHelper
import io.legado.app.help.book.TagManager
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.*
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.exception.NoStackTraceException
import io.legado.app.constant.EventBus
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class BookReadingDetailViewModel(application: Application) : BaseViewModel(application) {

    val bookData = MutableLiveData<Book>()
    val totalReadingTime = MutableLiveData<String>()
    val readingDays = MutableLiveData<String>()
    val lastReadTime = MutableLiveData<String>()
    val readingProgress = MutableLiveData<String>()
    val wordCount = MutableLiveData<String>()
    val bookKind = MutableLiveData<String>()
    val startReadingTime = MutableLiveData<String>()
    val readChapters = MutableLiveData<String>()
    val bookCoverUrl = MutableLiveData<String>()
    val annotations = MutableLiveData<List<Bookmark>>()
    val reviewCount = MutableLiveData<Int>()
    val reviews = MutableLiveData<List<BookReview>>()
    val bookTags = MutableLiveData<List<BookTag>>()
    val isLoading = MutableLiveData<Boolean>()
    val annotationCount = MutableLiveData<Int>(0)
    
    // 用于显示累计时长的小时和分钟
    val totalReadingHours = MutableLiveData<Long>(0)
    val totalReadingMinutes = MutableLiveData<Long>(0)
    
    // 用于显示单日阅读最久的小时和分钟
    val maxDayReadHours = MutableLiveData<Long>(0)
    val maxDayReadMinutes = MutableLiveData<Long>(0)
    val maxDayReadDate = MutableLiveData<String>("")
    
    // 用于显示阅读总字数
    val totalReadWords = MutableLiveData<String>("")
    val remainingWords = MutableLiveData<String>("")

    // 阅读会话数据
    val monthlyReadingSessions = MutableLiveData<List<MonthReadingSession>>()

    // 月度阅读会话数据类
    data class MonthReadingSession(
        val monthTitle: String, // 月份标题，如"2025年12月"
        val totalReadTime: Long, // 总阅读时间（毫秒）
        val dailySessions: List<DailyReadingSession> // 每日阅读时间
    )

    // 每日阅读会话数据类
    data class DailyReadingSession(
        val date: String, // 日期，如"12月21日"
        val readTime: Long // 阅读时间（毫秒）
    )

    private var bookUrl: String = ""
    
    fun initData(bookUrl: String) {
        Log.d("ReadingDetailVM", "开始初始化阅读详情数据，bookUrl: $bookUrl")
        val startTime = System.currentTimeMillis()
        
        this.bookUrl = bookUrl
        loadBookData()
        
        // 获取书籍信息后再加载进度
        viewModelScope.launch {
            val book: Book? = withContext(IO) {
                appDb.bookDao.getBook(bookUrl)
            }
            if (book != null) {
                // 预加载必要数据，减少后续查询
                val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                val chapters = appDb.bookChapterDao.getChapterCount(bookUrl)
                loadBookProgress(book, memory, sessions, chapters)
            }
            loadBookTags()
        }
        
        // 启动实时监听器
        startRealTimeListeners()
        
        Log.d("ReadingDetailVM", "初始化阅读详情数据完成，耗时: ${System.currentTimeMillis() - startTime}ms")
    }
    
    /**
     * 启动实时监听器，监听数据变化
     */
    private fun startRealTimeListeners() {
        // 实时监听书籍变化，实现双向同步
        viewModelScope.launch {
            var lastBook: Book? = null
            appDb.bookDao.flowGetBookByUrl(bookUrl).collect {
                it?.let { updatedBook ->
                    // 只有当书籍关键信息变化时才更新
                    val shouldUpdate = lastBook == null ||
                            lastBook!!.durChapterIndex != updatedBook.durChapterIndex ||
                            lastBook!!.durChapterPos != updatedBook.durChapterPos ||
                            lastBook!!.durChapterTime != updatedBook.durChapterTime ||
                            lastBook!!.name != updatedBook.name ||
                            lastBook!!.author != updatedBook.author
                    
                    if (shouldUpdate) {
                        // 只要书籍数据发生变化，就更新阅读详情页
                        // 这样可以确保无论是正版书源还是手动修改后，阅读详情页都能正确更新
                        bookData.value = updatedBook
                        // 预加载必要数据，减少后续查询
                        val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                        val sessions = appDb.readSessionDao.getByBook(updatedBook.name, updatedBook.author)
                        val chapters = appDb.bookChapterDao.getChapterCount(bookUrl)
                        loadBookProgress(updatedBook, memory, sessions, chapters)
                        // 创建一个updatedBook的副本，避免引用同一个对象导致比较失效
                        lastBook = updatedBook.copy()
                    }
                }
            }
        }
        
        // 实时监听阅读会话变化，更新阅读时间和天数
        viewModelScope.launch {
            val currentBook = appDb.bookDao.getBook(bookUrl)
            currentBook?.let {
                // 预加载必要数据，减少后续查询
                val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                val sessions = appDb.readSessionDao.getByBook(it.name, it.author)
                val chapters = appDb.bookChapterDao.getChapterCount(bookUrl)
                loadBookProgress(it, memory, sessions, chapters)
            }
        }
        
        // 实时监听书摘变化，实现删除后实时更新UI
        viewModelScope.launch {
            var lastBook: Book? = null
            appDb.bookDao.flowGetBookByUrl(bookUrl).collect {
                it?.let { currentBook ->
                    // 只有当书籍信息变化时才重新设置监听器
                    if (lastBook == null || lastBook!!.name != currentBook.name || lastBook!!.author != currentBook.author) {
                        lastBook = currentBook
                        
                        // 启动新的书摘监听器
                        viewModelScope.launch {
                            appDb.bookAnnotationDao.flowByBook(currentBook.name, currentBook.author)
                                .flowOn(Dispatchers.IO)
                                .catch { e ->
                                    Log.e("ReadingDetailVM", "Error listening annotations: ${e.localizedMessage}", e)
                                }
                                .collect {
                                    processAnnotations(it)
                                }
                        }
                    }
                }
            }
        }
        
        // 实时监听书评变化
        viewModelScope.launch {
            appDb.bookReviewDao.getByBook(bookUrl).collect {
                processReviews(it)
            }
        }
    }
    
    fun refresh() {
        // 强制重新加载所有数据，包括字数和分类
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    isLoading.value = true
                }
                
                // 获取最新的书籍信息
                val book: Book? = withContext(IO) {
                    appDb.bookDao.getBook(bookUrl)
                }
                
                if (book == null) {
                    val errorMsg = "书籍不存在或已被删除"
                    Log.e("BRDetailViewModel", errorMsg)
                    throw NoStackTraceException(errorMsg)
                }
                
                // 检查书源是否为正版
                val bookSource = withContext(IO) {
                    appDb.bookSourceDao.getBookSource(book.origin)
                }
                val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                
                // 使用BookInfoSyncHelper同步书籍的字数和分类到我的阅读记录
                val memory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                var syncSuccess = false
                if (memory != null) {
                    syncSuccess = BookInfoSyncHelper.syncBookWordCountAndKindToMemory(
                        book, memory, appDb
                    )
                }
                
                if (syncSuccess) {
                    Log.d("BRDetailViewModel", "书籍字数和分类同步到我的阅读记录成功")
                } else {
                    Log.w("BRDetailViewModel", "书籍字数和分类同步到我的阅读记录失败")
                    // 不中断流程，继续执行
                }
                
                withContext(Dispatchers.Main) {
                    // 只要书籍数据发生变化，就更新阅读详情页
                    // 这样可以确保无论是正版书源还是手动修改后，阅读详情页都能正确更新
                    bookData.value = book
                }
                
                // 预加载必要数据，减少后续查询
                val updatedMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                val chapters = appDb.bookChapterDao.getChapterCount(bookUrl)
                
                // 重新加载所有数据，包括字数和分类
                loadBookProgress(book, updatedMemory, sessions, chapters)
                loadAnnotations(book)
                loadReviews(book)
                loadBookTags()
                loadReadingSessions(book)
                
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    context.toastOnUi("刷新成功")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "刷新失败"
                Log.e("BRDetailViewModel", "刷新失败: $errorMsg", e)
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    context.toastOnUi(errorMsg)
                }
            }
        }
    }
    
    /**
     * 仅刷新书籍基本信息，根据参数决定是否更新字数和分类
     * 用于更换书源后的更新
     * @param updateWordCountAndKind 是否更新字数和分类信息
     */
    fun refreshBookDataOnly(updateWordCountAndKind: Boolean = false) {
        viewModelScope.launch {
            try {
                // 获取当前书籍信息
                val currentBook: Book? = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(bookUrl)
                }
                
                if (currentBook == null) {
                    context.toastOnUi("书籍信息不存在")
                    return@launch
                }
                
                // 获取书源信息，判断是否为正版书源
                val bookSource = withContext(Dispatchers.IO) {
                    appDb.bookSourceDao.getBookSource(currentBook.origin)
                }
                val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                
                if (updateWordCountAndKind) {
                    // 根据书源分组决定同步方向
                    if (isOfficialSource) {
                        // 正版书源：同步书籍的字数和分类到我的阅读记录
                        val memory = appDb.readingMemoryDao.getByBookUrl(currentBook.bookUrl)
                        var success = false
                        if (memory != null) {
                            success = BookInfoSyncHelper.syncBookWordCountAndKindToMemory(
                                currentBook, memory, appDb
                            )
                        }
                        
                        if (success) {
                            context.toastOnUi("书籍信息同步成功")
                        } else {
                            context.toastOnUi("书籍信息同步失败")
                        }
                    } else {
                        // 非正版书源：同步我的阅读记录的字数和分类到书籍
                        val memory = appDb.readingMemoryDao.getByBookUrl(currentBook.bookUrl)
                        var success = false
                        if (memory != null) {
                            success = BookInfoSyncHelper.syncMemoryWordCountAndKindToBook(
                                memory, currentBook, appDb
                            )
                        }
                        
                        if (success) {
                            context.toastOnUi("我的阅读记录信息同步成功")
                        } else {
                            context.toastOnUi("我的阅读记录信息同步失败")
                        }
                    }
                }
                
                // 重新加载书籍数据
                val updatedBook: Book? = withContext(IO) {
                    appDb.bookDao.getBook(currentBook.bookUrl)
                }
                
                withContext(Dispatchers.Main) {
                    if (updatedBook != null) {
                        // 只要书籍数据发生变化，就更新阅读详情页
                        // 这样可以确保无论是正版书源还是手动修改后，阅读详情页都能正确更新
                        bookData.value = updatedBook
                        // 更新字数和分类的LiveData，将字数转换为万字单位
                        val bookWordCount = updatedBook.wordCount
                        val updatedWordCount = if (bookWordCount.isNullOrEmpty()) "无" else {
                            val totalWords = parseTotalWords(bookWordCount)
                            "${formatWordCount(totalWords)}万字"
                        }
                        wordCount.value = updatedWordCount
                        bookKind.value = if (updatedBook.kind.isNullOrEmpty()) "无" else updatedBook.kind
                    }
                }
                
                // 根据参数决定加载哪些数据
                if (updateWordCountAndKind) {
                    // 加载所有数据包括字数和分类
                    if (updatedBook != null) {
                        val memory = appDb.readingMemoryDao.getByBookUrl(updatedBook.bookUrl)
                        val sessions = appDb.readSessionDao.getByBook(updatedBook.name, updatedBook.author)
                        val chapters = appDb.bookChapterDao.getChapterCount(updatedBook.bookUrl)
                        loadBookProgress(updatedBook, memory, sessions, chapters)
                    }
                } else {
                    // 只加载阅读进度数据，不加载字数和分类
                    if (updatedBook != null) {
                        loadReadingProgressOnly(updatedBook)
                    }
                }
                if (updatedBook != null) {
                    loadAnnotations(updatedBook)
                    loadReviews(updatedBook)
                }
                // 无论是否允许更新，都需要重新加载标签，否则标签不会显示
                loadBookTags()
            } catch (e: Exception) {
                // 静默处理错误，避免在后台更新时显示错误提示
            }
        }
    }
    
    private fun loadBookData() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    isLoading.value = true
                }
                
                // 并行加载所有基础数据，减少数据库查询次数
                val dataResult = withContext(IO) {
                    runCatching {
                        val bookDeferred = async {
                            appDb.bookDao.getBook(bookUrl)
                        }
                        val memoryDeferred = async {
                            appDb.readingMemoryDao.getByBookUrl(bookUrl)
                        }
                        val sessionsDeferred = async {
                            val b = bookDeferred.await()
                            if (b != null) {
                                appDb.readSessionDao.getByBook(b.name, b.author)
                            } else {
                                emptyList()
                            }
                        }
                        val chaptersDeferred = async {
                            appDb.bookChapterDao.getChapterCount(bookUrl)
                        }
                        val annotationsDeferred = async {
                            val b = bookDeferred.await()
                            if (b != null) {
                                appDb.bookAnnotationDao.getByBook(b.name, b.author)
                            } else {
                                emptyList()
                            }
                        }
                        val reviewsDeferred = async {
                            appDb.bookReviewDao.getReviewByBookUrl(bookUrl)
                        }
                        val tagRelationsDeferred = async {
                            appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
                        }
                        val excludedTagsDeferred = async {
                            appDb.excludedTagDao.getAllSync()
                        }
                        
                        // 等待所有异步操作完成
                        Octet(
                            bookDeferred.await(),
                            memoryDeferred.await(),
                            sessionsDeferred.await(),
                            chaptersDeferred.await(),
                            annotationsDeferred.await(),
                            reviewsDeferred.await(),
                            tagRelationsDeferred.await(),
                            excludedTagsDeferred.await()
                        )
                    }
                }
                
                val (deconstructedBook, memory, sessions, chapters, annotations, reviews, tagRelations, excludedTags) = dataResult.getOrElse {
                    val errorMsg = "数据加载失败: ${it.localizedMessage}"
                    Log.e("ReadingDetailVM", errorMsg, it)
                    withContext(Dispatchers.Main) {
                        context.toastOnUi(errorMsg)
                    }
                    // 返回默认值，继续执行但显示空数据
                    Octet(null, null, emptyList(), 0, emptyList(), emptyList(), emptyList(), emptyList())
                }
                
                val book = deconstructedBook ?: run {
                    val errorMsg = "书籍不存在或已被删除"
                    Log.e("ReadingDetailVM", errorMsg)
                    withContext(Dispatchers.Main) {
                        context.toastOnUi(errorMsg)
                        isLoading.value = false
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    bookData.value = book
                }
                
                // 并行处理数据，每个数据处理完成后立即更新UI
                    val processingResults = withContext(IO) {
                        runCatching {
                            val deferredProgress = async {
                                loadBookProgress(book, memory, sessions, chapters)
                            }
                            
                            val deferredAnnotations = async {
                                processAnnotations(annotations)
                            }
                            
                            val deferredReviews = async {
                                processReviews(reviews)
                            }
                            
                            val deferredTags = async {
                                processBookTags(tagRelations, excludedTags)
                            }
                            
                            val deferredSessions = async {
                                loadReadingSessions(book)
                            }
                            
                            // 等待所有数据处理完成
                            awaitAll(deferredProgress, deferredAnnotations, deferredReviews, deferredTags, deferredSessions)
                        }
                    }
                
                processingResults.onFailure {
                    val errorMsg = "数据处理失败: ${it.localizedMessage}"
                    Log.e("ReadingDetailVM", errorMsg, it)
                    withContext(Dispatchers.Main) {
                        context.toastOnUi(errorMsg)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，清理状态
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
                throw e
            } catch (e: Exception) {
                val errorMsg = "加载书籍详情失败: ${e.localizedMessage}"
                Log.e("ReadingDetailVM", errorMsg, e)
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    context.toastOnUi(errorMsg)
                }
            }
        }
    }
    
    // 数据类定义，用于并行返回多个值
    private data class Octet<A, B, C, D, E, F, G, H>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G,
        val eighth: H
    )
    
    private suspend fun loadBookProgress(book: Book, memory: ReadingMemory?, sessions: List<ReadSession>, totalChapters: Int) {
        try {
            // 并行计算所有数据，提高性能
            val (totalTime, days, lastTime, progressValue, startTime, readChapters, 
                 formattedWordCount, kind, maxDayReadData, readWordsData) = withContext(Dispatchers.IO) {
                val totalTimeDeferred = async { calculateTotalReadingTime(sessions) }
                val daysDeferred = async { calculateReadingDays(sessions) }
                val lastTimeDeferred = async { calculateLastReadTime(book, sessions) }
                val progressValueDeferred = async { ReadingProgressHelper.calculateReadingProgress(book) }
                val startTimeDeferred = async { calculateStartReadingTime(book, sessions) }
                val readChaptersDeferred = async { calculateReadChapters(book, totalChapters) }
                val wordCountDeferred = async { formatWordCountForDisplay(book.wordCount) }
                val kindDeferred = async { book.kind ?: "无" }
                val maxDayReadDataDeferred = async { calculateMaxDayReadData(sessions) }
                val readWordsDataDeferred = async { calculateReadWords(book, progressValueDeferred.await()) }
                
                // 等待所有异步操作完成
                Decuplet(
                    totalTimeDeferred.await(),
                    daysDeferred.await(),
                    lastTimeDeferred.await(),
                    progressValueDeferred.await(),
                    startTimeDeferred.await(),
                    readChaptersDeferred.await(),
                    wordCountDeferred.await(),
                    kindDeferred.await(),
                    maxDayReadDataDeferred.await(),
                    readWordsDataDeferred.await()
                )
            }
            
            // 统一更新UI，减少线程切换次数
            withContext(Dispatchers.Main) {
                // 更新总阅读时间
                val hours = totalTime / 60
                val mins = totalTime % 60
                totalReadingTime.value = formatReadingTime(totalTime)
                totalReadingHours.value = hours
                totalReadingMinutes.value = mins
                
                // 更新阅读天数
                readingDays.value = "$days"
                
                // 更新最近阅读时间
                lastReadTime.value = lastTime
                
                // 更新阅读进度
                val progress = progressValue.toInt().toString()
                readingProgress.value = progress
                
                // 更新开始阅读时间
                startReadingTime.value = startTime
                
                // 更新已读章节数
                this@BookReadingDetailViewModel.readChapters.value = readChapters
                
                // 更新字数和分类
                wordCount.value = formattedWordCount
                bookKind.value = kind
                
                // 更新单日阅读最久
                val (maxDayReadTime, maxDayDate) = maxDayReadData
                val maxHours = maxDayReadTime / 60
                val maxMins = maxDayReadTime % 60
                maxDayReadHours.value = maxHours
                maxDayReadMinutes.value = maxMins
                maxDayReadDate.value = maxDayDate
                
                // 更新阅读总字数和剩余字数
                val (readWords, remaining) = readWordsData
                totalReadWords.value = readWords
                remainingWords.value = "剩余字数${remaining}万字"
            }
            
            // 根据阅读进度自动更新阅读状态（如果用户没有手动修改过）
            if (memory?.userModifiedReadingStatus != true) {
                val progressInt = progressValue.toInt()
                updateReadingStatusByProgress(book, progressInt)
            }
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "Error loading book progress: ${e.localizedMessage}", e)
            // 保持UI状态稳定，不影响其他功能
        }
    }
    
    // 数据类定义，用于并行返回多个值
    private data class Decuplet<A, B, C, D, E, F, G, H, I, J>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G,
        val eighth: H,
        val ninth: I,
        val tenth: J
    )
    
    /**
     * 格式化字数显示
     */
    private fun formatWordCountForDisplay(wordCountStr: String?): String {
        if (wordCountStr.isNullOrEmpty()) return "无"
        
        val totalWords = parseTotalWords(wordCountStr)
        return "${formatWordCount(totalWords)}万字"
    }
    
    /**
     * 根据阅读进度自动更新阅读状态
     */
    private fun updateReadingStatusByProgress(book: Book, progress: Int) {
        viewModelScope.launch {
            try {
                // 使用ReadingProgressHelper计算阅读状态，考虑书籍的实际阅读情况
                val newStatus = ReadingProgressHelper.calculateReadingStatus(book)
                
                // 更新书籍状态
                val updatedBook = book.copy()
                updatedBook.setReadingStatus(newStatus)
                
                // 如果状态有变化，更新书籍状态
                if (book.getReadingStatusEnum() != updatedBook.getReadingStatusEnum()) {
                    withContext(Dispatchers.IO) {
                        appDb.bookDao.update(updatedBook)
                    }
                    
                    withContext(Dispatchers.Main) {
                        bookData.value = updatedBook
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 仅加载阅读进度数据，不包括字数和分类
     * 用于更换书源后的更新
     */
    private suspend fun loadReadingProgressOnly(book: Book) {
        // 获取书籍的阅读会话
        val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
        
        // 计算总阅读时间（分钟）
        val totalTime = calculateTotalReadingTime(sessions)
        withContext(Dispatchers.Main) {
            totalReadingTime.value = formatReadingTime(totalTime)
        }
        
        // 计算阅读天数
        val days = calculateReadingDays(sessions)
        withContext(Dispatchers.Main) {
            readingDays.value = "${days}天"
        }
        
        // 获取最近阅读时间
        val lastTime = calculateLastReadTime(book, sessions)
        withContext(Dispatchers.Main) {
            lastReadTime.value = lastTime
        }
        
        // 计算阅读进度百分比
        val progress = calculateReadingProgress(book)
        withContext(Dispatchers.Main) {
            readingProgress.value = progress
        }
        
        // 更新字数和分类信息，确保UI上显示正确的信息
        // 即使是更换到非正版书源，也需要显示原有的字数和分类
        withContext(Dispatchers.Main) {
            // 更新字数信息，转换为万字单位
            val bookWordCount = book.wordCount
            val formattedWordCount = if (bookWordCount.isNullOrEmpty()) "无" else {
                val totalWords = parseTotalWords(bookWordCount)
                "${formatWordCount(totalWords)}万字"
            }
            wordCount.value = formattedWordCount
            // 更新分类信息
            bookKind.value = if (book.kind.isNullOrEmpty()) "无" else book.kind
        }
    }
    
    private suspend fun calculateTotalReadingTime(sessions: List<ReadSession>): Long {
        // 直接使用传入的sessions列表计算总阅读时间，避免重复查询数据库
        return withContext(Dispatchers.IO) {
            if (sessions.isNotEmpty()) {
                // 计算所有会话的总时长（毫秒），转换为分钟
                return@withContext sessions.sumOf { it.duration } / 60000
            } else {
                // 如果sessions为空，使用readSession表查询
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext 0L
                
                var totalTime = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
                if (totalTime == 0L) {
                    totalTime = appDb.readSessionDao.getTotalReadTime(book.name) ?: 0L
                }
                if (totalTime == 0L) {
                    totalTime = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl) ?: 0L
                }
                
                return@withContext totalTime / 60000
            }
        }
    }
    
    private suspend fun calculateReadingDays(sessions: List<ReadSession>): Int {
        return withContext(Dispatchers.IO) {
            try {
                if (sessions.isNotEmpty()) {
                    // 从阅读会话中计算阅读天数
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
                    val uniqueDays = sessions.map { 
                        Instant.ofEpochMilli(it.startTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(dateFormatter)
                    }.toSet()
                    return@withContext uniqueDays.size
                }
                
                // 如果没有阅读会话，检查是否有阅读记录
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext 0
                val readSessions = appDb.readSessionDao.getByBook(book.name, book.author)
                if (readSessions.isNotEmpty()) {
                    // 如果有ReadSession，说明有阅读记录，至少1天
                    return@withContext 1
                }
                
                // 没有阅读记录
                0
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }
    
    private suspend fun calculateLastReadTime(book: Book, sessions: List<ReadSession>): String {
        return withContext(Dispatchers.IO) {
            try {
                // 优先使用阅读会话的结束时间
                val sessionLastTime = sessions.maxByOrNull { it.endTime }?.let { 
                    it.endTime ?: it.startTime
                }
                
                if (sessionLastTime != null) {
                    return@withContext formatLastReadTime(sessionLastTime)
                }
                
                // 如果没有阅读会话，使用书籍的最后阅读时间
                return@withContext formatLastReadTime(book.durChapterTime)
            } catch (e: Exception) {
                e.printStackTrace()
                formatLastReadTime(0L)
            }
        }
    }
    
    private fun formatLastReadTime(timestamp: Long): String {
        if (timestamp == 0L) {
            return "未阅读"
        }
        
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        // 转换为天
        val days = diff / (24 * 60 * 60 * 1000)
        
        return when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days <= 7L -> "${days}天前"
            else -> {
                // 超过7天的显示具体日期
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.getDefault())
                Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(dateFormatter)
            }
        }
    }
    
    private suspend fun calculateStartReadingTime(book: Book, sessions: List<ReadSession>): String {
        return withContext(Dispatchers.IO) {
            try {
                var firstReadTime: Long? = null
                
                // 1. 优先使用书籍专门的首次阅读时间字段
                if (book.firstReadTime > 0L) {
                    firstReadTime = book.firstReadTime
                } else {
                    // 2. 使用传入的sessions列表获取最早阅读时间
                    if (sessions.isNotEmpty()) {
                        firstReadTime = sessions.minByOrNull { it.startTime }?.startTime
                    }
                    
                    // 3. 如果没有ReadSession记录，使用书籍的创建时间
                    if (firstReadTime == null) {
                        firstReadTime = book.durChapterTime
                    }
                    
                    // 4. 最后使用书籍的最后阅读时间作为回退方案
                    firstReadTime = firstReadTime ?: book.durChapterTime
                    
                    // 5. 保存计算出的首次阅读时间到数据库，避免下次计算时再次变化
                    if (firstReadTime > 0L && book.firstReadTime == 0L) {
                        // 更新Book对象的firstReadTime字段
                        val updatedBook = book.copy(firstReadTime = firstReadTime)
                        appDb.bookDao.update(updatedBook)
                        
                        // 同时更新对应的ReadingMemory
                        val readingMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                        if (readingMemory != null) {
                            val updatedMemory = readingMemory.copy(firstReadTime = firstReadTime)
                            appDb.readingMemoryDao.update(updatedMemory)
                        }
                    }
                }
                
                if (firstReadTime == 0L) {
                    "未开始阅读"
                } else {
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.getDefault())
                    val formattedDate = Instant.ofEpochMilli(firstReadTime)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(dateFormatter)
                    "始于" + formattedDate
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "未开始阅读"
            }
        }
    }
    
    private fun calculateReadChapters(book: Book, totalChapters: Int): String {
        // 处理总章节数为0的情况
        val safeTotalChapters = maxOf(1, totalChapters)
        
        // 已读章节数，这里假设章节索引从0开始
        // 当durChapterIndex为0且未开始阅读时，应该显示0/xx
        val currentChapter = if (book.durChapterIndex == 0 && book.durChapterTime == 0L) {
            // 未开始阅读，显示0章
            0
        } else {
            // 已开始阅读，章节索引+1，确保不超过总章节数
            minOf(safeTotalChapters, book.durChapterIndex + 1)
        }
        return "$currentChapter/$safeTotalChapters"
    }
    
    private fun formatReadingTime(minutes: Long): String {
        // 只返回纯数值，单位由布局文件显示
        return "$minutes"
    }
    
    /**
     * 计算单日阅读最久的时间和日期
     */
    private suspend fun calculateMaxDayReadData(sessions: List<ReadSession>): Pair<Long, String> {
        return withContext(Dispatchers.IO) {
            try {
                if (sessions.isNotEmpty()) {
                    // 按日期分组计算每天的阅读时间
                    val dailyReadTimeMap = calculateDailyReadTime(sessions)
                    
                    // 找出最大阅读时间对应的日期和时间
                    val maxEntry = dailyReadTimeMap.entries.maxByOrNull { it.value }
                    if (maxEntry != null) {
                        val maxTimeMinutes = maxEntry.value / 60000
                        
                        // 转换为yyyy年MM月dd日格式
                        val originalFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
                        val targetFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.getDefault())
                        val formattedDate = LocalDate.parse(maxEntry.key, originalFormatter)
                            .format(targetFormatter)
                        
                        return@withContext Pair(maxTimeMinutes, formattedDate)
                    }
                } else {
                    // 处理旧版本备份数据：如果ReadSession中没有数据，使用书籍的总阅读时间
                    val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext Pair(0L, "暂无数据")
                    val totalTime = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
                    // 转换为分钟
                    val maxTimeMinutes = totalTime / 60000
                    return@withContext Pair(maxTimeMinutes, "暂无数据")
                }
                
                return@withContext Pair(0L, "暂无数据")
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(0L, "暂无数据")
            }
        }
    }
    
    /**
     * 计算每天的阅读时间（毫秒）
     */
    private fun calculateDailyReadTime(sessions: List<ReadSession>): Map<String, Long> {
        val dailyReadTimeMap = mutableMapOf<String, Long>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        
        for (session in sessions) {
            val date = Instant.ofEpochMilli(session.startTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(dateFormatter)
            
            val existingTime = dailyReadTimeMap.getOrDefault(date, 0L)
            dailyReadTimeMap[date] = existingTime + session.duration
        }
        
        return dailyReadTimeMap
    }
    
    /**
     * 计算阅读总字数和剩余字数
     */
    private fun calculateReadWords(book: Book, progress: Float): Pair<String, String> {
        try {
            // 直接从book对象获取总字数
            val totalWordsStr = book.wordCount ?: ""
            
            // 解析总字数，处理不同格式（如"50000字"和"5万字"）
            val totalWords = parseTotalWords(totalWordsStr)
            
            // 处理总字数为0的情况
            if (totalWords == 0L) {
                return Pair("0", "0")
            }
            
            // 验证进度值，确保在0-100之间
            val validProgress = when {
                progress < 0f -> 0f
                progress > 100f -> 100f
                else -> progress
            }
            
            // 计算已读字数：阅读进度 * 总字数 / 100
            val readWords = (totalWords * validProgress / 100).toLong()
            // 确保已读字数不超过总字数，且不为负数
            val validReadWords = maxOf(0L, minOf(totalWords, readWords))
            
            // 计算剩余字数：总字数 - 已读字数
            val remaining = totalWords - validReadWords
            // 确保剩余字数不为负数
            val validRemaining = maxOf(0L, remaining)
            
            // 格式化显示为万字并保留两位小数
            val formattedReadWords = String.format("%.2f", validReadWords.toDouble() / 10000)
            val formattedRemaining = String.format("%.2f", validRemaining.toDouble() / 10000)
            
            return Pair(formattedReadWords, formattedRemaining)
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "calculateReadWords error: ${e.localizedMessage}", e)
            return Pair("0", "0")
        }
    }
    
    /**
     * 解析总字数，处理不同格式
     * 支持格式：
     * - 50000字
     * - 5万字
     * - 50000
     * - 5万
     */
    private fun parseTotalWords(wordCountStr: String): Long {
        try {
            // 提取纯数字和单位
            val cleanStr = wordCountStr.trim()
            
            if (cleanStr.isBlank()) {
                return 0L
            }
            
            // 检查是否包含"万"字
            if (cleanStr.contains("万", ignoreCase = true)) {
                // 提取数字部分
                val numberStr = cleanStr.replace(Regex("[^0-9.]", RegexOption.IGNORE_CASE), "")
                val number = numberStr.toDoubleOrNull()
                
                if (number == null || number < 0) {
                    Log.w("ReadingDetailVM", "Invalid word count format: $wordCountStr")
                    return 0L
                }
                
                // 转换为实际数字（乘以10000），并确保结果为正数
                return maxOf(0L, (number * 10000).toLong())
            } else {
                // 不包含"万"字，直接提取数字
                val numberStr = cleanStr.replace(Regex("[^0-9]", RegexOption.IGNORE_CASE), "")
                val number = numberStr.toLongOrNull()
                
                if (number == null || number < 0) {
                    Log.w("ReadingDetailVM", "Invalid word count format: $wordCountStr")
                    return 0L
                }
                
                // 确保结果为正数
                return maxOf(0L, number)
            }
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "Error parsing word count: $wordCountStr", e)
            return 0L
        }
    }
    
    /**
     * 格式化字数显示
     */
    private fun formatWordCount(wordCount: Long): String {
        return when {
            wordCount >= 10000 -> String.format("%.2f", wordCount.toDouble() / 10000)
            else -> "$wordCount"
        }
    }
    
    private fun calculateReadingProgress(book: Book): String {
        // 获取数值型进度（0-100）
        val progressValue = ReadingProgressHelper.calculateReadingProgress(book)
        // 只返回纯数值，单位由布局文件显示
        return "${progressValue.toInt()}"
    }
    
    private suspend fun loadAnnotations(book: Book) {
        try {
            // 在IO线程中执行数据库查询
            val annotations: List<BookAnnotation> = withContext(Dispatchers.IO) {
                // 从bookAnnotationDao获取书摘数据，而不是bookmarkDao
                appDb.bookAnnotationDao.getByBook(book.name, book.author)
            }
            
            // 将BookAnnotation转换为Bookmark，因为AnnotationAdapter目前使用Bookmark
            val bookmarks = annotations.map { annotation ->
                io.legado.app.data.entities.Bookmark(
                    time = annotation.time,
                    bookName = annotation.bookName,
                    bookAuthor = annotation.bookAuthor,
                    chapterIndex = annotation.chapterIndex,
                    chapterPos = annotation.chapterPos,
                    chapterName = annotation.chapterName,
                    bookText = annotation.bookText,
                    content = annotation.content
                )
            }
            
            // 计算带有笔记的书摘数量（想法笔记数量）
            val annotationsWithNotes = annotations.count { it.content?.isNotBlank() == true }
            
            withContext(Dispatchers.Main) {
                this@BookReadingDetailViewModel.annotations.value = bookmarks
                this@BookReadingDetailViewModel.annotationCount.value = annotations.size
                // 想法笔记数量：带有笔记的书摘数量
                this@BookReadingDetailViewModel.reviewCount.value = annotationsWithNotes
            }
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "Error loading annotations: ${e.localizedMessage}", e)
            // 发生异常时，将书摘列表清空，避免显示错误数据
            withContext(Dispatchers.Main) {
                this@BookReadingDetailViewModel.annotations.value = emptyList()
                this@BookReadingDetailViewModel.annotationCount.value = 0
                this@BookReadingDetailViewModel.reviewCount.value = 0
            }
        }
    }

    private suspend fun loadReadingSessions(book: Book) {
        // 获取该书籍的所有阅读会话
        val sessions = withContext(Dispatchers.IO) {
            appDb.readSessionDao.getByBook(book.name, book.author)
        }
        
        if (sessions.isEmpty()) {
            // 如果没有阅读会话，返回空列表
            withContext(Dispatchers.Main) {
                monthlyReadingSessions.value = emptyList()
            }
            return
        }
        
        // 按月份分组（使用yyyy-MM格式作为键，确保排序正确）
        val monthlySessions = sessions.groupBy { session ->
            val date = java.util.Date(session.startTime)
            java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(date)
        }
        
        // 构建月度阅读会话数据
        val monthSessions = monthlySessions.map { (monthKey, monthSessionList) ->
            // 计算该月总阅读时间
            val totalReadTime = monthSessionList.sumOf { it.duration }
            
            // 转换月份键为显示格式（yyyy年MM月）
            val monthTitle = try {
                val date = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).parse(monthKey)
                if (date != null) {
                    java.text.SimpleDateFormat("yyyy年MM月", java.util.Locale.getDefault()).format(date)
                } else {
                    monthKey
                }
            } catch (e: Exception) {
                monthKey // 解析失败时使用原始月份键
            }
            
            // 按日期分组（使用yyyy-MM-dd格式作为键，确保排序正确）
            val dailySessions = monthSessionList.groupBy { session ->
                val date = java.util.Date(session.startTime)
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(date)
            }
            
            // 构建每日阅读会话数据
            val daySessions = dailySessions.map { (dateKey, daySessionList) ->
                // 计算该日总阅读时间
                val dayReadTime = daySessionList.sumOf { it.duration }
                // 转换日期格式为MM月dd日用于显示
                val displayDate = try {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateKey)
                    if (date != null) {
                        java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault()).format(date)
                    } else {
                        dateKey
                    }
                } catch (e: Exception) {
                    dateKey // 解析失败时使用原始日期键
                }
                DailyReadingSession(displayDate, dayReadTime)
            }.sortedBy { it.date } // 按日期排序
            
            MonthReadingSession(monthTitle, totalReadTime, daySessions)
        }.sortedByDescending { it.monthTitle } // 按月份倒序排序
        
        withContext(Dispatchers.Main) {
            monthlyReadingSessions.value = monthSessions
        }
    }
    
    suspend fun loadReviews(book: Book) {
        try {
            // 在IO线程中执行数据库查询
            val reviewList: List<BookReview> = withContext(Dispatchers.IO) {
                // 从数据库获取书评
                appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
            }
            
            // 更新书评列表，不更新reviewCount，因为reviewCount是带有笔记的书摘数量
            withContext(Dispatchers.Main) {
                reviews.value = reviewList
            }
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "Error loading reviews: ${e.localizedMessage}", e)
            // 发生异常时，将书评列表清空，避免显示错误数据
            withContext(Dispatchers.Main) {
                reviews.value = emptyList()
            }
        }
    }
    
    /**
     * 处理书摘数据，更新UI
     */
    private suspend fun processAnnotations(annotations: List<BookAnnotation>) {
        try {
            // 转换为Bookmark列表
            val bookmarks = annotations.map { annotation ->
                io.legado.app.data.entities.Bookmark(
                    time = annotation.time,
                    bookName = annotation.bookName,
                    bookAuthor = annotation.bookAuthor,
                    chapterIndex = annotation.chapterIndex,
                    chapterPos = annotation.chapterPos,
                    chapterName = annotation.chapterName,
                    bookText = annotation.bookText,
                    content = annotation.content
                )
            }
            
            // 计算带有笔记的书摘数量（想法笔记数量）
            val annotationsWithNotes = annotations.count { it.content?.isNotBlank() == true }
            
            withContext(Dispatchers.Main) {
                this@BookReadingDetailViewModel.annotations.value = bookmarks
                this@BookReadingDetailViewModel.annotationCount.value = annotations.size
                // 想法笔记数量：带有笔记的书摘数量
                this@BookReadingDetailViewModel.reviewCount.value = annotationsWithNotes
            }
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "Error processing annotations: ${e.localizedMessage}", e)
            // 发生异常时，将书摘列表清空，避免显示错误数据
            withContext(Dispatchers.Main) {
                this@BookReadingDetailViewModel.annotations.value = emptyList()
                this@BookReadingDetailViewModel.annotationCount.value = 0
                this@BookReadingDetailViewModel.reviewCount.value = 0
            }
        }
    }
    
    /**
     * 处理书评数据，更新UI
     */
    private suspend fun processReviews(reviews: List<BookReview>) {
        try {
            // 直接更新书评列表
            withContext(Dispatchers.Main) {
                this@BookReadingDetailViewModel.reviews.value = reviews
            }
        } catch (e: Exception) {
            Log.e("ReadingDetailVM", "Error processing reviews: ${e.localizedMessage}", e)
            // 发生异常时，将书评列表清空，避免显示错误数据
            withContext(Dispatchers.Main) {
                this@BookReadingDetailViewModel.reviews.value = emptyList()
            }
        }
    }
    
    /**
     * 处理书籍标签数据，更新UI
     */
    private suspend fun processBookTags(tagRelations: List<BookTagRelation>, excludedTags: List<ExcludedTag>) {
        try {
            val excludedTagNames = excludedTags.map { it.name }.toSet()
            
            // 获取标签详情
            val tags: List<BookTag> = withContext(Dispatchers.IO) {
                if (tagRelations.isNotEmpty()) {
                    val tagIds = tagRelations.map { it.tagId }
                    val allTags = appDb.bookTagDao.getTagsByIds(tagIds)
                    
                    // 过滤掉被排除的标签
                    val filteredTags = allTags.filter { tag -> 
                        !excludedTagNames.contains(tag.name)
                    }
                    
                    // 获取所有标签分组，按排序顺序
                    val tagGroups = appDb.bookTagGroupDao.getAllSorted()
                    val groupSortOrder = tagGroups.associateBy({ it.id }, { it.sortOrder })
                    
                    // 按分组排序，同一分组内按名称排序
                    filteredTags.sortedWith(compareBy<BookTag> {
                        // 先按分组排序，未分组的放最后
                        groupSortOrder[it.groupId] ?: Int.MAX_VALUE
                    }.thenBy { 
                        // 同一分组内按名称排序
                        it.name
                    })
                } else {
                    emptyList()
                }
            }
            
            // 检查书源是否属于正版分组
            val isOfficialSource = withContext(Dispatchers.IO) {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext false
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                bookSource?.bookSourceGroup?.contains("正版") == true
            }
            
            // 如果是正版书源，从kind字段生成标签
            var finalTags = tags
            if (isOfficialSource) {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@processBookTags
                if (!book.kind.isNullOrEmpty()) {
                    finalTags = withContext(Dispatchers.IO) {
                        // 处理分类信息，添加到标签中
                        val kindTagsList = mutableListOf<BookTag>()
                        kindTagsList.addAll(tags)
                        
                        // 从书籍分类中生成标签
                        // 解析kind字段为标签列表，支持多种分隔符
                        val allKindTags = book.kind!!.split(Regex("[,|\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                        
                        // 过滤标签，应用与其他地方相同的过滤规则
                        val filteredKindTags = allKindTags.filter { tag ->
                            // 排除"完结"和"连载"标签
                            tag != "完结" && tag != "连载" &&
                            // 排除包含标点的标签（只允许中文、英文、数字）
                            tag.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$")) &&
                            // 排除包含数字的标签
                            !tag.any { char -> char.isDigit() } &&
                            // 限制标签长度不超过5个字符
                            tag.length <= 5 &&
                            // 排除被用户排除的标签
                            !excludedTagNames.contains(tag)
                        }.distinct()
                        
                        for (kind in filteredKindTags) {
                            // 检查标签是否已存在
                            val existingTag = tags.find { it.name == kind }
                            if (existingTag == null) {
                                // 不存在则创建新标签
                                    val newTag = BookTag(
                                        name = kind,
                                        color = generateRandomDarkColor(), // 使用随机深色
                                        updateTime = System.currentTimeMillis()
                                    )
                                val tagId = appDb.bookTagDao.insert(newTag)
                                
                                // 创建关联关系
                                val newRelation = BookTagRelation(
                                    bookUrl = bookUrl,
                                    tagId = tagId
                                )
                                appDb.bookTagRelationDao.insert(newRelation)
                                
                                kindTagsList.add(newTag.copy(id = tagId))
                            }
                        }
                        
                        kindTagsList.distinctBy { it.id }
                    }
                }
            }
            
            // 按分组排序，同一分组内按名称排序
            finalTags = withContext(Dispatchers.IO) {
                val tagGroups = appDb.bookTagGroupDao.getAllSorted()
                val groupSortOrder = tagGroups.associateBy({ it.id }, { it.sortOrder })
                
                finalTags.sortedWith(compareBy<BookTag> {
                    // 先按分组排序，未分组的放最后
                    groupSortOrder[it.groupId] ?: Int.MAX_VALUE
                }.thenBy { 
                    // 同一分组内按名称排序
                    it.name
                })
            }
            
            // 更新UI
            withContext(Dispatchers.Main) {
                bookTags.value = finalTags
            }
        } catch (e: Exception) {
            Log.e("BookReadDetailVM", "处理书籍标签失败", e)
            withContext(Dispatchers.Main) {
                bookTags.value = emptyList()
            }
        }
    }
    
    fun refreshReviews(book: Book) {
        viewModelScope.launch {
            loadReviews(book)
        }
    }
    
    fun updateBookRating(rating: Float) {
        viewModelScope.launch {
            try {
                val book = bookData.value ?: return@launch
                
                // 使用BookInfoSyncHelper统一更新书籍和我的阅读记录的评分
                // 这会自动设置用户修改标记
                BookInfoSyncHelper.updateBookRating(book.bookUrl, rating)
                
                // 重新加载书籍数据以获取更新后的信息
                val updatedBook = withContext(IO) {
                    appDb.bookDao.getBook(book.bookUrl)
                }
                
                withContext(Dispatchers.Main) {
                    if (updatedBook != null) {
                        bookData.value = updatedBook
                    }
                    context.toastOnUi("评分更新成功")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.toastOnUi("更新评分失败: ${e.localizedMessage}")
                }
            }
        }
    }
    
    fun updateBookIntro(book: Book, intro: String) {
        viewModelScope.launch {
            try {
                // 使用BookInfoSyncHelper统一更新书籍和我的阅读记录的简介
                // 这会自动设置用户修改标记
                BookInfoSyncHelper.updateBookIntro(book.bookUrl, intro)
                
                // 重新加载书籍数据以获取更新后的信息
                val updatedBook = withContext(IO) {
                    appDb.bookDao.getBook(book.bookUrl)
                }
                
                withContext(Dispatchers.Main) {
                    if (updatedBook != null) {
                        bookData.value = updatedBook
                    }
                    context.toastOnUi("简介更新成功")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.toastOnUi("更新简介失败: ${e.localizedMessage}")
                }
            }
        }
    }
    
    fun addAnnotation(bookmark: Bookmark) {
        viewModelScope.launch {
            // 将Bookmark转换为BookAnnotation并保存到数据库
            val bookAnnotation = io.legado.app.data.entities.BookAnnotation(
                time = bookmark.time,
                bookName = bookmark.bookName,
                bookAuthor = bookmark.bookAuthor,
                chapterIndex = bookmark.chapterIndex,
                chapterPos = bookmark.chapterPos,
                chapterName = bookmark.chapterName ?: "",
                bookText = bookmark.bookText ?: "",
                content = bookmark.content ?: ""
            )
            
            // 保存到数据库
            appDb.bookAnnotationDao.insert(bookAnnotation)
            
            // 重新加载书摘
            val book = bookData.value ?: return@launch
            loadAnnotations(book)
        }
    }
    
    fun updateAnnotation(bookmark: Bookmark, newContent: String) {
        viewModelScope.launch {
            // 获取原始书摘
            val bookName = bookmark.bookName
            val bookAuthor = bookmark.bookAuthor
            val time = bookmark.time
            
            // 从数据库查找对应的BookAnnotation
            val originalAnnotation = appDb.bookAnnotationDao.getByTime(bookName, bookAuthor, time)
            
            if (originalAnnotation != null) {
                // 更新书摘内容
                val updatedAnnotation = originalAnnotation.copy(content = newContent)
                appDb.bookAnnotationDao.update(updatedAnnotation)
                
                // 重新加载书摘
                val book = bookData.value ?: return@launch
                loadAnnotations(book)
            }
        }
    }
    
    /**
     * 更新书籍信息后，同步更新所有相关的书摘和阅读会话
     * @param oldBookName 原书籍名称
     * @param oldBookAuthor 原作者名称
     * @param newBookName 新书籍名称
     * @param newBookAuthor 新作者名称
     */
    fun updateDataForBookChange(oldBookName: String, oldBookAuthor: String, newBookName: String, newBookAuthor: String) {
        viewModelScope.launch {
            // 1. 更新所有相关的书摘
            val annotations = appDb.bookAnnotationDao.getByBook(oldBookName, oldBookAuthor)
            
            // 更新每个书摘的书籍名称和作者
            annotations.forEach { annotation ->
                val updatedAnnotation = annotation.copy(
                    bookName = newBookName,
                    bookAuthor = newBookAuthor
                )
                appDb.bookAnnotationDao.update(updatedAnnotation)
            }
            
            // 2. 更新所有相关的阅读会话
            val sessions = appDb.readSessionDao.getByBook(oldBookName, oldBookAuthor)
            
            // 更新每个阅读会话的书籍名称和作者
            sessions.forEach { session ->
                val updatedSession = session.copy(
                    bookName = newBookName,
                    author = newBookAuthor
                )
                appDb.readSessionDao.update(updatedSession)
            }
            
            // 3. 重新加载数据
            val book = bookData.value ?: return@launch
            loadAnnotations(book)
        }
    }
    
    fun addReview(review: BookReview) {
        viewModelScope.launch {
            // 更新书评列表
            val currentReviews = reviews.value ?: emptyList()
            val newReviews = currentReviews.toMutableList()
            newReviews.add(review)
            withContext(Dispatchers.Main) {
                reviews.value = newReviews
            }
        }
    }

    /**
     * 手动刷新书籍信息（字数和分类）
     */
    fun refreshBookInfo() {
        viewModelScope.launch {
            try {
                val book = bookData.value ?: return@launch
                
                // 获取字数信息并转换为万字单位
                val wc = book.wordCount
                withContext(Dispatchers.Main) {
                    val formattedWordCount = if (wc.isNullOrEmpty()) "无" else {
                        val totalWords = parseTotalWords(wc)
                        "${formatWordCount(totalWords)}万字"
                    }
                    wordCount.value = formattedWordCount
                }
                
                // 获取分类信息
                val kind = book.kind
                withContext(Dispatchers.Main) {
                    bookKind.value = if (kind.isNullOrEmpty()) "无" else kind
                }
                
                withContext(Dispatchers.Main) {
                    context.toastOnUi("书籍信息已刷新")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.toastOnUi("刷新失败: ${e.localizedMessage}")
                }
            }
        }
    }
    
    /**
     * 更新阅读进度数据
     * 这个方法可以在阅读会话更新时调用，以实时更新UI
     */
    fun updateReadingProgress() {
        viewModelScope.launch {
            try {
                val book = bookData.value ?: return@launch
                val memory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                val chapters = appDb.bookChapterDao.getChapterCount(book.bookUrl)
                loadBookProgress(book, memory, sessions, chapters)
            } catch (e: Exception) {
                // 静默处理错误，避免在后台更新时显示错误提示
            }
        }
    }
    
    /**
     * 加载书籍标签
     * 兼容原有标签管理功能，同时与分类信息和字数的处理逻辑保持一致
     * - 正版书源：从kind字段生成标签，并保存到数据库
     * - 非正版书源：从数据库加载原有标签，不从kind字段生成新标签
     */
    fun loadBookTags() {
        viewModelScope.launch {
            try {
                // 一次性获取所有需要的数据，减少数据库访问次数
                val (book, relations, excludedTags) = withContext(Dispatchers.IO) {
                    // 并行获取数据，提高效率
                    val bookDeferred = async {
                        appDb.bookDao.getBook(bookUrl)
                    }
                    val relationsDeferred = async {
                        appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
                    }
                    val excludedTagsDeferred = async {
                        appDb.excludedTagDao.getAllSync()
                    }
                    
                    // 等待所有异步操作完成
                    Triple(bookDeferred.await(), relationsDeferred.await(), excludedTagsDeferred.await())
                }
                
                val excludedTagNames = excludedTags.map { it.name }.toSet()
                
                // 获取标签详情
                var tags: List<BookTag> = withContext(Dispatchers.IO) {
                    if (relations.isNotEmpty()) {
                        val tagIds = relations.map { it.tagId }
                        val allTags = appDb.bookTagDao.getTagsByIds(tagIds)
                        
                        // 过滤掉被排除的标签
                        allTags.filter { tag -> 
                            !excludedTagNames.contains(tag.name)
                        }
                    } else {
                        emptyList()
                    }
                }
                
                // 检查书源是否属于正版分组
                val isOfficialSource = book?.let { bk ->
                    withContext(Dispatchers.IO) {
                        val bookSource = appDb.bookSourceDao.getBookSource(bk.origin)
                        bookSource?.bookSourceGroup?.contains("正版") == true
                    }
                } ?: false
                
                // 与分类信息和字数的处理逻辑保持一致
                // 正版书源：
                //   - 如果没有标签关联关系，从kind字段生成标签并保存到数据库
                //   - 如果已有标签关联关系，保留现有标签，不重新生成
                // 非正版书源：从数据库加载原有标签，不从kind字段生成新标签
                if (book != null && !book.kind.isNullOrBlank() && isOfficialSource && tags.isEmpty()) {
                    // 从kind字段中提取标签信息
                    val kindTags = book.kind!!.split(Regex("[,\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                    
                    // 应用与processKindTags相同的过滤规则
                    val filteredKindTags = kindTags.filter { tag ->
                        // 排除"完结"和"连载"标签
                        tag != "完结" && tag != "连载" &&
                        // 排除包含标点的标签（只允许中文、英文、数字）
                        tag.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$")) &&
                        // 排除包含数字的标签
                        !tag.any { char -> char.isDigit() } &&
                        // 限制标签长度不超过5个字符
                        tag.length <= 5
                    }.distinct()
                    
                    // 使用之前已经获取的排除标签列表，避免重复查询数据库
                    // 过滤掉被排除的标签
                    val finalKindTags = filteredKindTags.filter { tag -> 
                        !excludedTagNames.contains(tag)
                    }
                    
                    // 批量处理标签，减少数据库操作次数
                    val kindTagsList = withContext(Dispatchers.IO) {
                        // 批量查询现有标签
                        val existingTagsMap = finalKindTags.mapNotNull { appDb.bookTagDao.getTagByName(it) }
                            .associateBy { it.name }
                        
                        val newRelations = mutableListOf<BookTagRelation>()
                        val resultTags = mutableListOf<BookTag>()
                        
                        for (tagName in finalKindTags) {
                            var existingTag = existingTagsMap[tagName]
                            
                            if (existingTag == null) {
                                // 创建新标签
                                val newTag = BookTag(
                                    name = tagName,
                                    color = generateRandomDarkColor(), // 使用随机深色
                                    createTime = System.currentTimeMillis()
                                )
                                val tagId = appDb.bookTagDao.insert(newTag)
                                existingTag = newTag.copy(id = tagId)
                                resultTags.add(existingTag)
                            } else {
                                resultTags.add(existingTag)
                            }
                            
                            // 创建标签与书籍的关联关系
                            val relation = BookTagRelation(
                                id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                                bookUrl = book.bookUrl,
                                tagId = existingTag.id,
                                createTime = System.currentTimeMillis()
                            )
                            newRelations.add(relation)
                        }
                        
                        // 批量插入关联关系
                        if (newRelations.isNotEmpty()) {
                            appDb.bookTagRelationDao.insertAll(newRelations)
                        }
                        
                        resultTags
                    }
                    
                    // 更新标签列表
                    tags = kindTagsList.distinctBy { it.id }
                }
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    bookTags.value = tags
                }
            } catch (e: Exception) {
                Log.e("BookReadDetailVM", "加载书籍标签失败", e)
            }
        }
    }
    
    /**
     * 生成随机亮色，提高可见度
     */
    private fun generateRandomDarkColor(): Int {
        return TagColorUtils.generateRandomColor()
    }
    
    /**
     * 加载标签详情
     */
    suspend fun loadTagDetail(tagId: Long): BookTag? {
        return withContext(Dispatchers.IO) {
            try {
                appDb.bookTagDao.getTag(tagId)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 为书籍添加标签
     */
    fun addTagToBook(bookUrl: String, tagId: Long) {
        viewModelScope.launch {
            try {
                // 使用TagManager添加标签
                val success = TagManager.addTagToBook(bookUrl, tagId)
                
                if (success) {
                    context.toastOnUi("标签添加成功")
                    
                    // 重新加载书籍标签
                    loadBookTags()
                    
                    // 重新加载书籍数据以获取更新后的分类信息
                    val updatedBook: Book? = withContext(IO) {
                        appDb.bookDao.getBook(bookUrl)
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (updatedBook != null) {
                            bookData.value = updatedBook
                            // 更新分类的LiveData
                            bookKind.value = if (updatedBook.kind.isNullOrEmpty()) "无" else updatedBook.kind
                            // 发送书架更新事件，通知书架列表刷新标签
                            postEvent(EventBus.BOOKSHELF_REFRESH, "")
                            // 发送标签更新事件，通知阅读详情列表页面刷新标签
                            postEvent(EventBus.TAGS_UPDATED, bookUrl)
                        }
                    }
                } else {
                    context.toastOnUi("标签添加失败: 标签不存在或已添加")
                }
            } catch (e: Exception) {
                context.toastOnUi("添加标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 更新标签信息
     */
    fun updateTag(tag: BookTag) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appDb.bookTagDao.update(tag)
                }
                context.toastOnUi("标签更新成功")
                // 重新加载书籍标签
                loadBookTags()
            } catch (e: Exception) {
                context.toastOnUi("更新标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 从书籍移除标签
     */
    fun removeTagFromBook(tagId: Long) {
        viewModelScope.launch {
            try {
                // 使用TagManager移除标签
                val success = TagManager.removeTagFromBook(bookUrl, tagId)
                
                if (success) {
                    context.toastOnUi("标签移除成功")
                    
                    // 重新加载书籍标签，确保UI实时更新
                    loadBookTags()
                    
                    // 重新加载书籍数据以获取更新后的分类信息
                    val updatedBook: Book? = withContext(IO) {
                        appDb.bookDao.getBook(bookUrl)
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (updatedBook != null) {
                            bookData.value = updatedBook
                            // 更新分类的LiveData
                            bookKind.value = if (updatedBook.kind.isNullOrEmpty()) "无" else updatedBook.kind
                            // 发送书架更新事件，通知书架列表刷新标签
                            postEvent(EventBus.BOOKSHELF_REFRESH, "")
                            // 发送标签更新事件，通知阅读详情列表页面刷新标签
                            postEvent(EventBus.TAGS_UPDATED, bookUrl)
                        }
                    }
                } else {
                    context.toastOnUi("标签移除失败: 标签不存在或未关联")
                }
            } catch (e: Exception) {
                context.toastOnUi("移除标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 获取当前书籍已关联的标签ID列表
     */
    fun getAssociatedTagIds(bookUrl: String): List<Long> {
        return try {
            // 使用 runBlocking 来在非协程环境中调用挂起函数
            kotlinx.coroutines.runBlocking {
                withContext(Dispatchers.IO) {
                    // 获取标签关联关系
                    val relations = appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
                    relations.map { it.tagId }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 加载所有标签
     */
    fun loadAllTags() {
        viewModelScope.launch {
            try {
                val allTags: List<BookTag> = withContext(Dispatchers.IO) {
                    appDb.bookTagDao.getAll()
                }
                
                // 更新LiveData
                bookTags.postValue(allTags)
            } catch (e: Exception) {
                // 加载标签失败
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 创建新标签
     */
    fun createTag(tagName: String, color: Int = 0xFFE3F2FD.toInt()) {
        if (tagName.isBlank()) {
            return
        }
        
        viewModelScope.launch {
            try {
                // 检查标签名是否已存在
                val existingTag: BookTag? = withContext(Dispatchers.IO) {
                    appDb.bookTagDao.getTagByName(tagName)
                }
                
                if (existingTag != null) {
                    context.toastOnUi("标签已存在")
                    return@launch
                }
                
                // 创建新标签 - 使用辅助构造函数
                val newTag = BookTag(
                    name = tagName,
                    color = color, // 使用传入的颜色参数
                    createTime = System.currentTimeMillis()
                )
                
                withContext(Dispatchers.IO) {
                    appDb.bookTagDao.insert(newTag)
                }
                
                context.toastOnUi("标签创建成功")
            } catch (e: Exception) {
                context.toastOnUi("创建标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 处理从kind获取的标签，实现分割、过滤和随机深色颜色功能
     */
    fun processKindTags(kindString: String?): List<Pair<String, Int>> {
        if (kindString.isNullOrBlank()) {
            return emptyList()
        }
        
        // 使用逗号或换行符分割标签
        val tags = kindString.split(Regex("[,\n]")
            ).map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // 过滤标签：
        // 1. 排除"完结"和"连载"标签
        // 2. 排除包含标点的标签（只允许中文、英文、数字）
        // 3. 排除包含数字的标签
        // 4. 限制标签长度不超过5个字
        // 5. 去重
        val filteredTags = tags.filter { tag ->
            // 排除"完结"和"连载"标签
            if (tag == "完结" || tag == "连载") {
                return@filter false
            }
            
            // 排除包含标点的标签（只允许中文、英文、数字）
            if (!tag.matches(Regex("^[\u4e00-\u9fa5a-zA-Z0-9]+\$"))) {
                return@filter false
            }
            
            // 排除包含数字的标签
            if (tag.any { char -> char.isDigit() }) {
                return@filter false
            }
            
            // 限制标签长度不超过5个字
            if (tag.length > 5) {
                return@filter false
            }
            
            return@filter true
        }.distinct() // 去重
        
        // 为每个标签生成随机亮色，提高可见度
        return filteredTags.map { tag ->
            Pair(tag, TagColorUtils.generateRandomColor(tag))
        }
    }
    

    fun updateBookCover(coverUrl: String) {
        viewModelScope.launch {
            try {
                val book = bookData.value ?: return@launch
                
                // 使用BookInfoSyncHelper统一更新书籍和我的阅读记录的封面
                // 这会自动设置用户修改标记
                BookInfoSyncHelper.updateBookCover(book.bookUrl, coverUrl)
                
                // 重新加载书籍数据
                val updatedBook: Book? = withContext(IO) {
                    appDb.bookDao.getBook(book.bookUrl)
                }
                
                withContext(Dispatchers.Main) {
                    if (updatedBook != null) {
                        bookData.value = updatedBook
                        // 更新封面的LiveData
                        bookCoverUrl.value = updatedBook.coverUrl ?: ""
                    }
                }
                
                context.toastOnUi("封面更新成功")
            } catch (e: Exception) {
                context.toastOnUi("更新封面失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 更新书籍阅读状态
     */
    fun updateBookReadingStatus(book: Book, status: io.legado.app.constant.ReadingStatus) {
        viewModelScope.launch {
            try {
                // 更新书籍阅读状态
                val updatedBook = book.copy()
                // 手动修改阅读状态，设置userModifiedReadingStatus为true
                updatedBook.setReadingStatus(status, true) // userModified=true
                
                withContext(Dispatchers.IO) {
                    appDb.bookDao.update(updatedBook)
                }
                
                // 更新LiveData
                withContext(Dispatchers.Main) {
                    bookData.value = updatedBook
                }
                
                // 无论设置什么状态，都设置userModifiedReadingStatus标记
                withContext(Dispatchers.IO) {
                    val memory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                    if (memory != null) {
                        // 如果状态变为已完成，设置完成时间
                        val finishReadTime = if (status == io.legado.app.constant.ReadingStatus.FINISHED && memory.finishReadTime == 0L) {
                            System.currentTimeMillis()
                        } else {
                            memory.finishReadTime
                        }
                        val updatedMemory = memory.copy(
                            readingStatus = status,
                            userModifiedReadingStatus = true,
                            finishReadTime = finishReadTime,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
                }
                
                context.toastOnUi("阅读状态更新成功")
            } catch (e: Exception) {
                context.toastOnUi("更新阅读状态失败: ${e.localizedMessage}")
            }
        }
    }
}
