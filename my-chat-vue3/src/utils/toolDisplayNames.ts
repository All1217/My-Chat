/**
 * FileTools / 常见工具名 → 时间线中文标题。
 * 未知 MCP 工具回退为原始 name。
 */
export const TOOL_DISPLAY_NAMES: Record<string, string> = {
  ls: '列出目录',
  cat: '读取文件',
  write: '写入文件',
  rm: '删除',
  mv: '移动/重命名',
  cp: '复制',
  mkdir: '创建目录',
  stat: '文件信息',
  grep: '搜索文本',
  tree: '目录树',
}

export function toolDisplayName(name: string | undefined | null): string {
  if (!name) return '未知工具'
  return TOOL_DISPLAY_NAMES[name] ?? name
}
