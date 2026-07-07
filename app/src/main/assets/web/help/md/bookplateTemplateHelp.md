# 藏书票模板帮助

## 模板分组

模板按使用场景分为三个默认分组，仅用于组织管理，各自可启用一个模板：

| 分组 | 使用场景 |
|------|----------|
| **书籍** | 书籍详情页、阅读记忆页生成藏书票 |
| **书摘** | 书摘详情页生成书摘卡片 |
| **统计** | 阅读统计页面生成统计卡片 |

所有变量在所有分组中均可使用，不受分组限制。变量值由生成页面决定，页面未提供值的变量会替换为空字符串。

---

## 模板语法

模板使用 HTML + CSS 编写，通过 `{{变量名}}` 插入数据。变量名使用驼峰命名（camelCase），用双大括号包裹。

---

## 可用变量一览

> 所有变量在所有模板中均可引用。带 `*` 标记的变量表示只在特定页面有实际值，其余页面为空。

### 基本信息

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{bookName}}` | 书名 | 三体 |
| `{{author}}` | 作者 | 刘慈欣 |
| `{{coverUrl}}` | 封面图片 URL | https://... 或 data:image/... |
| `{{intro}}` | 简介 | 文化大革命如火如荼... |
| `{{kind}}` | 分类标签 | 科幻,完结 |
| `{{wordCount}}` | 字数 | 90.00万字 |
| `{{typeText}}` | 媒体类型 | 文本 / 音频 / 视频 |
| `{{bookSourceName}}` | 书源名称 | 起点中文网 |
| `{{bookSourceGroup}}` | 书源分组 | 网络文学 |
| `{{totalChapterNum}}` | 总章节数 | 120 |
| `{{latestChapterTitle}}` | 最新章节标题 | 第一百二十章 终章 |
| `{{originName}}` | 原始来源名 | 起点中文网 |
| `{{charset}}` | 字符编码 | UTF-8 |

### 阅读状态

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{readingStatusText}}` | 阅读状态文本 | 在读 / 读完 / 弃文 / 待读 |
| `{{readingProgress}}` | 阅读进度百分比 | 75% |
| `{{readChapters}}` | 已读/总章节 | 90/120 |
| `{{unreadChapters}}` | 剩余章节数 | 30 |
| `{{readIteration}}` | 阅读遍数(原始值) | 2 |
| `{{readIterationText}}` | 阅读遍数(文本) | 二刷 |
| `{{durChapterTitle}}` | 当前阅读章节 | 第三章 红岸基地 |

### 阅读时间

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{totalReadTime}}` | 总阅读时长(格式化) | 12 小时 30 分钟 |
| `{{totalReadHours}}` | 总阅读小时数 | 12 |
| `{{totalReadMinutes}}` | 总阅读分钟数 | 30 |
| `{{readingDays}}` | 阅读天数 | 45 |
| `{{maxDayReadTime}}` | 单日最长阅读时间 | 3 小时 15 分钟 |
| `{{maxDayReadDate}}` | 单日最长阅读日期 | 2025年12月25日 |
| `{{firstReadTime}}` | 首次阅读时间 | 2025/01/15 |
| `{{lastReadTime}}` | 最近阅读时间 | 2025/03/20 |
| `{{finishReadTime}}` | 读完时间 | 2025/03/20 |
| `{{addBookshelfTime}}` | 加入书架时间 | 2025/01/10 |
| `{{lastCheckTime}}` | 最后检查时间 | 2025/03/21 |
| `{{lastReadTimeRelative}}` | 最近阅读相对时间 | 3天前 |

### 阅读统计

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{totalReadWords}}` | 已读字数 | 67.50万字 |
| `{{remainingWords}}` | 剩余字数 | 22.50万字 |

