package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.help.config.DataVisibilitySettings
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx

object BookplateGenerator {

    val DEFAULT_TEMPLATE_HTML = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width: 100%; max-width: 1080px; padding: 48px 56px;
    font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
  }
  .header { text-align: center; margin-bottom: 24px; }
  .header h1 { font-size: 20px; letter-spacing: 4px; font-weight: 600; }
  .header h2 { font-size: 14px; font-weight: 400; opacity: 0.85; margin-top: 4px; }
  .section { margin: 18px 0; }
  .section-title {
    font-size: 11px; text-transform: uppercase; letter-spacing: 3px;
    opacity: 0.6; margin-bottom: 8px; padding-bottom: 4px;
    border-bottom: 1px solid rgba(255,255,255,0.15);
  }
  .field { display: flex; justify-content: space-between; font-size: 14px; margin: 6px 0; }
  .field .label { opacity: 0.75; }
  .field .value { font-weight: 500; text-align: right; }
  .stars { font-size: 16px; letter-spacing: 3px; }
  .review { font-size: 13px; line-height: 1.7; opacity: 0.9; margin-top: 10px; white-space: pre-wrap; }
  .divider { border: none; border-top: 1px dashed rgba(255,255,255,0.25); margin: 20px 0; }
  .footer { text-align: center; font-size: 10px; opacity: 0.5; margin-top: 28px; }
  .footer p { margin: 2px 0; }
</style>
</head>
<body>
  <div class="header">
    <h1>Reading Certificate</h1>
    <h2>=== 阅 读 凭 证 ===</h2>
  </div>

  <div class="section">
    <div class="section-title">Basic Info / 基本信息</div>
    <div class="field"><span class="label">书名</span><span class="value">{{bookName}}</span></div>
    <div class="field"><span class="label">作者</span><span class="value">{{author}}</span></div>
  </div>

  <div class="section">
    <div class="section-title">Reading Period / 阅读时间</div>
    <div class="field"><span class="label">开始时间</span><span class="value">{{firstReadTime}}</span></div>
    <div class="field"><span class="label">结束时间</span><span class="value">{{finishReadTime}}</span></div>
    <div class="field"><span class="label">阅读天数</span><span class="value">{{readingDays}} 天</span></div>
    <div class="field"><span class="label">阅读时长</span><span class="value">{{totalReadTime}}</span></div>
  </div>

  <div class="section">
    <div class="section-title">Reading Stats / 阅读统计</div>
    <div class="field"><span class="label">阅读进度</span><span class="value">{{readingProgress}}</span></div>
    <div class="field"><span class="label">已读章节</span><span class="value">{{readChapters}}</span></div>
    <div class="field"><span class="label">书摘条数</span><span class="value">{{annotationCount}}</span></div>
    <div class="field"><span class="label">想法条数</span><span class="value">{{thoughtCount}}</span></div>
  </div>

  <hr class="divider">

  <div class="section">
    <div class="section-title">Rating / 评分</div>
    <div style="font-size: 14px;"><span class="stars">{{ratingStars}}</span> <span style="opacity:0.7;">{{rating}} / {{ratingMax}}</span></div>
  </div>

  <div class="review">{{reviewContent}}</div>

  <hr class="divider">

  <div class="footer">
    <p>BAD READS, NO RECEIPTS; GOOD READS, ON REPEAT.</p>
    <p>烂书不退款，好书请多读。</p>
  </div>
