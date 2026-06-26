package io.legado.app.data.entities

data class BookplateData(
    val bookName: String = "",
    val author: String = "",
    val coverUrl: String = "",
    val sourceOrigin: String = "",
    val intro: String = "",
    val kind: String = "",
    val wordCount: String = "",
    val originName: String = "",
    val totalChapterNum: Int = 0,
    val latestChapterTitle: String = "",
    val typeText: String = "",
    val charset: String = "",

    val readingStatusText: String = "",
    val readingProgress: String = "",
    val readChapters: String = "",
    val unreadChapters: Int = 0,
    val readIteration: Int = 0,
    val readIterationText: String = "",
    val durChapterTitle: String = "",

    val totalReadTime: String = "",
    val totalReadHours: Long = 0,
    val totalReadMinutes: Long = 0,
    val readingDays: Int = 0,
    val maxDayReadTime: String = "",
    val maxDayReadDate: String = "",
    val totalReadWords: String = "",
    val remainingWords: String = "",

    val firstReadTime: String = "",
    val lastReadTime: String = "",
    val finishReadTime: String = "",
    val addBookshelfTime: String = "",
    val lastCheckTime: String = "",
    val lastReadTimeRelative: String = "",

    val rating: Float = 0f,
    val ratingStars: String = "",
    val ratingMax: Int = 5,
    val reviewContent: String = "",

    val annotationCount: Int = 0,
    val thoughtCount: Int = 0,
    val latestAnnotation: String = "",
    val latestAnnotationNote: String = "",
    val latestAnnotationChapter: String = "",

    val protagonists: String = "",

    val tags: String = "",
    val tagCount: Int = 0,

    val bookSourceName: String = "",
    val bookSourceGroup: String = "",

    val readTimeRank: String = ""
)
