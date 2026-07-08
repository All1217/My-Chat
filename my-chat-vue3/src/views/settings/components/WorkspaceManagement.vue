<template>
  <div class="workspace-container">
    <div class="sidebar">
      <div class="header">
        <el-icon :size="20" color="#9d48ff">
          <FolderOpened />
        </el-icon>
        <span class="title">工作区</span>
      </div>
      <div class="project-switcher">
        <el-input v-model="projectPath" placeholder="输入项目目录路径…" size="small" clearable @keyup.enter="handleSwitch" />
        <el-button type="primary" size="small" :loading="switching" @click="handleSwitch">
          打开项目
        </el-button>
      </div>
      <div v-if="currentRoot" class="current-root-line">
        <el-tooltip :content="currentRoot" placement="top">
          <span class="current-root-text">{{ currentRoot }}</span>
        </el-tooltip>
      </div>
      <el-tree ref="treeRef" :data="treeData" node-key="path" :props="treeProps" :highlight-current="true"
        :expand-on-click-node="true" :default-expand-all="true" @node-click="handleNodeClick" class="workspace-tree">
        <template #default="{ data }">
          <span class="tree-node-label" @dblclick.stop="handleNodeDblClick(data)">
            <el-icon v-if="data.directory" :size="16" color="#437dff">
              <Folder />
            </el-icon>
            <el-icon v-else :size="16" color="#606266">
              <Document />
            </el-icon>
            <span class="node-name">{{ data.name }}</span>
          </span>
        </template>
      </el-tree>
    </div>

    <div class="content">
      <div class="path-bar">
        <el-icon :size="16" color="#909399">
          <HomeFilled />
        </el-icon>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="(seg, idx) in pathSegments" :key="idx" @click="navigateToSeg(seg.relative)">
            <span class="breadcrumb-text">{{ seg.label }}</span>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button type="primary" size="small" round :icon="Plus" @click="handleCreateFolder">
          新建文件夹
        </el-button>
        <el-button size="small" round :icon="Upload" @click="handleImportFile">
          导入文件
        </el-button>
        <el-button size="small" round :icon="Refresh" @click="handleRefresh">
          刷新
        </el-button>
      </div>

      <div class="file-table">
        <el-table :data="fileList" style="width: 100%" stripe highlight-current-row empty-text="暂无文件"
          @row-contextmenu="handleRowContextMenu" @row-dblclick="handleRowDblClick">
          <el-table-column label="名称" min-width="200">
            <template #default="{ row }">
              <span class="file-name-cell">
                <el-icon v-if="row.directory" :size="18" color="#437dff" style="margin-right: 6px">
                  <Folder />
                </el-icon>
                <el-icon v-else :size="18" color="#606266" style="margin-right: 6px">
                  <Document />
                </el-icon>
                {{ row.name }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100" align="center">
            <template #default="{ row }">
              {{ formatSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="修改时间" width="170" align="center">
            <template #default="{ row }">
              {{ row.modifiedAt }}
            </template>
          </el-table-column>
        </el-table>

        <!-- 右键菜单（使用 el-popover + virtual-ref） -->
        <el-popover ref="popoverRef" :visible="popoverVisible" trigger="manual" placement="right-start" :width="500"
          :show-arrow="false" :virtual-ref="virtualTriggerRef" @hide="onPopoverHide">
          <div class="menu-list">
            <el-button link class="menu-btn" :disabled="!contextMenuRow || contextMenuRow.directory"
              @click="onMenuCommand('preview')">
              <el-icon :size="14" style="margin-right: 6px">
                <View />
              </el-icon>
              预览
            </el-button>
            <el-button link class="menu-btn" @click="onMenuCommand('rename')">
              <el-icon :size="14" style="margin-right: 6px">
                <Edit />
              </el-icon>
              重命名
            </el-button>
            <el-button link class="menu-btn" @click="onMenuCommand('delete')">
              <el-icon :size="14" style="margin-right: 6px">
                <Delete />
              </el-icon>
              删除
            </el-button>
          </div>
        </el-popover>
      </div>
      <input ref="fileInputRef" type="file" multiple style="display:none" @change="handleFileChange" />
    </div>

    <!-- 预览对话框（仅文本格式） -->
    <el-dialog v-model="previewVisible" :title="'文件预览'" width="900px" top="3vh" destroy-on-close
      @closed="onPreviewClosed">
      <!-- 文本预览 -->
      <pre v-if="previewType === 'text'" class="preview-content">{{ previewContent }}</pre>

      <!-- PDF 预览 -->
      <iframe v-else-if="previewType === 'pdf'" :src="previewPdfUrl" class="preview-iframe" frameborder="0" />

      <!-- DOCX / XLSX 预览（HTML） -->
      <div v-else-if="previewType === 'docx' || previewType === 'xlsx'" class="preview-html" v-html="previewHtml" />

      <!-- 图片预览 -->
      <img v-else-if="previewType === 'image'" :src="previewImageUrl" class="preview-image" alt="预览图片" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Folder, Document, Refresh, Plus, Upload } from '@element-plus/icons-vue'
import { workspaceApi } from '@/api/workspace'
import type { FileTreeNode, FileInfo } from '@/types/settings/types'
import mammoth from 'mammoth'
import * as XLSX from 'xlsx'

// ---------- 状态 ----------
const treeRef = ref<any>(null)
const treeData = ref<FileTreeNode[]>([])
const treeProps = { children: 'children', label: 'name' }
const currentPath = ref('')
const fileList = ref<FileInfo[]>([])

const projectPath = ref('')
const switching = ref(false)
const currentRoot = ref('')

const previewVisible = ref(false)
const previewContent = ref('')
const fileInputRef = ref<HTMLInputElement>()

// ---------- 面包屑分段 ----------
const pathSegments = computed(() => {
  if (!currentPath.value) {
    return [{ label: '工作区', relative: '' }]
  }
  const parts = currentPath.value.split('/')
  const segments = [{ label: '工作区', relative: '' }]
  let accumulated = ''
  for (const p of parts) {
    accumulated = accumulated ? `${accumulated}/${p}` : p
    segments.push({ label: p, relative: accumulated })
  }
  return segments
})

// ---------- 网络请求 ----------
async function fetchTree() {
  try {
    treeData.value = await workspaceApi.tree()
  } catch { /* 已 toast */ }
}

async function fetchFileList(path: string) {
  try {
    fileList.value = (await workspaceApi.list(path)).map((f) => ({
      ...f,
      previewable: !f.directory && isPreviewableExt(f.name),
    }))
  } catch { /* 已 toast */ }
}

// ---------- 辅助函数 ----------
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function isPreviewableExt(name: string): boolean {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return ['txt', 'md', 'json', 'xml', 'yaml', 'yml', 'properties', 'csv', 'log', 'sql', 'java', 'py', 'js', 'ts', 'html', 'css', 'vue', 'sh', 'bat', 'pdf'].includes(ext)
}

function navigateToSeg(relative: string) {
  currentPath.value = relative
  fetchFileList(relative)
}

// ---------- 事件处理 ----------
function handleNodeClick(data: FileTreeNode) {
  if (data.directory) {
    currentPath.value = data.path
    fetchFileList(data.path)
  }
}
// 双击文件跳转到所属目录
function handleNodeDblClick(data: FileTreeNode) {
  if (data.directory) return  // 目录不需要处理，单击已经导航
  // 提取父目录路径
  const lastSlash = data.path.lastIndexOf('/')
  const parentPath = lastSlash > -1 ? data.path.substring(0, lastSlash) : ''
  // 导航到父目录
  currentPath.value = parentPath
  fetchFileList(parentPath)
  // 左侧树高亮父目录节点
  if (treeRef.value) {
    treeRef.value.setCurrentKey(parentPath || '')   // 根目录可能 key 为空字符串
  }
}
async function handleCreateFolder() {
  try {
    const { value: folderName } = await ElMessageBox.prompt('请输入文件夹名称', '新建文件夹', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    if (!folderName) return

    await workspaceApi.createFolder(currentPath.value, folderName)
    await fetchTree()
    await fetchFileList(currentPath.value)
    ElMessage.success('文件夹创建成功')
  } catch {
    // 用户取消或请求失败（已 toast）
  }
}

function handleImportFile() {
  fileInputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  const formData = new FormData()
  for (const file of files) {
    formData.append('files', file)
  }
  formData.append('path', currentPath.value)

  try {
    await workspaceApi.importFiles(formData)
    await fetchTree()
    await fetchFileList(currentPath.value)
    ElMessage.success(`成功导入 ${files.length} 个文件`)
  } catch { /* 已 toast */ }
  input.value = ''
}
// 双击表格行
function handleRowDblClick(row: FileInfo) {
  if (row.directory) {
    // 进入该文件夹
    currentPath.value = row.path
    fetchFileList(row.path)
    // 同步左侧树高亮
    if (treeRef.value) {
      treeRef.value.setCurrentKey(row.path || '')
    }
  } else {
    // 预览文件
    handlePreview(row)
  }
}
const previewType = ref<'text' | 'pdf' | 'docx' | 'xlsx' | 'image' | ''>('')
const previewHtml = ref('')        // docx 转换后的 HTML / xlsx 转换后的 HTML table
const previewPdfUrl = ref('')      // PDF 的 Blob URL
const previewImageUrl = ref('')    // 图片的 Base64 URL
async function handlePreview(row: FileInfo) {
  const name = row.name

  if (isText(name)) {
    try {
      const content = await workspaceApi.readText(row.path)
      previewType.value = 'text'
      previewContent.value = content
      previewVisible.value = true
    } catch { /* 已 toast */ }
    return
  }

  try {
    const { base64, mimeType } = await workspaceApi.readBinary(row.path)

    if (isPdf(name)) {
      // PDF：转为 Blob URL，iframe 展示
      const byteChars = atob(base64)
      const byteNums = new Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) {
        byteNums[i] = byteChars.charCodeAt(i)
      }
      const byteArr = new Uint8Array(byteNums)
      const blob = new Blob([byteArr], { type: mimeType })
      // 释放之前的 URL
      if (previewPdfUrl.value) URL.revokeObjectURL(previewPdfUrl.value)
      previewPdfUrl.value = URL.createObjectURL(blob)
      previewType.value = 'pdf'
      previewVisible.value = true

    } else if (isDocx(name)) {
      // DOCX：mammoth 转 HTML
      const byteChars = atob(base64)
      const byteNums = new Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) {
        byteNums[i] = byteChars.charCodeAt(i)
      }
      const buffer = new Uint8Array(byteNums).buffer
      const result = await mammoth.convertToHtml({ arrayBuffer: buffer })
      previewHtml.value = result.value
      previewType.value = 'docx'
      previewVisible.value = true

    } else if (isXlsx(name)) {
      // XLSX：SheetJS 解析为 HTML 表格
      const byteChars = atob(base64)
      const byteNums = new Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) {
        byteNums[i] = byteChars.charCodeAt(i)
      }
      const workbook = XLSX.read(new Uint8Array(byteNums), { type: 'array' })
      // 取第一个 Sheet
      const sheetName = workbook.SheetNames[0]
      const sheet = workbook.Sheets[sheetName]
      const html = XLSX.utils.sheet_to_html(sheet, { id: 'xlsx-preview-table' })
      previewHtml.value = html
      previewType.value = 'xlsx'
      previewVisible.value = true

    } else if (isImage(name)) {
      // 图片：直接显示 data URL
      previewImageUrl.value = `data:${mimeType};base64,${base64}`
      previewType.value = 'image'
      previewVisible.value = true

    } else {
      ElMessage.warning('不支持预览该文件格式')
    }
  } catch { /* 已 toast */ }
}
/** 获取文件扩展名（小写） */
function getExt(name: string): string {
  return name.split('.').pop()?.toLowerCase() || ''
}
/** 是否为 PDF */
function isPdf(name: string): boolean {
  return getExt(name) === 'pdf'
}
/** 是否为 DOCX */
function isDocx(name: string): boolean {
  return getExt(name) === 'docx'
}
/** 是否为 XLSX */
function isXlsx(name: string): boolean {
  return getExt(name) === 'xlsx'
}
/** 是否为图片 */
function isImage(name: string): boolean {
  const exts = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp']
  return exts.includes(getExt(name))
}
/** 是否为文本类型（使用旧逻辑） */
function isText(name: string): boolean {
  const exts = ['txt', 'md', 'json', 'xml', 'yaml', 'yml', 'properties', 'csv', 'log', 'sql', 'java', 'py', 'js', 'ts', 'html', 'css', 'vue', 'sh', 'bat']
  return exts.includes(getExt(name))
}
function onPreviewClosed() {
  if (previewPdfUrl.value) {
    URL.revokeObjectURL(previewPdfUrl.value)
    previewPdfUrl.value = ''
  }
  previewType.value = ''
  previewContent.value = ''
  previewHtml.value = ''
  previewImageUrl.value = ''
}

