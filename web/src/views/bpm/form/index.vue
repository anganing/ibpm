<template>
  <GiPageLayout>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :scroll="{ x: '100%', y: '100%', minWidth: 1000 }"
      :pagination="pagination"
      :disabled-tools="['size']"
      :disabled-column-keys="['formName']"
      @refresh="search"
    >
      <template #top>
        <GiForm v-model="queryForm" search :columns="queryFormColumns" size="medium" @search="search" @reset="reset"></GiForm>
      </template>
      <template #toolbar-left>
        <a-button v-permission="['bpm:form:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #isPublish="{ record }">
        <a-tag v-if="record.isPublish === 1" color="green" size="small">已发布</a-tag>
        <a-tag v-else color="orange" size="small">未发布</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['bpm:form:view']" title="预览" @click="onPreview(record)">预览</a-link>
          <a-link v-permission="['bpm:form:update']" title="设计" @click="onDesign(record)">设计</a-link>
          <!--
            发布是本页唯一的状态推进操作，用 success 语义色与「已发布」绿标签呼应，
            避免与预览/设计的常规链接混作一色，沦为“像删除按钮一样”的次要外观。
            已发布行同样展示（仅禁用），保证操作列每行按钮数量一致，不因状态增减跳动
          -->
          <a-link
            v-permission="['bpm:form:publish']"
            status="success"
            :disabled="record.isPublish === 1"
            :title="getPublishTitle(record)"
            @click="onPublish(record)"
          >
            发布
          </a-link>
          <a-link
            v-permission="['bpm:form:delete']"
            status="danger"
            :disabled="record.isPublish === 1"
            :title="getDeleteTitle(record)"
            @click="onDelete(record)"
          >
            删除
          </a-link>
        </a-space>
      </template>
    </GiTable>

    <AddModal ref="AddModalRef" @save-success="onCreateSuccess" />
    <FormDesignerModal ref="FormDesignerModalRef" @close="search" />
    <PreviewDrawer ref="PreviewDrawerRef" />
  </GiPageLayout>
</template>

<script setup lang="ts">
import { Message, Modal, type TableInstance } from '@arco-design/web-vue'
import AddModal from './AddModal.vue'
import FormDesignerModal from './FormDesignerModal.vue'
import PreviewDrawer from './PreviewDrawer.vue'
import { type FormResp, deleteForm, designForm, pageForm, publishForm } from '@/apis/bpm/form'
import type { ColumnItem } from '@/components/GiForm'
import { useResetReactive, useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'BpmForm' })

/**
 * 发布状态选项：表单只保留「未发布 / 已发布」两种业务状态。
 * 历史数据中可能残留 9（失效），按未发布处理，既不筛选、也不展示。
 */
const PublishStatusList = [
  { label: '未发布', value: 0, color: 'orange' },
  { label: '已发布', value: 1, color: 'green' },
]

const [queryForm, resetForm] = useResetReactive({})
const queryFormColumns: ColumnItem[] = reactive([
  {
    type: 'input',
    label: '表单名称',
    field: 'formName',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      placeholder: '请输入表单名称',
    },
  },
  {
    type: 'input',
    label: '表单编码',
    field: 'formCode',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      placeholder: '请输入表单编码',
    },
  },
  {
    type: 'select',
    label: '发布状态',
    field: 'isPublish',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      options: PublishStatusList,
      placeholder: '请选择发布状态',
    },
  },
])

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  handleDelete,
} = useTable((page) => pageForm({ ...queryForm, ...page }))
const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
    fixed: !isMobile() ? 'left' : undefined,
  },
  {
    // 实测最长 22 字符（T6快照V2表单_1790242926156），需约 203px，给 240px 留余量
    title: '表单名称',
    dataIndex: 'formName',
    width: 240,
    ellipsis: true,
    tooltip: true,
    fixed: !isMobile() ? 'left' : undefined,
  },
  // 实测最长 25 字符（t6_snap_form2_1790242926156），需约 211px，给 250px 留余量
  { title: '表单编码', dataIndex: 'formCode', width: 250, ellipsis: true, tooltip: true },
  { title: '版本', dataIndex: 'version', width: 90, align: 'center' },
  { title: '发布状态', dataIndex: 'isPublish', slotName: 'isPublish', width: 110, align: 'center' },
  { title: '更新时间', dataIndex: 'updateTime', width: 180 },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 220,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['bpm:form:view', 'bpm:form:update', 'bpm:form:publish', 'bpm:form:delete']),
  },
]

// 重置
const reset = () => {
  resetForm()
  search()
}

/**
 * 删除按钮禁用原因。
 * 已发布表单一律不可删除（后端同样拒绝）；未发布表单是否被流程定义引用，
 * 前端无法判断（列表接口不返回引用信息），按钮保持可用，由后端在 DELETE 时校验并拒绝，
 * http 层会自动提示错误消息。
 */
const getDeleteTitle = (record: FormResp) => {
  if (record.isPublish === 1) return '已发布的表单不允许删除'
  return '删除'
}

/**
 * 发布按钮禁用原因。
 * 已发布表单不需要再次发布（后端同样拒绝），按钮保留展示但置灰，
 * 使操作列每行按钮数量恒定。
 */
const getPublishTitle = (record: FormResp) => {
  if (record.isPublish === 1) return '已发布的表单无需重复发布'
  return '发布'
}

const AddModalRef = ref<InstanceType<typeof AddModal>>()
const FormDesignerModalRef = ref<InstanceType<typeof FormDesignerModal>>()
const PreviewDrawerRef = ref<InstanceType<typeof PreviewDrawer>>()

// 新增：先弹窗收集名称/编码，创建成功后立即开设计器
const onAdd = () => {
  AddModalRef.value?.onAdd()
}

// 表单记录创建成功 —— 拿到 ID 后才能开设计器（设计器只按 URL id 定位表单）
const onCreateSuccess = (formId: string) => {
  FormDesignerModalRef.value?.onOpen(formId)
}

// 预览：只读展示该表单的真实内容，无保存入口
const onPreview = (record: FormResp) => {
  PreviewDrawerRef.value?.onOpen(record)
}

/**
 * 设计：由后端决定打开哪条记录。
 * 未发布表单返回自身 ID（仍编辑原记录）；已发布表单返回复用或复制出的未发布新版本 ID，
 * 已发布记录不会被改写。关闭设计器时宿主会刷新列表，此处无需再拉一次。
 */
const onDesign = async (record: FormResp) => {
  try {
    const { data } = await designForm(record.id)
    const formId = String(data)
    if (!formId) return
    FormDesignerModalRef.value?.onOpen(formId)
  } catch {
    // 失败提示由 http 层统一处理
  }
}

// 发布
const onPublish = (record: FormResp) => {
  Modal.warning({
    title: '提示',
    content: `是否确定发布表单「${record.formName}」？`,
    hideCancel: false,
    maskClosable: false,
    onBeforeOk: async () => {
      try {
        const res = await publishForm(record.id)
        if (res.success) {
          Message.success('发布成功')
          await search()
        }
        return res.success
      } catch (error) {
        return false
      }
    },
  })
}

// 删除
const onDelete = (record: FormResp) => {
  return handleDelete(() => deleteForm(record.id), {
    content: `是否确定删除表单「${record.formName}(${record.formCode})」？`,
    showModal: true,
  })
}
</script>

<style scoped lang="scss"></style>
