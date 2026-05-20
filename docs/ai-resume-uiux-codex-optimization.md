# AI 简历优化与岗位匹配系统前端 UI/UX 优化任务说明

> 适用对象：Codex / AI 编码助手 / 前端重构执行者  
> 项目类型：Java + Vue 的 AI 简历优化与岗位匹配系统  
> 前端技术栈：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus + Axios + SCSS + ECharts  
> 目标风格：Linear 式现代 SaaS 工作台 + Claude 桌面端式低干扰 AI 内容体验 + 蓝白色专业工具感  
> 执行原则：按阶段小步重构，优先优化信息架构、布局层级和视觉统一，不优先堆复杂动画或炫酷图表。

---

## 0. 设计目标概述

当前系统已经具备核心功能闭环：

```text
注册 / 登录
-> 工作台
-> 上传简历
-> 简历解析
-> 简历诊断
-> 新增目标岗位
-> 目标岗位解析
-> 匹配分析
-> 岗位优化建议
-> 局部改写
-> 岗位优化报告
-> AI 历史回看
```

本次前端优化的目标不是新增复杂业务能力，而是让项目从“普通课程设计后台”升级为“可部署、可演示、可放进简历的现代 AI SaaS 产品”。

最终效果应满足：

1. 页面整体具有现代、简洁、高级、专业、可信赖的视觉感受。
2. 布局参考 Linear 的常驻展开侧边栏、清晰导航、低噪音工作流。
3. AI 内容展示参考 Claude 桌面端的低干扰、内容优先、阅读舒适体验。
4. 颜色采用蓝白色方向：冷白浅蓝背景、纯白卡片、低饱和专业蓝主色。
5. 登录后是 SaaS 工作台，不是传统后台管理系统。
6. 未登录时提供一个可公开访问的 Landing Page，便于服务器部署后展示完整产品感。
7. Dashboard 首页同时兼顾“流程引导”和“数据概览”，但第一屏优先告诉用户当前状态和下一步动作。
8. AI 输出结果必须结构化展示，不能直接展示 JSON、堆叠大段文本或杂乱卡片。
9. 所有页面围绕主流程组织，不把 AI 功能拆成多个互不相关的入口。
10. 保留现有后端接口和业务边界，前端先做布局、组件、文案和交互优化。

---

## 1. 参考风格说明

### 1.1 Linear-like SaaS Layout

参考点：

- 常驻展开侧边栏。
- 导航层级少，但任务边界清楚。
- 页面低噪音，强调当前工作流。
- 状态标签克制，视觉层级清晰。
- 操作按钮不堆叠，主操作明确。

本项目借鉴方式：

```text
左侧导航：
工作台
我的简历
目标岗位
匹配与优化
AI 历史
岗位库（辅助入口）

主内容区：
页面标题
页面说明
一个主操作按钮
核心内容卡片
次级信息区域
```

不要照搬：

- 不要做得过于开发者工具化。
- 不要信息密度太高。
- 不要过度暗色或极简到难以理解。

### 1.2 Claude-like AI Reading Experience

参考点：

- AI 输出聚焦内容。
- 阅读空间舒适。
- 少干扰、少装饰。
- 结果不是炫酷，而是清晰、有逻辑、有行动指引。

本项目借鉴方式：

AI 结果统一按以下结构展示：

```text
结论
依据
问题
建议
注意事项
下一步
```

不要照搬：

- 不要把整个系统做成聊天窗口。
- 不要让简历、岗位、历史等结构化业务入口消失。
- 不要将所有 AI 功能都变成对话框。

### 1.3 Vercel / Modern Dashboard Reference

参考点：

- 项目状态集中展示。
- 卡片干净，边框轻。
- 数据概览克制。
- 适合作为 SaaS 产品部署后的后台观感参考。

本项目借鉴方式：

- Dashboard 首页只展示关键状态，不放过多图表。
- 数据图表放在次级区域，服务决策，不作为视觉噱头。
- 图表只用于趋势、分布、对比，不用于展示文本型 AI 内容。

---

## 2. 产品定位与页面描述

### 2.1 最终产品定位

推荐描述：

> 本系统是一个面向求职者的 AI 简历优化与岗位匹配 SaaS 工具，支持从简历上传、解析、AI 诊断、目标岗位解析、岗位匹配、优化建议、局部改写到 AI 历史回看的完整闭环，帮助用户基于目标岗位差距进行结构化简历优化。

### 2.2 最终视觉描述

推荐描述：

> 前端采用蓝白色现代 SaaS 工作台设计方向，布局参考 Linear 的常驻侧边栏与低噪音任务流，AI 内容展示参考 Claude 桌面端的专注阅读体验。页面以冷白浅蓝背景、纯白卡片、低饱和专业蓝、细边框和轻阴影为基础，突出“简历资产、目标岗位、匹配分析、优化建议、AI 历史”五个核心任务。设计目标不是制造炫酷大屏，而是构建一个专业、可信、清晰、可长期使用的 AI 求职辅助平台。

---

## 3. 总体信息架构

### 3.1 登录前

```text
/               Landing Page 产品介绍页
/login          登录页
/register       注册页
```

说明：

- `/` 建议作为公开产品介绍页。
- 登录成功后进入 `/app` 或现有工作台路由。
- 如果短期不想调整路由，也可以保留 `/` 为工作台，但应新增 `/landing` 或 `/welcome` 作为公开页。长期建议 `/` 做 Landing Page，`/app` 做登录后工作台。

### 3.2 登录后

一级导航建议：

```text
工作台
我的简历
目标岗位
匹配与优化
AI 历史
```

辅助入口：

```text
岗位库
```

不作为一级导航：

```text
AI 改写
岗位管理
数据大屏
系统管理
```

原因：

