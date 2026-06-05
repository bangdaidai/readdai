package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.hutool.core.date.DateUtil
import io.legado.app.data.entities.CoverCalendarDayData
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.legado.app.data.appDb
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

data class ReadRecordUiState(
        val isLoading: Boolean = true,
        val totalReadTime: Long = 0,
        //每日聚合明细
        val groupedRecords: Map<String, List<ReadRecordDetail>> = emptyMap(),
        //每日所有阅读会话
        val timelineRecords: Map<String, List<ReadSession>> = emptyMap(),
        //最后阅读列表
        val latestRecords: List<ReadRecord> = emptyList(),
        //累计阅读时长列表
        val totalTimeRecords: List<ReadRecord> = emptyList(),
        val selectedDate: LocalDate? = null,
        val searchKey: String? = null,
        val displayMode: DisplayMode = DisplayMode.AGGREGATE,
        val readType: Int? = null // 阅读类型：null（全部）、text（文字）、audio（音频）、video（视频）
    )

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST,
    TOTAL_TIME
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadRecordViewModel(
    private val repository: ReadRecordRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _displayMode = MutableStateFlow(DisplayMode.AGGREGATE)
    val displayMode = _displayMode.asStateFlow()
    private val _searchKey = MutableStateFlow("")
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _readType = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedDataFlow = _readType
        .flatMapLatest { readType ->
            combine(
                repository.getAllSessions(readType),
                repository.getTotalReadTimeFromSessions(readType)
            ) { sessions, totalTime ->
                LoadedData(totalTime, sessions)
            }
        }

    val uiState: StateFlow<ReadRecordUiState> = combine(
        loadedDataFlow,
        _selectedDate,
        _searchKey,
        _displayMode,
        _readType
    ) { data, selectedDate, searchKey, displayMode, readType ->
        val dateStr = selectedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 先获取过滤后的会话数据
        val filteredSessions = filterSessions(data.sessions, dateStr, searchKey)

        // 生成时间线数据（包含会话合并）
        val timelineMap = generateTimelineRecords(filteredSessions)

        // 从原始会话生成groupedRecords，确保与latestRecords和总阅读时长一致
        val groupedRecords = generateGroupedRecords(filteredSessions)

        // 从过滤后的sessions生成latestRecords
        val latestRecords = generateLatestRecords(filteredSessions)
        
        // 从过滤后的sessions生成totalTimeRecords
        val totalTimeRecords = generateTotalTimeRecords(filteredSessions)

        // 计算过滤后的总阅读时间
        val filteredTotalTime = calculateTotalReadTime(filteredSessions)

        ReadRecordUiState(
            isLoading = false,
            totalReadTime = filteredTotalTime,
            groupedRecords = groupedRecords,
            timelineRecords = timelineMap,
            latestRecords = latestRecords,
            totalTimeRecords = totalTimeRecords,
            selectedDate = selectedDate,
            searchKey = searchKey,
            displayMode = displayMode,
            readType = readType
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadRecordUiState(isLoading = true)
    )

    /**
     * 过滤会话数据
     */
    private fun filterSessions(
        sessions: List<ReadSession>,
        dateStr: String?,
        searchKey: String
    ): List<ReadSession> {
        return sessions
            .asSequence()
            .filter { session ->
                val sDate = DateUtil.format(Date(session.startTime), "yyyy-MM-dd")
                (dateStr == null || sDate == dateStr) &&
                        (searchKey.isEmpty() || session.bookName.contains(
                            searchKey,
                            ignoreCase = true
                        ))
            }
            .toList()
    }

    /**
     * 生成时间线记录
     */
    private fun generateTimelineRecords(sessions: List<ReadSession>): Map<String, List<ReadSession>> {
        return sessions
            .groupBy { DateUtil.format(Date(it.startTime), "yyyy-MM-dd") }
            .mapValues { (_, sessions) ->
                mergeContinuousSessions(sessions).reversed()
            }
            .toSortedMap(compareByDescending { it }) // 按日期降序排序，今天在上，昨天在下
    }

    /**
     * 生成分组记录
     */
    private fun generateGroupedRecords(sessions: List<ReadSession>): Map<String, List<ReadRecordDetail>> {
        val mergedByBook = mergeContinuousByBook(sessions)
        return mergedByBook.flatMap { (_, bookMergedSessions) -> bookMergedSessions }
            .groupBy { DateUtil.format(Date(it.startTime), "yyyy-MM-dd") }
            .mapValues { (date, sessions) ->
                sessions.groupBy { it.bookName }.map { (bookName, bookSessions) ->
                    ReadRecordDetail(
                        deviceId = "local",
                        bookName = bookName,
                        date = date,
                        readTime = bookSessions.sumOf { it.duration },
                        readWords = bookSessions.sumOf { it.words },
                        firstReadTime = bookSessions.minByOrNull { it.startTime }?.startTime ?: 0,
                        lastReadTime = bookSessions.maxByOrNull { it.endTime }?.endTime ?: 0,
                        coverUrl = bookSessions.lastOrNull()?.coverUrl ?: ""
                    )
                }
            }
            .toSortedMap(compareByDescending { it })
    }

    private fun generateLatestRecords(sessions: List<ReadSession>): List<ReadRecord> {
        val mergedByBook = mergeContinuousByBook(sessions)
        return mergedByBook.flatMap { (_, bookMergedSessions) -> bookMergedSessions }
            .groupBy { it.bookName }
            .map { (bookName, bookSessions) ->
                ReadRecord(
                    deviceId = "local",
                    bookName = bookName,
                    readTime = bookSessions.sumOf { it.duration },
                    lastRead = bookSessions.maxByOrNull { it.endTime }?.endTime ?: 0,
                    coverUrl = bookSessions.lastOrNull()?.coverUrl ?: ""
                )
            }
            .sortedByDescending { it.lastRead }
    }
    
    private fun generateTotalTimeRecords(sessions: List<ReadSession>): List<ReadRecord> {
        val mergedByBook = mergeContinuousByBook(sessions)
        return mergedByBook.flatMap { (_, bookMergedSessions) -> bookMergedSessions }
            .groupBy { it.bookName }
            .map { (bookName, bookSessions) ->
                ReadRecord(
                    deviceId = "local",
                    bookName = bookName,
                    readTime = bookSessions.sumOf { it.duration },
                    lastRead = bookSessions.maxByOrNull { it.endTime }?.endTime ?: 0,
                    coverUrl = bookSessions.lastOrNull()?.coverUrl ?: ""
                )
            }
            .sortedByDescending { it.readTime }
    }

    /**
     * 计算总阅读时间
     */
    private fun calculateTotalReadTime(sessions: List<ReadSession>): Long {
        return sessions.sumOf { it.duration }
    }

    fun setSearchKey(query: String) {
        _searchKey.value = query
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun setSelectedDate(date: LocalDate?) {
        _selectedDate.value = date
    }
    
    fun setReadType(type: Int?) {
        _readType.value = type
    }
    
    /**
     * 立即刷新数据（不等待轮询）
     * 用于封面更新等需要立即刷新的场景
     */
    fun refreshData() {
        // 通过重新设置 readType 来触发 Flow 重新加载数据
        _readType.value = _readType.value
    }

    fun deleteDetail(detail: ReadRecordDetail) {
        viewModelScope.launch {
            repository.deleteDetail(detail)
        }
    }

    fun deleteSession(session: ReadSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    /**
     * 批量删除合并后的阅读会话
     * 删除该合并记录时间范围内的所有原始记录
     */
    fun deleteMergedSession(session: ReadSession) {
        viewModelScope.launch {
            repository.deleteMergedSession(session)
        }
    }

    fun deleteReadRecord(record: ReadRecord) {
        viewModelScope.launch {
            repository.deleteReadRecord(record)
        }
    }

    companion object {
        val CONTINUOUS_GAP_LIMIT = 5 * 60 * 1000L
    }

    private fun mergeContinuousSessions(sessions: List<ReadSession>): List<ReadSession> {
        if (sessions.isEmpty()) return emptyList()
        val sortedSessions = sessions.sortedBy { it.startTime }
        val mergedList = mutableListOf<ReadSession>()
        mergedList.add(sortedSessions.first().copy())

        for (i in 1 until sortedSessions.size) {
            val current = sortedSessions[i]
            val last = mergedList.last()
            if (current.bookName == last.bookName && (current.startTime - last.endTime) <= CONTINUOUS_GAP_LIMIT) {
                val newDuration = last.duration + current.duration
                mergedList[mergedList.lastIndex] = last.copy(
                    endTime = current.endTime,
                    duration = newDuration,
                    durChapterTitle = current.durChapterTitle
                )
            } else {
                mergedList.add(current.copy())
            }
        }
        return mergedList
    }

    private fun mergeContinuousByBook(sessions: List<ReadSession>): Map<String, List<ReadSession>> {
        return sessions.groupBy { it.bookName }.mapValues { (_, bookSessions) ->
            mergeContinuousSessions(bookSessions)
        }
    }

    suspend fun getChapterTitle(bookName: String, chapterIndexLong: Long): String? {
        return bookRepository.getChapterTitle(bookName, chapterIndexLong.toInt())
    }

    suspend fun getBookCover(bookName: String): String? {
        val coverFromSession = bookRepository.getSessionCover(bookName)
        if (coverFromSession != null) {
            return coverFromSession
        }
        val coverFromBook = bookRepository.getBookCoverByName(bookName)
        if (coverFromBook != null) {
            return coverFromBook
        }
        return withContext(Dispatchers.IO) {
            appDb.readingMemoryDao.getByBookName(bookName).firstOrNull()?.coverUrl
        }
    }

    private data class LoadedData(
        val totalTime: Long,
        val sessions: List<ReadSession>
    )

    suspend fun getCoverCalendarData(year: Int, month: Int): List<CoverCalendarDayData> {
        return repository.getMonthlyCoverCalendarData(year, month, _readType.value)
    }

    /**
     * 更新书籍名称
     */
    fun updateBookName(oldBookName: String, newBookName: String) {
        viewModelScope.launch {
            repository.updateBookName(oldBookName, newBookName)
        }
    }

    /**
     * 更新书籍封面
     */
    fun updateCoverUrl(bookName: String, coverUrl: String) {
        viewModelScope.launch {
            repository.updateCoverUrl(bookName, coverUrl)
        }
    }

    /**
     * 更新阅读会话的章节标题
     */
    fun updateChapterTitle(bookName: String, chapterTitle: String, startTime: Long, endTime: Long) {
        viewModelScope.launch {
            repository.updateChapterTitle(bookName, chapterTitle, startTime, endTime)
        }
    }

    /**
     * 更新书籍的显示名称
     */
    fun updateDisplayName(bookName: String, displayName: String) {
        viewModelScope.launch {
            repository.updateDisplayName(bookName, displayName)
        }
    }
}
