<template>
  <div class="bpm-flow-chart" :style="{ height }">
    <a-spin v-if="status === 'loading'" class="bpm-flow-chart__center" tip="正在加载流程图..." />
    <div v-else-if="status === 'error'" class="bpm-flow-chart__center">
      <a-result status="error" title="流程图加载失败" :subtitle="errorMessage">
        <template #extra>
          <a-button type="primary" @click="load">重试</a-button>
        </template>
      </a-result>
    </div>
    <a-empty v-else-if="status === 'empty'" class="bpm-flow-chart__center" :description="emptyDescription" />
    <!-- 只读接管失败：卸载 iframe 并给出失败提示与重试入口，页面上不残留任何可交互画布 -->
    <div v-else-if="status === 'guard-failed'" class="bpm-flow-chart__center">
      <a-result status="warning" title="画布只读保护未生效" :subtitle="guardFailMessage">
        <template #extra>
          <a-space direction="vertical" align="center">
            <a-button type="primary" @click="load">重试</a-button>
            <span class="bpm-flow-chart__hint">为保护流程数据，画布已停止渲染</span>
          </a-space>
        </template>
      </a-result>
    </div>
    <div v-else class="bpm-flow-chart__frame-wrap">
      <a-spin v-if="!frameLoaded" class="bpm-flow-chart__center" tip="正在渲染流程图..." />
      <!-- 业务码已通过预检确认成功，load 事件只用于决定何时移除渲染遮罩，不作为加载成功判据。
           iframe 保持正常尺寸以便 Warm-Flow 完成 LogicFlow 初始化，用户可见性由下方遮罩控制 -->
      <iframe ref="frameRef" class="bpm-flow-chart__iframe" :src="iframeSrc" @load="onFrameLoad"></iframe>
      <!--
        只读接管完成前不暴露可交互画布：遮罩覆盖整个 iframe 区域并吞掉鼠标/触屏/滚轮事件，
        同时以 inert + tabindex=-1 移除 iframe 自身的键盘焦点能力。
        接管成功后（readOnlyGuard === 'enforced'）遮罩移除，仅保留缩放、平移等查看操作。
      -->
      <div
        v-if="readOnlyGuard !== 'enforced'"
        class="bpm-flow-chart__shield"
        aria-hidden="true"
        @pointerdown.prevent.stop
        @pointerup.prevent.stop
        @pointermove.prevent.stop
        @click.prevent.stop
        @dblclick.prevent.stop
        @contextmenu.prevent.stop
        @wheel.prevent.stop
        @touchstart.prevent.stop
        @touchmove.prevent.stop
        @touchend.prevent.stop
        @dragstart.prevent.stop
        @drop.prevent.stop
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { getToken } from '@/utils/auth'

interface Props {
  /** 流程定义 ID（雪花 ID，字符串保存）。定义只读预览模式，与 instanceId 互斥 */
  definitionId?: string
  /** 流程实例 ID（雪花 ID，字符串保存）。实例运行图模式，与 definitionId 互斥 */
  instanceId?: string
  /** 需要展示时才加载 iframe；置 false 会卸载 iframe 并清空状态 */
  visible?: boolean
  /** 组件容器高度，由宿主给出确定值 */
  height?: string
}

defineOptions({ name: 'BpmFlowChart' })

const props = withDefaults(defineProps<Props>(), {
  definitionId: '',
  instanceId: '',
  visible: true,
  height: '100%',
})

/** guard-failed：只读接管失败，iframe 已卸载，页面只剩失败提示与重试入口 */
type ChartStatus = 'empty' | 'loading' | 'error' | 'guard-failed' | 'ready'

const status = ref<ChartStatus>('empty')
const errorMessage = ref('')
const guardFailMessage = ref('')
const emptyDescription = ref('')
const iframeSrc = ref('')
const frameLoaded = ref(false)
const frameRef = ref<HTMLIFrameElement>()

