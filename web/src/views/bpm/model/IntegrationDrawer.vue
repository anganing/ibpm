<template>
  <a-drawer
    v-model:visible="visible"
    title="对接说明"
    :width="drawerWidth"
    unmount-on-close
    :footer="false"
    @cancel="onClose"
  >
    <a-spin :loading="loading" class="integration-drawer__body">
      <!-- 无可发起版本 / 无可用表单 / 已暂停时只展示原因，不生成冒充可用的示例 -->
      <a-result
        v-if="errorMessage"
        status="warning"
        title="当前无法生成对接说明"
        :subtitle="errorMessage"
      />
      <template v-else-if="info">
        <a-descriptions :column="1" size="small" class="integration-drawer__summary" bordered>
          <a-descriptions-item label="流程名称">{{ info.flowName }}</a-descriptions-item>
          <a-descriptions-item label="流程编码">
            <span class="integration-drawer__mono">{{ info.flowCode }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="当前生效版本">
            v{{ info.definitionVersion }}
            <span class="integration-drawer__muted">（definitionId：{{ info.definitionId }}）</span>
          </a-descriptions-item>
          <a-descriptions-item label="绑定表单">
            {{ info.formName }}（v{{ info.formVersion }}）
            <span class="integration-drawer__muted">（formId：{{ info.formId }}）</span>
          </a-descriptions-item>
          <a-descriptions-item label="可发起">
            <a-tag color="green" size="small">可发起</a-tag>
          </a-descriptions-item>
        </a-descriptions>

        <a-tabs v-model:active-key="activeTab" class="integration-drawer__tabs">
          <a-tab-pane key="fields" title="字段说明">
            <a-alert type="normal" class="integration-drawer__tip">
              字段信息按当前生效版本绑定的已发布表单实时解析，<b>不是</b>按中文标签推测。
              流程或表单发布新版本后，请重新核对本说明。
            </a-alert>

            <a-empty v-if="!doc.fields.length" description="该表单未解析出可对接字段" />
            <a-table
              v-else
              row-key="path"
              size="small"
              :data="doc.fields"
              :columns="fieldColumns"
              :pagination="false"
              :scroll="{ x: 980 }"
            >
              <template #title="{ record }">
                <span :style="{ paddingLeft: `${record.depth * 16}px` }">{{ record.title }}</span>
              </template>
              <template #path="{ record }">
                <span class="integration-drawer__mono">{{ record.path }}</span>
              </template>
              <template #valueType="{ record }">
                <a-tag size="small">{{ record.valueType }}</a-tag>
              </template>
              <template #format="{ record }">
                <span class="integration-drawer__muted">{{ formatHint(record) }}</span>
              </template>
              <template #options="{ record }">
                <template v-if="record.options.length">
                  <div v-for="option in record.options" :key="String(option.value)" class="integration-drawer__option">
                    <span>{{ option.label }}</span>
                    <span class="integration-drawer__muted">→ 传 value</span>
                    <code class="integration-drawer__code">{{ option.literal }}</code>
                  </div>
                </template>
                <span v-else-if="record.dynamicTip" class="integration-drawer__warn">{{ record.dynamicTip }}</span>
                <span v-else class="integration-drawer__muted">—</span>
              </template>
            </a-table>

            <a-alert v-if="doc.unsupported.length" type="warning" class="integration-drawer__tip">
              <template #title>以下组件的字段说明暂未支持，请勿据此判定文档完整</template>
              <ul class="integration-drawer__unsupported">
                <li v-for="item in doc.unsupported" :key="`${item.path}-${item.typeLabel}`">
                  {{ item.title }}（{{ item.path }}）—— {{ item.typeLabel }}
                </li>
              </ul>
              本次仅支持普通表单组件与 tableForm 表格表单。
            </a-alert>
          </a-tab-pane>

          <a-tab-pane key="sample" title="填写示例" destroy-on-hide>
            <a-alert type="normal" class="integration-drawer__tip">
              填写内容仅用于生成本抽屉内的对接请求示例：不保存表单定义、不发起流程、不读取真实历史单据或用户资料。
            </a-alert>
            <div class="integration-drawer__sample-actions">
              <a-button size="small" @click="onResetSample">
                <template #icon><IconRefresh /></template>
                重置示例数据
              </a-button>
            </div>
            <ApprovalForm
              v-if="info.formContent"
              :key="sampleKey"
              v-model="sampleData"
              :form-content="info.formContent"
            />
          </a-tab-pane>

          <a-tab-pane key="request" title="请求示例" destroy-on-hide>
            <a-form :model="requestForm" layout="vertical" class="integration-drawer__params">
              <a-form-item
                field="initiatorUserId"
                label="initiatorUserId（ibpm 发起人用户 ID）"
                :help="INITIATOR_HELP"
              >
                <a-input v-model="initiatorUserId" placeholder="填写 ibpm 系统用户 ID" allow-clear />
              </a-form-item>
              <a-form-item field="businessId" label="businessId（第三方业务单据 ID）" :help="BUSINESS_ID_HELP">
                <a-input
                  v-model="businessId"
                  :max-length="BUSINESS_ID_MAX_LENGTH"
                  placeholder="填写第三方业务单据 ID"
                  allow-clear
                />
              </a-form-item>
            </a-form>

            <a-descriptions :column="1" size="small" bordered class="integration-drawer__request-meta">
              <a-descriptions-item label="请求方法">POST</a-descriptions-item>
              <a-descriptions-item label="请求路径">
                <span class="integration-drawer__mono">/open/approval/instance</span>
              </a-descriptions-item>
              <a-descriptions-item label="Content-Type">
                <span class="integration-drawer__mono">application/json</span>
              </a-descriptions-item>
              <a-descriptions-item label="签名查询参数">
                <span class="integration-drawer__mono">accessKey · timestamp · nonce · sign</span>
              </a-descriptions-item>
            </a-descriptions>

            <a-alert type="normal" class="integration-drawer__tip">
              签名遵循本项目实现：<code class="integration-drawer__code">md5(accessKey=&lt;ak&gt;&amp;key=&lt;sk&gt;&amp;nonce=&lt;nonce&gt;&amp;timestamp=&lt;ts&gt;)</code>
              —— 应用密钥 <b>key</b> 作为普通参数参与字典序拼接，与 Sa-Token 官方
              “省略 sign 后追加 &amp;key=…” 的算法不同。此处只说明规则，
              不读取也不展示任何应用 Secret Key。
            </a-alert>

            <a-alert v-if="!hasSampleData" type="warning" class="integration-drawer__tip">
              尚未填写表单数据，formData 为空。请到「填写示例」页填写单头字段，
              并至少新增一行明细后再复制，空对象不构成完整样例。
            </a-alert>
            <a-alert v-else type="success" class="integration-drawer__tip">
              formData 来自「填写示例」页表单组件的实时数据，未经过 DOM 拼值。
              修改或删除明细行后，返回本页签即取到最新数据。
            </a-alert>

            <div class="integration-drawer__actions">
              <a-button type="primary" @click="onCopyRequest">复制完整请求体</a-button>
              <a-button @click="onCopyFormData">复制 formData</a-button>
              <a-button @click="onCopyCurl">复制 curl 示例</a-button>
            </div>

            <pre class="integration-drawer__pre"><code>{{ requestJson }}</code></pre>

            <div class="integration-drawer__section-head">
              <span>curl 示例</span>
              <a-link @click="onCopyCurl">复制</a-link>
            </div>
            <a-alert type="normal" class="integration-drawer__tip">
              <b>&lt;ak&gt;</b>、<b>&lt;sk&gt;</b>、<b>&lt;ts&gt;</b>、<b>&lt;nonce&gt;</b>、<b>&lt;sign&gt;</b> 均为占位符，
              请替换为真实值；<b>&lt;sk&gt;</b> 是应用 Secret Key，只在第三方服务端参与签名计算，不得写入前端或日志。
              签名取值见上方规则，本项目<b>不读取也不展示</b>任何真实密钥或令牌。
            </a-alert>
            <pre class="integration-drawer__pre"><code>{{ curlSample }}</code></pre>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { IconRefresh } from '@arco-design/web-vue/es/icon'
import type { TableInstance } from '@arco-design/web-vue'
import { formatHint, parseFormDoc } from './integration/fieldDoc'
import { type ProcessIntegrationResp, integrationProcess } from '@/apis/bpm/model'
import { BUSINESS_ID_MAX_LENGTH } from '@/apis/bpm/approval'
import ApprovalForm from '@/components/ApprovalForm/index.vue'
import type { ApprovalFormData } from '@/components/ApprovalForm/type'
import { copyText, isMobile } from '@/utils'

defineOptions({ name: 'BpmModelIntegrationDrawer' })

/** initiatorUserId 说明：第三方需传本系统真实用户 ID，而不是第三方自己的用户标识 */
const INITIATOR_HELP = '本系统（ibpm）用户 ID，需真实存在且未停用；不是第三方自身的用户标识。'
const BUSINESS_ID_HELP = `第三方业务单据 ID，最长 ${BUSINESS_ID_MAX_LENGTH} 个字符，发起后与审批实例关联。`

const visible = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const info = ref<ProcessIntegrationResp>()
const activeTab = ref<string>('fields')

/** 填写示例：仅存在于本抽屉内存，关闭即清空 */
const sampleData = ref<ApprovalFormData>({})
/** 挂载 key：重置示例数据时递增，强制表单组件重建，避免残留上一次的控件状态 */
const sampleKey = ref(0)

/** 发起参数与动态表单数据分离，各自独立输入 */
const initiatorUserId = ref('')
const businessId = ref('')
const requestForm = computed(() => ({ initiatorUserId: initiatorUserId.value, businessId: businessId.value }))

/** 打开序号：快速切换流程时，旧请求迟到的响应不得覆盖新流程 */
let openSeq = 0

/**
 * 抽屉宽度取屏幕一半（与发起抽屉、详情抽屉一致）；
 * 窄屏下铺满，否则字段表与 JSON 区域的可视范围过小。
 */
const drawerWidth = computed(() => (isMobile() ? '100%' : '50%'))

const doc = computed(() => parseFormDoc(info.value?.formContent))

const fieldColumns: TableInstance['columns'] = [
  { title: '中文名称', dataIndex: 'title', slotName: 'title', width: 180 },
  { title: '数据键 / 路径', dataIndex: 'path', slotName: 'path', width: 240 },
  { title: '组件类型', dataIndex: 'typeLabel', width: 130 },
  { title: '值类型', dataIndex: 'valueType', slotName: 'valueType', width: 100 },
  { title: '日期时间格式', dataIndex: 'format', slotName: 'format', width: 200 },
  { title: '选项（label → 实际 value）', dataIndex: 'options', slotName: 'options', width: 240 },
]

/** 表单里是否已有可用的示例数据：单头字段或明细行任一非空即算 */
const hasSampleData = computed(() => {
  const data = sampleData.value
  if (!data || typeof data !== 'object') {
    return false
  }
  return Object.keys(data).some((key) => {
    const value = data[key]
    if (value == null || value === '') {
      return false
    }
    if (Array.isArray(value)) {
      return value.length > 0
    }
    return true
  })
})

/**
 * 请求体：formData 始终是对象，不做二次序列化。
 * 不加入接口不接受的 definitionId / formId / version / rule / option，版本信息只出现在顶部说明中。
 */
const requestBody = computed(() => ({
  flowCode: info.value?.flowCode ?? '',
  initiatorUserId: initiatorUserId.value,
  businessId: businessId.value,
  formData: sampleData.value ?? {},
}))

const requestJson = computed(() => JSON.stringify(requestBody.value, null, 2))

/**
 * curl 示例：与 requestJson 同源拼装，保证两边 body 完全一致、不会各写一份而漂移。
 *
 * 签名四参数一律用占位符，不读取应用 Secret Key、真实 token 或签名值：
 * sign 需在第三方服务端用 Secret Key 计算，前端不参与也不要展示。
 */
const curlSample = computed(() => {
  const query = 'accessKey=<ak>&timestamp=<ts>&nonce=<nonce>&sign=<sign>'
  return [
    `curl -X POST 'http://<ibpm-host>/open/approval/instance?${query}' \\`,
    '  -H \'Content-Type: application/json\' \\',
    `  -d '${JSON.stringify(requestBody.value)}'`,
  ].join('\n')
})

/**
 * 复制前统一走 JSON.parse 往返，确保复制结果本身可被 JSON.parse 还原；
 * 用 JSON.parse(JSON.stringify(...)) 而非直接取对象引用，避免后续把值改成字符串仍复制出旧内容。
 */
function serialize(): string {
  return JSON.stringify(JSON.parse(JSON.stringify(requestBody.value)), null, 2)
}

function onCopyRequest() {
  copyText(serialize())
}

function onCopyFormData() {
  copyText(JSON.stringify(JSON.parse(JSON.stringify(sampleData.value ?? {})), null, 2))
}

function onCopyCurl() {
  copyText(curlSample.value)
}

/** 重置示例数据：只清空本抽屉内的示例，不影响表单定义 */
function onResetSample() {
  sampleData.value = {}
  sampleKey.value += 1
}

/** 以只读方式打开某一行流程的对接说明 */
async function onOpen(record: { id: string }) {
  const seq = ++openSeq
  // 每次打开都重置全部状态，避免残留上一个流程的字段、示例数据与发起参数
  info.value = undefined
  errorMessage.value = ''
  sampleData.value = {}
  sampleKey.value = 0
  initiatorUserId.value = ''
  businessId.value = ''
  activeTab.value = 'fields'
  visible.value = true
  loading.value = true
  try {
    const { data } = await integrationProcess(record.id)
    if (seq !== openSeq) {
      return
    }
    info.value = data
  } catch (error) {
    if (seq !== openSeq) {
      return
    }
    // 失败必须整体清空：不能把上一份流程信息留在抽屉里
    info.value = undefined
    const msg = error instanceof Error ? error.message : ''
    errorMessage.value = msg || '对接说明加载失败，请稍后重试'
  } finally {
    if (seq === openSeq) {
      loading.value = false
    }
  }
}

function onClose() {
  // 作废在途请求并清空内容，关闭后不残留任何上一份数据
  openSeq += 1
  visible.value = false
  loading.value = false
  info.value = undefined
  errorMessage.value = ''
  sampleData.value = {}
  sampleKey.value = 0
  initiatorUserId.value = ''
  businessId.value = ''
  activeTab.value = 'fields'
}

defineExpose({ onOpen })
</script>

<style scoped lang="scss">
.integration-drawer__body {
  display: block;
  min-height: 120px;
}

.integration-drawer__summary {
  margin-bottom: 12px;
}

.integration-drawer__tabs {
  /* 不锁死高度：锁定后内容会被 .arco-tabs 的 overflow 裁掉且不出现滚动条 */
  height: auto;
}

.integration-drawer__tip {
  margin: 8px 0;
}

.integration-drawer__mono {
  font-family: var(--font-family-code, ui-monospace, sfmono-regular, menlo, monospace);
  overflow-wrap: anywhere;
}

.integration-drawer__muted {
  font-size: 12px;
  color: var(--color-text-3);
}

.integration-drawer__warn {
  font-size: 12px;
  color: rgb(var(--warning-6));
}

.integration-drawer__option {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.integration-drawer__code {
  padding: 0 4px;
  font-family: var(--font-family-code, ui-monospace, sfmono-regular, menlo, monospace);
  background-color: var(--color-fill-2);
  border-radius: 2px;
}

.integration-drawer__unsupported {
  margin: 4px 0;
  padding-left: 18px;
}

.integration-drawer__sample-actions {
  margin-bottom: 8px;
}

.integration-drawer__params {
  margin-top: 8px;
}

.integration-drawer__request-meta {
  margin-bottom: 8px;
}

.integration-drawer__actions {
  display: flex;
  gap: 8px;
  margin: 12px 0 8px;
}

.integration-drawer__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

/* JSON 区域横向可滚，窄视口下不撑破抽屉 */
.integration-drawer__pre {
  max-height: 420px;
  padding: 12px;
  overflow: auto;
  font-family: var(--font-family-code, ui-monospace, sfmono-regular, menlo, monospace);
  font-size: 12px;
  line-height: 18px;
  white-space: pre;
  background-color: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
}
</style>
