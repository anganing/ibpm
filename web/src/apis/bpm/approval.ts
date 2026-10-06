import type { CategoryResp } from './categoryType'
import type { ApprovalFormData } from '@/components/ApprovalForm/type'
import http from '@/utils/http'

/**
 * 发起审批页面（T9）专用 API。
 *
 * 与 `./index.ts` 的关系：`index.ts` 为既有文件，由其他 Agent 并行使用，故本文件
 * 独立承载发起审批改造所需的接口，不修改也不重复导出其内容。
 * `PageQuery`、`ApiRes`、`PageRes` 为 `src/types/api.d.ts` 提供的全局类型。
 *
 * T9 仅消费 L0 契约中的只读接口（§8.2 分类列表、§8.6 定义列表），
 * 发起与详情接口沿用既有形状，故此处不重复声明其响应类型。
 */
const BASE_URL = '/admin/approval'

/** 流程分类（契约 §8.2 CategoryResp）——单级扁平，无 children */
export type ApprovalCategory = CategoryResp

/** 已发布流程模型（契约 §8.6 DefinitionResp，含本期新增的 category 字段） */
export interface ApprovalDefinition {
  id: string
  flowCode: string
  flowName: string
  /** 分类 code；后端 ProcessCategoryResp/DefinitionResp 均声明为 `null` 表示未分类 */
  category: string | null
}

/** 定义表单内容（对应 GET /definition/{flowCode}/form） */
export interface ApprovalDefinitionForm {
  definitionId: string
  flowCode: string
  formContent: import('@/components/ApprovalForm/type').ApprovalFormContent
}

/** 发起审批结果 */
export interface ApprovalInstanceStart {
  instanceId: string
}

/**
 * 查询流程分类列表（左树数据源）。
 * 契约 §8.2：`GET /admin/approval/category/list`，不分页，返回全量。
 */
export function listApprovalCategories() {
  return http.get<ApprovalCategory[]>(`${BASE_URL}/category/list`)
}

/**
 * 查询已发布流程模型列表。
 * 契约 §8.6：`GET /admin/approval/definition/list`，`category` 为新增可选参数；
 * 不传时后端行为与改造前完全一致（已发布 + 激活 + 绑定已发布表单）。
 * @param category 分类 code，空串视为不过滤
 */
export function listApprovalDefinitions(category?: string) {
  return http.get<ApprovalDefinition[]>(`${BASE_URL}/definition/list`, { category })
}

/**
 * 读取流程模型的审批表单定义。
 * `flowCode` 需 URL 编码，避免编码中含保留字符时路径被拆断。
 */
export function getApprovalDefinitionForm(flowCode: string) {
  return http.get<ApprovalDefinitionForm>(`${BASE_URL}/definition/${encodeURIComponent(flowCode)}/form`)
}

/** 业务单据 ID 最大长度，与后端 InternalStartReq / OpenStartReq 及 bpm_form_data.business_id 列宽一致 */
export const BUSINESS_ID_MAX_LENGTH = 40

/**
 * 发起审批。
 *
 * `businessId` 为独立于动态表单的发起参数，不并入 `formData`：
 * 填写时传去掉首尾空格的真实业务单据 ID，留空时省略该字段，由后端生成 UUID。
 *
 * @param flowCode   流程编码
 * @param formData   表单数据
 * @param businessId 业务单据 ID，选填
 */
export function startApproval(flowCode: string, formData: ApprovalFormData, businessId?: string) {
  return http.post<ApprovalInstanceStart>(`${BASE_URL}/instance`, { flowCode, formData, businessId })
}
