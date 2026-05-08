# Legado主题系统说明 - AI页面适配指南

## ⚠️ 重要理解

### Legado不使用Android标准主题属性！

Legado使用的是**自定义主题系统**（ThemeStore），而不是Android标准的`?attr/xxx`主题属性。

## 🎨 Legado主题工作原理

### 1. 用户自定义主题流程
```
用户在"主题设置"中选择/自定义主题
    ↓
颜色值保存到SharedPreferences (PreferKey.cBackground等)
    ↓
ThemeConfig.applyTheme() 读取Preferences
    ↓
ThemeStore.editTheme() 应用颜色到ThemeStore
    ↓
BaseActivity.onCreate() 调用 setupSystemBar()
    ↓
window.decorView.setBackgroundColor(backgroundColor)
    ↓
所有子视图继承背景色
```

### 2. 关键代码位置

**ThemeConfig.kt** (第428-522行):
```kotlin
fun applyTheme(context: Context) = with(context) {
    val themeEditor = ThemeStore.editTheme(this)
    // 根据日夜模式读取用户自定义的颜色
    val background = getPrefInt(PreferKey.cBackground, ...)
    val primary = getPrefInt(PreferKey.cPrimary, ...)
    // ... 其他颜色
    
    themeEditor
        .primaryColor(primary)
        .accentColor(accent)
        .backgroundColor(background)  // ← 用户自定义的背景色
        .bottomBackground(bBackground)
        .backgroundCard(bCard)
        .textColorPrimary(textPrimary)
        .textColorSecondary(textSecondary)
        .apply()
}
```

**BaseActivity.kt** (第152-179行):
```kotlin
private fun setupSystemBar() {
    // 应用用户自定义的背景色
    window.decorView.applyBackgroundTint(backgroundColor)
    // backgroundColor 来自 ThemeStore.backgroundColor(this)
}
```

**MaterialValueHelper.kt** (第62-63行):
```kotlin
val Context.backgroundColor: Int
    get() = ThemeStore.backgroundColor(this)  // ← 从ThemeStore读取
```

## ✅ 正确的做法

### 布局文件中
```xml
<!-- ✅ 正确：不设置background，让BaseActivity自动处理 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    
    <!-- 子视图会继承背景色 -->
    
</LinearLayout>
```

### ❌ 错误的做法
```xml
<!-- ❌ 错误：不要使用 ?attr/xxx -->
<LinearLayout
    android:background="?attr/primaryBackgroundColor">
    
<!-- ❌ 错误：不要硬编码颜色 -->
<LinearLayout
    android:background="#FFFFFF">
    
<!-- ❌ 错误：不要手动设置背景 -->
<LinearLayout
    android:background="@color/background">
```

## 📋 AI页面适配检查清单

### activity_ai_chat.xml
- [x] 根布局**没有**设置background属性
- [x] 快捷提示区**没有**设置background属性  
- [x] 底部输入区**没有**设置background属性
- [x] 所有文字颜色使用 `?attr/primaryTextColor` (这个可以，因为需要动态获取)
- [x] 分隔线使用 `?attr/divider` (这个可以)

**注意**：文字颜色和分隔线可以使用`?attr/xxx`，因为它们需要从ThemeStore动态获取。但**背景色不能**，因为背景由BaseActivity统一设置。

### AiChatActivity.kt
- [x] 继承 `BaseActivity<ActivityAiChatBinding>()`
- [x] 使用 ViewBinding
- [x] **不需要**手动设置背景色
- [x] **不需要**手动设置状态栏颜色
- [x] BaseActivity会自动处理一切

## 🔍 为什么不能用 ?attr/primaryBackgroundColor？

### Android标准主题 vs Legado自定义主题

| 特性 | Android标准主题 | Legado主题 |
|------|----------------|-----------|
| 颜色定义 | res/values/themes.xml | SharedPreferences |
| 应用方式 | ?attr/xxx | ThemeStore + BaseActivity |
| 切换主题 | 重启Activity | recreate() |
| 背景设置 | 布局中指定 | BaseActivity自动设置 |

Legado的`?attr/primaryBackgroundColor`可能：
1. 指向默认颜色，不是用户自定义的
2. 不会随用户切换主题而更新
3. 与BaseActivity设置的背景色冲突

## 🎯 总结

### 核心原则
1. **背景色**：完全交给BaseActivity处理，布局中不设置
2. **文字颜色**：可以使用`?attr/primaryTextColor`等
3. **Drawable**：内部应该引用主题颜色或固定颜色
4. **一致性**：与应用其他页面保持相同做法

### 验证方法
1. 打开AI聊天页面
2. 进入"设置" → "主题设置"
3. 切换到不同的主题（浅色/深色/自定义）
4. 返回AI聊天页面
5. 背景色应该立即跟随变化

如果背景色没有变化，说明布局中硬编码了背景，需要移除。

## 📝 相关代码参考

### 用户如何自定义主题
1. 打开"设置" → "主题设置"
2. 选择预设主题或自定义颜色
3. 点击"应用"
4. ThemeConfig.saveDayTheme()/saveNightTheme() 保存
5. ThemeConfig.applyTheme() 应用到ThemeStore
6. Activity重建，BaseActivity读取新颜色

### ThemeStore存储的颜色
- `KEY_PRIMARY_COLOR` - 主色
- `KEY_ACCENT_COLOR` - 强调色
- `KEY_BACKGROUND_COLOR` - 背景色 ⭐
- `KEY_BOTTOM_BACKGROUND` - 底部背景
- `KEY_BACKGROUND_CARD` - 卡片背景
- `KEY_TEXT_COLOR_PRIMARY` - 主要文字
- `KEY_TEXT_COLOR_SECONDARY` - 次要文字

所有这些颜色都通过BaseActivity自动应用到UI，**不需要在布局中手动设置**。