- `AI 改写` 是匹配与优化页内动作，不是独立主流程。
- `岗位管理` 属于管理员概念，普通用户不应看到。
- `数据大屏` 当前不是核心需求，容易造成廉价感。
- `岗位库` 是系统预置岗位参考，不代表用户真实投递目标，因此作为辅助入口。

---

## 4. 路由规划建议

### 4.1 推荐路由结构

```text
/                               Landing Page
/login                          登录
/register                       注册

/app                            登录后工作台
/app/resumes                    我的简历
/app/jobs                       岗位库
/app/jobs/:id                   岗位详情
/app/job-descriptions           目标岗位
/app/job-descriptions/new       新增目标岗位
/app/job-descriptions/:id       目标岗位详情
/app/matches                    匹配与优化
/app/history                    AI 历史
```

### 4.2 与现有路由兼容

如果当前后端或前端已经依赖以下路由，短期可以保持不动：

```text
/                               工作台
/resumes                        我的简历
/jobs                           岗位库
/jobs/:id                       岗位详情
/job-descriptions               目标岗位
/job-descriptions/new           新增目标岗位
/job-descriptions/:id           目标岗位详情
/ai-job-matches                 匹配与优化
/history                        AI 历史
/login                          登录
/register                       注册
```

短期优化策略：

1. 不强制改接口路径。
2. 不强制改后端。
3. 不强制一次性迁移所有路由。
4. 可以先新增 `AppLayout` 并包裹现有登录后页面。
5. 页面标题和导航显示名称按新语义调整。
6. 后续再逐步迁移 `/app/*` 路由。

### 4.3 路由懒加载要求

Vue Router 页面组件应使用动态导入，避免主包过大：

```ts
{
  path: '/app',
  component: () => import('@/layouts/AppLayout.vue'),
  children: [
    {
      path: '',
      name: 'Dashboard',
      component: () => import('@/views/dashboard/DashboardView.vue')
    }
  ]
}
```

---

## 5. 目录结构要求

必须遵守当前项目结构，不允许随意创建新顶层目录。

### 5.1 前端目录

```text
web/src/
├── api/
├── assets/
├── components/
│   ├── common/
│   ├── layout/
│   ├── dashboard/
│   ├── resume/
│   ├── job/
│   ├── match/
│   └── ai/
├── layouts/
├── router/
├── stores/
├── styles/
├── types/
├── utils/
└── views/
    ├── landing/
    ├── auth/
    ├── dashboard/
    ├── resume/
    ├── job/
    ├── match/
    └── history/
```

如果当前项目使用 `layout/` 而不是 `layouts/`，优先沿用现有目录命名，避免重复创建两个布局目录。

### 5.2 不允许

```text
web/src/pages/        # 除非项目已有该目录
web/src/modules/      # 不新增
web/src/design/       # 不新增
web/src/business/     # 不新增顶层业务目录
src/                  # 不在仓库根目录新增
```

### 5.3 新增文件归属

| 文件类型 | 推荐位置 |
|---|---|
| 布局组件 | `web/src/layouts/` 或现有 `web/src/layout/` |
| 通用组件 | `web/src/components/common/` |
| 导航组件 | `web/src/components/layout/` |
| AI 结果组件 | `web/src/components/ai/` |
| 简历业务组件 | `web/src/components/resume/` |
| 岗位业务组件 | `web/src/components/job/` |
| 匹配业务组件 | `web/src/components/match/` |
| 页面级组件 | `web/src/views/` |
| 全局样式 | `web/src/styles/` |
| 类型定义 | `web/src/types/` |
| Pinia 状态 | `web/src/stores/` |

---

## 6. 视觉系统设计

### 6.1 设计关键词

```text
现代
简洁
蓝白色
低噪音
专业可信
高留白
内容优先
流程清晰
SaaS 工作台
AI 报告体验
```

### 6.2 禁止风格

```text
廉价渐变
强发光
大屏驾驶舱
过度玻璃拟态
满屏彩色图表
传统后台表格堆叠
按钮堆满顶部
所有卡片等权重
AI 输出 JSON dump
```

### 6.3 色彩 Token

建议在 `web/src/styles/variables.scss` 或 `web/src/styles/tokens.scss` 中定义：

```scss
:root {
  --app-bg: #f6f8fb;
  --app-bg-soft: #f8fafc;
  --app-surface: #ffffff;
  --app-surface-soft: #f9fbfd;

  --app-primary: #2563eb;
  --app-primary-hover: #1d4ed8;
  --app-primary-soft: #eff6ff;

  --app-navy: #0f172a;
  --app-text: #1e293b;
  --app-text-secondary: #64748b;
  --app-text-muted: #94a3b8;

  --app-border: #e2e8f0;
  --app-border-soft: #edf2f7;

  --app-success: #16a34a;
  --app-success-soft: #ecfdf3;

  --app-warning: #d97706;
  --app-warning-soft: #fff7ed;

  --app-danger: #dc2626;
  --app-danger-soft: #fef2f2;

  --app-radius-xl: 22px;
  --app-radius-lg: 18px;
  --app-radius-md: 12px;
  --app-radius-sm: 8px;

  --app-shadow-card: 0 12px 32px rgba(15, 23, 42, 0.06);
  --app-shadow-soft: 0 8px 24px rgba(15, 23, 42, 0.04);

  --app-sidebar-width: 248px;
  --app-content-max-width: 1200px;
  --app-content-padding: 32px;
}
```

### 6.4 Element Plus 主题覆盖

在 `web/src/styles/element.scss` 中覆盖：

```scss
:root {
  --el-color-primary: var(--app-primary);
  --el-color-primary-light-3: #60a5fa;
  --el-color-primary-light-5: #93c5fd;
  --el-color-primary-light-7: #bfdbfe;
  --el-color-primary-light-9: var(--app-primary-soft);
  --el-color-primary-dark-2: var(--app-primary-hover);

  --el-border-radius-base: 10px;
  --el-border-color: var(--app-border);
  --el-border-color-light: var(--app-border-soft);
  --el-fill-color-light: var(--app-bg-soft);

  --el-text-color-primary: var(--app-text);
  --el-text-color-regular: var(--app-text-secondary);
  --el-text-color-secondary: var(--app-text-muted);
}
```

