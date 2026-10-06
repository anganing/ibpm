<template>
  <a-drawer
    v-model:visible="visible"
    title="版本记录"
    :width="width >= 760 ? 760 : '100%'"
    unmount-on-close
    :footer="false"
    @cancel="onClose"
  >
    <!--
      只读：历史版本仅在此查看，不提供任何写入口（旧版本不从数据库删除）。
      unmount-on-close + 打开序号双重隔离，换一个流程或关闭抽屉都不会残留上一份数据
    -->
    <a-spin :loading="loading" class="version-drawer__body">
      <a-descriptions :column="1" size="small" class="version-drawer__summary" bordered>
        <a-descriptions-item label="流程名称">{{ flowName }}</a-descriptions-item>
        <a-descriptions-item label="流程编码">{{ flowCode }}</a-descriptions-item>
        <a-descriptions-item label="当前生效版本">
          <span v-if="effectiveVersion">v{{ effectiveVersion }}</span>
          <span v-else>无生效版本</span>
        </a-descriptions-item>
        <a-descriptions-item label="运行状态">
          <a-tag v-if="!effectiveVersion" color="gray" size="small">无生效版本</a-tag>
          <a-tag v-else-if="paused" color="orange" size="small">已暂停</a-tag>
          <a-tag v-else color="green" size="small">运行中</a-tag>
        </a-descriptions-item>
      </a-descriptions>

      <a-empty v-if="!loading && versionList.length === 0" description="暂无版本记录" />
      <a-table
        v-else
        row-key="id"
        size="small"
        :data="versionList"
        :columns="columns"
        :pagination="false"
      >
        <template #version="{ record }">
          <span>v{{ record.version }}</span>
          <a-tag v-if="record.effective" color="arcoblue" size="small" class="version-drawer__tag">生效中</a-tag>
        </template>
        <template #isPublish="{ record }">
          <a-tag v-if="record.isPublish === 1" color="green" size="small">已发布</a-tag>
          <a-tag v-else-if="record.isPublish === 9" color="gray" size="small">已失效</a-tag>
          <a-tag v-else color="orange" size="small">未发布</a-tag>
        </template>
        <template #activityStatus="{ record }">
          <a-tag v-if="record.activityStatus === 1" color="green" size="small">激活</a-tag>
          <a-tag v-else color="orange" size="small">挂起</a-tag>
        </template>
      </a-table>

      <a-alert type="normal" class="version-drawer__tip">
        历史版本只读保留，不会从数据库删除。已失效（9）版本不可恢复，也不可重新发布。
      </a-alert>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import type { TableInstance } from '@arco-design/web-vue'
import { type ProcessResp, type ProcessVersionResp, versionsProcess } from '@/apis/bpm/model'

defineOptions({ name: 'BpmModelVersionDrawer' })

const { width } = useWindowSize()

const visible = ref(false)
const loading = ref(false)
const flowName = ref('')
const flowCode = ref('')
const effectiveVersion = ref('')
const paused = ref(false)
const versionList = ref<ProcessVersionResp[]>([])

/** 打开序号：快速切换流程时，旧请求迟到的响应不得覆盖新流程的数据 */
let openSeq = 0

const columns: TableInstance['columns'] = [
  { title: '版本', dataIndex: 'version', slotName: 'version', width: 130 },
  { title: '定义 ID', dataIndex: 'id', width: 190, ellipsis: true, tooltip: true },
  { title: '发布状态', dataIndex: 'isPublish', slotName: 'isPublish', width: 100, align: 'center' },
  {
    title: '启停状态',
    dataIndex: 'activityStatus',
    slotName: 'activityStatus',
    width: 100,
    align: 'center',
  },
  { title: '更新时间', dataIndex: 'updateTime', width: 170 },
]

async function loadVersions(seq: number, id: string) {
  loading.value = true
  try {
    const { data } = await versionsProcess(id)
    if (seq !== openSeq) {
      return
    }
    versionList.value = data ?? []
  } catch (error) {
    if (seq !== openSeq) {
      return
    }
    // 失败必须整体清空，不能让上一份版本记录留在抽屉里
    versionList.value = []
  } finally {
    if (seq === openSeq) {
      loading.value = false
    }
  }
}

/** 以只读方式查看某一行流程的全部版本记录 */
async function onOpen(record: ProcessResp) {
  const seq = ++openSeq
  flowName.value = record.flowName
  flowCode.value = record.flowCode
  effectiveVersion.value = record.effectiveVersion ?? ''
  paused.value = record.paused === true
  versionList.value = []
  visible.value = true
  await loadVersions(seq, record.id)
}

function onClose() {
  // 作废在途请求并清空内容，关闭后不残留任何上一份数据
  openSeq += 1
  visible.value = false
  loading.value = false
  versionList.value = []
  flowName.value = ''
  flowCode.value = ''
  effectiveVersion.value = ''
  paused.value = false
}

defineExpose({ onOpen })
</script>

<style scoped lang="scss">
.version-drawer__body {
  display: block;
  min-height: 120px;
}

.version-drawer__summary {
  margin-bottom: 16px;
}

.version-drawer__tag {
  margin-left: 6px;
}

.version-drawer__tip {
  margin-top: 12px;
}
</style>
