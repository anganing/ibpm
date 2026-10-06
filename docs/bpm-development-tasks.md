# BPM 开发任务与多 Agent 协作安排

> 日期：2026-09-23
> 工作区：`D:\iboot-pro\ibpm`
> 状态：T0、T1 已完成 MySQL 验证；T2 的设计、绑定、发布和选人已联调；T3 已实现并真实跑通内部发起、第三方签名发起、待办、通过、驳回、快照和状态查询，后端提交 `2babcc30`。T4 审批工作台的发起、待办、只读详情、通过与驳回已在真实浏览器和数据库验证，前端 `ef8d2b1` 已提交，lint、typecheck、build 通过。T5 的鉴权边界、跨应用隔离、默认租户与持久化契约、共享入口和菜单均已核对收口；BPM API 聚合导出 `0844e66`、审计时间适配 `95295465` 与类型保护修正 `16f90827` 已提交并完成相应验证。T6 非超管菜单、宿主接口与插件设计器写权限已在新进程验证；权限修复提交 `640f6807`、`2f7eaa9a`。新版本发布后旧实例快照稳定性、MySQL 重复迁移和发起失败回滚已联调；本期约定的本机生产构建与 Windows Nginx 18081 部署联调已通过。PostgreSQL 运行验证按用户决定暂缓。
> 设计依据：[SaaS 化审批平台集成设计](bpm-saas-integration.md)。本文件集中记录任务、数据库调试约定、协作边界及验收结果，不另拆任务文档。

## 1. 本期范围与共同约定

- 实现流程设计、表单设计、本系统及第三方发起、待办审批、详情和状态查询。
- 经典（`CLASSICS`）和仿钉钉（`MIMIC`）流程模型均可使用；表单全部使用 form-create，业务接口不按模型过滤。
- 第三方直接提交本系统用户 ID；复用 `sys_app` 和现有 AK/SK 验签，不增加用户映射或应用与流程授权表。
- 第三方只开放发起、查询接口；内部审批与设计器业务接口拒绝带 `sign` 的请求，正常请求走登录及权限校验。
- 仅使用默认租户，处理好现有拦截器所需的上下文和表白名单，不建设多租户能力。
- 表单快照保存完整 `{ rule, option }` 和提交数据，审批详情不反查业务系统。
- **回调相关只定义 `bpm_app_ext` 表**。不开发回调配置页面、CRUD、监听器、发送服务、通知报文、记录表、重试或流程级回落逻辑。
- 不因本任务改造现有异步框架，不为将来需求创建空接口、空实现或预留字段。
- 所有 Agent 先读所属目录的 `AGENTS.md`；每次修改文件前重新读取最新内容。回复使用简体中文，称呼用户“老大”。

## 2. 数据库查询与调试：统一使用 DBX MCP

| 项目 | 固定约定 |
|---|---|
| 查询工具 | **DBX MCP** |
| 连接名称 | **`local-mysql-3306`** |
| 数据库 | **`ibpm`** |
| 用途 | 查询表结构、验证迁移结果、检查流程实例/任务/快照、定位联调问题 |
| 首选连接参数 | `connection_name: "local-mysql-3306"` |
| 数据库参数 | 每次显式传入 `database: "ibpm"`，不依赖连接默认库或上次 `USE` 状态 |

### 2.1 Agent 操作顺序

1. 发现并使用 DBX MCP 工具。需要确认连接时调用 `dbx_list_connections`，按名称选择已有连接，不新建连接或猜测账号密码。
2. 查询前用 `dbx_get_schema_context` 或 `dbx_describe_table` 确认实际表名、列名；Liquibase 记录表也以库内实际名称为准。
3. 通过 `dbx_execute_query` 执行带明确条件的查询。优先按本轮 `instanceId`、`taskId`、`businessId` 定位，返回必要列并限制行数。
4. JSON 被截断时，先限定到目标行，再调高 `cell_char_limit` 或按 `cell_char_offset` 读取，不将截断结果误判为数据缺失。
5. DBX 不可用或找不到指定连接时，记录具体阻塞并继续不依赖数据库的任务；不要擅自换库或改用本地客户端、脚本直连。

工具参数示例（连接配置无需写入仓库）：

```json
{
  "connection_name": "local-mysql-3306",
  "database": "ibpm",
  "table": "sys_app"
}
```

上例用于 `dbx_describe_table`。查询示例用于 `dbx_execute_query`：

```json
{
  "connection_name": "local-mysql-3306",
  "database": "ibpm",
  "sql": "SELECT DATABASE() AS current_database",
  "max_rows": 1
}
```

本文件已核对 DBX 工具及上述参数名称；后续已连接指定数据库，迁移验证记录见第 7、8 节。

### 2.2 查询调试与数据库变更的分工

- DBX 用于查询和检查，不用手工 DDL 代替 Liquibase，也不手改审批状态来模拟业务成功。
- 表结构、菜单和初始化数据的正式变更进入 Liquibase。业务测试数据通过本系统接口或页面产生。
- 测试时使用容易识别的业务单据号，并记录对应实例和任务 ID，方便其他 Agent 复查。
- 多个 Agent 可以分别查询；连接同一 `ibpm` 库的后端启动、迁移和有写入的联调由统筹统一安排，避免同时执行同一测试。

## 3. 角色与文件归属

