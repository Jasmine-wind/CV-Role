# 项目上下文与当前状态

本文是仓库唯一的项目级 Context Source of Truth，只记录**当前实现事实、已确认约束、未完成能力和仍有效风险**。产品决策以 [PRD.md](PRD.md) 为准，架构边界见 [ARCHITECTURE.md](ARCHITECTURE.md)，阶段顺序和 Gate 见 [PLAN.md](PLAN.md)；当前行为最终以代码、Flyway 迁移、配置和测试为准。

## 1. 当前阶段

| Phase | 状态 | 当前结果 |
|---|---|---|
| Phase 1 | 已完成 | 首页收敛为选择 / 上传简历、粘贴 JD、一键分析；解析和分析由后台编排 |
| Phase 2 | 已完成 | 建立 `ResumeVersion`、`JobTarget`、`OptimizationTask`，主链路改用正式任务身份和冻结快照 |
| Phase 3 | 已完成，Gate 已通过 | 建立正式 Evidence Matching / Gap Analysis，三态为 MATCHED / PARTIAL_EVIDENCE / NO_EVIDENCE |
| Phase 4 | 已完成，Gate 已通过 | 建立两栏 Workspace、结构化编辑、Undo / Redo、自动保存、乐观并发和恢复优化前版本 |
| Phase 5 | 已完成，Gate 已通过 | 建立单 Bullet AI Suggest、代码 Diff、Apply / Reject / Regenerate；真实性与严格 Parser Blocker 修复已通过独立复审 |
| Phase 6 | 已完成，Final Gate 已通过 | Typst 三模板、真实 PDF Preview、签名 Preview receipt、导出前检查、ExportArtifact 与可重试生命周期 |
| Phase 7 | 已完成，Final Gate 已通过 | 唯一 AI Gateway Chat 主链、每用户至多一个加密 BYOK Credential、pinned HTTPS transport、任务级 Selection Snapshot、稳定 failure code、attempt Usage ledger 与最小 Settings UI |
| Phase 8 | 已完成，独立 Final Gate 已通过 | 视觉与状态体验统一：Landing / 首页 / 分析 / Workspace / Preview / Export / AI 设置信息层级收敛，统一状态与文案，窄屏降级，Element Plus 按需引入消除大 chunk |
| Phase 9 | 已完成，Final Gate PASS | 只读 Multi-JD Insight、最小 committed-fact Observability、Usage hardening/retention、PostgreSQL/Flyway + MinIO lifecycle + fake Provider + Playwright recovery E2E，以及非生产普通 User Demo 环境 |
| Product Polish Slice C | 已完成，Final Gate PASS | Typst v3 三模板语义层级、字体/长字段/分页质量回归、Preview title seam、英文/中英混排与真实风格 JD fixtures；未改变 V1 SoT、业务协议或 IA |

Phase 1–8 的冻结语义（Evidence 三态、Rewrite 技术安全校验与事实 review advisory、Preview / Export 只读 seam、Workspace CAS 与 Suggest 会话生命周期）保持不变。Phase 9 已通过独立 Final Gate；Phase 1–9 均正式完成，且未批准或创建 Phase 10。

Phase 9 之后进入 Product Polish，不新建 Phase。Slice A（已完成）冻结了可信交付链：原始简历 → 候选解析 → 确定性验证 → `READY / NEEDS_REVIEW / FAILED` → canonical `RESUME_DOCUMENT_V1` → Workspace → Preview / PDF。候选解析不是事实；无法可靠判断的内容进入未决候选由用户确认，AI 不得为修解析补造事实，无法裁决时 fail closed。

Slice B（已完成，Final Gate PASS）：前端已完成行动优先 Analysis、编辑器优先 Workspace 与 contextual inspector、语义化 Resume 编辑字段、纵向 Suggest / Diff、Workspace 内“编辑 / 预览”模式和 Audit 字段收敛；新增 no-op Suggest 前端防护与相关回归覆盖。独立 Final Gate 已覆盖混合状态、Desktop / Narrow 浏览器和 Phase 1–9 / Slice A 回归，未改变业务 API、数据源、状态机、真实性、CAS、Preview receipt 或 Export gate。

当前 ResumeEditor 的层级排序使用 contextual drag handle：Section 标题左侧和每个 Entry 左侧都有轻量六点 grip，只有 handle 接受 mouse / pen / touch pointer 拖动，正文、标题编辑、textarea、删除和 AI 控件不会启动排序。Section 拖动会移动整个 Section；Entry 拖动会移动整个 Entry 及其 Bullets，可在同一 Section 内排序，也可在相同 `kind` 的 Section 之间移动；不同 kind 禁止跨 Section，空的兼容 Section 是可放置目标。Section 与 Entry 都保留 Alt + 上 / 下方向键的同组键盘 fallback，并通过 aria-live 报告位置；Bullet 不支持排序。拖动态使用轻量 picked-up surface、区分层级的插入线和统一的编辑区自动滚动，所有成功操作只通过既有 document change → dirty → autosave → CAS 链路保存。同一父节点内排序只移动已有对象，不重新生成 Section / Entry / Bullet ID，因此 Evidence anchor 保持稳定；Entry 跨到另一兼容 Section 时会生成新的 Entry/Bullet ID 并移除来源引用，把它作为新的无 provenance 内容交给既有 autosave/CAS 链路，避免把旧 ancestry 冒充为新 ancestry。AI Suggest 不会因排序自动 Reject、Reset 或 Apply。

