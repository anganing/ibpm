<template>
  <a-drawer
    v-model:visible="visible"
    :title="title"
    :width="drawerWidth"
    unmount-on-close
    :footer="false"
    @cancel="onClose"
  >
    <!--
      预览为可交互、不可提交：控件可正常填写、子表单与表格表单可增删行，
      但不提供任何保存入口，也不调用保存定义 / 发起流程 / 任务办理接口。
      填写内容只存在于本抽屉的内存中：unmount-on-close + 打开序号 + key 三重隔离，
      换一份表单或关闭抽屉都不会残留上一份的控件与数据，也不会回写表单定义。
    -->
    <a-spin :loading="loading" class="preview-drawer__body">
      <a-result
        v-if="errorMessage"
        status="warning"
        title="暂无可预览的表单"
        :subtitle="errorMessage"
      />
      <a-empty v-else-if="!formContent" description="该表单尚未保存表单内容" />
      <ApprovalForm
        v-else
        :key="previewKey"
        v-model="formData"
        :form-content="formContent"
      />
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import type { ApprovalFormContent, ApprovalFormData } from '@/components/ApprovalForm/type'
import { type FormResp, getFormContent } from '@/apis/bpm/form'

defineOptions({ name: 'BpmFormPreviewDrawer' })

const { width } = useWindowSize()

/**
 * 抽屉宽度取屏幕一半；窄屏下不再按比例收窄，改为至少铺满可用宽度，
 * 否则表格表单的可视区域过小、横向滚动过于频繁。
 */
const drawerWidth = computed(() => (width.value >= 1200 ? '50%' : '100%'))

const visible = ref(false)
const loading = ref(false)
const title = ref('表单预览')
/** 当前展示的表单定义，来自 GET /admin/approval/form/{id}/content 的真实 form_content */
const formContent = ref<ApprovalFormContent>()
const formData = ref<ApprovalFormData>({})
/** 加载失败（如未保存内容、无权限）时的提示，非空则不渲染表单 */
const errorMessage = ref('')
/** 挂载 key：每次打开递增，强制重挂载 ApprovalForm，避免复用上一份控件的禁用/回显状态 */
const previewKey = ref(0)

/** 打开序号：快速切换预览对象时，旧请求迟到的响应不得覆盖新对象 */
let openSeq = 0

async function loadContent(seq: number, id: string) {
  loading.value = true
  try {
    const { data } = await getFormContent(id)
    if (seq !== openSeq) {
      return
    }
    formContent.value = data
    errorMessage.value = ''
  } catch (error) {
    if (seq !== openSeq) {
      return
    }
    // 失败必须整体清空：不能让上一份表单内容留在抽屉里
    formContent.value = undefined
    formData.value = {}
    const msg = error instanceof Error ? error.message : ''
    errorMessage.value = msg || '表单预览加载失败，请稍后重试'
  } finally {
    if (seq === openSeq) {
      loading.value = false
    }
  }
}

/** 以只读方式预览某一行表单的真实内容 */
async function onOpen(record: FormResp) {
  const seq = ++openSeq
  previewKey.value += 1
  formContent.value = undefined
  formData.value = {}
  errorMessage.value = ''
  title.value = `预览：${record.formName}（v${record.version}）`
  visible.value = true
  await loadContent(seq, record.id)
}

function onClose() {
  // 作废在途请求并清空内容，关闭后不残留任何上一份表单数据
  openSeq += 1
  visible.value = false
  loading.value = false
  formContent.value = undefined
  formData.value = {}
  errorMessage.value = ''
}

defineExpose({ onOpen })
</script>

<style scoped lang="scss">
.preview-drawer__body {
  display: block;
  min-height: 120px;
}
</style>