### 评分与书评

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{rating}}` | 评分(数值) | 4.0 |
| `{{ratingStars}}` | 星级(文本) | ★★★★☆ |
| `{{ratingMax}}` | 最高评分 | 5 |
| `{{reviewContent}}` | 书评内容 | 震撼人心的科幻巨作... |

### 书摘与想法

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{annotationCount}}` | 书摘条数 | 25 |
| `{{thoughtCount}}` | 想法条数 | 12 |
| `{{latestAnnotation}}` | 最新书摘内容 | 给岁月以文明... |
| `{{latestAnnotationNote}}` | 最新书摘笔记 | 这句话道出了本书的核心思想 |
| `{{latestAnnotationChapter}}` | 最新书摘所在章节 | 第三章 红岸基地 |
| `{{bookText}} *` | 当前书摘原文 | 给岁月以文明... |
| `{{noteContent}} *` | 当前批注/笔记 | 这句话强调了... |
| `{{chapterName}} *` | 当前书摘章节 | 第三章 红岸基地 |
| `{{time}} *` | 当前书摘时间 | 2025-06-30 14:30 |

### 其他

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{protagonists}}` | 主角列表 | 叶文洁, 罗辑, 史强 |
| `{{tags}}` | 标签 | #科幻 #长篇 #刘慈欣 |
| `{{tagCount}}` | 标签数量 | 3 |
| `{{readTimeRank}}` | 阅读时长排名 | 第 3 名 |

### 统计专属变量 `*`

以下变量仅在统计页面有实际值：

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{{pageTitle}}` | 页面标题 | 阅读统计 / 每日统计·2025-06-30 |
| `{{periodLabel}}` | 时间段标签 | 全部时间 / 2025-06-30 |
| `{{bookCount}}` | 阅读书籍数量 | 42 |
| `{{finishedBookCount}}` | 已读完数量 | 18 |
| `{{abandonedBookCount}}` | 已弃读数量 | 5 |
| `{{reviewCount}}` | 书评数量 | 12 |
| `{{readDays}}` | 阅读天数 | 365 |
| `{{totalWords}}` | 总字数（万） | 856.3 |
| `{{timeDays}}` | 阅读时长-天 | 12 |
| `{{timeHours}}` | 阅读时长-小时 | 8 |
| `{{timeMinutes}}` | 阅读时长-分钟 | 30 |
| `{{top1Name}}` ~ `{{top5Name}}` | 阅读时长排行 Top1~5 书名 | 三体 |
| `{{top1Time}}` ~ `{{top5Time}}` | 阅读时长排行 Top1~5 时长 | 45h30m |
| `{{top1Cover}}` ~ `{{top5Cover}}` | 阅读时长排行 Top1~5 封面 URL | (URL) |
| `{{peakPeriod}}` | 时段偏好 | 晚上 |
| `{{peakPeriodPct}}` | 时段偏好占比（%） | 42 |
| `{{continuousDays}}` | 最长连续阅读天数 | 128 |
| `{{authorTop1Name}}` ~ `{{authorTop5Name}}` | 最爱作者排行 Top1~5 姓名 | 金庸 |
| `{{authorTop1Time}}` ~ `{{authorTop5Time}}` | 最爱作者排行 Top1~5 阅读时长 | 120h30m |
| `{{tagTop1Name}}` ~ `{{tagTop5Name}}` | 最爱类型排行 Top1~5 标签名 | 武侠 |
| `{{tagTop1Count}}` ~ `{{tagTop5Count}}` | 最爱类型排行 Top1~5 书籍数量 | 15 |

> `*` 标记的变量在其他页面生成时值为空，不影响渲染。

---

## 常见 CSS class

内置模板使用以下 class 来控制阅读状态标签的样式，你可以直接复用：

```css
.status { /* 通用状态标签 */ }
.status.在读 { /* 绿色背景 */ }
.status.读完 { /* 蓝色背景 */ }
.status.弃文 { /* 红色背景 */ }
.status.待读 { /* 灰色背景 */ }
```

在 HTML 中这样使用状态标签：
```html
<span class="status {{readingStatusText}}">{{readingStatusText}}</span>
```