采用 **1 个统筹 Agent + 3 个执行 Agent**。角色是后续执行时的分工，本次不创建开发任务或启动子 Agent。

| 角色 | 主要职责 | 独占修改范围 |
|---|---|---|
| Agent L：统筹与集成 | 模块骨架、公共契约、依赖、鉴权入口、公共配置、集成验收 | 本文件；所有 POM；`SaTokenConfiguration`；`application*.yml`；Liquibase master；公共 API；前端依赖、入口与聚合文件 |
| Agent A：数据库与设计器 | 双库脚本、Warm-Flow 接入、流程/表单设计与发布、选人适配 | BPM 插件的 `design/`、引擎专用 `config/`；MySQL/PostgreSQL 的 `plugin_bpm.sql` |
| Agent B：审批后端 | 发起、快照、待办、审批、详情和第三方查询 | BPM 插件的 `runtime/`，含业务 Controller、Service、Mapper 和实体；对应 Mapper XML |
| Agent C：前端 | 设计器嵌入、填表发起、待办与审批详情 | `web/src/apis/bpm/`、`web/src/views/bpm/`、`web/src/components/ApprovalForm/` |

建议 BPM 独立模块为 `server/continew-plugin/continew-plugin-bpm`，Java 包为 `top.continew.admin.bpm`，下面用 `design/`、`runtime/` 区分 A/B 的文件归属；包内沿用项目 Controller/Service/Mapper 等分层。以上新增路径在 T0 建立，不视为仓库现有实现。

L 负责的共享文件包括：

- 根、插件聚合、BPM 和 server 的 POM；按现有 Maven 聚合层级注册，根 `dependencyManagement` 管理内部模块版本。
- `server/continew-server/src/main/java/top/continew/admin/config/satoken/SaTokenConfiguration.java`。
- `server/continew-server/src/main/resources/config/application*.yml`。
- `server/continew-server/src/main/resources/db/changelog/db.changelog-master.yaml`。
- `server/continew-common/.../common/api/` 中本次确有需要的公共接口，以及对应模块实现。
- BPM 中 A/B 共用的 DTO 和服务接口，T0 固定路径与方法签名。
- `web/package.json`、`web/pnpm-lock.yaml`、`web/src/main.ts`、`web/src/apis/index.ts`；确有必要时的 Vite 配置和自动生成类型文件。

需要修改他人范围时，先把所需变更发给负责人，由负责人修改或明确移交文件；不要多个 Agent 同时编辑同一文件。新增文件也要遵守归属，不用事后覆盖解决冲突。

## 4. 执行顺序与并行安排

```text
T0：L 确定契约、建立模块骨架和依赖
 ├─ T1：A 数据库与引擎基础 → T2：A 设计器后端
 ├─ T3：B 按契约开发审批后端；T1 完成后运行验证
 └─ T4：C 按契约开发前端；T2/T3 完成后接入真实接口
              ↓
T5：L 集成；A/B/C 各自修复所属问题
              ↓
T6：L 组织完整业务验收与门禁
```

- T0 完成前，A/B/C 可并行只读检查现有代码、构件和接口，不先各写一套数据模型。
- T1 完成前，B 可开发 DTO、Service 和接口逻辑，但不能宣称数据库或引擎链路已通过。
- T4 可用少量本地样例验证表单组件；任务完成必须换成真实 API，不新增一套长期 Mock 服务。
- 同一工作区内统一依赖安装和构建，避免多个进程同时改锁文件、格式化全仓库或触发数据库迁移。

## 5. 任务清单

### T0 — 公共契约与模块骨架

**负责人：L。依赖：无。**

- [x] 阅读设计稿、前后端 `AGENTS.md`，确认本期范围与依赖引入要求；已有明确决策不重复询问。
- [x] 创建 BPM 模块骨架，接入 Maven 聚合、内部依赖管理和 server 启动依赖。
- [x] 按 Warm-Flow 1.8.9 选择 Spring Boot 3 / MyBatis-Plus 对应构件；根 POM 仅管理四个实际使用的 Warm-Flow 构件版本，模块 POM 不单独写版本，避免覆盖 Starter 的 Jakarta 依赖版本。
- [x] 前端采用设计器兼容基线：`element-plus` 2.4.3、`@form-create/element-ui` 3.2.41；依赖已加入 package/lock 并在宿主入口注册 form-create 和 Element Plus，iframe 不注入组件库。
- [x] 固定 A/B/C 共用的请求字段、响应结构、分页字段、流程/表单读取接口，以及流程定义选择和发布方式。插件已提供的能力直接复用，只补宿主实际需要的接口。
- [x] 固定 BPM 表单快照表名为 `bpm_form_snapshot`；保持设计稿字段，`form_content_json` 保存 `{ rule, option }`。
- [x] 固定字符串 ID 传输约定，以及前端 `formContent`、`formData` 与数据库 JSON 字段的转换位置，避免前后端重复解析。
- [x] 明确第三方 `app_id` 来自验签应用；内部发起 `app_id=NULL`、发起人为当前登录用户。跨模块读取用户或应用信息遵循现有 `common.api` 方式，仅补实际需要的方法。
- [x] 本期默认不做 `businessId` 幂等控制，不增加幂等表或锁。
- [x] 在本文件第 8 节记录最终契约，供 A/B/C 按各自范围实现。