Slice C（已完成，Final Gate PASS）：保持 `RESUME_DOCUMENT_V1` 与显式 section list order；默认 canonical 投影改为 Summary → Experience → Projects → Education → Skills → Other，用户显式编辑后的 section 顺序不由模板静默重排。Typst current templates 升为 v3：Summary / Education / Certificate / Other 使用无 marker 的文本层级，Experience / Project 保留真实 Bullet；长 title/date 使用 gutter 与安全换行，Section heading 与首条内容通过 Typst non-breakable block 关联，长条目与长通用章节内容均可自然续页。A4 页面统一排版节奏，正文目标 10pt、metadata/contact ≥9pt，中英正文左对齐。生产镜像复制 Noto CJK Regular/Bold 静态字体并通过 `APP_RENDER_FONT_PATH` + `--ignore-system-fonts` 固定字体环境；Renderer 为 PDF 写入稳定 title/metadata。PDF inspector 新增末页 glyph 垂直占用比例，低于 20% 与既有末页行数规则共同标记 `ORPHAN_FINAL_PAGE`，正式 Export 继续经原有 PDF Quality Gate 阻断。Preview 继续使用浏览器原生 PDF iframe，依赖 PDF metadata title 消除 blob UUID，未引入 PDF.js。独立 Gate 已完成真实三模板 PDF、通用章节长内容、固定字体、Preview / Export、Desktop / Narrow 浏览器、Fresh PostgreSQL/Flyway、Docker/Compose 与完整回归验证，**Final Gate PASS**。

当前事实（Structure Fidelity Resolver）：`GET /api/workspace/{taskId}/source-reference` 的每个 `SourceBlock` 现在携带服务端裁决的 restore capability（`restoreScope` NONE / BULLET / ENTRY / PROJECT_ENTRY / CONTACT、`restoreEligible`、`restoreBlockedReason`），前端不自行推导 provenance。新增专用 `POST /api/workspace/{taskId}/source-repairs/restore`：请求只命名 frozen SOURCE occurrence，恢复什么节点、恢复到哪、边界多大全部由服务端按冻结 manifest 与认证归属重新解析；恢复只从 frozen snapshot deep copy 重建节点与其 provenance（stable ID / sourceRef / sourceOccurrenceIds / field refs / ancestry 全部保留），自动撤销该边界上的 confirmed omission，并在写库前重新运行 authoritative fidelity——会引入 ambiguity / duplicate 或未真正解除 blocker 的恢复被拒绝。Project 边界只有在全部 meaningful frozen occurrence 当前都 unmapped 且无歧义时才可整 entry 恢复，部分映射或错误归属继续 fail closed；普通 PUT 的 frozen ID resurrection 拒绝保持不变，dedicated restore API 是唯一的 resurrect 通道。Workspace 的“结构核对清单”升级为“结构问题”卡片：每个 blocker 按服务端 capability 提供 恢复原文 / 恢复整个项目 / 确认省略 / 定位问题内容 / 返回首页重新上传，不再显示无法处理的假按钮；manifest 类问题明确说明无法在任务内修复。Restore 与 confirm / unconfirm omission 共享同一个 SOURCE mutation mutex 与 expectedRevision CAS，保存未完成（dirty / saving / failed / conflict / revision 不同步）时 restore 按钮保持禁用。Preview 的 needsReview 检查新增“查看并处理”：只导航回编辑态 Source 结构问题区域并定位第一个 blocker，不修改任何数据；所有结果仍由 `GET source-reference` 权威决定。

当前事实（resume extraction hardening）：DOCX main body 现在按 `getBodyElements()` 的 paragraph/table 原始出现顺序提取，table cell 内按 body element 递归处理 paragraph 与 nested table；header、footer 与 textbox 提取及 textbox duplicate suppression 保持原有边界。可选择文本 PDF 在同一个 `PDDocument` 内生成 legacy、position-sorted 与保守的 PDFBox `LAYOUT_LITE` 候选；布局候选只在确定性健康评分超过既有候选至少 8 分时采用，否则保留既有行为。布局块保留 stable source block ID、页码、坐标、字体、粗体、缩进、项目符号和合并行的 source IDs；扫描 PDF 仍不 OCR，复杂多栏 / absolute-position PDF 仍可能进入 Resume Review。PDF 的真实物理页数已传播到 `ResumeParseMeta.pageCount`；DOC/DOCX 无可靠分页时为 `0` 且 source block 不伪造 page。有效但无可提取 glyph 的 PDF 归类为 `EMPTY_PDF`，检测到图像内容且无文本的 PDF 归类为 `SCANNED_PDF`；旧的无 metadata 调用保留保守扫描分类。结构恢复健康模块以 21 个 PII-free synthetic case（包含多项目、技能、联系方式语义与荣誉的 OmniGateway 组合回归）作为 CI gate，检查 source coverage、未决内容、重复 ownership 和 entry boundary，并生成不含原文的聚合 effect report。项目分段复用通用 entry boundary 的 `ENTRY_HEADER`、粗体、字号与缩进信号，显式 `项目一：2048` 等尾部标题不再依赖“系统/平台”等词表，同时拒绝单字符序号与纯标点伪标题；可靠的独立 Skills 章节拥有 canonical skill keywords，工作/项目技术推断只在缺失可靠 Skills 时回退，项目自身 tech stack 不受影响；荣誉日期将 `-`、`–`、`—` 作为等价日期分隔符，仅合并因此产生的同奖项 extraction echo，完全相同的两个 source occurrence 仍保持独立。可选 AI repair 最多在一次 parse 中调用一次，且仅返回低置信度、source-backed、reference-only 候选；不自动写入 canonical 或用户事实，失败时规则结果保持不变。长 block 不再硬截断 500 字符，而是在安全标点、空格或必要的 hard boundary 处无损拆成不超过 500 字符的连续 fragments。历史结构化 JSON 先经过 raw-tree 严格形状校验，再对 generic `heading` / `meta` 做 section-compatible projection；source-free 兼容投影永不制造 provenance，source-backed generic education 行必须由当前 SourceContext 重新证明，无法证明的内容只进入 review sidecar。`sourceOccurrenceIds` 是唯一 occurrence 身份，block ID 不会直接升级为 occurrence。