要求：

1. 不要保留 Element Plus 默认强蓝后台感。
2. 不要在每个页面单独覆盖大量深层样式。
3. 优先使用 CSS Variables 和统一 class。
4. 必要时封装自己的业务组件，减少对 Element Plus 默认视觉的依赖。

### 6.5 字体层级

```scss
.app-page-title {
  font-size: 28px;
  line-height: 36px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.app-section-title {
  font-size: 18px;
  line-height: 26px;
  font-weight: 620;
}

.app-card-title {
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
}

.app-body {
  font-size: 14px;
  line-height: 22px;
  color: var(--app-text-secondary);
}

.app-caption {
  font-size: 12px;
  line-height: 18px;
  color: var(--app-text-muted);
}

.app-metric-number {
  font-size: 36px;
  line-height: 44px;
  font-weight: 680;
  letter-spacing: -0.03em;
}
```

### 6.6 卡片样式

```scss
.app-card {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-card);
  padding: 24px;
}

.app-card--flat {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: none;
  padding: 24px;
}
```

卡片使用规则：

1. 主卡片可以使用轻阴影。
2. 次级卡片尽量使用边框，不要所有卡片都有大阴影。
3. 一个页面最多 1 到 2 个视觉主卡片。
4. 卡片不要全部等宽等高堆叠。
5. 列表型内容优先使用行列表或紧凑卡片。

### 6.7 按钮样式

主按钮：

```text
低饱和专业蓝背景
白色文字
圆角 10px 或 999px
只用于页面主要动作
```

次按钮：

```text
白底
浅灰蓝边框
深蓝灰文字
用于辅助动作
```

危险按钮：

```text
仅删除、退出等动作使用
不要大面积红色
```

页面按钮规则：

1. 每个页面顶部只保留一个主按钮。
2. 顶部次按钮最多 2 个。
3. 低频操作放入更多菜单。
4. 不要顶部按钮排成一长排。

---

## 7. 公共组件清单

### 7.1 必做组件

| 组件 | 推荐路径 | 职责 |
|---|---|---|
| `AppLayout.vue` | `layouts/` | 登录后整体布局 |
| `AppSidebar.vue` | `components/layout/` | 常驻展开侧边栏 |
| `AppTopbar.vue` | `components/layout/` | 顶部页面辅助信息，可选 |
| `PageHeader.vue` | `components/common/` | 页面标题、说明、主操作 |
| `BaseCard.vue` | `components/common/` | 统一卡片 |
| `StatusTag.vue` | `components/common/` | 状态标签 |
| `EmptyState.vue` | `components/common/` | 空状态 |
| `ErrorState.vue` | `components/common/` | 错误状态 |
| `SkeletonBlock.vue` | `components/common/` | 骨架屏 |
| `MetricCard.vue` | `components/dashboard/` | 首页指标卡 |
| `ProcessStepper.vue` | `components/common/` | 主流程状态 |
| `AiReportBlock.vue` | `components/ai/` | AI 报告结构块 |
| `SuggestionCard.vue` | `components/ai/` | 优化建议卡片 |
| `ScoreSummary.vue` | `components/match/` | 匹配分数摘要 |
| `SkillMatchPanel.vue` | `components/match/` | 强匹配、弱匹配、缺失技能 |

### 7.2 组件设计原则

1. 页面只负责组织布局和调用接口。
2. 复杂展示逻辑下沉到业务组件。
3. 通用组件不写具体业务接口请求。
4. 业务组件可以接收业务数据，但不要直接决定全局路由。
5. 所有组件必须使用 TypeScript 类型定义。
6. 组件样式优先使用 scoped SCSS 或统一 class。
7. 避免在页面中复制粘贴多个相似卡片结构。

---

## 8. 页面重构方案

## 8.1 Landing Page

### 目标

部署到服务器后，用户打开网站首先看到一个完整产品介绍页，而不是直接进入登录页或混乱工作台。

### 页面结构

```text
顶部导航：
Logo / 产品名
功能亮点
技术栈
项目说明
登录
立即体验

首屏 Hero：
标题：AI 简历优化与岗位匹配系统
副标题：让简历优化从“凭感觉修改”变成“基于岗位差距的结构化决策”
主按钮：立即体验
次按钮：查看演示流程

能力展示：
简历解析
简历诊断
目标岗位解析
岗位匹配分析
优化建议
局部改写
AI 历史回看

流程展示：
上传简历 -> 添加目标岗位 -> 生成匹配报告 -> 获得优化建议

技术亮点：
Vue 3
Spring Boot
PostgreSQL
MinIO
Spring AI
JWT
Docker 部署

底部：
作者 / 项目说明 / GitHub 链接占位
```

### 验收标准

- 未登录访问 `/` 能看到产品介绍。
- 登录按钮进入 `/login`。
- 立即体验按钮未登录时进入 `/login`。
- 页面风格与登录后 AppLayout 一致。
- 不出现廉价渐变和大屏风。
- 首屏 1366px 宽度下视觉完整，不拥挤。

---

## 8.2 AppLayout

### 目标

将登录后页面统一为现代 SaaS 工作台布局。

### 布局结构

```text
左侧：常驻展开 Sidebar，宽度 248px
右侧：主应用区域
  顶部：页面标题区域或轻量 Topbar
  中间：内容区，最大宽度 1200px
```

### Sidebar 内容

```text
顶部：
产品 Logo
AI 简历优化

主导航：
工作台
我的简历
目标岗位
匹配与优化
AI 历史

辅助：
岗位库

底部：
当前用户
退出登录
```

