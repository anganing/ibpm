<template>
  <GiPageLayout :header-style="{ padding: 0, borderBottom: 'none' }">
    <template #left>
      <CategoryTree v-model:categories="categories" @node-click="handleSelectCategory" />
    </template>
    <a-spin :loading="loading" class="definition-list">
      <a-empty v-if="!definitions.length" description="该分类下暂无已发布流程" />
      <template v-else>
        <div class="definition-list__header">
          <span class="definition-list__title">选择要发起的流程</span>
          <span class="definition-list__count">共 {{ definitions.length }} 个流程</span>
        </div>
        <div class="definition-list__grid">
          <div
            v-for="item in definitions"
            :key="item.id"
            class="definition-card"
            role="button"
            tabindex="0"
            :title="`${item.flowName}（${item.flowCode}）`"
            @click="onStart(item)"
            @keydown.enter.prevent="onStart(item)"
            @keydown.space.prevent="onStart(item)"
          >
            <!--
              分类角标：装饰性斜角标签，不参与交互（pointer-events: none），
              避免遮挡流程名称或拦截卡片点击。分类名过长时由角标自身 title 提供完整名称。
            -->
            <span
              class="definition-card__category"
              :title="getCategoryName(item.category)"
            >{{ getCategoryName(item.category) }}</span>
            <div class="definition-card__icon">
              <GiSvgIcon name="stamp" :size="16" />
            </div>
            <div class="definition-card__text">
              <div class="definition-card__name">{{ item.flowName }}</div>
              <div class="definition-card__code">{{ item.flowCode }}</div>
            </div>
          </div>
        </div>
      </template>
    </a-spin>
    <StartPanel ref="startPanelRef" @started="loadDefinitions" />
  </GiPageLayout>
</template>

<script setup lang="ts">
import CategoryTree from './tree/index.vue'
import StartPanel from './StartPanel.vue'
import type { ApprovalCategory, ApprovalDefinition } from '@/apis/bpm/approval'
import { listApprovalDefinitions } from '@/apis/bpm/approval'

defineOptions({ name: 'BpmApproval' })

const category = ref('')
const definitions = ref<ApprovalDefinition[]>([])
/**
 * 分类列表由左树 CategoryTree 拉取后经 v-model 上抛，宿主只读复用：
 * 卡片角标据此把 category(code) 映射为分类名称，无需为每张卡片单独请求接口。
 */
const categories = ref<ApprovalCategory[]>([])
const loading = ref(false)
const startPanelRef = ref<InstanceType<typeof StartPanel>>()

/** 未分类流程（category 为 null/空串）与命中不到名称时的统一回退文案 */
const UNCATEGORIZED_LABEL = '未分类'

/**
 * 分类 code → 分类名称。
 * 不写死映射表：仅使用分类列表中真实存在的数据；
 * 未分类显示“未分类”，code 在列表中找不到对应项时原样展示 code（不猜测名称）。
 */
function getCategoryName(code?: string | null) {
  const trimmed = code?.trim()
  if (!trimmed) {
    return UNCATEGORIZED_LABEL
  }
  return categories.value.find((item) => item.code === trimmed)?.name || trimmed
}

// 查询当前分类下的已发布流程模型（契约 §8.6，不传 category 时后端返回全部已发布）
async function loadDefinitions() {
  loading.value = true
  try {
    const { data } = await listApprovalDefinitions(category.value)
    definitions.value = data
  } finally {
    loading.value = false
  }
}

function handleSelectCategory(code: string) {
  category.value = code
  loadDefinitions()
}

function onStart(item: ApprovalDefinition) {
  startPanelRef.value?.open(item)
}

onMounted(loadDefinitions)
</script>

<style scoped lang="scss">
.definition-list {
  display: block;
  height: 100%;
  overflow: auto;
}

.definition-list__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.definition-list__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
}

.definition-list__count {
  font-size: 12px;
  color: var(--color-text-3);
}

/**
 * 流程选择网格：卡片宽度固定，桌面端每行最多 4 张，右侧变窄时自动折行。
 * 用 auto-fill 而非 auto-fit，避免流程较少时单张卡片被拉伸。
 */
.definition-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 300px));
  justify-content: start;
  gap: 12px;

  @media (min-width: 992px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  @media (max-width: 575px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

// 左图标 + 右文字；悬停/聚焦样式沿用工作台“快捷操作”的图标底色与主色变化
.definition-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  // 右内边距在常规 12px 基础上留出斜角区，避免流程名称伸进角标所在角落；
  // 卡片自身裁剪，避免斜角溢出到相邻卡片
  padding: 12px 26px 12px 12px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  background-color: var(--color-bg-1);
  cursor: pointer;
  transition: all 0.2s;

  &:hover,
  &:focus-visible {
    border-color: rgb(var(--primary-6));
    outline: none;
    box-shadow: 0 2px 8px rgba(var(--primary-6), 0.15);

    .definition-card__icon {
      color: rgb(var(--primary-6));
      background-color: var(--color-primary-light-1);
    }

    .definition-card__name {
      color: rgb(var(--primary-6));
    }
  }
}

/**
 * 分类角标：沿用工作台“我的项目”卡片的右上角斜角做法（绝对定位 + 45° 旋转 + 负偏移），
 * 但按流程卡片尺寸收紧——Project.vue 的卡片更宽（74px 宽 + 20px 负偏移），
 * 这里的卡片更窄，故收窄为 64px 并把负偏移降到 14px，让斜角整体内收进卡片角落。
 *
 * 斜角实体是“旋转前的横向条带”绕中心旋转后的斜带，其可视位置由浏览器实测得出：
 * 左端出现在卡片右上角附近，右端落到卡片右侧外，故卡片仅需 26px 右内边距即可
 * 让名称区域避开该斜带（实测名称底边 70.7px，位于斜带形体之下，二者不重叠）。
 *
 * pointer-events: none 使角标不拦截卡片点击；分类名过长时条带内单行省略，
 * 完整名称由 title 提供（角标 title 优先于卡片 title 生效）。
 *
 * 底色取主题色 --primary-6，随主题色设置联动，与卡片 hover 态的描边/阴影同源。
 */
.definition-card__category {
  position: absolute;
  top: 3px;
  right: -14px;
  width: 64px;
  height: 16px;
  overflow: hidden;
  font-size: 11px;
  line-height: 16px;
  color: #fff;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
  background-color: rgb(var(--primary-6));
  transform: rotate(45deg);
}

.definition-card__icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: var(--color-text-2);
  background-color: var(--color-fill-1);
  border-radius: 4px;
  transition: all 0.2s;
}

.definition-card__text {
  flex: 1;
  min-width: 0;
}

// 流程名称为主信息，最多两行
.definition-card__name {
  display: -webkit-box;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  transition: color 0.2s;
}

// 流程编码为次要信息，单行截断，完整内容由卡片 title 提示
.definition-card__code {
  overflow: hidden;
  margin-top: 2px;
  font-size: 12px;
  line-height: 18px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
