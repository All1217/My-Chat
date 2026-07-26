/**
 * 后端 tool_result.preview 常被二次 JSON 化（形如 "\"目录: ...\""）。
 * 前端展示前尽量剥掉一层字符串包装。
 */
export function unwrapToolPreview(raw?: string | null): string {
  if (raw == null || raw === '') return ''
  let s = raw
  try {
    const once = JSON.parse(s)
    if (typeof once === 'string') {
      s = once
    }
  } catch {
    // 非 JSON 则原样返回
  }
  return s
}
