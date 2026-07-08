import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import { setupBigintAxios } from '@yaoshu/bigint-utils/axios'
import { getToken, removeToken } from './auth'

// 后端 /api/* 管理接口响应格式：yue-library Result（向前规范，不兼容已废弃的 Go 格式）
interface ResultFormat<T = unknown> {
  code: number
  msg: string
  flag: boolean
  traceId: string
  data: T
}

/**
 * 业务错误：flag:false 时由拦截器统一抛出。
 * 携带后端 ResultCode（code），供消费方按错误类型精确分支（如 channel test 601-607）。
 */
export class BusinessError extends Error {
  readonly code: number
  constructor(message: string, code: number) {
    super(message)
    this.name = 'BusinessError'
    this.code = code
  }
}

// 自定义配置标记：需要保留响应头的接口（如 login token 在 header 而非 body）
declare module 'axios' {
  interface InternalAxiosRequestConfig {
    _withHeaders?: boolean
    _silent?: boolean
  }
  interface AxiosRequestConfig {
    _withHeaders?: boolean
    _silent?: boolean
  }
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
  // CORS 跨域携带凭据（后端 sa-token 已关闭 Cookie 通道，此配置保留兼容）
  withCredentials: true
})

// BigInt 安全：必须在 transformResponse 阶段替换 JSON.parse，拦截器阶段精度已丢失
setupBigintAxios(service)

// 请求拦截器：注入鉴权头
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      // 后端 sa-token：token-name 为 yaoshu-token，通过此 Header 携带
      config.headers['yaoshu-token'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：双格式适配（/api/* 管理接口 vs /v1/* AI 中转）
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const url = response.config.url || ''
    // /v1/* AI 中转 OpenAI 兼容格式：透传，不做 success 解包
    if (url.startsWith('/v1/')) {
      return response.data
    }
    // /api/* 管理接口：yue-library Result 格式 {code, msg, flag, traceId, data}
    const body = response.data as ResultFormat
    if (body && typeof body.flag === 'boolean') {
      if (body.flag) {
        if (response.config._withHeaders) {
          return response
        }
        return body.data
      }
      const err = new BusinessError(body.msg || '请求失败', body.code)
      if (!response.config._silent) {
        ElMessage.error(err.message)
      }
      return Promise.reject(err)
    }
    // 非标准格式（如文件流/纯文本），透传
    return response.data
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      // 鉴权失效：清会话并跳登录页（带 redirect 回跳）
      removeToken()
      if (window.location.pathname !== '/sign-in') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/sign-in?redirect=${redirect}`
      }
      ElMessage.error('登录已过期，请重新登录')
    } else if (!error?.config?._silent) {
      const msg = error?.response?.data?.msg || error?.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

// 业务侧统一入口：经拦截器解包后直接返回 data，类型由调用方泛型标注
export const request = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as unknown as Promise<T>
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  // login 需完整 AxiosResponse（拦截器不解包），返回后由调用方从 response.data.data 提取
  postWithHeaders<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> {
    return service.post(url, data, { ...config, _withHeaders: true }) as unknown as Promise<AxiosResponse<T>>
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.patch(url, data, config) as unknown as Promise<T>
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as unknown as Promise<T>
  }
}

export default service
