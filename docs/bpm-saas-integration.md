# SaaS 化审批平台集成设计

> 状态：设计稿（审核修订版）
> 编写日期：2026-09-23
> 适用仓库：`D:\iboot-pro\ibpm`（`server/` 后端 + `web/` 前端）
> 本版范围：完成设计、发起、审批和状态查询；回调相关仅定义 `bpm_app_ext`，不设计通知实现或预留通知代码。

---

## 一、背景与目标

### 1.1 要做什么

在 ContiNew Admin 之上构建一个 **SaaS 化的审批平台**，供第三方系统接入使用。核心能力：

| # | 能力 | 说明 |
|---|---|---|
| 1 | 流程设计 | 管理员在本系统内使用流程设计器编排审批流程 |
| 2 | 表单设计 | 管理员在本系统内使用表单设计器设计审批表单 |
| 3 | 发起审批 | 提交表单数据、发起流程实例 |
| 4 | 审批处理 | 审批人在本系统内处理待办 |
| 5 | 状态查询 | 第三方通过实例 ID 查询审批状态 |

### 1.2 两条发起路径

```text
第三方系统 → HTTP + 签名 + 表单数据 + 本系统用户 ID → 发起流程
本系统用户 → 登录 + 填写表单 → 发起流程
审批人     → 登录本系统 → 查看待办、处理审批
第三方系统 → HTTP + 签名 + 实例 ID → 查询状态
```

- **第三方发起**：发起人 ID 由第三方提供，该 ID 即本系统的用户 ID。
- **本系统内发起**：以当前登录用户作为发起人，填写表单数据。
- **回调范围**：本版只定义应用扩展表中的回调地址与开关；通知机制、报文、记录表、重试及管理页面均不纳入本版。

### 1.3 明确的非目标

以下内容**本期不做**，设计时不为它们预留复杂度：

| 非目标 | 原因 |
|---|---|
| 独立自研表单方案 | 审批表单**全部**走 Warm-Flow 内置的 form-create，不再开发另一套渲染方案；设计器中的“自定义表单”选项用于绑定表单记录，不属于此非目标 |
| 第三方用户同步 / 映射 | 第三方提交的直接是本系统用户 ID，无需映射层 |
| 回调通知实现 | 只保留 `bpm_app_ext` 表定义，不设计监听器、发送、报文、记录表、重试或管理页面 |
| 多租户隔离设计 | 全部使用默认租户，不实际创建租户 |
| JSON 请求体参与入站签名 | 本期沿用现有 Sa-Token 请求参数签名，不扩展签名范围 |
| 应用与流程的授权绑定 | 本期按不同 `flowCode` 的使用约定区分第三方，不建设授权关系 |
| 流程级回调地址覆盖 | 只定义应用级配置，不保留回落逻辑或扩展接口 |

---

## 二、关键决策与依据

本章记录已确定的决策及**依据**（实测数据来源一并标注，便于日后复核）。

### 2.1 技术选型事实（实测）

以下版本信息通过**读取实际构件**获得，非文档推测：

| 组件 | 版本 / 形态 | 数据来源 |
|---|---|---|
| Warm-Flow | **1.8.9** | 阿里云镜像 `maven-metadata.xml`，`lastUpdated=20260713` |
| 设计器构件 | `org.dromara.warm:warm-flow-plugin-vue3-ui:1.8.9` | 同上，约 1.06 MB |
| 设计器内嵌 form-create | **`@form-create/element-ui` 3.2.41** | 反查打包产物运行时配置 `{ui:"element-ui", version:"3.2.41"}` |
| Element Plus | **2.4.3，已打包并注册** | 产物包含 `ZAe="2.4.3"`、组件实现及启动注册 `wc.use(cKe,{locale:dKe})` |
| Element Plus 样式 | **设计器自带**（381 个 `.el-*` 选择器） | 产物 CSS 扫描 |

> **踩坑记录**：Maven Central 的搜索 API 返回的是 **1.3.3（2024-11）的陈旧数据**，与真实最新版 1.8.9 相差甚远。查询 Warm-Flow 版本**必须以镜像的 `maven-metadata.xml` 为准**，不可使用 Central 搜索接口。

### 2.2 设计器承载方式：独立文档（iframe）

**决策：设计器作为独立文档承载，由后端以静态资源方式原样服务，前端通过 iframe 嵌入。**

依据（均来自产物实测）：

1. 设计器的 axios 实例为 `create({ baseURL: "", timeout: 1e4 })`，且 `p0t = "../"`，其配置接口地址为 `$i({ url: "../warm-flow-ui/config" })` —— **所有请求路径相对于 `index.html`，与 context-path 完全解耦**，可原样服务。
2. 设计器自带完整 Element Plus CSS，**在 iframe 内样式自洽**，与本系统 Arco Design 天然隔离。
3. 设计器已打包并注册 Element Plus；`window.ElementPlus` 检测只是 form-create 的条件兼容逻辑，不代表依赖外置，宿主无需为 iframe 注入组件库。

**收益**：仅嵌入设计器无需给 `web/package.json` 新增依赖。本系统内的表单提交与详情页面仍需引入渲染依赖，见 7.2。

### 2.3 设计器鉴权：URL 参数 → localStorage → 请求头

**决策：不新建登录接口，通过 URL 参数向设计器传递令牌。**

机制（产物逆向所得完整链路）：

```js
// 1. 设计器启动时拉取配置
function h0t(){ return $i({ url: p0t + "warm-flow-ui/config", method: "get" }) }   // p0t = "../"

// 2. 配置返回 tokenNameList（如 ["Authorization"]）与 framework
async fetchTokenName(){
  // GET warm-flow-ui/config → r.data.tokenNameList / r.data.framework
  //   Udt(tokenNameList) → localStorage 'Warm-TokenName'
  //   Kdt(framework)     → localStorage 'Warm-Framework'
  // 再遍历 URL 查询参数，命中 tokenNameList 的写入：
  //   Wdt(name, value)   → localStorage 'Warm-<name>'
}

// 3. 常量
const m3 = "Warm-", jq = m3 + "TokenName", Wq = m3 + "Framework"
function kL(e){ return localStorage.getItem(m3 + e) }   // 按名读令牌
function TL(){ return localStorage.getItem(jq) }        // 读令牌名列表

// 4. 请求拦截器自动附加
interceptors.request.use(e => {
  if (TL()) { TL().split(",").forEach(n => kL(n) && (e.headers[n] = kL(n))) }
  // ...
})
```

