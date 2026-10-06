<template>
  <div class="container">
    <div class="search">
      <a-input v-model="searchKey" placeholder="搜索名称/编码" allow-clear>
        <template #prefix><icon-search /></template>
      </a-input>
    </div>
    <div class="tree-wrapper">
      <div class="tree">
        <a-tree
          :data="(treeData as unknown as TreeNodeData[])"
          :field-names="{ key: 'code', title: 'name' }"
          block-node
          :selected-keys="selectedKeys"
          @select="select"
        >
          <template #title="node">
            <a-typography-paragraph
              :ellipsis="{
                rows: 1,
                showTooltip: true,
                css: true,
              }"
            >
              {{ node.name }}
            </a-typography-paragraph>
          </template>
        </a-tree>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TreeNodeData } from '@arco-design/web-vue'
import { type ApprovalCategory, listApprovalCategories } from '@/apis/bpm/approval'

defineOptions({ name: 'BpmApprovalCategoryTree' })

/**
 * 分类列表上抛给宿主（发起审批页），供流程卡片角标做 code → 名称映射。
 * 分类数据本就由本组件在全站唯一的分类接口上拉取一次，
 * 上抛可让宿主复用同一份数据，避免为每张卡片重复请求。
 */
const dataList = defineModel<ApprovalCategory[]>('categories', { default: () => [] })

const emit = defineEmits<{
  (e: 'node-click', category: string): void
}>()

const selectedKeys = ref<Array<string>>([])

/**
 * 选中节点，key 取 code（契约 §8.2：flow_definition.category 存的是 code，
 * §8.6 的 category 查询参数同样按 code 过滤），故 code 可直接作为列表查询参数。
 * Arco 单选模式再次点击同一节点仍返回该节点 key；宿主主动清空选中态，
 * 传空串由后端返回全部流程。
 */
const select = (keys: Array<any>) => {
  if (selectedKeys.value[0] === keys[0]) {
    selectedKeys.value = []
    emit('node-click', '')
    return
  }
  selectedKeys.value = keys
  emit('node-click', keys[0] ?? '')
}

// 查询分类列表（契约 §8.2 不分页，一次全量返回）
const getTreeData = async () => {
  const { data } = await listApprovalCategories()
  dataList.value = data
}

// 过滤树：单级扁平，按名称/编码匹配
const searchKey = ref('')
const treeData = computed(() => {
  const keyword = searchKey.value.toLowerCase()
  if (!keyword) {
    return dataList.value
  }
  return dataList.value.filter((item) => {
    return item.name?.toLowerCase().includes(keyword) || item.code?.toLowerCase().includes(keyword)
  })
})

onMounted(getTreeData)
</script>

<style scoped lang="scss">
:deep(.arco-tree-node) {
  line-height: normal;
  border-radius: var(--border-radius-medium);
  margin: 5px 0;

  &:hover {
    background-color: var(--color-secondary-hover);
  }

  .arco-tree-node-switcher {
    width: 0;
    margin-right: 0;
  }

  .arco-tree-node-title {
    &:hover {
      background-color: transparent;
    }
  }

  .arco-tree-node-title-text {
    width: 100%;
    white-space: normal;
    overflow-wrap: anywhere;
  }
}

:deep(.arco-tree-node-selected) {
  font-weight: bold;
  background-color: rgba(var(--primary-6), 0.1);

  &:hover {
    background-color: rgba(var(--primary-6), 0.1);
  }

  .arco-typography {
    color: rgb(var(--primary-6));
  }
}

.container {
  flex: 1;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100%;

  .search {
    display: flex;
    justify-content: start;
    margin-bottom: 10px;
  }

  .tree-wrapper {
    flex: 1;
    overflow: hidden;
    background-color: var(--color-bg-1);
    position: relative;
    height: 100%;

    .tree {
      position: absolute;
      top: 0;
      bottom: 0;
      left: 0;
      right: 0;
      overflow: auto;
    }
  }
}
</style>
