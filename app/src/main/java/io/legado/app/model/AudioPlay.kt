package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.service.DataSyncService
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.getBookSource
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.toReplaceBook
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.globalExecutor
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.AudioPlayService
import io.legado.app.model.SourceCallBack
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import splitties.init.appCtx
import java.util.concurrent.atomic.AtomicLong
import kotlin.text.trim
import io.legado.app.ui.book.readingmemory.ReadingMemoryViewModel

@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object AudioPlay : CoroutineScope by MainScope() {
    /**
     * 播放模式枚举
     */
    enum class PlayMode(val iconRes: Int) {
        LIST_END_STOP(R.drawable.ic_play_mode_list_end_stop),
        SINGLE_LOOP(R.drawable.ic_play_mode_single_loop),
        RANDOM(R.drawable.ic_play_mode_random),
        LIST_LOOP(R.drawable.ic_play_mode_list_loop);

        fun next(): PlayMode {
            return when (this) {
                LIST_END_STOP -> SINGLE_LOOP
                SINGLE_LOOP -> RANDOM
                RANDOM -> LIST_LOOP
                LIST_LOOP -> LIST_END_STOP
            }
        }
    }

    var playMode = PlayMode.LIST_END_STOP
    var status = Status.STOP
    private var activityContext: Context? = null
    private var serviceContext: Context? = null
    private val context: Context get() = activityContext ?: serviceContext ?: appCtx
    var callback: CallBack? = null
    var book: Book? = null
    var chapterSize = 0
    var simulatedChapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var durChapter: BookChapter? = null
    var durPlayUrl = ""
    var durLyric: String? = null
    var durAudioSize = 0
    var inBookshelf = false
    var bookSource: BookSource? = null
    val loadingChapters = arrayListOf<Int>()
    private val readRecord = ReadRecord()
    val readStartTime = AtomicLong(System.currentTimeMillis())
    val executor = globalExecutor
    
    // 用于跟踪上次保存的时间，避免过于频繁的保存
    private var lastSaveTime = 0L
    // 保存间隔，默认30秒
    private val SAVE_INTERVAL = 30 * 1000L
    // 用于跟踪上次保存的状态，避免重复保存相同状态
    private var lastDurChapterIndex = -1
    private var lastDurChapterPos = -1

    fun changePlayMode() {
        playMode = playMode.next()
        book?.setPlayMode(playMode.ordinal)
        postEvent(EventBus.PLAY_MODE_CHANGED, playMode)
    }

    fun upData(book: Book) {
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        if (durChapterIndex != book.durChapterIndex) {
            stopPlay()
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            durPlayUrl = ""
            durLyric = null
            durAudioSize = 0
        }
        upDurChapter()
    }

    fun resetData(book: Book) {
        stop()
        AudioPlay.book = book
        readRecord.bookName = book.name
        // 从数据库读取现有阅读时间
        Coroutine.async {
            readRecord.readTime = appDb.readSessionDao.getTotalReadTime(book.name) ?: 0
        }
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        bookSource = book.getBookSource()
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        PlayMode.entries.getOrNull(book.getPlayMode())?.let{
            playMode = it
            postEvent(EventBus.PLAY_MODE_CHANGED, it)
        }
        val playSpeed = book.getPlaySpeed()
        AudioPlayService.playSpeed = playSpeed
        postEvent(EventBus.AUDIO_SPEED, playSpeed)
        durPlayUrl = ""
        durLyric = null
        durAudioSize = 0
        readStartTime.set(System.currentTimeMillis())
        upDurChapter()
        SourceCallBack.callBackBook(SourceCallBack.START_READ, bookSource, book, durChapter)
        postEvent(EventBus.AUDIO_BUFFER_PROGRESS, 0)
        
        // 更新阅读记忆，确保类型正确
        try {
            val viewModel = ReadingMemoryViewModel(context.applicationContext as android.app.Application)
            viewModel.updateReadingMemoryFromBook(book)
        } catch (e: Exception) {
            AppLog.put("更新阅读记忆失败: ${e.localizedMessage}", e)
        }
    }

    fun upReadTime() {
        if (!AppConfig.enableReadRecord) {
            return
        }
        Coroutine.async {
            try {
                val startTime = readStartTime.get()
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - startTime
                
                val MIN_READ_DURATION = 10 * 1000L // 10秒
                if (duration <= MIN_READ_DURATION) {
                    readStartTime.set(currentTime)
                    return@async
                }
                
                val book = AudioPlay.book
                if (book == null) {
                    return@async
                }
                
                val currentChapter = durChapter?.title.orEmpty()
                val coverUrl = book.getDisplayCover().orEmpty()
                
                val readSession = ReadSession(
                    bookName = book.name,
                    author = book.author,
                    bookUrl = book.bookUrl,
                    deviceId = "",
                    startTime = startTime,
                    endTime = currentTime,
                    duration = duration,
                    words = durChapterIndex.toLong(),
                    type = book.type,
                    durChapterTitle = currentChapter,
                    coverUrl = coverUrl
                )
                
                appDb.readSessionDao.insert(readSession)
                
                // 同时更新ReadRecord
                readRecord.readTime += duration
                readRecord.lastRead = currentTime
                appDb.readRecordDao.insert(readRecord)
                
                // 数据同步
                try {
                    DataSyncService.syncReadRecord(readSession, book)
                } catch (e: Exception) {
                    AppLog.put("同步阅读记录失败: ${e.localizedMessage}", e)
                }
                
                postEvent(EventBus.READ_SESSION_UPDATED, book.name)
                readStartTime.set(currentTime)
            } catch (e: Exception) {
                AppLog.put("创建音频阅读会话失败: ${e.localizedMessage}", e)
                // 异常时不重置startTime，避免时间丢失
            }
        }
    }

    private fun addLoading(index: Int): Boolean {
        synchronized(this) {
            if (loadingChapters.contains(index)) return false
            loadingChapters.add(index)
            return true
        }
    }

    private fun removeLoading(index: Int) {
        synchronized(this) {
            loadingChapters.remove(index)
        }
    }

    fun loadOrUpPlayUrl() {
        if (durPlayUrl.isEmpty()) {
            loadPlayUrl()
        } else {
            upPlayUrl()
        }
    }

    /**
     * 加载播放URL
     */
    private fun loadPlayUrl() {
        val index = durChapterIndex
        if (addLoading(index)) {
            val book = book
            val bookSource = bookSource
            if (book != null && bookSource != null) {
                upDurChapter()
                val chapter = durChapter
                if (chapter == null) {
                    removeLoading(index)
                    return
                }
                if (chapter.isVolume) {
                    skipTo(index + 1)
                    removeLoading(index)
                    return
                }
                upLoading(true)
                WebBook.getContent(this, bookSource, book, chapter)
                    .onSuccess { content ->
                        val content = content.trim()
                        if (content.isEmpty()) {
                            appCtx.toastOnUi("未获取到资源链接")
                        } else {
                            contentLoadFinish(chapter, content)
                        }
                    }.onError {
                        AppLog.put("获取资源链接出错\n$it", it, true)
                        upLoading(false)
                    }.onCancel {
                        removeLoading(index)
                    }.onFinally {
                        callback?.upLyric(durLyric)
                        removeLoading(index)
                    }
            } else {
                removeLoading(index)
                appCtx.toastOnUi("book or source is null")
            }
        }
    }

    /**
     * 加载完成
     */
    private fun contentLoadFinish(chapter: BookChapter, content: String) {
        if (chapter.index == book?.durChapterIndex) {
            durPlayUrl = content
            durLyric = chapter.getVariable("lyric")
            upPlayUrl()
        }
    }

    private fun upPlayUrl() {
        if (isPlayToEnd()) {
            playNew()
        } else {
            play()
        }
    }

    /**
     * 播放当前章节
     */
    fun play() {
        context.startService<AudioPlayService> {
            action = IntentAction.play
        }
    }

    /**
     * 从头播放新章节
     */
    private fun playNew() {
        context.startService<AudioPlayService> {
            action = IntentAction.playNew
        }
    }

    /**
     * 更新当前章节
     */
    fun upDurChapter() {
        val book = book ?: return
        durChapter = appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)
        durAudioSize = durChapter?.end?.toInt() ?: 0
        val title = durChapter?.title ?: appCtx.getString(R.string.data_loading)
        postEvent(EventBus.AUDIO_SUB_TITLE, title)
        postEvent(EventBus.AUDIO_SIZE, durAudioSize)
        postEvent(EventBus.AUDIO_PROGRESS, durChapterPos)
    }

    fun pause(context: Context) {
        if (AudioPlayService.isRun) {
            upReadTime()
            context.startService<AudioPlayService> {
                action = IntentAction.pause
            }
        }
    }

    fun resume(context: Context) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.resume
            }
        }
    }

    fun stop() {
        if (AudioPlayService.isRun) {
            upReadTime()
            context.startService<AudioPlayService> {
                action = IntentAction.stop
            }
        }
    }

    fun setSpeed(speed: Float) {
        if (AudioPlayService.isRun) {
            book?.setPlaySpeed(speed)
            val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
            context.startService<AudioPlayService> {
                action = IntentAction.setSpeed
                putExtra("speed", clampedSpeed)
            }
        }
    }



    fun adjustProgress(position: Int) {
        durChapterPos = position
        saveRead()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustProgress
                putExtra("position", position)
            }
        }
    }

    fun skipTo(index: Int) {
        Coroutine.async {
            upReadTime()
            stopPlay()
            if (index in 0..<simulatedChapterSize) {
                durChapterIndex = index
                durChapterPos = 0
                durPlayUrl = ""
                durLyric = null
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun prev() {
        Coroutine.async {
            upReadTime()
            stopPlay()
            if (durChapterIndex > 0) {
                durChapterIndex -= 1
                durChapterPos = 0
                durPlayUrl = ""
                durLyric = null
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun next() {
        stopPlay()
        upReadTime()
        when (playMode) {
            PlayMode.LIST_END_STOP -> {
                if (durChapterIndex + 1 < simulatedChapterSize) {
                    durChapterIndex += 1
                    durChapterPos = 0
                    durPlayUrl = ""
                    durLyric = null
                    saveRead()
                    loadPlayUrl()
                }
            }

            PlayMode.SINGLE_LOOP -> {
                durChapterPos = 0
                durPlayUrl = ""
                durLyric = null
                saveRead()
                loadPlayUrl()
            }

            PlayMode.RANDOM -> {
                durChapterIndex = (0 until simulatedChapterSize).random()
                durChapterPos = 0
                durPlayUrl = ""
                durLyric = null
                saveRead()
                loadPlayUrl()
            }

            PlayMode.LIST_LOOP -> {
                durChapterIndex = (durChapterIndex + 1) % simulatedChapterSize
                durChapterPos = 0
                durPlayUrl = ""
                durLyric = null
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun setTimer(minute: Int) {
        if (AudioPlayService.isRun) {
            val intent = Intent(context, AudioPlayService::class.java)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", minute)
            context.startService(intent)
        } else {
            AudioPlayService.timeMinute = minute
            postEvent(EventBus.AUDIO_DS, minute)
        }
    }

    fun addTimer() {
        val intent = Intent(context, AudioPlayService::class.java)
        intent.action = IntentAction.addTimer
        context.startService(intent)
    }

    fun stopPlay() {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stopPlay
            }
        }
    }

    fun saveRead(first: Boolean = false) {
        val book = book ?: return
        val currentTime = System.currentTimeMillis()
        
        // 检查是否需要保存：只有当阅读位置变化或时间间隔超过SAVE_INTERVAL时才保存
        if (!first &&
            book.durChapterIndex == durChapterIndex &&
            book.durChapterPos == durChapterPos &&
            currentTime - lastSaveTime < SAVE_INTERVAL) {
            return // 不需要保存，直接返回
        }
        
        // 更新上次保存时间和状态
        lastSaveTime = currentTime
        lastDurChapterIndex = durChapterIndex
        lastDurChapterPos = durChapterPos
        
        Coroutine.async {
            book.lastCheckCount = 0
            val durTime = System.currentTimeMillis()
            book.durChapterTime = durTime
            val chapterChanged = book.durChapterIndex != durChapterIndex
            book.durChapterIndex = durChapterIndex
            book.durChapterPos = durChapterPos
            if (first || chapterChanged) {
                appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex)?.let {
                    book.durChapterTitle = it.getDisplayTitle(
                        ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                        book.getUseReplaceRule(),
                        replaceBook = book.toReplaceBook()
                    )
                    SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, bookSource, book, it, durTime.toString())
                }
            }
            book.update()
        }
    }

    /**
     * 保存章节长度
     */
    fun saveDurChapter(audioSize: Long) {
        val chapter = durChapter ?: return
        Coroutine.async {
            durAudioSize = audioSize.toInt()
            chapter.end = audioSize
            chapter.update()
        }
    }

    fun playPositionChanged(position: Int) {
        durChapterPos = position
        saveRead()
    }

    fun upLoading(loading: Boolean) {
        callback?.upLoading(loading)
    }

    private fun isPlayToEnd(): Boolean {
        return durChapterIndex + 1 == simulatedChapterSize
                && durChapterPos == durAudioSize
    }

    fun register(context: Context) {
        activityContext = context
        callback = context as CallBack
    }

    fun unregister(context: Context) {
        if (activityContext === context) {
            activityContext = null
            callback = null
        }
        coroutineContext.cancelChildren()
    }

    fun registerService(context: Context) {
        serviceContext = context
    }

    fun unregisterService() {
        serviceContext = null
    }

    interface CallBack {

        fun upLoading(loading: Boolean)
        fun upLyric(lyric: String?)
        fun upLyricP(position: Int)
    }

}