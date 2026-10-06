import type { Rule } from '@form-create/element-ui'
import type { ApprovalFormContent } from '@/components/ApprovalForm/type'

/**
 * 第三方对接说明：把已发布表单的 form-create 规则解析为「可对接字段」清单。
 *
 * 依据真实规则解析，不依据中文标签猜字段名：
 * 数据键一律取 `field`（缺失时该组件无法对接，需如实标记），
 * tableForm 取其 `props.columns[].rule` 中的真实列字段，路径形如 `数组字段[].列字段`。
 *
 * 本次只支持普通组件与 tableForm 表格表单；group（可重复子表单）、subForm（分组）
 * 等复杂嵌套结构不解析，遇到时整块列入 unsupported，不静默略过。
 */

/** 对接说明支持的组件类型（type → 展示名称） */
const TYPE_LABELS: Record<string, string> = {
  input: '单行输入',
  textarea: '多行输入',
  inputNumber: '数字输入',
  radio: '单选',
  checkbox: '多选',
  select: '选择器',
  switch: '开关',
  datePicker: '日期',
  timePicker: '时间',
  slider: '滑块',
  rate: '评分',
  colorPicker: '颜色选择',
}

/** 直接渲染为数据字段、可用于对接的组件 */
const SCALAR_TYPES = new Set(Object.keys(TYPE_LABELS))

/** 明确不支持的结构：整块提示，不做递归解析 */
const UNSUPPORTED_TYPES = new Set(['group', 'subForm', 'subFormItem'])

/** 仅承担排版职责、不产生数据层级的容器 */
const LAYOUT_TYPES = new Set(['fcRow', 'col', 'fcRowWrap', 'div', 'el-row', 'el-col'])

/** 值类型展示名 */
const VALUE_LABELS: Record<string, string> = {
  string: 'string',
  number: 'number',
  boolean: 'boolean',
  array: 'array',
}

/** 选项的一条可选值 */
export interface FieldOption {
  label: string
  /** 实际提交值，保留原始类型（数字不会变成字符串） */
  value: any
  /** value 的 JSON 字面量，用于示例中照抄 */
  literal: string
}

/** 一个可对接字段 */
export interface FieldDoc {
  /** 层级：0 顶层字段，1 表格明细列 */
  depth: number
  /** 中文名称 */
  title: string
  /** 真实数据键 / 路径（tableForm 列为 `数组字段[].列字段`） */
  path: string
  /** 组件类型展示名 */
  typeLabel: string
  /** 值类型 */
  valueType: string
  /** 日期时间格式（仅日期/时间类有值） */
  format: string
  /** 可选项；无可选项时为空数组 */
  options: FieldOption[]
  /** 是否必填 */
  required: boolean
  /** 无法静态确定的动态选项提示 */
  dynamicTip: string
}

/** 范围外但有值的组件提示 */
export interface UnsupportedDoc {
  title: string
  path: string
  typeLabel: string
}

export interface ParsedFormDoc {
  fields: FieldDoc[]
  unsupported: UnsupportedDoc[]
}

/** 取规则的 props，缺失时给空对象 */
function propsOf(rule: Rule): Record<string, any> {
  return (rule.props ?? {}) as Record<string, any>
}

/** 把任意值渲染成可直接写进 JSON 的字面量 */
function toLiteral(value: any): string {
  return JSON.stringify(value)
}

/** 读取真实的 valueFormat：未配置时不猜测，交由调用方提示 */
function readFormat(rule: Rule, props: Record<string, any>): string {
  const configured = props.valueFormat ?? props.format ?? props['value-format']
  if (typeof configured === 'string' && configured) {
    return configured
  }
  return ''
}

/** 归一化选项：保留真实值类型，过滤掉明显不是选项的数据 */
function readOptions(rule: Rule): FieldOption[] {
  const raw = (rule as any).options ?? propsOf(rule).options
  if (!Array.isArray(raw)) {
    return []
  }
  return raw
    .filter((item: any) => item != null && typeof item === 'object')
    .map((item: any) => ({
      label: item.label == null ? String(item.value ?? '') : String(item.label),
      value: item.value,
      literal: toLiteral(item.value),
    }))
}

/** 组件类型 → 展示名（未知类型原样展示，便于发现范围外组件） */
function typeLabelOf(type: string): string {
  return TYPE_LABELS[type] ?? type
}

/** 值类型推断：以组件语义为准，不依赖选项值 */
function valueTypeOf(type: string): string {
  switch (type) {
    case 'inputNumber':
      return VALUE_LABELS.number
    case 'switch':
      return VALUE_LABELS.boolean
    case 'checkbox':
      return VALUE_LABELS.array
    case 'select':
      return VALUE_LABELS.array
    default:
      return VALUE_LABELS.string
  }
}

/** select 是否多选：multiple 或 _optionType=2（Warm-Flow 设计器写法的多选标记） */
function isMultiple(rule: Rule): boolean {
  const props = propsOf(rule)
  return props.multiple === true || props._optionType === 2
}

/** 动态选项提示：选项无法静态确定时如实说明，不编造 */
function dynamicTipOf(type: string, options: FieldOption[]): string {
  if (type === 'select' && !options.length) {
    return '选项由接口动态加载，请按实际返回值填写（value 原样回传）'
  }
  if (type === 'checkbox' && !options.length) {
    return '选项由接口动态加载，请按实际返回值填写（提交为 value 数组）'
  }
  return ''
}

