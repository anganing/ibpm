# Warm-Flow 集成检查：现有能力与缺口

核查日期：2026-10-06。工作区：`D:\iboot-pro\ibpm`。

后端提交：`5ae9200d`；前端提交：`30da867`。引擎版本：Warm-Flow 1.8.9。

本轮检查当前代码、项目设计文档、匹配版本的本地引擎源码，以及 DBX 的 MySQL `local-mysql-3306 / ibpm`。没有修改业务代码、写入数据库、外发回调或重新执行权限实测。以下“已有”指实现存在，不能替代完整运行验收；历史验收记录只作为线索。

## 1. 结论

基础审批链路已经具备：流程及表单设计、版本管理、发布、分类、暂停新发起、内部及第三方发起、表单快照、待办/已办/本人流程、通过/驳回、详情与流程图、第三方状态查询、应用级回调配置及轻量终态通知。

当前应优先处理三项业务正确性问题：审批操作没有节点策略约束、填表期间流程换版没有提交一致性校验、回调雪花 ID 实际发送为 JSON 数字。其次补齐详情状态与审批记录、办理并发保护，以及按对接需求决定的回调可靠交付。

不能把引擎提供的能力都当作当前项目必须补的缺陷。多租户、第三方用户同步、流程级回调覆盖、应用与流程授权绑定、请求体签名、发起幂等曾明确不在首期范围。

## 2. 优先处理的问题

### P1：通过/驳回没有按任务节点判定操作能力

- 证据：[DetailDrawer.vue](D:/iboot-pro/ibpm/web/src/views/bpm/approval/DetailDrawer.vue:60) 只要有 taskId 和 detail 就显示两个按钮。
- 后端：[ApprovalRuntimeServiceImpl.java](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/impl/ApprovalRuntimeServiceImpl.java:171) 检查 APPROVAL 办理资格后直接执行 pass/reject，没有任务动作策略。
- 匹配版本的 NodeServiceImpl 会将空 skipType 连线视为可匹配动作；退回目标还可能由 anyNodeSkip 指定。因此不能仅以“存在出线”放行驳回，也不能只检查 REJECT 连线。
- 建议：实施已有 [动态操作方案](D:/iboot-pro/ibpm/docs/bpm-task-actions-design.md:98)，基于真实任务的 definitionId + nodeCode 返回 enabled/reason，前端显示与写接口使用同一策略。
- 证据等级：当前代码及匹配源码确认；本轮未重新执行异常路由。

### P1：填写期间发布新版本，旧表单数据可能按新版本提交

- 触发：用户打开 V1 表单后填写；管理员发布绑定新表单的 V2；用户继续提交。
- 证据：[StartPanel.vue](D:/iboot-pro/ibpm/web/src/views/bpm/approval/StartPanel.vue:167) 已拿到预览 definitionId，但 [提交](D:/iboot-pro/ibpm/web/src/views/bpm/approval/StartPanel.vue:200) 只发送 flowCode、formData 和 businessId。
- 后端请求 [InternalStartReq.java](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/model/req/InternalStartReq.java:35) 没有预览版本；[发起服务](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/impl/InstanceStartServiceImpl.java:59) 按提交时的当前定义取表单。
- 结果：预览内容和实际发起使用的定义可能不一致，快照可能使用新字段展示旧数据。
- 建议：内部发起提交 expectedDefinitionId，服务端比较当前生效版本；变化时明确提示重新加载并检查填写内容。不要为解决这个问题允许任意旧定义发起。
- 证据等级：代码确认版本校验缺失，场景本轮未做写入复现。

### P1：回调实例 ID 序列化类型错误

- [AppCallbackMessage.java](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/model/message/AppCallbackMessage.java:49) 使用 Jackson ToStringSerializer，约定 ID 为字符串。
- [CallbackSender.java](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/CallbackSender.java:70) 却使用 Hutool JSONUtil，该注解不生效。
- 已用当前编译类和依赖做离线探针：Hutool 输出 `"instanceId":894303088410702087`；Jackson 输出 `"instanceId":"894303088410702087"`。JavaScript 接收方解析数字时会发生精度损失。
- 建议：使用项目 ObjectMapper 序列化回调报文，核对接收端 JSON 类型。无需因此改变报文字段范围。
- 证据等级：源码确认并完成离线序列化复现，没有发送 HTTP 请求。

### P2：详情状态把已退回等状态显示为“审批中”

- [DetailDrawer.vue](D:/iboot-pro/ibpm/web/src/views/bpm/approval/DetailDrawer.vue:103) 对所有非 8 状态统一返回“审批中”。
- 当前数据库已经有 1 个 flow_status=9 的实例，所以这不是仅为未来功能准备的边界。
- [task.ts](D:/iboot-pro/ibpm/web/src/apis/bpm/task.ts:50) 已有统一状态映射，详情未复用。
- 建议：复用状态映射；对完成状态再按已约定的审批结果显示通过/驳回，避免把动作和实例状态混成同一概念。
- 证据等级：代码与当前数据库值确认；本轮未打开真实详情页面。

## 3. 缺少的集成能力