开发环境经现有 Vite 代理访问，嵌入地址形如：

```text
/dev-api/warm-flow-ui/index.html?Authorization=Bearer%20<token>
```

生产环境使用实际后端基址，具体拼接规则见 7.1。

其中 `<token>` 取自本系统 localStorage 的 `'token'` 键；URL 参数需编码，设计器最终发出的请求头必须是 `Authorization: Bearer <token>`，与本系统 `web/src/utils/http.ts` 一致。令牌出现在 URL 中，部署时应避免在访问日志中记录查询串。

**前提**：后端 `warm-flow-ui/config` 接口的 `tokenNameList` 必须包含 `Authorization`，否则设计器不会读取该 URL 参数。

### 2.4 流程模型：使用 Warm-Flow 自带双模式

**决策：允许使用设计器内置的经典模型（`CLASSICS`）和仿钉钉模型（`MIMIC`），业务接口不按模型过滤可用流程。**

两种模型均由 Warm-Flow 1.8.9 原生支持，主要影响设计器的编排与流程图展示；本项目没有依模型分支的审批逻辑。模型仍由用户在新建流程时选择，无需增加配置或扩展代码。

> 附注：产物中 `window.__WF_FLOW_DESIGN_MODE__` 是设计器启动时**自行赋值**的内部渲染标记（控制节点圆角/阴影/填充色），与模型选择无关，**不应由外部注入**。

### 2.5 第三方识别：复用 `sys_app`

**决策：复用 `continew-plugin-open` 现有的 `sys_app` 表，不新建应用表。**

依据：`sys_app` 已是"一个第三方系统"的完整实体，包含名称、AK/SK、失效时间、状态、描述，且自带 `tenant_id`、完整 CRUD 接口与权限码（`open:app:*`）。

### 2.6 入站鉴权：复用 Sa-Token 签名体系

**决策：第三方入站请求携带 `accessKey / timestamp / nonce / sign` 四参数，走现成的签名校验，不新造鉴权机制。**

现有机制（`SaTokenConfiguration`，以下为仓库现状，不是新增 BPM 接口的完整鉴权规则）：

```java
return new SaExtensionInterceptor(handle -> SaRouter.match("/**")
    .notMatch(properties.getSecurity().getExcludes())
    .check(r -> {
        Collection<String> paramNames = SaHolder.getRequest().getParamNames();
        if (paramNames.stream().anyMatch(SaSignTemplate.sign::equals)) {
            SaSignUtil.checkRequest(saRequest);   // 带 sign → 走签名校验
            return;
        }
        StpUtil.checkLogin();                     // 不带 sign → 走普通登录
        // ...
    }));
```

**现有行为**：只要请求带 `sign` 参数，拦截器就分流到签名校验，不校验登录态。因此开放接口可复用验签：

- **不需要 `@SaIgnore`**
- **不需要配置放行路径**
- **不需要权限码**（`BaseController.preHandle` 与 `SaCheckPermissionHandler` 均对带 `sign` 的请求跳过权限校验）

验签逻辑（`OpenApiSignTemplate`）已内置：应用存在性、启用状态、过期时间、时间戳防重放、nonce 防重放、参数签名比对。

**签名算法（务必按此实现，与 Sa-Token 原生逻辑不同）**：

本项目的 `OpenApiSignTemplate` **重写了 `createSign`**，与 Sa-Token 原生"省略 `sign` 后追加 `&key=<secret>`"的做法不同，此处是**把 `key` 当普通参数一起参与字典序拼接**：

```java
paramMap.remove(sign);
return SaSecureUtil.md5(super.joinParamsDictSort(paramMap));
// 实际拼接结果形如（key 按字典序排在第 2 位）：
// accessKey=<ak>&key=<sk>&nonce=<nonce>&timestamp=<ts>
```

对接第三方时**必须按此规则生成签名**；沿用 Sa-Token 官方文档的原生算法会验签失败（本版实测踩坑）。

**BPM 接口边界（本版已实现并实测通过）**：只开放发起、查询两个接口，不提供第三方审批接口。在现有拦截器进入通用签名分支前，对 `/admin/approval/**`、`/warm-flow/**` 拒绝带 `sign` 的请求；不带 `sign` 的请求继续走登录及权限检查。这样 BPM 的签名调用仅限 `/open/approval/**`，不改变其他既有开放接口的策略，也不新增授权表或权限体系。单纯不公布内部审批地址不能替代这条路径判断，因为当前验签逻辑适用于所有路径。

代码位置：`continew-server/src/main/java/top/continew/admin/config/satoken/SaTokenConfiguration.java:96-106`。除路径判断外，同处还禁用了插件自带的通用办理入口 `/warm-flow/execute/handle`（提示"请通过审批接口办理任务"）。


### 2.7 回调配置：仅定义应用扩展表

**决策：回调地址挂在 `sys_app` 维度（应用级），通过本系统自有的扩展表存储，不修改框架表。**

依据：

- 一个第三方系统拥有多个流程，**回调地址通常是该系统的属性**，配置一次即应适用于其名下所有流程；逐条流程配置会造成大量重复劳动。
- `sys_app` 是**框架表**（由 `plugin_open.sql` 创建），直接加字段存在升级被覆盖的风险。
- 扩展表归属本系统，可自由演进。

**本版范围**：仅保留 5.2.1 的 `bpm_app_ext` 表定义。无需预留通知监听器、发送服务、回调记录、重试字段或流程级回落逻辑。

### 2.8 租户策略：仅使用默认租户

**决策：全部使用默认租户，不实际创建租户。**

**注意**：单租户不代表可忽略数据权限。第四章所述的无登录态调用可能使带 `@DataPermission` 的查询失去过滤，与租户拦截是两套机制。

新增公共表登记租户白名单：项目当前已启用租户拦截，表定义不含 `tenant_id`，须明确排除，避免拦截器追加不存在的列。

---

## 三、总体架构

### 3.1 组件视图