### 样式要求

```scss
.app-layout {
  min-height: 100vh;
  display: flex;
  background: var(--app-bg);
}

.app-sidebar {
  width: var(--app-sidebar-width);
  flex-shrink: 0;
  background: #ffffff;
  border-right: 1px solid var(--app-border);
}

.app-main {
  flex: 1;
  min-width: 0;
}

.app-content {
  max-width: var(--app-content-max-width);
  margin: 0 auto;
  padding: var(--app-content-padding);
}
```

### 验收标准

- 登录后所有核心页面都有统一侧边栏。
- 侧边栏当前路由高亮清晰。
- 主内容区不会过窄，也不会无限拉伸。
- 页面左右空白变成合理版心，而不是大片无效空白。
- 退出登录入口位于侧边栏底部。
- 普通用户不显示“岗位管理”。

---

## 8.3 工作台 Dashboard

### 目标

从“功能集合页”重构为“下一步决策页”。

### 信息比例

```text
60% 流程引导
40% 数据概览
```

### 第一屏结构

```text
PageHeader：
标题：工作台
说明：继续完成你的简历优化流程
主按钮：根据当前状态动态显示

主卡片：
当前建议
例如：
你已有解析成功的简历和目标岗位，可以开始匹配分析。

右侧摘要：
当前简历
当前目标岗位
最近 AI 结果
```

### 状态驱动主操作

| 当前状态 | 主按钮 | 次按钮 |
|---|---|---|
| 无简历 | 上传简历 | 新增目标岗位 |
| 有简历未解析 | 开始解析简历 | 查看我的简历 |
| 简历已解析无目标岗位 | 新增目标岗位 | 查看简历诊断 |
| 有目标岗位未解析 | 解析目标岗位 | 查看目标岗位 |
| 简历和目标岗位都已解析 | 开始匹配分析 | 查看 AI 历史 |
| 已有匹配分析 | 查看优化报告 | 生成优化建议 |

### 第二屏结构

```text
流程进度：
上传简历
简历解析
简历诊断
目标岗位
匹配分析
岗位优化建议
局部改写

数据概览：
简历数
目标岗位数
匹配报告数
AI 建议数

最近记录：
最近简历诊断
最近匹配分析
最近局部改写
```

### 不允许

- 不要一屏放 8 个等权重卡片。
- 不要把所有按钮放在页面顶部。
- 不要首页一上来展示太多历史列表。
- 不要用大量图表抢主流程注意力。

### 验收标准

- 用户进入工作台 3 秒内能看懂下一步做什么。
- 首页第一屏最多一个主按钮。
- 页面第一屏不再显得混乱。
- 数据概览和流程引导平衡存在。
- 最近 AI 结果只做摘要，不铺满内容。

---

## 8.4 我的简历页

### 目标

让“简历上传、解析、诊断”成为一个清晰的简历资产管理页面。

### 推荐布局

```text
PageHeader：
标题：我的简历
说明：上传、解析并诊断你的简历资产
主按钮：上传新简历

主体：
左侧 35%：简历列表
右侧 65%：选中简历详情
```

### 右侧详情 Tabs

```text
概览
解析结果
简历诊断
调试信息
```

### 简历列表卡片字段

```text
文件名
上传时间
解析状态
诊断状态
文件类型
操作：查看 / 解析 / 诊断 / 删除
```

### 解析结果展示

结构：

```text
解析状态摘要
质量提示
基础信息
技能关键词
教育经历
项目经历
实习经历
校园经历
获奖证书
其他内容
调试信息（默认折叠）
```

### 简历诊断展示

结构：

```text
整体评分
优势
主要问题
优化建议
注意事项
下一步：新增目标岗位 / 开始匹配
```

### 验收标准

- 页面不再把所有简历、解析结果、诊断结果同时铺满。
- 用户先选简历，再看详情。
- 调试信息默认折叠。
- 解析 warning 不等于失败，要用 warning tag 表示。
- 简历诊断只分析简历本身，不出现岗位匹配内容。

---

## 8.5 目标岗位页

### 目标

突出“目标岗位是用户自己粘贴的真实 JD”，不要和系统岗位库混淆。

### 页面结构

```text
PageHeader：
标题：目标岗位
说明：保存你正在投递或准备投递的岗位 JD
主按钮：新增目标岗位

主体：
目标岗位列表
```

### 岗位卡片字段

```text
岗位名称
公司名称（如果有）
解析状态
核心技能标签
创建时间
最近匹配状态
操作：查看详情 / 解析 / 开始匹配 / 删除
```

### 空状态

```text
你还没有添加目标岗位
粘贴一份真实 JD 后，系统可以分析岗位要求并与你的简历进行匹配。

主按钮：新增目标岗位
次按钮：查看岗位库
```

### 验收标准

- 页面中不出现“岗位管理”。
- 目标岗位和岗位库语义清晰区分。
- 新增目标岗位入口清晰。
- 已解析和未解析状态明确。
- 可从目标岗位快速进入匹配与优化页。

---

## 8.6 新增目标岗位页

### 目标

提供一个简单、专注的 JD 输入页面。

### 页面结构

```text
PageHeader：
标题：新增目标岗位
说明：粘贴你准备投递的岗位 JD，系统会抽取技能、职责和经验要求

表单：
岗位标题
公司名称（可选）
岗位原文 JD
备注（可选）

右侧说明卡片：
如何粘贴 JD
不要填写敏感信息
保存后可进行目标岗位解析
```

### 验收标准

- 表单宽度适中，不铺满全屏。
- JD 文本框足够大。
- 保存成功后进入目标岗位详情页。
- 文案强调“用户自己的目标岗位”，不是系统岗位管理。

---

## 8.7 目标岗位详情页

### 目标

清晰展示 JD 原文、解析状态和结构化结果，并提供进入匹配分析的主操作。

