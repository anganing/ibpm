<template>
  <GiPageLayout>
    <template #left>
      <FlowCategoryTree @node-click="handleSelectCategory" />
    </template>
    <GiTable
      row-key="id"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :scroll="{ x: '100%', y: '100%', minWidth: 1620 }"
      :pagination="pagination"
      :disabled-tools="['size']"
      :disabled-column-keys="['flowName']"
      @refresh="search"
    >
      <template #top>
        <GiForm v-model="queryForm" search :columns="queryFormColumns" size="medium" @search="search" @reset="reset"></GiForm>
      </template>
      <template #toolbar-left>
        <a-button v-permission="['bpm:designer:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
      </template>
      <template #modelValue="{ record }">
        <a-tag size="small" :color="record.modelValue === 'MIMIC' ? 'arcoblue' : 'green'">
          {{ record.modelValue === 'MIMIC' ? '仿钉钉' : '经典' }}
        </a-tag>
      </template>
      <template #version="{ record }">
        <span>v{{ record.version }}</span>
        <!-- 最新版本是未发布草稿时，生效版本仍是上一已发布版本，需明确区分，避免误判为不可发起 -->
        <a-tooltip
          v-if="record.effectiveVersion && record.effectiveVersion !== record.version"
          :content="`该流程当前生效的是 v${record.effectiveVersion}，仍可发起`"
        >
          <a-tag color="arcoblue" size="small" class="model-tag">生效 v{{ record.effectiveVersion }}</a-tag>
        </a-tooltip>
      </template>
      <template #isPublish="{ record }">
        <a-tag v-if="record.isPublish === 1" color="green" size="small">已发布</a-tag>
        <a-tag v-else-if="record.isPublish === 9" color="gray" size="small">已失效</a-tag>
        <a-tag v-else color="orange" size="small">未发布</a-tag>
      </template>
      <template #runStatus="{ record }">
        <a-tag v-if="!record.effectiveId" color="gray" size="small">无生效版本</a-tag>
        <a-tag v-else-if="record.paused" color="orange" size="small">已暂停</a-tag>
        <a-tag v-else color="green" size="small">运行中</a-tag>
      </template>
      <template #action="{ record }">
        <a-space>
          <!--
            设计：由后端定位应打开的定义（已发布则复制新版本草稿），语义与流程表单页的「设计」一致。
            已失效行同样展示（仅禁用），保证操作列每行按钮数量一致
          -->
          <a-link
            v-permission="['bpm:designer:update']"
            :disabled="record.isPublish === 9"
            :title="record.isPublish === 9 ? '已失效的流程不可设计' : '设计'"
            @click="onUpdate(record)"
          >
            设计
          </a-link>
          <!--
            发布是状态推进操作，用 success 语义色与「已发布」绿标签呼应。
            已发布/已失效行同样展示（仅禁用），保证操作列每行按钮数量一致
          -->
          <a-link
            v-permission="['bpm:designer:publish']"
            status="success"
            :disabled="record.isPublish === 1 || record.isPublish === 9"
            :title="getPublishTitle(record)"
            @click="onPublish(record)"
          >
            发布
          </a-link>
          <!--
            对接说明是高频只读入口，直接放在操作列。
            已失效行同样展示（仅禁用），保证操作列每行按钮数量一致
          -->
          <a-link
            :disabled="record.isPublish === 9"
            :title="record.isPublish === 9 ? '已失效的流程无对接版本' : '查看该流程的第三方对接字段与发起请求示例'"
            @click="onIntegration(record)"
          >
            对接说明
          </a-link>
          <!--
            低频与危险操作收进「更多」下拉，避免操作列过宽。
            仿用户管理页的操作列写法，但按需求用文字「更多」而非三点图标
          -->
          <a-dropdown>
            <a-button
              v-if="has.hasPermOr(['bpm:designer:update', 'bpm:designer:delete'])"
              type="text"
              size="mini"
              title="更多"
            >
              更多
            </a-button>
            <template #content>
              <!--
                暂停/恢复只作用于当前生效版本，故传 effectiveId；最新版本是草稿时它与 id 不同。
                暂停只拦截新发起，已在途的实例与待办不受影响。
              -->
              <a-doption
                v-permission="['bpm:designer:update']"
                :disabled="!record.effectiveId"
                :title="getPauseTitle(record)"
                @click="onTogglePause(record)"
              >
                {{ record.paused ? '恢复' : '暂停' }}
              </a-doption>
              <a-doption title="版本记录" @click="onVersions(record)">版本记录</a-doption>
              <a-doption v-permission="['bpm:designer:delete']">
                <a-link
                  status="danger"
                  :disabled="record.isPublish === 1 || record.isPublish === 9"
                  :title="getDeleteTitle(record)"
                  @click="onDelete(record)"
                >
                  删除
                </a-link>
              </a-doption>
            </template>
          </a-dropdown>
        </a-space>
      </template>
    </GiTable>

    <BpmDesignerModal ref="BpmDesignerModalRef" @close="search" />
    <VersionDrawer ref="VersionDrawerRef" />
    <IntegrationDrawer ref="IntegrationDrawerRef" />
  </GiPageLayout>