```text
第三方系统（sys_app / AK、SK）
  └─ 签名请求 → /open/approval/** → 发起流程、查询状态

本系统前端（Arco Design）
  ├─ iframe → Warm-Flow 设计器（自带 Element Plus）
  └─ 登录请求 → /admin/approval/** → 填表、待办、审批详情、审批处理

后端
  ├─ /warm-flow-ui/**：静态资源与启动配置
  ├─ /warm-flow/**：设计器业务接口，要求登录和相应权限
  ├─ Warm-Flow 引擎：流程定义、实例、任务
  ├─ 表单快照：完整定义（rule + option）及提交数据
  └─ bpm_app_ext：本版仅定义应用回调配置表
```

### 3.2 数据流

**设计态（管理员）**

```
管理员 → 本系统菜单 → iframe 加载设计器（URL 带 token）
       → 宿主先创建 flow_form 基础记录，取得表单 ID
       → 以 type=form&id=<表单 ID> 打开内置 form-create 设计器
       → /warm-flow/form-content 保存已有表单内容；宿主发布表单
       → 选择经典或仿钉钉模型设计流程、绑定已发布表单 ID
       → /warm-flow/save-json 保存流程定义；宿主发布流程
```

**发起态（两条路径）**

```
路径 1（第三方）：
  第三方系统 → POST /open/approval/instance（accessKey/timestamp/nonce/sign + 表单数据 + 发起人用户 ID）
             → 本系统创建流程实例 + 保存表单快照

路径 2（本系统内）：
  本系统用户 → 前端发起页面（填写表单）→ POST /admin/approval/instance
             → 本系统创建流程实例 + 保存表单快照
```

**审批态**

```
审批人 → 本系统待办 → 读取表单快照（只读渲染）→ 审批通过/拒绝
       → Warm-Flow 推进流程
```

**查询态**

```text
第三方系统 → GET /open/approval/instance/{id}（签名）→ 实例状态
```

**关键原则**：审批详情页不反查业务系统。快照中的完整表单定义（`rule` + `option`）与数据是渲染来源，确保审批人看到发起时刻的表单结构、配置与数据。

---

## 四、数据权限与登录态（重点章节）

> **本章关系到开放接口的查询边界。原稿将无登录态异常误判为“查询变空”；按当前代码和 Starter 字节码，实际需警惕过滤失效。**

### 4.1 数据权限生效条件（已核对代码 + 运行时实证）

数据权限处理器先查找 Mapper 方法上的 `@DataPermission`。本项目的 `DataPermissionMapper` 只给部分继承方法（如 `selectList`、`deleteById`）加了该注解；不能仅凭 Mapper 继承了此接口，就认定其每个方法都会加数据权限。对**确实带注解**的方法，无登录态时的链路是：

```
无登录态请求
  → DefaultDataPermissionUserDataProvider.isFilter()
       调用 UserContextHolder.isSuperAdmin() → StpUtil.checkLogin()
  → 抛出未登录异常（未能读取到有效 token）
  → DefaultDataPermissionHandler.getSqlSegment()
       catch 捕获异常并返回原 where
  → 不追加数据权限条件，按原 SQL 执行
```

> **本版已用真实签名请求实测确认**（第 1 步验证，2026-09-23）。带 `@DataPermission` 的 `selectList` 在无登录态下的实际 SQL 为：
>
> ```sql
> SELECT ... FROM sys_app WHERE deleted = 0 LIMIT 10
> ```
>
> 同时服务端日志出现如下异常，栈帧与上述链路逐层对应：
>
> ```
> ERROR DefaultDataPermissionHandler - Data permission handler build data scope filter occurred an error: 未能读取到有效 token.
>   at cn.dev33.satoken.stp.StpLogic.checkLogin(StpLogic.java:1088)
>   at cn.dev33.satoken.stp.StpUtil.checkLogin(StpUtil.java:476)
>   at DefaultDataPermissionUserDataProvider.isFilter(DefaultDataPermissionUserDataProvider.java:37)
>   at DefaultDataPermissionHandler.getSqlSegment(DefaultDataPermissionHandler.java:78)
> ```
>
> 注意 `DefaultDataPermissionUserDataProvider` 自身**没有**直接调用 `StpUtil.checkLogin()`，而是经 `UserContextHolder.isSuperAdmin()` 间接调用；结果一致，均为抛异常后被 catch。

**关键危害**：带注解的查询在无登录态下失去预期的数据权限过滤，属于潜在越权风险（**实测为"条件完全不追加"，比"追加 null"更宽**）。原稿所写的 `WHERE dept_id = null` 和"静默返回空结果"与处理器返回原 where 的行为不符。

租户拦截与数据权限是不同机制；`TenantUtils.execute` 只能处理租户上下文，不能修复上述数据权限过滤失效。是否有租户条件还须按具体表和白名单判断（`sys_app` 已在 `ignore-tables` 中，故无租户条件）。


### 4.2 影响面一：第三方入站接口

`/open/approval/**` 在签名校验通过后仍处于无登录态。新增 Service 不能依赖 `@DataPermission` 或 `UserContextHolder` 自动限定开放接口的读取范围。

**处理原则**：

| 查询类型 | 做法 |
|---|---|---|
| 开放接口的业务查询 | 在 Service/Mapper 中明确写出该接口需要的查询条件，不把登录态数据权限作为兜底 |
| 需要租户上下文的查询 | 单独显式设置租户上下文；这不代替业务查询条件 |
| 依赖登录用户数据权限的查询 | 仅供本系统已登录接口调用；不要把第三方提交的发起人 ID 当成已登录身份 |

> **注意**：不能通过 `StpUtil.login` 或伪造 `UserContext` 把第三方请求变成本系统用户的登录请求。

### 4.3 验签查询：`AppMapper.selectByAccessKey`

`continew-plugin-open` 的验签入口调用：

```java
@Mapper
public interface AppMapper extends DataPermissionMapper<AppDO> {
    @Select("select * from sys_app where deleted = 0 AND access_key = #{accessKey}")
    AppDO selectByAccessKey(@FieldEncrypt @Param("accessKey") String accessKey);
}
```

该方法本身**没有** `@DataPermission`，继承接口中其他方法的注解不会自动套用到它。按当前代码与 Starter 的按方法查注解逻辑，不能推导出原稿所述“追加 `dept_id = null`、导致所有验签失败”。