当前事实（manual acceptance corrective）：账户区域已提供 Settings shell 与 `/settings/profile`、`/settings/ai-provider` 两个入口；账号菜单顺序为账户设置、AI 设置、退出登录。个人资料只允许通过 `PATCH /api/users/me` 更新 nickname，空值回退 username，username / email 只读并立即同步 auth store。AI settings GET 返回不敏感的 `credentialStorageAvailable` 与用户 Credential 状态；存储未启用时仍可 Test 但 Save disabled。AI Settings 对外为 `UNCONFIGURED`、`SAVED_DISABLED`、`ACTIVE` 三态，不暴露系统 Provider capability。Classic 当前模板由 v4 升为 v5，新增 `typst/classic/v5/main.typ`；v4 及更早版本保留供历史导出物解释，Modern / Minimal 继续使用 v3。Classic v5 只调整章节 / entry / bullet vertical rhythm 与稳定 Regular / Bold 字体环境，不改变 `RESUME_DOCUMENT_V1`、渲染器版本 `typst-resume-renderer/3`、Preview receipt 或 Export Gate。

Frontend Layout Unification Slice 1、Corrective、Slice 2、Slice 3、Slice 4 与 Slice 5 均已完成前端实现并通过 Frontend Functional / Visual Consistency / Responsive Gates。Slice 3 清理了 Resume Review 的旧隐藏 inline presentation branch，建立共享 `TaskHeader` 与 `RequirementNavigator` presentation primitives，并让 Job Analysis 与 Workspace 共用 focused Task Shell、Evidence / Requirement 语言和 `?requirement=:id` 连续性。Slice 4 保持 Preview / Export 为 Workspace 内 Edit ↔ Preview mode：Preview 复用同一 TaskHeader、Job / Resume identity 与 workflow，隐藏 Requirement Navigator，使用 fluid PDF canvas + compact Export Inspector；Template、Preview receipt、Document Check、ExportArtifact history、download / delete / stale / blocked 语义均沿用正式 API。Job Direction Insights 改为 standard read-only analysis workspace，按 cohort 展示真实样本、保守共性要求与可追溯 Source Trace；AI Provider Settings 改为 standard Configuration + Status / Security + Danger Zone 布局，并将 test / save / enable / disable / delete 失败状态原位展示。Slice 5 将 Landing 收敛为唯一更具品牌表现力的 product-as-demo split hero，并以共享 `ProductFlowDemo` 复用真实 Resume → JD → Requirement → Evidence → Gap → Confirm → PDF 语言；Login / Register 共用 `AuthShell`，桌面为产品上下文 + focused form，窄屏顺序为 Brand → context → form。未新增 API、route、Provider、History、Score 或业务状态机。Workspace 主体、CAS、Suggest 与 Preview / Export 后端边界未改变。当前本机 backend / PostgreSQL 未启动，因此 Slice 5 的 Real PDF Visual Gate 标记为 `BLOCKED BY ENVIRONMENT`；此前 Slice C 的真实 Typst / PDF Gate 仍为 PASS。

## 2. 当前系统与主流程

当前系统是 Vue 3 SPA + Spring Boot 模块化单体，使用 PostgreSQL / Flyway / pgvector、Redis、Local / MinIO，以及 OpenAI-compatible Chat / Embedding 接口。PostgreSQL 是业务事实来源；Redis 只保存可重建内容；文件访问统一经过存储抽象和用户归属校验。

Chat AI 调用在 Phase 7 收敛为唯一 seam：业务模块只依赖 `AiGateway` 并显式携带 user/task 上下文与 Selection Snapshot，不接触 API Key、Authorization、Provider URL 或 HTTP 细节；Gateway 负责 selection、SYSTEM / USER 分离、模型 / 配置、安全传输、重试 / 时限、稳定错误映射和 Usage 记录。OpenAI-compatible Adapter 默认先发 STANDARD Chat Completions 请求，仅在 400/422 body 的 allowlist 参数证据明确表示不支持时，有限协商 `max_tokens` / `max_completion_tokens` / temperature；reasoning-only 成功响应只保留内存 boolean，并在同一基础 dialect 下最多尝试三种通用 reasoning control。进程内兼容 profile cache 以规范化 Base URL、model 和 JVM 内 SHA-256 key fingerprint 隔离，TTL 12 小时、容量 256，不保存输入或响应。生成式 Chat 现在是 BYOK-only：没有 ACTIVE Credential 时新任务返回 `AI_CONFIGURATION_REQUIRED`，绝不读取平台 Chat 密钥或 fallback。Embedding 保持独立的 platform-only 语义检索基础设施，不使用 BYOK，也不替代 Chat generation。历史 `SYSTEM_DEFAULT` selection 仅用于读取旧结果；Retry / Regenerate / Suggest fail closed。

当前前端路由只有 Landing、首页、我的简历、岗位分析结果、优化工作区、AI 设置、登录和注册；一级导航只有首页和我的简历，AI 设置仅从顶栏账号菜单进入，不进入默认用户主流程。岗位库、独立目标岗位管理、旧匹配编排和技术分类式 AI 历史不在默认用户流中。全局壳只承担 navigation / account / 窄屏菜单，页面标题与任务操作由各页面自身承担；Element Plus 按需引入且路由懒加载，窄屏下 sidebar 变为 Drawer。Workspace 桌面默认采用冻结 SOURCE / 当前 TARGET 约 40/60 双栏，原文支持 occurrence blocks 与 task-scoped 原始 PDF；窄屏切换为 Source / Current / Context focused tabs，切换时保留 target/source 定位状态。Requirement / Evidence 和 AI Suggestion 只在临时 context drawer 中出现，AI 打开时仍保留 Source、Current、Suggestion 三层上下文。Preview / Export 仍使用 Workspace 内“编辑 / 预览”模式。

```text
登录
→ 选择已有简历，或上传后由后台自动准备
→ 粘贴目标岗位 JD
→ 创建 JobTarget + SOURCE / TARGETED ResumeVersion + OptimizationTask
→ 冻结 Resume / JD 输入并完成岗位解析
→ 生成正式 Evidence / Gap 分析
→ 通过 optimizationTaskId 查看结果并进入 Workspace
→ 人工编辑 TARGET，或对单个 Bullet 请求受约束 AI 建议
→ 查看 Diff，显式 Apply / Reject / Regenerate
→ Apply 后复用 Undo / Auto Save / expectedRevision CAS
→ 保存完成后选择模板预览 PDF，导出为带生命周期记录的导出物
```

