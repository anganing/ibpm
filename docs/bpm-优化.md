# BPM 优化：当前状态与收口方案

> 核对日期：2026-09-25。适用范围仅为 `D:\iboot-pro\ibpm` 当前工作区的 `server`、`web` 和 MySQL `ibpm`。
> 前置背景：[BPM 开发任务](./bpm-development-tasks.md)、[SaaS 化审批平台集成设计](./bpm-saas-integration.md)。前一期已验收的发起、审批、快照和第三方接口继续有效；本文只管理其后的 BPM 菜单与管理界面优化。

## 1. 先看结论

本轮优化的主要前后端代码已写入两个仓库的**未提交工作区**，但尚未集成验收。T1—T10 的原交付记录是开发线索，不等同于本轮上线结论。T11（数据库、菜单、权限、前端 API 收口）和 T12（完整门禁、数据库核对、业务联调）仍未完成。

| 核对项 | 2026-09-25 实况 | 证据 |
|---|---|---|
| 数据库变更 | `bpm:4/5` 已由 Liquibase 在 MySQL 执行；分类表已建且当前 0 行，七个页面菜单已落库；PostgreSQL 脚本仅静态核对 | DBX `local-mysql-3306` / `ibpm` 查询 |
| BPM 菜单 | 七个页面均可访问，但当前 `sort` 将“发起审批”排在第二位，与目标的末位不符；三个个人菜单的权限码也与下文契约不符 | DBX `sys_menu`；超级管理员与测试人员浏览器验收 |
| 角色授权 | 五个现有角色均有发起、分类只读和三个个人菜单；`10010`、流程表单未授普通角色。超管由特殊分支取得全部启用菜单 | DBX `sys_role_menu`；测试人员浏览器验收 |
| 权限数据结构 | 当前项目没有 `sys_api_permission` 表；该名称来自**另一个同名但内容不同的 ibpm 项目** | DBX `information_schema`、当前仓库菜单与权限代码；用户澄清 |
| 静态检查 | `pnpm typecheck` 通过；`pnpm lint` 在 `model/AddModal.vue` 有 3 个错误；BPM Maven 模块及依赖的 `compile` 通过 | 本轮命令结果；尚未运行完整 `pnpm build`、`mvnw verify` |
| 业务验证 | 发起审批分类树已用临时模拟数据验证选择、切换和再次点击取消；真实分类接口现返回 200 和空列表，端到端筛选未验证。“我的流程”行键已在真实接口数据下核对；超级管理员和测试人员的正式菜单可达性已验证 | 浏览器交互报告；DBX 核对空表、定义数据和菜单，T12 未执行 |

**事实优先级**：当前仓库代码和当前 `ibpm` 库的实查结果优先于历史任务自述。同名旧项目的表、权限和经验不得移植到这里。本文中的计划不是已完成证明。

## 2. 范围与目标

### 2.1 目标菜单

```text
流程审批（10000，目录）
├─ 流程模型（10010，原“流程设计”）
├─ 流程分类（新增）
├─ 流程表单（新增）
├─ 我的待办（新增）
├─ 我的已办（新增）
├─ 我的流程（新增）
└─ 发起审批（10020，原“审批工作台”）
```

`10010.component` 应改为 `bpm/model/index`；`10020.component` 保持 `bpm/approval/index`，其标题和页面权限码改为“发起审批”与 `bpm:approval:start`。新增页面分别指向 `bpm/category/index`、`bpm/form/index`、`bpm/todo/index`、`bpm/done/index`、`bpm/mine/index`。菜单由后端动态下发，前端不另建静态路由。

### 2.2 本轮交互

