import { ElMessage } from 'element-plus'
import type { ResultData } from '@/types/models'

/**
 * 通用请求包装器（适用于 GET 查询类请求）
 *
 * 统一处理：try/catch → code 判断 → 错误提示 → 数据解包
 * 调用方只需关心"成功时拿到的业务数据"
 *
 * @param requestFn  - 返回 Promise<ResultData<T>> 的请求函数（如 ragHttp.get、crmHttp.post 等）
 * @param options    - 可选的错误提示配置
 * @returns 成功时返回解包后的业务数据 T；失败时返回 null
 *
 * @example
 * ```ts
 * const list = await request(
 *   () => ragHttp.get<ChatSessionVO[]>('/api/getConversations'),
 *   { errorMsg: '获取会话列表失败' }
 * )
 * if (list) { chatList.value = list }
 * ```
 */
export async function request<T>(
    requestFn: () => Promise<T>,
    options?: {
        /** 自定义错误提示文案，覆盖后端返回的 message */
        errorMsg?: string
        /** 是否静默处理错误（不弹出 ElMessage） */
        silent?: boolean
    }
): Promise<T | null> {
    try {
        const res = await requestFn()
        console.log('原始数据', res);
        if (res.status === 200 && res.data.code === 200) {
            return res.data
        }
        console.log('业务错误', res);
        // code ≠ 200：业务错误
        const msg = options?.errorMsg || res.message || '请求失败'
        if (!options?.silent) {
            ElMessage.error(msg)
        }
        return null
    } catch (e: any) {
        // 网络异常 / 超时等
        console.error(e)
        const msg = options?.errorMsg || e?.message || '网络请求失败'
        if (!options?.silent) {
            ElMessage.error(msg)
        }
        return null
    }
}

/**
 * 变更请求包装器（适用于 POST / PUT / DELETE 类请求）
 *
 * 与 `request` 的区别：只关心"操作是否成功"，不返回业务数据
 *
 * @param requestFn - 返回 Promise<ResultData<any>> 的请求函数
 * @param errorMsg  - 可选的统一错误提示
 * @returns true 表示成功；false 表示失败
 *
 * @example
 * ```ts
 * const ok = await mutate(
 *   () => ragHttp.post('/api/addConversation?conversationId=xxx')
 * )
 * if (ok) { /* 后续操作 *\/ }
 * ```
 */
export async function mutate(
    requestFn: () => Promise<ResultData<any>>,
    errorMsg?: string
): Promise<boolean> {
    const result = await request(requestFn, { errorMsg })
    return result !== null
}

/**
 * 查询并直接赋值给 ref（适用于"请求成功 → 覆盖赋值"的最简场景）
 *
 * @example
 * ```ts
 * await requestAndSet(
 *   () => ragHttp.get<ChatSessionVO[]>('/api/list'),
 *   chatList,
 *   '获取列表失败'
 * )
 * ```
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