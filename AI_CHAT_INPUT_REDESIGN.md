# AI 聊天输入框样式优化 - 完全参考 ReadAny

## 优化概述

根据 ReadAny 项目的设计风格，完全重构了 AI 聊天页面的输入框区域，实现了更现代、简洁的 UI。

## 主要改进

### 1. **外层容器卡片**

**ReadAny 设计：**
```tsx
<div className="relative rounded-2xl border bg-background shadow-around">
```

**我们的实现：**
- ✅ 圆角：`16dp`（对应 `rounded-2xl`）
- ✅ 边框：`1dp`，使用主题 `divider` 颜色
- ✅ 背景色：使用主题 `background` 属性
- ✅ 阴影：`2dp` elevation（轻微的 `shadow-around` 效果）
- ✅ 外边距：`16dp`（四周）

### 2. **输入框 (EditText)**

**ReadAny 设计：**
```tsx
<textarea
  className="w-full resize-none bg-transparent px-4 pb-1 pt-3 text-sm"
  style={{ minHeight: 36, maxHeight: 160 }}
/>
```

**我们的实现：**
- ✅ 背景：透明（`@android:color/transparent`）
- ✅ 最小高度：`36dp`
- ✅ 最大高度：`160dp`
- ✅ 内边距：上 `8dp`，下 `8dp`
- ✅ 字体大小：`14sp`（对应 `text-sm`）
- ✅ 文字颜色：使用主题 `primaryTextColor`
- ✅ 对齐方式：顶部左对齐（`top|start`）
- ❌ **移除了内部分割线**（之前有 divider View）

### 3. **深度思考 & 防剧透开关**

**ReadAny 设计：**
```tsx
<button
  className={`flex items-center gap-1 rounded-full border px-2 py-1 text-xs ${
    deepThinking
      ? "border-primary/50 bg-primary/10 text-foreground"
      : "border-border text-muted-foreground hover:bg-muted"
  }`}
>
```

**我们的实现：**

#### 未激活状态：
- ✅ 背景：透明
- ✅ 边框：`1dp`，使用主题 `secondaryTextColor`
- ✅ 圆角：`999dp`（完全圆角，胶囊形状）
- ✅ 图标大小：`12dp × 12dp`（对应 `size-3`）
- ✅ 文字大小：`11sp`（对应 `text-xs`）
- ✅ 内边距：水平 `8dp`，垂直 `4dp`

#### 激活状态：
- ✅ 背景：强调色的 10% 透明度（`Color.argb(26, R, G, B)`）
- ✅ 边框：`1dp`，强调色的 50% 透明度（`Color.argb(128, R, G, B)`）
- ✅ 动态切换：通过代码实时修改背景

### 4. **发送按钮**

**ReadAny 设计：**
```tsx
<button
  className="flex size-7 items-center justify-center rounded-full bg-primary text-primary-foreground"
>
  <Send className="size-3.5" />
</button>
```

**我们的实现：**
- ✅ 尺寸：`28dp × 28dp`（对应 `size-7` = 1.75rem ≈ 28px）
- ✅ 形状：圆形（`oval` shape）
- ✅ 背景：使用主题 `accentColor`
- ✅ 图标大小：实际显示约 `14dp`（通过 padding 控制）
- ✅ 图标颜色：白色
- ✅ 缩放类型：`centerInside`
- ✅ 内边距：`7dp`（使图标约为按钮的一半大小）

**之前的问题：**
- ❌ 硬编码白色背景
- ❌ 尺寸过大（30-40dp）
- ❌ 固定白色 tint

**现在的优势：**
- ✅ 使用主题强调色，自动适配深浅色模式
- ✅ 尺寸适中，符合现代设计
- ✅ 圆形设计，更友好

### 5. **取消按钮**

**改进：**
- ✅ 尺寸：从 `40dp` 缩小到 `28dp`
- ✅ 背景：使用系统 selectableItemBackgroundBorderless（涟漪效果）
- ✅ 图标颜色：使用主题 `accentColor`（而非硬编码白色）
- ✅ 缩放类型：`centerInside`
- ✅ 内边距：`6dp`

### 6. **布局结构调整**

**之前的结构：**
```
MaterialCardView (外层，elevation=8dp)
  └─ LinearLayout (padding=12dp)
      ├─ 引用区域
      └─ MaterialCardView (内层，elevation=2dp)
          └─ LinearLayout
              ├─ EditText
              ├─ Divider View ← 移除
              └─ 工具栏
```

