<template>
  <a-modal
    v-model:visible="visible"
    :fullscreen="true"
    :footer="false"
    :mask-closable="false"
    :esc-to-close="false"
    title="流程设计"
    @close="onClose"
  >
    <div class="bpm-designer-modal">
      <a-spin v-if="loading" class="bpm-designer-modal__loading" tip="正在加载设计器..." />
      <iframe v-else-if="iframeSrc" ref="iframeRef" class="bpm-designer-modal__iframe" :src="iframeSrc"></iframe>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { getToken } from '@/utils/auth'

defineOptions({ name: 'BpmDesignerModal' })

const emit = defineEmits<{
  /** 弹窗关闭时触发，宿主据此刷新列表 */
  (e: 'close'): void
}>()

const visible = ref(false)
const loading = ref(false)
const iframeSrc = ref('')
const iframeRef = ref<HTMLIFrameElement>()

/**
 * Warm-Flow 设计器由后端提供（/warm-flow-ui/index.html），其内部以 ../ 解析业务接口，
 * 因此必须让 iframe 地址比 /warm-flow-ui/ 深一层，接口才会落在 /warm-flow/** 上。
 * 接口前缀统一由 VITE_API_PREFIX 提供（dev/test 为 /dev-api、/test-api，生产为 /api），
 * 设计器与普通 HTTP API 均经此前缀同源访问。
 *
 * 设计器会读取 URL 中匹配 warm-flow.token-name 的参数并写入请求头 Authorization，
 * 故此处以 Bearer <token> 传入登录凭证。
 *
 * 「新增」不带 id 打开：query-def 在不传 id 时返回一份初始数据且不落库，
 * saveDef 在定义 ID 为 null 时新建记录（已核对 warm-flow 1.8.9 源码）。
 * 保存成功后设计器会执行 window.parent.postMessage({ method: 'close' })，宿主动关闭弹窗，
 * 使设计器实例随 iframe 卸载，从而避免同一实例再次保存造成重复创建定义。
 * 「修改」仍带已有 ID，设计器按 URL id 定位流程。
 * 分类选择器由设计器自身从 query-def 响应读取 categoryList，不依赖 URL 参数。
 */
function buildIframeSrc(definitionId?: string) {
  const params = new URLSearchParams()
  const token = getToken()
  if (token) {
    params.set('Authorization', `Bearer ${token}`)
  }
  if (definitionId) {
    params.set('id', definitionId)
  }
  const baseUrl = import.meta.env.VITE_API_PREFIX ?? import.meta.env.VITE_API_BASE_URL
  return `${baseUrl}/warm-flow-ui/index.html?${params.toString()}`
}

/**
 * 设计器保存成功后通知宿主关闭。
 * 只接受来自本弹窗 iframe 的消息：origin 同源、source 为该 iframe 的 contentWindow，
 * 避免同页其他 iframe（如表单设计器）的同类消息误触发关闭。
 */
function onDesignerMessage(event: MessageEvent) {
  if (!visible.value) return
  if (event.origin !== window.location.origin) return
  if (!iframeRef.value || event.source !== iframeRef.value.contentWindow) return
  if (!event.data || event.data.method !== 'close') return
  onClose()
}

/** 打开设计器；传 definitionId 为「修改」，不传为「新增」 */
function onOpen(definitionId?: string) {
  loading.value = true
  iframeSrc.value = ''
  // 先挂载空 iframe 容器再赋值地址，避免复用上一次的 src 造成设计器不重载
  nextTick(() => {
    iframeSrc.value = buildIframeSrc(definitionId)
    loading.value = false
    visible.value = true
  })
}

/** 关闭并卸载 iframe；close 事件只由 onClose 发出一次，避免宿主重复刷新列表 */
function onClose() {
  if (!visible.value && !iframeSrc.value) return
  visible.value = false
  // 卸载 iframe，终止设计器内部状态，保证下次以新 ID 重新加载
  iframeSrc.value = ''
  loading.value = false
  emit('close')
}

onMounted(() => {
  window.addEventListener('message', onDesignerMessage)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onDesignerMessage)
})

defineExpose({ onOpen })
</script>

<style scoped lang="scss">
.bpm-designer-modal {
  height: calc(100vh - 100px);
  overflow: hidden;
}

.bpm-designer-modal__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.bpm-designer-modal__iframe {
  display: block;
  border: 0;
  width: 100%;
  height: 100%;
}
</style>
