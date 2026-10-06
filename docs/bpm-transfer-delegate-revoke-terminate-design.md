# 转办、委派、撤销、终止：接入方案与难度

核查日期：2026-10-06。工作区：`D:\iboot-pro\ibpm`。后端 `5ae9200d`，前端 `30da867`，Warm-Flow 1.8.9。

本轮核对当前宿主代码、匹配版本的本地引擎源码及 DBX MySQL 数据。未实现这些功能，未操作现有业务实例，未进行运行验收或权限实测。以下接入方式属于建议，不能作为已交付能力。

## 1. 推荐结论

四项都有现成引擎能力，主要工作在宿主接口、待办资格、操作入口、状态和记录，通常不需要改引擎表结构或自己实现委派状态机。

| 操作 | 推荐业务语义 | 引擎能力 | 难度 |
|---|---|---|---|
| 转办 | A 把自己的办理资格交给 B；B 审批后按原流程继续，不回 A | `TaskService.transfer(taskId, params)` | 偏简单，但要接通 B 的待办、查看与办理 |
| 委派 | A 请 B 处理；B 提交反馈后回 A，A 再正式审批 | `depute`，受托人办理时引擎自动归还 | 中等，要区分反馈与正式审批并展示两轮记录 |
| 撤销申请 | 发起人结束本次申请，清空待办；修改后发起新实例 | `terminationByInsId`，服务端指定状态 `CANCEL=6` | 中等，可复用终止链路，适合当前只读快照模型 |
| 终止流程 | 管理员强制结束某个运行实例，清空待办 | `terminationByInsId`，默认 `TERMINATE=4` | 核心调用偏简单；管理所有实例需增加管理入口 |

首期建议每次转办/委派只选一人，受托人只提交反馈。先覆盖当前实际使用的普通节点与会签节点；不引入当前未使用的比例票签、重启和多层委派设计。

## 2. 转办与委派的真实语义

### 转办

1. A 执行转办时删除 A 的待办人员记录，增加 B 的 `TRANSFER` 记录。
2. 当前任务 ID、节点不变；转办不代表通过或进入下一节点。
3. B 正常审批后，按该任务所属定义版本继续流转。
4. 同节点其他会签人员的资格不能被宿主整体覆盖。

### 委派

1. A 委派给 B，删除 A 的待办人员记录，增加 B 的 `DEPUTE` 记录；该记录的 `createBy` 保存 A。
2. B 调用通过或驳回时，引擎先记录反馈，再删除 B 的委派记录，给 A 恢复 `APPROVAL`。
3. 任务、节点不变，引擎直接返回；B 的“不同意”也不会执行流程驳回路线。
4. A 再审批时才进入正常流转。A 若原本是转办接收人，归还后人员类型恢复为 `APPROVAL`，宿主不应要求仍为 `TRANSFER`。

因此 B 的界面应显示“提交委派反馈”，选择“同意/不同意”并填写意见，成功提示“已交回委派人”。不能沿用“审批通过/流程已驳回”的提示，也不能按普通驳回连线禁用不同意反馈。

### 宿主必须同时修改的三处

- [待办查询](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/impl/ApprovalRuntimeServiceImpl.java:84)：当前只读取 `APPROVAL`。
- [办理资格](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/impl/ApprovalRuntimeServiceImpl.java:173)：当前只接受 `APPROVAL`。
- [详情及流程图查看资格](D:/iboot-pro/ibpm/server/continew-plugin/continew-plugin-bpm/src/main/java/top/continew/admin/runtime/service/impl/InstanceViewServiceImpl.java:65)：当前办理人判断只读取 `APPROVAL`。

三处统一识别 `APPROVAL / TRANSFER / DEPUTE`，但动作策略按实际人员类型区分。不能将抄送等其他类型一起视为可办理。

候选用户复用 common 中 `UserApi.listSimple/isEnabled`，BPM 不直接依赖 system 具体 Service。返回字符串 ID、用户名、昵称；提交时核验目标启用、不是本人、没有当前重复办理资格。

## 3. 撤销必须先确定含义

### 引擎 `revoke` 是撤回重办

`TaskService.revoke(instanceId, params)` 默认状态为 6，检查发起人，保存原待办历史并删除原任务/人员，然后重新创建开始节点之后的任务；遇到网关会重新计算后续节点。

新待办默认按模型目标节点的办理人配置生成，不保证交给发起人。服务端可用 `nextHandler` 指定人员，但指定人员不能代替“提交节点、修改申请、重新提交”这一整套业务设计。状态 6 仍可办理，后续会恢复为审批中或完成。因此只看 `flowStatus=6` 不能判断实例已经彻底结束。

