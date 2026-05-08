# 快捷操作栏优化 - 4个位置固定布局

## 📋 变更说明

将快捷操作栏从**5个位置**优化为**4个位置**，并改为**固定布局**，与输入框左右对齐，无需滚动。

---

## 🎯 优化原因

1. **视觉对齐**：4个位置可以更好地与输入框左右对齐（padding都是16dp）
2. **简洁美观**：避免横向滚动，所有按钮一目了然
3. **空间利用**：4个位置足够覆盖核心功能，减少用户选择困难

---

## 🔧 修改内容

### 1. 布局文件修改

**文件**: `activity_ai_chat.xml`

**变更前**:
```xml
<!-- 使用HorizontalScrollView，支持滚动 -->
<HorizontalScrollView>
    <LinearLayout android:id="@+id/prompts_container" />
</HorizontalScrollView>
```

**变更后**:
```xml
<!-- 固定4个Chip，与输入框左右对齐 -->
<LinearLayout
    android:paddingStart="16dp"
    android:paddingEnd="16dp">
    
    <TextView android:id="@+id/chipQuickAction1" ... />
    <TextView android:id="@+id/chipQuickAction2" ... />
    <TextView android:id="@+id/chipQuickAction3" ... />
    <TextView android:id="@+id/chipQuickAction4" ... />
    
</LinearLayout>
```

**关键改动**:
- ✅ 移除HorizontalScrollView
- ✅ 添加4个固定的TextView作为Chip
- ✅ 每个Chip使用`layout_weight="1"`均分宽度
- ✅ padding与输入框保持一致（16dp）
- ✅ Chip之间间距8dp

---

### 2. 代码逻辑修改

**文件**: `AiChatActivity.kt`

**方法**: `renderQuickActionBar()`

**变更前**:
```kotlin
// 动态创建TextView并添加到容器
quickActionItems.forEach { item ->
    val textView = TextView(this).apply { ... }
    binding.promptsContainer.addView(textView)
}
```

**变更后**:
```kotlin
// 使用固定的4个Chip视图
val chipViews = listOf(
    binding.chipQuickAction1,
    binding.chipQuickAction2,
    binding.chipQuickAction3,
    binding.chipQuickAction4
)

// 隐藏所有chip
chipViews.forEach { it.visibility = View.GONE }

// 动态显示（最多4个）
quickActionItems.take(4).forEachIndexed { index, item ->
    chipViews[index].apply {
        text = item.displayName
        visibility = View.VISIBLE
        setOnClickListener { ... }
    }
}
```

---

### 3. 配置管理器修改

**文件**: `AiAssistantConfigManager.kt`

**默认配置从5个改为4个**:

**无选中文字**:
```kotlin
// 之前：5个
listOf(
    QuickActionItem(0, "智能问答", "..."),
    QuickActionItem(1, "总结本章", "..."),
    QuickActionItem(2, "人物分析", "..."),
    QuickActionItem(3, "主题解读", "..."),
    QuickActionItem(4, "前情回顾", "...")  // ❌ 删除
)

// 现在：4个
listOf(
    QuickActionItem(0, "智能问答", "..."),
    QuickActionItem(1, "总结本章", "..."),
    QuickActionItem(2, "人物分析", "..."),
    QuickActionItem(3, "主题解读", "...")
)
```

**有选中文字**:
```kotlin
// 之前：5个
listOf(
    QuickActionItem(0, "解释这个", "..."),
    QuickActionItem(1, "为什么", "..."),
    QuickActionItem(2, "举个例子", "..."),
    QuickActionItem(3, "换句话说", "..."),
    QuickActionItem(4, "深层含义", "...")  // ❌ 删除
)

// 现在：4个
listOf(
    QuickActionItem(0, "解释这个", "..."),
    QuickActionItem(1, "为什么", "..."),
    QuickActionItem(2, "举个例子", "..."),
    QuickActionItem(3, "换句话说", "...")
)
```

---

### 4. 设置对话框修改

**文件**: `AiSettingsPreferenceFragment.kt`

**标题文本更新**:
```kotlin
// 之前
val title = if (hasSelectedText) 
    "快捷操作栏 - 有选中文字（固定5个位置）" 
else 
    "快捷操作栏 - 无选中文字（固定5个位置）"

// 现在
val title = if (hasSelectedText) 
    "快捷操作栏 - 有选中文字（固定4个位置）" 
else 
    "快捷操作栏 - 无选中文字（固定4个位置）"
```

---

## 📊 对比效果

### 之前（5个位置 + 滚动）
```
┌─────────────────────────────────────┐
│ [智能问答] [总结本章] [人物分析]     │ ← 需要横向滚动才能看到后两个
│ [主题解读] [前情回顾] →              │
└─────────────────────────────────────┘
┌─────────────────────────────────────┐
│ 输入框...                      [发送]│
└─────────────────────────────────────┘
```

### 现在（4个位置 + 固定）
```
┌─────────────────────────────────────┐
│ [智能问答] [总结本章] [人物分析]     │ ← 全部可见，无需滚动
│ [主题解读]                           │
└─────────────────────────────────────┘
┌─────────────────────────────────────┐
│ 输入框...                      [发送]│
└─────────────────────────────────────┘
```

**优势**:
- ✅ 所有按钮一目了然
- ✅ 与输入框左右对齐（padding都是16dp）
- ✅ 无需横向滚动
- ✅ 视觉更整洁

---

## 🎨 UI细节

### Chip样式
- **背景**: `@drawable/bg_quick_action_item`
- **内边距**: 水平12dp，垂直8dp
- **字体大小**: 13sp
- **文字颜色**: `@color/primaryText`
- **对齐方式**: 居中（gravity="center"）

### 布局间距
- **Chip之间**: 8dp（marginStart）
- **左右padding**: 16dp（与输入框一致）
- **上下padding**: 8dp

### 响应式
- 每个Chip使用`layout_weight="1"`均分宽度
- 如果配置少于4个，多余的Chip自动隐藏（visibility="gone"）
- 最多显示4个，超出的配置项被忽略（take(4)）

---

## ✅ 测试要点

1. **布局对齐**
   - [ ] 快捷操作栏左右padding与输入框一致（16dp）
   - [ ] 4个Chip均匀分布，无溢出

2. **功能正常**
   - [ ] 点击Chip能正确填充触发词到输入框
   - [ ] 根据选中文字状态切换内容
   - [ ] 配置修改后能正确显示

3. **边界情况**
   - [ ] 配置少于4个时，只显示配置的数量的Chip
   - [ ] 配置为空时，整个快捷操作栏隐藏
   - [ ] 应用重启后配置仍然有效

---

## 📝 注意事项

1. **配置数量限制**
   - 用户在设置中最多只能配置4个快捷操作
   - 如果尝试配置超过4个，超出的部分会被忽略

2. **兼容性**
   - 如果用户之前配置了5个，第5个配置会被保留但不会显示
   - 建议用户重新配置为4个

3. **恢复默认**
   - 点击"恢复默认"会重置为预设的4个配置
   - 不会影响其他配置项

---

## 🔄 迁移建议

如果用户之前自定义了5个快捷操作：

1. **自动处理**: 系统会自动取前4个配置显示
2. **手动调整**: 建议用户进入设置，重新配置为最需要的4个功能
3. **备份配置**: 如需保留第5个配置，可以先记录下来

---

**完成时间**: 2026-05-05  
**影响范围**: UI布局、配置管理、用户体验  
**向后兼容**: ✅ 是（旧配置仍然有效，只是第5个不显示）