/** 只读接管状态：idle 未开始、pending 等待 UI 创建画布实例、enforced 已置只读；失败直接切 status，不经此态 */
type ReadOnlyGuardState = 'idle' | 'pending' | 'enforced'
const readOnlyGuard = ref<ReadOnlyGuardState>('idle')
let guardTimer: ReturnType<typeof setInterval> | undefined
/**
 * iframe 挂载后 load 事件一直不触发的兜底定时器：此时只读接管还没开始，画布可能被 Warm-Flow
 * 当作可编辑页面渲染出来，因此不能只留一个转圈的遮罩，必须超时降级为失败态（已卸载画布）。
 */
let mountTimer: ReturnType<typeof setTimeout> | undefined
/**
 * 只读接管的轮询代次：每次 load/重试/卸载都自增。
 * 旧轮询回调（含关闭抽屉、切换 ID、重试后仍在队列里的那一次）发现代次不匹配即自行退出，
 * 不会再挂载画布或改写新一次请求的状态。
 */
let guardEpoch = 0

/** 自增请求序号：ID/可见性切换后旧请求的响应一律作废，避免迟到的响应挂载错图 */
let requestId = 0
/**
 * 仅指向“最近一次”请求的 controller，用于新请求开始前与卸载时中断在途请求；
 * 超时定时器不引用该可变字段，防止旧定时器经它误中断新请求。
 */
let latestController: AbortController | undefined

const SNOWFLAKE_ID_PATTERN = /^[1-9]\d*$/
const baseUrl = import.meta.env.VITE_API_PREFIX ?? import.meta.env.VITE_API_BASE_URL

/**
 * 挂载前先用原生 fetch 预检流程图业务接口，确认返回的是数据而不是业务失败：
 * Warm-Flow 插件（/warm-flow/query-def/{定义ID}、/warm-flow/query-flow-chart/{实例ID}）
 * 成功响应为 { code: 200(数字), data: {...} } 且无 success 字段，
 * 而后端权限拦截/全局异常返回 ContiNew 契约 { success: false, code: '500'|'401'|'403'|'404', msg }，
 * 两种情况 HTTP 状态都是 200。因此既不能只看 iframe 的 load 事件，
 * 也不能复用 src/utils/http.ts（其拦截器以 success 字段判成败，会把插件的成功误判为失败）。
 */
async function verifyChartResponse(url: string, signal: AbortSignal): Promise<{ ok: boolean, msg?: string }> {
  const token = getToken()
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    signal,
  })
  if (!response.ok) {
    return { ok: false, msg: `流程图接口 HTTP ${response.status}` }
  }
  let body: { code?: number | string, data?: unknown, msg?: string }
  try {
    body = await response.json()
  } catch {
    return { ok: false, msg: '流程图接口响应不是有效 JSON' }
  }
  if (body.code === 200 && body.data != null) {
    return { ok: true }
  }
  return { ok: false, msg: typeof body.msg === 'string' && body.msg ? body.msg : '流程图接口未返回图数据' }
}

/**
 * Warm-Flow UI 由后端提供（/warm-flow-ui/index.html），其内部以 ../ 解析业务接口，
 * 因此必须让 iframe 地址比 /warm-flow-ui/ 深一层，接口才会落在 /warm-flow/** 上；
 * 前缀与业务 API 同源（dev/test 为 VITE_API_PREFIX，生产为 VITE_API_BASE_URL）。
 * UI 会读取 URL 中匹配 warm-flow.token-name 的参数写入请求头 Authorization，
 * 故以 Bearer <token> 传入登录凭证（与 BpmDesignerModal 机制一致）。
 *
 * 定义预览：id=<定义ID>&disabled=true&onlyDesignShow=true；
 * 实例运行图：type=FlowChart&id=<实例ID>。
 * disabled/onlyDesignShow 仅是 UI 渲染开关（只影响保存按钮与基础信息步骤），
 * 画布交互只读由 iframe 加载后的只读桥接负责；写库授权仍由后端 T2 拦截器独立判定。
 */
