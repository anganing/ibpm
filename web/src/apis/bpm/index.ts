import type * as T from './type'
import type { ApprovalFormData } from '@/components/ApprovalForm/type'
import http from '@/utils/http'

export type * from './type'

const BASE_URL = '/admin/approval'

export function listApprovalDefinitions() {
  return http.get<T.ApprovalDefinition[]>(`${BASE_URL}/definition/list`)
}

export function getApprovalDefinitionForm(flowCode: string) {
  return http.get<T.ApprovalDefinitionForm>(`${BASE_URL}/definition/${encodeURIComponent(flowCode)}/form`)
}

export function startApproval(flowCode: string, formData: ApprovalFormData) {
  return http.post<T.ApprovalInstanceStart>(`${BASE_URL}/instance`, { flowCode, formData })
}

export function listApprovalTasks(query: PageQuery) {
  return http.get<T.ApprovalTaskPage>(`${BASE_URL}/task/page`, query)
}

export function getApprovalInstance(id: string) {
  return http.get<T.ApprovalInstanceDetail>(`${BASE_URL}/instance/${id}`)
}

export function approveTask(id: string, message: string) {
  return http.post<T.ApprovalTaskAction>(`${BASE_URL}/task/${id}/approve`, { message })
}

export function rejectTask(id: string, message: string) {
  return http.post<T.ApprovalTaskAction>(`${BASE_URL}/task/${id}/reject`, { message })
}
