import type { TableInstance } from '@arco-design/web-vue'
import { Tag } from '@arco-design/web-vue'
import CellCopy from '@/components/CellCopy/index.vue'
import http from '@/utils/http'

const BASE_URL = '/admin/approval'

/** 审批任务行（待办 / 已办 / 我的流程 三态共用同一结构，契约 §8.5） */
export interface TaskResp {
  /** 任务 ID，我的流程为空 */
  taskId: string
  /** 流程实例 ID，后端按字符串序列化，前端不得转成 Number */
  instanceId: string
  /** 业务单据 ID */
  businessId: string
  flowCode: string
  flowName: string
  /** 待办节点 / 已办理节点 / 实例当前节点 */
  nodeName: string
  /** 引擎流程状态，取值见 FLOW_STATUS_LIST */
  flowStatus: string
  /** 待办创建时间 / 办理时间 / 流程发起时间 */
  createTime: string
}

/** 审批任务分页响应 */
export interface TaskPageResp {
  list: TaskResp[]
  total: number
}

/** 审批任务分页查询条件，筛选全部由后端在本人数据范围内执行 */
export interface TaskPageQuery extends PageQuery {
  /** 流程名称，模糊匹配 */
  flowName?: string
  /** 业务单据 ID，精确匹配 */
  businessId?: string
  /** 流程实例 ID，按字符串精确匹配，避免雪花 ID 精度丢失 */
  instanceId?: string
  /** 流程状态，精确匹配，取值与 FLOW_STATUS_LIST 一致 */
  flowStatus?: string
}

/**
 * 引擎流程状态口径，与 Warm-Flow 的 flow_status 取值一一对应。
 *
 * 状态 8 只表示流程已流转到结束节点，通过和驳回结束的都是 8，因此这里用中性的「已结束」，
 * 具体办理结果属于历史任务的 skipType（详情接口以 lastSkipType 返回），不在列表列表现。
 */
export const FLOW_STATUS_LIST = [
  { label: '待提交', value: '0', color: 'orange' },
  { label: '审批中', value: '1', color: 'arcoblue' },
  { label: '已通过', value: '2', color: 'green' },
  { label: '自动完成', value: '3', color: 'green' },
  { label: '已终止', value: '4', color: 'red' },
  { label: '已作废', value: '5', color: 'gray' },
  { label: '已撤销', value: '6', color: 'gray' },
  { label: '已取回', value: '7', color: 'orange' },
  { label: '已结束', value: '8', color: 'cyan' },
  { label: '已退回', value: '9', color: 'red' },
  { label: '已失效', value: '10', color: 'gray' },
  { label: '已拿回', value: '11', color: 'orange' },
  { label: '已重启', value: '12', color: 'arcoblue' },
  { label: '已暂存', value: '13', color: 'gray' },
]

/** 流程状态文案，未知取值直接展示原值 */
export function getFlowStatusLabel(flowStatus?: string) {
  return FLOW_STATUS_LIST.find((item) => item.value === flowStatus)?.label || flowStatus || '-'
}

/** 流程状态标签颜色 */
export function getFlowStatusColor(flowStatus?: string) {
  return FLOW_STATUS_LIST.find((item) => item.value === flowStatus)?.color || 'gray'
}

/**
 * 我的待办分页（契约 §8.5，接口已存在）
 *
 * @param query 查询条件与分页参数
 */
export function pageTodoTask(query: TaskPageQuery) {
  return http.get<TaskPageResp>(`${BASE_URL}/task/page`, query)
}

/**
 * 我的已办分页（契约 §8.5，对应 T4 新增接口）
 *
 * @param query 查询条件与分页参数
 */
export function pageDoneTask(query: TaskPageQuery) {
  return http.get<TaskPageResp>(`${BASE_URL}/task/done/page`, query)
}

/**
 * 我的流程分页（契约 §8.5，对应 T4 新增接口）
 *
 * @param query 查询条件与分页参数
 */
export function pageMineTask(query: TaskPageQuery) {
  return http.get<TaskPageResp>(`${BASE_URL}/instance/mine/page`, query)
}

/** 节点列与时间列在各页的语义不同，由调用方传入列标题 */
export interface TaskColumnTitles {
  node: string
  time: string
}

/**
 * 待办 / 已办 / 我的流程三页共用的表格列定义。
 *
 * 契约 §8.5 明确三个接口统一返回 `TaskPageResp`，字段完全一致，
 * 因此此处只维护一份列定义，由三页分别拼接各自的操作列。
 * 业务单据 ID 与流程实例 ID 用 CellCopy 渲染，保证长 ID 可完整查看并可复制。
 *
 * 列宽必须给 width 而非 minWidth：Arco 的表格是 `table-layout: fixed`，
 * 只给 minWidth 的列会被均分剩余空间——实测流程名称（41px 内容）与
 * 业务单据 ID（282px 内容）同为 305px，宽屏下大片留白而真正需要空间的列没有余量。
 * 给 width 后 Arco 按各列宽度比例分配，多余空间按比例流向两个 ID 列。
 *
 * 宽度依据实测内容：business_id 最长 40 字符 UUID≈282px、instance_id 18 位≈148px、
 * flow_name 最长 22 字符、node_name 最长 4 字符；长文本列由 ellipsis + tooltip 兜底。
 *
 * @param pagination 分页对象
 * @param pagination.current 当前页码
 * @param pagination.pageSize 每页数量
 * @param titles 节点列与时间列的标题
 */
export function useTaskColumns(pagination: { current: number, pageSize: number }, titles: TaskColumnTitles) {
  const columns: TableInstance['columns'] = [
    {
      title: '序号',
      width: 66,
      align: 'center',
      render: ({ rowIndex }) => h('span', {}, rowIndex + 1 + (pagination.current - 1) * pagination.pageSize),
    },
    // 流程名称：最长约 22 字符即可容纳，不参与均分剩余空间
    { title: '流程名称', dataIndex: 'flowName', width: 220, ellipsis: true, tooltip: true },
    {
      // 最长 40 字符 UUID，全表最宽的一列，给最大档位
      title: '业务单据 ID',
      dataIndex: 'businessId',
      width: 300,
      render: ({ record }) => h(CellCopy, { content: record.businessId || '-' }),
    },
    {
      // 雪花 ID 最长 18 位数字，比业务单据 ID 短，收窄到刚好容纳
      title: '流程实例 ID',
      dataIndex: 'instanceId',
      width: 210,
      render: ({ record }) => h(CellCopy, { content: record.instanceId || '-' }),
    },
    {
      title: '流程状态',
      dataIndex: 'flowStatus',
      width: 110,
      align: 'center',
      render: ({ record }) => h(Tag, { color: getFlowStatusColor(record.flowStatus), size: 'small' }, () => getFlowStatusLabel(record.flowStatus)),
    },
    // 节点名：最长 4 字符，收窄到标题宽度附近，把空间让给长 ID 列
    { title: titles.node, dataIndex: 'nodeName', width: 160, ellipsis: true, tooltip: true },
    { title: titles.time, dataIndex: 'createTime', width: 180 },
  ]
  return columns
}
