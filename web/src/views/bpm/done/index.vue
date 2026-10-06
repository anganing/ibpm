<template>
  <GiPageLayout>
    <GiTable
      row-key="taskId"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :scroll="{ x: '100%', y: '100%', minWidth: 1300 }"
      :pagination="pagination"
      :disabled-tools="['size']"
      :disabled-column-keys="['flowName']"
      @refresh="refresh"
    >
      <template #top>
        <GiForm v-model="queryForm" search :columns="queryFormColumns" size="medium" @search="search" @reset="reset"></GiForm>
      </template>
      <template #action="{ record }">
        <a-link title="查看" @click="onView(record)">查看</a-link>
      </template>
    </GiTable>

    <DetailDrawer ref="DetailDrawerRef" @completed="refresh" />
  </GiPageLayout>
</template>

<script setup lang="ts">
import DetailDrawer from '@/views/bpm/approval/DetailDrawer.vue'
import { FLOW_STATUS_LIST, type TaskResp, pageDoneTask, useTaskColumns } from '@/apis/bpm/task'
import type { ColumnItem } from '@/components/GiForm'
import { useResetReactive, useTable } from '@/hooks'
import { isMobile } from '@/utils'

defineOptions({ name: 'BpmDone' })

const [queryForm, resetForm] = useResetReactive({})
const queryFormColumns: ColumnItem[] = reactive([
  {
    type: 'input',
    label: '流程名称',
    field: 'flowName',
    span: { xs: 24, sm: 8, xxl: 6 },
    props: { placeholder: '请输入流程名称（模糊匹配）' },
  },
  {
    type: 'input',
    label: '业务单据 ID',
    field: 'businessId',
    span: { xs: 24, sm: 8, xxl: 6 },
    props: { placeholder: '请输入完整业务单据 ID' },
  },
  {
    type: 'input',
    label: '流程实例 ID',
    field: 'instanceId',
    span: { xs: 24, sm: 8, xxl: 6 },
    props: { placeholder: '请输入完整流程实例 ID' },
  },
  {
    type: 'select',
    label: '流程状态',
    field: 'flowStatus',
    span: { xs: 24, sm: 8, xxl: 6 },
    props: {
      options: FLOW_STATUS_LIST,
      placeholder: '请选择流程状态',
    },
  },
])

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  refresh,
} = useTable((page) => pageDoneTask({ ...queryForm, ...page }))

const columns = [
  // 一条历史任务一行，节点与时间为该次办理所在的节点和办理时间
  ...useTaskColumns(pagination, { node: '已办理节点', time: '办理时间' }),
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 100,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
  },
]

// 重置查询条件并回到第一页
const reset = () => {
  resetForm()
  search()
}

const DetailDrawerRef = ref<InstanceType<typeof DetailDrawer>>()

// 查看详情（已办记录只读，不传 taskId，抽屉不渲染审批意见与通过/驳回）
const onView = (record: TaskResp) => {
  DetailDrawerRef.value?.open(record.instanceId)
}
</script>

<style scoped lang="scss"></style>