> **本版已实测确认**（第 1 步验证，2026-09-23）。用无登录态的真实签名请求访问 `/open/app`，服务端实际执行：
>
> ```sql
> SELECT * FROM sys_app WHERE deleted = 0 AND access_key = '<加密后的 access_key>'
> ```
>
> 即**原样使用方法自身的 `@Select` 条件，未追加任何数据权限条件**，验签成功并正常返回应用数据（`code: 0`）。原稿"追加 `dept_id = null` 导致所有验签失败"的结论**不成立**，本版予以修正。

新增 Mapper 方法仍须遵循 4.2 的原则：开放接口的查询条件必须显式写出，不得依赖数据权限注解兜底。


### 4.4 异步线程中的上下文传递

`AbstractLoginHandler.authenticate` 使用 `CompletableFuture.supplyAsync(..., threadPoolTaskExecutor)` 并发拉取权限与角色，而 `UserContextHolder` / `TenantContextHolder` 均基于 `TransmittableThreadLocal`。

**当前未在项目或 starter 中发现 TTL 装饰的 Executor**，因此异步线程中的上下文传递**存疑**。

**相关事实（已通过字节码确认）**：

- `TenantContext` 是**非静态内部类**（`getIsolationLevel()` 字节码为 `aload_0; getfield CONTEXT_HOLDER`），符合 TTL 对持有者的判定条件；
- `TenantIgnoreAspect` **不包装 Executor**，仅做 `setIgnore(true)` → proceed → `setIgnore(false)`；
- `TenantUtils.execute` **不跨异步线程传递上下文**。

**待验证项**：在异步线程内输出 `TenantContextHolder.getTenantId()` 与 `UserContextHolder.getUserId()`，确认是否为空。

**若确认为空**：新增跨线程逻辑显式传递必要的上下文或在进入异步前查好所需数据。本版不因这一既有疑点引入异步改造。

### 4.5 设计约束

综合以上，本章对后续设计构成以下**硬约束**：

1. 开放接口查询必须有明确的业务条件，不依赖登录态数据权限自动补条件；
2. 不依赖 `UserContextHolder.getContext()` 在无登录态下提供用户；
3. 跨线程执行时显式传递所需上下文或已查询的数据，并在使用后清理线程上下文；
4. 联调时验证返回数据的范围，不能只看接口是否返回 200。

---

## 五、数据库设计

### 5.1 设计原则

| 原则 | 说明 |
|---|---|
| 不改框架表 | Warm-Flow 的 `flow_definition` 等表、ContiNew 的 `sys_app` 等表**均不修改** |
| 双库同步 | 每套变更集必须同时提供 `mysql/` 与 `postgresql/` 两版 SQL |
| 变更登记 | 新增变更集须登记至 `db.changelog-master.yaml` |
| 白名单登记 | 新增公共表须登记 `continew-starter.tenant.ignore-tables` |
| 主键与审计字段 | 遵循 `BaseDO` 约定（`id` / `create_user` / `create_time` / `update_user` / `update_time` / `deleted`） |

### 5.2 新增表

#### 5.2.1 `bpm_app_ext` —— 应用扩展（回调配置）

1:1 关联 `sys_app`，定义第三方系统的回调配置。本版仅保留表结构，不配套通知实现或配置管理页面。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, AUTO_INCREMENT | ID |
| `app_id` | bigint | NOT NULL, UNIQUE(`app_id`,`deleted`) | 关联 `sys_app.id` |
| `callback_url` | varchar(500) | NULL | 回调地址，NULL 表示不通知 |
| `callback_status` | tinyint | NOT NULL DEFAULT 1 | 回调开关（1：启用；2：禁用） |
| 审计字段 | | | 遵循 `BaseDO` 约定 |

**说明**：仅保留应用关联、地址、开关及通用审计字段，不增加通知记录或重试相关字段。

#### 5.2.2 表单快照表

存放流程实例发起时刻的完整表单定义与数据，保证审批人看到的是发起时刻的形态。设计器实际保存 `{ rule, option }`，两部分均须快照保存。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK | ID |
| `instance_id` | bigint | NOT NULL, INDEX | 关联流程实例 |
| `definition_id` | bigint | NOT NULL, INDEX | 关联流程定义 |
| `business_id` | varchar(40) | NULL, INDEX | 第三方业务单据号，与 Warm-Flow `flow_instance.business_id` 长度一致 |
| `form_content_json` | longtext / text | NOT NULL | 完整 form-create 定义 JSON，包含 `rule`（规则）与 `option`（表单配置） |
| `form_data_json` | longtext / text | NOT NULL | 表单数据 JSON |
| `app_id` | bigint | NULL | 来源应用（本系统内发起时为 NULL），**不做外键强约束** |
| 审计字段 | | | 遵循 `BaseDO` 约定 |

> **沿用既有裁决**：`app_id` 保留**可空**，且**不经任何用户映射表解析**。第三方提交的发起人 ID 即为本系统用户 ID，无需中间映射。

> **`business_id` 是冗余列，权威源为 Warm-Flow 实例表。** 见 5.3 说明。冗余的目的是便于"按业务单据号直接查快照"，避免每次先查实例再关联。由于 `businessId` 发起后不再变更，该冗余不存在一致性风险。

> 发起接口须校验 `businessId` 最多 40 个字符，避免快照写入成功而 Warm-Flow 实例字段超长。



### 5.3 不新增的表（复用 Warm-Flow 自带字段）

以下数据**不需要自建表**，Warm-Flow 1.8.9 的实体已自带字段（经反编译确认）：

| 数据 | 承载位置 | 说明 |
|---|---|---|
| `businessId` | `Instance.businessId`、`Task.businessId` | 第三方业务单据号，**实例与任务上均有**，任务层可直接读取，无需回查实例 |
| 流程变量 | `Instance.variable`（`String`，含 `getVariableMap()` 便捷方法） | 存 JSON，可承载"需随实例走"的轻量数据 |
| 租户标识 | `RootEntity.tenantId`（`String`） | Warm-Flow 自有租户字段，本设计仅写默认值，**不与 ContiNew 租户体系做映射** |

**因此不建立"实例扩展表"。**

