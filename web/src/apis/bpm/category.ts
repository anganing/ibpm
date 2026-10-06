/**
 * 流程分类 CRUD（契约见 docs/bpm-优化.md §8.2）。
 *
 * 说明：类型从 ./categoryType 以 `import type * as T` 导入并 re-export，运行时不产生
 * 额外模块；`export * from './categoryType'` 等价于此前把类型定义写在本文件内的效果。
 */
import type * as T from './categoryType'
import http from '@/utils/http'

export type * from './categoryType'

const BASE_URL = '/admin/approval/category'

/** @desc 查询全部分类（不分页，单级扁平，供左树与列表页使用） */
export function listCategory() {
  return http.get<T.CategoryResp[]>(`${BASE_URL}/list`)
}

/** @desc 新增分类，返回新 ID */
export function addCategory(data: T.CategoryReq) {
  return http.post<string>(`${BASE_URL}`, data)
}

/** @desc 修改分类 */
export function updateCategory(data: T.CategoryReq, id: string) {
  return http.put(`${BASE_URL}/${id}`, data)
}

/** @desc 删除分类（被流程定义引用时由后端拒绝） */
export function deleteCategory(id: string) {
  return http.del(`${BASE_URL}/${id}`)
}