`{{readingStatusText}}` 会被替换为"在读/读完/弃文/待读"，同时作为 CSS class 应用对应的颜色样式。

## 模板编写规则

### 1. 变量必须用双大括号
```
正确: {{bookName}}
错误: {$bookName} 或 {{$book.name}}
```

### 2. 必须包含完整的 HTML 文档结构
```html
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>/* 你的 CSS */</style>
</head>
<body>
  <!-- 你的模板内容 -->
</body>
</html>
```

### 3. viewport 会自动注入
你不需要手动添加 viewport meta 标签，系统会自动注入。如果你自己写了，系统也会正确处理。

### 4. 使用 width: 100%; max-width: 100%
为确保模板在不同屏幕宽度下正确渲染，建议 body 使用：
```css
body {
  width: 100%;
  max-width: 100%;
  /* 你的其他样式 */
}
```

### 5. 封面图片处理
封面图片可能为空。建议添加 onerror 处理：
```html
<img src="{{coverUrl}}" onerror="this.style.display='none'" />
```

### 6. 文本内容可能包含 HTML 特殊字符
`intro`、`reviewContent`、`latestAnnotation` 等变量中的 `<`、`>`、`&`、`"` 已被自动转义为 HTML 实体，可以安全地作为文本内容使用。

### 7. 数值变量可用于条件判断
`rating`、`totalChapterNum`、`annotationCount` 等数值变量可用于 CSS 或 JS 条件。

## 模板示例

### 示例 1: 迷你藏书票
```html
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width:100%; max-width:100%; padding:20px;
    font-family:"Noto Serif SC",serif;
    background:#fffaf0; color:#3c3028;
  }
  .card {
    text-align:center; padding:20px;
    border:1px solid #d5c9b0; border-radius:4px;
  }
  .card h1 { font-size:22px; margin-bottom:4px; }
  .card .author { font-size:13px; color:#8c7355; margin-bottom:12px; }
  .card .info { font-size:13px; color:#666; line-height:1.8; }
  .stars { color:#c49530; font-size:16px; margin-top:8px; }
</style>
</head>
<body>
  <div class="card">
    <h1>{{bookName}}</h1>
    <div class="author">{{author}} 著</div>
    <div class="info">
      <div>{{kind}} · {{wordCount}}</div>
      <div>进度 {{readingProgress}} · {{readChapters}}章</div>
      <div>{{totalReadTime}}</div>
    </div>
    <div class="stars">{{ratingStars}}</div>
    <div style="font-size:12px;color:#888;margin-top:2px;">{{rating}} / {{ratingMax}}</div>
  </div>
</body>
</html>
```

### 示例 2: 带封面的简洁风格
```html
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width:100%; max-width:100%; padding:24px 16px;
    font-family:"Noto Sans SC","Microsoft YaHei",sans-serif;
    background:#f0f2f5; color:#333;
  }
  .book-card {
    background:#fff; border-radius:10px; padding:20px;
    box-shadow:0 1px 6px rgba(0,0,0,0.06);
  }
  .header { display:flex; align-items:center; gap:14px; margin-bottom:16px; }
  .cover { width:72px; height:96px; object-fit:cover; border-radius:6px; }
  .meta h2 { font-size:18px; margin-bottom:2px; }
  .meta .author { font-size:12px; color:#999; }
  .row { display:flex; justify-content:space-between; font-size:13px; padding:5px 0; border-bottom:1px solid #f5f5f5; }
  .row .label { color:#999; }
  .row .value { color:#333; }
</style>
</head>
<body>
  <div class="book-card">
    <div class="header">
      <img src="{{coverUrl}}" class="cover" onerror="this.style.display='none'" />
      <div class="meta">
        <h2>{{bookName}}</h2>
        <div class="author">{{author}}</div>
      </div>
    </div>
    <div class="row"><span class="label">进度</span><span class="value">{{readingProgress}}</span></div>
    <div class="row"><span class="label">已读</span><span class="value">{{readChapters}} 章</span></div>
    <div class="row"><span class="label">时长</span><span class="value">{{totalReadTime}}</span></div>
    <div class="row"><span class="label">字数</span><span class="value">{{totalReadWords}}</span></div>
    <div class="row"><span class="label">评分</span><span class="value">{{ratingStars}}</span></div>
  </div>
</body>
</html>
```