function buildIframeSrc(mode: 'definition' | 'instance', id: string): string {
  const params = new URLSearchParams()
  const token = getToken()
  if (token) {
    params.set('Authorization', `Bearer ${token}`)
  }
  if (mode === 'definition') {
    params.set('id', id)
    params.set('disabled', 'true')
    params.set('onlyDesignShow', 'true')
  } else {
    params.set('type', 'FlowChart')
    params.set('id', id)
  }
  return `${baseUrl}/warm-flow-ui/index.html?${params.toString()}`
}

/**
 * 只读桥接：Warm-Flow 1.8.9 UI 的 disabled/onlyDesignShow 参数只控制保存按钮与基础信息步骤，
 * 其 LogicFlow 画布始终以可编辑配置创建（定义预览与实例图都是如此），因此这里在 iframe
 * 加载后从同源文档内定位 LogicFlow 实例，再调用官方 API 置为只读。
 * 定位路径一：LogicFlow 内部用 Preact 渲染画布，渲染根 DOM 的 __k vnode 携带
 * tool/dnd 实例（分别反引用 tool.instance、dnd.lf 指向 LogicFlow）；
 * 路径二（兜底）：定义预览的 PropertySetting 组件持有 lf prop，遍历 Vue 组件树可取到。
 * __k/__c/__v 为该版本 Preact 压缩后的内部字段名，升级 Warm-Flow UI 后需回归此桥接。
 */

/** LogicFlow 实例中只读接管用到的最小对外接口 */
interface LogicFlowHandle {
  graphModel: unknown
  updateEditConfig: (config: Record<string, unknown>) => void
  extension?: { menu?: { setMenuConfig?: (config: { nodeMenu: unknown[], edgeMenu: unknown[] }) => void } }
  keyboard?: { destroy?: () => void }
  dnd?: Record<string, unknown>
}

interface VueVNodeLike {
  component?: VueInstanceLike
  children?: unknown
}

interface VueInstanceLike {
  props?: Record<string, unknown>
  subTree?: VueVNodeLike
}

interface PreactVNodeLike {
  props?: Record<string, unknown>
  __k?: PreactVNodeLike[] | null
  __c?: { __v?: PreactVNodeLike }
}

const READ_ONLY_NOOP = () => {}

function isLogicFlowHandle(value: unknown): value is LogicFlowHandle {
  if (!value || typeof value !== 'object') {
    return false
  }
  const record = value as Record<string, unknown>
  return !!record.graphModel && typeof record.updateEditConfig === 'function'
}

/** 既接受实例本身，也解包 tool.instance / dnd.lf 这类反引用 */
function pickLogicFlowHandle(value: unknown): LogicFlowHandle | undefined {
  if (!value || typeof value !== 'object') {
    return undefined
  }
  const record = value as Record<string, unknown>
  if (isLogicFlowHandle(record)) {
    return record
  }
  if (isLogicFlowHandle(record.instance)) {
    return record.instance
  }
  if (isLogicFlowHandle(record.lf)) {
    return record.lf
  }
  return undefined
}

/** 首选路径：.lf-graph 渲染根 DOM 的 __k vnode 树中携带 tool/dnd 反引用 */
function findLogicFlowViaPreact(doc: Document): LogicFlowHandle | undefined {
  const container = doc.querySelector('.lf-graph')?.parentElement as
    | (Element & { __k?: PreactVNodeLike })
    | undefined
  const root = container?.__k
  if (!root) {
    return undefined
  }
  const queue: PreactVNodeLike[] = [root]
  let visited = 0
  while (queue.length > 0 && visited < 800) {
    const vnode = queue.shift()
    if (!vnode || typeof vnode !== 'object') {
      continue
    }
    visited += 1
    if (vnode.props) {
      for (const value of Object.values(vnode.props)) {
        const handle = pickLogicFlowHandle(value)
        if (handle) {
          return handle
        }
      }
    }
    if (Array.isArray(vnode.__k)) {
      queue.push(...vnode.__k)
    }
    const rendered = vnode.__c?.__v
    if (rendered) {
      queue.push(rendered)
    }
  }
  return undefined
}

