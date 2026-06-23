# 藏书票模板帮助

## 变量说明

模板使用 HTML 语法，可以通过变量插入书籍信息。

### 书籍信息变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{$book.name}}` | 书名 | 活着 |
| `{{$book.author}}` | 作者 | 余华 |
| `{{$book.kind}}` | 分类 | 小说 |
| `{{$book.custom}}` | 自定义标签 | 文学 |
| `{{$book.desc}}` | 简介 | 讲述人生的故事... |
| `{{$book.wordCount}}` | 字数 | 10万字 |
| `{{$book.latestChapter}}` | 最新章节 | 第100章 |
| `{{$book.bookUrl}}` | 书籍URL | https://... |
| `{{$book.coverUrl}}` | 封面URL | https://... |

### 书架信息变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{$shelf.name}}` | 书架名称 | 我的书架 |
| `{{$shelf.order}}` | 书架序号 | 1 |

### 时间变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{$time.year}}` | 年份 | 2024 |
| `{{$time.month}}` | 月份 | 6 |
| `{{$time.day}}` | 日 | 15 |
| `{{$time.hour}}` | 小时 | 14 |
| `{{$time.minute}}` | 分钟 | 30 |

### 翻页信息变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{$page.chapterName}}` | 章节名 | 第一章 |
| `{{$page.pageName}}` | 页名/位置 | P100 |
| `{{$page.totalPage}}` | 总页数 | 500 |
| `{{$page.readProgress}}` | 阅读进度 | 20% |

### 统计变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{$stat.tocSize}}` | 目录章节数 | 100 |
| `{{$stat.readTime}}` | 阅读时长(分钟) | 120 |
| `{{$stat.readCount}}` | 阅读次数 | 3 |
| `{{$stat.chapterCount}}` | 章节数 | 50 |
| `{{$stat.bookmarkCount}}` | 书签数 | 5 |

### 用户信息变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{$user.name}}` | 用户名 | 读者 |
| `{{$user.group}}` | 用户组 | VIP |

## CSS 样式变量

模板支持以下 CSS 变量：

```css
--book-name-font-size: 16px;    /* 书名字体大小 */
--book-name-color: #333333;      /* 书名颜色 */
--author-font-size: 12px;       /* 作者字体大小 */
--author-color: #666666;        /* 作者颜色 */
--page-font-size: 12px;         /* 页码字体大小 */
--page-color: #999999;          /* 页码颜色 */
--background-color: #ffffff;   /* 背景颜色 */
--width: 320px;                 /* 宽度 */
--height: 180px;                /* 高度 */
```

## 模板示例

```html
<div class="bookplate">
  <div class="cover">
    <img src="{{$book.coverUrl}}" />
  </div>
  <div class="info">
    <div class="name">{{$book.name}}</div>
    <div class="author">{{$book.author}}</div>
    <div class="page">{{$page.pageName}}</div>
    <div class="time">{{$time.year}}.{{$time.month}}.{{$time.day}}</div>
  </div>
</div>
```

## 注意事项

1. 变量使用双大括号 `{{}}` 包裹
2. 封面图片会自动裁剪为圆形或方形
3. 建议设置 `max-width` 为 `640px` 以适配手机竖屏
4. 支持完整的 CSS 样式