</template>

<script setup lang="ts">
import { Message, Modal, type TableInstance } from '@arco-design/web-vue'
import FlowCategoryTree from './tree/index.vue'
import IntegrationDrawer from './IntegrationDrawer.vue'
import VersionDrawer from './VersionDrawer.vue'
import {
  type ProcessResp,
  deleteProcess,
  designProcess,
  pageProcess,
  pauseProcess,
  publishProcess,
  resumeProcess,
} from '@/apis/bpm/model'
import BpmDesignerModal from '@/components/BpmDesignerModal/index.vue'
import type { ColumnItem } from '@/components/GiForm'
import { useResetReactive, useTable } from '@/hooks'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: 'BpmModel' })

/** 发布状态选项，契约 §8.3：0 未发布 / 1 已发布 / 9 失效 */
const PublishStatusList = [
  { label: '未发布', value: 0, color: 'orange' },
  { label: '已发布', value: 1, color: 'green' },
  { label: '已失效', value: 9, color: 'gray' },
]

const [queryForm, resetForm] = useResetReactive({})
const queryFormColumns: ColumnItem[] = reactive([
  {
    type: 'input',
    label: '流程名称',
    field: 'flowName',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      placeholder: '请输入流程名称',
    },
  },
  {
    type: 'input',
    label: '流程编码',
    field: 'flowCode',
    span: { xs: 24, sm: 8, xxl: 8 },
    props: {
      placeholder: '请输入流程编码',
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
} = useTable((page) => pageProcess({ ...queryForm, ...page }))
const columns: TableInstance['columns'] = [
  {
    title: '序号',
    width: 66,
    align: 'center',
    render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
    fixed: !isMobile() ? 'left' : undefined,
  },
  {
    // 实测最长 22 字符（T6快照V1流程_1790242926156），需约 203px，给 240px 留余量
    title: '流程名称',
    dataIndex: 'flowName',
    width: 240,
    ellipsis: true,
    tooltip: true,
    fixed: !isMobile() ? 'left' : undefined,
  },
  // 实测最长 25 字符（t6_perm_probe_1790242265280），需约 214px，给 250px 留余量
  { title: '流程编码', dataIndex: 'flowCode', width: 250, ellipsis: true, tooltip: true },
  // 分类名实测最长 3 字符，仅需约 29px；给 120px 容纳标题与稍长分类名
  { title: '分类', dataIndex: 'category', width: 120, ellipsis: true, tooltip: true },
  { title: '版本', dataIndex: 'version', slotName: 'version', width: 150, align: 'center' },
  { title: '发布状态', dataIndex: 'isPublish', slotName: 'isPublish', width: 110, align: 'center' },
  { title: '运行状态', dataIndex: 'runStatus', slotName: 'runStatus', width: 120, align: 'center' },
  { title: '设计器模型', dataIndex: 'modelValue', slotName: 'modelValue', width: 120, align: 'center' },
  { title: '更新时间', dataIndex: 'updateTime', width: 180 },
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    // 常态渲染「设计」「发布」「对接说明」「更多」四个控件（其余收进下拉），约 4×56 + 间距 + padding，给 236px
    width: 236,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['bpm:designer:update', 'bpm:designer:publish', 'bpm:designer:delete']),
  },
]

// 重置
const reset = () => {
  resetForm()
  search()
}

// 根据选中分类查询；取消选择时查询全部
const handleSelectCategory = (keys: Array<any>) => {
  queryForm.category = keys.length === 1 ? keys[0] : undefined
  search()
}

/**
 * 发布按钮禁用原因。
 * 已发布流程无需重复发布，已失效流程不可再发布（后端同样拒绝）；
 * 两种状态均保留按钮展示并置灰，使操作列每行按钮数量恒定。
 */