/** 解析单个普通组件；范围外的有值组件返回 null 并登记到 unsupported */
function readScalar(rule: Rule, path: string, title: string, depth: number): FieldDoc {
  const type = String(rule.type ?? '')
  const props = propsOf(rule)
  const options = readOptions(rule)
  return {
    depth,
    title: title || '(未命名)',
    path,
    typeLabel: typeLabelOf(type),
    valueType: valueTypeOf(type),
    format: readFormat(rule, props),
    options,
    required: rule.$required === true || props.required === true,
    dynamicTip: dynamicTipOf(type, options),
  }
}

/** tableForm 的列定义，与设计器写入的 props.columns 一致 */
interface TableColumn {
  label?: string
  rule?: Rule[]
}

/** 遍历规则树，按容器语义下钻；只解析普通组件与 tableForm */
function walk(rules: Rule[], prefix: string, depth: number, doc: ParsedFormDoc) {
  rules.forEach((rule) => {
    const type = String(rule.type ?? '')
    const children = Array.isArray(rule.children) ? (rule.children as Rule[]) : []

    // 纯排版容器：不下钻只影响排版，不增加 JSON 数据层级，故原样穿透
    if (LAYOUT_TYPES.has(type) && children.length) {
      walk(children, prefix, depth, doc)
      return
    }

    // 表格表单：数组字段 + 列字段，路径为 数组字段[].列字段
    if (type === 'tableForm') {
      const field = typeof rule.field === 'string' ? rule.field : ''
      const columns: TableColumn[] = Array.isArray(propsOf(rule).columns) ? propsOf(rule).columns : []
      const rowPath = field ? `${prefix}${field}[]` : '(未命名字段)[]'
      if (!field) {
        doc.unsupported.push({
          title: String(rule.title ?? '表格表单'),
          path: rowPath,
          typeLabel: 'tableForm（数组字段未命名）',
        })
      }
      columns.forEach((col) => {
        const colRules = Array.isArray(col.rule) ? col.rule : []
        const colCells = colRules.filter((item) => {
          const childType = String(item.type ?? '')
          return SCALAR_TYPES.has(childType)
        })
        colCells.forEach((cell) => {
          const cellField = typeof cell.field === 'string' ? cell.field : ''
          const colPath = cellField ? `${rowPath}.${cellField}` : `${rowPath}.(未命名列字段)`
          doc.fields.push(readScalar(cell, colPath, col.label ?? '', 1))
        })
        // 列内出现范围外的有值组件（如嵌套 group）时一并登记，不静默遗漏
        colRules.forEach((cell) => {
          const childType = String(cell.type ?? '')
          if (SCALAR_TYPES.has(childType) || LAYOUT_TYPES.has(childType)) {
            return
          }
          doc.unsupported.push({
            title: `${col.label ?? '明细列'} / ${String(cell.title ?? childType)}`,
            path: `${rowPath}.${String(cell.field ?? '')}`,
            typeLabel: typeLabelOf(childType),
          })
        })
      })
      return
    }

    // 范围外的结构（可重复子表单、分组等）：整块提示，本次不解析
    if (UNSUPPORTED_TYPES.has(type)) {
      doc.unsupported.push({
        title: String(rule.title ?? type),
        path: `${prefix}${String(rule.field ?? '')}`,
        typeLabel: typeLabelOf(type),
      })
      return
    }

    // 未识别的容器：若有子规则则继续下钻，避免漏掉其中的普通字段
    if (!SCALAR_TYPES.has(type)) {
      if (children.length) {
        walk(children, prefix, depth, doc)
        return
      }
      doc.unsupported.push({
        title: String(rule.title ?? type),
        path: `${prefix}${String(rule.field ?? '')}`,
        typeLabel: typeLabelOf(type),
      })
      return
    }

    // 普通组件：必须用 field 作为数据键
    const field = typeof rule.field === 'string' ? rule.field : ''
    if (!field) {
      doc.unsupported.push({
        title: String(rule.title ?? '(未命名)'),
        path: `${prefix}(未命名字段)`,
        typeLabel: `${typeLabelOf(type)}（无 field，无法生成数据键）`,
      })
      return
    }
    // tableForm 的列可指定多选，此处按同一语义判定
    const scalar = readScalar(rule, `${prefix}${field}`, String(rule.title ?? ''), depth)
    if (type === 'select' && isMultiple(rule)) {
      scalar.valueType = VALUE_LABELS.array
      scalar.typeLabel = '多选选择器'
    }
    doc.fields.push(scalar)
  })
}

/** 解析已发布表单内容，产出对接字段说明 */
export function parseFormDoc(formContent?: ApprovalFormContent): ParsedFormDoc {
  const doc: ParsedFormDoc = { fields: [], unsupported: [] }
  const rules = (formContent?.rule ?? []) as Rule[]
  walk(rules, '', 0, doc)
  return doc
}

/** 无 valueFormat 时按组件给出格式提示，不统一猜成某一个格式 */
export function formatHint(field: FieldDoc): string {
  if (field.format) {
    return field.format
  }
  if (field.typeLabel === '日期') {
    return '未配置 valueFormat，按组件实际输出为准（常见为 13 位时间戳）'
  }
  if (field.typeLabel === '时间') {
    return '未配置 valueFormat，按组件实际输出为准（如 HH:mm:ss）'
  }
  return '—'
}
