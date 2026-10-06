/** 应用类型 */
export interface AppResp {
  id: string
  name: string
  accessKey: string
  secretKey: string
  expireTime: string
  description: string
  status: 1 | 2
  createUserString: string
  createTime: string
  updateUserString: string
  updateTime: string
}

export interface AppQuery {
  description?: string
  sort: Array<string>
}

export interface AppPageQuery extends AppQuery, PageQuery {}

/** 应用审批回调配置 */
export interface AppCallbackConfig {
  appId: string
  callbackUrl?: string
  /** 回调开关（1：启用；2：禁用） */
  callbackStatus?: 1 | 2
  /** 是否已配置（false：无扩展记录，展示默认关闭状态） */
  configured: boolean
}

/** 应用审批回调配置保存参数 */
export interface AppCallbackConfigReq {
  /** 回调开关（1：启用；2：禁用） */
  callbackStatus: 1 | 2
  /** 回调地址，启用时必填且须为合法 HTTP/HTTPS 地址，禁用时可保留原地址 */
  callbackUrl?: string
}

/** 查询审批回调配置响应 */
export type AppCallbackConfigResp = AppCallbackConfig