async function handleRename(row: FileInfo) {
  try {
    const { value: newName } = await ElMessageBox.prompt('请输入新名称', '重命名', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: row.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    if (!newName || newName === row.name) return

    await workspaceApi.rename(row.path, newName)
    await fetchTree()
    await fetchFileList(currentPath.value)
    ElMessage.success('重命名成功')
  } catch {
    // 用户取消或请求失败（已 toast）
  }
}

async function handleDelete(row: FileInfo) {
  try {
    await ElMessageBox.confirm(
      row.directory
        ? `确定删除文件夹"${row.name}"及其内部所有内容吗？`
        : `确定删除"${row.name}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return // 用户取消
  }

  try {
    await workspaceApi.remove(row.path)
    await fetchTree()
    await fetchFileList(currentPath.value)
    ElMessage.success('删除成功')
  } catch { /* 已 toast */ }
}
async function handleSwitch() {
  if (!projectPath.value.trim()) {
    ElMessage.warning('请输入项目路径')
    return
  }
  switching.value = true
  try {
    const newRoot = await workspaceApi.switchRoot(projectPath.value.trim())
    currentRoot.value = newRoot
    await fetchTree()
    await fetchFileList('')
    currentPath.value = ''
    ElMessage.success(`已切换至: ${newRoot}`)
  } catch { /* 已 toast */ }
  switching.value = false
}

async function handleRefresh() {
  await fetchTree()
  await fetchFileList(currentPath.value)
  ElMessage.success('刷新成功')
}

/**
 * 右键菜单业务块
 */
// ---------- 右键菜单（el-popover + virtual-ref）----------
const popoverRef = ref<any>(null)
const popoverVisible = ref(false)
const contextMenuRow = ref<FileInfo | null>(null)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
// 虚拟触发对象：Popper 每次定位时读取最新鼠标坐标
const virtualTriggerRef = computed(() => ({
  getBoundingClientRect() {
    return {
      top: contextMenuY.value,
      bottom: contextMenuY.value,
      left: contextMenuX.value,
      right: contextMenuX.value,
      width: 0,
      height: 0,
      x: contextMenuX.value,
      y: contextMenuY.value,
      toJSON: () => ({}),
    }
  }
}))
function handleRowContextMenu(row: FileInfo, _column: any, event: MouseEvent) {
  event.preventDefault()
  contextMenuRow.value = row
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  popoverVisible.value = true
}
async function onMenuCommand(command: string) {
  if (!contextMenuRow.value) {
    return
  }
  switch (command) {
    case 'preview':
      await handlePreview(contextMenuRow.value)
      break
    case 'rename':
      handleRename(contextMenuRow.value)
      break
    case 'delete':
      await handleDelete(contextMenuRow.value)
      break
  }
  popoverVisible.value = false
}
function onPopoverHide() {
  popoverVisible.value = false
  contextMenuRow.value = null
}

// ---------- 生命周期 ----------
function handleDocumentClick(e: MouseEvent) {
  // 如果菜单没打开，什么也不做
  if (!popoverVisible.value) return
  // 获取 popover 内容节点
  const popoverContent = popoverRef.value?.popperRef?.contentRef
  // 如果点击目标不在 popover 内，关闭菜单
  if (popoverContent && !popoverContent.contains(e.target as Node)) {
    popoverVisible.value = false
    contextMenuRow.value = null
  }
}
onMounted(() => {
  document.addEventListener('click', handleDocumentClick, true)
  fetchTree()
  fetchFileList('')
})
onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick, true)
})
</script>

<style scoped>
.workspace-container {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ---------- 左侧树 ---------- */
.sidebar {
  width: 280px;
  min-width: 240px;
  background-color: #fff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar .header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px 12px;
  border-bottom: 1px solid #ebeef5;
}

.sidebar .header .title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.workspace-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
}

.node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* el-tree 选中节点高亮 */
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: #f0e6ff;
  color: #9d48ff;
}

:deep(.el-tree-node__content:hover) {
  background-color: #f5f0fa;
}

/* ---------- 右侧内容 ---------- */
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 24px;
  background-color: #f5f7fa;
  overflow: hidden;

  .file-table {
    position: relative;
    flex: 1;
    overflow-y: auto;
  }
}

.path-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;

  .breadcrumb-text {
    cursor: pointer;
  }
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.action-bar .el-button--primary {
  --el-button-bg-color: #9d48ff;
  --el-button-border-color: #9d48ff;
  --el-button-hover-bg-color: #8a3ee0;
  --el-button-hover-border-color: #8a3ee0;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.preview-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  background-color: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  max-height: 500px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
}

/* ---------- 响应式滚动 ---------- */
.sidebar,
.content {
  overflow: hidden;
}

.el-table {
  flex: 1;
  overflow-y: auto;
}

/* 右键菜单 */
.menu-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-btn {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  padding: 8px 12px;
  margin: 0;
  font-size: 13px;
  color: #303133;
  border: none;
  border-radius: 6px;
  transition: background 0.15s;
  box-sizing: border-box;
}

.menu-btn:hover:not(:disabled) {
  background-color: #f5f7fa;
  color: #9d48ff;
}

.menu-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* PDF iframe */
.preview-iframe {
  width: 100%;
  height: 75vh;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

/* DOCX / XLSX 渲染后的 HTML */
.preview-html {
  max-height: 70vh;
  overflow-y: auto;
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  line-height: 1.7;
}

/* XLSX 表格样式 */
.preview-html :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}

.preview-html :deep(th),
.preview-html :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  text-align: left;
}

.preview-html :deep(th) {
  background-color: #f5f7fa;
  font-weight: 600;
}

/* 图片预览 */
.preview-image {
  max-width: 100%;
  max-height: 75vh;
  display: block;
  margin: 0 auto;
  border-radius: 8px;
}

/* 项目切换器 */
.project-switcher {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
}

.current-root-line {
  padding: 4px 12px 8px;
  border-bottom: 1px solid #ebeef5;
}

.current-root-text {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
</style>