Preview 与 Export 是同步渲染：只读取服务端已保存的 TARGET `structured_content` 与 revision，前端草稿、SOURCE、任务冻结快照与证据分析都不能作为渲染输入；`expectedRevision` 与服务端不一致时拒绝生成，防止静默输出旧版本。

失败分析按同一 `OptimizationTask` 重试并复用冻结输入；成功任务不可被重试改写。历史任务缺少正式证据分析时只兼容读取旧结果，不允许启用 Phase 5 岗位定向改写。

## 3. 当前领域与数据事实

- `ResumeVersion`：规则解析会把可证明的 canonical 文档物化为当前无岗位的 SOURCE；`READY` SOURCE 可交付，`NEEDS_REVIEW` SOURCE 以 `PENDING` 保存为用户确认草稿，不能进入任务 / 导出。每次新分析引用已确认 SOURCE 并派生独立 `TARGETED` 岗位版本；审查修改不覆盖已引用 SOURCE，而是在同一事务中发布新的 SOURCE 并 CAS 移动当前指针，保留旧快照。Workspace 只写 TARGET，不修改上传简历、解析结果、SOURCE 或任务冻结快照。Parse generation/token claim 由独立 `REQUIRES_NEW` 协调事务在 CPU、文件和 Provider 工作前提交；SOURCE `insertIfCurrentParseClaim` 与解析结果 `updateIfCurrent` 仍在同一 service-level 事务内原子提交，旧 worker 只能 CAS 失败并回滚其 SOURCE。
- 可选 Resume AI repair 由 `resume_ai_repair_attempts` 做 durable single-flight：reservation 在 Provider dispatch 前通过 `REQUIRES_NEW` 独立提交，成功 reference projection 可复用；CLAIMED 与 dispatch 后失败不 reclaim，只有明确为零 dispatch 的失败可再次 claim，跨节点无法确认的状态 fail closed。它不是 canonical 或用户事实 Source of Truth。
- `JobTarget`：保存用户归属、原始 JD、标题和来源；当前仍通过兼容引用复用 `job_descriptions` 的解析能力。
- `OptimizationTask`：正式业务身份和前端路由身份，保存版本关系、输入快照及 Prompt / Rules / Provider / Model / Template 配置快照；`async_tasks` 只承担执行状态和轮询。ResumeVersion ownership、Task SOURCE / TARGET / JobTarget 输入及 EvidenceAnalysis / EvidenceRequirement 父关系在插入后不可重挂，避免直接 SQL 或并发 write skew 改写已发布图；内容、状态、revision 与 async/result attachment 仍可沿各自 CAS 更新。Resume、OptimizationTask 与 JobDescription 删除时按 user + business identity 将 PENDING / RUNNING task 置为 `CANCELLED`，取消与父级删除在同一事务中提交或回滚，删除失败不会永久停掉仍保留的任务。Resume / OptimizationTask 提交路径按固定 Resume → OptimizationTask 行锁顺序，把 async row 插入和关联放进同一事务，删除与提交共享该生命周期边界；基于既有 JD 新建任务按 Resume → JobDescription 锁定，JD 删除先持有同一 JD 行锁再发现派生任务，不能漏掉并发提交；Resume / JD 父删除发现多个正式任务后统一按 task ID 升序锁定，避免交叉删除形成反序死锁。worker 只在 committed completion 后进入线程池，事务回滚不 dispatch，executor rejection 通过新事务记录。正式 worker 的 RUNNING / SUCCESS / FAILED 回调必须以精确 `asyncTaskId` 和 `MATCH_ANALYSIS / OPTIMIZATION_TASK / bizId` 绑定通过数据库 CAS，晚到旧执行不写正式任务或 Evidence。
- 正式证据分析：每个任务最多一条 `evidence_analyses`，子表为 `evidence_requirements` 和 `requirement_evidences`；正式主链路不再向 `ai_job_match_results` 写新结果。
- Workspace 文档：`RESUME_DOCUMENT_V1` 是唯一规范编辑结构（Slice A），持久化在 `resume_versions.structured_content`，不存在第二套 Workspace 内容字段；历史 generic V1 内容只读升级。SOURCE root manifest 的 occurrence order/text/primary alias 是 provenance 鉴权基础；新 SOURCE 还在同一 V1 JSON 内以可选 `sourceOccurrenceRefs` 冻结 occurrence 页码、bounding box、字体、粗体、缩进、bullet/role，sidecar 的 ID/text 不一致时 manifest invalid 并 fail closed，历史 SOURCE 缺少 sidecar 时不推测坐标。TARGET 的 section/entry/bullet/contact 引用只能与冻结节点 lineage 一致，未知或被重挂的引用按 AMBIGUOUS fail closed。Workspace Save 不信任客户端提交的 provenance：CAS revision 预检通过后，服务端递归清空请求中的 root/node 引用，只按冻结 SOURCE 与当前持久化 TARGET 的稳定 ID + ancestry 恢复可信引用；重复拓扑 ID 或已知节点重挂直接拒绝，新节点保持无引用。无边界的重复显式 source text 不再取第一条 occurrence，只有唯一 occurrence、可信 preferred boundary 或唯一连续 span 才可建立 canonical 引用。`GET /api/workspace/{taskId}/source-reference` 聚合原文 blocks、EXACT/MERGED/SPLIT/UNMAPPED/AMBIGUOUS 映射和 fidelity issues；映射状态只表达 lineage relationship，`textChanged` 单独表达 TARGET 文本是否不同于冻结 SOURCE。用户可对服务端判定为可省略的 UNMAPPED occurrence（Project 以完整 entry 边界、跨多个物理 occurrence 的普通 Bullet 以冻结 bullet 边界）显式确认或取消 Intentional Omission；确认集合仍保存在 TARGET `RESUME_DOCUMENT_V1`，但普通 Save 会剥离客户端提交值并恢复服务端已确认状态，只有 task-scoped omission API 经完整 ownership / frozen manifest / mapping 校验后才能以同一 revision CAS 更新；请求在途期间前端冻结 TARGET 编辑、Undo / Redo、恢复和 Suggest，避免 omission 与自动保存竞争同一 revision。alias occurrence 按同一 logical group 原子处理；确认只消除对应 `SOURCE_CONTENT_UNMAPPED`，不能绕过歧义、无效 manifest 或重复 lineage/topology。Project omission 仍按完整 entry 的 meaningful occurrence 原子确认，但确认只消除这些 occurrence 的 `SOURCE_CONTENT_UNMAPPED`；冻结 Project entry 在 TARGET 中缺失时 `PROJECT_BOUNDARY_LOST` 仅作为建议检查返回，完整确认省略后该建议消失，从头到尾不阻断 Preview / Export。`source.pdf` 经完整 task ownership graph 后从 Storage abstraction 读取且不返回 object key。
- 解析交付质量（Slice A）：`resume_parse_results` 新增 `quality_status`（PENDING / READY / NEEDS_REVIEW / FAILED，SoT）、`quality_issues`、`unresolved_items`（未决候选，审查态数据，不是简历内容）与 `canonical_source_version_id`（仅指向当前 SOURCE）。canonical JSON 只存在于 `resume_versions.structured_content`；`structured_json` 仍是候选解析产物，不能进入新任务快照。`NEEDS_REVIEW` 也返回规则生成的 source-backed canonical 草稿供审查，但其 SOURCE 为 `PENDING`；解析成功不等于可安全投递，非 `READY` 或没有 canonical SOURCE 的简历禁止创建新分析任务，历史任务不受影响。
- 内容并发：`resume_versions.content_revision` 是服务端乐观并发版本；保存、恢复以及确认/取消 Intentional Omission 都必须携带 `expectedRevision` 并通过单条条件更新递增。冲突保留本地草稿，不允许无条件覆盖。
- 导出物：`export_artifacts` 记录成功生成的 PDF 派生文件及实际 preflight（用户 / 任务 / TARGET / revision / 模板与渲染器版本 / storage metadata / 页数 / 联系方式 / 页数告警 / 越界 / 孤立末页 / 可读性告警）。Structure Fidelity 全部降级为 advisory：冻结原文缺失、歧义/重复映射、项目边界丢失、标题丢失但 children 存活、重复联系方式等只作为 `fidelityIssues` 建议检查返回（severity 仅表达严重程度），`exportBlocked` 恒为 false；服务端确认的 Intentional Omission 只影响建议项是否出现，restore / omission 能力保持不变；PDF 页数仍属于后续 layout preflight。READY 可下载；DELETE_PENDING 不可下载但保留重试依据。任务与 TARGET 的关系由复合外键直接约束。单个导出物删除完成 DELETE_PENDING → 对象删除 → 元数据删除；父级删除的 async task `CANCELLED` fence 随父事务提交或回滚；DELETE_PENDING 独立提交后再删除对象，保留元数据到数据库父事务成功级联，回滚时仍可重试。
- Multi-JD Insight：没有表、cache 或 Capability Source of Truth；只读聚合当前用户近 180 天的 `SUCCESS` Task、冻结输入、SOURCE Version 与正式 Evidence。cohort 必须同时匹配 `resumeId + SHA-256(resume_input_snapshot)`；相同规范化冻结 JD 只取最新成功 Task，最多 20 个、至少 8 个才显示。
- Insight Requirement：仅对单一、字面技术锚点做小型固定注册表分组（否则精确规范化文本）；每个 JD 取最保守三态，结果保留 Task / Requirement / Evidence 追溯，绝不推断用户现实能力或重算 TARGET 编辑。
- Observability：`ProductObservabilityService` 只查询已提交且仍保留的业务表；没有 `product_events`、用户指标页或长期识别性聚合。不可由现有事实可靠得出的 Workspace entry、Preview success、Suggestion apply 指标继续不记录。
- AI Usage：每个实际 Provider dispatch 仍是一条最小 ledger row；写入通过独立 `REQUIRES_NEW` 事务，正式 JD parse 与 Evidence 调用绑定 `optimizationTaskId`，写入失败不影响业务。记录不含 Prompt、Resume/JD、Output、Key、URL 或货币成本，原始 metadata 90 天清理。

