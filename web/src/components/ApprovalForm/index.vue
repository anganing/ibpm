<template>
  <FormCreate
    v-model="formData"
    v-model:api="formApi"
    class="approval-form"
    :rule="formRule"
    :option="formOption"
  />
</template>

<script setup lang="ts">
import type { Api, Options, Rule } from '@form-create/element-ui'
import { cloneDeep } from 'lodash-es'
import type { ApprovalFormContent, ApprovalFormData } from './type'

defineOptions({ name: 'ApprovalForm' })

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: ApprovalFormData]
}>()

/** 组件属性定义 */
interface Props {
  /** 已解析的表单定义，直接来源于接口 formContent，禁止二次 JSON.parse */
  formContent?: ApprovalFormContent
  /** 表单数据 */
  modelValue?: ApprovalFormData
  /**
   * 只读模式：控件禁用、不可编辑，子表单与表格表单不可增删行。
   * 详情页（含待办/已办/我的流程）使用；表单预览不使用，预览要求可交互。
   */
  readonly?: boolean
}

const formApi = ref<Api>()
const formData = computed({
  get: () => props.modelValue ?? {},
  set: (value: ApprovalFormData) => emit('update:modelValue', value),
})

/**
 * 把设计器保存的规则归一化为运行时可用形态。
 *
 * Warm-Flow 设计器的「表格表单」只在 props.columns 里保存列定义，
 * 不写 props.modelValue；而 tableForm 组件按 form-create 子表单契约
 * 以 modelValue 数组承载行数据，缺省为空数组时才不会建行。
 * 此处按需补上，不覆盖已有值，也不改写其它字段。
 */
function normalizeRule(rules: Rule[]): Rule[] {
  rules.forEach((rule) => {
    if (rule.type === 'tableForm') {
      const ruleProps = (rule.props ?? {}) as Record<string, any>
      if (!Array.isArray(ruleProps.modelValue)) {
        ruleProps.modelValue = []
      }
      rule.props = ruleProps
    }
    const children = rule.children
    if (Array.isArray(children) && children.length) {
      normalizeRule(children as Rule[])
    }
  })
  return rules
}

/**
 * form-create 会直接改写传入的 rule / option，而接口返回的 formContent 与
 * bpm_form_snapshot 快照共用同一对象引用，故需深拷贝隔离。
 *
 * 注意：拷贝只能在 formContent 变化时做一次。若放进 computed 每次返回新对象，
 * form-create 内部的 refRule 会与传入对象脱钩，导致数据无法回显到控件。
 */
const clonedContent = ref<ApprovalFormContent>({ rule: [], option: {} })
watch(() => props.formContent, (content) => {
  clonedContent.value = cloneDeep({
    rule: normalizeRule(content?.rule ?? []),
    option: content?.option ?? {},
  })
}, { immediate: true })

const formRule = computed<Rule[]>(() => clonedContent.value.rule as Rule[])

/**
 * 审批场景由页面自行提交，隐藏 form-create 自带按钮。
 * forceCoverValue 让外部传入的 modelValue 以覆盖方式同步，缺省字段会被清空，
 * 否则 form-create 的 setValue 只更新出现过的字段，残留上一次的数据。
 */
const formOption = computed<Options>(() => ({
  ...clonedContent.value.option,
  forceCoverValue: true,
  submitBtn: { show: false },
  resetBtn: { show: false },
}))

/**
 * 校验表单，供发起页面在提交前调用
 * @returns 校验是否通过
 */
const validate = async (): Promise<boolean> => {
  if (!formApi.value) return false
  try {
    await formApi.value.validate()
    return true
  } catch {
    return false
  }
}

/** 只读模式通过运行时配置禁用表单，不改写传入的表单定义 */
watch([formApi, () => props.readonly], ([api, readonly]) => {
  api?.disabled(readonly)
}, { immediate: true })

defineExpose({ validate })
</script>

<style scoped lang="scss">
.approval-form {
  width: 100%;
}
</style>
