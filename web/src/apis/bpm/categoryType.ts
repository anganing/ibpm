/**
 * 流程分类类型（契约 §8.2）
 *
 * 注意：id 为后端 Long 序列化后的十进制字符串，禁止用 Number。
 */
export interface CategoryReq {
  name: string
  code: string
  sort: number
}

export interface CategoryResp {
  id: string
  name: string
  code: string
  sort: number
  createTime: string
}