**交付与验收**：模块骨架可编译；共用方法签名及请求响应示例明确；三个执行 Agent 无需自行猜测接口。骨架阶段先验证已有验签查询；具体内部接口的拒绝行为在接口落地后验收，不要求尚不存在的接口提前通过联调。

### T1 — 数据库脚本与引擎基础

**负责人：A；共享配置由 L 修改。依赖：T0。**

- [x] 使用 DBX 查询 `local-mysql-3306` / `ibpm` 的实际表结构和 Liquibase 执行记录；未执行写入或迁移。
- [x] 核对 Warm-Flow 1.8.9 官方表结构；已编写本次所需 Liquibase DDL。
- [x] 编写 MySQL / PostgreSQL 两份 `plugin_bpm.sql`：引擎表、`bpm_form_snapshot`、`bpm_app_ext` 和设计器菜单。
- [x] `bpm_app_ext` 只交付表定义，不创建 Controller、Service、管理页面或通知相关类。
- [x] L 已在 master 登记 MySQL 脚本；PostgreSQL include 仍按仓库现有方式注释。
- [x] 当前 BPM changeset 为新增文件中的首个 changeset，未改动既有 changeset。
- [x] Warm-Flow 表保留引擎自带 `tenant_id`；无租户列的 `bpm_form_snapshot`、`bpm_app_ext` 加入租户忽略表配置，沿用默认租户，不新增租户映射。
- [x] 用户已启动后端并执行一次迁移；DBX 已确认 BPM 表和 Liquibase 执行记录。
- [x] 已用 DBX 核对 `bpm_form_snapshot`、`bpm_app_ext` 索引；后端重复启动后 `bpm:1`、`bpm:2` 均仍仅执行一次。新增工作台菜单 `bpm:3` 同样仅执行一次。

**交付与验收**：双库 SQL 均提供；MySQL 实际迁移和重复启动验证通过；PostgreSQL 未运行时明确写“仅静态检查”，不宣称双库均已实测。

MySQL 迁移经 DBX 确认 `bpm:1`、`bpm:2`、`bpm:3` 均为 `EXECUTED` 且各只有一条执行记录；`flow_form.create_by` 和 `update_by` 均存在。用户提供的完整 Warm-Flow 建表 SQL 只有 7 张流程表，与项目脚本及当前库逐表字段一致，不含 `flow_form`。PostgreSQL 版本按官方 FlowForm ORM 字段与 MySQL migration 等价转换，尚未运行验证。

### T2 — 流程与表单设计器后端

**负责人：A。依赖：T1；鉴权和公共配置由 L 配合。**

- [x] 接入设计器静态资源与插件自带 `warm-flow-ui/config`；Warm-Flow 配置使用 `Authorization` token 名称。
- [x] 核对设计器本轮调用的 `/warm-flow-ui/config`、`/warm-flow/listener-list`、`/warm-flow/query-def` 响应；已按 `ApiResult` 返回类型排除全局二次封装，浏览器实测三者响应体均为 `code: 200`。
- [x] 实测 `/warm-flow/save-json` 保存流程及 `/warm-flow/query-def/{id}` 重新读取成功；本轮测试记录为 `CLASSICS`，模型本身可用，但流程未发布且未绑定表单，暂不能用于发起联调。
- [x] 补齐最小表单管理入口：创建 `flow_form` 基础记录后，以设计器 `type=form&id=<已有表单ID>` 编辑；插件 `/warm-flow/form-content` 只更新已有记录。接口及 `FormPathService` 提交 `956b4403`；经 `bc072502` 修复宿主填充类型冲突后，创建、保存、重开、发布和已发布列表均已运行时验证。
- [x] 补齐流程发布入口；宿主调用 Warm-Flow 核心服务，提交 `8549935f`，运行时发布成功。
- [x] 已打通流程和表单创建、保存、读取、绑定及发布；可用流程和表单读取接口已由 T3 实现并实测。
- [x] 已对接用户选人并入库真实用户 ID；T3 真实发起后，`admin` 办理人的待办列表返回对应任务，办理后清空。
- [x] `/warm-flow-ui/**` 静态资源与初始化配置已加入免登录路径；`/warm-flow/**` 拒绝签名请求。
- [x] 设计器写操作校验相应管理权限；非超管对插件两个 POST 写接口均被拒，管理员可保存，表单内容 GET 仍可读取，见 T6 运行验收记录。
- [x] 产出已发布双分支流程 `t2_dual_path_test`（定义 `893307971583361110`，审批人 `test`）与 `t3_admin_dual_test`（定义 `893315636061552748`，审批人 `admin`），均绑定表单 `893280850785865743`。DBX 证实后一流程的出线为 `PASS` 与 `REJECT`，T3 真实办理结果也已分别验证。

**交付与验收**：C 的 iframe 可打开设计器，能保存、重新打开和发布流程及表单；B 能读取确定的已发布定义和完整表单。

### T3 — 发起、快照与审批后端

**负责人：B。依赖：T0 契约；运行依赖 T1，业务联调依赖 T2。**

