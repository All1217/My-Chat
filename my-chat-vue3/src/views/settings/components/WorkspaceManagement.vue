<template>
  <div class="workspace-container">
    <div class="sidebar">
      <div class="header">
        <el-icon :size="20" color="#9d48ff">
          <FolderOpened />
        </el-icon>
        <span class="title">工作区</span>
      </div>
      <el-tree ref="treeRef" :data="treeData" node-key="path" :props="treeProps" :highlight-current="true"
        :expand-on-click-node="true" :default-expand-all="true" @node-click="handleNodeClick" class="workspace-tree">
        <template #default="{ node, data }">
          <span class="tree-node-label">
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
            {{ seg.label }}
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
      </div>

      <div style="position: relative;">
        <el-table :data="fileList" style="width: 100%" stripe highlight-current-row empty-text="暂无文件"
          @row-contextmenu="handleRowContextMenu">
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
          <!-- 原操作列移除，改为右键菜单触发的 Dropdown -->
        </el-table>

        <!-- 自定义右键菜单 -->
        <div v-if="contextMenuVisible" class="context-menu"
          :style="{ left: contextMenuX + 'px', top: contextMenuY + 'px' }">
          <div class="context-item" @click="handlePreview(contextMenuRow)">预览</div>
          <div class="context-item" @click="handleRename(contextMenuRow)">重命名</div>
          <div class="context-item" @click="handleDelete(contextMenuRow)">删除</div>
        </div>

        <!-- 点击空白区域关闭菜单 -->
        <div v-if="contextMenuVisible" class="context-overlay" @click="closeContextMenu"></div>
      </div>
    </div>

    <!-- 预览对话框（仅文本格式） -->
    <el-dialog v-model="previewVisible" title="文件预览" width="800px" top="5vh" destroy-on-close>
      <pre class="preview-content">{{ previewContent }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Folder, Document, HomeFilled, Plus, Upload } from '@element-plus/icons-vue'
import { ragHttp } from '@/utils/http'
import { request } from '@/utils/request'
import { FileTreeNode, FileInfo } from '@/types/settings/types'

// ---------- 状态 ----------
const treeRef = ref<any>(null)
const treeData = ref<FileTreeNode[]>([])
const treeProps = { children: 'children', label: 'name' }
const currentPath = ref('')
const fileList = ref<FileInfo[]>([])

const previewVisible = ref(false)
const previewContent = ref('')

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
  const data = await request(
    () =>
      ragHttp.get<FileTreeNode[]>('/ai/file/workspace/tree', {
        path: '',
      }), { errorMsg: '获取目录树失败' }
  )
  if (data) {
    treeData.value = data
  }
}

async function fetchFileList(path: string) {
  const data = await request(
    () =>
      ragHttp.get<FileInfo[]>('/ai/file/workspace/list', { path }),
    { errorMsg: '获取文件列表失败' }
  )
  if (data) {
    fileList.value = data.map((f) => ({
      ...f,
      previewable: !f.directory && isPreviewableExt(f.name),
    }))
  }
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

// ---------- 右键菜单 ----------
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuRow = ref<FileInfo | null>(null)

function handleRowContextMenu(row: FileInfo, column: any, event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  contextMenuRow.value = row
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuVisible.value = true
}

function closeContextMenu() {
  contextMenuVisible.value = false
  contextMenuRow.value = null
}

// ---------- 事件处理 ----------
function handleNodeClick(data: FileTreeNode) {
  if (data.directory) {
    currentPath.value = data.path
    fetchFileList(data.path)
  }
}

function handleCreateFolder() {
  ElMessage.info('新建文件夹功能待实现')
}

function handleImportFile() {
  ElMessage.info('导入文件功能待实现')
}

async function handlePreview(row: FileInfo) {
  const content = await request(
    () =>
      ragHttp.get<string>('/ai/file/workspace/read', {
        path: row.path,
      }),
    { errorMsg: '读取文件失败' }
  )
  if (content !== null) {
    previewContent.value = content
    previewVisible.value = true
  }
}

function handleRename(row: FileInfo) {
  ElMessage.info('重命名功能待实现')
}

function handleDelete(row: FileInfo) {
  ElMessageBox.confirm(`确定删除"${row.name}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    // 实际删除逻辑待实现
    ElMessage.success('删除成功（模拟）')
  }).catch(() => { })
}

// ---------- 生命周期 ----------
onMounted(() => {
  fetchTree()
  fetchFileList('')
})
</script>

<style scoped>
.workspace-container {
  display: flex;
  height: calc(100vh - 120px);
  background-color: #f5f7fa;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
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
  overflow: hidden;
}

.path-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
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

/* 右键菜单 */
.context-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: transparent;
}

.context-menu {
  position: fixed;
  z-index: 1000;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 100px;
}

.context-item {
  padding: 8px 16px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  transition: background 0.15s;
}

.context-item:hover {
  background-color: #f5f0fa;
  color: #9d48ff;
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
</style>