### 页面结构

```text
PageHeader：
标题：目标岗位详情
主按钮：开始匹配分析 / 解析目标岗位

主体两栏：
左侧：岗位原文
右侧：解析结果
```

### 解析结果

```text
岗位标题
必备技能
加分技能
职责要求
经验要求
关键词
质量提示
```

### 验收标准

- 未解析时主按钮为“解析目标岗位”。
- 已解析时主按钮为“开始匹配分析”。
- 解析结果不判断简历是否匹配。
- 目标岗位详情页不展示岗位优化建议。

---

## 8.8 匹配与优化页

### 目标

这是核心演示页面。必须从“大杂烩页面”改为流程式 AI 工作流页面。

### 页面结构

```text
PageHeader：
标题：匹配与优化
说明：选择一份简历和一个目标岗位，生成匹配分析、优化建议和局部改写
主按钮：根据当前步骤变化

Stepper：
1. 选择简历和目标岗位
2. 匹配分析
3. 岗位优化建议
4. 局部改写
5. 优化报告
```

### Step 1：选择简历和目标岗位

```text
左侧：简历选择卡片
右侧：目标岗位选择卡片
底部：开始匹配分析
```

要求：

- 未解析简历不能进入匹配，提示先解析。
- 未解析目标岗位不能进入匹配，提示先解析。
- 状态提示要明确。

### Step 2：匹配分析

布局：

```text
左侧主卡片：
匹配分数
匹配结论
下一步建议

右侧卡片：
强匹配项
弱匹配项
缺失技能
风险提示
匹配依据
```

展示规则：

- 匹配分数要有解释，不只放数字。
- 强匹配项使用浅绿 tag。
- 弱匹配项使用浅黄 tag。
- 缺失技能使用浅红 tag。
- 风险提示强调不要虚构能力。

### Step 3：岗位优化建议

结构：

```text
建议总览
高优先级建议
中优先级建议
低优先级建议
每条建议：
问题
依据
建议
注意事项
可触发局部改写
```

### Step 4：局部改写

结构：

```text
原文片段输入 / 选择
目标章节选择
生成改写
结果展示：
原文
改写后
改写理由
注意事项
采纳 / 拒绝
```

要求：

- 明确提示：AI 只优化表达，不自动写回原简历。
- 改写不能补造不存在的经历。
- 采纳/拒绝只记录状态。

### Step 5：优化报告

结构：

```text
报告概览
匹配结论
核心差距
岗位优化建议
局部改写建议
下一步清单
warning
模型信息 / prompt 版本 / 生成时间
```

要求：

- 报告聚合已有结果，不重新调用 AI。
- 不展示 JSON dump。
- 报告可以用于面试演示闭环收口。

### 验收标准

- 用户能按 Stepper 理解完整流程。
- 页面不再同时铺满所有功能区。
- 每一步只显示当前阶段最重要内容。
- AI 输出结构清晰，有结论、有依据、有建议、有注意事项。
- 匹配与优化页成为最核心的项目展示页面。

---

## 8.9 AI 历史页

### 目标

AI 历史只做回看和跳转，不触发新的 AI 生成。

### 页面结构

```text
PageHeader：
标题：AI 历史
说明：回看已保存的简历诊断、目标岗位解析、匹配分析、优化建议和局部改写结果

主体三栏：
左侧：筛选器
中间：历史列表
右侧：详情预览 / 抽屉
```

### 筛选条件

```text
结果类型
简历
目标岗位
生成状态
时间范围
```

### 历史列表字段

```text
结果类型
标题
摘要
关联简历
关联目标岗位
状态
更新时间
```

### 详情展示

```text
标题
类型
状态
摘要
结构化详情
模型名称
Prompt 版本
创建时间
更新时间
回到业务页面按钮
```

### 验收标准

- AI 历史页不出现“生成”类按钮。
- 支持按结果类型筛选。
- 详情展示可读，不是 JSON。
- 可跳回我的简历、目标岗位或匹配与优化页。

---

## 8.10 岗位库页

### 目标

岗位库只是辅助参考入口，不是主流程核心。

### 页面结构

```text
PageHeader：
标题：岗位库
说明：浏览系统预置岗位，了解常见岗位要求
主按钮：新增目标岗位（引导用户回到真实 JD 主流程）

主体：
系统预置岗位列表
```

### 验收标准

- 岗位库不显示“新增系统岗位”“编辑岗位”“删除岗位”等管理操作。
- 岗位库可以作为参考，但不要抢目标岗位的主流程地位。
- 岗位详情可以引导用户创建自己的目标岗位。

---

## 9. 状态设计规范

### 9.1 空状态

空状态必须包含：

```text
当前为什么为空
用户可以做什么
主行动按钮
可选说明
```

示例：

```text
你还没有上传简历
上传一份 PDF、DOC 或 DOCX 简历后，系统可以帮你解析内容并生成 AI 诊断。

[上传第一份简历]
```

禁止：

```text
暂无数据
```

除非只是表格内部轻量提示。

### 9.2 加载状态

普通列表加载：

```text
使用 SkeletonBlock
```

AI 生成加载：

```text
显示当前步骤说明
例如：AI 正在分析简历与目标岗位的匹配差距...
```

上传/解析加载：

```text
显示任务状态
允许用户知道系统没有卡死
```

### 9.3 错误状态

错误状态必须包含：

```text
错误标题
可能原因
下一步操作
重试按钮
```

示例：

```text
解析失败
可能原因：文件内容不可复制、格式不支持或文件过大。
你可以重新上传文件，或尝试转换为 PDF 后再次解析。

[重新上传] [查看支持格式]
```

### 9.4 AI 风险提示

所有涉及改写和优化建议的地方必须提示：

```text
AI 只提供表达优化建议，不应补造不存在的经历、数据或能力。
采纳前请确认内容真实可信。
```