> **类型差异提醒**：Warm-Flow 的 `Instance.createBy` / `updateBy` 为 **`String`** 类型，而 ContiNew 的 `BaseDO.create_user` 为 `bigint`。写入发起人 ID 时需做类型转换（`String.valueOf(userId)`），读取时反向转换。

### 5.4 租户白名单

`bpm_app_ext`、表单快照表登记至 `continew-starter.tenant.ignore-tables`。

理由：当前租户拦截已开启，而这两张公共表没有 `tenant_id` 字段；登记白名单以避免追加租户条件。

### 5.5 变更集文件

按既有目录约定新增：

```
db/changelog/mysql/plugin/plugin_bpm.sql
db/changelog/postgresql/plugin/plugin_bpm.sql
```

并在 `db.changelog-master.yaml` 中登记（PostgreSQL 部分当前在 master 中被注释，需与既有注释风格保持一致）。

---

## 六、后端接口设计

### 6.1 设计器资源与配置

**静态资源**：设计器构件 `warm-flow-plugin-vue3-ui` 将其前端产物置于 `META-INF/resources/warm-flow-ui/`，Spring Boot 会自动将该路径映射为 `/warm-flow-ui/**`，**无需额外配置**。

**配置接口**：设计器启动时请求 `warm-flow-ui/config`，本系统需实现该接口，返回：

```json
{
  "code": 200,
  "data": {
    "tokenNameList": ["Authorization"],
    "framework": "vue3"
  }
}
```

**`tokenNameList` 必须包含 `Authorization`**，否则设计器不会从 URL 读取令牌（见 2.3）。

**放行配置**：仅放行设计器静态资源与启动配置。仓库实际使用的配置键为：

```yaml
sa-token.extension:
  security.excludes:
    - /warm-flow-ui/**
```

`/warm-flow/**` 包含查询、保存等设计器业务接口，**不得整段加入放行清单**。这些接口按 2.6 拒绝带 `sign` 的请求，使用 2.3 节的 `Authorization: Bearer <token>` 校验登录态，保存和发布等写接口还须校验管理员权限。官方“共享后端权限”方案也只放行 `/warm-flow-ui/**`。同时确认实际资源映射和生产反向代理均能访问该路径。

**表单与发布入口的实际边界**：Warm-Flow 1.8.9 插件 Controller 提供流程 JSON 保存/读取、已有表单内容保存/读取和已发布表单查询；`POST /warm-flow/form-content` 内部先按 ID 读取 `flow_form`，再更新内容，不能创建表单记录。内置表单设计器需以 `type=form&id=<已有表单 ID>` 打开。插件未暴露创建表单记录、发布表单或发布流程的 HTTP 入口；本系统需提供最小管理入口调用 Warm-Flow 核心服务，并将已发布表单提供给流程设计器选择，不重做设计器。

### 6.2 第三方开放接口

**统一前缀**：`/open/approval/**`

**鉴权**：请求携带 `accessKey` / `timestamp` / `nonce` / `sign` 四参数，复用现有签名校验。接口须要求这四个参数，不能以普通登录代替应用验签。无需 `@SaIgnore`、放行配置或权限码；内部接口的路径限制见 2.6。

**接口清单**：

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/open/approval/instance` | 发起流程实例（携带表单数据 + 发起人用户 ID） |
| GET | `/open/approval/instance/{id}` | 查询实例状态 |

**无第三方审批接口**：只提供上表两个开放接口。审批与驳回由本系统已登录用户办理；内部接口按 2.6 拒绝签名请求，不能仅依赖接口命名或是否对外公布。

**第三方如何查询实例**：发起接口同步返回字符串形式的 `instanceId`，第三方保存后用于后续查询。**本期不提供"按 businessId 查询"的接口**；`businessId` 用于业务单据关联和后台排查，是否用于幂等控制见 9.2。

**发起请求关键字段**：

| 字段 | 必填 | 说明 |
|---|---|---|
| `flowCode` | 是 | 流程编码，与 Warm-Flow `flow_definition.flow_code`、`FlowParams.flowCode` 同名；本期约定不同第三方使用不同编码，不作为授权边界 |
| `initiatorUserId` | 是 | 发起人 ID，**即本系统用户 ID** |
| `businessId` | **是** | 第三方业务单据号，最多 40 个字符，见下方说明 |
| `formData` | 是 | 表单数据（form-create 格式） |

> **关于 `businessId` 为什么必需**：
>
> 1. **业务关联** —— 将审批实例与第三方业务单据关联，便于两边核对。
> 2. **业务留痕** —— 快照保留第三方单据号，便于后台排查。
> 3. **后续幂等控制的候选键** —— 是否据此拒绝重复发起仍是 9.2 未决项，当前不能宣称已具备幂等能力。
>
> **命名说明**：字段名与 Warm-Flow 内部字段保持一致，统一使用 **`businessId`**（非 Flowable 生态的 `businessKey`）。

**流程编码命名**：对外请求和本系统代码统一使用 `flowCode`。Warm-Flow 的流程定义字段和发起参数均为 `flowCode`，不引入 `definitionCode` 或 `processKey` 的名称转换。

**鉴权通过后的查询处理**：见第四章 4.2，查询条件必须显式定义；不能把第三方提交的用户 ID 当成登录态上下文。

### 6.3 本系统内接口

面向本系统前端，按 2.6 拒绝带 `sign` 的请求，走常规登录态；审批操作使用当前登录用户作为办理人并校验待办资格：

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/admin/approval/instance` | 本系统内发起（需填表单） |
| GET | `/admin/approval/instance/{id}` | 审批详情（返回表单快照） |
| GET | `/admin/approval/task/page` | 我的待办 |
| POST | `/admin/approval/task/{id}/approve` | 审批通过 |
| POST | `/admin/approval/task/{id}/reject` | 驳回 |

---

## 七、前端设计

### 7.1 设计器嵌入

**承载方式**：iframe 独立文档。

**路由**：现有菜单路径 `/bpm/designer`，组件为 `bpm/designer/index`；组件内：

```vue
<template>
  <iframe :src="designerUrl" class="designer-iframe" />
</template>
```