| 页面 | 目标 |
|---|---|
| 流程模型 | 左侧单级分类树，右侧 `GiTable` 分页列表，每个流程编码只显示最新版本；新增直接以无 ID 方式打开全屏 Warm-Flow 设计器（定义记录由设计器保存时创建），修改由后端定位应打开的定义 ID（已发布则复制出新版本草稿）；支持发布、暂停/恢复、版本记录（只读）、删除 |
| 流程分类 | 单级扁平列表和新增、修改、删除；提供设计器分类下拉的数据源 |
| 流程表单 | `GiTable` 分页管理；先创建表单记录，再以 `type=form&id=<表单ID>` 打开设计器；每行提供预览（抽屉只读展示真实内容，无保存入口）、设计、发布、删除，不提供停用 |
| 我的待办、我的已办、我的流程 | 三个独立菜单；待办可办理，已办和我的流程只读查看快照 |
| 发起审批 | 左侧分类树，右侧可发起的已发布模型；点击模型在全局弹窗中填写表单并提交 |

前端沿用项目的 `GiPageLayout`、`GiTable`、`GiForm` 和 `useTable`。本项目当前没有 i18n 目录或依赖，本轮中文文案直接写在页面中。发起时展示流程图不在本轮已定范围内。

## 3. 已确定的业务与数据契约

### 3.1 分类和设计器

分类只有一级，不做父子树。新表 `bpm_process_category` 至少包含雪花主键 `id`、`name`、唯一 `code`、`sort`、项目标准审计字段和逻辑删除字段 `deleted`；MySQL 与 PostgreSQL 的 SQL 应等价。该表没有 `tenant_id`，须同步加入 `server/continew-server/src/main/resources/config/application.yml` 的 `continew-starter.tenant.ignore-tables`，否则现有租户拦截器可能追加不存在的列。`code` 是流程定义的稳定引用键：`flow_definition.category` 保存 `code`，Warm-Flow `CategoryService.queryCategory()` 返回的 `Tree.id` 也必须是 `code`，`Tree.name` 为名称。

当前代码已有 `ProcessCategoryDO`、CRUD 服务和 `BpmCategoryServiceImpl` SPI 实现；分类表已在 MySQL 建立，但尚无分类数据，完整 CRUD 与设计器下拉仍待业务联调。分类被任一流程定义引用时不能删除或改 `code`。当前 10 条 `flow_definition` 的 `category` 均为空值或空串，本轮不迁移分类值。

设计器分类列表由 Warm-Flow 的 `CategoryService` SPI 注入 `query-def` 响应；仅创建分类 REST 接口无法让设计器下拉获得数据。新增流程模型以无 ID 方式打开设计器：`query-def` 不传 ID 时返回一份初始数据且不落库，定义记录由设计器保存时创建（`saveDef` 在定义 ID 为 null 时新建）。新增表单仍必须先有表单记录，再以 `?type=form&id=<表单ID>` 打开。

### 3.2 删除、停用、版本

- 流程定义仅在 **`is_publish=0` 且无实例** 时允许物理删除；`1`（已发布）和 `9`（失效）均不物理删除。这样可保留实例详情、表单绑定与历史快照的引用。
- **「最新版本」与「当前生效版本」是两个不同口径**，模型页与接口均须区分：
  - **最新版本**：同一 `flow_code` 下版本号最大的定义，主列表每行即代表它。版本号按数值比较（`Integer.parseInt`），不按字符串字典序。
  - **当前生效版本**：发起链路实际选中的那一版，由 Warm-Flow 的 `getPublishByFlowCode(flowCode)` 决定，**必须复用该方法判定**，不可在宿主侧另立「取版本号最大者」的规则，否则会出现「列表显示 A、实际发起走 B」。注意该方法无版本排序（`getOne` 退化为 `list.get(0)`），靠 `publish` 收敛同编码的其他已发布版本（有实例→`9`，无实例→`0`）来保证唯一。
  - 两者可以不同：最新版本是未发布草稿时，上一已发布且激活的版本仍在生效、仍可发起。列表需同时展示，避免用户误判「该流程不可发起」。
