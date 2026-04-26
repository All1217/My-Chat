import { ElMessage } from 'element-plus'
import type { ResultData } from '@/types/models'

/**
 * 通用请求包装器
 *
 * 自动 try/catch，校验业务 code，解包 data，失败时弹出提示
 *
 * @param requestFn - 返回 ResultData<T> 的请求函数
 * @param options   - 可选错误配置
 * @returns 成功时返回 data（T），失败时返回 null
 */
export async function request<T>(
  requestFn: () => Promise<ResultData<T>>,
  options?: {
    errorMsg?: string
    silent?: boolean
  }
): Promise<T | null> {
  try {
    const res = await requestFn()   // res 直接是 { code, message, data }
    if (res.code === 200) {
      return res.data
    }
    // 业务错误
    const msg = options?.errorMsg || res.message || '请求失败'
    if (!options?.silent) {
      ElMessage.error(msg)
    }
    return null
  } catch (e: any) {
    console.error(e)
    // 网络异常（Axios 错误已在拦截器中弹过，这里可只记录）
    if (!options?.silent) {
      ElMessage.error(options?.errorMsg || e?.message || '网络请求失败')
    }
    return null
  }
}

/**
 * 变更请求包装器（POST/PUT/DELETE）
 * 只关心操作是否成功，不返回业务数据
 */
export async function mutate(
    requestFn: () => Promise<ResultData<any>>,
    errorMsg?: string
): Promise<boolean> {
    try {
        const res = await requestFn()
        if (res.code === 200) {
            return true
        }
        console.log('业务错误:', res)
        ElMessage.error(errorMsg || res.message || '请求失败')
        return false
    } catch (e: any) {
        console.error('网络错误:', e)
        ElMessage.error(errorMsg || e?.message || '网络请求失败')
        return false
    }
}

/**
 * 查询并直接赋值给 ref
 */
export async function requestAndSet<T>(
  requestFn: () => Promise<ResultData<T>>,
  targetRef: { value: T },
  errorMsg?: string
): Promise<boolean> {
  const data = await request(requestFn, { errorMsg })
  if (data !== null) {
    targetRef.value = data
    return true
  }
  return false
}