**URL 拼接**：沿用 `web/src/utils/http.ts` 的基址规则：`VITE_API_PREFIX ?? VITE_API_BASE_URL`。令牌通过宿主的 `getToken()` 读取，再以编码后的 `Authorization=Bearer <token>` 参数传给 iframe；无需 iframe 读取宿主 localStorage。

```ts
const apiBase = import.meta.env.VITE_API_PREFIX ?? import.meta.env.VITE_API_BASE_URL
const designerBase = `${apiBase.replace(/\/$/, '')}/warm-flow-ui/index.html`
const designerUrl = `${designerBase}?${new URLSearchParams({
  Authorization: `Bearer ${getToken()}`,
})}`
```

- **开发环境**：`/dev-api/warm-flow-ui/index.html?...`，由现有 Vite 代理转发；设计器的相对请求也保留 `/dev-api` 前缀。
- **生产环境**：使用部署时的 `VITE_API_BASE_URL`，例如 `https://<实际后端域名>/warm-flow-ui/index.html?...`。不能直接照抄前端根路径 `/warm-flow-ui/index.html`。

**样式**：撑满可用区域，去除边框。

**菜单挂载**：通过后端菜单管理配置，`component` 指向新增页面路径，权限标识遵循 `模块:资源:操作` 格式。

### 7.2 Element Plus 与 form-create 的引入（本系统前端）

**审批详情与表单提交页面需要渲染 form-create 表单**，这些页面位于本系统前端（Arco Design），因此需要引入 Element Plus 运行时。

**依赖清单**（按 `web/AGENTS.md`，新增依赖**须先经讨论**）：

| 依赖 | 建议版本 | 说明 |
|---|---|---|
| `element-plus` | **2.4.3** | 与设计器内嵌版本保持一致，仅供宿主提交与详情页面渲染使用 |
| `@form-create/element-ui` | **3.2.41** | 与设计器内嵌版本严格一致 |

**版本约束**：`@form-create/element-ui` 必须与设计器内嵌版本（**3.2.41**）保持一致。Warm-Flow 官方将 form-create 版本升级视为**高风险操作**，要求实际验证。

**安装约束**：显式指定上述版本，不使用 `latest`。设计器 iframe 已包含自己的 Element Plus，宿主不向它注入依赖；宿主使用相同版本作为兼容基线，并以实际表单验证渲染效果。

**引入方式**：**必须全局注册**。

```ts
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
app.use(ElementPlus)
```

**原因**：form-create 在运行时解析规则中动态生成的子组件（`el-form-item`、`el-row`、`el-col`、`el-option` 及复合组件内部节点）时，查找的是 **Vue 全局组件注册表**，而非 form-create 自身的组件表。局部注册（`formCreate.component(...)`）无法覆盖这些动态生成的子组件，会导致渲染残缺。**早期方案中的"局部按需注册"方式不可行。**

### 7.3 样式隔离

**污染程度评估**：Element Plus 全部样式带 `.el-` 前缀，Arco Design 带 `.arco-` 前缀，**两者不重叠，不会出现组件级样式互串**。

**实际风险点**（按优先级）：

| 风险 | 说明 | 处理 |
|---|---|---|
| `html` / `body` 级重置 | Element Plus 的 `dist/index.css` 含少量全局规则 | 实测确认；本系统 `src/styles/index.scss` 已有重置，通常无碍 |
| CSS 变量碰撞 | Element 用 `--el-*`，Arco 用 `--primary-N` | **不碰撞**，无需处理 |
| 本项目 Arco 覆盖样式过宽 | `src/styles/arco-ui/` 下若存在裸标签选择器或过泛选择器，可能命中 Element 组件 | **实施前需扫描该目录确认** |

**推荐做法（视觉对齐，而非硬隔离）**：在 form-create 渲染容器内重映射 Element 的 CSS 变量，使其视觉向 Arco 靠拢：

```scss
.approval-form-renderer {
  --el-color-primary: var(--primary-6);
  --el-font-size-base: 14px;
  --el-border-radius-base: 4px;
  --el-text-color-regular: var(--color-text-2);
}
```

**不推荐**使用 PostCSS 加前缀做硬隔离：Element Plus 有大量样式在运行时动态注入（弹层、Tooltip、Message 均挂载于 `body`），前缀无法覆盖，会产生"输入框正常但下拉面板样式丢失"类问题。

### 7.4 表单渲染组件

审批详情页需以**只读**方式渲染快照中的表单：

- 输入源：解析 `form_content_json`，将其中的 `rule` 与 `option` 一并交给 form-create，再加载 `form_data_json`
- 渲染：`@form-create/element-ui` + Element Plus
- 模式：只读（在运行时复制的 `option` 上禁用提交/重置按钮并禁用表单，不修改落库快照）
- 约束：**不反查业务系统**，快照即唯一数据源

---

## 八、实施顺序

> **严格按序执行，第 1 步未通过前不进入后续步骤。**

### 第 0 步：前置讨论

按 `web/AGENTS.md` 要求，`element-plus` 与 `@form-create/element-ui` 的引入**须先经讨论**（说明用途、体积、维护活跃度）。此步不可跳过。

### 第 1 步：验证开放接口鉴权与查询边界（**已完成**）

**目标**：确认发起和查询接口可验签调用，内部审批及设计器业务接口只接受登录调用；无登录态查询使用明确的业务条件。

**验证内容与结论**（2026-09-23 实测，后端 port 8000 + MySQL `ibpm`）：

| # | 验证项 | 结论 | 实证 |
|---|---|---|---|
| 1 | 无登录态真实签名请求可查到启用应用 | **通过** | `/open/app` 签名请求返回 `code:0` 及 `sys_app` 数据；实际 SQL 为 `WHERE deleted = 0 AND access_key = ...`，未追加数据权限条件 |
| 2 | 开放接口 Mapper 的 `@DataPermission` 与显式条件 | **通过** | `AppMapper.selectByAccessKey` 无该注解，使用自身 `@Select` 条件；`selectList`（带注解）在无登录态下亦未追加条件 |
| 3 | 无登录态返回范围不因异常扩大 | **确认风险存在，但无新增暴露** | 异常被 catch 后返回原 where，过滤失效（详见 4.1）。现有 `/open/app` 本就以签名分支访问，行为与改造前一致；**新增的 `/open/approval/**` 必须按 4.2 显式写业务条件** |
| 4 | 签名请求访问内部接口被拒绝 | **通过** | `/warm-flow/query-def`、`/warm-flow/form-content/1`、`/warm-flow/save-json`、`/warm-flow/execute/load/1`、`/warm-flow/listener-list` 均返回"工作流管理接口不支持签名调用"；`/warm-flow/execute/handle` 返回"请通过审批接口办理任务" |
| 5 | 不带签名的内部接口要求登录 | **通过** | `/warm-flow/query-def`、`/open/app` 无签名均返回 401 |