---

## 10. ECharts 使用规范

### 10.1 适合使用图表的地方

```text
Dashboard：最近匹配分数趋势
Dashboard：AI 结果类型分布
Dashboard：缺失技能 Top 5
Dashboard：简历完整度变化
匹配页：技能匹配分布
历史页：不同 AI 类型数量统计
```

### 10.2 不适合使用图表的地方

```text
AI 建议正文
局部改写结果
岗位职责文本
简历解析正文
错误原因说明
```

### 10.3 图表风格要求

1. 蓝白风格，低饱和。
2. 不使用大屏驾驶舱配色。
3. 不使用 3D 图。
4. 不使用过多环形图。
5. 图表必须有明确标题和解释。
6. 图表用于辅助理解，不抢主要内容。

---

## 11. Pinia 状态建议

### 11.1 Store 划分

```text
stores/auth.ts
stores/app.ts
stores/resume.ts
stores/jobDescription.ts
stores/match.ts
stores/aiHistory.ts
```

### 11.2 auth store

职责：

```text
token
currentUser
login
logout
fetchCurrentUser
```

### 11.3 app store

职责：

```text
sidebar 状态
当前页面标题
全局 loading
用户当前主流程状态摘要
```

### 11.4 resume store

职责：

```text
简历列表
当前选中简历
解析状态
诊断状态
```

### 11.5 jobDescription store

职责：

```text
目标岗位列表
当前选中目标岗位
解析状态
```

### 11.6 match store

职责：

```text
当前简历 ID
当前目标岗位 ID
匹配结果
优化建议
局部改写结果
优化报告
当前 step
```

### 11.7 aiHistory store

职责：

```text
历史筛选条件
历史列表
当前历史详情
```

---

## 12. 响应式设计要求

### 12.1 桌面优先

本项目主要用于 PC 端演示和部署展示，优先保证：

```text
1366px
1440px
1920px
```

### 12.2 断点建议

```scss
$breakpoint-sm: 640px;
$breakpoint-md: 768px;
$breakpoint-lg: 1024px;
$breakpoint-xl: 1280px;
```

### 12.3 小屏处理

在小屏下：

1. 侧边栏可以收起为抽屉。
2. 双栏布局改为单栏。
3. 卡片间距适当减少。
4. 表格优先改为卡片列表。
5. 不要求复杂移动端体验，但不能完全不可用。

---

## 13. 执行阶段与任务清单

## Phase 0：只读审查

### 目标

先理解现有代码，不修改文件。

### 操作

1. 查看 `web/src/router/index.ts`。
2. 查看当前布局文件。
3. 查看 `HomeView.vue`、`ResumeView.vue`、`JobDescriptionListView.vue`、`AiJobMatchView.vue`、`HistoryView.vue`。
4. 查看 `web/src/api/` 下接口封装。
5. 查看 `web/src/stores/` 下 Pinia 状态。
6. 查看 `web/src/styles/` 是否已有全局样式。

### 输出

在回复中说明：

```text
当前路由结构
当前页面结构
当前组件复用情况
当前样式入口
需要改造的文件清单
不需要改造的文件清单
```

### 禁止

- 不修改任何文件。
- 不新增目录。
- 不删除旧代码。

---

## Phase 1：建立视觉 Token 与 Element Plus 主题

### 目标

先统一视觉基础，不急着改页面。

### 新增或修改

```text
web/src/styles/tokens.scss
web/src/styles/element.scss
web/src/styles/global.scss
web/src/main.ts
```

### 要求

1. 定义蓝白色 Design Tokens。
2. 覆盖 Element Plus 主题变量。
3. 设置全局背景、字体、滚动条、链接、按钮基础样式。
4. 不破坏现有页面功能。

### 验收

- 页面默认背景变为冷白浅蓝。
- Element Plus 主按钮变成专业蓝。
- 全局文字颜色统一。
- 卡片和边框不再有默认后台廉价感。

---

## Phase 2：重构 AppLayout 和 Sidebar

### 目标

建立登录后统一 SaaS 工作台布局。

### 新增或修改

```text
web/src/layouts/AppLayout.vue
web/src/components/layout/AppSidebar.vue
web/src/components/layout/AppTopbar.vue
web/src/router/index.ts
```

### 要求

1. 左侧常驻展开侧边栏。
2. 导航项：工作台、我的简历、目标岗位、匹配与优化、AI 历史。
3. 岗位库作为辅助入口。
4. 当前路由高亮。
5. 底部显示用户和退出。
6. 主内容区最大宽度 1200px。
7. 不显示岗位管理。

### 验收

- 登录后页面统一进入 AppLayout。
- 页面左右空白合理。
- 导航清晰，主流程明确。
- 原有页面还能正常访问。

---

## Phase 3：新增 Landing Page

### 目标

让部署后的项目具备公开产品入口。

### 新增或修改

```text
web/src/views/landing/LandingView.vue
web/src/router/index.ts
```

### 要求

1. `/` 显示 Landing Page。
2. 登录后工作台迁移到 `/app`，或短期保留现有工作台但新增 `/landing`。
3. Landing Page 采用同一套蓝白视觉。
4. 展示产品定位、核心能力、流程、技术栈和登录入口。

### 验收

- 未登录用户可以看到完整产品介绍。
- 页面不像课程作业封面。
- 登录入口清晰。
- 不影响原登录注册流程。

---

## Phase 4：抽取公共组件

### 目标

减少页面重复代码，统一视觉。

### 新增

```text
web/src/components/common/PageHeader.vue
web/src/components/common/BaseCard.vue
web/src/components/common/StatusTag.vue
web/src/components/common/EmptyState.vue
web/src/components/common/ErrorState.vue
web/src/components/common/SkeletonBlock.vue
web/src/components/common/ProcessStepper.vue
```

### 要求

