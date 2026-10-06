<template>
  <a-drawer
    :visible="visible"
    :width="drawerWidth"
    title="审批详情"
    unmount-on-close
    @cancel="close"
  >
    <!--
      概览与页签同级，独立于 activeTab：两个页签下都展示，且只从当前成功返回的 detail 取值，
      加载中或失败时整体不渲染（下方失败态由 result 承担），不会残留上一个实例的信息
    -->
    <a-descriptions v-if="detail" :column="2" bordered class="detail-drawer__overview">
      <a-descriptions-item label="流程">{{ detail.flowName }}</a-descriptions-item>
      <a-descriptions-item label="实例 ID">{{ detail.instanceId }}</a-descriptions-item>
      <a-descriptions-item label="状态">{{ statusText }}</a-descriptions-item>
      <a-descriptions-item label="业务单据">{{ detail.businessId }}</a-descriptions-item>
    </a-descriptions>
    <a-tabs v-model:active-key="activeTab" class="detail-drawer__tabs">
      <a-tab-pane key="form" title="申请内容">
        <a-spin :loading="loading" class="detail-drawer__form">
          <!-- 详情失败或无查看资格：不保留任何上一次的表单数据，只给出失败状态与重试入口 -->
          <a-result v-if="errorMessage" status="error" title="审批详情不可用" :subtitle="errorMessage">
            <template #extra>
              <a-button type="primary" @click="retry">重试</a-button>
            </template>
          </a-result>
          <template v-else-if="detail">
            <ApprovalForm
              v-model="readonlyData"
              :form-content="detail.formContent"
              readonly
            />
            <template v-if="taskId">
              <h3>审批意见</h3>
              <a-textarea v-model="message" placeholder="请输入审批意见" :max-length="500" show-word-limit />
            </template>
          </template>
        </a-spin>
      </a-tab-pane>
      <a-tab-pane key="chart" title="流程图" destroy-on-hide>
        <div class="detail-drawer__chart">
          <!--
            图 ID 取自详情响应的 instanceId，与当前展示的表单同源对应；
            仅流程图页激活时挂载，切走或关闭抽屉即卸载 iframe，重新打开必然重新拉取
          -->
          <BpmFlowChart
            v-if="chartInstanceId && activeTab === 'chart'"
            :instance-id="chartInstanceId"
            height="100%"
          />
          <a-spin v-else-if="loading" class="detail-drawer__placeholder" tip="正在准备流程图..." />
          <a-empty v-else class="detail-drawer__placeholder" :description="chartUnavailableText" />
        </div>
      </a-tab-pane>
    </a-tabs>
    <template #footer>
      <a-space>
        <a-button @click="close">关闭</a-button>
        <template v-if="taskId && detail">
          <a-button status="danger" :loading="submitting" @click="handle(false)">驳回</a-button>
          <a-button type="primary" :loading="submitting" @click="handle(true)">通过</a-button>
        </template>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import type { ApprovalInstanceDetail } from '@/apis/bpm'
import { approveTask, getApprovalInstance, rejectTask } from '@/apis/bpm'
import BpmFlowChart from '@/components/BpmFlowChart/index.vue'
import type { ApprovalFormData } from '@/components/ApprovalForm/type'
import { isMobile } from '@/utils'

defineOptions({ name: 'BpmApprovalDetailDrawer' })

const emit = defineEmits<{ completed: [] }>()
const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const detail = ref<ApprovalInstanceDetail>()
const readonlyData = ref<ApprovalFormData>({})
const taskId = ref('')
const message = ref('')

/**
 * 抽屉宽度取屏幕一半（与 StartPanel、表单预览一致）；
 * 窄视口下不再按比例收窄，改为铺满，否则表格表单等宽内容可视区域过小。
 */
const drawerWidth = computed(() => (isMobile() ? '100%' : '50%'))

/** 抽屉页签：默认展示申请内容，切到流程图页才挂载实例运行图 */
const activeTab = ref<string>('form')
/** 详情加载失败/无查看资格的提示，非空时表单与图均不展示 */
const errorMessage = ref('')
/** 实例运行图的 ID，取自详情响应的 instanceId，保证图与当前表单对应同一个实例 */
const chartInstanceId = ref('')
/** 本次详情的请求参数，重试时复用 */
const requestInstanceId = ref('')

const statusText = computed(() => {
  if (!detail.value) return ''
  if (detail.value.flowStatus !== '8') return '审批中'
  return detail.value.lastSkipType === 'REJECT' ? '已驳回' : '已通过'
})

const chartUnavailableText = computed(() => {
  if (errorMessage.value) return '审批详情不可用，流程图暂不可查看'
  return '流程图暂不可用'
})

/** 自增打开序号：快速打开不同实例时，旧详情迟到的响应不得覆盖新详情 */
let openSeq = 0

async function loadDetail(seq: number, instanceId: string) {
  loading.value = true
  try {
    const { data } = await getApprovalInstance(instanceId)
    // 序号校验：期间已重新打开其它实例时，本次响应整体作废
    if (seq !== openSeq) {
      return
    }
    detail.value = data
    readonlyData.value = data.formData
    chartInstanceId.value = data.instanceId
    errorMessage.value = ''
  } catch (error) {
    if (seq !== openSeq) {
      return
    }
    // 失败必须留下明确状态：detail 与图 ID 已在 open 中清空，不会残留上一次实例的内容
    const msg = error instanceof Error ? error.message : ''
    errorMessage.value = msg || '审批详情加载失败，请稍后重试'
  } finally {
    if (seq === openSeq) {
      loading.value = false
    }
  }
}

/**
 * 打开实例详情。每次打开都重新拉取详情，页签固定回到“申请内容”，
 * 流程图在详情响应返回 instanceId 且用户切到流程图页后才挂载。
 */
async function open(instanceId: string, currentTaskId = '') {
  const seq = ++openSeq
  visible.value = true
  detail.value = undefined
  readonlyData.value = {}
  chartInstanceId.value = ''
  errorMessage.value = ''
  activeTab.value = 'form'
  taskId.value = currentTaskId
  message.value = ''
  requestInstanceId.value = instanceId
  await loadDetail(seq, instanceId)
}

function retry() {
  if (!requestInstanceId.value) {
    return
  }
  void loadDetail(++openSeq, requestInstanceId.value)
}

function close() {
  visible.value = false
}

async function handle(approve: boolean) {
  if (!taskId.value) return
  submitting.value = true
  try {
    if (approve) {
      await approveTask(taskId.value, message.value)
    } else {
      await rejectTask(taskId.value, message.value)
    }
    Message.success(approve ? '审批通过' : '审批已驳回')
    close()
    emit('completed')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<style scoped lang="scss">
/* 概览固定在页签上方，与页签保持一段间距 */
.detail-drawer__overview {
  margin-bottom: 12px;
}

/*
 * 同 StartPanel：不锁定高度。定为 100% 会把页签内容裁在抽屉可视高度内，
 * 且 drawer-body 不认为自己溢出，滚动条不出现，下方内容静默不可见。
 */
.detail-drawer__tabs {
  height: auto;
}

.detail-drawer__form {
  display: block;
  min-height: 200px;
  padding-top: 8px;
}

.detail-drawer__chart {
  height: 560px;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-medium);
  overflow: hidden;
}

.detail-drawer__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
</style>
