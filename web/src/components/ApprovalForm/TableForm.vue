<template>
  <div class="_fc-table-form" :class="{ '_fc-disabled': disabled }">
    <!--
      行容器由内层 FormCreate 渲染：rule 是一棵原生 table 规则树，
      列的字段规则挂在原生 td 的 children 上，这样行内字段才会注册进内层表单，
      并由 getChildrenFormData 按行收集数据。

      多列时表格会宽于抽屉，此处用横向滚动容器承接，
      避免抽屉 body 的 overflow-x:hidden 直接裁掉右侧列（选择器/时间/操作列）。
    -->
    <div class="_fc-table-form__scroll">
      <FormCreate
        v-model:api="fapi"
        :option="formOption"
        :rule="rule"
        :extend-option="true"
        :disabled="disabled"
        @change="onFormChange"
        @emit-event="$emit"
      />
    </div>
    <!-- 只读（disabled）时不提供新增入口，与设计器内表格表单的只读表现一致 -->
    <a-button
      v-if="canAdd"
      class="fc-clock _fc-table-form__add"
      type="primary"
      @click="addRow(true)"
    >
      <IconPlus />
      {{ addText }}
    </a-button>
  </div>
</template>

<script setup lang="ts">
import type { Api, Options, Rule } from '@form-create/element-ui'
import { IconPlus } from '@arco-design/web-vue/es/icon'
import FormCreate from '@form-create/element-ui'

defineOptions({ name: 'TableForm' })

const props = withDefaults(defineProps<{
  formCreateInject?: FormCreateInject
  modelValue?: Record<string, any>[]
  columns?: TableFormColumn[]
  filterEmptyColumn?: boolean
  deletable?: boolean
  addable?: boolean
  options?: Options
  max?: number
  min?: number
  disabled?: boolean
}>(), {
  modelValue: () => [],
  columns: () => [],
  filterEmptyColumn: true,
  deletable: true,
  addable: true,
  options: undefined,
  max: undefined,
  min: undefined,
  disabled: false,
})

const emit = defineEmits<{
  'change': [value: Record<string, any>[]]
  'add': [total: number]
  'delete': [index: number]
  'update:modelValue': [value: Record<string, any>[]]
}>()

/** 表格表单列定义，与设计器写入的 props.columns 一致 */
interface TableFormColumn {
  label?: string
  align?: string
  required?: boolean
  style?: Record<string, any>
  rule?: Rule[]
}

/** form-create 注入的运行时对象，字段见 form-create 自定义组件契约 */
interface FormCreateInject {
  form?: any
  t?: (key: string) => string
  preview?: boolean
}

const fapi = ref<Api>()
/** 行规则树，由 loadRule 构建；第一行是表头，第二行是数据行容器 */
const rule = ref<Rule[]>([])
/** 数据行规则集合，每项对应表格中的一行 */
const trs = ref<any[]>([])
/** 新增行的规则模板（由空数据行序列化而来），每次新增都克隆它 */
const copyTrs = ref('')
/** 上一次向外同步的数据快照，用于避免重复 emit */
const oldValue = ref('')

/**
 * form-create 解析规则时会把 props 展开为组件属性，未配置的项会以显式 undefined 传入，
 * 而显式 undefined 不会触发 withDefaults 的默认值，故此处统一兜底为数组。
 */
const rowList = computed<Record<string, any>[]>(() => (Array.isArray(props.modelValue) ? props.modelValue : []))
const columnList = computed<TableFormColumn[]>(() => (Array.isArray(props.columns) ? props.columns : []))

/** 是否展示「添加」入口：只读、不可新增或已达 max 上限时均不展示 */
const canAdd = computed(() => {
  if (props.disabled || !props.addable) {
    return false
  }
  return !props.max || props.max > trs.value.length
})

/** form-create 未注册中文语言包时 t() 返回空串，此处给出兜底文案 */
const addText = computed(() => props.formCreateInject?.t?.('add') || '添加')
const operationText = computed(() => props.formCreateInject?.t?.('operation') || '操作')
const emptyText = computed(() => props.formCreateInject?.t?.('dataEmpty') || '暂无数据')

/** 空态占位行：表格无数据时展示的提示行 */
const emptyRule = computed(() => ({
  type: 'tr',
  _isEmpty: true,
  native: true,
  subRule: true,
  children: [
    {
      type: 'td',
      style: { textAlign: 'center' },
      native: true,
      subRule: true,
      props: { colspan: columnList.value.length + (props.formCreateInject?.preview ? 1 : 2) },
      children: [emptyText.value],
    },
  ],
}))