const getPublishTitle = (record: ProcessResp) => {
  if (record.isPublish === 1) return '已发布的流程无需重复发布'
  if (record.isPublish === 9) return '已失效的流程不可发布'
  return '发布'
}

/**
 * 删除按钮禁用原因。
 * 前端无法判断「有无流程实例」（列表接口不返回实例数），因此未发布时按钮保持可用，
 * 由后端在 DELETE 时校验并拒绝，http 层会自动提示错误消息（契约 §8.3 删除规则）。
 */
const getDeleteTitle = (record: ProcessResp) => {
  if (record.isPublish === 1) return '已发布的流程不允许删除'
  if (record.isPublish === 9) return '已失效的流程不允许删除'
  return '删除'
}

/**
 * 暂停/恢复按钮的禁用原因与提示。
 * 只作用于当前生效版本：无生效版本（例如最新版本还是草稿且从未发布）时无可暂停对象。
 */
const getPauseTitle = (record: ProcessResp) => {
  if (!record.effectiveId) return '该流程当前没有生效版本，无需暂停'
  if (record.paused) return '恢复后该流程可再次发起'
  return '暂停后该流程不可再发起，已有流程实例与审批任务不受影响'
}

const BpmDesignerModalRef = ref<InstanceType<typeof BpmDesignerModal>>()
const VersionDrawerRef = ref<InstanceType<typeof VersionDrawer>>()
const IntegrationDrawerRef = ref<InstanceType<typeof IntegrationDrawer>>()

// 新增：直接打开不带 id 的全屏设计器，保存后由设计器通知关闭并刷新列表
const onAdd = () => {
  BpmDesignerModalRef.value?.onOpen()
}

/**
 * 设计：由后端决定打开哪条记录。
 * 未发布草稿返回自身 ID（仍编辑原记录）；已发布定义返回复制/复用出的下一版本草稿 ID，
 * 已发布记录不会被改写。关闭设计器时宿主会刷新列表，此处无需再拉一次。
 */
const onUpdate = async (record: ProcessResp) => {
  try {
    const { data } = await designProcess(record.id)
    const definitionId = String(data ?? '')
    if (!definitionId) return
    BpmDesignerModalRef.value?.onOpen(definitionId)
  } catch {
    // 失败提示由 http 层统一处理
  }
}

// 版本记录：只读查看同一流程编码下的全部版本，不提供任何写入口
const onVersions = (record: ProcessResp) => {
  VersionDrawerRef.value?.onOpen(record)
}

/**
 * 对接说明：只读展示该流程当前生效定义绑定的已发布表单，供第三方核对字段并生成发起请求示例。
 * 由后端按发起链路选版，主列表显示最新草稿时也不会拿草稿当对接版本。
 */
const onIntegration = (record: ProcessResp) => {
  IntegrationDrawerRef.value?.onOpen(record)
}

// 发布
const onPublish = (record: ProcessResp) => {
  Modal.warning({
    title: '提示',
    content: `是否确定发布流程「${record.flowName}」？`,
    hideCancel: false,
    maskClosable: false,
    onBeforeOk: async () => {
      try {
        const res = await publishProcess(record.id)
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

/**
 * 暂停 / 恢复：只作用于当前生效版本，故传 effectiveId（最新版本是草稿时它与 id 不同）。
 * 暂停只拦截新实例发起，不影响已有实例与在途审批任务。
 */
const onTogglePause = (record: ProcessResp) => {
  const targetId = record.effectiveId
  if (!targetId) return
  const isPaused = record.paused === true
  Modal.warning({
    title: '提示',
    content: isPaused
      ? `是否确定恢复流程「${record.flowName}」？恢复后该流程可再次发起。`
      : `是否确定暂停流程「${record.flowName}」？暂停后该流程不可再发起，已有流程实例与审批任务不受影响。`,
    hideCancel: false,
    maskClosable: false,
    onBeforeOk: async () => {
      try {
        const res = isPaused ? await resumeProcess(targetId) : await pauseProcess(targetId)
        if (res.success) {
          Message.success(isPaused ? '恢复成功' : '暂停成功')
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
const onDelete = (record: ProcessResp) => {
  return handleDelete(() => deleteProcess(record.id), {
    content: `是否确定删除流程「${record.flowName}(${record.flowCode})」？`,
    showModal: true,
  })
}
</script>

<style scoped lang="scss">
.model-tag {
  margin-left: 6px;
}
</style>