</body>
</html>
    """.trimIndent()

    suspend fun generate(context: Context, book: Book): Bitmap = withContext(Dispatchers.IO) {
        BookplateLogger.log("GEN", "开始生成藏书票 (Book): ${book.name} - ${book.author}")
        val selectedId = appCtx.getPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
        BookplateLogger.log("GEN", "选中模板ID: $selectedId (0=经典Canvas)")
        if (selectedId == 0L) {
            BookplateLogger.log("GEN", "使用经典Canvas样式")
            return@withContext io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, book)
        }

        val template = appDb.bookplateTemplateDao.getById(selectedId)
            ?: appDb.bookplateTemplateDao.getBuiltin()
            ?: getOrCreateBuiltinTemplate()
        if (template == null || template.htmlContent.isBlank()) {
            BookplateLogger.log("GEN", "HTML模板为空，回退经典Canvas")
            return@withContext io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, book)
        }
        BookplateLogger.log("GEN", "使用模板: ${template.name} (id=${template.id}, builtin=${template.isBuiltin})")
        BookplateLogger.log("GEN", "模板HTML长度: ${template.htmlContent.length} 字符")

        BookplateLogger.log("GEN", "开始构建数据...")
        val data = BookplateDataBuilder.build(book)
        BookplateLogger.log("GEN", "数据构建完成: bookName=${data.bookName}, author=${data.author}, progress=${data.readingProgress}")

        BookplateLogger.log("GEN", "开始HTML离屏渲染...")
        val bitmap = withContext(Dispatchers.Main) {
            BookplateHtmlRenderer.render(context, template, data)
        }

        if (bitmap != null) {
            BookplateLogger.log("GEN", "渲染成功: ${bitmap.width}x${bitmap.height}")
        } else {
            BookplateLogger.log("GEN", "渲染失败(返回null)，回退经典Canvas")
        }
        bitmap ?: io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, book)
    }

    suspend fun generate(context: Context, memory: ReadingMemory): Bitmap = withContext(Dispatchers.IO) {
        BookplateLogger.log("GEN", "开始生成藏书票 (ReadingMemory): ${memory.bookName}")
        val selectedId = appCtx.getPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
        BookplateLogger.log("GEN", "选中模板ID: $selectedId (0=经典Canvas)")
        if (selectedId == 0L) {
            BookplateLogger.log("GEN", "使用经典Canvas样式")
            return@withContext io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, memory)
        }

        val template = appDb.bookplateTemplateDao.getById(selectedId)
            ?: appDb.bookplateTemplateDao.getBuiltin()
            ?: getOrCreateBuiltinTemplate()
        if (template == null || template.htmlContent.isBlank()) {
            BookplateLogger.log("GEN", "HTML模板为空，回退经典Canvas")
            return@withContext io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, memory)
        }
        BookplateLogger.log("GEN", "使用模板: ${template.name} (id=${template.id}, builtin=${template.isBuiltin})")
        BookplateLogger.log("GEN", "模板HTML长度: ${template.htmlContent.length} 字符")

        BookplateLogger.log("GEN", "开始构建数据...")
        val data = BookplateDataBuilder.build(memory)
        BookplateLogger.log("GEN", "数据构建完成: bookName=${data.bookName}, author=${data.author}, progress=${data.readingProgress}")

        BookplateLogger.log("GEN", "开始HTML离屏渲染...")
        val bitmap = withContext(Dispatchers.Main) {
            BookplateHtmlRenderer.render(context, template, data)
        }

        if (bitmap != null) {
            BookplateLogger.log("GEN", "渲染成功: ${bitmap.width}x${bitmap.height}")
        } else {
            BookplateLogger.log("GEN", "渲染失败(返回null)，回退经典Canvas")
        }
        bitmap ?: io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, memory)
    }

    suspend fun getOrCreateBuiltinTemplate(): BookplateTemplate {
        val existing = appDb.bookplateTemplateDao.getBuiltin()
        if (existing != null) return existing

        val now = System.currentTimeMillis()
        val template = BookplateTemplate(
            name = "默认模板",
            htmlContent = DEFAULT_TEMPLATE_HTML,
            isBuiltin = true,
            createTime = now,
            updateTime = now
        )
        val id = appDb.bookplateTemplateDao.insert(template)
        // 清理并发竞争产生的重复内置模板
        appDb.bookplateTemplateDao.deleteBuiltinExcept(id)
        return template.copy(id = id)
    }

    fun getPreviewData(): io.legado.app.data.entities.BookplateData {
        return io.legado.app.data.entities.BookplateData(
            bookName = "三体",
            author = "刘慈欣",
            coverUrl = "",
            intro = "文化大革命如火如荼进行的同时，军方探寻外星文明的绝秘计划红岸工程取得了突破性进展...",
            kind = "科幻,完结",
            wordCount = "90.00万字",
            totalChapterNum = 120,
            latestChapterTitle = "第一百二十章 终章",
            typeText = "文本",
            charset = "UTF-8",

            readingStatusText = "读完",
            readingProgress = "100%",
            readChapters = "120/120",
            unreadChapters = 0,
            readIteration = 2,
            readIterationText = "二刷",
            durChapterTitle = "第三章 红岸基地",

            totalReadTime = "12 小时 30 分钟",
            totalReadHours = 12,
            totalReadMinutes = 30,
            readingDays = 45,
            maxDayReadTime = "3 小时 15 分钟",
            maxDayReadDate = "2025年12月25日",
            totalReadWords = "67.50万字",
            remainingWords = "22.50万字",

            firstReadTime = "2025/01/15",
            lastReadTime = "2025/03/20",
            finishReadTime = "2025/03/20",
            addBookshelfTime = "2025/01/10",
            lastCheckTime = "2025/03/21",

            rating = 4.0f,
            ratingStars = "★★★★☆",
            ratingMax = 5,
            reviewContent = "震撼人心的科幻巨作，让人重新思考宇宙与文明的关系。",

            annotationCount = 25,
            thoughtCount = 12,
            latestAnnotation = "给岁月以文明，而不是给文明以岁月",
            latestAnnotationNote = "这句话道出了本书的核心思想",
            latestAnnotationChapter = "第三章 红岸基地",

            protagonists = "叶文洁, 罗辑, 史强",

            tags = "#科幻 #长篇 #刘慈欣",
            tagCount = 3,

            bookSourceName = "起点中文网",
            readTimeRank = "第 3 名"
        )
    }
}