当前正式迁移为：

- V19：加法式建立 Phase 2 正式领域并回填可验证的 V1 数据，保留旧表。
- V20：加法式建立 Phase 3 正式证据表，不用无可追溯引用的旧匹配结果伪造正式 Evidence。
- V20.1：将正式语义收敛为 MATCHED / PARTIAL_EVIDENCE / NO_EVIDENCE 和 SUFFICIENT / PARTIAL；旧 Phase 3 派生分析失效后可用冻结输入重试，V1 历史不变。
- V21：只增加 `content_revision BIGINT NOT NULL DEFAULT 0`，不增加第二个内容字段。
- V22：加法式建立 `export_artifacts`，补充 task ownership 与 task→TARGET 复合唯一索引，持久化 preflight 和 READY / DELETE_PENDING 生命周期；不修改 V1 数据。
- V23：加法式建立 `ai_provider_credentials`、OptimizationTask AI Selection Snapshot 与 `ai_usage_records`；Credential / Task / Usage 使用复合用户归属外键，旧 Task 回填为 SYSTEM_DEFAULT，部署 Secret 不迁入数据库。
- V24（Slice A）：加法式为 `resume_parse_results` 增加质量状态、当前 canonical SOURCE 指针和审查 sidecar（存量行默认 `READY`，但无 canonical SOURCE 的新任务必须重新解析），为 `export_artifacts` 增加导出时刻的文档质量门、孤立末页和可读性标记（历史行为可空）；不改写历史内容。
- V29（Product Polish）：为可选 reference-only Resume repair 增加 durable reservation/result 表；CLAIMED 在 Provider dispatch 前提交，进程中断和 dispatch 后失败不 reclaim，明确零 dispatch 的失败可安全重试。
- V30（Product Polish hardening）：以加法式函数/触发器更新强化已被 task、子版本、canonical pointer 或正式 Evidence 引用的 SOURCE 形状不可变；V27/V29 保持已发布 checksum。
- V32（Product Polish hardening）：引用中的 SOURCE 删除改为 fail closed，SOURCE→TARGET 外键补强同一用户/同一简历约束，正式 task 的 source/target 也必须属于同一简历；整份 Resume 删除由服务先清理正式 Evidence/Task 后再走父级级联。
- V33：迁移前校验历史正式 task 的 source/target 同简历关系；发现既有越权边时 fail closed，不静默放行。
- V34：迁移前校验正式 Evidence 必须回溯到其分析任务的同一 SOURCE；数据库拒绝跨 Resume / 跨任务 Evidence，并拒绝已有正式 Evidence 的 task/source 关系被重挂。
- V35：为解析结果补齐并强制保存 Resume owner，使用 `(user_id, resume_id)` 复合外键约束 canonical SOURCE 指针，阻止跨租户/跨简历的直接 SQL 重挂。
- V36：加法式冻结 ResumeVersion / OptimizationTask / Evidence 父链的 ownership identity edges，阻止 task 创建后重挂 TARGET，以及 Evidence 插入与父级重挂并发互相不可见造成的 write skew；迁移前再次校验既有正式 task 图，异常历史数据 fail closed。

