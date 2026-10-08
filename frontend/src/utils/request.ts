import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types'

// 业务成功码（后端统一返回体 code；具体成功码以后端 §2.4 为准，此处按 0 约定）
const SUCCESS_CODE = 0

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截：可在此注入 token 等（当前系统无登录，预留）
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => config,
  (error) => Promise.reject(error)
)

// 响应拦截：统一解析 {code,message,data}，集中处理错误
request.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const res = response.data
    // 未包裹统一返回体（如静态资源/异常透传）直接返回
    if (res == null || typeof res.code === 'undefined') {
      return response.data as never
    }
    if (res.code === SUCCESS_CODE) {
      return res.data as never
    }
    // 业务码非成功：提示后端 message，由具体页决定后续动作
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Business error'))
  },
  (error) => {
    const status = error?.response?.status
    const msg = error?.response?.data?.message || error?.message || '网络异常'
    if (status === 400) {
      ElMessage.error(`参数错误：${msg}`)
    } else if (status === 409) {
      // 冲突（每格仅1人/时间段重叠/删除保护）——不静默吞掉，由具体页决定后续动作
      ElMessage.error(`冲突：${msg}`)
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
