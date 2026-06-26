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

    // ============================================================
    // 内置模板 1: 暗黑科幻风格 (Dark Sci-Fi) - 默认
    // ============================================================
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
  .cover-section { text-align: center; margin-bottom: 28px; }
  .cover-img { width: 120px; height: 160px; object-fit: cover; border-radius: 6px; box-shadow: 0 8px 24px rgba(0,0,0,0.4); }
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
  .section { margin: 16px 0 20px 0; }
  .section-title {
    font-size: clamp(9px, 2vw, 12px); text-transform: uppercase; letter-spacing: 0.35em;
    opacity: 0.45; margin-bottom: 10px; padding-bottom: 6px;
    border-bottom: 1px solid rgba(240,220,190,0.12); font-weight: 330;
  }
  .field { display: flex; justify-content: space-between; font-size: clamp(13px, 3vw, 16px); margin: 8px 0; }
  .field .label { opacity: 0.6; flex-shrink: 0; }
  .field .value { font-weight: 480; text-align: right; max-width: 65%; word-break: break-word; }
  .field.highlight .value { color: #ffeac2; font-weight: 510; }
  .field.intro .value { font-size: clamp(12px, 2.8vw, 14px); line-height: 1.6; max-width: 100%; opacity: 0.9; }
  .tags { font-size: clamp(12px, 2.8vw, 14px); opacity: 0.85; word-break: break-word; }
  .protagonists { font-size: clamp(12px, 2.8vw, 14px); opacity: 0.8; font-style: italic; }
  .rating-wrapper { display: flex; align-items: baseline; gap: 14px; margin: 10px 0 6px 0; flex-wrap: wrap; }
  .stars { font-size: clamp(20px, 5vw, 32px); letter-spacing: 0.15em; color: #f5c542; filter: drop-shadow(0 0 8px rgba(245,197,66,0.25)); }
  .rating-number { font-size: clamp(13px, 3vw, 16px); opacity: 0.7; }
  .review {
    font-size: clamp(13px, 3vw, 15px); line-height: 1.85; opacity: 0.92; margin-top: 14px;
    padding: 14px 16px; background: rgba(30,25,42,0.5); border-radius: 8px;
    border-left: 3px solid rgba(235,195,110,0.3); white-space: pre-wrap; word-break: break-word;
  }
  .annotation {
    font-size: clamp(12px, 2.8vw, 14px); line-height: 1.7; opacity: 0.88; margin-top: 10px;
    padding: 12px 14px; background: rgba(20,20,35,0.5); border-radius: 6px; white-space: pre-wrap;
  }
  .annotation-chapter { font-size: clamp(10px, 2.2vw, 11px); opacity: 0.5; margin-top: 6px; }
  .divider { border: none; border-top: 1px dashed rgba(210,190,165,0.18); margin: 22px 0 18px 0; }
  .footer { text-align: center; font-size: clamp(9px, 2vw, 11px); opacity: 0.35; margin-top: 30px; padding-top: 18px; border-top: 1px dashed rgba(180,160,140,0.12); }
  .footer p { margin: 3px 0; letter-spacing: 0.1em; font-weight: 290; }
  .footer p:last-child { font-style: italic; font-size: clamp(8px, 1.8vw, 10px); opacity: 0.5; }
  .status { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 11px; opacity: 0.8; }
  .status.reading { background: rgba(76,175,80,0.25); }
  .status.finished { background: rgba(33,150,243,0.25); }
  .status.abandoned { background: rgba(244,67,54,0.25); }
  .status.pending { background: rgba(158,158,158,0.25); }
</style>
</head>
<body>
  <div class="header">
    <h1>{{bookName}}</h1>
    <h2>BY {{author}}</h2>
  </div>
  <div class="cover-section">
    <img src="{{coverUrl}}" class="cover-img" onerror="this.parentElement.style.display='none'" />
  </div>
  <div class="section">
    <div class="section-title">Basic Info / 基本信息</div>
    <div class="field"><span class="label">类型</span><span class="value">{{typeText}}</span></div>
    <div class="field"><span class="label">分类</span><span class="value">{{kind}}</span></div>
    <div class="field"><span class="label">字数</span><span class="value">{{wordCount}}</span></div>
    <div class="field"><span class="label">来源</span><span class="value">{{bookSourceName}}</span></div>
    <div class="field"><span class="label">阅读状态</span><span class="value"><span class="status {{readingStatusText}}">{{readingStatusText}}</span></span></div>
    <div class="field intro"><span class="label">简介</span><span class="value intro">{{intro}}</span></div>
  </div>
  <div class="section">
    <div class="section-title">Progress / 阅读进度</div>
    <div class="field highlight"><span class="label">当前进度</span><span class="value">{{readingProgress}}</span></div>
    <div class="field"><span class="label">已读章节</span><span class="value">{{readChapters}}</span></div>
    <div class="field"><span class="label">剩余章节</span><span class="value">{{unreadChapters}}</span></div>
    <div class="field"><span class="label">最近阅读</span><span class="value">{{durChapterTitle}}</span></div>
    <div class="field"><span class="label">最新章节</span><span class="value">{{latestChapterTitle}}</span></div>
  </div>
  <div class="section">
    <div class="section-title">Time / 阅读时间</div>
    <div class="field"><span class="label">首读时间</span><span class="value">{{firstReadTime}}</span></div>
    <div class="field"><span class="label">最近阅读</span><span class="value">{{lastReadTime}}</span></div>
    <div class="field"><span class="label">读完时间</span><span class="value">{{finishReadTime}}</span></div>
    <div class="field"><span class="label">阅读天数</span><span class="value">{{readingDays}} 天</span></div>
    <div class="field highlight"><span class="label">总阅读时长</span><span class="value">{{totalReadTime}}</span></div>
  </div>
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
  <div class="section">
    <div class="section-title">Rating / 评分</div>
    <div class="rating-wrapper">
      <span class="stars">{{ratingStars}}</span>
      <span class="rating-number">{{rating}} / {{ratingMax}}</span>
    </div>
  </div>
  <div class="review">{{reviewContent}}</div>
  <div class="annotation">{{latestAnnotation}}</div>
  <div class="annotation-chapter">{{latestAnnotationChapter}}</div>
  <hr class="divider">
  <div class="footer">
    <p>✦ 好书如挚友，常读常新 ✦</p>
    <p>BAD READS, NO RECEIPTS; GOOD READS, ON REPEAT.</p>
  </div>
</body>
</html>
    """.trimIndent()

    // ============================================================
    // 内置模板 2: 简约清新风格 (Minimal Fresh)
    // ============================================================
    val TEMPLATE_HTML_MINIMAL = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width: 100%; max-width: 100%; padding: 32px 24px;
    font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    background: #f8f9fa; color: #2c3e50; min-height: 100vh;
  }
  .card {
    background: #ffffff; border-radius: 12px; padding: 28px 24px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.06); margin-bottom: 16px;
  }
  .book-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
  .cover-img { width: 80px; height: 106px; object-fit: cover; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
  .book-info { flex: 1; }
  .book-info h1 { font-size: 20px; font-weight: 600; color: #1a1a2e; margin-bottom: 4px; }
  .book-info .author { font-size: 13px; color: #888; letter-spacing: 0.1em; }
  .book-info .status-tag {
    display: inline-block; margin-top: 6px; padding: 2px 10px; border-radius: 10px; font-size: 11px;
  }
  .status-tag.在读 { background: #e8f5e9; color: #388e3c; }
  .status-tag.读完 { background: #e3f2fd; color: #1976d2; }
  .status-tag.弃文 { background: #ffebee; color: #d32f2f; }
  .status-tag.待读 { background: #f5f5f5; color: #999; }
  .section-title {
    font-size: 12px; color: #aaa; text-transform: uppercase; letter-spacing: 0.2em;
    font-weight: 500; margin-bottom: 12px;
  }
  .field { display: flex; justify-content: space-between; font-size: 14px; margin: 6px 0; }
  .field .label { color: #999; flex-shrink: 0; }
  .field .value { color: #333; text-align: right; max-width: 65%; word-break: break-word; }
  .stars { color: #f5a623; font-size: 18px; letter-spacing: 2px; }
  .review {
    font-size: 14px; line-height: 1.8; color: #555; padding: 12px 0; border-left: 3px solid #e0e0e0;
    padding-left: 14px; white-space: pre-wrap; word-break: break-word;
  }
  .annotation {
    font-size: 13px; line-height: 1.7; color: #666; font-style: italic;
    padding: 10px 14px; background: #f9f9f9; border-radius: 8px; white-space: pre-wrap;
  }
  .annotation-chapter { font-size: 11px; color: #bbb; margin-top: 4px; text-align: right; }
  hr { border: none; border-top: 1px solid #eee; margin: 16px 0; }
  .footer { text-align: center; font-size: 11px; color: #ccc; margin-top: 20px; }
</style>
</head>
<body>
  <div class="card">
    <div class="book-header">
      <img src="{{coverUrl}}" class="cover-img" onerror="this.style.display='none'" />
      <div class="book-info">
        <h1>{{bookName}}</h1>
        <div class="author">{{author}}</div>
        <span class="status-tag {{readingStatusText}}">{{readingStatusText}}</span>
      </div>
    </div>
  </div>
  <div class="card">
    <div class="section-title">阅读进度</div>
    <div class="field"><span class="label">进度</span><span class="value">{{readingProgress}}</span></div>
    <div class="field"><span class="label">已读 / 总章节</span><span class="value">{{readChapters}}</span></div>
    <div class="field"><span class="label">当前章节</span><span class="value">{{durChapterTitle}}</span></div>
    <div class="field"><span class="label">总阅读时长</span><span class="value">{{totalReadTime}}</span></div>
    <div class="field"><span class="label">已读字数</span><span class="value">{{totalReadWords}}</span></div>
  </div>
  <div class="card">
    <div class="section-title">书籍信息</div>
    <div class="field"><span class="label">类型</span><span class="value">{{typeText}}</span></div>
    <div class="field"><span class="label">分类</span><span class="value">{{kind}}</span></div>
    <div class="field"><span class="label">字数</span><span class="value">{{wordCount}}</span></div>
    <div class="field"><span class="label">来源</span><span class="value">{{bookSourceName}}</span></div>
    <div class="field"><span class="label">标签</span><span class="value">{{tags}}</span></div>
    <div style="font-size:13px;color:#666;line-height:1.6;margin-top:8px;">{{intro}}</div>
  </div>
  <div class="card">
    <div class="section-title">评分</div>
    <div class="stars">{{ratingStars}}</div>
    <div style="font-size:13px;color:#999;margin-top:2px;">{{rating}} / {{ratingMax}}</div>
    <div class="review">{{reviewContent}}</div>
  </div>
  <div class="annotation">{{latestAnnotation}}</div>
  <div class="annotation-chapter">—— {{latestAnnotationChapter}}</div>
  <hr>
  <div class="footer">记录于 {{lastReadTime}}</div>
</body>
</html>
    """.trimIndent()

    // ============================================================
    // 内置模板 3: 古典书香风格 (Classical Literary)
    // ============================================================
    val TEMPLATE_HTML_CLASSICAL = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width: 100%; max-width: 100%; padding: 28px 24px;
    font-family: "Noto Serif SC", "SimSun", "STSong", serif;
    background: #f5efe0; color: #3c3028; min-height: 100vh;
    line-height: 1.8;
  }
  .paper {
    background: #fdfaf3; border: 1px solid #e0d5c0; border-radius: 2px; padding: 32px 28px;
    box-shadow: 0 2px 16px rgba(139,119,80,0.1);
  }
  .title-block { text-align: center; margin-bottom: 28px; padding-bottom: 20px; border-bottom: 2px solid #c9b896; }
  .title-block h1 { font-size: 26px; font-weight: 700; color: #4a3728; letter-spacing: 0.2em; }
  .title-block h2 { font-size: 14px; color: #8c7355; margin-top: 6px; letter-spacing: 0.3em; }
  .cover-section { text-align: center; margin: 20px 0; }
  .cover-img { width: 100px; height: 133px; object-fit: cover; border: 2px solid #c9b896; border-radius: 2px; }
  .seal { text-align: center; font-size: 14px; color: #b54141; margin: 16px 0; letter-spacing: 0.3em; }
  .row { display: flex; margin: 10px 0; font-size: 15px; }
  .row .label { width: 80px; flex-shrink: 0; color: #8c7355; }
  .row .content { flex: 1; color: #3c3028; word-break: break-word; }
  .intro-text { font-size: 14px; color: #6b5d4e; line-height: 2; margin: 14px 0; text-indent: 2em; }
  .stars-text { font-size: 22px; color: #c49530; letter-spacing: 3px; }
  .review-text {
    font-size: 15px; color: #5a4a3a; line-height: 2; margin: 12px 0;
    padding: 10px 0; border-top: 1px solid #e0d5c0; border-bottom: 1px solid #e0d5c0; white-space: pre-wrap;
  }
  .annotation-block {
    font-size: 14px; color: #7a6a55; line-height: 1.9; margin: 14px 0;
    padding: 10px 14px; background: rgba(201,184,150,0.15); border-left: 3px solid #c9b896; white-space: pre-wrap;
  }
  .annotation-source { font-size: 12px; color: #b0a088; text-align: right; margin-top: 4px; }
  .footer { text-align: center; font-size: 12px; color: #b0a088; margin-top: 28px; padding-top: 16px; border-top: 1px solid #e0d5c0; }
  .footer p { margin: 4px 0; }
</style>
</head>
<body>
  <div class="paper">
    <div class="title-block">
      <h1>{{bookName}}</h1>
      <h2>{{author}} 著</h2>
    </div>
    <div class="cover-section">
      <img src="{{coverUrl}}" class="cover-img" onerror="this.parentElement.style.display='none'" />
    </div>
    <div class="seal">[ {{readingStatusText}} ]</div>
    <div class="intro-text">{{intro}}</div>
    <hr style="border:none;border-top:1px dashed #d5c9b0;margin:16px 0;">
    <div class="row"><span class="label">分类</span><span class="content">{{kind}}</span></div>
    <div class="row"><span class="label">字数</span><span class="content">{{wordCount}}</span></div>
    <div class="row"><span class="label">来源</span><span class="content">{{bookSourceName}}</span></div>
    <div class="row"><span class="label">类型</span><span class="content">{{typeText}}</span></div>
    <div class="row"><span class="label">标签</span><span class="content">{{tags}}</span></div>
    <hr style="border:none;border-top:1px dashed #d5c9b0;margin:16px 0;">
    <div class="row"><span class="label">阅读进度</span><span class="content">{{readingProgress}}</span></div>
    <div class="row"><span class="label">已读章节</span><span class="content">{{readChapters}}</span></div>
    <div class="row"><span class="label">当前章节</span><span class="content">{{durChapterTitle}}</span></div>
    <div class="row"><span class="label">总阅读时长</span><span class="content">{{totalReadTime}}</span></div>
    <div class="row"><span class="label">已读字数</span><span class="content">{{totalReadWords}}</span></div>
    <div class="row"><span class="label">阅读天数</span><span class="content">{{readingDays}} 天</span></div>
    <hr style="border:none;border-top:1px dashed #d5c9b0;margin:16px 0;">
    <div class="stars-text">{{ratingStars}}</div>
    <div style="font-size:13px;color:#8c7355;">{{rating}} / {{ratingMax}}</div>
    <div class="review-text">{{reviewContent}}</div>
    <div class="annotation-block">{{latestAnnotation}}</div>
    <div class="annotation-source">—— {{latestAnnotationChapter}}</div>
    <div class="footer">
      <p>始于 {{firstReadTime}}</p>
      <p>一纸藏书票，满卷读书香</p>
    </div>
  </div>
</body>
</html>
    """.trimIndent()

    // ============================================================
    // 内置模板 4: 现代卡片风格 (Modern Card)
    // ============================================================
    val TEMPLATE_HTML_MODERN = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width: 100%; max-width: 100%; padding: 20px 16px;
    font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    background: #0a0a0a; color: #e8e8e8; min-height: 100vh;
    line-height: 1.6;
  }
  .hero {
    position: relative; border-radius: 16px; overflow: hidden; margin-bottom: 16px;
    background: linear-gradient(135deg, #1a1a2e 0%, #232343 100%);
    padding: 28px 20px; text-align: center;
  }
  .hero-cover { width: 88px; height: 117px; object-fit: cover; border-radius: 8px; box-shadow: 0 4px 20px rgba(0,0,0,0.5); margin-bottom: 14px; }
  .hero h1 { font-size: 22px; font-weight: 700; background: linear-gradient(135deg, #f0d98c, #e8c560); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
  .hero .author { font-size: 12px; color: #777; letter-spacing: 0.15em; margin-top: 2px; }
  .hero-badge {
    display: inline-block; margin-top: 8px; padding: 3px 14px;
    border-radius: 20px; font-size: 11px; font-weight: 500;
  }
  .hero-badge.在读 { background: rgba(76,175,80,0.2); color: #81c784; border: 1px solid rgba(76,175,80,0.3); }
  .hero-badge.读完 { background: rgba(33,150,243,0.2); color: #64b5f6; border: 1px solid rgba(33,150,243,0.3); }
  .hero-badge.弃文 { background: rgba(244,67,54,0.2); color: #ef9a9a; border: 1px solid rgba(244,67,54,0.3); }
  .hero-badge.待读 { background: rgba(158,158,158,0.2); color: #bdbdbd; border: 1px solid rgba(158,158,158,0.3); }
  .grid { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
  .stat-card {
    flex: 1 1 calc(50% - 6px); min-width: 140px; background: rgba(255,255,255,0.04);
    border-radius: 12px; padding: 14px; border: 1px solid rgba(255,255,255,0.06);
  }
  .stat-card .stat-value { font-size: 18px; font-weight: 600; color: #f0d98c; }
  .stat-card .stat-label { font-size: 11px; color: #888; margin-top: 2px; text-transform: uppercase; letter-spacing: 0.1em; }
  .full-card {
    background: rgba(255,255,255,0.04); border-radius: 12px; padding: 16px;
    border: 1px solid rgba(255,255,255,0.06); margin-bottom: 12px;
  }
  .full-card h3 { font-size: 12px; color: #666; text-transform: uppercase; letter-spacing: 0.15em; margin-bottom: 10px; }
  .detail-row { display: flex; justify-content: space-between; padding: 5px 0; font-size: 13px; }
  .detail-row .k { color: #888; flex-shrink: 0; }
  .detail-row .v { color: #ddd; text-align: right; max-width: 65%; word-break: break-word; }
  .stars { color: #f5a623; font-size: 20px; letter-spacing: 3px; }
  .quote {
    font-size: 14px; line-height: 1.8; color: #bbb; font-style: italic;
    padding: 10px 0; border-left: 2px solid rgba(240,217,140,0.3); padding-left: 14px;
    white-space: pre-wrap; word-break: break-word;
  }
  .footer { text-align: center; font-size: 11px; color: #555; margin-top: 16px; }
</style>
</head>
<body>
  <div class="hero">
    <img src="{{coverUrl}}" class="hero-cover" onerror="this.style.display='none'" />
    <h1>{{bookName}}</h1>
    <div class="author">{{author}}</div>
    <span class="hero-badge {{readingStatusText}}">{{readingStatusText}}</span>
  </div>
  <div class="grid">
    <div class="stat-card">
      <div class="stat-value">{{readingProgress}}</div>
      <div class="stat-label">阅读进度</div>
    </div>
    <div class="stat-card">
      <div class="stat-value">{{totalReadTime}}</div>
      <div class="stat-label">总阅读时长</div>
    </div>
    <div class="stat-card">
      <div class="stat-value">{{totalReadWords}}</div>
      <div class="stat-label">已读字数</div>
    </div>
    <div class="stat-card">
      <div class="stat-value">{{readChapters}}</div>
      <div class="stat-label">已读章节</div>
    </div>
  </div>
  <div class="full-card">
    <h3>书籍详情</h3>
    <div class="detail-row"><span class="k">分类</span><span class="v">{{kind}}</span></div>
    <div class="detail-row"><span class="k">字数</span><span class="v">{{wordCount}}</span></div>
    <div class="detail-row"><span class="k">来源</span><span class="v">{{bookSourceName}}</span></div>
    <div class="detail-row"><span class="k">当前章节</span><span class="v">{{durChapterTitle}}</span></div>
    <div class="detail-row"><span class="k">首读时间</span><span class="v">{{firstReadTime}}</span></div>
    <div class="detail-row"><span class="k">标签</span><span class="v">{{tags}}</span></div>
    <div style="font-size:13px;color:#999;line-height:1.7;margin-top:10px;">{{intro}}</div>
  </div>
  <div class="full-card">
    <h3>评分</h3>
    <div class="stars">{{ratingStars}}</div>
    <div style="font-size:12px;color:#888;margin-top:2px;">{{rating}} / {{ratingMax}}</div>
    <div class="quote">{{reviewContent}}</div>
  </div>
  <div class="quote">{{latestAnnotation}}</div>
  <div style="font-size:11px;color:#666;text-align:right;margin-top:4px;">{{latestAnnotationChapter}}</div>
  <div class="footer">藏书票 · {{lastReadTime}}</div>
</body>
</html>
    """.trimIndent()

    private val BUILTIN_TEMPLATES = listOf(
        "暗黑科幻" to DEFAULT_TEMPLATE_HTML,
        "简约清新" to TEMPLATE_HTML_MINIMAL,
        "古典书香" to TEMPLATE_HTML_CLASSICAL,
        "现代卡片" to TEMPLATE_HTML_MODERN
    )

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

    private suspend fun resolveTemplate(selectedId: Long): BookplateTemplate? {
        if (selectedId > 0L) {
            return appDb.bookplateTemplateDao.getById(selectedId)
        }
        return null
    }

    suspend fun generate(context: Context, book: Book): Bitmap = withContext(Dispatchers.IO) {
        BookplateLogger.log("GEN", "开始生成藏书票 (Book): ${book.name} - ${book.author}")
        val selectedId = appCtx.getPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
        BookplateLogger.log("GEN", "选中模板ID: $selectedId (0=经典Canvas)")
        if (selectedId == 0L) {
            BookplateLogger.log("GEN", "使用经典Canvas样式")
            return@withContext io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity.createBookplateBitmap(context, book)
        }

        val template = resolveTemplate(selectedId)
            ?: appDb.bookplateTemplateDao.getBuiltins().firstOrNull()
            ?: getOrCreateBuiltinTemplates().firstOrNull()
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

        val template = resolveTemplate(selectedId)
            ?: appDb.bookplateTemplateDao.getBuiltins().firstOrNull()
            ?: getOrCreateBuiltinTemplates().firstOrNull()
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

    suspend fun getOrCreateBuiltinTemplates(): List<BookplateTemplate> {
        val existing = appDb.bookplateTemplateDao.getBuiltins()
        if (existing.size >= BUILTIN_TEMPLATES.size) return existing

        val now = System.currentTimeMillis()
        val existingNames = existing.map { it.name }.toSet()
        val newIds = mutableListOf<Long>()

        BUILTIN_TEMPLATES.forEach { (name, html) ->
            if (name !in existingNames) {
                val template = BookplateTemplate(
                    name = name,
                    htmlContent = html,
                    isBuiltin = true,
                    createTime = now,
                    updateTime = now
                )
                val id = appDb.bookplateTemplateDao.insert(template)
                newIds.add(id)
            }
        }

        val allIds = existing.map { it.id } + newIds
        if (allIds.isNotEmpty()) {
            appDb.bookplateTemplateDao.deleteBuiltinNotIn(allIds)
        }

        return appDb.bookplateTemplateDao.getBuiltins()
    }

    suspend fun getOrCreateBuiltinTemplate(): BookplateTemplate {
        return getOrCreateBuiltinTemplates().first()
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