## 4. 必须保持的设计约束

### 用户归属与数据边界

- 保持前后端分离和模块化单体；未经新的产品基线，不引入微服务、消息队列或 Kubernetes。
- 所有资源读取、写入和文件访问都必须校验 `current_user + resource_id`；正式关系继续使用服务校验和复合所有权外键。
- 数据库结构只由 `backend/src/main/resources/db/migration/` 下的 Flyway 迁移维护；不得修改已发布迁移改变生产状态。
- 原始 Resume、原始 JD、任务输入和配置快照必须可追溯；岗位版本不得静默污染源版本。
- Redis 不能成为唯一业务事实来源；存储访问必须经过 `FileStorageService` 抽象；日志和客户端错误不得泄露凭据或原始供应商秘密。
- 异步任务必须使用真实状态 / 阶段，不伪造进度百分比；失败必须保留已保存的 Resume / JD 和冻结输入，不能要求用户重做前置步骤。

### Evidence 与真实性

- MATCHED 表示冻结材料足以支持完整要求；PARTIAL_EVIDENCE 表示存在直接相关但不完整的材料证据；NO_EVIDENCE 只表示当前材料未找到证据，不代表用户没有该能力。
- Requirement 必须来自冻结 JD；MATCHED / PARTIAL_EVIDENCE 必须保留逐字命中冻结 SOURCE ResumeVersion 且与要求相关的 Evidence；无有效 Evidence 时必须降级为 NO_EVIDENCE，且 NO_EVIDENCE 不保存 Evidence。
- PARTIAL_EVIDENCE 不改变 Evidence 语义。Rewrite 可以提出技能、经历、数字或成果候选，但必须以 review advisory 提醒用户确认；用户显式 Apply 前不写入，且不修改 Evidence。
- Resume 和 JD 都是不可信输入，不能覆盖平台指令、Schema、权限或真实性约束；AI 输出必须经过受控 DTO / Schema 解析和代码校验后才能进入业务流程。

### Workspace 与 Phase 5

- Workspace 编辑的文档格式为 `RESUME_DOCUMENT_V1`（Slice A）：联系方式携带显式类型（电话 / 邮箱等，不再靠自由 label 猜测），条目按章节语义携带公司 / 职位 / 学校 / 学历 / 专业 / 起止时间原文，技能为一等技能组；自由 `heading/meta` 仅作历史只读兼容。Slice A 之前保存的 generic V1 目标内容在读取时按确定性规则升级为 V1 语义结构，保存仍写 V1；升级失败显式报错引导重新解析，不降级产出。

- `optimizationTaskId` 是 Workspace 唯一入口；服务端负责解析 Task → SOURCE / TARGET / JobTarget / Resume / User 完整链路，前端不能指定可写 ResumeVersion。
- TARGET 是唯一可编辑版本；SOURCE、`resume_input_snapshot` 和 Evidence 始终只读。用户对 SOURCE 内容的 Intentional Omission 也是写入 TARGET 的服务端确认状态，不修改 SOURCE；仅保存成功且无本地草稿时可确认/取消。TARGET 编辑后不实时重算分析，Workspace 通过可收起的 contextual inspector 展示分析时的 SOURCE Evidence。
- Undo / Redo 只属于当前页面会话；刷新后只恢复最后成功保存的服务端内容；localStorage / sessionStorage 不是正式简历内容恢复源。
- AI Suggest 只处理用户明确选中的单个 Bullet。平台策略进入 SYSTEM，简历、JD、Evidence 和本次要求进入标记为不可信的 USER 数据区；Prompt 使用单遍模板替换。
- Suggestion 只存在于当前前端会话，服务端只读生成且没有服务端 Apply。Suggest / Reject / Regenerate 不修改 TARGET 或 revision。
- `RewriteFactValidator` 只以当前 Bullet 原文为审查基线，不跨 Bullet 或从 SOURCE / Evidence 搬运事实。新增或升级技术、实体、数字、量化、责任级别、成果、因果、范围或时间返回 `reviewCode` / `reviewMessage` advisory，并不拒绝；空值、超长、malformed / refusal、内部身份泄露、不可见控制字符、不支持脚本等技术问题仍 fail closed。
- 候选绑定 requestId、baseRevision、草稿变更序号、bulletId 和原文哈希。人工编辑、Undo / Redo、Restore、revision 变化、冲突、任务切换、Regenerate 替代或乱序响应都会使候选失效。
- Apply 必须由用户显式触发并再次验证候选，只替换对应 Bullet，形成一个 Undo 节点，然后进入既有 dirty → Auto Save → CAS；不得绕过 Phase 4 并发协议。

### Preview / Export 与渲染

