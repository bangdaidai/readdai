package io.legado.app.help.book

import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookplateData
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.help.config.DataVisibilitySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookplateDataBuilder {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    suspend fun build(book: Book): BookplateData = withContext(Dispatchers.IO) {
        val annotations = appDb.bookAnnotationDao.getByBook(book.name, book.author)
        val reviews = appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
        val readSessionTotal = run {
            var time = appDb.readSessionDao.getTotalReadTime(book.name, book.author)
            if (time == null || time == 0L) {
                time = appDb.readSessionDao.getTotalReadTime(book.name)
            }
            if (time == null || time == 0L) {
                time = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl)
            }
            time ?: 0L
        }
        val protagonists = appDb.bookProtagonistDao.getByBook(book.bookUrl).map { it.name }
        val tags = run {
            val relations = appDb.bookTagRelationDao.getRelationsByBook(book.bookUrl)
            val tagIds = relations.map { it.tagId }
            if (tagIds.isNotEmpty()) appDb.bookTagDao.getTagsByIds(tagIds) else emptyList()
        }

        val typeText = when {
            (book.type and BookType.video) != 0 -> "视频"
            (book.type and BookType.audio) != 0 -> "音频"
            (book.type and BookType.image) != 0 -> "图片"
            (book.type and BookType.webFile) != 0 -> "文件"
            else -> "文本"
        }
        val totalReadMinutes = readSessionTotal / 60000
        val totalReadHours = totalReadMinutes / 60
        val totalReadDays = totalReadMinutes / (24 * 60)

        val readTimeStr = when {
            totalReadDays > 0 -> "${totalReadDays} 天 ${totalReadMinutes % (24 * 60) / 60} 小时 ${totalReadMinutes % 60} 分钟"
            totalReadHours > 0 -> "$totalReadHours 小时 ${totalReadMinutes % 60} 分钟"
            else -> "$totalReadMinutes 分钟"
        }

        val progressPercent = if (book.totalChapterNum > 0) {
            "${((book.durChapterIndex + 1).toFloat() / book.totalChapterNum * 100).toInt()}%"
        } else "0%"

        val chaptersStr = if (book.totalChapterNum > 0) {
            "${book.durChapterIndex + 1}/${book.totalChapterNum}"
        } else "0/0"

        val unreadCount = (book.totalChapterNum - book.durChapterIndex - 1).coerceAtLeast(0)

        val readingStatusText = when (book.readingStatus) {
            1 -> "在读"
            2 -> "读完"
            3 -> "弃文"
            else -> "待读"
        }

        val iterationNum = book.readIteration / 2
        val iterationText = when (iterationNum) {
            0 -> "初读"
            1 -> "初读"
            2 -> "二刷"
            3 -> "三刷"
            else -> "${iterationNum}刷"
        }

        val ratingStars = buildStars(book.rating)

        val reviewContent = reviews.firstOrNull()?.reviewContent?.takeIf { it.isNotBlank() } ?: ""

        val annotationCount = annotations.size
        val thoughtCount = annotations.count { !it.content.isNullOrBlank() }
        val latestAnno = annotations.maxByOrNull { it.time }
        val latestAnnoText = latestAnno?.bookText?.take(200) ?: ""
        val latestAnnoNote = latestAnno?.content ?: ""
        val latestAnnoChapter = latestAnno?.chapterName ?: ""

        val wordCountTotal = book.wordCount?.parseWordCount() ?: 0f
        val readWords = (wordCountTotal * ((book.durChapterIndex + 1).toFloat() / book.totalChapterNum.coerceAtLeast(1))).coerceAtMost(wordCountTotal)
        val remainingWords = (wordCountTotal - readWords).coerceAtLeast(0f)

        BookplateData(
            bookName = book.name,
            author = book.author,
            coverUrl = book.getDisplayCover() ?: "",
            intro = book.getDisplayIntro()?.take(500) ?: "",
            kind = book.kind ?: "",
            wordCount = book.wordCount?.let { formatWordCount(it) } ?: "",
            originName = book.originName ?: "",
            totalChapterNum = book.totalChapterNum,
            latestChapterTitle = book.latestChapterTitle ?: "",
            typeText = book.typeText,
            charset = book.charset ?: "",

            readingStatusText = readingStatusText,
            readingProgress = progressPercent,
            readChapters = chaptersStr,
            unreadChapters = unreadCount,
            readIteration = book.readIteration,
            readIterationText = iterationText,
            durChapterTitle = book.durChapterTitle ?: "",

            totalReadTime = readTimeStr,
            totalReadHours = totalReadHours,
            totalReadMinutes = totalReadMinutes % 60,
            readingDays = 0,
            maxDayReadTime = "",
            maxDayReadDate = "",
            totalReadWords = formatWordCountValue(readWords),
            remainingWords = formatWordCountValue(remainingWords),

            firstReadTime = if (book.firstReadTime > 0) dateFormat.format(Date(book.firstReadTime)) else "____/__/__",
            lastReadTime = if (book.lastCheckTime > 0) dateFormat.format(Date(book.lastCheckTime)) else "____/__/__",
            finishReadTime = if (book.finishReadTime > 0) dateFormat.format(Date(book.finishReadTime)) else "____/__/__",
            addBookshelfTime = if (book.durChapterTime > 0) dateFormat.format(Date(book.durChapterTime)) else "____/__/__",
            lastCheckTime = if (book.lastCheckTime > 0) dateFormat.format(Date(book.lastCheckTime)) else "____/__/__",
            lastReadTimeRelative = "",

            rating = book.rating,
            ratingStars = ratingStars,
            ratingMax = 5,
            reviewContent = reviewContent,

            annotationCount = annotationCount,
            thoughtCount = thoughtCount,
            latestAnnotation = latestAnnoText,
            latestAnnotationNote = latestAnnoNote,
            latestAnnotationChapter = latestAnnoChapter,

            protagonists = protagonists.joinToString(", ").ifEmpty { "未知" },

            tags = tags.joinToString(" ") { "#${it.name}" },
            tagCount = tags.size,

            bookSourceName = book.originName ?: "",
            bookSourceGroup = "",

            readTimeRank = ""
        )
    }

    suspend fun build(memory: ReadingMemory): BookplateData = withContext(Dispatchers.IO) {
        val annotations = appDb.bookAnnotationDao.getByBook(memory.bookName, memory.bookAuthor)
        val reviews = appDb.bookReviewDao.getReviewByBookUrl(memory.bookUrl)
        val readSessionTotal = run {
            var time = appDb.readSessionDao.getTotalReadTime(memory.bookName, memory.bookAuthor)
            if (time == null || time == 0L) {
                time = appDb.readSessionDao.getTotalReadTime(memory.bookName)
            }
            if (time == null || time == 0L) {
                time = appDb.readSessionDao.getTotalReadTimeByUrl(memory.bookUrl)
            }
            time ?: 0L
        }

        val totalReadMinutes = readSessionTotal / 60000
        val totalReadHours = totalReadMinutes / 60
        val totalReadDays = totalReadMinutes / (24 * 60)

        val readTimeStr = when {
            totalReadDays > 0 -> "${totalReadDays} 天 ${totalReadMinutes % (24 * 60) / 60} 小时 ${totalReadMinutes % 60} 分钟"
            totalReadHours > 0 -> "$totalReadHours 小时 ${totalReadMinutes % 60} 分钟"
            else -> "$totalReadMinutes 分钟"
        }

        val progressPercent = if (memory.totalChapterNum > 0) {
            "${((memory.durChapterIndex + 1).toFloat() / memory.totalChapterNum * 100).toInt()}%"
        } else "0%"

        val chaptersStr = if (memory.totalChapterNum > 0) {
            "${memory.durChapterIndex + 1}/${memory.totalChapterNum}"
        } else "0/0"

        val ratingStars = buildStars(memory.rating)
        val reviewContent = reviews.firstOrNull()?.reviewContent?.takeIf { it.isNotBlank() } ?: ""

        val annotationCount = annotations.size
        val thoughtCount = annotations.count { !it.content.isNullOrBlank() }
        val latestAnno = annotations.maxByOrNull { it.time }
        val latestAnnoText = latestAnno?.bookText?.take(200) ?: ""
        val latestAnnoNote = latestAnno?.content ?: ""
        val latestAnnoChapter = latestAnno?.chapterName ?: ""

        val readingStatusText = when (memory.readingStatus) {
            io.legado.app.constant.ReadingStatus.FINISHED -> "读完"
            io.legado.app.constant.ReadingStatus.READING -> "在读"
            io.legado.app.constant.ReadingStatus.ABANDONED -> "弃文"
            else -> "待读"
        }

        BookplateData(
            bookName = memory.bookName,
            author = memory.bookAuthor,
            coverUrl = memory.coverUrl ?: "",
            intro = memory.intro?.take(500) ?: "",
            kind = memory.kind ?: "",
            wordCount = memory.wordCount?.let { formatWordCount(it) } ?: "",
            totalChapterNum = memory.totalChapterNum,
            latestChapterTitle = memory.durChapterTitle ?: "",
            typeText = memory.kind ?: "",

            readingStatusText = readingStatusText,
            readingProgress = progressPercent,
            readChapters = chaptersStr,
            unreadChapters = 0,
            readIteration = 0,
            readIterationText = "",
            durChapterTitle = memory.durChapterTitle ?: "",

            totalReadTime = readTimeStr,
            totalReadHours = totalReadHours,
            totalReadMinutes = totalReadMinutes % 60,

            firstReadTime = if (memory.firstReadTime > 0) dateFormat.format(Date(memory.firstReadTime)) else "____/__/__",
            finishReadTime = if (memory.finishReadTime > 0) dateFormat.format(Date(memory.finishReadTime)) else "____/__/__",
            addBookshelfTime = if (memory.createTime > 0) dateFormat.format(Date(memory.createTime)) else "____/__/__",

            rating = memory.rating,
            ratingStars = ratingStars,
            ratingMax = 5,
            reviewContent = reviewContent,

            annotationCount = annotationCount,
            thoughtCount = thoughtCount,
            latestAnnotation = latestAnnoText,
            latestAnnotationNote = latestAnnoNote,
            latestAnnotationChapter = latestAnnoChapter,

            protagonists = "未知",

            tags = "",
            tagCount = 0,

            bookSourceName = "",
            bookSourceGroup = ""
        )
    }

    private fun buildStars(rating: Float): String {
        val sb = StringBuilder()
        for (i in 1..5) {
            sb.append(if (rating >= i) "★" else "☆")
        }
        return sb.toString()
    }

    private fun formatWordCount(wordCount: String): String {
        return try {
            val value = wordCount.replace("[^0-9.万]".toRegex(), "")
            if (value.contains("万")) " ${value}字" else if (value.isNotBlank()) "${value}字" else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatWordCountValue(count: Float): String {
        return when {
            count >= 10000f -> "%.2f万字".format(count / 10000f)
            else -> "${count.toInt()}字"
        }
    }

    private fun String.parseWordCount(): Float {
        return try {
            val clean = this.replace("[^0-9.]".toRegex(), "")
            val num = clean.toFloatOrNull() ?: 0f
            if (this.contains("万")) num * 10000f else num
        } catch (_: Exception) {
            0f
        }
    }
}