function collectVNodeInstances(vnode: VueVNodeLike | undefined, queue: VueInstanceLike[]) {
  if (!vnode || typeof vnode !== 'object') {
    return
  }
  if (vnode.component) {
    queue.push(vnode.component)
  }
  const { children } = vnode
  if (Array.isArray(children)) {
    for (const child of children) {
      collectVNodeInstances(child as VueVNodeLike, queue)
    }
  }
}

/** 兜底路径：定义预览的 PropertySetting 组件持有 lf prop，遍历 Vue 组件树可取到实例 */
function findLogicFlowViaVueTree(doc: Document): LogicFlowHandle | undefined {
  const mount = doc.getElementById('app') as (HTMLElement & { _vnode?: VueVNodeLike }) | null
  const rootInstance = mount?._vnode?.component
  if (!rootInstance) {
    return undefined
  }
  const queue: VueInstanceLike[] = [rootInstance]
  const visited = new WeakSet<VueInstanceLike>()
  while (queue.length > 0) {
    const instance = queue.shift()
    if (!instance || visited.has(instance)) {
      continue
    }
    visited.add(instance)
    const handle = pickLogicFlowHandle(instance.props?.lf)
    if (handle) {
      return handle
    }
    if (instance.subTree) {
      collectVNodeInstances(instance.subTree, queue)
    }
  }
  return undefined
}

function enforceChartReadOnly(lf: LogicFlowHandle, doc: Document) {
  // 官方只读配置：禁拖动节点/锚点连线/文字编辑，且静默默认保留缩放与平移
  lf.updateEditConfig({ isSilentMode: true })
  const menu = lf.extension?.menu
  if (menu?.setMenuConfig) {
    menu.setMenuConfig({ nodeMenu: [], edgeMenu: [] })
  }
  // 静默模式只会 keyboard.disable()，组件创建时注册的 Delete 快捷键需显式解绑
  if (lf.keyboard?.destroy) {
    lf.keyboard.destroy()
  }
  if (lf.dnd) {
    // 左侧栏加节点经由 dnd.startDrag，置空以阻断新增
    lf.dnd.startDrag = READ_ONLY_NOOP
  }
  // 实例级中和改图入口：工具栏 清空/撤销/重做 与一切删除路径
  const writable = lf as unknown as Record<string, unknown>
  writable.undo = READ_ONLY_NOOP
  writable.redo = READ_ONLY_NOOP
  writable.clearData = READ_ONLY_NOOP
  writable.render = READ_ONLY_NOOP
  writable.deleteNode = READ_ONLY_NOOP
  writable.deleteEdge = READ_ONLY_NOOP
  // 节点侧栏（仅定义预览存在）：禁用其拖拽入口并从视图移除
  doc.querySelectorAll('.diagram-sidebar').forEach((node) => node.remove())
  const style = doc.createElement('style')
  style.textContent = '.diagram-sidebar{display:none !important}'
  doc.head.appendChild(style)
}

function stopReadOnlyGuard() {
  if (guardTimer !== undefined) {
    clearInterval(guardTimer)
    guardTimer = undefined
  }
}

function stopMountWatchdog() {
  if (mountTimer !== undefined) {
    clearTimeout(mountTimer)
    mountTimer = undefined
  }
}

const GUARD_POLL_INTERVAL = 200
const GUARD_POLL_TIMEOUT = 10 * 1000
/** iframe 挂载到 load 事件之间的兜底上限，仅覆盖“load 完全不触发”这一异常分支 */
const MOUNT_TIMEOUT = 20 * 1000