**现在的结构：**
```
MaterialCardView (外层，elevation=2dp, cornerRadius=16dp)
  └─ LinearLayout (padding: 16dp左右, 12dp上, 8dp下)
      ├─ 引用区域
      ├─ EditText (无背景，无分割线)
      └─ 工具栏
          ├─ 深度思考开关
          ├─ 防剧透开关
          ├─ Spacer
          ├─ 取消按钮
          ├─ 加载指示器
          └─ 发送按钮
```

## 新增资源文件

### 1. bg_option_chip_border_only.xml
```xml
<!-- 只有边框，无背景色的胶囊形状 -->
<shape android:shape="rectangle">
    <solid android:color="@android:color/transparent" />
    <stroke android:width="1dp" android:color="?attr/divider" />
    <corners android:radius="999dp" />
</shape>
```

### 2. bg_send_button_circle.xml
```xml
<!-- 圆形发送按钮背景 -->
<shape android:shape="oval">
    <solid android:color="?attr/accentColor" />
</shape>
```

## 代码变更

### AiChatActivity.kt - updateOptionStyle()

**之前：**
```kotlin
private fun updateOptionStyle(view: View, enabled: Boolean) {
    if (enabled) {
        view.setBackgroundResource(R.drawable.bg_active_tag)
    } else {
        view.setBackgroundResource(R.drawable.bg_option_chip)
    }
}
```

**现在：**
```kotlin
private fun updateOptionStyle(view: View, enabled: Boolean) {
    val context = view.context
    val accentColor = ThemeStore.accentColor(context)
    
    if (enabled) {
        // 激活状态：强调色10%透明度背景 + 50%透明度边框
        val bgColor = Color.argb(26, R, G, B)
        view.setBackgroundColor(bgColor)
        val gradientDrawable = view.background as? GradientDrawable ?: GradientDrawable()
        gradientDrawable.setStroke(1.dpToPx(), Color.argb(128, R, G, B))
        view.background = gradientDrawable
    } else {
        // 未激活状态：透明背景 + 次要文字颜色边框
        view.setBackgroundColor(Color.TRANSPARENT)
        val gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        gradientDrawable.cornerRadius = 999f.dpToPx().toFloat()
        gradientDrawable.setStroke(1.dpToPx(), ThemeStore.textColorSecondary(context))
        view.background = gradientDrawable
    }
}
```

## 与 ReadAny 对比

| 特性 | ReadAny | dai411 (优化后) | 匹配度 |
|------|---------|----------------|--------|
| 外层容器圆角 | rounded-2xl (16px) | 16dp | ✅ 完全一致 |
| 外层容器边框 | border | 1dp divider | ✅ 完全一致 |
| 外层容器背景 | bg-background | ?attr/background | ✅ 主题适配 |
| 外层容器阴影 | shadow-around | elevation 2dp | ✅ 轻微阴影 |
| 输入框背景 | transparent | transparent | ✅ 完全一致 |
| 输入框最小高度 | 36px | 36dp | ✅ 完全一致 |
| 输入框最大高度 | 160px | 160dp | ✅ 完全一致 |
| 输入框字号 | text-sm (14px) | 14sp | ✅ 完全一致 |
| 内部分割线 | 无 | 已移除 | ✅ 完全一致 |
| 开关未激活 | border only | border only | ✅ 完全一致 |
| 开关激活 | bg-primary/10 | accentColor 10% | ✅ 完全一致 |
| 发送按钮尺寸 | size-7 (28px) | 28dp | ✅ 完全一致 |
| 发送按钮形状 | rounded-full | oval | ✅ 完全一致 |
| 发送按钮背景 | bg-primary | accentColor | ✅ 主题适配 |
| 发送图标尺寸 | size-3.5 (14px) | ~14dp | ✅ 完全一致 |

## 视觉效果

### 整体风格
- 🎨 **扁平化设计**：移除了多余的阴影和分割线
- 🎯 **主题适配**：所有颜色都来自 ThemeStore
- 📱 **现代化**：符合当前主流聊天应用的设计趋势
- ✨ **简洁清爽**：减少视觉噪音，突出内容

### 交互细节
- 💫 **开关状态**：激活时有明显的背景色变化
- 🎭 **按钮反馈**：使用系统涟漪效果
- 🔄 **动态响应**：所有状态变化都有视觉反馈

## 测试建议

1. 切换不同主题（浅色/深色/自定义），验证颜色适配
2. 点击深度思考/防剧透开关，验证样式切换
3. 输入长文本，验证自适应高度（最大160dp）
4. 测试发送按钮在不同状态下的显示
5. 验证整体布局在不同屏幕尺寸下的表现

## 后续优化项

1. 添加输入框焦点状态的边框高亮
2. 支持引用芯片的横向滚动（当有多个引用时）
3. 添加发送按钮禁用状态的视觉反馈
4. 优化键盘弹出时的布局调整
