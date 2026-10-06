<template>
  <div class="bpm-designer">
    <iframe class="bpm-designer__iframe" :src="iframeSrc"></iframe>
  </div>
</template>

<script setup lang="ts">
import { getToken } from '@/utils/auth'

defineOptions({ name: 'BpmDesigner' })

/**
 * Warm-Flow 设计器由后端提供（/warm-flow-ui/index.html），其内部以 ../ 解析业务接口，
 * 因此必须让 iframe 地址比 /warm-flow-ui/ 深一层，接口才会落在 /warm-flow/** 上。
 * 接口前缀统一由 VITE_API_PREFIX 提供（dev/test 为 /dev-api、/test-api，生产为 /api），
 * 设计器与普通 HTTP API 均经此前缀同源访问。
 *
 * 设计器会读取 URL 中匹配 warm-flow.token-name 的参数并写入请求头 Authorization，
 * 故此处以 Bearer <token> 传入登录凭证。
 */
const token = getToken()
const params = new URLSearchParams()
if (token) {
  params.set('Authorization', `Bearer ${token}`)
}

const baseUrl = import.meta.env.VITE_API_PREFIX ?? import.meta.env.VITE_API_BASE_URL
const iframeSrc = `${baseUrl}/warm-flow-ui/index.html?${params.toString()}`
</script>

<style scoped lang="scss">
.bpm-designer {
  flex: 1;
  height: 100%;
  overflow: hidden;
}

.bpm-designer__iframe {
  display: block;
  border: 0;
  width: 100%;
  height: 100%;
}
</style>