/**
 * load 事件迟迟不触发时不能只留一个转圈遮罩：画布可能被 Warm-Flow 渲染为可编辑状态却永不接管，
 * 因此降级为失败态（同一套卸载与重试入口）。代次不匹配或 load 已触发则直接作废，不影响新一次请求。
 */
function startMountWatchdog() {
  stopMountWatchdog()
  const epoch = guardEpoch
  mountTimer = setTimeout(() => {
    mountTimer = undefined
    if (epoch !== guardEpoch || frameLoaded.value) {
      return
    }
    failReadOnlyGuard('流程图页面渲染超时（iframe 未完成加载）')
  }, MOUNT_TIMEOUT)
}

/**
 * 只读接管失败：不再保留任何可交互画布。
 * 清空 iframeSrc 会让 Vue 卸载 iframe 元素，连同其内部的 LogicFlow 监听器一并消失；
 * 同时停止轮询，避免迟到的回调把已经卸载的画布重新接上。
 */
function failReadOnlyGuard(detail: string) {
  stopReadOnlyGuard()
  stopMountWatchdog()
  guardEpoch += 1
  readOnlyGuard.value = 'idle'
  iframeSrc.value = ''
  frameLoaded.value = false
  guardFailMessage.value = `${detail}。为避免在可编辑画布上误改流程，画布已卸载，请重试。`
  status.value = 'guard-failed'
}

function startReadOnlyGuard() {
  stopReadOnlyGuard()
  readOnlyGuard.value = 'pending'
  const epoch = ++guardEpoch
  const startedAt = Date.now()
  guardTimer = setInterval(() => {
    // 代次校验：关闭、切换 ID、重试之后，旧轮询不得再触碰 DOM 或组件状态
    if (epoch !== guardEpoch) {
      stopReadOnlyGuard()
      return
    }
    // 定位与接管都可能因 Warm-Flow 内部结构变化抛异常，绝不能让异常逃出定时器中断轮询
    try {
      const doc = frameRef.value?.contentDocument
      const lf = doc ? findLogicFlowViaPreact(doc) ?? findLogicFlowViaVueTree(doc) : undefined
      if (doc && lf) {
        enforceChartReadOnly(lf, doc)
        if (epoch !== guardEpoch) {
          return
        }
        readOnlyGuard.value = 'enforced'
        stopReadOnlyGuard()
        return
      }
    } catch (error) {
      failReadOnlyGuard(`画布只读接管异常：${error instanceof Error ? error.message : String(error)}`)
      return
    }
    if (Date.now() - startedAt > GUARD_POLL_TIMEOUT) {
      failReadOnlyGuard('画布只读接管超时')
    }
  }, GUARD_POLL_INTERVAL)
}

function onFrameLoad() {
  stopMountWatchdog()
  frameLoaded.value = true
  startReadOnlyGuard()
}

/** 只读接管失败：不再是警告，而是卸载画布并给出重试入口 */
function fail(msg: string) {
  status.value = 'error'
  errorMessage.value = msg
  iframeSrc.value = ''
  frameLoaded.value = false
  stopReadOnlyGuard()
  stopMountWatchdog()
  guardEpoch += 1
  readOnlyGuard.value = 'idle'
}