当前详情表单只读，数据来自固定快照。直接接 `revoke` 无法自动得到“发起人修改原申请再提交”的体验；这条路线需要独立设计修改和快照版本记录。

### 推荐首期：撤销后结束本次申请

复用正式结束 API，以自定义状态 6 区分于管理员终止：

```java
taskService.terminationByInsId(instanceId, FlowParams.build()
    .handler(currentUserId.toString())
    .message(reason)
    .flowStatus(FlowStatus.CANCEL.getKey())
    .ignore(true));
```

必须由宿主先确认当前用户是实例发起人、实例仍允许撤销、有有效待办，再由服务端设置 `ignore(true)`。这个参数只用于跳过引擎的“当前审批人资格”；不得接受客户端传入 `ignore`，也不能省略宿主资格检查。

结果进入结束节点、状态 6、删除全部在途待办，保留实例、表单快照和历史。重新申请创建新实例。不要调用完 `revoke` 再手改状态或删除待办来拼出终态。

“审批人拿回自己的上一轮已办”是另一个操作，引擎对应 `taskBack`，本轮不把它混进发起人撤销。

## 4. 管理员终止

服务端确认管理资格和实例范围后调用 `terminationByInsId`，操作人取当前登录用户，填写终止原因，默认状态 4。无需选择退回节点。

已结束实例或没有待办的实例不能再次终止。`InsService.active` 只切激活状态，不会恢复被清掉的任务；首期不提供终止后恢复，重新发起应产生新实例。

源码中终止先从当前待办选择一条写入主动终止历史，再删除该实例所有剩余待办与办理人。其余并行任务没有逐条转历史，不能把接口注释“所有待办转历史”当作实现事实。最小交付以一条明确的实例终止记录表达全实例停止；若后续要求逐条记录取消原因，再单独设计审计记录，不擅自伪造审批结果。

## 5. 入口和抽屉

- 转办/委派：我的待办 → 详情抽屉底部“更多操作”。用同一操作抽屉选择一名用户、填写意见。
- 撤销：我的流程 → 详情抽屉 → 撤销申请。只输入原因，并明确本次申请会结束。
- 终止：流程实例管理 → 详情抽屉 → 终止流程。终止的是实例，不能放在流程模型行上让人误解为停止全部实例。

项目现有统一样式基于 `a-drawer`，可参照 [回调配置抽屉](D:/iboot-pro/ibpm/web/src/views/open/app/CallbackConfigDrawer.vue:2)。不新建小弹窗。

当前 [详情底部](D:/iboot-pro/ibpm/web/src/views/bpm/approval/DetailDrawer.vue:60) 的操作全部依赖 taskId，而 [我的流程](D:/iboot-pro/ibpm/web/src/views/bpm/mine/index.vue:99) 不传 taskId。应分开返回任务动作和实例动作，撤销/终止不依赖具体待办 ID。

现有 UserSelect 直接使用系统用户管理列表，并额外查询部门及全量用户，不适合原样用于普通审批候选选择。最小方案是抽屉内搜索单选，调用 BPM 任务候选接口。

“我的流程”只查本人申请，详情也只允许参与人员查看。管理员需要处理任意实例时应增加“流程实例管理”列表及管理查看入口，复用现有申请内容和流程图组件，不把个人列表改成全量。

## 6. 接口与共同闭环

建议宿主接口如下，响应仍使用项目统一包装，所有 ID 为字符串：

| 接口 | 用途 |
|---|---|
| `GET /admin/approval/task/{id}/actions` | 按实际节点版本和办理人类型返回任务动作及禁用原因 |
| `GET /admin/approval/task/{id}/candidates` | 查询当前动作可选的启用用户 |
| `POST /admin/approval/task/{id}/transfer` | 接收 targetUserId、message |
| `POST /admin/approval/task/{id}/depute` | 接收 targetUserId、message |
| `POST /admin/approval/task/{id}/depute-feedback` | 接收 agree、message；按受托人身份处理反馈 |
| `GET /admin/approval/instance/{id}/actions` | 返回发起人撤销等实例动作 |
| `POST /admin/approval/instance/{id}/cancel` | 发起人彻底撤销，接收 reason |
| 管理实例查询/详情及 `POST .../{id}/terminate` | 在管理范围内终止，接收 reason |

转办/委派沿用此前动态动作方案，读取当前任务实际 `definitionId + nodeCode` 的节点配置；撤销/终止按实例状态与身份判断，不要求配置一条流程出线。能力查询和写接口复用判断，提交时重新读取资格。

审批、转办、委派、撤销、终止应使用同一个实例维度的事务互斥入口，取得锁后复读任务及人员。这样实例撤销和某个并行任务审批不会各用不同锁而互相穿透。前端提交期间禁用其他动作。

### 记录和状态