1. 每个组件有清晰 props 类型。
2. 不直接写业务接口请求。
3. 样式统一使用 token。
4. 先在 Dashboard 使用，再逐步推广到其他页面。

### 验收

- Dashboard 至少使用 PageHeader、BaseCard、StatusTag、ProcessStepper。
- 组件样式统一。
- 页面代码更清晰。

---

## Phase 5：重构工作台 Dashboard

### 目标

解决当前页面混乱、左右空、卡片堆叠的问题。

### 修改

```text
web/src/views/HomeView.vue
```

或迁移为：

```text
web/src/views/dashboard/DashboardView.vue
```

### 要求

1. 第一屏只突出当前状态、下一步动作、最近 AI 结果。
2. 流程引导和数据概览比例约为 60/40。
3. 根据用户状态动态显示主按钮。
4. 最近记录只做摘要。
5. 快捷入口不超过 6 个。
6. 图表放到次级区域。

### 验收

- 67% 缩放下仍能看清主信息。
- 1366px 宽度下第一屏不混乱。
- 用户能知道下一步做什么。
- 顶部按钮不再一排堆叠。

---

## Phase 6：重构我的简历页

### 目标

让简历页从混杂页面变成资产管理页。

### 修改

```text
web/src/views/resume/ResumeView.vue
web/src/components/resume/*
```

### 要求

1. 左侧简历列表，右侧详情。
2. 详情使用 Tabs：概览、解析结果、简历诊断、调试信息。
3. 调试信息默认折叠。
4. 空状态引导上传。
5. AI 诊断结构化展示。

### 验收

- 简历页不再一次性铺满所有内容。
- 解析结果层级清晰。
- AI 诊断可读。
- 简历操作按钮主次明确。

---

## Phase 7：重构目标岗位相关页面

### 目标

明确目标岗位与岗位库边界。

### 修改

```text
web/src/views/job/JobDescriptionListView.vue
web/src/views/job/JobDescriptionCreateView.vue
web/src/views/job/JobDescriptionDetailView.vue
web/src/views/job/JobListView.vue
web/src/views/job/JobDetailView.vue
```

### 要求

1. 目标岗位页突出用户自己的 JD。
2. 新增目标岗位页专注表单。
3. 目标岗位详情页两栏展示原文和解析结果。
4. 岗位库只读参考。
5. 不出现岗位管理语义。

### 验收

- 用户不会混淆岗位库和目标岗位。
- 从目标岗位详情可自然进入匹配与优化。
- 岗位库不抢主流程。

---

## Phase 8：重构匹配与优化页

### 目标

打造项目最核心的演示页面。

### 修改

```text
web/src/views/job/AiJobMatchView.vue
web/src/components/match/*
web/src/components/ai/*
```

### 要求

1. 使用 Stepper 组织五步流程。
2. 每一步只展示当前关键内容。
3. 匹配分析展示分数、结论、强匹配、弱匹配、缺失技能、风险提示、依据。
4. 岗位优化建议按优先级展示。
5. 局部改写展示原文、改写后、理由、注意事项、采纳状态。
6. 优化报告结构化展示。
7. 不展示 JSON dump。

### 验收

- 页面从“大杂烩”变成流程式 AI 工作台。
- AI 输出像报告。
- 面试演示时可以完整讲清闭环。
- 不新增后端接口，不改变业务边界。

---

## Phase 9：重构 AI 历史页

### 目标

把历史记录变成 AI 结果回看中心。

### 修改

```text
web/src/views/history/HistoryView.vue
web/src/components/ai/*
```

### 要求

1. 左侧筛选，中间列表，右侧详情或抽屉。
2. 支持结果类型筛选。
3. 不触发新的 AI 生成。
4. 详情可跳转回业务页面。
5. 结构化展示，不展示原始 JSON。

### 验收

- AI 历史页职责清晰。
- 可快速找到过去的简历诊断、匹配分析、优化建议、局部改写。
- 页面不像普通数据库记录表。

---

## Phase 10：统一空状态、加载状态、错误状态

### 目标

增强产品完整度和部署展示质感。

### 修改范围

所有核心页面。

### 要求

1. 无数据时使用 EmptyState。
2. 加载页面使用 SkeletonBlock。
3. AI 生成中显示具体步骤说明。
4. 错误状态包含原因和解决动作。
5. 上传、解析、AI 分析、匹配流程都有状态反馈。

### 验收

- 不再出现大量“暂无数据”。
- 用户知道系统正在做什么。
- 出错时知道怎么恢复。
- 整体体验更像真实产品。

---

## Phase 11：响应式与部署展示优化

### 目标

保证部署后在常见屏幕上展示稳定。

### 要求

1. 适配 1366、1440、1920 宽度。
2. 1366 下不横向溢出。
3. 1920 下内容不会无限拉伸。
4. 小屏下侧边栏可收起或变为抽屉。
5. Landing Page 和登录页视觉统一。
6. 去掉开发期临时文案和调试入口暴露。

### 验收

- 可以用于录屏演示。
- 可以部署到服务器让别人访问。
- 第一眼不像课程设计。
- 页面表现稳定。

---

## 14. Codex 执行总提示词

下面内容可以直接复制给 Codex：