const formOption = computed<Options>(() => ({
  ...(props.options ?? {}),
  submitBtn: { show: false },
  resetBtn: { show: false },
}))

/**
 * 收集各行的数据：以当前 modelValue 为底，叠加内层表单中该行的字段值，
 * 再按 filterEmptyColumn 过滤掉整行为空的记录。
 */
function collectValue(): Record<string, any>[] {
  const rows = trs.value.map((tr, index) => ({
    ...(rowList.value[index] ?? {}),
    ...(fapi.value?.getChildrenFormData?.(tr) ?? {}),
  }))
  if (!props.filterEmptyColumn) {
    return rows
  }
  return rows.filter((row) => {
    if (row == null) {
      return false
    }
    return Object.keys(row).some((key) => row[key] !== undefined && row[key] !== '' && row[key] !== null)
  })
}

/** 数据变化时向外同步，内容未变则不重复 emit */
function updateValue() {
  const next = JSON.stringify(collectValue())
  if (next !== oldValue.value) {
    oldValue.value = next
    const value = collectValue()
    emit('update:modelValue', value)
    emit('change', value)
  }
}

function onFormChange() {
  updateValue()
}

/** 把某一行的数据写回内层表单 */
function setRowData(index: number, data: Record<string, any>) {
  const tr = trs.value[index]
  if (tr) {
    fapi.value?.setChildrenFormData?.(tr, data, true)
  }
}

/** 用当前 modelValue 重建行集合 */
function updateTable() {
  const next = JSON.stringify(rowList.value)
  if (oldValue.value === next) {
    return
  }
  oldValue.value = next
  const list = rowList.value
  trs.value = trs.value.splice(0, list.length)
  if (list.length) {
    clearEmptyRow()
  } else {
    addEmptyRow()
  }
  list.forEach((row, index) => {
    if (!trs.value[index]) {
      addRow()
    }
    setRowData(index, row ?? {})
  })
  const body = rule.value[0]?.children?.[1] as any
  if (body) {
    body.children = trs.value
  }
}

function addEmptyRow() {
  if (trs.value.length) {
    trs.value.splice(0, trs.value.length)
  }
  trs.value.push(emptyRule.value)
}

function clearEmptyRow() {
  if (trs.value[0]?._isEmpty) {
    trs.value.splice(0, 1)
  }
}

/** 删除一行；disabled、不可删除或已达 min 下限时不动作 */
function delRow(index: number) {
  if (props.disabled || !props.deletable) {
    return
  }
  if (props.min != null && props.min > 0 && trs.value.length <= props.min) {
    return
  }
  trs.value.splice(index, 1)
  updateValue()
  if (trs.value.length) {
    trs.value.forEach((tr) => updateRow(tr))
  } else {
    addEmptyRow()
  }
  emit('delete', index)
}

/** 新增一行；用户点击新增时（fromClick）才向外 emit */
function addRow(fromClick = false) {
  if (fromClick && props.disabled) {
    return
  }
  const parsed = props.formCreateInject?.form?.parseJson?.(copyTrs.value) ?? []
  const tr = parsed[0]
  if (!tr) {
    return
  }
  if (trs.value.length === 1 && trs.value[0]?._isEmpty) {
    trs.value.splice(0, 1)
  }
  trs.value.push(tr)
  updateRow(tr)
  if (fromClick) {
    emit('add', trs.value.length)
    updateValue()
  }
}

/** 刷新行的序号与删除按钮回调 */
function updateRow(tr: any) {
  const index = trs.value.indexOf(tr)
  if (index < 0) {
    return
  }
  if (tr.children?.[0]?.props) {
    // 这里写的是 form-create 原生规则的 props.innerText（渲染文本的约定属性），不是 DOM 节点 API
    // eslint-disable-next-line unicorn/prefer-dom-node-text-content
    tr.children[0].props.innerText = index + 1
  }
  const lastCell = tr.children?.[tr.children.length - 1]
  if (lastCell) {
    // 点击回调挂在操作列单元格上，避免只点到细线条才生效
    lastCell.on = { ...(lastCell.on ?? {}), click: () => delRow(index) }
  }
}

/**
 * 由 columns 构建整张表的规则树：
 * 表头行取每列的 label/style/align，数据行取每列的 rule 作为单元格 children，
 * 末尾追加操作列。copyTrs 是数据行的空模板，供新增时克隆。
 */
