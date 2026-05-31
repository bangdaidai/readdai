package io.legado.app.ui.book.readingmemory

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.BookReview
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.BookProtagonist
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.help.book.BookInfoSyncHelper
import io.legado.app.help.book.ReadingProgressHelper
import io.legado.app.help.book.ProtagonistExtractor
import io.legado.app.base.BaseViewModel
import io.legado.app.utils.TagColorUtils
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadingMemoryDetailViewModel(application: Application) : BaseViewModel(application) {

    // 保存所有协程任务的引用，用于取消
    private val jobs = mutableListOf<Job>()
    
    val memoryData = MutableLiveData<ReadingMemory>()
    val totalReadingTime = MutableLiveData<String>()
    val readingDays = MutableLiveData<String>()
    val lastReadTime = MutableLiveData<String>()
    val readingProgress = MutableLiveData<String>()
    val wordCount = MutableLiveData<String>()
    val bookKind = MutableLiveData<String>()
    val startReadingTime = MutableLiveData<String>()
    val readChapters = MutableLiveData<String>()
    val annotations = MutableLiveData<List<Bookmark>>()
    val reviews = MutableLiveData<List<BookReview>>()
    val isLoading = MutableLiveData<Boolean>()
    val annotationCount = MutableLiveData<Int>(0)
    val reviewCount = MutableLiveData<Int>(0)
    
    // 用于显示单日阅读最久的小时和分钟
    val maxDayReadHours = MutableLiveData<Long>(0)
    val maxDayReadMinutes = MutableLiveData<Long>(0)
    val maxDayReadDate = MutableLiveData<String>("")
    
    // 用于显示阅读总字数
    val totalReadWords = MutableLiveData<String>("")
    val remainingWords = MutableLiveData<String>("")
    
    // 书籍标签
    val bookTags = MutableLiveData<List<BookTag>>()
    
    // 主角名列表
    val protagonists = MutableLiveData<List<String>>()
    
    // 操作结果
    private val _operationResult = MutableLiveData<String>()
    val operationResult: LiveData<String> = _operationResult
    
    // 错误信息
    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg
    
    // 阅读会话数据
    val readingSessions = MutableLiveData<List<MonthReadingSession>>()
    
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
    

    
    private var memoryId: String = ""
    
    fun initData(memoryId: String, bookUrl: String = "", preserveBookInfo: Boolean = false) {
        if (!bookUrl.isNullOrEmpty()) {
            // 根据bookUrl查找阅读记录
            viewModelScope.launch {
                val memory = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getByBookUrl(bookUrl)
                }
                if (memory != null) {
                    this@ReadingMemoryDetailViewModel.memoryId = memory.id
                    loadMemoryData(preserveBookInfo)
                    loadBookTags(preserveBookInfo)
                    setupListeners(memory, preserveBookInfo)
                } else {
                    // 如果没有阅读记录，创建一个
                    val book = withContext(Dispatchers.IO) {
                        appDb.bookDao.getBook(bookUrl)
                    }
                    if (book != null) {
                        // 创建阅读记录
                        val newMemory = ReadingMemory(
                            id = "memory_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                            bookUrl = book.bookUrl,
                            bookName = book.name,
                            bookAuthor = book.author,
                            coverUrl = book.getDisplayCover(),
                            readingStatus = io.legado.app.constant.ReadingStatus.READING,
                            progress = 0f,
                            type = book.type,
                            createTime = System.currentTimeMillis(),
                            updateTime = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            appDb.readingMemoryDao.insert(newMemory)
                        }
                        this@ReadingMemoryDetailViewModel.memoryId = newMemory.id
                        loadMemoryData(preserveBookInfo)
                        loadBookTags(preserveBookInfo)
                        setupListeners(newMemory, preserveBookInfo)
                    }
                }
            }
        } else {
            // 使用memoryId加载数据
            this.memoryId = memoryId
            loadMemoryData(preserveBookInfo)
            loadBookTags(preserveBookInfo)
            setupListeners(null, preserveBookInfo)
        }
    }
    
    private fun setupListeners(memory: ReadingMemory?, preserveBookInfo: Boolean = false) {
        val currentMemory = memory ?: appDb.readingMemoryDao.getMemoryById(memoryId)
        currentMemory?.let { 
            // 实时监听书籍变化，实现双向同步
            viewModelScope.launch {
                appDb.bookDao.flowGetBookByUrl(it.bookUrl).collect {
                    // 书籍变化时，重新加载记忆数据
                    loadMemoryData(preserveBookInfo)
                }
            }
            
            // 实时监听书摘变化，实现删除后实时更新UI
            viewModelScope.launch {
                appDb.bookAnnotationDao.flowByBook(it.bookName, it.bookAuthor)
                    .catch { e: Throwable ->
                        e.printStackTrace()
                    }
                    .collect { bookAnnotations: List<BookAnnotation> ->
                        // 转换为Bookmark列表
                        val bookmarks = bookAnnotations.map { bookAnnotation: BookAnnotation ->
                            Bookmark(
                                time = bookAnnotation.time,
                                bookName = bookAnnotation.bookName,
                                bookAuthor = bookAnnotation.bookAuthor,
                                chapterIndex = bookAnnotation.chapterIndex,
                                chapterPos = bookAnnotation.chapterPos,
                                chapterName = bookAnnotation.chapterName,
                                bookText = bookAnnotation.bookText,
                                content = bookAnnotation.content
                            )
                        }
                        
                        // 计算带有笔记的书摘数量
                        val annotationsWithNotes = bookAnnotations.count { bookAnnotation: BookAnnotation -> 
                            bookAnnotation.note?.isNotBlank() == true 
                        }
                        
                        // 更新LiveData
                        withContext(Dispatchers.Main) {
                            annotations.value = bookmarks
                            annotationCount.value = bookAnnotations.size
                            reviewCount.value = annotationsWithNotes
                        }
                        
                        // 更新阅读记忆中的书摘数量
                        val updatedMemory = it.copy(
                            annotationCount = bookAnnotations.size,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
            }
            
            // 实时监听ReadSession数据变化，确保阅读时间和会话数据实时更新
            viewModelScope.launch {
                appDb.readSessionDao.flowGetAll().collect {
                    // 当ReadSession数据变化时，重新加载记忆数据和阅读会话
                    val currentMemory = appDb.readingMemoryDao.getMemoryById(memoryId)
                    currentMemory?.let { mem ->
                        loadMemoryProgress(mem, preserveBookInfo)
                        loadReadingSessions(mem)
                    }
                }
            }
        }
    }
    
    fun refresh() {
        // 强制重新加载所有数据
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    isLoading.value = true
                }
                
                // 获取我的阅读记录信息
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory == null) {
                    throw Exception("我的阅读记录不存在")
                }
                
                withContext(Dispatchers.Main) {
                    memoryData.value = memory
                }
                
                // 重新加载所有数据
                loadMemoryProgress(memory)
                loadAnnotations(memory)
                loadReviews(memory)
                
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    getApplication<Application>().toastOnUi(e.localizedMessage ?: "刷新失败")
                }
            }
        }
    }
    

    
    /**
     * 格式化阅读时长
     */
    private fun formatDuring(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        val d = if (days > 0) "${days}天" else ""
        val h = if (hours > 0) "${hours}小时" else ""
        val m = if (minutes > 0) "${minutes}分钟" else ""
        val s = if (seconds > 0) "${seconds}秒" else ""
        var time = "$d$h$m$s"
        if (time.isBlank()) {
            time = "0秒"
        }
        return time
    }
    
    private fun loadMemoryData(preserveBookInfo: Boolean = false) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    isLoading.value = true
                }
                
                // 获取我的阅读记录信息
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory == null) {
                    throw Exception("我的阅读记录不存在")
                }
                
                withContext(Dispatchers.Main) {
                    memoryData.value = memory
                }
                
                // 分步加载其他数据，每个数据加载完成后立即更新UI
                val deferredProgress = async {
                    loadMemoryProgress(memory, preserveBookInfo)
                }
                
                val deferredAnnotations = async {
                    loadAnnotations(memory)
                }
                
                val deferredReviews = async {
                    loadReviews(memory)
                }
                
                val deferredSessions = async {
                    loadReadingSessions(memory)
                }
                
                val deferredProtagonists = async {
                    loadProtagonists(memory)
                }
                
                // 等待所有数据加载完成
                awaitAll(deferredProgress, deferredAnnotations, deferredReviews, deferredSessions, deferredProtagonists)
                
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    getApplication<Application>().toastOnUi(e.localizedMessage ?: "加载失败")
                }
            }
        }
    }
    
    private suspend fun loadProtagonists(memory: ReadingMemory) {
        // 先从数据库加载主角名
        val dbProtagonists = withContext(Dispatchers.IO) {
            appDb.bookProtagonistDao.getByBook(memory.bookUrl)
        }
        
        if (dbProtagonists.isNotEmpty()) {
            // 如果数据库中有主角名，使用数据库中的，不重新提取
            val protagonistNames = dbProtagonists.map { it.name }
            withContext(Dispatchers.Main) {
                protagonists.value = protagonistNames
            }
        } else {
            // 如果数据库中没有，从简介中提取
            // 获取书籍信息以获取简介
            val book = withContext(Dispatchers.IO) {
                appDb.bookDao.getBook(memory.bookUrl)
            }
            
            // 从简介中提取主角名
            val intro = if (book != null && !book.getDisplayIntro().isNullOrEmpty()) {
                book.getDisplayIntro()
            } else if (!memory.intro.isNullOrEmpty()) {
                memory.intro
            } else {
                null
            }
            
            val extractedProtagonists = ProtagonistExtractor.extractProtagonists(intro)
            
            // 将提取的主角名保存到数据库
            if (extractedProtagonists.isNotEmpty()) {
                val protagonistEntities: List<BookProtagonist> = extractedProtagonists.map {
                    BookProtagonist(
                        bookUrl = memory.bookUrl,
                        name = it,
                        isCustom = false
                    )
                }
                withContext(Dispatchers.IO) {
                    appDb.bookProtagonistDao.insertAll(protagonistEntities)
                }
            }
            
            withContext(Dispatchers.Main) {
                protagonists.value = extractedProtagonists
            }
        }
    }
    
    /**
     * 添加主角名
     */
    fun addProtagonist(bookUrl: String, name: String) {
        viewModelScope.launch {
            try {
                val protagonist = BookProtagonist(
                    bookUrl = bookUrl,
                    name = name,
                    isCustom = true
                )
                withContext(Dispatchers.IO) {
                    appDb.bookProtagonistDao.insert(protagonist)
                }
                // 重新加载主角名
                val memory = memoryData.value
                if (memory != null) {
                    loadProtagonists(memory)
                }
                _operationResult.postValue("主角名添加成功")
            } catch (e: Exception) {
                _errorMsg.postValue("添加主角名失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 删除主角名
     */
    fun deleteProtagonist(bookUrl: String, name: String) {
        viewModelScope.launch {
            try {
                val protagonists = withContext(Dispatchers.IO) {
                    appDb.bookProtagonistDao.getByBook(bookUrl)
                }
                val protagonist = protagonists.find { it.name == name }
                if (protagonist != null) {
                    withContext(Dispatchers.IO) {
                        appDb.bookProtagonistDao.delete(protagonist.id)
                    }
                    // 重新加载主角名
                    val memory = memoryData.value
                    if (memory != null) {
                        loadProtagonists(memory)
                    }
                    _operationResult.postValue("主角名删除成功")
                }
            } catch (e: Exception) {
                _errorMsg.postValue("删除主角名失败: ${e.localizedMessage}")
            }
        }
    }
    
    private suspend fun loadReadingSessions(memory: ReadingMemory) {
        val book = withContext(Dispatchers.IO) {
            appDb.bookDao.getBook(memory.bookUrl)
        }
        
        if (book == null) {
            // 如果书籍不存在，返回空列表
            withContext(Dispatchers.Main) {
                readingSessions.value = emptyList()
            }
            return
        }
        
        // 获取该书籍的所有阅读会话
        val sessions = withContext(Dispatchers.IO) {
            appDb.readSessionDao.getByBook(book.name, book.author)
        }
        
        if (sessions.isEmpty()) {
            // 如果没有阅读会话，返回空列表
            withContext(Dispatchers.Main) {
                readingSessions.value = emptyList()
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
            readingSessions.value = monthSessions
        }
    }
    
    private suspend fun loadMemoryProgress(memory: ReadingMemory, preserveBookInfo: Boolean = false) {
        // 获取书籍信息以获取准确的阅读时间
        val book = withContext(Dispatchers.IO) {
            appDb.bookDao.getBook(memory.bookUrl)
        }
        
        // 计算总阅读时间（分钟）
        val totalTime = if (book != null) {
            // 使用书籍的阅读时间数据，与阅读详情页保持一致
            val readTime = try {
                // 1. 首先从readSession表中获取总阅读时间
                var time = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
                
                if (time == 0L) {
                    // 2. 如果readSession中没有，使用bookName查询所有记录的总和
                    time = appDb.readSessionDao.getTotalReadTime(book.name) ?: 0L
                }
                
                if (time == 0L) {
                    // 3. 最后使用bookUrl查询
                    time = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl) ?: 0L
                }
                
                time
            } catch (e: Exception) {
                0L
            }
            
            // 转换为分钟
            readTime / 60000
        } else {
            // 如果书籍不存在，使用我的阅读记录的阅读时间
            memory.readTime / 60000
        }
        
        withContext(Dispatchers.Main) {
            totalReadingTime.value = formatReadingTime(totalTime)
        }
        
        // 计算阅读天数
        val days = if (book != null) {
            // 使用书籍的阅读天数计算方法，与阅读详情页保持一致
            calculateReadingDays(book)
        } else {
            // 如果书籍不存在，默认为1天
            1
        }
        withContext(Dispatchers.Main) {
            readingDays.value = "${days}"
        }
        
        // 如果需要保留书籍信息，则检查书源分组
        if (preserveBookInfo && book != null) {
            // 获取书源信息
            val bookSource = withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(book.origin)
            }
            
            // 判断书源是否属于"正版"分组
            val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
            
            // 如果不是正版书源，则保留原有的字数和分类信息
            if (!isOfficialSource) {
                // 计算阅读进度百分比，使用统一的进度计算工具类
            val fullProgress = calculateReadingProgress(memory)
            // 只提取纯数字百分比，不包含%和章节进度
            val pureProgressStr = if (fullProgress.contains("%") && !fullProgress.contains("未开始")) {
                fullProgress.split("%")[0].trim()
            } else if (fullProgress == "未开始") {
                "0"
            } else {
                fullProgress.split("%")[0].trim()
            }
            // 转换为Int类型的进度值，与BookReadingDetailViewModel保持一致
            val pureProgress = pureProgressStr.toFloatOrNull() ?: 0.0f
            val progressInt = pureProgress.toInt()
            withContext(Dispatchers.Main) {
                readingProgress.value = progressInt.toString()
            }
            
            // 使用我的阅读记录中的字数和分类信息，并转换为万字单位
            withContext(Dispatchers.Main) {
                val memoryWordCount = memory.wordCount ?: "未知"
                val formattedWordCount = if (memoryWordCount != "未知") {
                    // 解析字数并转换为万字单位
                    val totalWords = parseTotalWords(memoryWordCount)
                    "${formatWordCount(totalWords)}万字"
                } else {
                    "未知"
                }
                wordCount?.value = formattedWordCount
                bookKind?.value = memory.kind ?: "未分类"
            }
            
            // 获取最近阅读时间
            val lastTime = calculateLastReadTime(book)
            withContext(Dispatchers.Main) {
                lastReadTime.value = lastTime
            }
            return
            }
        }
        
        // 如果不需要保留书籍信息或者是正版书源，则正常更新字数和分类
        // 计算阅读进度百分比，使用统一的进度计算工具类
        val fullProgress = if (book != null) {
            // 当book存在时，使用book对象计算进度，与阅读详情页保持一致
            calculateReadingProgress(book)
        } else {
            // 当book不存在时，使用memory对象计算进度
            calculateReadingProgress(memory)
        }
        // 只提取纯数字百分比，不包含%和章节进度
        val pureProgressStr = if (fullProgress.contains("%") && !fullProgress.contains("未开始")) {
            fullProgress.split("%")[0].trim()
        } else if (fullProgress == "未开始") {
            "0"
        } else {
            fullProgress.split("%")[0].trim()
        }
        // 转换为Int类型的进度值，与BookReadingDetailViewModel保持一致
        val pureProgress = pureProgressStr.toFloatOrNull() ?: 0.0f
        val progressInt = pureProgress.toInt()
        withContext(Dispatchers.Main) {
            readingProgress.value = progressInt.toString()
        }
        
        // 获取书籍字数信息
        val wordCountStr = if (book != null) {
            try {
                // 使用书籍的字数信息，与阅读详情页保持一致
                if (!book.wordCount.isNullOrEmpty()) {
                    book.wordCount
                } else {
                    // 如果书籍没有字数信息，尝试从我的阅读记录中获取
                    if (!memory.wordCount.isNullOrEmpty()) {
                        memory.wordCount
                    } else {
                        "未知"
                    }
                }
            } catch (e: Exception) {
                if (!memory.wordCount.isNullOrEmpty()) {
                    memory.wordCount
                } else {
                    "未知"
                }
            }
        } else {
            // 如果书籍不存在，使用我的阅读记录的字数信息
            if (!memory.wordCount.isNullOrEmpty()) {
                memory.wordCount
            } else {
                "未知"
            }
        }
        withContext(Dispatchers.Main) {
            // 将字数统一转换为万字单位
            val formattedWordCount = if (wordCountStr != null && wordCountStr != "未知") {
                // 解析字数并转换为万字单位
                val totalWords = parseTotalWords(wordCountStr)
                "${formatWordCount(totalWords)}万字"
            } else {
                "未知"
            }
            wordCount?.value = formattedWordCount
        }
        
        // 获取书籍分类信息
        val kind = if (book != null) {
            try {
                // 使用书籍的分类信息，与阅读详情页保持一致
                if (!book.kind.isNullOrEmpty()) {
                    book.kind
                } else {
                    // 如果书籍没有分类信息，尝试从我的阅读记录中获取
                    if (!memory.kind.isNullOrEmpty()) {
                        memory.kind
                    } else {
                        "未分类"
                    }
                }
            } catch (e: Exception) {
                if (!memory.kind.isNullOrEmpty()) {
                    memory.kind
                } else {
                    "未分类"
                }
            }
        } else {
            // 如果书籍不存在，使用我的阅读记录的分类信息
            if (!memory.kind.isNullOrEmpty()) {
                memory.kind
            } else {
                "未分类"
            }
        }
        withContext(Dispatchers.Main) {
            bookKind?.value = kind ?: "未分类"
        }
        
        // 获取最近阅读时间
        val lastTime = if (book != null) {
            // 使用书籍的最近阅读时间计算方法
            calculateLastReadTime(book)
        } else {
            // 如果书籍不存在，使用我的阅读记录的更新时间
            formatLastReadTime(memory.updateTime)
        }
        
        // 获取开始阅读时间
        val startTime = if (book != null) {
            // 使用书籍的开始阅读时间计算方法
            calculateStartReadingTime(book)
        } else {
            // 如果书籍不存在，使用默认值
            "未开始阅读"
        }
        
        // 计算已读章节
        val chapters = if (book != null) {
            // 使用书籍的已读章节计算
            calculateReadChapters(book)
        } else {
            // 如果书籍不存在，使用默认值
            "0/0"
        }
        
        withContext(Dispatchers.Main) {
            lastReadTime.value = lastTime
            startReadingTime.value = startTime
            readChapters.value = chapters
        }
        
        // 计算单日阅读最久
        val maxDayReadTime = if (book != null) {
            calculateMaxDayReadingTime(book)
        } else {
            0L
        }
        val maxHours = maxDayReadTime / 60
        val maxMins = maxDayReadTime % 60
        // 计算单日阅读最久的日期
        val maxDayDate = if (book != null) {
            calculateMaxDayReadDate(book, maxDayReadTime)
        } else {
            ""
        }
        withContext(Dispatchers.Main) {
            maxDayReadHours.value = maxHours
            maxDayReadMinutes.value = maxMins
            maxDayReadDate.value = maxDayDate
        }
        
        // 计算阅读总字数和剩余字数
        val (readWords, remaining) = if (book != null) {
            calculateReadWords(book, pureProgress)
        } else {
            Pair("0", "0")
        }
        withContext(Dispatchers.Main) {
            totalReadWords.value = readWords
            remainingWords.value = "剩余字数${remaining}万字"
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
        
        // 获取日期
        val date = java.util.Date(timestamp)
        
        return when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days <= 7L -> "${days}天前"
            else -> {
                // 超过7天的显示具体日期
                val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
                dateFormat.format(date)
            }
        }
    }
    
    private fun formatReadingTime(minutes: Long): String {
        // 返回纯分钟数，由Activity负责格式化显示
        return minutes.toString()
    }

    /**
     * 计算阅读天数
     */
    private suspend fun calculateReadingDays(book: Book): Int {
        return withContext(Dispatchers.IO) {
            try {
                var readingDays = 0
                
                // First try with bookName
                val bookName = book.name
                val sessionsByBookName = appDb.readSessionDao.getByBook(bookName, book.author)
                if (sessionsByBookName.isNotEmpty()) {
                    val uniqueDays = sessionsByBookName.map { 
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(it.startTime))
                    }.toSet()
                    readingDays = uniqueDays.size
                }
                
                // If no sessions found with bookName, try with bookUrl
                if (readingDays == 0) {
                    // 检查是否有该书籍的阅读会话记录
                    val readSessions = appDb.readSessionDao.getByBook(book.name, book.author)
                    if (readSessions.isNotEmpty()) {
                        // 如果有阅读会话，说明有阅读记录
                        readingDays = 1
                    }
                }
                
                readingDays
            } catch (e: Exception) {
                e.printStackTrace()
                1
            }
        }
    }

    /**
     * 计算最近阅读时间
     */
    private suspend fun calculateLastReadTime(book: Book): String {
        return withContext(Dispatchers.IO) {
            try {
                var sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                
                if (sessions.isNotEmpty()) {
                    // 获取最近一次阅读会话的结束时间，与阅读详情页保持一致
                    val lastSession = sessions.maxByOrNull { it.endTime }
                    val lastTime = lastSession?.endTime ?: lastSession?.startTime ?: book.durChapterTime
                    return@withContext formatLastReadTime(lastTime)
                } else {
                    // 如果没有阅读会话，使用书籍的最近阅读时间（durChapterTime）
                    return@withContext formatLastReadTime(book.durChapterTime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                formatLastReadTime(0L)
            }
        }
    }

    /**
     * 计算阅读进度
     */
    private suspend fun calculateReadingProgress(book: Book): String {
        // 获取数值型进度（0-100），与BookReadingDetailViewModel保持一致
        val progressValue = ReadingProgressHelper.calculateReadingProgress(book)
        // 只返回纯数值，单位由布局文件显示
        return "${progressValue.toInt()}"
    }
    
    /**
     * 计算阅读进度
     */
    private fun calculateReadingProgress(memory: ReadingMemory): String {
        // 使用统一的阅读进度计算方法，获取数值型进度
        val book = appDb.bookDao.getBook(memory.bookUrl)
        val progressValue = if (book != null) {
            ReadingProgressHelper.calculateReadingProgress(book)
        } else {
            memory.progress
        }
        // 只返回纯数值，单位由布局文件显示
        return "${progressValue.toInt()}"
    }
    
    /**
     * 计算开始阅读时间
     */
    private suspend fun calculateStartReadingTime(book: Book): String {
        var firstReadTime: Long? = null
        
        // 1. 优先使用书籍专门的首次阅读时间字段
        if (book.firstReadTime > 0L) {
            firstReadTime = book.firstReadTime
        } else {
            // 2. 尝试从ReadSession表获取最早阅读时间
            var sessions = appDb.readSessionDao.getByBook(book.name, book.author)
            if (sessions.isNotEmpty()) {
                firstReadTime = sessions.minByOrNull { it.startTime }?.startTime
            }
            
            // 3. 如果没有ReadSession记录，尝试从书籍信息获取
            if (firstReadTime == null) {
                // 使用书籍的更新时间作为参考
                firstReadTime = book.durChapterTime
            }
            
            // 4. 最后使用书籍的最后阅读时间作为回退方案
            firstReadTime = firstReadTime ?: book.durChapterTime
            
            // 5. 保存计算出的首次阅读时间到数据库，避免下次计算时再次变化
            if (firstReadTime > 0L && book.firstReadTime == 0L) {
                withContext(Dispatchers.IO) {
                    // 更新Book对象的firstReadTime字段
                    book.firstReadTime = firstReadTime
                    appDb.bookDao.update(book)
                    
                    // 同时更新对应的ReadingMemory
                    val readingMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                    if (readingMemory != null) {
                        val updatedMemory = readingMemory.copy(firstReadTime = firstReadTime)
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
                }
            }
        }
        
        return if (firstReadTime == 0L) {
            "未开始阅读"
        } else {
            val date = java.util.Date(firstReadTime)
            val format = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
            "始于" + format.format(date)
        }
    }
    
    /**
     * 计算已读章节
     */
    private fun calculateReadChapters(book: Book): String {
        // 处理总章节数为0的情况
        val totalChapters = appDb.bookChapterDao.getChapterCount(book.bookUrl)
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
    
    /**
     * 计算单日阅读最久的时间（分钟）
     */
    private suspend fun calculateMaxDayReadingTime(book: Book): Long {
        return withContext(Dispatchers.IO) {
            try {
                val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                
                if (sessions.isEmpty()) {
                    return@withContext 0L
                }
                
                // 按日期分组计算每天的阅读时间
                val dailyReadTimeMap = calculateDailyReadTime(sessions)
                
                // 找出最大的日阅读时间（转换为分钟）
                dailyReadTimeMap.values.maxOrNull()?.div(60000) ?: 0L
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }
    
    /**
     * 计算单日阅读最久的日期
     */
    private suspend fun calculateMaxDayReadDate(book: Book, maxTime: Long): String {
        return withContext(Dispatchers.IO) {
            try {
                val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                
                if (sessions.isEmpty()) {
                    return@withContext "暂无数据"
                }
                
                // 按日期分组计算每天的阅读时间
                val dailyReadTimeMap = calculateDailyReadTime(sessions)
                
                // 找出最大阅读时间对应的日期
                val maxEntry = dailyReadTimeMap.entries.maxByOrNull { it.value }
                val maxDate = maxEntry?.key
                
                if (maxDate.isNullOrEmpty()) {
                    return@withContext "暂无数据"
                }
                
                // 转换为yyyy年MM月dd日格式
                val originalFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val date = originalFormat.parse(maxDate)
                val targetFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
                if (date != null) {
                    targetFormat.format(date)
                } else {
                    maxDate
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "暂无数据"
            }
        }
    }
    
    /**
     * 计算每天的阅读时间（毫秒）
     */
    private fun calculateDailyReadTime(sessions: List<io.legado.app.data.entities.readRecord.ReadSession>): Map<String, Long> {
        val dailyReadTimeMap = mutableMapOf<String, Long>()
        
        for (session in sessions) {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(session.startTime))
            
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
            e.printStackTrace()
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
        // 提取纯数字和单位
        val cleanStr = wordCountStr.trim()
        
        // 检查是否包含"万"字
        if (cleanStr.contains("万", ignoreCase = true)) {
            // 提取数字部分
            val numberStr = cleanStr.replace(Regex("[^0-9.]", RegexOption.IGNORE_CASE), "")
            val number = numberStr.toDoubleOrNull() ?: 0.0
            // 转换为实际数字（乘以10000）
            return (number * 10000).toLong()
        } else {
            // 不包含"万"字，直接提取数字
            val numberStr = cleanStr.replace(Regex("[^0-9]", RegexOption.IGNORE_CASE), "")
            return numberStr.toLongOrNull() ?: 0L
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
    
    private suspend fun loadAnnotations(memory: ReadingMemory) {
        // 在IO线程中执行数据库查询
        val annotations = withContext(Dispatchers.IO) {
            // 从bookAnnotationDao获取书摘数据
            appDb.bookAnnotationDao.getByBook(memory.bookName, memory.bookAuthor)
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
            this@ReadingMemoryDetailViewModel.annotations.value = bookmarks
            this@ReadingMemoryDetailViewModel.annotationCount.value = annotations.size
            this@ReadingMemoryDetailViewModel.reviewCount.value = annotationsWithNotes
        }
    }
    
    suspend fun getReviews(bookUrl: String): List<BookReview> {
        return withContext(Dispatchers.IO) {
            appDb.bookReviewDao.getReviewByBookUrl(bookUrl)
        }
    }
    
    fun updateMemoryRating(memoryId: String, rating: Float) {
        // 创建协程任务并保存引用
        val job = viewModelScope.launch {
            try {
                // 获取当前我的阅读记录
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory == null) {
                    _operationResult.postValue("我的阅读记录不存在")
                    return@launch
                }
                
                // 检查评分是否已经相同，避免不必要的更新
                if (memory.rating == rating) {
                    return@launch
                }
                
                // 使用BookInfoSyncHelper统一更新书籍和我的阅读记录的评分
                // 这会自动设置用户修改标记
                BookInfoSyncHelper.updateBookRating(memory.bookUrl, rating)
                
                // 更新我的阅读记录的评分
                val updatedMemory = memory.copy(
                    wordCount = memory.wordCount,
                    kind = memory.kind,
                    rating = rating, 
                    updateTime = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.update(updatedMemory)
                }
                
                // 更新UI
                memoryData.postValue(updatedMemory)
                _operationResult.postValue("评分更新成功")
            } catch (e: Exception) {
                _operationResult.postValue("更新评分失败: ${e.localizedMessage}")
            }
        }
        
        // 保存任务引用
        jobs.add(job)
        
        // 任务完成后从列表中移除
        job.invokeOnCompletion {
            jobs.remove(job)
        }
    }
    
    fun updateMemoryIntro(memoryId: String, intro: String) {
        // 创建协程任务并保存引用
        val job = viewModelScope.launch {
            try {
                // 获取当前我的阅读记录
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory == null) {
                    getApplication<Application>().toastOnUi("我的阅读记录不存在")
                    return@launch
                }
                
                // 使用BookInfoSyncHelper统一更新书籍和我的阅读记录的简介
                // 这会自动设置用户修改标记
                BookInfoSyncHelper.updateBookIntro(memory.bookUrl, intro)
                
                // 重新获取更新后的我的阅读记录
                val updatedMemory = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                // 更新UI
                if (updatedMemory != null) {
                    memoryData.postValue(updatedMemory)
                }
                getApplication<Application>().toastOnUi("简介已更新")
            } catch (e: Exception) {
                getApplication<Application>().toastOnUi("更新简介失败: ${e.localizedMessage}")
            }
        }
        
        // 保存任务引用
        jobs.add(job)
        
        // 任务完成后从列表中移除
        job.invokeOnCompletion {
            jobs.remove(job)
        }
    }
    
    fun updateMemoryReview(memoryId: String, reviewContent: String) {
        // 创建协程任务并保存引用
        val job = viewModelScope.launch {
            try {
                // 获取当前我的阅读记录
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory == null) {
                    getApplication<Application>().toastOnUi("我的阅读记录不存在")
                    return@launch
                }
                
                // 获取或创建书评
                val reviewList = withContext(Dispatchers.IO) {
                    appDb.bookReviewDao.getReviewByBookUrl(memory.bookUrl)
                }
                
                val review = if (reviewList.isNotEmpty()) {
                    // 更新现有书评
                    val existingReview = reviewList.first()
                    existingReview.copy(reviewContent = reviewContent)
                } else {
                    // 创建新书评
                    BookReview(
                        bookUrl = memory.bookUrl,
                        bookName = memory.bookName,
                        bookAuthor = memory.bookAuthor,
                        reviewContent = reviewContent
                    )
                }
                
                // 保存书评
                withContext(Dispatchers.IO) {
                    if (reviewList.isNotEmpty()) {
                        appDb.bookReviewDao.update(review)
                    } else {
                        appDb.bookReviewDao.insert(review)
                    }
                }
                
                // 重新加载书评
                loadReviews(memory)

                // 更新UI（不修改 ReadingMemory 的书评字段，统一从 BookReview 表读取）
                memoryData.postValue(memory)
                getApplication<Application>().toastOnUi("书评已更新")
            } catch (e: Exception) {
                getApplication<Application>().toastOnUi("更新书评失败: ${e.localizedMessage}")
            }
        }
        
        // 保存任务引用
        jobs.add(job)
        
        // 任务完成后从列表中移除
        job.invokeOnCompletion {
            jobs.remove(job)
        }
    }
    
    suspend fun loadReviews(memory: ReadingMemory) {
        // 在IO线程中执行数据库查询
        val reviewList = withContext(Dispatchers.IO) {
            // 从数据库获取书评
            appDb.bookReviewDao.getReviewByBookUrl(memory.bookUrl)
        }
        
        // 直接返回BookReview列表
        withContext(Dispatchers.Main) {
            reviews.value = reviewList
        }
    }
    
    fun loadBookTags(preserveBookInfo: Boolean = false) {
        viewModelScope.launch {
            try {
                // 获取我的阅读记录信息
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory != null) {
                    loadBookTags(memory, preserveBookInfo)
                }
            } catch (e: Exception) {
                _errorMsg.postValue("获取书籍标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    private fun loadBookTags(memory: ReadingMemory, preserveBookInfo: Boolean = false) {
        viewModelScope.launch {
            try {
                // 获取书籍关联的标签
                val tagRelations = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getRelationsByBook(memory.bookUrl)
                }
                
                // 获取标签详情
                var tags = if (tagRelations.isNotEmpty()) {
                    val tagIds = tagRelations.map { it.tagId }
                    val allTags = tagIds.mapNotNull { tagId ->
                        try {
                            appDb.bookTagDao.getTag(tagId)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    // 获取排除标签列表
                    val excludedTags = withContext(Dispatchers.IO) {
                        appDb.excludedTagDao.getAllSync()
                    }
                    val excludedTagNames = excludedTags.map { it.name }.toSet()
                    
                    // 过滤掉被排除的标签
                    val filteredTags = allTags.filter { tag -> 
                        !excludedTagNames.contains(tag.name)
                    }
                    
                    // 获取所有标签分组，按排序顺序
                    val tagGroups = withContext(Dispatchers.IO) {
                        appDb.bookTagGroupDao.getAllSorted()
                    }
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
                
                // 获取书籍信息
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(memory.bookUrl)
                }
                
                // 检查书源是否属于正版分组
                val isOfficialSource = book?.let {
                    withContext(Dispatchers.IO) {
                        val bookSource = appDb.bookSourceDao.getBookSource(it.origin)
                        bookSource?.bookSourceGroup?.let { group -> group.contains("正版") } == true
                    }
                } ?: false
                
                // 与分类信息和字数的处理逻辑保持一致
                // 正版书源：
                //   - 如果没有标签关联关系，从kind字段生成标签并保存到数据库
                //   - 如果已有标签关联关系，保留现有标签，不重新生成
                // 非正版书源：从数据库加载原有标签，不从kind字段生成新标签
                if (isOfficialSource && tags.isEmpty()) {
                    // 优先从Book对象获取kind字段
                    val kind = if (book != null && !book.kind.isNullOrBlank()) {
                        book.kind
                    } else if (!memory.kind.isNullOrBlank()) {
                        memory.kind
                    } else {
                        null
                    }
                    
                    if (!kind.isNullOrBlank()) {
                        // 从kind字段中提取标签信息
                        val kindTags = kind!!.split(Regex("[,\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                        
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
                        
                        // 获取排除标签列表
                        val excludedTags = withContext(Dispatchers.IO) {
                            appDb.excludedTagDao.getAllSync()
                        }
                        val excludedTagNames = excludedTags.map { it.name }.toSet()
                        
                        // 过滤掉被排除的标签
                        val finalKindTags = filteredKindTags.filter { tag -> 
                            !excludedTagNames.contains(tag)
                        }
                        
                        // 将过滤后的标签转换为BookTag对象
                        val kindTagsList = finalKindTags.map { tagName ->
                            // 检查标签是否已存在于数据库中
                            var existingTag = withContext(Dispatchers.IO) {
                                appDb.bookTagDao.getTagByName(tagName)
                            }
                            
                            if (existingTag == null) {
                                // 创建标签并保存到数据库
                                val newTag = BookTag(
                                    name = tagName,
                                    color = generateRandomDarkColor(), // 使用随机深色
                                    createTime = System.currentTimeMillis()
                                )
                                val tagId = appDb.bookTagDao.insert(newTag)
                                existingTag = newTag.copy(id = tagId)
                            }
                            
                            // 创建标签与书籍的关联关系
                            val relation = BookTagRelation(
                                id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                                bookUrl = memory.bookUrl,
                                tagId = existingTag.id,
                                createTime = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO) {
                                appDb.bookTagRelationDao.insert(relation)
                            }
                            
                            existingTag
                        }
                        
                        // 更新标签列表
                        tags = kindTagsList.distinctBy { it.id }
                        
                        // 按分组排序，同一分组内按名称排序
                        val tagGroups = withContext(Dispatchers.IO) {
                            appDb.bookTagGroupDao.getAllSorted()
                        }
                        val groupSortOrder = tagGroups.associateBy({ it.id }, { it.sortOrder })
                        
                        tags = tags.sortedWith(compareBy<BookTag> {
                            // 先按分组排序，未分组的放最后
                            groupSortOrder[it.groupId] ?: Int.MAX_VALUE
                        }.thenBy { 
                            // 同一分组内按名称排序
                            it.name
                        })
                    }
                }
                
                withContext(Dispatchers.Main) {
                    bookTags.value = tags
                }
            } catch (e: Exception) {
                _errorMsg.postValue("获取书籍标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 生成随机亮色，提高可见度
     */
    private fun generateRandomDarkColor(): Int {
        return TagColorUtils.generateRandomColor()
    }
    
    fun addBookTag(bookUrl: String, tagId: Long) {
        viewModelScope.launch {
            try {
                // 检查关联关系是否已存在
                val existingRelation = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getRelation(bookUrl, tagId)
                }
                
                if (existingRelation != null) {
                    _operationResult.postValue("书籍已包含此标签")
                } else {
                    // 创建关联关系
                    val relation = BookTagRelation(
                        id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                        bookUrl = bookUrl,
                        tagId = tagId,
                        createTime = System.currentTimeMillis()
                    )
                    
                    withContext(Dispatchers.IO) {
                        appDb.bookTagRelationDao.insert(relation)
                    }
                    
                    // 获取标签信息
                    val tag = withContext(Dispatchers.IO) {
                        appDb.bookTagDao.getTag(tagId)
                    }
                    
                    // 使用BookInfoSyncHelper同步标签到Book实体的kind字段
                    if (tag != null) {
                        BookInfoSyncHelper.syncTagToBookKind(
                            bookUrl, tag.name
                        )
                        
                        _operationResult.postValue("标签添加成功")
                    } else {
                        _operationResult.postValue("标签添加成功")
                    }
                    
                    // 重新加载标签
                    loadBookTags(preserveBookInfo = false)
                    
                    // 通知书架更新
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    // 通知标签更新
                    postEvent(EventBus.TAGS_UPDATED, bookUrl)
                }
            } catch (e: Exception) {
                _errorMsg.postValue("添加标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    fun removeBookTag(bookUrl: String, tagId: Long) {
        viewModelScope.launch {
            try {
                // 获取关联关系
                val relation = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getRelation(bookUrl, tagId)
                }
                
                if (relation != null) {
                    // 获取标签信息
                    val tag = withContext(Dispatchers.IO) {
                        appDb.bookTagDao.getTag(tagId)
                    }
                    
                    // 删除关联关系
                    withContext(Dispatchers.IO) {
                        appDb.bookTagRelationDao.delete(relation)
                    }
                    
                    // 使用BookInfoSyncHelper从Book实体的kind字段移除标签
                    if (tag != null) {
                        BookInfoSyncHelper.removeTagFromBookKind(
                            bookUrl, tag.name
                        )
                        
                        _operationResult.postValue("标签移除成功")
                    } else {
                        _operationResult.postValue("标签移除成功")
                    }
                    
                    // 重新加载标签
                    loadBookTags(preserveBookInfo = false)
                    
                    // 通知书架更新
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                } else {
                    _operationResult.postValue("书籍不包含此标签")
                }
            } catch (e: Exception) {
                _errorMsg.postValue("移除标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    fun updateMemoryCover(memoryId: String, coverUrl: String) {
        // 创建协程任务并保存引用
        val job = viewModelScope.launch {
            try {
                // 获取当前我的阅读记录
                val memory: ReadingMemory? = withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.getMemoryById(memoryId)
                }
                
                if (memory == null) {
                    getApplication<Application>().toastOnUi("我的阅读记录不存在")
                    return@launch
                }
                
                // 使用BookInfoSyncHelper统一更新书籍和我的阅读记录的封面
                // 这会自动设置用户修改标记
                BookInfoSyncHelper.updateBookCover(memory.bookUrl, coverUrl)
                
                // 更新我的阅读记录的封面URL
                val updatedMemory = memory.copy(
                    wordCount = memory.wordCount,
                    kind = memory.kind,
                    coverUrl = coverUrl, 
                    updateTime = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    appDb.readingMemoryDao.update(updatedMemory)
                }
                
                // 更新UI
                memoryData.postValue(updatedMemory)
                getApplication<Application>().toastOnUi("封面已更新")
            } catch (e: Exception) {
                getApplication<Application>().toastOnUi("更新封面失败: ${e.localizedMessage}")
            }
        }
        
        // 保存任务引用
        jobs.add(job)
        
        // 任务完成后从列表中移除
        job.invokeOnCompletion {
            jobs.remove(job)
        }
    }
    
    /**
     * 手动刷新书籍信息（字数和分类）
     */
    fun refreshBookInfo() {
        viewModelScope.launch {
            try {
                val memory = memoryData.value ?: return@launch
                
                // 获取书籍信息以获取最新的字数和分类
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(memory.bookUrl)
                }
                
                if (book != null) {
                    // 使用BookInfoSyncHelper同步书籍的字数和分类信息到我的阅读记录
                    BookInfoSyncHelper.syncBookWordCountAndKindToMemory(
                        book, memory, appDb
                    )
                    
                    // 更新UI，将字数转换为万字单位
                    withContext(Dispatchers.Main) {
                        val bookWordCount = if (book.wordCount.isNullOrEmpty()) "无" else book.wordCount ?: "无"
                        val formattedWordCount = if (bookWordCount != "无") {
                            // 解析字数并转换为万字单位
                            val totalWords = parseTotalWords(bookWordCount)
                            "${formatWordCount(totalWords)}万字"
                        } else {
                            "无"
                        }
                        wordCount?.value = formattedWordCount
                        bookKind?.value = if (book.kind.isNullOrEmpty()) "无" else book.kind ?: "无"
                        getApplication<Application>().toastOnUi("书籍信息已刷新")
                    }
                } else {
                    // 没有书籍信息，使用我的阅读记录的数据，并转换为万字单位
                    withContext(Dispatchers.Main) {
                        val memoryWordCount = if (memory.wordCount.isNullOrEmpty()) "无" else memory.wordCount ?: "无"
                        val formattedWordCount = if (memoryWordCount != "无") {
                            // 解析字数并转换为万字单位
                            val totalWords = parseTotalWords(memoryWordCount)
                            "${formatWordCount(totalWords)}万字"
                        } else {
                            "无"
                        }
                        wordCount?.value = formattedWordCount
                        bookKind?.value = if (memory.kind.isNullOrEmpty()) "无" else memory.kind ?: "无"
                        getApplication<Application>().toastOnUi("书籍信息已刷新（使用本地数据）")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    getApplication<Application>().toastOnUi("刷新失败: ${e.localizedMessage}")
                }
            }
        }
    }
    
    /**
     * 仅刷新书籍基本信息（保留原有的字数和分类）
     * 用于书源变更时更新书籍信息但保留用户自定义的字数和分类
     */
    fun refreshBookDataOnly() {
        viewModelScope.launch {
            try {
                val memory = memoryData.value ?: return@launch
                
                // 获取当前书籍信息
                val currentBook = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(memory.bookUrl)
                }
                
                if (currentBook != null) {
                    // 获取书源信息
                    val bookSource = withContext(Dispatchers.IO) {
                        appDb.bookSourceDao.getBookSource(currentBook.origin)
                    }
                    
                    // 判断书源是否属于"正版"分组
                    val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                    
                    // 根据书源分组决定是否保留字数和分类
                    if (isOfficialSource) {
                        // 正版书源，允许更新字数和分类
                        withContext(Dispatchers.Main) {
                            getApplication<Application>().toastOnUi("检测到正版书源，已更新字数和分类信息")
                        }
                        
                        // 使用BookInfoSyncHelper同步书籍的字数和分类信息到我的阅读记录
                        BookInfoSyncHelper.syncBookWordCountAndKindToMemory(
                            currentBook, memory, appDb
                        )
                        
                        // 更新UI
                        withContext(Dispatchers.Main) {
                            wordCount?.value = if (currentBook.wordCount.isNullOrEmpty()) "无" else currentBook.wordCount ?: "无"
                            bookKind?.value = if (currentBook.kind.isNullOrEmpty()) "无" else currentBook.kind ?: "无"
                        }
                        
                        // 重新加载书籍信息，包括字数和分类
                        loadMemoryProgress(memory, preserveBookInfo = false)
                    } else {
                        // 非正版书源，保留原有的字数和分类信息
                        val currentWordCount = wordCount.value
                        val currentKind = bookKind.value
                        
                        // 使用BookInfoSyncHelper同步我的阅读记录的字数和分类信息到书籍
                        BookInfoSyncHelper.syncMemoryWordCountAndKindToBook(
                            memory, currentBook, appDb
                        )
                        
                        withContext(Dispatchers.Main) {
                            getApplication<Application>().toastOnUi("非正版书源，已保留原有字数和分类信息")
                        }
                        
                        // 更新我的阅读记录信息，保留字数和分类
                        val updatedMemory = memory.copy(
                            wordCount = currentWordCount ?: "0",
                            kind = currentKind ?: "未分类"
                        )
                        
                        withContext(Dispatchers.IO) {
                            appDb.readingMemoryDao.update(updatedMemory)
                        }
                        
                        // 更新UI
                        memoryData.postValue(updatedMemory)
                        
                        // 确保LiveData的值与保留的值一致
                        wordCount?.postValue(currentWordCount ?: "无")
                        bookKind?.postValue(currentKind ?: "无")
                    }
                }
            } catch (e: Exception) {
                _errorMsg.postValue("刷新书籍信息失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 取消所有正在进行的协程任务
     */
    fun cancelAllJobs() {
        jobs.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        jobs.clear()
    }
    
    /**
     * 手动更新阅读状态
     */
    fun updateReadingStatus(memoryId: String, status: io.legado.app.constant.ReadingStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val readingMemory = appDb.readingMemoryDao.getMemoryById(memoryId)
                if (readingMemory != null) {
                    // 更新阅读记忆的阅读状态和userModifiedReadingStatus标记
                    val updatedMemory = readingMemory.copy(
                        readingStatus = status,
                        userModifiedReadingStatus = true,
                        updateTime = System.currentTimeMillis()
                    )
                    appDb.readingMemoryDao.update(updatedMemory)
                    
                    // 如果书籍存在，同时更新书籍的阅读状态
                    val book = appDb.bookDao.getBook(readingMemory.bookUrl)
                    if (book != null) {
                        val updatedBook = book.copy()
                        updatedBook.setReadingStatus(status, true) // userModified=true
                        appDb.bookDao.update(updatedBook)
                    }
                    
                    // 重新加载数据，更新UI
                    initData(memoryId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理从lb_kind获取的标签，实现分割、过滤和随机深色颜色功能
     */
    fun processKindTags(kindString: String?): List<Pair<String, Int>> {
        if (kindString.isNullOrBlank()) {
            return emptyList()
        }
        
        // 使用逗号或换行符分割标签
        val tags = kindString.split(Regex("[,\n]"))
            .map { it.trim() }
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
            if (!tag.matches(Regex("^[\u4e00-\u9fa5a-zA-Z0-9]+$"))) {
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
}