- Structured Resume JSON（TARGET `structured_content`）是唯一简历业务 Source of Truth；Preview / Export 只能经 `optimizationTaskId` 读取服务端已保存 revision，禁止 HTML 内容源、第二套简历数据、PDF 反解析、模板存业务数据与前端指定可渲染版本。
- 渲染是独立 seam：确定性映射 → 版本化内置模板 → Typst 同步编译 → PDF；用户内容全部转义为 Typst 字符串字面量，渲染进程通过 `--root` 限制文件读取，内置模板不引用外部包且包目录隔离；用户内容经转义无法触发导入。当前没有 OS 级进程网络沙箱，不得把空包目录表述为网络隔离；模板只负责展示，不承担业务判断。
- Preview 与 Export 共享同一 Renderer、模板版本、编译器与字体环境。服务端签名 receipt 绑定 user / task / TARGET / revision / template+version / renderer / PDF checksum；无 Preview、过期 receipt、revision / 模板 / 任务 / 用户变化或重编译 checksum 不同均拒绝 Export。
- 导出检查分两层（Slice A/Slice C；最新一轮起只保留技术性阻断）。Document Gate 仅在解析 `PENDING` / `FAILED` 或当前没有可渲染 document 时阻断；未确认候选、系统兜底章节、重复章节、缺少联系方式与 Structure Fidelity 只标记 `needsReview`。PDF Quality Gate 只在 renderer / 文件生成失败时阻断：文字越界、不可读字号、孤立末页（页数 ≥2 且末页非空行 <3，或末页 glyph 垂直占用比例低于 20%）与页数超两页一律只作为导出物上的 advisory 记录。两页简历合法，孤立/稀疏第二页仍会提示。单个导出物删除采用持久化 DELETE_PENDING → 对象删除 → 元数据删除；Resume / OptimizationTask / JobDescription 父删除先独立提交 DELETE_PENDING 并删除对象，元数据留到数据库级联成功，失败回滚仍可重试。
- 模板升级新增版本而不原位修改：Slice A 的 v2 与更早 v1 保留供历史导出物解释；当前 renderer 消费 V1 语义模型并按章节类型分支（Classic v5、Modern / Minimal v3，Classic v4 及更早版本保留），渲染器版本为 `typst-resume-renderer/3`。
- 未 Apply 的 AI Suggest 仅存在于前端会话，不进入 Preview / PDF / ExportArtifact；Phase 6 不新增 Suggestion History、Change Event 或 AI 持久化链路。

## 5. 尚未实现

- 缺少事实时向用户询问并记录真实补充 / 确认；Slice A 已实现解析层最小确认流（未决候选的接受 / 编辑 / 删除），更完整的事实补充仍属后续。
- 用户 Profile / Rules 与平台策略的完整分层；Phase 5 只有平台默认策略和本次自定义要求。
- Markdown / JSON 迁移导出仍属后续 P1；不属于 Phase 6。
- 用户数据导出 / 全量删除仍未实现。Home 已提供最近优化列表，可继续成功任务或重试失败任务。Resume Review 会在当前会话保留未确认的本地 candidate 草稿，离开前需要显式放弃；Workspace 当前 Requirement 的 Evidence navigation anchor 在 TARGET 文本编辑和 autosave 中保持稳定，仅在切换 Requirement 或目标 ID 消失时更新。

Phase 1–9 已正式完成；后续能力仍须依 `PLAN.md` 和新的产品决策推进，当前没有批准或创建 Phase 10。

## 6. 当前技术债与遗留风险

