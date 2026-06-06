package io.legado.app.ui.book.readingmemory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.ReadingStatus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookAnnotation

import io.legado.app.data.entities.ReadingMemory
import io.legado.app.help.book.ReadingStatusGroupHelper
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadingMemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _readingMemories = MutableStateFlow<List<ReadingMemory>>(emptyList())
    val readingMemories: StateFlow<List<ReadingMemory>> = _readingMemories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 保存原始数据，用于筛选
    private var allReadingMemories: List<ReadingMemory> = emptyList()

    // 各状态的书籍数量
    private val _statusCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val statusCounts: StateFlow<Map<String, Int>> = _statusCounts.asStateFlow()

    // 当前的筛选状态
    private var currentFilterStatus: String? = null

    // 当前的搜索关键词
    private var currentSearchKeyword: String? = null
    
    // 当前的筛选条件，用于计算状态数量
    private var currentStatusFilter: String? = null
    private var currentReadTypeFilter: Int? = null
    private var currentRatingFilter: String? = "all"

    init {
        // 实时监听数据库变化
        viewModelScope.launch {
            appDb.readingMemoryDao.flowAll().collect {
                allReadingMemories = it
                // 重新应用当前的筛选状态和搜索关键词
                applyFilters()
                calculateStatusCounts(it)
            }
        }

        // 监听阅读会话更新事件，及时更新阅读记忆
        observeEvent<String>(EventBus.READ_SESSION_UPDATED) { bookName ->
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    // 根据书名查找对应的书籍
                    val books = appDb.bookDao.all.filter { it.name.contains(bookName, ignoreCase = true) }
                    if (books.isNotEmpty()) {
                        // 更新每本书的阅读记忆
                        books.forEach { book: Book ->
                            updateReadingMemoryFromBook(book)
                        }
                    }
                }
            }
        }
    }

    /**
     * 加载所有我的阅读记录
     * 显示所有曾经加入过书架的书籍的阅读记录，包括已删除的书籍
     */
    fun loadReadingMemories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val memories = withContext(Dispatchers.IO) {
                    // 获取所有我的阅读记录，包括已删除书籍的记忆
                    var allMemories = appDb.readingMemoryDao.all
                    
                    // 为没有 lastReadTime 的记录补充最后阅读时间
                    allMemories = allMemories.map { memory ->
                        if (memory.lastReadTime == 0L) {
                            try {
                                val sessions = appDb.readSessionDao.getByBook(memory.bookName, memory.bookAuthor)
                                val lastRead = sessions.maxOfOrNull { it.endTime } ?: 0L
                                if (lastRead > 0) {
                                    val updated = memory.copy(lastReadTime = lastRead)
                                    appDb.readingMemoryDao.update(updated)
                                    updated
                                } else {
                                    memory
                                }
                            } catch (e: Exception) {
                                memory
                            }
                        } else {
                            memory
                        }
                    }
                    
                    allMemories
                }
                allReadingMemories = memories
                // 重新应用筛选，包括搜索关键词
                applyFilters()

                // 计算各状态的书籍数量
                calculateStatusCounts(memories)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 将阅读记忆按年份分组
     * @param memories 要分组的阅读记忆列表
     * @param status 筛选状态
     * @return 分组后的阅读记忆列表
     */
    fun groupMemoriesByYear(memories: List<ReadingMemory>, status: String?): List<ReadingMemory> {
        // 先按照状态进行排序
        val sortedMemories = when (status) {
            "在读" -> {
                // 在读按照开始阅读时间排序（使用updateTime，因为开始阅读时间没有直接字段）
                memories.sortedByDescending { it.updateTime }
            }
            "待读" -> {
                // 待读按照加入书架时间排序（使用createTime）
                memories.sortedByDescending { it.createTime }
            }
            "弃文" -> {
                // 弃文按照弃文时间排序（使用updateTime）
                memories.sortedByDescending { it.updateTime }
            }
            "读完" -> {
                // 读完按照读完时间排序（使用updateTime）
                memories.sortedByDescending { it.updateTime }
            }
            else -> {
                // 全部默认按照更新时间排序
                memories.sortedByDescending { it.updateTime }
            }
        }

        return sortedMemories
    }

    /**
     * 计算各状态的书籍数量
     */
    private fun calculateStatusCounts(memories: List<ReadingMemory>) {
        val counts = mutableMapOf<String, Int>()

        // 初始化所有状态为0
        counts["全部"] = memories.size
        counts[ReadingStatus.PENDING.displayName] = 0
        counts[ReadingStatus.READING.displayName] = 0
        counts[ReadingStatus.FINISHED.displayName] = 0
        counts[ReadingStatus.ABANDONED.displayName] = 0

        // 统计各状态数量
        memories.forEach { memory ->
            when (memory.readingStatus) {
                ReadingStatus.PENDING -> counts[ReadingStatus.PENDING.displayName] = counts[ReadingStatus.PENDING.displayName]!! + 1
                ReadingStatus.READING -> counts[ReadingStatus.READING.displayName] = counts[ReadingStatus.READING.displayName]!! + 1
                ReadingStatus.FINISHED -> counts[ReadingStatus.FINISHED.displayName] = counts[ReadingStatus.FINISHED.displayName]!! + 1
                ReadingStatus.ABANDONED -> counts[ReadingStatus.ABANDONED.displayName] = counts[ReadingStatus.ABANDONED.displayName]!! + 1
            }
        }

        _statusCounts.value = counts
    }

    /**
     * 根据当前筛选条件计算各状态的书籍数量
     */
    private fun calculateStatusCountsWithFilters() {
        viewModelScope.launch {
            val filteredMemories = withContext(Dispatchers.IO) {
                // 1. 状态筛选
                var filtered = if (currentStatusFilter == null) {
                    allReadingMemories
                } else {
                    val targetStatus = when (currentStatusFilter) {
                        "未读", "待读", "待看" -> ReadingStatus.PENDING
                        "阅读中", "在读", "在看" -> ReadingStatus.READING
                        "已读完", "读完", "看完" -> ReadingStatus.FINISHED
                        "弃文", "弃读", "弃" -> ReadingStatus.ABANDONED
                        else -> null
                    }

                    if (targetStatus != null) {
                        allReadingMemories.filter { it.readingStatus == targetStatus }
                    } else {
                        allReadingMemories
                    }
                }

                // 2. 类型筛选
                if (currentReadTypeFilter != null) {
                    filtered = filtered.filter { memory ->
                        // 对于type为0的旧数据，尝试从Book中获取类型
                        val memoryType = if (memory.type == 0) {
                            // 尝试从Book中获取类型
                            val book = appDb.bookDao.getBook(memory.bookUrl)
                            book?.type ?: io.legado.app.constant.BookType.text
                        } else {
                            memory.type
                        }
                        (memoryType and currentReadTypeFilter!!) > 0
                    }
                }

                // 3. 评分筛选
                if (currentRatingFilter != "all") {
                    filtered = when (currentRatingFilter) {
                        "5" -> filtered.filter { it.rating >= 5 }
                        "4" -> filtered.filter { it.rating >= 4 && it.rating < 5 }
                        "3" -> filtered.filter { it.rating >= 3 && it.rating < 4 }
                        "2" -> filtered.filter { it.rating >= 2 && it.rating < 3 }
                        "1" -> filtered.filter { it.rating >= 1 && it.rating < 2 }
                        "unrated" -> filtered.filter { it.rating == 0.0f }
                        else -> filtered
                    }
                }

                filtered
            }

            calculateStatusCounts(filteredMemories)
        }
    }

    /**
     * 应用所有筛选条件（状态、搜索关键词、评分筛选）
     */
    private fun applyFilters() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val filteredMemories = withContext(Dispatchers.IO) {
                    // 首先根据状态筛选
                    var result = if (currentFilterStatus == null) {
                        allReadingMemories
                    } else {
                        // 状态字符串映射：将Activity传递的状态字符串转换为对应的ReadingStatus
                        val targetStatus = when (currentFilterStatus) {
                            "未读", "待读", "待看" -> ReadingStatus.PENDING
                            "阅读中", "在读", "在看" -> ReadingStatus.READING
                            "已读完", "读完", "看完" -> ReadingStatus.FINISHED
                            "弃文", "弃读", "弃" -> ReadingStatus.ABANDONED
                            else -> null
                        }

                        if (targetStatus != null) {
                            allReadingMemories.filter { memory ->
                                memory.readingStatus == targetStatus
                            }
                        } else {
                            allReadingMemories
                        }
                    }

                    // 然后根据搜索关键词筛选
                    if (!currentSearchKeyword.isNullOrEmpty()) {
                        val keyword = currentSearchKeyword!!.trim()
                        val keywordLower = keyword.lowercase()
                        // 先获取所有包含该标签的书籍URL
                        val bookUrlsWithTag = mutableSetOf<String>()
                        try {
                            // 查找精确匹配的标签（不区分大小写）
                            // 先尝试精确匹配
                            var tag = appDb.bookTagDao.getTagByName(keyword)
                            // 如果没找到，尝试不区分大小写查找
                            if (tag == null) {
                                val allTags = appDb.bookTagDao.getAll()
                                tag = allTags.find { it.name.equals(keyword, ignoreCase = true) }
                            }
                            if (tag != null) {
                                val relations = appDb.bookTagRelationDao.getRelationsByTag(tag.id)
                                bookUrlsWithTag.addAll(relations.map { it.bookUrl })
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        result = result.filter { memory ->
                            if (bookUrlsWithTag.isNotEmpty()) {
                                // 如果找到了对应的标签，只检查书籍是否包含该标签
                                bookUrlsWithTag.contains(memory.bookUrl)
                            } else {
                                // 如果没找到对应的标签，按关键词搜索书名、作者、分类
                                memory.bookName.lowercase().contains(keywordLower) ||
                                memory.bookAuthor.lowercase().contains(keywordLower) ||
                                (memory.kind?.lowercase()?.contains(keywordLower) ?: false)
                            }
                        }
                    }

                    result
                }

                // 按年份分组并排序
                _readingMemories.value = groupMemoriesByYear(filteredMemories, currentFilterStatus)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 获取筛选、排序和分组后的数据
     */
    fun getFilteredAndSortedMemories(
        status: String?,
        sortBy: String,
        groupBy: String,
        ratingFilter: String,
        ratingSort: String,
        readType: Int?
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 更新当前的筛选条件
                currentStatusFilter = status
                currentReadTypeFilter = readType
                currentRatingFilter = ratingFilter
                
                val result = withContext(Dispatchers.IO) {
                    // 1. 状态筛选
                    var filtered = if (status == null) {
                        allReadingMemories
                    } else {
                        val targetStatus = when (status) {
                            "未读", "待读", "待看" -> ReadingStatus.PENDING
                            "阅读中", "在读", "在看" -> ReadingStatus.READING
                            "已读完", "读完", "看完" -> ReadingStatus.FINISHED
                            "弃文", "弃读", "弃" -> ReadingStatus.ABANDONED
                            else -> null
                        }

                        if (targetStatus != null) {
                            allReadingMemories.filter { it.readingStatus == targetStatus }
                        } else {
                            allReadingMemories
                        }
                    }

                    // 2. 类型筛选
                    if (readType != null) {
                        filtered = filtered.filter { memory ->
                            // 根据readType筛选阅读记忆
                            // 对于type为0的旧数据，尝试从Book中获取类型
                            val memoryType = if (memory.type == 0) {
                                // 尝试从Book中获取类型
                                val book = appDb.bookDao.getBook(memory.bookUrl)
                                book?.type ?: io.legado.app.constant.BookType.text
                            } else {
                                memory.type
                            }
                            (memoryType and readType) > 0
                        }
                    }

                    // 3. 评分筛选
                    if (ratingFilter != "all") {
                        filtered = when (ratingFilter) {
                            "5" -> filtered.filter { it.rating >= 5 }
                            "4" -> filtered.filter { it.rating >= 4 && it.rating < 5 }
                            "3" -> filtered.filter { it.rating >= 3 && it.rating < 4 }
                            "2" -> filtered.filter { it.rating >= 2 && it.rating < 3 }
                            "1" -> filtered.filter { it.rating >= 1 && it.rating < 2 }
                            "unrated" -> filtered.filter { it.rating == 0.0f }
                            else -> filtered
                        }
                    }

                    // 4. 分组
                    val grouped = when (groupBy) {
                        "year" -> groupByYear(filtered)
                        "rating" -> groupByRating(filtered)
                        "status" -> groupByStatus(filtered)
                        else -> mapOf("" to filtered)
                    }

                    // 5. 组内排序
                    val sortedGroups = grouped.mapValues { (_, memories) ->
                        sortMemories(memories, sortBy, ratingSort)
                    }

                    // 6. 组间排序
                    sortGroups(sortedGroups, groupBy)
                }

                _readingMemories.value = result
                
                // 重新计算状态数量
                calculateStatusCountsWithFilters()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 按年份分组
     */
    private fun groupByYear(memories: List<ReadingMemory>): Map<String, List<ReadingMemory>> {
        return memories.groupBy { memory ->
            val date = java.util.Date(memory.updateTime)
            val calendar = java.util.Calendar.getInstance()
            calendar.time = date
            calendar.get(java.util.Calendar.YEAR).toString()
        }
    }

    /**
     * 按评分等级分组
     */
    private fun groupByRating(memories: List<ReadingMemory>): Map<String, List<ReadingMemory>> {
        return memories.groupBy { memory ->
            when {
                memory.rating >= 5 -> "★★★★★"
                memory.rating >= 4 -> "★★★★☆"
                memory.rating >= 3 -> "★★★☆☆"
                memory.rating >= 2 -> "★★☆☆☆"
                memory.rating >= 1 -> "★☆☆☆☆"
                else -> "未评分"
            }
        }
    }

    /**
     * 按阅读状态分组
     */
    private fun groupByStatus(memories: List<ReadingMemory>): Map<String, List<ReadingMemory>> {
        return memories.groupBy { it.readingStatus.displayName }
    }

    /**
     * 对记忆列表进行排序
     */
    private fun sortMemories(memories: List<ReadingMemory>, sortBy: String, ratingSort: String): List<ReadingMemory> {
        return when (sortBy) {
            "lastRead_desc" -> memories.sortedByDescending { it.lastReadTime }
            "lastRead_asc" -> memories.sortedBy { it.lastReadTime }
            "added_desc" -> memories.sortedByDescending { it.createTime }
            "added_asc" -> memories.sortedBy { it.createTime }
            else -> {
                // 默认按评分排序
                if (ratingSort == "high_to_low") {
                    memories.sortedByDescending { it.rating }
                } else {
                    memories.sortedBy { it.rating }
                }
            }
        }
    }

    /**
     * 对分组进行排序
     */
    private fun sortGroups(groups: Map<String, List<ReadingMemory>>, groupBy: String): List<ReadingMemory> {
        val result = mutableListOf<ReadingMemory>()

        // 按不同的分组方式排序
        val sortedKeys = when (groupBy) {
            "year" -> groups.keys.sortedDescending() // 年份从新到旧
            "rating" -> {
                // 评分从高到低
                val ratingOrder = listOf("★★★★★", "★★★★☆", "★★★☆☆", "★★☆☆☆", "★☆☆☆☆", "未评分")
                groups.keys.sortedBy { ratingOrder.indexOf(it) }
            }
            "status" -> {
                // 状态顺序：在读 -> 待读 -> 已读 -> 弃文
                val statusOrder = listOf("在读", "待读", "读完", "弃文")
                groups.keys.sortedBy { statusOrder.indexOf(it) }
            }
            else -> groups.keys.toList()
        }

        // 按排序后的顺序添加到结果列表
        sortedKeys.forEach { key ->
            groups[key]?.let { result.addAll(it) }
        }

        return result
    }

    /**
     * 根据阅读状态筛选我的阅读记录
     */
    fun filterReadingMemoriesByStatus(status: String?) {
        currentFilterStatus = status
        applyFilters()
    }

    /**
     * 根据关键词搜索我的阅读记录
     */
    fun searchReadingMemories(keyword: String?) {
        currentSearchKeyword = keyword
        applyFilters()
    }
    
    /**
     * 根据标签名称搜索我的阅读记录
     */
    fun searchReadingMemoriesByTag(tagName: String?) {
        currentSearchKeyword = tagName
        applyFilters()
    }

    /**
     * 根据书籍更新我的阅读记录（专门用于同步阅读进度）
     */
    fun updateReadingMemoryFromBook(book: Book) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 检查是否已存在该书籍的我的阅读记录
                var existingMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)

                // 如果没有找到匹配bookUrl的记忆，尝试查找相同书名和作者的记忆
                if (existingMemory == null) {
                    val memoriesByBook = appDb.readingMemoryDao.getByBook(book.name, book.author)
                    if (memoriesByBook.isNotEmpty()) {
                        // 使用最新的记忆（按更新时间降序排列，第一个是最新的）
                        existingMemory = memoriesByBook.first()
                    }
                }

                // 获取所有相关的书评，包括相同书名和作者的书评
                val reviewList = appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
                val reviewByBook = appDb.bookReviewDao.getReviewByBook(book.name, book.author)
                val allReviews = reviewList + reviewByBook
                val review = allReviews.firstOrNull()

                // 获取实际阅读时间，而不是使用章节阅读时间
                val readTime = try {
                    // 1. 首先从readSession表中获取总阅读时间
                    var time = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L

                    if (time == 0L) {
                        // 2. 如果还是没有，使用bookUrl查询
                        time = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl) ?: 0L
                    }

                    time
                } catch (e: Exception) {
                    0L
                }

                // 获取最后阅读时间
                val lastReadTime = try {
                    val sessions = appDb.readSessionDao.getByBook(book.name, book.author)
                    sessions.maxOfOrNull { it.endTime } ?: 0L
                } catch (e: Exception) {
                    0L
                }

                // 获取书摘数量，包括相同书名和作者的书摘
                val annotations = appDb.bookAnnotationDao.getByBook(book.name, book.author)

                // 计算阅读进度 - 使用与详情页一致的逻辑
                val progress = calculateReadingProgress(book)

                // 计算阅读状态 - 使用统一的阅读状态计算逻辑
                val readingStatus = io.legado.app.help.book.ReadingProgressHelper.calculateReadingStatus(book)

                if (existingMemory != null) {
                    // 如果现有记忆是用户手动修改过状态，保持原状态不变
                    val finalReadingStatus = if (existingMemory.userModifiedReadingStatus) {
                        existingMemory.readingStatus
                    } else {
                        readingStatus
                    }

                    // 更新现有记忆
                    val updatedMemory = existingMemory.copy(
                        bookUrl = book.bookUrl, // 更新为新的bookUrl
                        bookName = book.name,
                        bookAuthor = book.author,
                        wordCount = book.wordCount,
                        kind = book.kind,
                        coverUrl = book.getDisplayCover(), // 使用getDisplayCover获取封面URL，包含用户自定义封面
                        intro = book.intro,
                        rating = book.rating,
                        totalChapterNum = book.totalChapterNum,
                        durChapterIndex = book.durChapterIndex,
                        durChapterTitle = book.durChapterTitle,
                        durChapterPos = book.durChapterPos,
                        progress = progress,
                        readTime = readTime,
                        annotationCount = annotations.size,
                        readingStatus = finalReadingStatus,
                        type = book.type, // 更新书籍类型
                        updateTime = book.durChapterTime,
                        lastReadTime = lastReadTime
                    )
                    appDb.readingMemoryDao.update(updatedMemory)

                    // 删除相同书名和作者的其他旧记忆，避免重复
                    appDb.readingMemoryDao.deleteByBookNameAndAuthorExcept(book.name, book.author, updatedMemory.id)
                } else {
                    // 创建新记忆
                    val memory = ReadingMemory(
                        id = "memory_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                        bookUrl = book.bookUrl,
                        bookName = book.name,
                        bookAuthor = book.author,
                        wordCount = book.wordCount,
                        kind = book.kind,
                        coverUrl = book.getDisplayCover(), // 使用getDisplayCover获取封面URL，包含用户自定义封面
                        intro = book.intro,
                        rating = book.rating,
                        totalChapterNum = book.totalChapterNum,
                        durChapterIndex = book.durChapterIndex,
                        durChapterTitle = book.durChapterTitle,
                        durChapterPos = book.durChapterPos,
                        progress = progress,
                        readTime = readTime,
                        annotationCount = annotations.size,
                        readingStatus = readingStatus,
                        type = book.type, // 设置书籍类型
                        createTime = book.durChapterTime,
                        updateTime = book.durChapterTime,
                        lastReadTime = lastReadTime
                    )
                    appDb.readingMemoryDao.insert(memory)

                    // 删除相同书名和作者的其他旧记忆，避免重复
                    appDb.readingMemoryDao.deleteByBookNameAndAuthorExcept(book.name, book.author, memory.id)
                }

                // 不重新加载记忆列表，避免频繁刷新UI
            }
        }
    }

    /**
     * 根据书籍信息创建我的阅读记录
     */
    fun createReadingMemoryFromBook(book: Book): ReadingMemory? {
        var result: ReadingMemory? = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 检查是否已存在该书的我的阅读记录
                var existingMemory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)

                // 如果没有找到匹配bookUrl的记忆，尝试查找相同书名和作者的记忆
                if (existingMemory == null) {
                    val memoriesByBook = appDb.readingMemoryDao.getByBook(book.name, book.author)
                    if (memoriesByBook.isNotEmpty()) {
                        // 使用最新的记忆（按更新时间降序排列，第一个是最新的）
                        existingMemory = memoriesByBook.first()

                        // 更新现有记忆的信息
                        val readTime = getBookReadTime(book)
                        val annotations = getBookAnnotations(book)

                        // 计算阅读进度 - 使用与详情页一致的逻辑
                        val progress = calculateReadingProgress(book)

                        // 计算阅读状态
                        val calculatedStatus = calculateReadingStatus(progress, readTime, book.durChapterIndex, book.durChapterPos)

                        // 如果现有记忆是用户手动修改过状态，保持原状态不变
                        val finalReadingStatus = if (existingMemory.userModifiedReadingStatus) {
                            existingMemory.readingStatus
                        } else {
                            calculatedStatus
                        }

                        // 更新现有的我的阅读记录
                        val updatedMemory = existingMemory.copy(
                            bookUrl = book.bookUrl,
                            bookName = book.name,
                            bookAuthor = book.author,
                            wordCount = book.wordCount,
                            kind = book.kind,
                            coverUrl = book.getDisplayCover(),
                            intro = book.intro,
                            rating = book.rating,
                            totalChapterNum = book.totalChapterNum,
                            durChapterIndex = book.durChapterIndex,
                            durChapterTitle = book.durChapterTitle,
                            durChapterPos = book.durChapterPos,
                            progress = progress,
                            readTime = readTime,
                            annotationCount = annotations.size,
                            readingStatus = finalReadingStatus,
                            updateTime = System.currentTimeMillis()
                        )

                        appDb.readingMemoryDao.update(updatedMemory)
                        result = updatedMemory

                        // 删除相同书名和作者的其他旧记忆，避免重复
                        appDb.readingMemoryDao.deleteByBookNameAndAuthorExcept(book.name, book.author, updatedMemory.id)
                    }
                } else {
                    // 已经存在匹配bookUrl的记忆，更新它
                    val readTime = getBookReadTime(book)
                    val annotations = getBookAnnotations(book)

                    // 计算阅读进度 - 使用与详情页一致的逻辑
                    val progress = calculateReadingProgress(book)

                    // 计算阅读状态
                        val calculatedStatus = calculateReadingStatus(progress, readTime, book.durChapterIndex, book.durChapterPos)

                    // 如果现有记忆是用户手动修改过状态，保持原状态不变
                    val finalReadingStatus = if (existingMemory.userModifiedReadingStatus) {
                        existingMemory.readingStatus
                    } else {
                        calculatedStatus
                    }

                    // 更新现有的我的阅读记录
                    val updatedMemory = existingMemory.copy(
                        bookName = book.name,
                        bookAuthor = book.author,
                        wordCount = book.wordCount,
                        kind = book.kind,
                        coverUrl = book.getDisplayCover(),
                        intro = book.intro,
                        rating = book.rating,
                        totalChapterNum = book.totalChapterNum,
                        durChapterIndex = book.durChapterIndex,
                        durChapterTitle = book.durChapterTitle,
                        durChapterPos = book.durChapterPos,
                        progress = progress,
                        readTime = readTime,
                        annotationCount = annotations.size,
                        readingStatus = finalReadingStatus,
                        type = book.type,
                        updateTime = System.currentTimeMillis()
                    )

                    appDb.readingMemoryDao.update(updatedMemory)
                    result = updatedMemory
                }

                if (existingMemory == null) {
                    // 如果仍然没有找到现有记忆，创建新记忆
                    val readTime = getBookReadTime(book)
                    val annotations = getBookAnnotations(book)

                    // 计算阅读进度 - 使用与详情页一致的逻辑
                    val progress = calculateReadingProgress(book)

                    // 计算阅读状态
                    val readingStatus = calculateReadingStatus(progress, readTime, book.durChapterIndex, book.durChapterPos)

                    // 创建新记忆
                    val memory = ReadingMemory(
                        id = "memory_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                        bookUrl = book.bookUrl,
                        bookName = book.name,
                        bookAuthor = book.author,
                        wordCount = book.wordCount,
                        kind = book.kind,
                        coverUrl = book.getDisplayCover(), // 使用getDisplayCover获取封面URL，包含用户自定义封面
                        intro = book.intro,
                        rating = book.rating,
                        totalChapterNum = book.totalChapterNum,
                        durChapterIndex = book.durChapterIndex,
                        durChapterTitle = book.durChapterTitle,
                        durChapterPos = book.durChapterPos,
                        progress = progress,
                        readTime = readTime,
                        annotationCount = annotations.size,
                        readingStatus = readingStatus,
                        type = book.type,
                        createTime = System.currentTimeMillis(),
                        updateTime = System.currentTimeMillis()
                    )

                    appDb.readingMemoryDao.insert(memory)
                    result = memory

                    // 删除相同书名和作者的其他旧记忆，避免重复
                    appDb.readingMemoryDao.deleteByBookNameAndAuthorExcept(book.name, book.author, memory.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }



    /**
     * 根据ID删除我的阅读记录
     */
    fun deleteReadingMemoryById(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 获取要删除的我的阅读记录，以便获取bookUrl
                val memory = appDb.readingMemoryDao.getById(id)

                // 删除我的阅读记录
                appDb.readingMemoryDao.deleteById(id)

                // 如果记忆存在，删除相关的书评和书摘
                memory?.let {
                    // 删除相关的书评
                    appDb.bookReviewDao.deleteByBookUrl(it.bookUrl)
                    // 删除相关的书摘
                    appDb.bookAnnotationDao.deleteByBook(it.bookName, it.bookAuthor)
                }

                // 重新加载记录列表（这会自动更新状态计数）
                loadReadingMemories()
            }
        }
    }

    /**
     * 清空所有我的阅读记录
     */
    fun clearAllReadingMemories() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 清空所有我的阅读记录
                appDb.readingMemoryDao.deleteAll()
                // 重新加载记录列表（这会自动更新状态计数）
                loadReadingMemories()
            }
        }
    }



    /**
     * 获取书籍的阅读时间
     */
    private suspend fun getBookReadTime(book: Book): Long {
        return try {
            // 1. 优先从readSession表获取总阅读时间
            var time = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L

            if (time == 0L) {
                // 2. 如果readSession中没有，使用bookName查询所有记录的总和
                time = appDb.readSessionDao.getTotalReadTime(book.name) ?: 0L
            }

            if (time == 0L) {
                // 3. 作为最后的备选，使用bookUrl查询
                time = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl) ?: 0L
            }

            time
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取书籍的书摘
     */
    private fun getBookAnnotations(book: Book): List<BookAnnotation> {
        return try {
            appDb.bookAnnotationDao.getByBook(book.name, book.author)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 根据阅读进度计算阅读状态
     */
    private fun calculateReadingStatus(progress: Float, readTime: Long, durChapterIndex: Int = 0, durChapterPos: Int = 0): ReadingStatus {
        // 检查用户是否真正阅读过这本书
        val hasRead = durChapterIndex > 0 || durChapterPos > 0

        return when {
            progress >= 100f -> ReadingStatus.FINISHED
            progress > 0f || hasRead -> ReadingStatus.READING  // 进度>0%或有阅读记录为在读
            else -> ReadingStatus.PENDING  // 进度为0%且无阅读记录时显示为"未读"
        }
    }

    /**
     * 计算阅读进度 - 使用统一的阅读进度计算逻辑
     */
    private fun calculateReadingProgress(book: Book): Float {
        return io.legado.app.help.book.ReadingProgressHelper.calculateReadingProgress(book)
    }

    /**
     * 更新我的阅读记录的阅读状态
     * 根据当前进度和阅读时间自动计算并更新阅读状态
     */
    fun updateReadingStatus(memoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val readingMemory = appDb.readingMemoryDao.getMemoryById(memoryId)
                if (readingMemory != null) {
                    // 如果当前状态是弃文，不自动更新
                    if (readingMemory.readingStatus == ReadingStatus.ABANDONED) {
                        return@launch
                    }

                    // 计算新的阅读状态
                    val newStatus = calculateReadingStatus(readingMemory.progress, readingMemory.readTime, readingMemory.durChapterIndex, readingMemory.durChapterPos)

                    // 如果状态有变化，更新数据库
                    if (readingMemory.readingStatus != newStatus) {
                        val updatedMemory = readingMemory.copy(
                            readingStatus = newStatus,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

                    // 重新加载数据
                    loadReadingMemories()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 批量更新所有我的阅读记录的封面信息
     */
    fun updateAllMemoryCovers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allMemories = appDb.readingMemoryDao.all

                allMemories.forEach { memory ->
                    // 获取书籍封面URL
                    val coverUrl = getBookCover(memory.bookUrl)

                    // 只有当封面URL不为空且与当前值不同时才更新，保留原有封面URL
                    if (coverUrl != null && memory.coverUrl != coverUrl) {
                        val updatedMemory = memory.copy(
                            coverUrl = coverUrl,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
                }

                // 重新加载记录列表
                loadReadingMemories()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 批量更新所有我的阅读记录的阅读状态
     */
    fun updateAllReadingStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allMemories = appDb.readingMemoryDao.all

                allMemories.forEach { memory ->
                    // 如果当前状态是弃文，不自动更新
                    if (memory.readingStatus == ReadingStatus.ABANDONED) {
                        return@forEach
                    }

                    // 计算新的阅读状态
                    val newStatus = calculateReadingStatus(memory.progress, memory.readTime, memory.durChapterIndex, memory.durChapterPos)

                    // 如果状态有变化，更新数据库
                    if (memory.readingStatus != newStatus) {
                        val updatedMemory = memory.copy(
                            readingStatus = newStatus,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
                }

                // 重新加载记录列表（这会自动更新状态计数）
                loadReadingMemories()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取书籍封面URL
     * 根据书籍URL获取书籍封面URL
     */
    suspend fun getBookCover(bookUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val book = appDb.bookDao.getBook(bookUrl)
                book?.getDisplayCover()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 设置书籍为弃文状态
     */
    suspend fun setBookAsAbandoned(memoryId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val readingMemory = appDb.readingMemoryDao.getMemoryById(memoryId) ?: return@withContext false
                val book = appDb.bookDao.getBook(readingMemory.bookUrl) ?: return@withContext false

                // 更新阅读记录的状态
                val updatedReadingMemory = readingMemory.copy(readingStatus = ReadingStatus.ABANDONED)
                appDb.readingMemoryDao.update(updatedReadingMemory)

                // 更新书籍状态和分组
                book.readingStatus = ReadingStatus.ABANDONED.value
                book.userModifiedReadingStatus = true
                appDb.bookDao.update(book)

                // 更新书籍分组，使用forceUpdate=true确保更新
                ReadingStatusGroupHelper.updateBookGroupByReadingStatus(readingMemory.bookUrl, ReadingStatus.ABANDONED, forceUpdate = true)

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 取消书籍的弃文标记
     */
    suspend fun removeAbandonedStatus(memoryId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val readingMemory = appDb.readingMemoryDao.getMemoryById(memoryId) ?: return@withContext false
                val book = appDb.bookDao.getBook(readingMemory.bookUrl) ?: return@withContext false

                // 计算新的阅读状态
                val newStatus = calculateReadingStatus(readingMemory.progress, readingMemory.readTime, readingMemory.durChapterIndex, readingMemory.durChapterPos)

                // 更新阅读记录的状态
                val updatedReadingMemory = readingMemory.copy(readingStatus = newStatus)
                appDb.readingMemoryDao.update(updatedReadingMemory)

                // 更新书籍状态和分组
                book.readingStatus = newStatus.value
                book.userModifiedReadingStatus = false // 取消用户修改标记，允许自动更新
                appDb.bookDao.update(book)

                // 更新书籍分组，使用forceUpdate=true确保更新
                ReadingStatusGroupHelper.updateBookGroupByReadingStatus(readingMemory.bookUrl, newStatus, forceUpdate = true)

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 删除我的阅读记录
     * 根据书籍URL删除记录
     */
    fun deleteReadingMemory(bookUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 根据bookUrl查找并删除我的阅读记录
                    val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                    if (memory != null) {
                        appDb.readingMemoryDao.delete(memory)
                        loadReadingMemories()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}