- [x] 共用发起服务已实现，内部与第三方 Controller 分别取得当前登录用户及已验签应用身份。
- [x] 第三方发起绑定四个签名参数，校验可用流程、启用用户和 `businessId` 长度；有效签名请求已实际发起实例 `893314020788953093`。
- [x] 提交的 `formData` 同时进入引擎变量和快照；DBX 已核对两处数据一致。
- [x] 同一事务创建流程实例并写入完整快照；表单规则从已发布定义绑定的表单读取，实测内部/第三方实例均产生快照。
- [x] 状态查询、我的待办、审批详情、通过和驳回已实现并使用真实实例验证。
- [x] 第三方状态查询限定为同一验签应用，另一应用查询返回“审批实例不存在”；内部办理资格校验阻止未分配用户操作。
- [x] 审批办理人取当前登录用户；内部 BPM 路径的签名请求由 `SaTokenConfiguration` 显式拒绝，T5 已运行时反向验证。
- [x] 双分支流程真实办理通过：实例 `893316068569804801` 返回 `flowStatus=8,lastSkipType=PASS`；实例 `893316190980567053` 返回 `flowStatus=8,lastSkipType=REJECT`，不能仅用 `flowStatus=8` 区分结果。
- [x] 快照写入失败时引擎实例、任务和快照一同回滚，DBX 已按本次实例 ID 核对；正式发布新版本后旧实例仍使用原快照，见 T6 记录。

**交付与验收**：以下接口真实可用，返回契约与 C 一致；无第三方审批接口、回调接口或回调实现。

| 方法 | 路径 | 使用方 |
|---|---|---|
| POST | `/open/approval/instance` | 第三方发起 |
| GET | `/open/approval/instance/{id}` | 第三方查询状态 |
| POST | `/admin/approval/instance` | 本系统发起 |
| GET | `/admin/approval/instance/{id}` | 本系统审批详情 |
| GET | `/admin/approval/task/page` | 我的待办 |
| POST | `/admin/approval/task/{id}/approve` | 审批通过 |
| POST | `/admin/approval/task/{id}/reject` | 审批驳回 |

### T4 — 设计器嵌入与审批前端

**负责人：C；共享入口和依赖由 L 修改。依赖：T0 契约，真实联调依赖 T2/T3。**

- [x] 使用 Vue 3 Composition API、`<script setup lang="ts">`，复用项目 HTTP 封装及 Arco 页面组件。
- [x] 新增 `web/src/views/bpm/designer/index.vue`，按 `VITE_API_PREFIX ?? VITE_API_BASE_URL` 拼接 iframe 地址并编码令牌；开发环境经 `/dev-api` 代理，浏览器已验证页面与读取接口。
- [x] 设计器新建、编辑、表单保存和发布已在开发环境实测；生产构建的设计器入口已通过本期约定的 Windows Nginx 18081 部署联调。
- [x] 共用 `ApprovalForm` 已用真实设计器表单验证编辑、必填拦截、下拉选项与只读快照回显，原始规则未被改写。
- [x] 页面可选择已发布流程、读取动态表单并发起；浏览器得到字符串实例 ID `893394418407587896`、`893394603170873422`、`893395126280274015`。发起人可通过“查看详情”读取自己的只读快照。
- [x] 待办列表、详情、审批意见、通过和驳回已接真实 API；浏览器操作后待办清空，DBX 验证前两条实例分别为 `PASS`、`REJECT`。
- [x] 已与 SQL 菜单的 `bpm/designer/index` 路径和 `bpm:designer:view` 权限标识对齐，浏览器实际进入 `/bpm/designer`；未新增静态路由。
- [ ] 检查标签布局、输入控件和下拉弹层，确认表单配置正确还原且现有 Arco 页面未受影响。

**交付与验收**：管理员可设计流程与表单，普通用户可发起，审批人可在待办中查看快照并完成处理；提交与只读模式均通过实际页面验证。

### T5 — 集成与共享文件收口

**负责人：L；A/B/C 修复各自范围。依赖：T1—T4 交付。**

- [x] 收口公共 API、DTO、依赖版本、配置、菜单和前端聚合导出，清理临时样例与重复实现。
- [x] 在签名通用分支之前落实内部 BPM 路径限制；只调整本期路径，不重构全站权限体系。
- [x] 核对实体与 SQL 字段、JSON 序列化、ID 类型、审计字段及默认租户行为；PostgreSQL 仅静态比对，运行验证暂缓。
- [x] 为所有 Agent 安排同一后端实例和数据库迁移时段；集中记录联调使用的用户、流程和业务单据 ID。
- [x] 跟踪失败根因并分派给对应负责人，修复后验证相关链路，不用绕过校验或手改数据库使测试通过。

**交付与验收**：真实前后端联通，没有“待另一个 Agent 补接口”的占位实现；共享文件只有统一后的最终版本。

### T6 — 业务验收与质量门禁

**负责人：L；A/B/C 提供证据并处理失败。依赖：T5。**

| 检查项 | 通过标准 |
|---|---|
| 数据迁移 | DBX 确认目标为 `ibpm`，表结构和记录正确，重复启动不重复初始化 |
| 设计与发布 | 流程、表单可保存和重新读取，实际发布后可用于发起 |
| 内部发起 | 登录用户填写表单成功，实例与完整快照一致 |
| 第三方发起与查询 | 真实签名请求成功，来源应用正确，只能查询对应来源的实例 |
| 内部接口边界 | 有效应用签名也不能调用内部审批或设计器业务接口；正常登录按权限可用 |
| 审批处理 | 真实审批人收到待办，通过/拒绝路径均核对实例及任务结果 |
| 快照还原 | `rule`、`option`、数据完整；新设计不改变旧实例详情 |
| 范围检查 | 回调仅有 `bpm_app_ext` 表，无通知服务、回调记录表、重试或配置页面 |

