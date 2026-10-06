<template>
  <a-drawer
    :visible="visible"
    :width="drawerWidth"
    :title="title"
    :mask-closable="false"
    unmount-on-close
    @cancel="close"
  >
    <a-tabs v-model:active-key="activeTab" class="start-panel__tabs">
      <a-tab-pane key="form" title="申请内容">
        <!--
          业务单据 ID 是发起参数，不属于流程模型的动态表单字段：
          因此独立于 ApprovalForm 渲染，也不并入 formData
        -->
        <a-form :model="{ businessId }" layout="vertical" class="start-panel__business">
          <a-form-item
            field="businessId"
            label="业务单据 ID"
            :help="BUSINESS_ID_HELP"
            :validate-status="businessIdError ? 'error' : undefined"
            :error="businessIdError"
          >
            <a-input
              v-model="businessId"
              :max-length="BUSINESS_ID_MAX_LENGTH"
              :placeholder="BUSINESS_ID_PLACEHOLDER"
              allow-clear
            />
          </a-form-item>
        </a-form>
        <a-spin :loading="loadingForm" class="start-panel__form">
          <ApprovalForm
            v-if="formContent"
            ref="formRef"
            v-model="formData"
            :form-content="formContent"
          />
          <a-empty v-else-if="!loadingForm" description="该流程模型未绑定可用表单" />
        </a-spin>
      </a-tab-pane>
      <a-tab-pane key="chart" title="流程图" destroy-on-hide>
        <div class="start-panel__chart">
          <!-- 图 ID 取自表单响应的 definitionId，与当前加载的表单同源对应；仅流程图页激活时挂载，切走即卸载 iframe -->
          <BpmFlowChart v-if="chartDefinitionId && activeTab === 'chart'" :definition-id="chartDefinitionId" height="100%" />
          <a-spin v-else-if="loadingForm" class="start-panel__placeholder" tip="正在准备流程图..." />
          <a-empty v-else class="start-panel__placeholder" description="流程图暂不可用" />
        </div>
      </a-tab-pane>
    </a-tabs>
    <template #footer>
      <a-space>
        <a-button @click="close">取消</a-button>
        <a-button
          type="primary"
          :loading="submitting"
          :disabled="!formContent"
          @click="submit"
        >
          提交
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import type { ApprovalDefinition } from '@/apis/bpm/approval'
import { BUSINESS_ID_MAX_LENGTH, getApprovalDefinitionForm, startApproval } from '@/apis/bpm/approval'
import ApprovalForm from '@/components/ApprovalForm/index.vue'
import BpmFlowChart from '@/components/BpmFlowChart/index.vue'
import type { ApprovalFormContent, ApprovalFormData } from '@/components/ApprovalForm/type'
import { isMobile } from '@/utils'

defineOptions({ name: 'BpmApprovalStartPanel' })

const emit = defineEmits<{ started: [] }>()

const BUSINESS_ID_PLACEHOLDER = '选填，留空则由系统自动生成'
const BUSINESS_ID_HELP = `选填。填写时作为业务单据标识保存，最长 ${BUSINESS_ID_MAX_LENGTH} 个字符；留空则由系统自动生成。`

const visible = ref(false)
const definition = ref<ApprovalDefinition>()
const formContent = ref<ApprovalFormContent>()
const formData = ref<ApprovalFormData>({})
const formRef = ref<InstanceType<typeof ApprovalForm>>()
const loadingForm = ref(false)
const submitting = ref(false)

/** 定义预览图的 ID，来自 getApprovalDefinitionForm 响应，保证图与当前表单对应 */
const chartDefinitionId = ref('')

/** 抽屉页签：默认展示申请内容，首次切到流程图页才挂载并加载图 */
const activeTab = ref<string>('form')

/**
 * 业务单据 ID：独立于 formData 的发起参数。
 * 仅在「申请内容」页签可编辑；页签切换不重置它，重新打开抽屉才清空。
 */
const businessId = ref('')
/** 业务单据 ID 的校验提示，非空时阻止提交 */
const businessIdError = ref('')

