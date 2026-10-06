<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    :mask-closable="false"
    :esc-to-close="false"
    :body-style="{ maxHeight: width >= 650 ? '76vh' : '100vh' }"
    :width="width >= 650 ? 650 : '100%'"
    draggable
    @before-ok="save"
    @close="reset"
  >
    <a-form ref="formRef" :model="form" :rules="formRules" auto-label-width :layout="width >= 700 ? 'horizontal' : 'vertical'">
      <a-form-item label="分类名称" field="name">
        <a-input v-model="form.name" placeholder="请输入分类名称" :max-length="50" show-word-limit allow-clear />
      </a-form-item>
      <a-form-item label="分类编码" field="code">
        <a-input v-model="form.code" placeholder="请输入分类编码" :max-length="50" show-word-limit allow-clear />
      </a-form-item>
      <a-form-item label="排序" field="sort">
        <a-input-number v-model="form.sort" placeholder="请输入排序" :min="1" mode="button" style="width: 150px" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { type FormInstance, Message } from '@arco-design/web-vue'
import { useWindowSize } from '@vueuse/core'
import { type CategoryReq, type CategoryResp, addCategory, updateCategory } from '@/apis/bpm/category'
import { useResetReactive } from '@/hooks'

const emit = defineEmits<{
  (e: 'save-success'): void
}>()

const { width } = useWindowSize()

const dataId = ref('')
const visible = ref(false)
const isUpdate = computed(() => !!dataId.value)
const title = computed(() => (isUpdate.value ? '修改流程分类' : '新增流程分类'))
const formRef = ref<FormInstance>()

const [form, resetForm] = useResetReactive<CategoryReq>({
  name: '',
  code: '',
  sort: 1,
})

const formRules: FormInstance['rules'] = {
  name: [{ required: true, message: '请输入分类名称' }],
  code: [{ required: true, message: '请输入分类编码' }],
}

// 重置
const reset = () => {
  formRef.value?.resetFields()
  resetForm()
}

// 保存
const save = async () => {
  try {
    const isInvalid = await formRef.value?.validate()
    if (isInvalid) return false
    if (isUpdate.value) {
      await updateCategory(form, dataId.value)
      Message.success('修改成功')
    } else {
      await addCategory(form)
      Message.success('新增成功')
    }
    emit('save-success')
    return true
  } catch (error) {
    return false
  }
}

// 新增
const onAdd = () => {
  reset()
  dataId.value = ''
  visible.value = true
}

// 修改（后端无单条查询接口，直接回填行数据）
const onUpdate = (record: CategoryResp) => {
  reset()
  dataId.value = record.id
  form.name = record.name
  form.code = record.code
  form.sort = record.sort
  visible.value = true
}

defineExpose({ onAdd, onUpdate })
</script>

<style scoped lang="scss"></style>