本期以 MySQL `ibpm` 运行验收为准；PostgreSQL 脚本保留静态核对结果，按用户决定暂不做运行验证，也不以此阻塞 T6 收口。

由 L 协调在对应目录执行：

```powershell
# D:\iboot-pro\ibpm\server
.\mvnw.cmd verify

# D:\iboot-pro\ibpm\web
pnpm lint
pnpm typecheck
pnpm build
```

后端 Spotless 失败时按项目规则运行 `mvnw.cmd compile -Pformat` 后再验证；前端需要全局修复时由 L 统一安排。当前 POM 配置了跳过单元测试，`verify` 成功不等于业务测试已运行，交付时分别说明门禁和实际联调结果。仅编写本任务文档无需执行这些构建命令。

## 6. Agent 交付格式

每个执行 Agent 完成后向 L 提供：

1. 完成的任务编号及修改文件。
2. 可供下游使用的接口、方法或数据库结构，以及必要的请求响应示例。
3. 实际运行的命令、页面或接口验证结果；未验证项及具体原因。
4. DBX 查询涉及的连接、数据库、业务 ID 和结论；不提交密钥、令牌或无关数据。
5. 需要 L 修改的共享文件及准确变更内容。

本文件由 L 单独更新状态。任务有阻塞时继续可独立推进的部分，不将“编译通过”写成“业务完成”。

## 7. 任务状态

| 编号 | 负责人 | 状态 | 依赖 | 验收记录 |
|---|---|---|---|---|
| T0 | L | 已完成 | 无 | 契约见第 8 节；原模块已通过 Maven verify，本次边界调整通过含 Spotless、Checkstyle 的 Maven compile；运行时待 T5 验证 |
| T1 | A | MySQL 已验收 | T0 | `bpm:1`、`bpm:2`、`bpm:3` 已在 MySQL 执行且重复启动未重复初始化；官方流程表字段、`flow_form` 补列及索引已核对；PostgreSQL 仅静态检查，运行验证暂缓 |
| T2 | A | 已联调 | T1 | 表单 `893280850785865743` 已发布；流程 `893315636061552748` 已绑定该表单与真实办理人、发布并用于 PASS/REJECT 实例联调 |
| T3 | B | 已验收 | T0；运行需 T1/T2 | 后端 `2babcc30`；内部与签名应用发起、待办、通过、驳回、快照、状态查询、应用隔离、非办理人拒绝均已实测；正式发布新版本后的旧实例快照稳定性与快照写入失败回滚已补验 |
| T4 | C | 核心链路与本地生产入口已联调 | T0；真实联调需 T2/T3 | iframe、`ApprovalForm`、审批工作台均在 `dev` 提交；真实浏览器完成发起、发起人只读详情、待办、通过和驳回，数据库结果相符。前端 `30c4a63` 配置生产 `/api` 同源入口，本地 Nginx 18081 已验证设计器渲染与鉴权头；标签布局及其余界面检查待做 |
| T5 | L | 已完成 | T1—T4 | 鉴权、应用隔离、默认租户及实体/SQL/JSON/ID 已核对；前端聚合导出 `0844e66`、后端审计时间修正 `95295465`/`16f90827` 已验证。最终共享文件、依赖、菜单和动态路由核对无缺陷；普通用户权限视角已在 T6 补验，本地生产入口已在 T6 联调 |
| T6 | L | 已验收 | T5 | 非超管设计权限、V1/V2 快照稳定性和发起失败回滚已实测；MySQL 迁移记录、菜单、索引和回调交付范围已复核。本期约定的 Windows Nginx 18081 生产构建同源部署联调通过；T4 标签布局检查仍单独待做。PostgreSQL 运行验证暂缓、不阻塞本期 |

## 8. 执行记录与契约定稿

以下内容执行时更新在本文件，不另建任务追踪文档：

