import http from '@/utils/http'

const BASE_URL = '/admin/approval/process'

/** @desc 流程模型（流程定义）列表行，契约见 docs/bpm-优化.md §8.3 */
export interface ProcessResp {
  /** 定义 ID（十进制字符串），该流程编码的最新版本 */
  id: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName: string
  /** 设计器模型：CLASSICS 经典 / MIMIC 仿钉钉 */
  modelValue: string
  /** 所属分类 code（对应分类表 code，非主键） */
  category: string
  /** 版本号（最新版本） */
  version: string
  /** 发布状态：0 未发布 / 1 已发布 / 9 失效 */
  isPublish: number
  /** 激活状态：0 挂起 / 1 激活 */
  activityStatus: number
  /** 更新时间 */
  updateTime: string
  /** 当前生效版本的定义 ID（发起链路实际选中的那一版；无生效版本时为 null） */
  effectiveId: string | null
  /** 当前生效版本的版本号（无生效版本时为 null） */
  effectiveVersion: string | null
  /** 当前生效版本是否已被暂停（宿主侧标记） */
  paused: boolean
}

/** @desc 流程版本记录行（只读） */
export interface ProcessVersionResp {
  /** 定义 ID（十进制字符串） */
  id: string
  /** 流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName: string
  /** 版本号 */
  version: string
  /** 发布状态：0 未发布 / 1 已发布 / 9 失效 */
  isPublish: number
  /** 激活状态：0 挂起 / 1 激活 */
  activityStatus: number
  /** 更新时间 */
  updateTime: string
  /** 是否为当前生效版本 */
  effective: boolean
  /** 该流程编码是否已被暂停 */
  paused: boolean
}

/** @desc 流程模型分页响应 */
export interface ProcessPageResp {
  list: ProcessResp[]
  total: number
}

/** @desc 流程模型分页查询参数 */
export interface ProcessQuery extends PageQuery {
  flowName?: string
  flowCode?: string
  category?: string
  isPublish?: number
}

/** @desc 创建流程定义（草稿）请求 */
export interface ProcessReq {
  flowCode: string
  flowName: string
}

/**
 * @desc 第三方对接说明：该流程当前生效定义及其绑定的已发布表单内容
 *
 * 版本选择与发起链路同源（后端复用 getPublishByFlowCode + 已发布表单判定），
 * 因此主列表展示最新草稿时，这里仍返回真正用于发起的那一版。
 * 无可发起版本/表单时后端返回 success=false 与原因，前端直接展示，不伪造可用示例。
 */
export interface ProcessIntegrationResp {
  /** 该流程编码 */
  flowCode: string
  /** 流程名称 */
  flowName: string
  /** 当前生效定义的 ID（十进制字符串） */
  definitionId: string
  /** 当前生效定义的版本号 */
  definitionVersion: string
  /** 绑定表单的 ID（十进制字符串） */
  formId: string
  /** 绑定表单的名称 */
  formName: string
  /** 绑定表单的版本号 */
  formVersion: string
  /** 已解析的表单规则与配置（rule + option） */
  formContent: import('@/components/ApprovalForm/type').ApprovalFormContent
}

/** @desc 流程模型分页列表 */
export function pageProcess(query: ProcessQuery) {
  return http.get<ProcessPageResp>(`${BASE_URL}/page`, query)
}

/** @desc 第三方对接说明：生效定义 + 绑定已发布表单 */
export function integrationProcess(id: string) {
  return http.get<ProcessIntegrationResp>(`${BASE_URL}/${id}/integration`)
}

/** @desc 创建流程定义（草稿），返回新定义 ID 供设计器定位 */
export function createProcess(data: ProcessReq) {
  return http.post<string>(`${BASE_URL}`, data)
}

/** @desc 物理删除流程定义（未发布且无实例才允许，否则后端拒绝） */
export function deleteProcess(id: string) {
  return http.del(`${BASE_URL}/${id}`)
}

/** @desc 定位设计器应打开的定义：未发布返回自身 ID；已发布返回复制/复用出的下一版本草稿 ID */
export function designProcess(id: string) {
  return http.post<string>(`${BASE_URL}/${id}/design`)
}

/** @desc 查询该定义所属流程编码下的全部版本记录（只读） */
export function versionsProcess(id: string) {
  return http.get<ProcessVersionResp[]>(`${BASE_URL}/${id}/versions`)
}

/**
 * @desc 暂停流程的当前生效版本。只拦截新实例发起，已有实例与在途审批任务不受影响。
 * 传生效版本 ID（effectiveId），不是最新版本 ID。
 */
export function pauseProcess(id: string) {
  return http.post(`${BASE_URL}/${id}/pause`)
}

/** @desc 恢复流程的当前生效版本，恢复后可再次发起；后端会校验绑定的表单仍有效 */
export function resumeProcess(id: string) {
  return http.post(`${BASE_URL}/${id}/resume`)
}

/** @desc 发布流程定义 */
export function publishProcess(id: string) {
  return http.post(`${BASE_URL}/${id}/publish`)
}