- **`9` 是 Warm-Flow 原生的「已失效」状态**（`PublishStatus.EXPIRED`），既可能来自人工失效，也会由 `publish` 自动降级产生（同编码旧的已发布版本若已有实例则降为 `9`）。历史 `9` 数据不迁移、不清理，只读展示，**不提供恢复或重新发布入口**。
- 流程的启停改为**「暂停 / 恢复」**，只作用于**当前生效版本**，且只拦截**新实例发起**：
  - 状态保存在宿主侧独立表 `bpm_process_pause`（键为 `definition_id`），**不使用 Warm-Flow 的 `activity_status`**。原因：`activity_status=0` 会被引擎同时用于拒绝新发起（`InsServiceImpl#start`）与拒绝**在途实例**的任务办理（`TaskServiceImpl#judgeActivityStatus`），用它做暂停会连带冻结在途审批任务。
  - 暂停标记按定义 ID 存储，复制出的新版本是新 ID，因此**不继承**旧版本的暂停标记；发布切换生效版本后旧标记自然失效。
  - 已有的 `is_publish=9` 不得当作可恢复的「暂停」状态。
- 表单只有「未发布（0）」「已发布（1）」两种业务状态，**不提供停用，也不再产生 `9`**；已发布表单不能物理删除。未发布表单被任一流程定义的 `form_path` 引用时也不能删除，前后端规则一致：前端只禁用“已发布”，是否被引用由后端在 DELETE 时校验并拒绝。历史数据中残留的 `9` 不迁移、不清理，按未发布处理，已执行的 changeset 不做修改。
- `form_code` 允许同编码多版本。“新增”入口仅检查表单名称不重复，该检查不作用于“设计”生成的新版本（复制同名新版本不会被拦截）。新版本号由 Warm-Flow 的 `FormService#save` 按同编码最大数字版本 +1 计算并**持久化**，前端不自行拼版本号；已发布表单点“设计”时先复用同编码已有的未发布草稿，没有草稿才复制，且由后端加锁防止并发重复创建。表单已无失效状态。
- 流程模型点「修改」时与表单同构：未发布草稿直接编辑自身；已发布定义由后端 `POST /admin/approval/process/{id}/design` 复制出同编码下一版本未发布草稿（完整复制定义、节点、连线与表单绑定），再用新 ID 打开设计器，原已发布内容不变；已有未发布草稿时复用该草稿，重复点击不会不断产生新版本。
- 继续保留前一期完整 `{rule, option}` 表单快照及提交数据。审批详情从快照读取，不反查当前表单；本轮不得破坏第三方签名发起与查询、内部办理资格和默认租户边界。

### 3.3 当前接口契约

| 模块 | 接口 | 数据与约束 |
|---|---|---|
| 分类 | `GET /admin/approval/category/list`；`POST /category`；`PUT/DELETE /category/{id}` | 列表全量、不分页；分类 ID 以字符串返回；设计器用 `code` |
| 流程模型 | `GET /admin/approval/process/page`；`POST /process`；`POST /process/{id}/design`；`POST /process/{id}/pause`、`/resume`、`/publish`；`GET /process/{id}/versions`；`DELETE /process/{id}` | 分页参数 `page/size`，可按名称、编码、分类、发布状态过滤；创建返回字符串定义 ID。列表按「每个流程编码只取版本号最大的定义」收敛，`total` 为**流程编码数**而非历史版本行数；发布状态筛选作用于最新版本自身状态。响应含 `effectiveId`/`effectiveVersion`（当前生效版本，可能非最新）与 `paused`（宿主侧暂停标记）。`/design` 返回设计器应打开的定义 ID（未发布=自身，已发布=复用或复制出的下一版本草稿）；`/pause`、`/resume` 只作用于当前生效版本，只拦截新发起；`/versions` 只读返回该编码全部版本；**不再提供 `/process/{id}/disable`**，历史 `is_publish=9` 仅只读展示、不可恢复 |
| 流程表单 | `GET /admin/approval/form/page`；`POST /form`；`POST /form/{id}/design`；`GET /form/{id}/content`；`DELETE /form/{id}`；`POST /form/{id}/publish` | `/form/list` 已被分页接口取代；响应含 `list/total`，表单 ID 以字符串返回。`/design` 返回设计器应打开的表单 ID（未发布=自身，已发布=复用或复制出的未发布新版本，版本号由后端持久化）；`/content` 返回只读预览所需的 `{rule, option}`；**不再提供 `/form/{id}/disable`** |
| 我的待办 | `GET /admin/approval/task/page` | 沿用现有接口与 `TaskPageResp` |
| 我的已办 | `GET /admin/approval/task/done/page` | 当前用户为历史任务 `approver`；按办理时间倒序 |
| 我的流程 | `GET /admin/approval/instance/mine/page` | 以 `bpm_form_snapshot.create_user` 为发起人依据；按实例创建时间倒序 |
| 发起审批 | `GET /admin/approval/definition/list?category=`；`GET /definition/{flowCode}/form`；`POST /admin/approval/instance` | 仅展示已发布、激活且绑定已发布表单的定义；不传分类时保持原列表行为 |

