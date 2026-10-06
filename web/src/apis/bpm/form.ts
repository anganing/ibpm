import type { ApprovalFormContent } from '@/components/ApprovalForm/type'
import http from '@/utils/http'

const BASE_URL = '/admin/approval/form'

/** @desc 流程表单列表行，契约见 docs/bpm-优化.md §3.3 */
export interface FormResp {
  /** 表单 ID（十进制字符串） */
  id: string
  /** 表单编码 */
  formCode: string
  /** 表单名称 */
  formName: string
  /** 版本号 */
  version: string
  /** 发布状态：0 未发布 / 1 已发布（历史数据可能残留 9，但不再作为业务状态使用） */
  isPublish: number
  /** 更新时间 */
  updateTime: string
}

/** @desc 流程表单分页响应 */
export interface FormPageResp {
  list: FormResp[]
  total: number
}

/** @desc 流程表单分页查询参数 */
export interface FormQuery extends PageQuery {
  formName?: string
  formCode?: string
  isPublish?: number
}

/** @desc 创建表单请求 */
export interface FormReq {
  formCode: string
  formName: string
}

/** @desc 流程表单分页列表 */
export function pageForm(query: FormQuery) {
  return http.get<FormPageResp>(`${BASE_URL}/page`, query)
}

/** @desc 创建表单（未发布），返回新表单 ID 供设计器定位 */
export function createForm(data: FormReq) {
  return http.post<string>(`${BASE_URL}`, data)
}

/**
 * @desc 打开设计器：未发布表单返回自身 ID；已发布表单返回复制/复用出的未发布新版本 ID。
 * 版本号与复制动作全部由后端完成，前端只拿最终 ID。
 */
export function designForm(id: string) {
  return http.post<string>(`${BASE_URL}/${id}/design`)
}

/** @desc 读取表单定义（rule + option），供只读预览 */
export function getFormContent(id: string) {
  return http.get<ApprovalFormContent>(`${BASE_URL}/${id}/content`)
}

/** @desc 删除表单（已发布或已被流程定义引用时由后端拒绝，前端不硬编码该判断） */
export function deleteForm(id: string) {
  return http.del(`${BASE_URL}/${id}`)
}

/** @desc 发布表单 */
export function publishForm(id: string) {
  return http.post(`${BASE_URL}/${id}/publish`)
}
