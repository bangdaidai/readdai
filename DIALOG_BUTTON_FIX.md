# 技能编辑对话框按钮显示问题修复

## 问题描述

在编辑技能的对话框中，当指令内容较多时，底部的"取消"、"删除"、"保存"按钮会被挤出屏幕可视区域，导致用户无法看到和点击这些按钮。

## 问题原因

1. **对话框内容过多**：技能编辑对话框包含4个输入框（名称、描述、触发词、指令）
2. **指令输入框高度过大**：`maxLines="15"` 导致指令输入框可能占用大量垂直空间
3. **缺少滚动支持**：原布局使用 `LinearLayout`，没有包裹在 `ScrollView` 中，当内容超出屏幕高度时无法滚动

## 解决方案

### 1. 添加 ScrollView 包裹

将对话框的根布局从 `LinearLayout` 改为 `ScrollView`，并在内部嵌套 `LinearLayout`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">
        
        <!-- 原有内容 -->
        
    </LinearLayout>

</ScrollView>
```

**关键点：**
- `android:fillViewport="true"`：确保 ScrollView 至少填满整个视口，即使内容较少时也能正常显示
- 保持内部 `LinearLayout` 的 `android:layout_height="wrap_content"`，让内容自然扩展

### 2. 减少指令输入框的最大行数

将指令输入框的 `maxLines` 从 15 减少到 10：

```xml
<io.legado.app.lib.theme.view.ThemeEditText
    android:id="@+id/et_instruction"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="详细指令（系统提示词模板）"
    android:inputType="textMultiLine|textCapSentences"
    android:maxLines="10"  <!-- 从 15 改为 10 -->
    android:gravity="top|start"
    android:scrollbars="vertical" />
```

**优势：**
- 减少初始占用空间，给其他字段更多显示空间
- 仍然可以通过滚动查看更多内容
- 按钮更容易出现在可视区域内

## 修改文件

1. ✅ `app/src/main/res/layout/dialog_ai_skill.xml`
   - 添加 ScrollView 包裹
   - 减少指令输入框 maxLines 从 15 到 10

2. ✅ `app/src/main/res/layout/dialog_ai_quick_action_edit.xml`
   - 添加 ScrollView 包裹（预防性修复）

## 效果对比

### 修复前
- ❌ 内容过多时按钮被挤出屏幕
- ❌ 用户无法看到和操作按钮
- ❌ 需要手动调整窗口大小或旋转屏幕才能看到按钮

### 修复后
- ✅ 内容可以垂直滚动
- ✅ 按钮始终可访问（滚动到底部即可看到）
- ✅ 适配各种屏幕尺寸
- ✅ 用户体验更好

## 技术要点

### ScrollView 最佳实践

1. **fillViewport 属性**
   ```xml
   android:fillViewport="true"
   ```
   - 确保 ScrollView 至少填满父容器
   - 避免内容较少时出现空白区域

2. **子布局高度**
   ```xml
   android:layout_height="wrap_content"
   ```
   - 让内容自然扩展
   - 不要使用 `match_parent`，会导致滚动失效

3. **避免嵌套滚动**
   - ScrollView 内部不要再放 RecyclerView 或其他滚动组件
   - EditText 的 `scrollbars="vertical"` 是独立的，不会冲突

### 输入框 maxLines 设置

- **单行输入**：`maxLines="1"`
- **简短文本**：`maxLines="2-3"`
- **中等长度**：`maxLines="5-10"`
- **长文本**：`maxLines="10-15` + ScrollView

## 类似问题的预防

对于所有包含多个输入字段的对话框，建议：

1. **优先使用 ScrollView**：特别是字段数量 ≥ 3 时
2. **合理设置 maxLines**：根据字段重要性分配空间
3. **测试不同屏幕尺寸**：确保在小屏幕上也能正常使用
4. **考虑横屏模式**：横屏时垂直空间更少，更需要滚动支持

## 相关布局规范

参考项目中的其他对话框布局：
- `dialog_ai_prompt.xml`：2个字段，未使用 ScrollView（内容较少）
- `dialog_ai_assistant_config_item.xml`：多个字段，应检查是否需要 ScrollView
- 其他复杂对话框都应考虑添加滚动支持

## 测试建议

1. **小屏幕测试**：在低分辨率设备上测试
2. **长内容测试**：输入很长的指令文本
3. **横屏测试**：旋转屏幕测试横屏模式
4. **键盘弹出测试**：确保键盘弹出时仍可滚动查看按钮