待办、已办、我的流程沿用 `TaskPageResp { list, total }` 与 `TaskResp` 字段，但“我的流程”行的 `taskId` 为 `null`，前端必须使用 `instanceId` 作行键。前端十进制长 ID 使用字符串，后端相应响应要保持字符串序列化。

## 4. 当前项目的权限机制

### 4.1 事实与落库方式

当前项目**不存在 `sys_api_permission`，也不存在接口权限登记表**。误写来自另一个同名 ibpm 项目，与本项目的 `CrudApiPermissionPrefixCache` 无关。当前项目中，`MenuMapper.xml#selectPermissionByUserId` 经 `sys_menu`、`sys_role_menu`、角色和用户关联取得非空 `permission`；超管由 `RoleServiceImpl#listPermissionByUserId` 返回 `*:*:*`。动态页面路由由已授权菜单构成，按钮由前端 `v-permission` 判断，标注了 `@SaCheckPermission` 的接口由后端校验对应权限码。

页面菜单是 `sys_menu.type=2`；操作权限通常建为其下的 `type=3` 按钮菜单，赋予角色时在 `sys_role_menu` 增加关联。**菜单权限码不会自动给所有接口加鉴权**：部分审批运行时接口没有 `@SaCheckPermission`，其可访问性还取决于登录拦截和服务内的本人、办理人校验。不要把“有页面菜单”误写成“接口有同名权限校验”。

`sys_menu` 的唯一索引为 `(title, parent_id, deleted)`。同一父菜单下按钮标题要唯一。已执行的 `bpm:1/2/3/4/5` 不可修改，只能用后续 changeset 修正菜单并新增记录。

### 4.2 需要落地的权限码

| 页面菜单码（`type=2`） | 该页面下需要的按钮码（`type=3`） |
|---|---|
| `bpm:designer:view` | `bpm:designer:create`、`update`、`delete`、`publish` |
| `bpm:category:view` | `bpm:category:create`、`update`、`delete` |
| `bpm:form:view` | `bpm:form:create`、`update`、`delete`、`publish` |
| `bpm:approval:todo`、`done`、`mine`、`start` | 本轮没有对应操作按钮码；它们主要用于菜单与前端权限集合 |

操作按钮共 **11 个不同权限码**（4+3+4），不因“修改/停用”共用 `update` 而重复建行。`10020.permission` 已改为 `bpm:approval:start`，原有 `sys_role_menu` 关联仍在，但不再提供旧的 `bpm:approval:view` 字符串。本轮运行时代码没有接口消费旧码，文档不再宣称“保留旧码给待办”。`bpm:5` 将三个个人菜单写成了 `bpm:todo:view`、`bpm:done:view`、`bpm:mine:view`，与上表契约不符，须用新变更集修正。