### 示例 3: 深色优雅风格
```html
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    width:100%; max-width:100%; padding:28px 20px;
    font-family:"Noto Serif SC",serif;
    background:#1a1a24; color:#d0d0d8;
    min-height:100vh;
  }
  .panel {
    border:1px solid rgba(255,255,255,0.08);
    border-radius:8px; padding:24px 20px;
    margin-bottom:14px;
  }
  h1 { font-size:24px; color:#f0d98c; text-align:center; letter-spacing:0.2em; margin-bottom:4px; }
  .subtitle { text-align:center; color:#888; font-size:12px; letter-spacing:0.3em; margin-bottom:18px; }
  .info-row { display:flex; justify-content:space-between; font-size:13px; margin:6px 0; }
  .info-row .k { color:#777; }
  .info-row .v { color:#ccc; text-align:right; }
  .stars { color:#f5a623; text-align:center; font-size:20px; margin:10px 0; }
  .footer { text-align:center; color:#555; font-size:11px; margin-top:20px; }
</style>
</head>
<body>
  <div class="panel">
    <h1>{{bookName}}</h1>
    <div class="subtitle">{{author}} · {{readingStatusText}}</div>
  </div>
  <div class="panel">
    <div class="info-row"><span class="k">进度</span><span class="v">{{readingProgress}}</span></div>
    <div class="info-row"><span class="k">已读</span><span class="v">{{readChapters}} 章</span></div>
    <div class="info-row"><span class="k">时长</span><span class="v">{{totalReadTime}}</span></div>
    <div class="info-row"><span class="k">字数</span><span class="v">{{totalReadWords}}</span></div>
    <div class="stars">{{ratingStars}}</div>
    <div style="text-align:center;color:#888;font-size:12px;">{{rating}} / {{ratingMax}}</div>
  </div>
  <div class="footer">始于 {{firstReadTime}} · 藏书票</div>
</body>
</html>
```

## 系统内置模板

### 书籍分组

应用内置了 4 套不同风格的书籍模板：

| 模板名 | 风格描述 |
|--------|----------|
| 暗黑科幻 | 深色渐变背景，金色文字，适合科幻/悬疑类书籍 |
| 简约清新 | 白色卡片式布局，清爽简洁，适合日常阅读记录 |
| 古典书香 | 米色纸张质感，衬线字体，中国风，适合文学/历史类 |
| 现代卡片 | 深色背景 + 亮色数据卡片，适合都市/网文类书籍 |

### 书摘分组

应用内置了 1 套默认书摘模板：

| 模板名 | 风格描述 |
|--------|----------|
| 古典书摘 | 米色渐变背景，衬线字体，引用符号装饰，适合书摘分享 |

### 统计分组

应用内置了 1 套默认统计模板：

| 模板名 | 风格描述 |
|--------|----------|
| 阅读统计 | 暖色古典卡片风格，展示阅读总览与时长数据 |

在模板管理页面，通过分组在不同场景的模板之间切换。每种分组独立管理，各自启用一个模板。

## 注意事项

1. **变量区分大小写**，`{{bookname}}` 和 `{{bookName}}` 是不同的
2. **所有变量在所有模板中均可使用**，未提供实际值的变量会替换为空字符串，不会报错
3. 模板中未使用到的变量不会影响渲染，不会有错误提示
4. 图片加载失败时会静默处理，不会影响整体布局
5. 渲染宽度为屏幕宽度的 92%，建议内容适配这个宽度设计
6. 支持 `clamp()` 等现代 CSS 函数来实现响应式字体
7. `@font-face` 引入的外部字体可能无法在离屏渲染中加载