| 项目 | 当前约定 / 待补充记录 |
|---|---|
| 数据库查询与调试 | DBX MCP；`local-mysql-3306`；`ibpm` |
| DBX 迁移验证 | 2026-09-24 复核 `local-mysql-3306` / `ibpm`：`databasechangelog` 的 `AUTHOR=bpm`、`ID=1/2/3`（对应 `bpm:1/2/3`）各 1 行、`EXECTYPE=EXECUTED`；10 张 BPM/引擎表存在，快照及应用扩展表索引齐全，菜单 10000/10010/10020 各 1 行，10000/10020 各授予 5 个角色；结合此前重复启动记录，未见重复初始化。MySQL 已验收；PostgreSQL 运行验证暂缓，不能记为通过 |
| `flow_form` 字段核对 | 2026-09-23 对照用户粘贴的 7 张流程表 DDL、项目 MySQL 脚本和 DBX 中的 `ibpm`：这 7 张表列名一致；粘贴内容不含 `flow_form`。项目自建的 `flow_form` 曾缺 `create_by`、`update_by`，现经 DBX 确认已由 `bpm:2` 补齐 |
| 快照表 | `bpm_form_snapshot`；完整定义字段 `form_content_json` |
| 回调范围 | 2026-09-24 复核 MySQL/PostgreSQL 脚本均只定义 `bpm_app_ext`；MySQL 实表 9 列、索引齐全、当前 0 行，未见 BPM 回调记录/通知/重试表。BPM 后端模块和前端 BPM 页面/API 中未发现回调配置或发送实现；本期仍仅交付表定义 |
| 并行角色 | L 统筹；A 数据库/设计器；B 审批后端；C 前端 |
| 依赖与模块接入结果 | 后端 Warm-Flow 1.8.9 的四个实际构件由根 POM 统一管理；前端 Element Plus 2.4.3 与 form-create 3.2.41 已注册 |
| 设计器初始化与读取联调 | `ac475377` 按 `ApiResult` 返回类型排除二次封装；`a79e0ba` 提供 iframe 页面。2026-09-23 在 Codex 内置浏览器刷新后，`/warm-flow-ui/config`、`/warm-flow/listener-list`、`/warm-flow/query-def` 均为 HTTP 200、响应体 `code: 200`；后两个业务请求携带 `Authorization`。流程保存、重开、发布另见下条 |
| 流程保存与表单入口排查 | 2026-09-23 DBX 在 `local-mysql-3306` / `ibpm` 查到流程 `893224086832939080`（`t4_leave_test`）为 `CLASSICS`、`is_publish=0`、`form_custom=N`、`form_path=NULL`，`flow_form` 为 0 行。执行 Agent 报告 `/warm-flow/save-json` 与 `/warm-flow/query-def/{id}` 均返回 `code: 200`；没有表单保存成功的证据。插件源码确认 `type=form&id=<已有ID>` 编辑、`/warm-flow/form-content` 仅更新已有表单；创建和发布入口需要宿主补齐 |
| 表单设计与发布联调 | 2026-09-24，表单 `893280850785865743`（`t2_demo_form`）已通过创建、设计器保存、重新打开、发布和已发布列表验证。DBX 独立确认 `form_content` 长度 734，`rule` 有 2 个字段，`is_publish=1`；宿主填充类型冲突由提交 `bc072502` 修复，`mvnw verify` 通过。`flow_form.update_time` 异常见下方 T5 审计时间记录 |
| `ApprovalForm` 组件 | `a8ed6bf` 已在 `dev` 本地提交；接收已解析的 `{rule, option}` 和数据，支持编辑同步、校验及只读。先以本地样例验证，随后在 `ef8d2b1` 审批工作台中使用真实已发布表单，浏览器验证必填拦截、下拉选择、发起及只读详情回显；前端 lint、typecheck、build 通过 |
| 流程/表单接口及公共 DTO | 见下方 T0 契约；B 实现 Java DTO，C 建立对应 TypeScript 类型，A 复用插件设计器接口 |
| 状态查询响应 | 固定原始 `flowStatus` 与 `lastSkipType`；T3 核对实际通过/拒绝结果，不凭 `FlowStatus.isFinished()` 推断业务结论 |
| 幂等控制 | 本任务默认不实现；有新决定时由 L 更新 |
| 联调流程与业务 ID | 2026-09-24 使用设计器新建并发布 `t2_dual_path_test`，定义 ID `893307971583361110`，表单 ID `893280850785865743`。流程级 `form_custom=Y`、`form_path` 指向该表单；审批节点 `submit` 的办理人是 `test`（用户 ID `547889293968801822`）。DBX 核对 active 连线为 `start→submit PASS`、`submit→end PASS`、`submit→reject_end REJECT`，发布后 `is_publish=1`。应用 A 发起实例 `893314020788953093`，应用 B 发起实例 `893400974738079873`；原 `t4_leave_test` 流程保持不变 |
| T5 鉴权边界联调 | 2026-09-24：执行 Agent 用有效应用 A 签名查询自己的实例 `893314020788953093` 返回 `code=0`；应用 B 新建实例 `893400974738079873`，A/B 各自查询自身成功，交叉查询及查询内部实例 `893394418407587896` 均返回“审批实例不存在”。DBX 复核三份快照的 `app_id` 分别为 A=`893313600800710658`、B=`893314361412575248`、内部=`NULL`。A/B 签名请求 `/warm-flow/**`、`/admin/approval/**` 在验签前被路径规则拒绝；无签名且无登录态返回 401，管理员登录后相关读取接口可用。管理员非任务 `893314020851867656` 办理人，尝试通过/驳回均被拒，DBX 复核任务及实例仍为状态 1、节点 `submit`；没有生产代码改动。无设计权限用户的细粒度权限码拒绝尚未实测 |
| T5 聚合导出与审计时间 | 前端 `0844e66` 将 `bpm` 函数及类型加入 `src/apis/index.ts`，执行 Agent 报告 lint、typecheck、build 均通过。后端 `95295465` 添加 `LocalDateTime` 到 `java.util.Date` 的填充适配，`16f90827` 恢复适配后的可赋值性校验；Maven verify 通过。执行 Agent 在新进程用正式接口创建表单 `893476746744066050`，报告创建、保存内容、发布三个时点的 `update_time` 分别为 12:37:36、12:37:55、12:38:15；DBX 复核最终记录已发布、内容长度 68、`create_time=12:37:36`、`update_time=12:38:15`，并核对 `flow_instance` 8 行中 `update_time` 无空值。旧表单 `893437121744879811` 的时间仍等于创建时间，作为修复前对照；两份专用测试表单暂留，不直接改库清理 |
| T5 默认租户与持久化核对 | 执行 Agent 对照 BPM 自建两表、Warm-Flow 八表的 MySQL/PostgreSQL 脚本及实体，未发现本期字段不一致；八个响应 DTO 的 ID 序列化、快照与引擎表单数据比对通过。配置默认租户为 0，`bpm_form_snapshot`、`bpm_app_ext` 在 ignore-tables 中；DBX 独立复核 `flow_form` 3 行、`flow_definition` 4 行、`flow_instance` 8 行、`flow_user` 8 行均为 `tenant_id=0`，8 份快照的内容与数据 JSON 均合法。上述数据与默认租户过滤相容；本次未单独追踪 INSERT SQL，不能只据此断言 `tenant_id` 一定由拦截器写入。PostgreSQL 仅静态比对，非默认租户不在本期范围；无代码改动或提交 |
| T5 共享文件与菜单收口 | 执行 Agent 对照 Warm-Flow 1.8.9 的 POM 依赖管理、BPM 模块注册、common.api 复用、12 个接口及 DTO、前端 BPM API 聚合与动态路由，未发现缺失、重复实现或临时占位。DBX 独立复核菜单 10000 `/bpm` 重定向 `/bpm/approval`，10010 `bpm/designer/index`、权限 `bpm:designer:view`，10020 `bpm/approval/index`、权限 `bpm:approval:view`；10000/10020 各关联 5 个角色，10010 未分配普通角色，当前由超管使用设计器。执行 Agent 报告本轮前端 lint、typecheck、build 通过，无代码改动或提交；非超管视角已在 T6 补验 |
| T6 普通用户设计权限 | 执行 Agent 用非超管用户 `test`（ID `547889293968801822`）验证：菜单只下发 10000/10020，审批工作台和待办可用，直访 `/bpm/designer` 无路由，宿主表单/流程管理接口返回权限拒绝。旧进程中 `POST /warm-flow/save-json` 曾返回成功，确认插件写接口缺少设计权限。后端 `640f6807` 增加校验，`2f7eaa9a` 收窄到精确的两个 POST 写接口并保留表单内容 GET 读取；执行 Agent 报告新进程中 `test` 的两个 POST 均返回 `code=403`，GET 表单内容返回 `code=200`，管理员两个 POST 均可保存。DBX 复核专用未发布定义 `893550603903451148`、表单 `893550382096072709` 存在，已发布定义 `893224086832939080` 的更新时间未变；专用表单内容为 76 字节、72 字符。两份专用测试记录暂留，未直接改库清理 |
| T6 旧实例快照稳定性 | 执行 Agent 经正式接口创建同编码 V1/V2 流程定义 `893553449076015137`/`893553783559176247` 及不同设计的表单 `893553448086159389`/`893553782644817971`，分别发起实例 `893553659760099371`/`893554137457770560`；V2 发布后 V1 定义状态为 9、V2 为 1。执行 Agent 报告重读 V1 详情时 rule、option 和提交数据保持原值；DBX 独立核对两份快照 `893553660057894960`/`893554137675874373` 各指向对应定义，内容分别与各自表单一致（314/314、310/310 字符），提交数据分别为 V1、V2 字段。已发布表单不能经正式接口原地修改，故验收结论限定于支持的新版发布路径；未绕过发布规则。专用测试记录暂留，无代码改动或提交 |
| T6 发起失败事务 | 2026-09-24 临时 Spring 集成测试在默认租户 0 使用已发布流程 `t3_admin_dual_test`，让 `FormSnapshotMapper.insert` 在 Warm-Flow 已产生实例 ID `893577318654570497` 后抛异常；测试报告为 1 运行、0 失败/错误。DBX 独立核对该 ID 的 `flow_instance`、`flow_task`、`flow_his_task`、`bpm_form_snapshot` 及关联任务的 `flow_user` 均为 0 行，确认这条发起路径没有孤立引擎记录或快照。测试只覆盖快照插入失败这一真实事务切点；临时探针和 Maven 测试开关已清理，两个仓库生产代码无改动 |
| T6 本地生产入口联调 | 2026-09-24，前端 `30c4a63` 将生产 HTTP API 前缀设为 `/api`，后端仓库 `84ce10ac` 启用 Nginx `/api/` 反代并剥离前缀；前端 `fa16230` 将开源项目默认的 API 文档与 WebSocket 地址改为本机后端 8000，普通 HTTP API 仍使用 `/api`。用户报告本期约定的本机生产构建配合 `D:\iboot-pro\ibpm\nginx-1.30.5`（18081）已实测静态产物、history 回退、`/api/` 转发、设计器同源渲染与鉴权头，以及审批工作台普通 API；本轮重新构建后独立复查 `/bpm/designer`、`/api/warm-flow-ui/config`、`/api/warm-flow/query-def` 均返回 HTTP 200，产物中不再含 `api.continew.top`。本期生产部署验收以该本机 Nginx 环境为准，不另设公网域名验收门槛；API 文档页与 WebSocket 本轮未单独验收 |
| 构建与业务验收结果 | 后端 `2babcc30`、`2f7eaa9a` 均报告通过 `mvnw verify`；前端 `ef8d2b1` 通过 lint、typecheck、build。内部/签名发起、审批、快照、应用隔离及设计器权限边界已有真实联调，正式发布新版本后旧实例表单与数据稳定，快照写入失败时引擎数据回滚；本期约定的本机生产构建同源部署联调通过，PostgreSQL 运行验证暂缓 |

