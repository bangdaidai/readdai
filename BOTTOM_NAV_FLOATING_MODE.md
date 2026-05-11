# 底部导航栏悬浮模式实现说明

## 功能概述

为 readdai 项目实现了**经典模式**和**悬浮模式**两种底部导航栏样式：
- **经典模式**: 固定底部导航栏，纯实色背景，不透明（无效果选择）
- **悬浮模式**: 浮动导航栏，支持三种效果（实色/玻璃/磨砂玻璃），使用 LiquidGlassView 实现真正的模糊效果

## 实现内容

### 1. 配置项添加

#### PreferKey.kt
添加了以下配置键：
- `frostedGlassLevel`: 磨砂玻璃透明度级别
- `liquidGlassLevel`: 液体玻璃透明度级别  
- `bottomBarEffectMode`: 底栏效果模式（solid/glass/frosted）
- `bottomBarLayoutMode`: 底栏布局模式（classic/floating）

#### AppConfig.kt
添加了对应的配置属性，包含默认值和验证逻辑。

### 2. 布局改造

#### activity_main.xml
使用 FrameLayout 包含两种布局模式：
- **经典模式 (classic_layout)**: 原有的 LinearLayout 布局，底部固定导航栏
- **悬浮模式 (floating_layout)**: ConstraintLayout 布局，单个胶囊容器包含5个按钮
  - 移除了独立的搜索按钮
  - 只保留一个 LiquidGlassView 容器包裹 BottomNavigationView
  - 胶囊形状：高度 56dp，圆角 28dp（完美胶囊）
  - 左右边距 20dp，底部边距 10dp

#### bg_bottom_nav_floating.xml
创建了圆角矩形背景 drawable，用于悬浮导航栏的基础样式：
- 圆角半径：28dp（形成完美胶囊）
- 默认颜色：半透明白色 (#CCFFFFFF)

### 3. MainActivity 增强

##### 双布局支持
- 添加 `currentPageViewPager` 和 `currentBottomNav` 属性，根据模式自动选择正确的视图
- `initView()` 方法根据配置显示经典或悬浮布局
- 所有页面切换和菜单操作都使用动态选择的视图

##### updateBottomBarStyle() 方法
仅在悬浮模式下应用样式效果：

**经典模式**:
- 纯实色背景，完全不透明 (alpha = 1.0)
- 不支持玻璃/磨砂效果
- 保持原有设计，兼容性强

**悬浮模式 - 三种效果**:
- **实色模式 (solid)**: 纯色背景，完全不透明，隐藏 LiquidGlassView
- **玻璃模式 (glass)**: 使用 LiquidGlassView，中等透明度，轻微模糊和折射效果
- **磨砂玻璃模式 (frosted)**: 使用 LiquidGlassView + 真实模糊效果，更强的模糊和更少的折射

LiquidGlassView 实现真正的磨砂玻璃效果（参考 archive 项目）：
```kotlin
// Frosted mode parameters
val blurRadius = (10f + glassLevel * 24f).dpToPx()  // 强模糊
val tintAlpha = 0.12f + glassLevel * 0.18f          // 较高不透明度
val dispersion = (0.18f + glassLevel * 0.16f)       // 低色散

// Glass mode parameters  
val blurRadius = (5f + glassLevel * 14f).dpToPx()   // 轻模糊
val tintAlpha = 0.05f + glassLevel * 0.10f          // 较低不透明度
val dispersion = 0.46f + glassLevel * 0.32f         // 高色散

liquidGlassView.bind(contentContainer)  // 绑定到内容容器实现模糊
liquidGlassView.setBlurRadius(blurRadius)
liquidGlassView.setTintAlpha(tintAlpha)
liquidGlassView.setDispersion(dispersion)
liquidGlassView.setRefractionHeight(refractionHeight)
liquidGlassView.setRefractionOffset(refractionOffset)
```

支持日夜主题自动切换背景颜色。

### 4. NavigationBarManageDialog 完善

#### 新增功能
- **布局模式选择**: 经典模式 / 悬浮模式
- **效果模式选择**: 实色 / 玻璃 / 磨砂玻璃（仅悬浮模式可见）
- **不透明度控制**: 根据效果模式分别控制 frosted/liquid 透明度（实色模式隐藏）
- **智能UI**: 经典模式下隐藏效果和透明度选项，简化界面
- **实时预览**: 修改配置后立即生效

#### UI 改进
- 卡片式选项行，可点击展开选择器
- 显示当前选中的配置值
- SeekBar 实时显示百分比数值

### 5. 字符串资源

添加了完整的中英文字符串资源：
- 配置项标签
- 模式名称
- 操作提示
- 确认对话框文本

## 使用方法

### 访问配置
1. 进入"我的"页面
2. 点击"设置"
3. 选择"主题与外观"
4. 点击"底栏管理"

### 配置选项

#### 底栏样式
- **经典模式**: 传统的底部固定导航栏，与原有设计保持一致
- **悬浮模式**: 导航栏浮动在页面底部，带圆角和阴影，现代感更强

#### 底栏效果
- **实色**: 纯色背景，可调节不透明度
- **玻璃**: 半透明玻璃效果，现代感强
- **磨砂玻璃**: 更高透明度，模拟 iOS 磨砂效果

#### 不透明度
- 范围：0-100%
- 默认值：玻璃模式 68%，磨砂模式 76%
- 实时调整，立即生效

## 技术细节

### 玻璃效果实现
当前使用透明度模拟玻璃效果：
```kotlin
backgroundColor = if (night) "#CC1A1A1A" else "#CCFFFFFF"
alpha = opacityLevel / 100f
```

未来可以升级为真正的模糊效果（需要 Android 12+ RenderEffect API）。

### 布局适配
- 使用 ConstraintLayout 实现灵活的浮动布局
- 导航栏高度：48dp
- 圆角半径：24dp
- 阴影 elevation：12dp
- 搜索按钮独立浮动，elevation：14dp

### 主题兼容
- 自动检测日夜主题
- 动态切换背景颜色
- 保持视觉一致性

## 注意事项

1. **经典模式**: 保持原有的底部固定导航栏设计，纯实色背景，完全不透明，无效果选择
2. **悬浮模式效果**: 实色/玻璃/磨砂三种效果仅在悬浮模式下可用
3. **磨砂玻璃实现**: 使用 LiquidGlassView 库实现真正的模糊效果，需要绑定到内容容器
4. **性能考虑**: LiquidGlassView 的模糊效果会消耗一定 GPU 资源，但优化良好
5. **兼容性**: 最低支持 Android 5.0，LiquidGlassView 在所有版本上均可工作

## 后续优化建议

1. 添加模式切换动画过渡效果
2. 支持自定义圆角大小和阴影强度
3. 添加预设主题快速切换
4. 悬浮模式下搜索按钮可自定义位置
5. 为 LiquidGlassView 添加性能优化选项（如降低模糊质量以提高性能）
