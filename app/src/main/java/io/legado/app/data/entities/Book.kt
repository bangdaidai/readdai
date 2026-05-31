package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.PageAnim
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isImage
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.nio.charset.Charset
import java.time.LocalDate
import kotlin.math.max

@Parcelize
@TypeConverters(Book.Converters::class)
@Entity(
    tableName = "books",
    indices = [Index(value = ["name", "author"], unique = true)]
)
data class Book(
    // 详情页Url(本地书源存储完整文件路径)
    @PrimaryKey
    @ColumnInfo(defaultValue = "")
    override var bookUrl: String = "",
    // 目录页Url (toc=table of Contents)
    @ColumnInfo(defaultValue = "")
    var tocUrl: String = "",
    // 书源URL(默认BookType.local)
    @ColumnInfo(defaultValue = BookType.localTag)
    var origin: String = BookType.localTag,
    //书源名称 or 本地书籍文件名
    @ColumnInfo(defaultValue = "")
    var originName: String = "",
    // 书籍名称(书源获取)
    @ColumnInfo(defaultValue = "")
    override var name: String = "",
    // 作者名称(书源获取)
    @ColumnInfo(defaultValue = "")
    override var author: String = "",
    // 分类信息(书源获取)
    override var kind: String? = null,
    // 分类信息(用户修改)
    var customTag: String? = null,
    // 封面Url(书源获取)
    var coverUrl: String? = null,
    // 封面Url(用户修改)
    var customCoverUrl: String? = null,
    // 简介内容(书源获取)
    var intro: String? = null,
    // 简介内容(用户修改)
    var customIntro: String? = null,
    // 自定义字符集名称(仅适用于本地书籍)
    var charset: String? = null,
    // 类型,详见BookType
    @ColumnInfo(defaultValue = "0")
    var type: Int = BookType.text,
    // 自定义分组索引号
    @ColumnInfo(defaultValue = "0")
    var group: Long = 0,
    // 最新章节标题
    var latestChapterTitle: String? = null,
    // 最新章节标题更新时间
    @ColumnInfo(defaultValue = "0")
    var latestChapterTime: Long = System.currentTimeMillis(),
    // 最近一次更新书籍信息的时间
    @ColumnInfo(defaultValue = "0")
    var lastCheckTime: Long = System.currentTimeMillis(),
    // 最近一次发现新章节的数量
    @ColumnInfo(defaultValue = "0")
    var lastCheckCount: Int = 0,
    // 书籍目录总数
    @ColumnInfo(defaultValue = "0")
    var totalChapterNum: Int = 0,
    // 当前章节名称
    var durChapterTitle: String? = null,
    // 当前章节索引
    @ColumnInfo(defaultValue = "0")
    var durChapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    /**  当前卷索引  **/
    var durVolumeIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    /**  相对于卷的索引  **/
    var chapterInVolumeIndex: Int = 0,
    // 当前阅读的进度(首行字符的索引位置)
    @ColumnInfo(defaultValue = "0")
    var durChapterPos: Int = 0,
    // 最近一次阅读书籍的时间(打开正文的时间)
    @ColumnInfo(defaultValue = "0")
    var durChapterTime: Long = System.currentTimeMillis(),
    //字数
    override var wordCount: String? = null,
    // 刷新书架时更新书籍信息
    @ColumnInfo(defaultValue = "1")
    var canUpdate: Boolean = true,
    // 手动排序
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    //书源排序
    @ColumnInfo(defaultValue = "0")
    var originOrder: Int = 0,
    // 自定义书籍变量信息(用于书源规则检索书籍信息)
    override var variable: String? = null,
    //阅读设置
    var readConfig: ReadConfig? = null,
    //同步时间
    @ColumnInfo(defaultValue = "0")
    var syncTime: Long = 0L,
    // 评分 (0-5分)
    @ColumnInfo(defaultValue = "0")
    var rating: Float = 0f,
    
    // 用户修改标记字段
    // 标记用户是否修改了评分
    @ColumnInfo(defaultValue = "false")
    var userModifiedRating: Boolean = false,
    // 标记用户是否修改了简介
    @ColumnInfo(defaultValue = "false")
    var userModifiedIntro: Boolean = false,
    // 标记用户是否修改了封面
    @ColumnInfo(defaultValue = "false")
    var userModifiedCover: Boolean = false,
    // 标记用户是否修改了字数
    @ColumnInfo(defaultValue = "false")
    var userModifiedWordCount: Boolean = false,
    // 标记用户是否修改了分类
    @ColumnInfo(defaultValue = "false")
    var userModifiedKind: Boolean = false,
    
    // 阅读状态字段
    // 阅读状态：0-待读，1-在读，2-读完，3-弃文
    @ColumnInfo(defaultValue = "0")
    var readingStatus: Int = 0,
    
    // 标记用户是否修改了阅读状态
    @ColumnInfo(defaultValue = "false")
    var userModifiedReadingStatus: Boolean = false,
    
    // 书籍读完时间
    @ColumnInfo(defaultValue = "0")
    var finishReadTime: Long = 0L,
    
    // 首次阅读时间
    @ColumnInfo(defaultValue = "0")
    var firstReadTime: Long = 0L,
    
    // 阅读轮次(0=未读完, 1=初读完成, 2=二刷中, 3=二刷完成, 4=三刷中, 依此类推)
    @ColumnInfo(defaultValue = "0")
    var readIteration: Int = 0
) : Parcelable, BaseBook {

    override fun equals(other: Any?): Boolean {
        if (other is Book) {
            return other.bookUrl == bookUrl
        }
        return false
    }

    override fun hashCode(): Int {
        return bookUrl.hashCode()
    }

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    @Ignore
    @IgnoredOnParcel
    override var infoHtml: String? = null

    @Ignore
    @IgnoredOnParcel
    override var tocHtml: String? = null

    @Ignore
    @IgnoredOnParcel
    var downloadUrls: List<String>? = null

    @Ignore
    @IgnoredOnParcel
    private var folderName: String? = null

    @get:Ignore
    @IgnoredOnParcel
    val lastChapterIndex get() = totalChapterNum - 1

    fun getRealAuthor() = author.replace(AppPattern.authorRegex, "")

    fun getUnreadChapterNum() = max(simulatedTotalChapterNum() - durChapterIndex - 1, 0)

    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    fun getDisplayIntro() = if (customIntro.isNullOrEmpty()) intro else customIntro

    //自定义简介有自动更新的需求时，可通过更新intro再调用upCustomIntro()完成
    @Suppress("unused")
    fun upCustomIntro() {
        customIntro = intro
    }
    
    // 获取阅读状态显示文本
    fun getReadingStatusText(): String {
        return io.legado.app.constant.ReadingStatus.fromValue(readingStatus).displayName
    }
    
    // 获取阅读状态枚举值
    fun getReadingStatusEnum(): io.legado.app.constant.ReadingStatus {
        return io.legado.app.constant.ReadingStatus.fromValue(readingStatus)
    }
    
    // 设置阅读状态
    /**
     * 设置阅读状态
     * @param status 阅读状态值
     * @param userModified 是否是用户手动修改
     */
    fun setReadingStatus(status: Int, userModified: Boolean = false) {
        val statusEnum = io.legado.app.constant.ReadingStatus.fromValue(status)
        setReadingStatus(statusEnum, userModified)
    }
    
    /**
     * 设置阅读状态（使用枚举）
     * @param status 阅读状态枚举
     * @param userModified 是否是用户手动修改
     */
    fun setReadingStatus(status: io.legado.app.constant.ReadingStatus, userModified: Boolean = false) {
        // 更新阅读状态和用户修改标记
        val oldStatus = readingStatus
        readingStatus = status.value
        // 如果状态变为已完成，设置完成时间
        if (status == io.legado.app.constant.ReadingStatus.FINISHED && finishReadTime == 0L) {
            finishReadTime = System.currentTimeMillis()
        }
        userModifiedReadingStatus = userModified
        
        // 只有当用户未手动修改阅读状态时，才更新分组
        // 这样可以保留用户在分组设置中的选择
        if (!userModified) {
            // 必须更新分组，即使状态值相同
            // 解决新书加入书架时状态正确但分组未更新的问题
            val groupId = io.legado.app.help.book.ReadingStatusGroupHelper.getGroupIdByReadingStatus(status)
            
            // 清除所有阅读状态相关的分组ID，然后设置新的分组ID
            val readingStatusGroupIds = listOf(
                io.legado.app.help.book.ReadingStatusGroupHelper.GROUP_ID_PENDING,
                io.legado.app.help.book.ReadingStatusGroupHelper.GROUP_ID_READING,
                io.legado.app.help.book.ReadingStatusGroupHelper.GROUP_ID_FINISHED,
                io.legado.app.help.book.ReadingStatusGroupHelper.GROUP_ID_ABANDONED
            )
            var newGroupValue = group
            
            // 清除所有阅读状态分组的ID
            for (statusGroupId in readingStatusGroupIds) {
                newGroupValue = newGroupValue and statusGroupId.inv()
            }
            
            // 设置新的阅读状态分组ID
            newGroupValue = newGroupValue or groupId
            
            // 仅当分组发生变化时才更新
            if (group != newGroupValue) {
                group = newGroupValue
            }
        }
    }

    fun fileCharset(): Charset {
        return charset(charset ?: "UTF-8")
    }

    @IgnoredOnParcel
    val config: ReadConfig
        get() {
            if (readConfig == null) {
                readConfig = ReadConfig()
            }
            return readConfig!!
        }

    fun setReverseToc(reverseToc: Boolean) {
        config.reverseToc = reverseToc
    }

    fun getReverseToc(): Boolean {
        return config.reverseToc
    }

    fun setUseReplaceRule(useReplaceRule: Boolean) {
        config.useReplaceRule = useReplaceRule
    }

    fun getUseReplaceRule(): Boolean {
        val useReplaceRule = config.useReplaceRule
        if (useReplaceRule != null) {
            return useReplaceRule
        }
        //图片类书源 epub本地 默认关闭净化
        if (isImage || isEpub) {
            return false
        }
        return AppConfig.replaceEnableDefault
    }
    
    /**
     * 判定是否为同一本书（根据书名和作者）
     */
    fun isSameNameAuthor(book: Book): Boolean {
        return this.name == book.name && this.author == book.author
    }

    fun setReSegment(reSegment: Boolean) {
        config.reSegment = reSegment
    }

    fun getReSegment(): Boolean {
        return config.reSegment
    }

    fun setPageAnim(pageAnim: Int?) {
        config.pageAnim = pageAnim
    }

    fun getPageAnim(): Int {
        var pageAnim = config.pageAnim
            ?: if (isImage) PageAnim.scrollPageAnim else ReadBookConfig.pageAnim
        if (pageAnim < 0) {
            pageAnim = ReadBookConfig.pageAnim
        }
        return pageAnim
    }

    fun setImageStyle(imageStyle: String?) {
        config.imageStyle = imageStyle
    }

    fun getImageStyle(): String? {
        return config.imageStyle
    }

    fun setTtsEngine(ttsEngine: String?) {
        config.ttsEngine = ttsEngine
    }

    fun getTtsEngine(): String? {
        return config.ttsEngine
    }

    fun setSplitLongChapter(limitLongContent: Boolean) {
        config.splitLongChapter = limitLongContent
    }

    fun getSplitLongChapter(): Boolean {
        return config.splitLongChapter
    }

    // readSimulating 的 setter 和 getter
    fun setReadSimulating(readSimulating: Boolean) {
        config.readSimulating = readSimulating
    }

    fun getReadSimulating(): Boolean {
        return config.readSimulating
    }

    // startDate 的 setter 和 getter
    fun setStartDate(startDate: LocalDate?) {
        config.startDate = startDate
    }

    fun getStartDate(): LocalDate? {
        if (!config.readSimulating || config.startDate == null) {
            return LocalDate.now()
        }
        return config.startDate
    }

    // startChapter 的 setter 和 getter
    fun setStartChapter(startChapter: Int) {
        config.startChapter = startChapter
    }

    fun getStartChapter(): Int {
        if (config.readSimulating) return config.startChapter ?: 0
        return this.durChapterIndex
    }

    // dailyChapters 的 setter 和 getter
    fun setDailyChapters(dailyChapters: Int) {
        config.dailyChapters = dailyChapters
    }

    fun getDailyChapters(): Int {
        return config.dailyChapters
    }

    // 片头 的 setter 和 getter
    fun setOpenCredits(openCredits: Int) {
        config.openCredits = openCredits
    }

    fun getOpenCredits(): Int {
        return config.openCredits
    }
    // 片尾 的 setter 和 getter
    fun setCloseCredits(closeCredits: Int) {
        config.closeCredits = closeCredits
    }

    fun getCloseCredits(): Int {
        return config.closeCredits
    }

    // 播放模式 的 setter 和 getter
    fun setPlayMode(playMode: Int) {
        config.playMode = playMode
    }

    fun getPlayMode(): Int {
        return config.playMode
    }

    // 播放速度 的 setter 和 getter
    fun setPlaySpeed(playSpeed: Float) {
        config.playSpeed = playSpeed
    }

    fun getPlaySpeed(): Float {
        return config.playSpeed
    }

    fun getDelTag(tag: Long): Boolean {
        return config.delTag and tag == tag
    }

    fun addDelTag(tag: Long) {
        config.delTag = config.delTag and tag
    }

    fun removeDelTag(tag: Long) {
        config.delTag = config.delTag and tag.inv()
    }

    fun getFolderName(): String {
        folderName?.let {
            return it
        }
        //防止书名过长,只取9位
        folderName = getFolderNameNoCache()
        return folderName!!
    }

    fun toSearchBook() = SearchBook(
        name = name,
        author = author,
        kind = kind,
        bookUrl = bookUrl,
        origin = origin,
        originName = originName,
        type = type,
        wordCount = wordCount,
        latestChapterTitle = latestChapterTitle,
        coverUrl = coverUrl,
        intro = intro,
        tocUrl = tocUrl,
        originOrder = originOrder,
        variable = variable
    ).apply {
        this.infoHtml = this@Book.infoHtml
        this.tocHtml = this@Book.tocHtml
    }

    /**
     * 迁移旧的书籍的一些信息到新的书籍中
     * 根据书源分组决定是否迁移书名、作者、封面、分类、字数、简介等字段
     * 非正版书源时保留原有的这些信息，正版书源时使用新书源的信息
     */
    fun migrateTo(newBook: Book, toc: List<BookChapter>, isOfficialSource: Boolean? = null): Book {
        // 1. 迁移阅读进度相关信息
        newBook.durChapterIndex = BookHelp
            .getDurChapter(durChapterIndex, durChapterTitle, toc, totalChapterNum)
        newBook.durChapterTitle = toc[newBook.durChapterIndex].getDisplayTitle(
            ContentProcessor.get(newBook.name, newBook.origin).getTitleReplaceRules(),
            getUseReplaceRule()
        )
        newBook.durChapterPos = durChapterPos
        newBook.durChapterTime = durChapterTime

        // 2. 迁移分组和排序信息
        newBook.group = group
        newBook.order = order

        // 3. 迁移自定义信息
        newBook.customCoverUrl = customCoverUrl
        newBook.customIntro = customIntro
        newBook.customTag = customTag
        newBook.canUpdate = canUpdate
        newBook.readConfig = readConfig

        // 4. 迁移评分和用户修改标记
        newBook.rating = rating
        newBook.userModifiedRating = userModifiedRating
        newBook.userModifiedIntro = userModifiedIntro
        newBook.userModifiedCover = userModifiedCover

        // 5. 迁移书籍基本信息（书名、作者、封面、分类、字数、简介）
        // 检查新书源是否属于正版分组
        val finalIsOfficialSource = isOfficialSource ?: runCatching {
            appDb.bookSourceDao.getBookSource(newBook.origin)?.bookSourceGroup?.contains("正版") == true
        }.getOrDefault(false)

        if (finalIsOfficialSource) {
            // 正版书源：使用新书源的书籍基本信息
            // 保留用户修改标记
            newBook.userModifiedWordCount = this.userModifiedWordCount
            newBook.userModifiedIntro = this.userModifiedIntro
        } else {
            // 非正版书源：保留原有的书籍基本信息，不使用新书源的数据
            // 保留书名、作者、封面、分类、字数、简介
            newBook.name = this.name
            newBook.author = this.author
            newBook.coverUrl = this.coverUrl
            newBook.kind = this.kind
            newBook.wordCount = this.wordCount
            newBook.intro = this.intro
            // 保留用户修改标记
            newBook.userModifiedWordCount = this.userModifiedWordCount
            newBook.userModifiedIntro = this.userModifiedIntro
        }

        return newBook
    }

    fun createBookMark(): Bookmark {
        return Bookmark(
            bookName = name,
            bookAuthor = author,
        )
    }

    fun createBookAnnotation(): BookAnnotation {
        return BookAnnotation(
            bookName = name,
            bookAuthor = author,
        )
    }

    fun save() {
        if (appDb.bookDao.has(bookUrl)) {
            appDb.bookDao.update(this)
        } else {
            appDb.bookDao.insert(this)
        }
    }

    fun delete() {
        if (ReadBook.book?.bookUrl == bookUrl) {
            ReadBook.book = null
        }
        appDb.bookDao.delete(this)
    }

    @Suppress("ConstPropertyName")
    companion object {
        const val hTag = 2L
        const val rubyTag = 4L
        const val imgStyleDefault = "DEFAULT"
        const val imgStyleFull = "FULL"
        const val imgStyleText = "TEXT"
        const val imgStyleSingle = "SINGLE"
    }

    @Parcelize
    data class ReadConfig(
        var reverseToc: Boolean = false,
        var pageAnim: Int? = null,
        var reSegment: Boolean = false,
        var imageStyle: String? = null,
        var useReplaceRule: Boolean? = null,// 正文使用净化替换规则
        var delTag: Long = 0L,//去除标签
        var ttsEngine: String? = null,
        var splitLongChapter: Boolean = true,
        var readSimulating: Boolean = false,
        var startDate: LocalDate? = null,
        var startChapter: Int? = null,     // 用户设置的起始章节
        var dailyChapters: Int = 3,    // 用户设置的每日更新章节数
        var openCredits: Int = 0,       //音频片头
        var closeCredits: Int = 0,       //音频片尾
        var playMode: Int = 0,           //音频播放模式
        var playSpeed: Float = 1.0f      //音频播放速度
    ) : Parcelable

    class Converters {

        @TypeConverter
        fun readConfigToString(config: ReadConfig?): String = GSON.toJson(config)

        @TypeConverter
        fun stringToReadConfig(json: String?) = GSON.fromJsonObject<ReadConfig>(json).getOrNull()
    }
}