**通过标准达成**：验签可用、内部接口不能通过签名调用。

**其中第 4 项为既有实现**：`SaTokenConfiguration.java:96-106` 已完成路径拒绝与通用办理入口禁用，本版无需新增代码；文档此前将其描述为"待在拦截器进入签名分支前实现"，已按实测修正为现状描述（见 2.6）。

**2026-09-24 补验**：`/admin/approval/**` Controller 落地后，执行 Agent 用已在 `/open/approval/instance/{id}` 正向验证的应用身份重测 `/admin/approval/**` 与 `/warm-flow/**`，均被路径规则拒绝；该拒绝发生在签名校验之前。管理员登录访问内部读取接口正常，非办理人通过/驳回任务均被拒且实例、任务状态未变。双应用查询隔离及 DBX 快照归属见 [开发任务文档](bpm-development-tasks.md) 第 8 节。

**若失败**：修正具体查询与上下文使用方式；不得用通用“忽略数据权限”包装绕过问题。


### 第 2 步：后端骨架

1. 引入 `warm-flow` 相关依赖（版本 **1.8.9**）；
2. 新增 `plugin_bpm.sql` 双库变更集并登记 master；
3. 建立实体 / Mapper / Service / Controller 骨架；
4. 登记租户白名单；
5. **此时不写业务逻辑**，先跑通启动与门禁（`./mvnw verify`）。

### 第 3 步：设计器接入

1. 确认设计器静态资源可访问（`/warm-flow-ui/index.html`）；
2. 实现 `warm-flow-ui/config` 接口，返回 `tokenNameList: ["Authorization"]`；
3. 仅放行 `/warm-flow-ui/**`，验证 `/warm-flow/**` 仍需登录及相应写权限；
4. 前端按现有 API 基址新增 iframe 页面并挂菜单；
5. 补齐表单基础记录创建、表单/流程发布及已发布表单选择的最小宿主入口；内置表单设计器编辑已有表单 ID；
6. **验证**：能选择任一内置模型新建并保存流程，能保存、重开、发布表单并将其绑定到已发布流程。

> **本步同时产出后续验证所需的流程模型**：至少画一条**包含"拒绝"分支**的流程，用于第 6 步验证通过与拒绝的实际状态。

### 第 4 步：表单渲染

1. 前端引入 `element-plus` + `@form-create/element-ui`（全局注册）；
2. 实现只读渲染组件；
3. **验证**：用第 3 步设计的表单确认 `rule`、`option` 与数据均正确还原；扫描 `src/styles/arco-ui/` 确认无样式冲突；
4. 跑通前端三道门禁（`pnpm lint && pnpm typecheck && pnpm build`）。

### 第 5 步：发起流程

1. 实现表单快照表与写入逻辑；
2. 实现本系统内发起接口 + 页面；
3. 实现第三方开放接口（`/open/approval/**`）并处理上下文（第四章 4.2）；
4. **验证**：两条路径均能发起，快照正确落库，`businessId` 正确写入实例。

### 第 6 步：审批处理

1. 待办列表、审批通过/驳回；
2. 审批详情读取快照渲染；
3. **验证**：完整走通一条流程（含通过路径与拒绝路径）。

### 第 7 步：整体联调

1. 模拟第三方完整走一遍：发起 → 本系统审批 → 第三方查询状态；
2. 验证完整表单快照、数据可见性和内部接口拒绝签名调用；
3. 全量门禁。

本版回调相关仅随数据库变更定义 `bpm_app_ext`，不增加通知联调步骤。

---

## 九、风险与未决项

### 9.1 风险

| # | 风险 | 等级 | 应对 |
|---|---|---|---|
| 1 | 无登录态下数据权限过滤失效导致查询范围扩大 | 高 | **已在第 1 步实测确认该失效真实存在**（4.1）。应对方式为：`/open/approval/**` 的每个查询都显式写出业务条件，不依赖数据权限兜底；不得依赖 `@DataPermission` 的自动过滤 |
| 2 | form-create 版本或表单配置不匹配导致渲染失败 | 中 | 锁定已核对版本，快照同时保存 `rule` 与 `option` |
| 3 | Element Plus 与 Arco 样式冲突 | 低 | 按 7.3 检查实际页面 |
| 4 | 设计器资源请求未到达后端 | 中 | 按 7.1 使用 API 基址，验证开发代理与生产资源访问 |
| 5 | 第三方按 Sa-Token 官方签名算法对接导致验签失败 | 中 | 本项目的 `createSign` 已重写（2.6），`key` 参与字典序拼接而非末尾追加；须向第三方提供按本算法编写的对接示例 |

### 9.2 未决项

| # | 待定事项 | 影响 |
|---|---|---|
| 1 | 设计器在生产环境的资源发布方式 | 需按实际部署确认 jar 内资源和业务接口可访问 |
| 2 | `businessId` 是否参与发起幂等控制（如相同 `flowCode` + `businessId` 拒绝重复发起） | 影响发起接口校验逻辑与错误码 |

### 9.3 需注意的既有缺陷（非本次引入）

1. **`AbstractLoginHandler.authenticate` 的异步上下文传递存疑**（4.4）——本次不修改框架既有代码，仅确保新增代码规避。
2. **数据权限处理器异常后返回原 SQL 条件**（4.1，**已实测确认**）——新增开放接口不得依赖其在无登录态下兜底过滤；`AppMapper.selectByAccessKey` 本身没有 `@DataPermission`（4.3，**已实测确认不追加条件**）。


---

## 十、附录

### 10.1 版本查询方法（踩坑记录）

查询 Warm-Flow 版本时，**不要使用 Maven Central 的搜索 API**（返回陈旧数据）。正确方式：

