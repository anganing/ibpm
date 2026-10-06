import type { Options, Rule } from '@form-create/element-ui'

/** 已解析的表单定义，对应接口 formContent 字段 */
export interface ApprovalFormContent {
  /** form-create 表单规则 */
  rule: Rule[]
  /** form-create 表单配置 */
  option: Options
}

/** 表单数据，字段由表单规则决定 */
export type ApprovalFormData = Record<string, any>
