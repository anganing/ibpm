<template>
  <div class="approval-tasks">
    <a-space class="approval-tasks__toolbar">
      <a-button :loading="loading" @click="loadTasks">刷新待办</a-button>
    </a-space>
    <a-table
      row-key="taskId"
      :data="tasks"
      :loading="loading"
      :pagination="{ current: page, pageSize: size, total }"
      @page-change="changePage"
    >
      <template #columns>
        <a-table-column title="流程名称" data-index="flowName" />
        <a-table-column title="当前节点" data-index="nodeName" />
        <a-table-column title="创建时间" data-index="createTime" />
        <a-table-column title="操作" :width="100">
          <template #cell="{ record }">
            <a-link @click="openDetail(record)">办理</a-link>
          </template>
        </a-table-column>
      </template>
    </a-table>
    <DetailDrawer ref="detailRef" @completed="loadTasks" />
  </div>
</template>

<script setup lang="ts">
import DetailDrawer from './DetailDrawer.vue'
import type { ApprovalTask } from '@/apis/bpm'
import { listApprovalTasks } from '@/apis/bpm'

defineOptions({ name: 'BpmApprovalTaskPanel' })

const tasks = ref<ApprovalTask[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const detailRef = ref<InstanceType<typeof DetailDrawer>>()

async function loadTasks() {
  loading.value = true
  try {
    const { data } = await listApprovalTasks({ page: page.value, size })
    tasks.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function changePage(value: number) {
  page.value = value
  loadTasks()
}

function openDetail(task: ApprovalTask) {
  detailRef.value?.open(task.instanceId, task.taskId)
}

onMounted(loadTasks)
defineExpose({ loadTasks })
</script>

<style scoped lang="scss">
.approval-tasks {
  padding: 24px;
}

.approval-tasks__toolbar {
  margin-bottom: 16px;
}
</style>
