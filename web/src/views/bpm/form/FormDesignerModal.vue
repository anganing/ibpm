<template>
  <a-modal
    v-model:visible="visible"
    title="表单设计"
    :fullscreen="true"
    :footer="false"
    :mask-closable="false"
    :esc-to-close="false"
    @close="onClose"
  >
    <div class="bpm-form-designer-modal">
      <a-spin v-if="loading" class="bpm-form-designer-modal__loading" tip="正在加载表单设计器..." />
      <iframe v-else-if="iframeSrc" class="bpm-form-designer-modal__iframe" :src="iframeSrc"></iframe>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { getToken } from '@/utils/auth'

defineOptions({ name: 'BpmFormDesignerModal' })

const emit = defineEmits<{
  /** 弹窗关闭时触发，宿主据此刷新列表 */
  (e: 'close'): void
}>()

const visible = ref(false)
const loading = ref(false)
const iframeSrc = ref('')

/**
 * Warm-Flow 表单设计器由后端提供（/warm-flow-ui/index.html），其内部以 ../ 解析业务接口，
 * 因此必须让 iframe 地址比 /warm-flow-ui/ 深一层，接口才会落在 /warm-flow/** 上。
 * 接口前缀统一由 VITE_API_PREFIX 提供（dev/test 为 /dev-api、/test-api，生产为 /api），
 * 设计器与普通 HTTP API 均经此前缀同源访问。
 *
 * 设计器会读取 URL 中匹配 warm-flow.token-name 的参数并写入请求头 Authorization，
 * 故此处以 Bearer <token> 传入登录凭证。
 *
 * 设计器以 type 参数选择要渲染的界面（form / FlowChart / formCreate），
 * 表单设计器的入口为 type=form，并以 id 定位表单：设计器内部调用
 * GET /warm-flow/form-content/{id} 读取内容、POST /warm-flow/form-content 回写内容。
 * 该 id 对应 flow_form 主键，因此宿主必须保证「先有表单记录、再开设计器」，
 * 否则设计器会提示「流程id不能为空！」（原文如此，id 在表单设计器语境下指表单记录 ID）。
 *
 * 注意 type 与 id 是两个独立的 URL 参数：type 只决定渲染哪个界面，id 才是表单定位依据。
 */
function buildIframeSrc(formId: string) {
  const params = new URLSearchParams()
  const token = getToken()
  if (token) {
    params.set('Authorization', `Bearer ${token}`)
  }
  params.set('type', 'form')
  params.set('id', formId)
  const baseUrl = import.meta.env.VITE_API_PREFIX ?? import.meta.env.VITE_API_BASE_URL
  return `${baseUrl}/warm-flow-ui/index.html?${params.toString()}`
}

/** 以指定表单 ID 打开表单设计器 */
function onOpen(formId: string) {
  if (!formId) return
  loading.value = true
  iframeSrc.value = ''
  // 先挂载空 iframe 容器再赋值地址，避免复用上一次的 src 造成设计器不重载
  nextTick(() => {
    iframeSrc.value = buildIframeSrc(formId)
    loading.value = false
    visible.value = true
  })
}

function onClose() {
  visible.value = false
  // 卸载 iframe，终止设计器内部状态，保证下次以新 ID 重新加载
  iframeSrc.value = ''
  loading.value = false
  emit('close')
}

defineExpose({ onOpen })
</script>

<style scoped lang="scss">
.bpm-form-designer-modal {
  height: calc(100vh - 100px);
  overflow: hidden;
}

.bpm-form-designer-modal__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.bpm-form-designer-modal__iframe {
  display: block;
  border: 0;
  width: 100%;
  height: 100%;
}
</style>