/** 归一后的业务单据 ID：去首尾空格；空串表示留空，交由后端生成 UUID */
const normalizedBusinessId = computed(() => businessId.value.trim())

/**
 * 校验业务单据 ID：留空合法（后端生成）；填写时不得超过 40 字符。
 * 输入框已限制 max-length，此处为提交前的兜底校验。
 */
function validateBusinessId() {
  const value = normalizedBusinessId.value
  if (!value) {
    businessIdError.value = ''
    return true
  }
  if (value.length > BUSINESS_ID_MAX_LENGTH) {
    businessIdError.value = `业务单据 ID 长度不能超过 ${BUSINESS_ID_MAX_LENGTH} 个字符`
    return false
  }
  businessIdError.value = ''
  return true
}

/** 输入变化时即时清掉上一次的报错，避免用户已修正仍看到红字 */
watch(businessId, () => {
  if (businessIdError.value) {
    businessIdError.value = ''
  }
})

/**
 * 抽屉宽度取屏幕一半（与详情抽屉、表单预览一致）；
 * 窄视口下不再按比例收窄，改为铺满，否则表格表单等宽内容可视区域过小。
 */
const drawerWidth = computed(() => (isMobile() ? '100%' : '50%'))

const title = computed(() => {
  if (!definition.value) {
    return '发起审批'
  }
  return `发起审批 - ${definition.value.flowName}`
})

/** 自增打开序号：快速连点不同流程时，旧流程迟到的表单响应不得覆盖新流程 */
let openSeq = 0

/**
 * 打开抽屉并加载该模型绑定的审批表单。
 * 表单定义每次打开都重新拉取，避免流程模型换绑表单后抽屉仍渲染旧定义；
 * 页签固定回到“申请内容”，流程图在表单响应返回 definitionId 且用户切到流程图页后才挂载。
 * 业务单据 ID 每次打开都清空，不复用上一次流程填过的值。
 */
async function open(row: ApprovalDefinition) {
  const seq = ++openSeq
  visible.value = true
  definition.value = row
  formContent.value = undefined
  formData.value = {}
  businessId.value = ''
  businessIdError.value = ''
  chartDefinitionId.value = ''
  activeTab.value = 'form'
  loadingForm.value = true
  try {
    const { data } = await getApprovalDefinitionForm(row.flowCode)
    if (seq !== openSeq) {
      return
    }
    formContent.value = data.formContent
    chartDefinitionId.value = data.definitionId
  } finally {
    if (seq === openSeq) {
      loadingForm.value = false
    }
  }
}

function close() {
  visible.value = false
}

async function submit() {
  if (!definition.value) {
    return
  }
  if (!validateBusinessId()) {
    // 业务单据 ID 非法时切回「申请内容」页签，否则用户看不到报错
    activeTab.value = 'form'
    return
  }
  const valid = await formRef.value?.validate()
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    // 留空传 undefined，由后端生成 UUID；填写则传去空格后的真实业务单据 ID
    await startApproval(definition.value.flowCode, formData.value, normalizedBusinessId.value || undefined)
    Message.success('审批已发起')
    close()
    emit('started')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<style scoped lang="scss">
/*
 * 不锁定高度：定为 100% 会让 a-tabs 固定成抽屉可视高度，
 * 内容超出部分被 .arco-tabs 的 overflow:hidden 静默裁掉，
 * 且 drawer-body 的 scrollHeight 只算到 tabs 高度，滚动条不会出现。
 * 保持自适应高度，由 .arco-drawer-body 的 overflow-y:auto 承担滚动。
 */
.start-panel__tabs {
  height: auto;
}

/* 业务单据 ID 是发起参数，与下方动态表单视觉上分隔 */
.start-panel__business {
  padding-top: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--color-border);
}

.start-panel__form {
  display: block;
  min-height: 200px;
  padding-top: 8px;
}

.start-panel__chart {
  height: 560px;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-medium);
  overflow: hidden;
}

.start-panel__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
</style>
