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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    width: 100%; max-width: 100%; padding: 36px 28px;
    font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    background: linear-gradient(160deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
    color: #eef0f5; min-height: 100vh;
  }
  /* ===== 封面区域 ===== */
  .cover-section { text-align: center; margin-bottom: 28px; }
  .cover-img { width: 120px; height: 160px; object-fit: cover; border-radius: 6px; box-shadow: 0 8px 24px rgba(0,0,0,0.4); }
  /* ===== 头部 ===== */
  .header { text-align: center; margin-bottom: 28px; position: relative; }
  .header::after {
    content: ''; display: block; width: 100%; height: 2px;
    background: repeating-linear-gradient(90deg, rgba(230,200,160,0.25) 0px, rgba(230,200,160,0.35) 6px, transparent 6px, transparent 11px);
    margin-top: 16px;
  }
  .header h1 {
    font-size: clamp(18px, 5vw, 28px); letter-spacing: 0.3em; font-weight: 650;
    background: linear-gradient(135deg, #f5e6c8, #e6d4b0); -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  }
  .header h2 { font-size: clamp(10px, 2.5vw, 14px); font-weight: 280; opacity: 0.5; margin-top: 4px; letter-spacing: 0.5em; }
  /* ===== 通用区块 ===== */
  .section { margin: 16px 0 20px 0; }
  .section-title {
    font-size: clamp(9px, 2vw, 12px); text-transform: uppercase; letter-spacing: 0.35em;
    opacity: 0.45; margin-bottom: 10px; padding-bottom: 6px;
    border-bottom: 1px solid rgba(240,220,190,0.12); font-weight: 330;
  }
  /* ===== 字段行 ===== */
  .field { display: flex; justify-content: space-between; font-size: clamp(13px, 3vw, 16px); margin: 8px 0; }
  .field .label { opacity: 0.6; flex-shrink: 0; }
  .field .value { font-weight: 480; text-align: right; max-width: 65%; word-break: break-word; }
  /* ===== 特殊字段 ===== */
  .field.highlight .value { color: #ffeac2; font-weight: 510; }
  .field.intro .value { font-size: clamp(12px, 2.8vw, 14px); line-height: 1.6; max-width: 100%; opacity: 0.9; }
  /* ===== 标签 ===== */
  .tags { font-size: clamp(12px, 2.8vw, 14px); opacity: 0.85; word-break: break-word; }
  /* ===== 主角 ===== */
  .protagonists { font-size: clamp(12px, 2.8vw, 14px); opacity: 0.8; font-style: italic; }
  /* ===== 评分 ===== */
  .rating-wrapper { display: flex; align-items: baseline; gap: 14px; margin: 10px 0 6px 0; flex-wrap: wrap; }
  .stars { font-size: clamp(20px, 5vw, 32px); letter-spacing: 0.15em; color: #f5c542; filter: drop-shadow(0 0 8px rgba(245,197,66,0.25)); }
  .rating-number { font-size: clamp(13px, 3vw, 16px); opacity: 0.7; }
  /* ===== 书评 ===== */
  .review {
    font-size: clamp(13px, 3vw, 15px); line-height: 1.85; opacity: 0.92; margin-top: 14px;
    padding: 14px 16px; background: rgba(30,25,42,0.5); border-radius: 8px;
    border-left: 3px solid rgba(235,195,110,0.3); white-space: pre-wrap; word-break: break-word;
  }
  /* ===== 最新书摘 ===== */
  .annotation {
    font-size: clamp(12px, 2.8vw, 14px); line-height: 1.7; opacity: 0.88; margin-top: 10px;
    padding: 12px 14px; background: rgba(20,20,35,0.5); border-radius: 6px; white-space: pre-wrap;
  }
  .annotation-chapter { font-size: clamp(10px, 2.2vw, 11px); opacity: 0.5; margin-top: 6px; }
  /* ===== 分割虚线 ===== */
  .divider { border: none; border-top: 1px dashed rgba(210,190,165,0.18); margin: 22px 0 18px 0; }
  /* ===== 底部 ===== */
  .footer { text-align: center; font-size: clamp(9px, 2vw, 11px); opacity: 0.35; margin-top: 30px; padding-top: 18px; border-top: 1px dashed rgba(180,160,140,0.12); }
  .footer p { margin: 3px 0; letter-spacing: 0.1em; font-weight: 290; }
  .footer p:last-child { font-style: italic; font-size: clamp(8px, 1.8vw, 10px); opacity: 0.5; }
  /* ===== 状态标签 ===== */
  .status { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 11px; opacity: 0.8; }
  .status.reading { background: rgba(76,175,80,0.25); }
  .status.finished { background: rgba(33,150,243,0.25); }
  .status.abandoned { background: rgba(244,67,54,0.25); }
  .status.pending { background: rgba(158,158,158,0.25); }
</style>
</head>
<body>
  <!-- 头部 -->
  <div class="header">
    <h1>{{bookName}}</h1>
    <h2>BY {{author}}</h2>
  </div>

  <!-- 封面 -->
  <div class="cover-section">
    <img src="{{coverUrl}}" class="cover-img" onerror="this.parentElement.style.display='none'" />
  </div>

  <!-- 基本信息 -->
  <div class="section">
    <div class="section-title">Basic Info / 基本信息</div>
    <div class="field"><span class="label">类型</span><span class="value">{{typeText}}</span></div>
    <div class="field"><span class="label">分类</span><span class="value">{{kind}}</span></div>
    <div class="field"><span class="label">字数</span><span class="value">{{wordCount}}</span></div>
    <div class="field"><span class="label">来源</span><span class="value">{{bookSourceName}}</span></div>
    <div class="field"><span class="label">阅读状态</span><span class="value"><span class="status {{readingStatusText}}">{{readingStatusText}}</span></span></div>
    <div class="field intro"><span class="label">简介</span><span class="value intro">{{intro}}</span></div>
  </div>

  <!-- 阅读进度 -->
  <div class="section">
    <div class="section-title">Progress / 阅读进度</div>
    <div class="field highlight"><span class="label">当前进度</span><span class="value">{{readingProgress}}</span></div>
    <div class="field"><span class="label">已读章节</span><span class="value">{{readChapters}}</span></div>
    <div class="field"><span class="label">剩余章节</span><span class="value">{{unreadChapters}}</span></div>
    <div class="field"><span class="label">最近阅读</span><span class="value">{{durChapterTitle}}</span></div>
    <div class="field"><span class="label">最新章节</span><span class="value">{{latestChapterTitle}}</span></div>
  </div>

  <!-- 阅读时间 -->
  <div class="section">
    <div class="section-title">Time / 阅读时间</div>
    <div class="field"><span class="label">首读时间</span><span class="value">{{firstReadTime}}</span></div>
    <div class="field"><span class="label">最近阅读</span><span class="value">{{lastReadTime}}</span></div>
    <div class="field"><span class="label">读完时间</span><span class="value">{{finishReadTime}}</span></div>
    <div class="field"><span class="label">阅读天数</span><span class="value">{{readingDays}} 天</span></div>
    <div class="field highlight"><span class="label">总阅读时长</span><span class="value">{{totalReadTime}}</span></div>
  </div>

  <!-- 阅读统计 -->
  <div class="section">
    <div class="section-title">Statistics / 阅读统计</div>
    <div class="field"><span class="label">已读字数</span><span class="value">{{totalReadWords}}</span></div>
    <div class="field"><span class="label">剩余字数</span><span class="value">{{remainingWords}}</span></div>
    <div class="field"><span class="label">书摘条数</span><span class="value">{{annotationCount}}</span></div>
    <div class="field"><span class="label">想法条数</span><span class="value">{{thoughtCount}}</span></div>
    <div class="field"><span class="label">主角</span><span class="value protagonists">{{protagonists}}</span></div>
    <div class="field"><span class="label">标签</span><span class="value tags">{{tags}}</span></div>
  </div>

  <hr class="divider">

  <!-- 评分 -->
  <div class="section">
    <div class="section-title">Rating / 评分</div>
    <div class="rating-wrapper">
      <span class="stars">{{ratingStars}}</span>
      <span class="rating-number">{{rating}} / {{ratingMax}}</span>
    </div>
  </div>

  <!-- 书评 -->
  <div class="review">{{reviewContent}}</div>

  <!-- 最新书摘 -->
  <div class="annotation">{{latestAnnotation}}</div>
  <div class="annotation-chapter">{{latestAnnotationChapter}}</div>

  <hr class="divider">

  <!-- 底部 -->
  <div class="footer">
    <p>✦ 好书如挚友，常读常新 ✦</p>
    <p>BAD READS, NO RECEIPTS; GOOD READS, ON REPEAT.</p>
  </div>
</body>
</html>
    """.trimIndent()

    fun prewarmWebView(context: Context) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                BookplateLogger.log("GEN", "WebView预热开始")
                BookplateHtmlRenderer.clearCache()
                val data = getPreviewData()
                val template = BookplateTemplate(
                    name = "_prewarm",
                    htmlContent = DEFAULT_TEMPLATE_HTML,
                    isBuiltin = true
                )
                val prewarmStart = System.currentTimeMillis()
                BookplateHtmlRenderer.render(context, template, data)
                BookplateHtmlRenderer.clearCache()
                BookplateLogger.log("GEN", "WebView预热完成, 耗时=${System.currentTimeMillis() - prewarmStart}ms")
            } catch (e: Exception) {
                BookplateLogger.log("GEN", "WebView预热异常: ${e.message}")
            }
        }
    }

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
        BookplateHtmlRenderer.clearCache()
        val bitmap = withContext(Dispatchers.Main) {
            BookplateHtmlRenderer.render(context, template, data)
        }

        if (bitmap != null) {
            BookplateLogger.log("GEN", "渲染成功: ${bitmap.width}x${bitmap.height}")
        } else {
            BookplateLogger.log("GEN", "渲染失败(返回null), 错误: ${BookplateHtmlRenderer.lastError ?: "未知"}, 回退经典Canvas")
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
        BookplateHtmlRenderer.clearCache()
        val bitmap = withContext(Dispatchers.Main) {
            BookplateHtmlRenderer.render(context, template, data)
        }

        if (bitmap != null) {
            BookplateLogger.log("GEN", "渲染成功: ${bitmap.width}x${bitmap.height}")
        } else {
            BookplateLogger.log("GEN", "渲染失败(返回null), 错误: ${BookplateHtmlRenderer.lastError ?: "未知"}, 回退经典Canvas")
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