- 增加审批记录，区分转办、委派、委派反馈、正式通过/驳回、撤销、终止。
- 当前已办按 `taskId` 作行 key。同一个任务委派后又审批会有多条历史，应返回 `historyId` 作已办行 key，不能按 taskId 去重丢历史。
- 详情复用已有状态映射；状态 4/6 不能显示为审批中。
- 终止 API 内部固定设置 `skipType=PASS`，它表示引擎结束路径，不能据此把业务终止判为审批通过。撤销/终止结果按实例状态优先判断。

### 第三方回调

现有 CallbackServiceImpl 只发送状态 8。撤销/终止的 6/4 会被跳过，第三方将继续等待。

不能只扩大状态白名单，因为现有 `approved` 通过最后动作是否 REJECT 判定，会把终止的 PASS 错当批准。建议分别增加 `PROCESS_CANCELLED`、`PROCESS_TERMINATED` 事件，以状态和原因明确表达结果；`approved` 改为可空，仅正式审批完成时有通过/驳回含义，并同步接收方契约。发送仍在事务提交后执行，同时修复已发现的 ID 序列化问题。

如果未来做 `revoke` 撤回重办，状态 6 不是终态，不能发结束事件；需要按具体动作和实例是否处于结束节点区分。

## 7. 当前实际范围与验收

DBX 当前 `flow_node` 的人工节点比例只有 0 和 100：0 共 72 个，其中已发布 32 个；100 共 2 个，其中已发布 1 个。当前 `flow_user` 46 条全是类型 1，尚无转办/委派存量记录。当前没有比例票签节点，不据此扩展首期范围。

1.8.9 的会签统计会把该任务所有历史计入 allNum，转办的 NONE 与委派反馈历史也在其中。不过末位待办有兜底处理，不能仅凭分母增加断言会签卡死。应使用代表性 100 会签流程验证转办、委派后必须由剩余正式审批人完成，受托人反馈不能提前完成节点。若以后启用比例票签，再专项核查反馈计票和阈值计算。

最小验收场景：

1. A 转办给 B：A 不再能办理，B 看得到、打开得了、办得了，之后按原节点流转。
2. A 委派给 B：B 同意或不同意都回 A，不推进节点；A 正式办理后推进；两轮历史均可查。
3. 100 会签下转办/委派保持其他正式审批人的待办和完成时机。
4. 发起人撤销：状态 6 且处于结束节点，原待办全部消失，快照/历史保留，旧任务无法再提交。
5. 管理员终止：状态 4，同样清理全部待办，原因与操作人可查。
6. 审批与撤销/终止并发时只有一个有效结果，不重复流转，不产生残留待办。
7. 已结束实例不再开放生命周期写操作；详情、已办、本人流程、实例管理状态一致。
8. 第三方收到撤销/终止事件，结果不是批准，实例 ID 是字符串；内部发起仍无来源应用回调。

推荐顺序：统一办理资格、动作策略、互斥与历史 → 转办 → 委派 → 共享结束服务实现本人撤销 → 管理实例入口与管理员终止 → 代表性业务回归。

## 8. 精确源码依据

版本匹配源码来自 `C:\Users\14190\.m2\repository\org\dromara\warm\warm-flow-core\1.8.9\warm-flow-core-1.8.9-sources.jar`。以下为 JAR 内 entry 行号，不是宿主文件行号：

- `org/dromara/warm/flow/core/service/impl/TaskServiceImpl.java`：转办 389–398，委派 402–411，更新人员及协作历史 461–481；委派处理 701–726，提前返回 182–185。
- 同 entry：`revoke` 238–311；`terminationByInsId/termination` 316–374；任务生成 532–544；有效性检查 675–684；会签统计 737–836；剩余任务清理 951–956。
- `org/dromara/warm/flow/core/service/impl/HisTaskServiceImpl.java`：按 taskId 查询全部历史 50–51；委派反馈历史 156–173。
- `org/dromara/warm/flow/core/service/impl/UserServiceImpl.java`：协作目标人员和 createBy 151–157。
- `org/dromara/warm/flow/core/service/impl/InsServiceImpl.java`：active/unActive 217–229。
- `org/dromara/warm/flow/core/utils/ExpressionUtil.java`：办理人表达式与 nextHandler 覆盖 81–104、184–193。
- `org/dromara/warm/flow/core/enums/FlowStatus.java`：终止 4、撤销 6、完成 8，42–50；isFinished 98–99 仅检查状态 8。

通过项：匹配源码语义、宿主缺口、入口与 DBX 实际节点类型已核对。未通过项：四项尚未实现和运行验收。阻塞项：没有阻止方案交付的阻塞。下一步：按推荐范围实施，撤回修改原实例属于另一个明确增加的功能范围。