### 4.3 分类读取的跨页面依赖

`GET /admin/approval/category/list` 当前标注 `@SaCheckPermission("bpm:category:view")`，流程分类、流程模型和发起审批三个页面都会调用。给用户模型或发起菜单而不给分类读取码，左侧分类树会 403。

T11 落授权前必须明确取舍：

1. **最小改动方案**：需要模型或发起页面的角色同时获授“流程分类”页面菜单；可读取分类，也会在侧边栏看到分类管理页。增删改按钮仍由独立权限控制。
2. **拆分读取码方案**：另设独立的只读权限码并修改分类列表接口与对应菜单/按钮授权；可避免显示分类管理页，但超出纯 SQL 集成，需同步修改接口契约与代码。

本轮已选择最小改动方案：原有五个 `10020` 角色均获授分类只读页及三个个人页面，浏览器已用测试人员角色验证；流程模型和表单未扩大普通角色授权，增删改权限也未授予。超管虽不依赖普通角色菜单授权取得路由，仍应检查后续按钮权限集合。

## 5. 已发现的待修问题

| 优先级 | 问题与根因 | 验收点 |
|---|---|---|
| P0，部分完成 | 页面菜单及当前角色授权已落库并经超管、测试人员浏览器验证；当前排序与目标顺序不符，个人菜单权限码与契约不符 | 后续变更集修正 `sort` 和三个权限码；不修改已执行的 `bpm:5` |
| P0，模拟验证通过 | `approval/tree/index.vue` 已改用分类 `code` 作树 key；Arco 单选重复点击时由宿主主动清空选中态并发出空分类 | 目标文件 ESLint、全项目 typecheck 通过；临时模拟数据下选择、切换、取消均通过；真实接口已返回空列表，仍需补真实数据联调 |
| P0，组件验证通过 | `mine/index.vue` 已用 `instanceId` 作表格行键；真实接口的 9 行 `taskId` 全为空、`instanceId` 互不相同 | 目标文件 ESLint、全项目 typecheck 通过；正式菜单现可达，行键和翻页交互仍待正式页面复测 |
| P0 | `model/AddModal.vue` 的无用 `v-bind` 和编码正则触发 3 个 ESLint 错误 | `pnpm lint` 通过，编码规则语义不变 |
| P1 | 新的 `apis/bpm/*.ts` 尚未统一导出；直接通配导出 `approval.ts` 会与旧 `bpm/index.ts`、`bpm/type.ts` 重名 | 统一函数与类型来源，`pnpm typecheck` 和 build 通过 |
| P1 | 模型页“暂停/恢复”按钮需 `bpm:designer:update`（已加前端控制）；已办页“办理”按钮实际只读 | 无权限者不出现可点后报 403 的暂停按钮；已办文案改“查看” |
| P1 | 表单已发布即不可删除、未发布删除时由后端校验 `form_path` 引用（本轮已收口，状态简化为未发布/已发布）；两个设计器宿主及分类 API 有重复实现 | 前后端同规则，不为整洁而改动尚无问题的链路 |
| 待定 | 已办是否包含 `node_type=0` 起始记录、发起时流程图展示 | 先记录现行为；业务确认后再改契约 |

> 流程模型失效后是否重新发布**已确定**：历史 `is_publish=9` 只读保留，不提供恢复或重新发布入口（见 §3.2）。

流程模型或表单列表当前采用 Warm-Flow 全量查询后内存过滤与分页，与既有待办实现一致。本轮先保证正确性；数据量增长时另评估数据库分页，不在本轮提前重构。

## 6. 按优先级逐项收口

主 Agent 每次只安排一个有明确验收点的任务，复核文件、命令或 DBX 证据后再进入下一项。T1—T10 的代码交付不能替代下列收口。