async function load() {
  const request = ++requestId
  latestController?.abort()
  latestController = undefined
  stopReadOnlyGuard()
  stopMountWatchdog()
  // 自增代次，使上一次仍在队列中的轮询回调彻底作废（重试不得被旧回调干扰）
  guardEpoch += 1
  readOnlyGuard.value = 'idle'
  iframeSrc.value = ''
  frameLoaded.value = false
  guardFailMessage.value = ''
  if (!props.visible) {
    status.value = 'empty'
    emptyDescription.value = ''
    return
  }
  const { definitionId, instanceId } = props
  if (definitionId && instanceId) {
    fail('definitionId 与 instanceId 只能二选一')
    return
  }
  const mode = definitionId ? 'definition' : 'instance'
  const id = definitionId || instanceId
  if (!id) {
    status.value = 'empty'
    emptyDescription.value = '未提供流程图标识'
    return
  }
  if (!SNOWFLAKE_ID_PATTERN.test(id)) {
    fail('流程图标识无效，应为雪花 ID 字符串')
    return
  }
  status.value = 'loading'
  errorMessage.value = ''
  // 本次请求独占一个 AbortController：fetch 的 signal、超时回调、卸载中断都只经由该局部引用，
  // 组件级 latestController 仅用于中断“上一次”在途请求
  const controller = new AbortController()
  latestController = controller
  const timer = setTimeout(() => {
    // 守卫序号：若期间已发起新请求（切换 ID/重开），旧定时器不得中断新请求
    if (request === requestId) {
      controller.abort()
    }
  }, 15 * 1000)
  const verifyUrl = mode === 'definition'
    ? `${baseUrl}/warm-flow/query-def/${id}`
    : `${baseUrl}/warm-flow/query-flow-chart/${id}`
  try {
    const result = await verifyChartResponse(verifyUrl, controller.signal)
    if (request !== requestId) return
    if (!result.ok) {
      fail(result.msg ?? '流程图加载失败')
      return
    }
    iframeSrc.value = buildIframeSrc(mode, id)
    status.value = 'ready'
    // iframe 已挂载但 load 可能不触发：先挂兜底，保证任何情况下都不会停在“只能转圈”的中间态
    startMountWatchdog()
  } catch {
    if (request !== requestId) return
    fail('流程图加载请求失败，请检查网络或登录状态后重试')
  } finally {
    clearTimeout(timer)
  }
}

watch(
  () => [props.visible, props.definitionId, props.instanceId] as const,
  () => load(),
  { immediate: true },
)

/**
 * 键盘焦点隔离：遮罩只拦指针与滚轮事件，Tab 仍可能把焦点送进 iframe 内部，
 * 进而用 Delete / Tab 等键盘操作改图。接管完成前把 iframe 移出 tab 序列，
 * 接管成功后恢复，使缩放、平移等查看操作可正常使用。
 */
watch([readOnlyGuard, frameRef], ([guard, frame]) => {
  if (!frame) {
    return
  }
  if (guard === 'enforced') {
    frame.removeAttribute('inert')
    frame.removeAttribute('tabindex')
    frame.removeAttribute('aria-hidden')
    return
  }
  // inert 在 Chromium 下同时屏蔽焦点与交互；tabindex/aria-hidden 作为其它内核的兜底
  frame.setAttribute('inert', '')
  frame.setAttribute('tabindex', '-1')
  frame.setAttribute('aria-hidden', 'true')
}, { immediate: true })

onBeforeUnmount(() => {
  requestId++
  latestController?.abort()
  latestController = undefined
  stopReadOnlyGuard()
  stopMountWatchdog()
  // 卸载后旧轮询回调必须自行退出，不得再访问已销毁的 iframe
  guardEpoch += 1
})
</script>

<style scoped lang="scss">
.bpm-flow-chart {
  position: relative;
  width: 100%;
  overflow: hidden;
}

.bpm-flow-chart__center {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.bpm-flow-chart__frame-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.bpm-flow-chart__iframe {
  display: block;
  border: 0;
  width: 100%;
  height: 100%;
}

/**
 * 只读接管完成前的交互遮罩：透明但覆盖整个画布，吞掉所有鼠标/触屏/滚轮事件。
 * iframe 本身保留正常尺寸（Warm-Flow 需要可见容器才能完成 LogicFlow 初始化），
 * 因此隔离靠遮罩 + iframe 的 inert，而不是隐藏或缩小 iframe。
 */
.bpm-flow-chart__shield {
  position: absolute;
  inset: 0;
  z-index: 2;
  cursor: progress;
  touch-action: none;
  user-select: none;
  background: transparent;
}

.bpm-flow-chart__hint {
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