- `requirement_evidences.source_resume_version_id` 的数据库外键仍只直接约束同用户，没有直接约束为所属任务的 SOURCE；正式服务当前固定写入并校验任务 SOURCE，后续如补强该关系必须继续使用新迁移。V32 已阻止直接删除被 Evidence 引用的 SOURCE，并保留整份 Resume 的服务级清理路径。
- V19 / V20 的新增表尚未经过生产规模数据验证；V20.1 会原位改变正式枚举和列语义，旧 Phase 3 应用不能运行在迁移后 Schema 上，部署必须同步升级应用与 Flyway 并遵循备份流程。
- Workspace 已有真实 PostgreSQL + Playwright 双页面 CAS conflict / 本地草稿恢复覆盖；更大规模多线程争用与数据库故障注入仍未做压力验证。
- Resume worker 的 active 检查是协作式取消，不中断已开始的解析 / Provider / Embedding；成功删除后的迟到写入由 parse CAS 与父级外键阻断，不保证立即停止计算或外部调用。
- 异步 dispatch 仍是单进程内存机制；事务提交后到线程池接收前的进程崩溃可能留下 `PENDING` 任务，当前没有 durable outbox / scheduler 或跨进程 `Future` cancellation seam。修复该残余窗口需要新的架构决策，不能在 Phase 9 合约内自行引入 MQ。
- `Resume.storageType` 正确记录写入后端，但读取 / 删除使用当前配置的单一 `FileStorageService`，并未按历史行路由不同后端，`export_artifacts` 也没有独立后端列；切换 `APP_STORAGE_TYPE` 必须按 `OPERATIONS.md` §9.1 停写、迁移并逐 key 验证既有对象，不能只改配置。多后端路由需另行批准，不在本 Phase 隐式引入。
- Workspace 转换器对未知 / 错误类型、超限内容和无法完整转换的旧快照会整体 fail closed；少量旧数据可能需要重新解析，不能用不完整投影覆盖 TARGET。
- 初始 Workspace 元素 ID 按位置派生，只对同一冻结快照的重复转换稳定，并非语义或内容寻址 ID；Restore 会恢复基线位置 ID。Workspace 的当前 Evidence 导航 anchor 只在建立时使用 section label / evidence quote 定位，后续只按 sectionId / bulletId 验证存活，不随 TARGET 文本或 autosave 重新匹配。当前 Phase 5 还绑定 revision、草稿序号和原文哈希，未来功能不得只凭元素 ID 判断候选仍有效。
- 正式 Evidence 目前只保存 SOURCE 版本、section label 和逐字 quote，没有 Workspace 元素 ID 或字符范围。当前单 Bullet 手动选择绕开了该缺口，但可靠的“查看原文”、从建议跳转到编辑位置和更细来源追踪仍缺正式锚点模型。
- 正式证据分析依赖 JD 结构化解析质量；解析失败或信息不足时只能返回少量要求或整体失败，不能猜测补全。推理型模型还需要足够输出 token，切换模型或 Provider 时必须重新验证额度。
- 旧表、接口和服务仍被解析、历史读取、删除和兼容重试依赖，当前不能直接删除。删除 Resume / JobDescription 的级联影响和可能残留的源快照仍需在统一数据生命周期阶段核对。
- RewriteFactValidator 现为内容 review detector 与技术安全检查：完整 Latin token、数字—单位—对象关系、否定极性、能力程度、责任层级、成果 / 因果、时间 / 范围及未知中文事实片段会生成 advisory，交由用户确认；Unicode 控制字符、未知脚本、内部身份泄露、空值与超长输出仍 hard reject。不得用第二次 LLM、Embedding 或相似度判断替代技术安全门。
- AI Suggest 是同步请求，当前无限流；服务端 AI 默认超时 30 秒、前端请求超时 65 秒。事实变化不会隐藏候选或阻止 Apply；Apply 仍是用户显式操作并继续经过 Undo、Autosave 与 CAS。生成窗口内的并发编辑通过候选失效和 CAS 防止落库覆盖，但后续运维仍可评估频控。
- 首页进行中的任务仍可通过会话引用恢复；已完成的任务由 Home 最近优化列表按 updatedAt / id 倒序展示，可跨设备继续成功任务或重试失败任务。
- 前端主 chunk 曾超过 Vite 500 kB 提示阈值；Phase 8 已通过 Element Plus 按需引入 + 路由懒加载消除（入口 chunk 约 160 kB，组件样式随路由分块加载）。
- 结构化编辑器曾因对响应式 Proxy 调用 structuredClone 导致手工编辑崩溃；已改为与 useWorkspaceEditor 一致的 JSON 克隆并有组件回归测试。
- 本机开发环境的 DNS 被 VPN 工具劫持为 198.18/15 fake-ip，会被 Phase 7 pinned transport 合法拒绝（UNSAFE_BASE_URL，fail-closed 预期行为）；本地 E2E 需通过 `jdk.net.hosts.file` 提供真实公网 IP 后才能完成真实 Provider 调用。
- 本地 dev 数据库曾应用过 Phase 6 草稿版 V22，已在本地清理并由正式 V22 / V23 重新应用；该修复只涉及开发库，不涉及已发布迁移。
- Typst 编译为同步请求：首次冷启动（字体扫描）可达十余秒，后续编译通常在秒级；当前以 30 秒编译超时与前端 65 秒请求超时兜底。若未来内容规模使同步无法满足，必须重新决策而不是自行引入后台导出架构。
- 渲染依赖部署环境的 Typst 二进制与 CJK 字体：后端镜像与 CI 已内置固定版本（typst v0.15.1 + Noto CJK），非容器化部署必须自行安装；二进制缺失时渲染接口 fail closed，其它链路不受影响。
- Phase 7 前本环境未使用真实公网 Provider Credential 执行 smoke；Phase 8 浏览器 E2E 已在本地以真实 DeepSeek Credential 走通 JD 解析、证据匹配、单 Bullet Suggest 与 Preview / Export（需上述 fake-ip DNS 规避）。不同公网 Provider 的兼容性仍是部署环境相关风险。
- Snapshot-hash cohort 会在材料变化后拆分样本，字面锚点策略也会保守地少聚合；这是避免混合不同材料或错误语义合并的既定取舍。
- Demo 仅允许 `demo` profile 与 `APP_DEMO_ENABLED=true` 的独立数据库/存储环境；当前没有用户全量删除入口；Home 已提供当前用户最近 OptimizationTask 列表，按 updatedAt / id 倒序，可继续成功任务或重试失败任务。Phase 9 不应被表述为已完成账号生命周期。
- 本机使用 Java 25 时需要显式开启 annotation processing 才能生成 Lombok 代码；CI 的标准运行环境是 Java 21。
- Slice A 的确定性验证与覆盖判定是有意的保守规则：联系方式格式、章节结构、跨章节重复、类型错位、短行碎片与未表示行进入未决候选；它不追求完美解析，只保证可确定则接受、不确定则确认、明显错误则阻断。覆盖判定使用整行包含、明确标签后的全量事实 token、联系方式短标签残差与结构标题白名单；不以 70% 比例掩盖遗漏。

## 7. 文档与事实优先级

1. [PRD.md](PRD.md)：V2 产品和最高层架构决策。
2. 代码、Flyway 迁移、配置和测试：当前行为事实。
3. [ARCHITECTURE.md](ARCHITECTURE.md)：当前实现边界和演进约束。
4. [PLAN.md](PLAN.md)：阶段顺序、Gate 和非目标。
5. 本文：当前状态、已确认约束、差距和风险。
6. [OPERATIONS.md](OPERATIONS.md)：部署与运行方式。

阶段 Gate 的详细完成记录保留在 `PLAN.md` 和 Git 历史中，不在本文重复维护逐轮测试数量、审查过程或迭代日志。

## 当前 BYOK-only 事实

生成式 AI 已切换为用户自带 Credential only。`ContextAwareAiGatewayService.selectionForNewTask` 只解析当前用户 ACTIVE `USER_BYOK`；没有 Credential 或状态为 DISABLED 时在 `OptimizationTask` 创建前抛出 `AI_CONFIGURATION_REQUIRED`。`resolveMaterial` 只解密当前用户 Credential，平台 Chat 配置不再作为正式 fallback。历史 `SYSTEM_DEFAULT` 保留数据库枚举和读取能力，但不创建新任务、不 Retry、不 Regenerate、不执行 AI Suggest；需要 AI 的恢复路径是新建岗位优化任务。简历解析中的章节分类、结构化补全、展示模型和指针抽取在 AI 不可用时继续返回已有规则结果或明确 fallback，不把上传 / 管理简历变成失败。

生产固定启用 Credential AES-256-GCM key ring，并由 Compose 与启动校验共同 fail fast；local/test 可以关闭。Embedding-compatible 仍是独立的内部语义检索 Provider，不能被当作 Chat / Generation fallback。