function loadRule() {
  const head: any[] = [{ type: 'th', native: true, class: '_fc-tf-head-idx' }]
  const cells: any[] = [{ type: 'td', class: '_fc-tf-idx', native: true, props: { innerText: '0' } }]
  columnList.value.forEach((col) => {
    head.push({
      type: 'th',
      native: true,
      style: { ...(col.style ?? {}), textAlign: col.align || 'center' },
      class: col.required ? '_fc-tf-head-required' : '',
      props: { innerText: col.label || '' },
    })
    cells.push({ type: 'td', native: true, children: [...(col.rule ?? [])] })
  })
  head.push({
    type: 'th',
    native: true,
    class: '_fc-tf-edit fc-clock',
    props: { innerText: operationText.value },
  })
  /*
   * 删除按钮用内联 SVG，而不是设计器的 fc-icon 图标字体：
   * 该字体由 @form-create/designer 的 icon.css 提供，宿主未引入该包，
   * 沿用会让 <i> 变成零尺寸不可见元素（DOM 在、但看不见也点不中)。
   * 路径取自本项目所用的 Arco IconDelete，与工具栏图标同一套视觉。
   *
   * 注意：原生规则的属性要写在 props 上，写 attrs 会被 form-create 丢弃、不落到 DOM。
   */
  cells.push({
    type: 'td',
    native: true,
    class: '_fc-tf-btn fc-clock',
    children: [{
      type: 'svg',
      native: true,
      class: '_fc-table-form__del',
      props: {
        'viewBox': '0 0 48 48',
        'fill': 'none',
        'stroke': 'currentColor',
        'stroke-width': '4',
        'stroke-linecap': 'butt',
        'stroke-linejoin': 'miter',
      },
      children: [{
        type: 'path',
        native: true,
        props: {
          d: 'M5 11h5.5m0 0v29a1 1 0 0 0 1 1h25a1 1 0 0 0 1-1V11m-27 0H16m21.5 0H43m-5.5 0H32m-16 0V7h16v4m-16 0h16M20 18v15m8-15v15',
        },
      }],
    }],
  })

  copyTrs.value = props.formCreateInject?.form?.toJson?.([
    { type: 'tr', native: true, subRule: true, children: cells },
  ]) ?? ''

  rule.value = [{
    type: 'table',
    native: true,
    class: '_fc-tf-table',
    props: { border: '1', cellspacing: '0', cellpadding: '0' },
    children: [
      { type: 'thead', native: true, children: [{ type: 'tr', native: true, children: head }] },
      { type: 'tbody', native: true, children: trs.value },
    ],
  }]
}

watch(() => props.modelValue, () => updateTable(), { deep: true })

onMounted(() => {
  loadRule()
  updateTable()
})

defineExpose({ addRow, delRow, updateValue })
</script>

<style scoped lang="scss">
._fc-table-form {
  width: 100%;
}

._fc-table-form__add {
  margin-top: 8px;
}

/* 列较多时横向滚动，保证每一列都能完整查看与操作，而不是被抽屉裁切 */
._fc-table-form__scroll {
  width: 100%;
  overflow-x: auto;
}

/* 行内单元格：与设计器一致的排布，宿主未引入设计器 CSS 时保证可读 */
._fc-table-form :deep(._fc-tf-table) {
  width: 100%;
  border-collapse: collapse;

  th,
  td {
    padding: 8px 12px;
    border: 1px solid var(--color-border-2, #e5e6eb);
  }

  th {
    font-weight: 500;
    background: var(--color-fill-1, #f7f8fa);
  }
}

._fc-table-form :deep(._fc-tf-idx),
._fc-table-form :deep(._fc-tf-head-idx) {
  width: 48px;
  text-align: center;
}

/* 操作列固定为图标按钮，禁用态不可点击 */
._fc-table-form :deep(._fc-tf-btn) {
  width: 64px;
  text-align: center;
  cursor: pointer;
}

/* 删除图标：内联 SVG，靠 currentColor 取色，故用 color 控制而非 fill */
._fc-table-form :deep(._fc-table-form__del) {
  display: inline-block;
  width: 16px;
  height: 16px;
  color: rgb(var(--danger-6, 245 63 63));
  vertical-align: middle;
}

._fc-table-form :deep(._fc-tf-btn:hover) :deep(._fc-table-form__del) {
  color: rgb(var(--danger-5, 247 114 114));
}

._fc-table-form._fc-disabled :deep(._fc-tf-btn) {
  cursor: not-allowed;
  opacity: 0.5;
}

._fc-table-form :deep(._fc-tf-head-required)::after {
  color: rgb(var(--danger-6, 245 63 63));
  content: '*';
  margin-left: 4px;
}
</style>