```text
你现在负责优化 AI 简历优化与岗位匹配系统的前端 UI/UX。

项目技术栈：
Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus + Axios + SCSS + ECharts。

设计目标：
将当前前端界面重构为“蓝白色现代 AI SaaS 工作台风格”。整体参考 Linear 的常驻展开侧边栏、低噪音工作流和现代 SaaS 工具感；AI 输出体验参考 Claude 桌面端的低干扰、内容优先、阅读舒适感。不要照抄任何产品，只参考布局气质和体验原则。

核心要求：
1. 不做传统后台管理系统，不做大屏驾驶舱。
2. 不使用廉价渐变、强发光、过多图表、玻璃拟态。
3. 登录后使用常驻展开左侧导航。
4. 主导航为：工作台、我的简历、目标岗位、匹配与优化、AI 历史。
5. 岗位库作为辅助入口，不作为主流程核心入口。
6. 普通用户侧不出现“岗位管理”。
7. 主内容区最大宽度约 1200px，避免页面太窄导致左右空白过大，也避免 1920 屏无限拉伸。
8. 工作台首页要平衡流程引导和数据概览，第一屏优先展示当前状态、下一步动作、最近 AI 结果。
9. 匹配与优化页必须改为流程式 Stepper：选择简历和目标岗位 -> 匹配分析 -> 岗位优化建议 -> 局部改写 -> 优化报告。
10. AI 输出必须结构化展示为：结论、依据、问题、建议、注意事项、下一步，不允许 JSON dump。
11. 继续使用 Element Plus，但通过 SCSS 和 CSS Variables 改造默认风格。
12. 优先抽取公共组件：PageHeader、BaseCard、StatusTag、EmptyState、ErrorState、SkeletonBlock、ProcessStepper、AiReportBlock、SuggestionCard、ScoreSummary、SkillMatchPanel。
13. 不修改后端接口路径，不新增不必要的后端能力。
14. 严格遵循项目目录规范，不随意创建新顶层目录。
15. 每个阶段完成后说明修改文件、完成内容、验证方式和未完成风险。

执行顺序：
Phase 0：只读审查，不修改文件。
Phase 1：建立视觉 token 和 Element Plus 主题。
Phase 2：重构 AppLayout 和 Sidebar。
Phase 3：新增 Landing Page。
Phase 4：抽取公共组件。
Phase 5：重构工作台 Dashboard。
Phase 6：重构我的简历页。
Phase 7：重构目标岗位和岗位库页面。
Phase 8：重构匹配与优化页。
Phase 9：重构 AI 历史页。
Phase 10：统一空状态、加载状态、错误状态。
Phase 11：响应式和部署展示优化。

每次只执行一个阶段，不要一次性大改所有页面。
```

---

## 15. 每阶段提交建议

### Commit 格式

```text
feat(ui): add app layout and sidebar
style(ui): add blue-white design tokens
refactor(dashboard): simplify workspace flow
refactor(match): convert match page to stepper workflow
feat(landing): add public landing page
```

### PR 描述模板

```markdown
## 本次目标

说明本次优化的是哪个阶段。

## 修改内容

- 修改文件 1
- 修改文件 2
- 新增组件 1
- 新增样式 1

## 验证方式

- 本地启动前端
- 登录后访问工作台
- 检查核心路由
- 检查 1366px / 1440px 展示
- 检查无数据、加载、错误状态

## 未完成事项

- xxx
```

---

## 16. 最终验收清单

### 16.1 视觉验收

- [ ] 整体是蓝白色现代 SaaS 风格。
- [ ] 没有廉价渐变和强发光。
- [ ] 页面留白充足。
- [ ] 文字层级清楚。
- [ ] 卡片主次明显。
- [ ] 按钮主次明确。
- [ ] Element Plus 默认后台感明显降低。

### 16.2 布局验收

- [ ] 登录后有常驻展开侧边栏。
- [ ] 主内容最大宽度合理。
- [ ] 1366px 下不拥挤。
- [ ] 1920px 下不显得内容飘在中间太窄。
- [ ] 工作台第一屏能看懂下一步。
- [ ] 页面不再大量等权重卡片堆叠。

### 16.3 业务验收

- [ ] 工作台能引导上传简历、目标岗位、匹配分析。
- [ ] 我的简历页能上传、解析、诊断。
- [ ] 目标岗位页能新增、查看、解析 JD。
- [ ] 匹配与优化页能完成匹配、建议、改写、报告。
- [ ] AI 历史只回看，不生成。
- [ ] 岗位库只是辅助参考。
- [ ] 普通用户看不到岗位管理。

### 16.4 AI 输出验收

- [ ] 简历诊断结构化。
- [ ] 匹配分析有分数和解释。
- [ ] 强匹配、弱匹配、缺失技能区分清楚。
- [ ] 岗位优化建议按优先级展示。
- [ ] 局部改写有原文、改写后、理由、注意事项。
- [ ] 优化报告不是 JSON dump。
- [ ] 所有 AI 改写处提示不要虚构经历。

### 16.5 部署展示验收

- [ ] 有公开 Landing Page。
- [ ] 登录注册页面视觉统一。
- [ ] 可以录屏完整演示流程。
- [ ] 页面第一眼不像课程设计。
- [ ] 页面适合放入简历项目展示。
- [ ] 没有明显调试信息暴露给普通用户。

---

## 17. 风险与注意事项

1. 不要一次性重构所有页面，容易引入功能回归。
2. 不要为了高级感引入过多动画库。
3. 不要为了 Dashboard 感堆图表。
4. 不要让岗位库和目标岗位混淆。
5. 不要让 AI 历史变成新的 AI 生成入口。
6. 不要把局部改写做成自动修改原始简历。
7. 不要修改后端接口路径，除非单独立项。
8. 不要在根目录创建前端或后端源码。
9. 不要提交 `.env`、`node_modules`、`dist`、`.idea`。
10. 不要破坏现有登录鉴权流程。

---

## 18. 推荐最终演示话术

> 这个项目不是简单调用 AI 生成简历，而是围绕求职者真实投递流程做了一个 AI SaaS 工作台。用户先上传并解析简历，再粘贴目标岗位 JD，系统会基于简历和岗位生成匹配分析，指出强匹配项、弱匹配项和缺失技能，然后给出岗位优化建议和局部改写方案。前端采用蓝白色现代 SaaS 工作台风格，左侧导航组织主流程，AI 输出以结构化报告展示，避免直接展示 JSON 或大段文本，更适合真实使用和面试演示。
