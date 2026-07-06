import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { ResultEnum } from '@/types/enums'
import { ApiError, type ResultData, type RequestOptions } from './types'

type Config = AxiosRequestConfig & RequestOptions

class HttpClient {
  private readonly axios: AxiosInstance

  constructor(baseURL: string) {
    this.axios = axios.create({
      baseURL,
      timeout: ResultEnum.TIMEOUT as number,
    })
    this.setupInterceptors()
  }

  private setupInterceptors() {
    this.axios.interceptors.request.use((config) => {
      config.headers['X-Access-Token'] =
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6IjgwMTAwMzk2IiwiY2xpZW50VHlwZSI6IlBDIiwiZXhwIjoxNzc2OTc2MTU0fQ.CjRzmrPp9hcW3fd5if6kD6htN24gyHiemLaw0r-gx4Y'
      return config
    })

    this.axios.interceptors.response.use(
      (response): any => {
        const result = response.data as ResultData
        const opts = response.config as Config
        if (result.code === ResultEnum.SUCCESS) {
          return result.data
        }
        const msg = opts.errorMsg ?? result.message ?? '请求失败'
        if (!opts.silent) ElMessage.error(msg)
        throw new ApiError(result.code, msg, !!opts.silent)
      },
      (error) => {
        const opts = error.config as Config | undefined
        const msg = opts?.errorMsg ?? error.message ?? '网络请求失败'
        if (!opts?.silent) ElMessage.error(msg)
        return Promise.reject(error)
      },
    )
  }

  get<T>(url: string, options?: RequestOptions): Promise<T> {
    return this.axios.get(url, this.toConfig(options)) as Promise<T>
  }

  post<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return this.axios.post(url, data, this.toConfig(options)) as Promise<T>
  }

  put<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return this.axios.put(url, data, this.toConfig(options)) as Promise<T>
  }

  delete<T>(url: string, options?: RequestOptions): Promise<T> {
    return this.axios.delete(url, this.toConfig(options)) as Promise<T>
  }

  private toConfig(options?: RequestOptions): Config {
    if (!options) return {}
    const { params, silent, errorMsg, ...rest } = options
    return { params, silent, errorMsg, ...rest }
  }
}

export const ragClient = new HttpClient('/rag')
export const crmClient = new HttpClient('/api')