### 8.1 T0 接口契约

本系统业务接口统一使用现有响应封装 `{code, data, msg, success, timestamp}`；ID 在 HTTP JSON、路径和 TypeScript 中均为**十进制字符串**，Java 与数据库仍用 `Long`。列表分页沿用 `{list, total}`，请求参数为 `page`、`size`。前端只解析一次响应的 `data`，不对 ID 使用 `Number`。

| 方法与路径 | 请求 | `data` 关键字段 | 实现归属 |
|---|---|---|---|
| `GET /admin/approval/definition/list` | 无 | `[{id, flowCode, flowName}]` | B；只列已发布、激活且绑定可用表单的定义，经典和仿钉钉模型均可返回 |
| `GET /admin/approval/definition/{flowCode}/form` | 路径 `flowCode` | `{definitionId, flowCode, formContent:{rule,option}}` | B；使用该编码的已发布定义及其 `formPath` 指向的已发布内置表单 |
| `POST /admin/approval/instance` | `{flowCode, formData}` | `{instanceId}` | B；发起人为当前登录用户，内部 `businessId` 由服务生成 |
| `POST /open/approval/instance` | 签名参数 + `{flowCode, initiatorUserId, businessId, formData}` | `{instanceId}` | B；`businessId` 必填且不超过 40 字符 |
| `GET /open/approval/instance/{id}` | 签名参数 + 路径 ID | `{instanceId, flowCode, businessId, flowStatus, lastSkipType}` | B；只查当前验签应用发起的实例 |
| `GET /admin/approval/instance/{id}` | 路径 ID | `{instanceId, definitionId, flowCode, flowName, businessId, flowStatus, lastSkipType, initiatorUserId, formContent:{rule,option}, formData}` | B；按发起人或实际办理资格控制读取 |
| `GET /admin/approval/task/page` | `page`、`size` | `{list:[{taskId, instanceId, flowCode, flowName, nodeName, createTime}], total}` | B；只查当前登录用户的待办 |
| `POST /admin/approval/task/{id}/approve` | `{message}` | `{instanceId, flowStatus, lastSkipType}` | B；办理人取当前登录用户 |
| `POST /admin/approval/task/{id}/reject` | `{message}` | `{instanceId, flowStatus, lastSkipType}` | B；办理人取当前登录用户 |

