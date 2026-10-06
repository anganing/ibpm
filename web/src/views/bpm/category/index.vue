<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :scroll="{ x: '100%', y: '100%', minWidth: 900 }"
      :pagination="false"
      :disabled-column-keys="['name']"
      @refresh="search"
    >
      <template #top>
        <GiForm v-model="queryForm" search :columns="queryFormColumns" size="medium" @search="search" @reset="reset"></GiForm>
      </template>
      <template #toolbar-left>
        <a-button v-permission="['bpm:category:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['bpm:category:update']" title="修改" @click="onUpdate(record)">修改</a-link>
          <a-link v-permission="['bpm:category:delete']" status="danger" title="删除" @click="onDelete(record)">删除</a-link>
        </a-space>
      </template>
    </GiTable>

    <AddModal ref="AddModalRef" @save-success="search" />
  </GiPageLayout>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import AddModal from './AddModal.vue'
import { type CategoryResp, deleteCategory, listCategory } from '@/apis/bpm/category'
import type { ColumnItem } from '@/components/GiForm'
import { useResetReactive, useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'BpmCategory' })

const [queryForm, resetForm] = useResetReactive({
  name: '',
  code: '',
})
const queryFormColumns: ColumnItem[] = [
  {
    type: 'input',
    label: '分类名称',
    field: 'name',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      placeholder: '请输入分类名称',
    },
  },
  {
    type: 'input',
    label: '分类编码',
    field: 'code',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      placeholder: '请输入分类编码',
    },
  },
]

const { tableData, loading, search, handleDelete } = useTable(() => listCategory())

// 分类接口为全量返回（不分页），按名称/编码在内存中过滤
const dataList = computed(() => {
  const { name, code } = queryForm
  if (!name && !code) return tableData.value
  return tableData.value.filter((item) => {
    return (
      (!name || item.name?.toLowerCase().includes(name.toLowerCase()))
      && (!code || item.code?.toLowerCase().includes(code.toLowerCase()))
    )
  })
})

const columns: TableInstance['columns'] = [
  // 分类名称：实测最长 5 字符（自动化办公）约 70px，给 220px 容纳更长的业务分类名
  { title: '分类名称', dataIndex: 'name', width: 220, ellipsis: true, tooltip: true, fixed: !isMobile() ? 'left' : undefined },
  // 分类编码：实测最长 3 字符约 36px；原先只给 minWidth，被均分撑到 971px（利用率 4%），此处定宽收紧
  { title: '分类编码', dataIndex: 'code', width: 200, ellipsis: true, tooltip: true },
  { title: '排序', dataIndex: 'sort', width: 100, align: 'center' },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 160,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['bpm:category:update', 'bpm:category:delete']),
  },
]

// 重置
const reset = () => {
  resetForm()
  search()
}

// 删除
const onDelete = (record: CategoryResp) => {
  return handleDelete(() => deleteCategory(record.id), {
    content: `是否确定删除流程分类「${record.name}」？`,
    showModal: true,
  })
}

const AddModalRef = ref<InstanceType<typeof AddModal>>()
// 新增
const onAdd = () => {
  AddModalRef.value?.onAdd()
}

// 修改
const onUpdate = (record: CategoryResp) => {
  AddModalRef.value?.onUpdate(record)
}
</script>

<style scoped lang="scss"></style>