```bash
curl -s "https://maven.aliyun.com/repository/public/org/dromara/warm/warm-flow-core/maven-metadata.xml"
```

### 10.2 设计器 localStorage 键名

| 键名 | 来源 | 用途 |
|---|---|---|
| `Warm-TokenName` | `warm-flow-ui/config` 的 `tokenNameList` | 令牌名列表 |
| `Warm-Framework` | `warm-flow-ui/config` 的 `framework` | 前端框架标识 |
| `Warm-<tokenName>` | URL 查询参数 | 具体令牌值（如 `Warm-Authorization`） |

### 10.3 设计器调用的后端接口

从构件产物中提取的完整接口清单：

```
warm-flow-ui/config              启动配置（tokenNameList / framework）
warm-flow/query-def              流程定义查询
warm-flow/save-json              保存设计
warm-flow/form-content[/...]     表单内容
warm-flow/query-flow-chart/...   流程图
warm-flow/execute/load/          加载
warm-flow/execute/hisLoad/       历史加载
warm-flow/node-ext               节点扩展
warm-flow/listener-list          监听器列表
warm-flow/handler-type           处理器类型
warm-flow/handler-result         处理器结果
warm-flow/handler-feedback       处理器反馈
```

### 10.4 Warm-Flow 实体字段（本次用到）

经反编译 `warm-flow-core:1.8.9` 确认，以下字段为框架自带，**不需要自建表**：

| 实体 | 字段 | 类型 | 用途 |
|---|---|---|---|
| `Instance` | `businessId` | `String` | 第三方业务单据号 |
| `Instance` | `createBy` / `updateBy` | **`String`** | 发起人 ID（**注意与 `BaseDO` 的 `bigint` 不一致**） |
| `Instance` | `variable` | `String` | 流程变量（JSON），含 `getVariableMap()` 便捷方法 |
| `Instance` | `tenantId` | `String` | Warm-Flow 自有租户字段 |
| `Task` | `businessId` | `String` | 任务层冗余，可直接读取 |
| `RootEntity`（基类） | `id` / `createTime` / `updateTime` / `createBy` / `updateBy` / `tenantId` / `delFlag` | | 公共字段 |

### 10.5 Warm-Flow 流程状态对照表（`FlowStatus`）

经官方文档 + 反编译 `warm-flow-core:1.8.9` 双重确认。`flow_instance.flow_status` 与 `flow_his_task.flow_status` 共用同一套取值。

官方对 `flow_status` 字段的备注原文：

> 流程状态（0待提交 1审批中 2审批通过 4终止 5作废 6撤销 8已完成 9已退回 10失效 11拿回）

| key | 常量名 | 中文 | 业务可用 |
|---|---|---|---|
| `0` | `TOBESUBMIT` | 待提交 | ✅ |
| `1` | `APPROVAL` | 审批中 | ✅ |
| `2` | `PASS` | 审批通过 | ✅ |
| `3` | `AUTO_PASS` | 自动完成 | 引擎内部 |
| `4` | `TERMINATE` | 终止 | ✅ |
| `5` | `NULLIFY` | 作废 | ✅ |
| `6` | `CANCEL` | 撤销 | ✅ |
| `7` | `RETRIEVE` | 取回 | 引擎内部 |
| `8` | `FINISHED` | 已完成 | ✅ |
| `9` | `REJECT` | 已退回 | ✅ |
| `10` | `INVALID` | 失效 | ✅ |
| `11` | `TASK_BACK` | 拿回 | ✅ |
| `12` | `RE_START` | 重启 | 引擎内部 |
| `13` | `PENDING` | 暂存 | 引擎内部 |

**枚举提供的静态方法**：

```java
FlowStatus.isFinished(String)      // 1.8.9 仅在状态为 FINISHED（"8"）时返回 true
FlowStatus.getKeyByValue(String)   // 中文 → key
FlowStatus.getValueByKey(String)   // key → 中文
FlowStatus.getByKey(String)        // key → 枚举
```

> **已确认行为**：1.8.9 的 `isFinished()` 仅判断是否等于 `FINISHED`（`8`），对终止（`4`）、作废（`5`）、撤销（`6`）和退回（`9`）均返回 `false`。不能把它当作所有业务终态的判断；审批通过与拒绝的实际状态仍须在第 6 步结合流程验证。

### 10.6 流转类型对照表（`skip_type`）

`flow_his_task.skip_type` 与 `FlowParams.skipType` 共用。官方备注原文：

> 流转类型（PASS通过 REJECT退回 NONE无动作）

| 值 | 含义 |
|---|---|
| `PASS` | 通过 |
| `REJECT` | 退回 |
| `NONE` | 无动作 |

> **注意**：这是本次审批动作，与 `flowStatus` 是两个维度。退回上一步不等于流程已结束；通过或拒绝进入结束节点时，引擎状态均可能为 `8`，应结合实际流转判断业务含义。

### 10.7 `FlowParams` 字段速查

**`FlowParams`**（本次操作上下文，反编译确认）：

| 字段 | 类型 | 用途 |
|---|---|---|
| `flowCode` | `String` | 流程编码 |
| `handler` | `String` | 办理人 |
| `nodeCode` | `String` | 节点编码 |
| `skipType` | `String` | **流转类型**（`PASS` / `REJECT` / `NONE`） |
| `message` | `String` | **审批意见** |
| `flowStatus` | `String` | 流程状态 |
| `hisStatus` | `String` | 历史任务状态（可与流程状态不同） |
| `cooperateType` | `Integer` | 协作类型（1审批 2转办 3委派 4会签 5票签 6加签 7减签） |
| `variable` | `Map<String,Object>` | 流程变量 |
| `activityStatus` | `Integer` | 激活状态 |

### 10.8 参考实现

| 场景 | 参考位置 |
|---|---|
| 开放接口的免登录 + 租户上下文 | `CommonController.listSiteOptionDict`（`@SaIgnore` + `@TenantIgnore`） |
| 登录态构造（若需要） | `AbstractLoginHandler.authenticate`（**注意第 4.5 节约束**） |
| AK/SK 验签 | `OpenApiSignTemplate` / `SaTokenConfiguration` |
| 应用管理 CRUD | `AppController`（`/open/app`） |
