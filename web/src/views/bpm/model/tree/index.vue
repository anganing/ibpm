<template>
  <div class="container">
    <div class="search">
      <a-input v-model="searchKey" placeholder="搜索分类名称/编码" allow-clear>
        <template #prefix><icon-search /></template>
      </a-input>
    </div>
    <div class="tree-wrapper">
      <a-spin :loading="loading" style="display: block; width: 100%">
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
                {{ node.name }} ({{ node.code }})
              </a-typography-paragraph>
            </template>
          </a-tree>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TreeNodeData } from '@arco-design/web-vue'
import { type CategoryResp, listCategory } from '@/apis/bpm/category'

defineOptions({ name: 'BpmModelTree' })

const emit = defineEmits<{
  /** 选中分类，keys 为空数组表示取消选择（查询全部） */
  (e: 'node-click', keys: Array<any>): void
}>()

/**
 * 分类为单级扁平结构（docs/bpm-优化.md §5.2），节点 key 取 code —— 与
 * flow_definition.category 存储的值一致，可直接作为列表查询参数。
 */
const dataList = ref<CategoryResp[]>([])
const loading = ref(false)
const selectedKeys = ref<Array<string>>([])

// 选中节点；再次点击同一节点时 Arco 会传出空数组，此时视为取消选择（查询全部）
const select = (keys: Array<any>) => {
  if (selectedKeys.value && selectedKeys.value[0] === keys[0]) {
    return
  }
  selectedKeys.value = keys
  emit('node-click', keys)
}

// 查询分类列表
const getTreeData = async () => {
  try {
    loading.value = true
    const { data } = await listCategory()
    dataList.value = data ?? []
  } finally {
    loading.value = false
  }
}

// 过滤树
const searchKey = ref('')
const search = (keyword: string) => {
  return dataList.value.filter((item) => {
    return item.name?.toLowerCase().includes(keyword) || item.code?.toLowerCase().includes(keyword)
  })
}

const treeData = computed(() => {
  if (!searchKey.value) return dataList.value
  return search(searchKey.value.toLowerCase())
})

onMounted(() => {
  getTreeData()
})
</script>

<style scoped lang="scss">
:deep(.arco-tree-node) {
  line-height: normal;
  border-radius: var(--border-radius-medium);
  margin: 5px 0;

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

    .arco-btn {
      margin-left: 8px;
      padding: 0 15px;
    }
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
