<template>
  <a-drawer
    v-model:visible="visible"
    title="审批回调配置"
    :mask-closable="false"
    :esc-to-close="false"
    :width="width >= 500 ? 500 : '100%'"
    :footer="errorMessage ? false : undefined"
    :ok-button-props="{ disabled: loading || !!errorMessage, loading: saving }"
    @before-ok="save"
    @close="onClose"
  >
    <a-spin :loading="loading" class="callback-drawer__body">
      <a-result v-if="errorMessage" status="warning" title="回调配置加载失败" :subtitle="errorMessage" />
      <a-form v-else ref="formRef" :model="form" :rules="rules" :disabled="loading || saving" size="large" auto-label-width>
        <a-form-item label="应用名称">
          <span>{{ appName }}</span>
        </a-form-item>
        <a-form-item label="启用回调" field="callbackStatus">
          <a-switch
            v-model="form.callbackStatus"
            type="round"
            :checked-value="1"
            :unchecked-value="2"
            checked-text="启用"
            unchecked-text="禁用"
            @change="onStatusChange"
          />
        </a-form-item>
        <a-form-item label="回调地址" field="callbackUrl" :help="CALLBACK_URL_HELP">
          <a-input
            v-model="form.callbackUrl"
            :max-length="500"
            show-word-limit
            placeholder="请输入回调地址"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import type { FormInstance } from '@arco-design/web-vue'
import { Message } from '@arco-design/web-vue'
import { useWindowSize } from '@vueuse/core'
import { type AppCallbackConfigReq, type AppResp, getAppCallbackConfig, saveAppCallbackConfig } from '@/apis/open/app'
import { useResetReactive } from '@/hooks'

defineOptions({ name: 'OpenAppCallbackConfigDrawer' })

const CALLBACK_URL_HELP = '审批结果将通知到此地址，同一应用共用此配置。'

const { width } = useWindowSize()

const dataId = ref('')
const appName = ref('')
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const formRef = ref<FormInstance>()

const [form, resetForm] = useResetReactive({
  callbackStatus: 2,
  callbackUrl: '',
})

/** 打开序号：快速切换应用时，旧请求迟到的响应不得覆盖新应用的配置 */
let openSeq = 0

const isValidHttpUrl = (value: string): boolean => {
  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname !== ''
  } catch {
    return false
  }
}

const rules: FormInstance['rules'] = {
  callbackUrl: [
    {
      validator: (value: string | undefined, cb: (error?: string) => void) => {
        const url = value?.trim()
        if (form.callbackStatus === 1 && !url) {
          return cb('启用回调后必须填写回调地址')
        }
        if (url && !isValidHttpUrl(url)) {
          return cb('回调地址必须是合法的 HTTP/HTTPS 地址')
        }
        cb()
      },
    },
  ],
}

// 切换开关后必填条件变化，清除地址上残留的校验错误
const onStatusChange = () => {
  formRef.value?.clearValidate('callbackUrl')
}

// 重置
const reset = () => {
  formRef.value?.resetFields()
  resetForm()
}

const onClose = () => {
  ++openSeq
  reset()
  dataId.value = ''
  loading.value = false
}

// 保存：成功关闭抽屉，失败保留表单供修正
const save = async () => {
  if (loading.value || saving.value || errorMessage.value || !dataId.value || !formRef.value) return false
  saving.value = true
  const seq = openSeq
  const appId = dataId.value
  try {
    const isInvalid = await formRef.value.validate()
    if (isInvalid || seq !== openSeq) return false
    const data: AppCallbackConfigReq = {
      callbackStatus: form.callbackStatus,
      callbackUrl: form.callbackUrl?.trim() || undefined,
    }
    await saveAppCallbackConfig(appId, data)
    if (seq !== openSeq) return false
    Message.success('保存成功')
    return true
  } catch (error) {
    return false
  } finally {
    saving.value = false
  }
}

// 打开：加载当前应用配置，未配置时展示默认关闭状态
const onOpen = async (record: AppResp) => {
  const seq = ++openSeq
  // 每次打开都重置全部状态，避免残留上一个应用的配置
  dataId.value = record.id
  appName.value = record.name
  errorMessage.value = ''
  reset()
  visible.value = true
  loading.value = true
  try {
    const { data } = await getAppCallbackConfig(record.id)
    if (seq !== openSeq) {
      return
    }
    form.callbackStatus = data.configured ? (data.callbackStatus ?? 2) : 2
    form.callbackUrl = data.configured ? (data.callbackUrl ?? '') : ''
  } catch (error) {
    if (seq !== openSeq) {
      return
    }
    errorMessage.value = error instanceof Error ? error.message : '回调配置加载失败，请稍后重试'
  } finally {
    if (seq === openSeq) {
      loading.value = false
    }
  }
}

defineExpose({ onOpen })
</script>

<style scoped lang="scss">
.callback-drawer__body {
  display: block;
  min-height: 120px;
}
</style>
