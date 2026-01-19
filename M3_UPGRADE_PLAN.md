# SmartTodo Material Design 3 (M3) UI 升级与体验优化计划

本计划旨在基于 Google 最新的 **Material Design 3 (M3)** 规范，对 SmartTodo 进行全方位的视觉升级与交互优化，提升 App 的专业感与用户体验。

---

## 1. 核心视觉升级：动态配色 (Dynamic Color)

M3 的灵魂在于个性化。我们将引入 Android 12+ 支持的动态配色系统。

- [ ] **动态主题接入**：修改 `Theme.kt`，引入 `dynamicLightColorScheme` 和 `dynamicDarkColorScheme`。
- [ ] **壁纸联动**：App 颜色将根据用户桌面壁纸自动生成主色、次色和强调色。
- [ ] **备选方案**：为低版本 Android 系统保留一套标准的 M3 调色板（基于项目 Logo 提取）。

## 2. 界面结构重构：表现力布局

利用 M3 的新组件提升界面的“呼吸感”和层级感。

- [ ] **大标题联动滚动 (LargeTopAppBar)**：
    - 在“待办”和“信息流”主页，标题初始状态以大字显示。
    - 随着列表上滑，标题优雅收缩至标准高度，并改变背景色/添加阴影。
- [ ] **沉浸式导航栏 (NavigationBar)**：
    - 将现有的底部导航升级为 M3 标准的 `NavigationBar`。
    - 优化选中状态的指示器（胶囊型高亮）。
- [ ] **增强型卡片 (M3 Cards)**：
    - 统一使用 `OutlinedCard` 或 `FilledCard`。
    - 调整圆角为 M3 标准的 12dp/16dp。

## 3. 交互体验优化：微动效与触感

让每一次操作都有反馈。

- [ ] **全新下拉刷新 (PullToRefresh)**：
    - 引入 M3 1.3 版本的 `PullToRefreshBox`，替换旧版的圆形进度条。
- [ ] **触感反馈深度整合 (Haptic Feedback)**：
    - 列表长按、任务完成勾选、对话框弹出时，加入不同震动强度的微反馈。
- [ ] **全局对话框升级**：
    - 统一对话框圆角为 28dp。
    - 优化对话框弹出/消失的过渡动画。

## 4. 响应式与自适应 (Adaptive)

确保 App 在各种设备上都有优秀表现。

- [ ] **WindowSizeClass 适配**：
    - 针对平板和折叠屏，自动将底部导航切换为侧边导航栏 (Navigation Rail)。

---

## 已完成的优化

- [x] **动态配色 (Dynamic Color)**: 接入 Material 3 动态配色系统，支持 Android 12+ 提取系统主题色。
- [x] **大顶部栏 (LargeTopAppBar)**: 首页使用响应式大顶部栏，支持随滚动收缩。
- [x] **表现力排版 (Expressive Typography)**: 顶部栏标题在展开时使用 `headlineLarge` 和 `ExtraBold` 字重，增强视觉张力。
- [x] **波浪进度条 (Wavy Progress Indicator)**: 替换所有传统的圆形/线性进度条为 M3 Expressive 波浪风格。
- [x] **触感反馈 (Haptic Feedback)**: 在完成任务、删除任务和切换标签时集成物理震动反馈，提升操作确定感。
- [x] **卡片形状统一**: 统一所有卡片为 M3 风格的大圆角 (`24.dp`)。
- [x] **AGP 9.0 兼容性**: 清理了所有过时的 Gradle 标志并适配了新的 compileSdk 语法。

## 待优化的细节 (后续计划)

- [ ] **分段按钮 (SplitButton)**: 在任务详情页接入。
- [ ] **微交互动画**: 进一步细化列表项进入和删除的动画效果。

---
*注：本项目将遵循「Build 成功即 Commit」原则进行迭代。*
