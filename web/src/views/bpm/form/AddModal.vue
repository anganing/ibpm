<template>
  <a-modal
    v-model:visible="visible"
    title="新增表单"
    :mask-closable="false"
    :esc-to-close="false"
    :width="width >= 500 ? 500 : '100%'"
    draggable
    ok-text="确定并设计"
    @before-ok="save"
    @close="reset"
  >
    <GiForm ref="formRef" v-model="form" :columns="columns" />
  </a-modal>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { useWindowSize } from '@vueuse/core'
import { createForm } from '@/apis/bpm/form'
import { type ColumnItem, GiForm } from '@/components/GiForm'
import { useResetReactive } from '@/hooks'

defineOptions({ name: 'BpmFormAddModal' })

const emit = defineEmits<{
  /** 表单记录创建成功，回传新表单 ID 供宿主打开设计器 */
  (e: 'save-success', formId: string): void
}>()

const { width } = useWindowSize()

const visible = ref(false)
const formRef = ref<InstanceType<typeof GiForm>>()

const [form, resetForm] = useResetReactive({})

const columns: ColumnItem[] = reactive([
  {
    label: '表单名称',
    field: 'formName',
    type: 'input',
    span: 24,
    props: {
      maxLength: 100,
      placeholder: '请输入表单名称',
    },
    rules: [{ required: true, message: '请输入表单名称' }],
  },
  {
    label: '表单编码',
    field: 'formCode',
    type: 'input',
    span: 24,
    props: {
      maxLength: 40,
      placeholder: '请输入表单编码，如 leave_form',
    },
    rules: [
      { required: true, message: '请输入表单编码' },
      { match: /^[a-z]\w*$/i, message: '编码需以字母开头，仅可包含字母、数字与下划线' },
    ],
  },
])

// 重置
const reset = () => {
  formRef.value?.formRef?.resetFields()
  resetForm()
}

// 保存：先建表单记录拿到 ID，设计器只能按 URL id 定位表单（契约 §8.4）
const save = async () => {
  try {
    const isInvalid = await formRef.value?.formRef?.validate()
    if (isInvalid) return false
    const { data } = await createForm({ formCode: form.formCode, formName: form.formName })
    Message.success('表单已创建，正在打开设计器')
    emit('save-success', data)
    return true
  } catch (error) {
    return false
  }
}

const onAdd = () => {
  reset()
  visible.value = true
}

defineExpose({ onAdd })
</script>

<style scoped lang="scss"></style>