`flowStatus` 保留引擎字符串状态码，`lastSkipType` 为最后一次流转的 `PASS`、`REJECT`、`NONE` 或 `null`；两者含义不同，前端和第三方不自行推断“最终审批通过”。B 在真实流程联调后确定从历史任务取得 `lastSkipType` 的查询方式。实例响应中的 `businessId` 对内部发起为服务生成值；前端不需要预先生成。

`formData` 是 JSON 对象，不接受规则或配置；`formContent` 是已解析的 `{rule, option}` JSON 对象。B 在服务层读取 `flow_form.form_content` 并解析、校验一次，发起时将同一份定义和提交数据序列化到 `bpm_form_snapshot.form_content_json`、`form_data_json`。详情从快照反序列化返回，不反查当前表单。C 直接把对象交给 form-create，不再二次 `JSON.parse`。B 使用 `flow_definition.form_path` 中的表单 ID 读取表单；`flowCode` 选择已发布定义。流程 JSON 保存和已有表单内容编辑复用插件接口；表单基础记录创建及流程/表单发布需由宿主提供最小管理入口并调用 Warm-Flow 核心服务，不重复实现设计器。

第三方请求必须同时携带 `accessKey`、`timestamp`、`nonce`、`sign`；不能以登录态代替。签名验证后 B 再用 `accessKey` 调用现有 `AppService.getByAccessKey` 取得 `app_id`，不接受客户端传入 `appId`。开放查询同时限定 `instance_id` 和快照的 `app_id`，不依赖无登录态下的数据权限。内部请求的 `app_id` 为 `NULL`，发起人和办理人均来自当前登录态；用户有效性需要跨模块查询时只扩充现有 `common.api.system.UserApi`，不复制用户 Mapper。

Warm-Flow 1.8.9 已提供 `/warm-flow/query-def`、`/warm-flow/save-json`、`/warm-flow/published-form`、`/warm-flow/form-content/{id}`、`POST /warm-flow/form-content` 和 `warm-flow-ui/config`。内置表单设计器通过 `type=form&id=<已有表单ID>` 打开，保存内容接口仅更新该 ID 对应的记录；插件 Controller 未暴露创建表单或发布流程/表单的 HTTP 入口。插件还自带 `/warm-flow/execute/handle`，该入口直接办理任务，L 已在拦截器中禁用，审批只走 B 的内部接口；`/admin/approval/**` 与 `/warm-flow/**` 均拒绝签名请求。设计器的具体读写权限由 A 在 T2 落实。