| 顺序 | 单项任务 | 完成标准 |
|---|---|---|
| 1 | 确认权限落库事实与角色授权原则 | 无旧项目 `sys_api_permission` 假设；明确分类跨页读取方案、普通角色应获得的菜单与按钮 |
| 2 | 修复确定性的前端契约错误 | 分类树使用 `code`、我的流程使用 `instanceId` 行键、模型新增弹窗 lint 通过 |
| 3，MySQL 已完成 | 新建分类表变更集 | MySQL `bpm:4` 已执行且 DBX 核对表结构；PostgreSQL SQL 已提供但未运行验收；分类表仍为空 |
| 4，部分完成 | 菜单与授权变更集 | `bpm:5` 已执行，页面可达；排序和个人菜单权限码待修正，11 个操作按钮码尚未落库 |
| 5 | 前端 API 和页面收口 | 聚合导出无重名冲突；操作权限与按钮文案正确；检查 T8/T9 交付而非只看自检 |
| 6 | 完整质量与业务验收 | `pnpm lint && pnpm typecheck && pnpm build`、`mvnw.cmd verify`；DBX 和浏览器验证完整主链路 |

SQL 变更遵循 `server/AGENTS.md`：数据库结构与正式菜单数据走 Liquibase；`mysql/` 与 `postgresql/` 两套脚本同步提供。当前 master 的 PostgreSQL include 仍被注释，因此 PostgreSQL 只能称为**静态提供，未运行验收**，除非本轮另行实际运行。数据库查询使用 DBX 连接 `local-mysql-3306` 且显式指定 `database=ibpm`。现有测试流程与实例不清理。

## 7. 最终验收与报告口径

- **构建门禁**：前端 lint、typecheck、build 全绿；后端完整 `mvnw.cmd verify` 全绿。Maven 默认跳过单测，`verify` 成功不能代替业务联调。
- **数据库**：核对新增表、`bpm:4+` 记录、菜单标题和 `component`、按钮权限码、角色菜单关联；已执行的 `bpm:1/2/3` 无改动。
- **业务主链路**：创建分类 → 新建流程模型（直接打开设计器，保存时创建定义）→ 设计流程并绑定已发布表单 → 发布 → 按分类找到模型并发起 → 审批人待办办理 → 已办和我的流程只读查看。验证旧实例详情仍从快照读取。流程表单侧另需验证：预览为只读且无保存入口；未发布表单“设计”仍为原 ID；已发布表单“设计”产生新 ID、版本号 +1、状态未发布、内容已复制，保存新版本后原版本内容不变，重复点击不产生多个草稿。
- **权限视角**：至少用超管和一个非超管角色核对菜单可见、分类树读取、按钮显隐及后端权限拒绝；不能只测超管。
- **交付报告**：逐项给出文件、命令结果与 DBX 查询证据。代码存在、静态检查通过、数据库迁移完成、业务联调通过是四种不同状态，不可混写。若环境阻碍某一步，记录准确阻碍和已完成范围。

## 8. 尚需确定的业务选择

1. 流程模型、流程表单的普通角色管理权限以及 11 个操作按钮应分别授给谁；现有五个角色已获发起、分类只读和三个个人页面。
2. 分类跨页面读取已采用第 4.3 节的最小改动方案，普通角色能看到只读的“流程分类”；如需隐藏该入口，需另行修改契约和代码。
3. ~~失效流程是否允许重新发布。~~ **已确定**：历史 `is_publish=9` 只读保留，不提供恢复或重新发布入口；流程的启停改为「暂停/恢复」，只作用于当前生效版本且只拦截新发起（见 §3.2）。流程表单已无失效状态：已发布不可删除，未发布删除时由后端检查是否被流程定义引用，不再有待定项。
4. 已办列表是否包含 `node_type=0` 的起始历史记录。
5. 发起审批时是否展示 Warm-Flow 流程图；当前不作为本轮验收前置条件。

上述选择需要在对应实现或授权 SQL 落地前统一口径；未决定的内容不应写成“已验收”。
