import type { ApprovalFormContent, ApprovalFormData } from '@/components/ApprovalForm/type'

export interface ApprovalDefinition {
  id: string
  flowCode: string
  flowName: string
}

export interface ApprovalDefinitionForm {
  definitionId: string
  flowCode: string
  formContent: ApprovalFormContent
}

export interface ApprovalInstanceStart {
  instanceId: string
}

export interface ApprovalTask {
  taskId: string
  instanceId: string
  flowCode: string
  flowName: string
  nodeName: string
  createTime: string
}

export interface ApprovalTaskPage {
  list: ApprovalTask[]
  total: number
}

export interface ApprovalInstanceDetail {
  instanceId: string
  definitionId: string
  flowCode: string
  flowName: string
  businessId: string
  flowStatus: string
  lastSkipType: string | null
  initiatorUserId: string
  formContent: ApprovalFormContent
  formData: ApprovalFormData
}

export interface ApprovalTaskAction {
  instanceId: string
  flowStatus: string
  lastSkipType: string | null
}