| 优先顺序 | 能力 | 当前情况与建议 |
|---|---|---|
| 1 | 加签、减签 | 仅有方案，宿主没有节点扩展、选人、接口与执行链路。实施时验证待办实际归属、减签至少保留一人、会签/票签人数及结束时机。加签不自动把或签改成会签。 |
| 2 | 办理并发保护 | handle 有事务但无任务/实例串行锁；1.8.9 TaskServiceImpl 自身也留有并发 TODO。多个或签人员同时处理、重复提交、并行汇聚需要专项检查。取得锁后复读任务和办理资格；当前没有证据证明数据库已发生重复流转。 |
| 3 | 完整审批记录 | 详情只有快照、状态、最后动作，没有按步骤的办理人、时间、意见和协作类型。[InstanceDetailResp](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/model/resp/InstanceDetailResp.java:40) 尚无记录列表。建议增加“审批记录”，尤其支持退回后再次审批的多轮记录。 |
| 4 | 回调可靠交付 | [发送器](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/CallbackSender.java:39) 明确失败只写日志、不重试，没有持久发送记录或人工补发。第三方短暂不可用后只能主动查询。若正式对接依赖回调推进业务，需设计发送记录、失败查看、重试/补发及接收成功契约。 |
| 5 | 转办、委派、撤销、终止 | 无宿主入口。按真实业务先确定要哪几项。当前待办与办理仅认 APPROVAL，直接调用转办/委派引擎 API 会缺少 TRANSFER/DEPUTE 后续链路。 |
| 6 | 角色/部门/动态主管选人 | [BpmHandlerSelectServiceImpl](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/design/service/impl/BpmHandlerSelectServiceImpl.java:35) 明确只提供具体用户。若业务要求动态组织选人，需同时设计办理人标识、解析与任务查询，不能只增加下拉选项。 |

这些能力不是全部都违反首期验收；应按实际使用范围排期。

## 4. 当前数据库核查

DBX 实查结果：

- BPM Liquibase 变更集 1～9 均为 EXECUTED。
- 应用回调、流程暂停、分类等预期唯一索引均存在。
- 未发现同编码多个已发布流程定义，也未发现同表单编码和版本重复。
- 未发现缺失或重复的有效实例表单快照、快照定义 ID 与实例定义 ID 不一致、孤立待办任务。
- 两个已发布流程 `test77`、`test88` 绑定同一历史表单 `test_1 / v1`，表单状态为 9。当前读取逻辑会将其排除于可发起列表，属于存量绑定问题，不是新发布表单必然破坏旧流程。

建议通过流程模型新建草稿、重新绑定有效已发布表单后发布来修正这两个配置；不要手改历史表单发布状态或迁移历史数据。管理页面也应让“已发布但不可发起”的原因更容易看到。

以上是当前数据一致性结果，不证明并发、回调网络可靠性或全部图形模型已完成验收。

## 5. 待运行验证与工程收口

- 最新已发布表单选项筛选已有代码与逻辑回归证据；历史流程复制草稿保留旧表单 ID 时，该 ID 可能已不在选项中，需要检查设计器回显。不可擅自自动换绑存量定义。
- 会签、或签、票签、退回目标、并行汇聚、多级审批及 CLASSICS/MIMIC 两种模型需要有代表性的集成回归，尤其新加减签上线前。代码存在不能替代这些场景证据。
- 如果实际允许开始或条件分支直接结束，检查第三方发起当场完成时的回调：引擎 start 返回前触发 finish，但快照在返回后才插入，监听器遇不到快照会跳过。仅在确认支持此业务模型时补测，不能当作已发生故障。
- 流程图只读已经实现，但 [BpmFlowChart](D:/iboot-pro/ibpm/web/src/components/BpmFlowChart/index.vue:173) 依赖该版本 UI 的 Preact/Vue 内部字段。升级 Warm-Flow UI 必须回归只读接管；已有文档仍缺真实触屏、键盘遍历等部分运行证据。
- 待办/已办/本人流程当前先加载用户全部数据后做内存分页，见 [ApprovalRuntimeServiceImpl](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/impl/ApprovalRuntimeServiceImpl.java:199)。数据量增加后优先改已办与本人流程的数据库分页，当前不据此判定性能故障。
- PostgreSQL 仍为脚本静态准备，按既有决定暂缓运行验证。如果以后部署，需同时切换 JDBC、Liquibase、MyBatis database-id/db-type 和 warm-flow.data-source-type，不能只更换连接地址。
- 没有 BPM 专项持久回归测试；后端默认跳过单元测试，前端没有测试框架。构建门禁不能代替业务场景验证。
- [bpm-saas-integration.md](D:/iboot-pro/ibpm/docs/bpm-saas-integration.md:45) 与 [bpm-development-tasks.md](D:/iboot-pro/ibpm/docs/bpm-development-tasks.md:16) 仍描述不做回调配置和发送，与当前代码不一致；[bpm-优化.md](D:/iboot-pro/ibpm/docs/bpm-优化.md:8) 的未提交及收口状态也已过时。应更新当前交付范围、对接说明及验证记录。

## 6. 推荐实施顺序

1. 修正回调 ID 序列化、详情状态和填表提交版本校验。
2. 落实通过/驳回动作策略，并补办理并发保护及代表性路由回归。
3. 按已有方案完成加签、减签；增加完整审批记录。
4. 按第三方对接需求决定回调发送记录、失败查看与补发；同步对接文档。
5. 有真实业务需求后再排转办、委派、撤销、终止、动态组织选人和发起幂等。

权限实测本轮按用户要求跳过；PostgreSQL 运行验证维持暂缓。没有阻塞本轮检查的事项。

