<template>
  <div class="ks-layout">
    <aside class="ks-sidebar">
      <div class="sidebar-header">
        <h2>知识库</h2>
        <el-button type="primary" :icon="Plus" circle size="small" @click="showCreateDialog = true" />
      </div>
      <el-menu :default-active="activeKbId" class="ks-menu" @select="handleSelectKb">
        <el-menu-item v-for="kb in kbList" :key="kb.id" :index="kb.id">
          <span class="kb-name">{{ kb.name }}</span>
          <el-button link type="danger" size="small" class="delete-btn" :icon="Delete"
            @click.stop="handleDeleteKb(kb.id)" />
        </el-menu-item>
        <el-menu-item v-if="kbList.length === 0" disabled>
          暂无知识库，点击右上角 + 新建
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="ks-content">
      <div class="top-bar">
        <span class="current-kb-title" v-if="currentKb">{{ currentKb.name }}</span>
        <span v-else class="current-kb-title">请选择一个知识库</span>
        <div class="top-actions">
          <el-button type="primary" :icon="Upload" :disabled="!currentKb" :loading="uploading"
            @click="handleUploadClick">
            上传文档
          </el-button>
          <input ref="fileInputRef" type="file" style="display: none" @change="handleFileChange" />
          <el-button type="primary" :icon="ArrowLeft" @click="$router.push({ name: 'lobby' })">
            回到大厅
          </el-button>
          <el-button type="primary" :icon="House" @click="$router.push({ name: 'home' })">
            回到首页
          </el-button>
        </div>
      </div>

      <div class="content-area">
        <el-table :data="docList" v-if="currentKb" stripe style="width: 100%" empty-text="暂无文档，点击上方上传">
          <el-table-column prop="filename" label="文件名" min-width="200" />
          <el-table-column prop="fileType" label="类型" width="80" />
          <el-table-column prop="fileSize" label="大小" width="100">
            <template #default="{ row }">
              {{ formatSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分片数" width="80" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="180" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteDoc(row.id)" />
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-hint">选择知识库后查看文档列表</div>
      </div>
    </main>

    <el-dialog v-model="showCreateDialog" title="新建知识库" width="400px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="createForm.name" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateKb">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Delete, House, ArrowLeft } from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api/knowledge'
import type { KnowledgeBase, DocumentMeta } from '@/types/knowledgeStore/types'

const fileInputRef = ref<HTMLInputElement>()
const uploading = ref(false)
const creating = ref(false)
const showCreateDialog = ref(false)
const activeKbId = ref('')
const kbList = ref<KnowledgeBase[]>([])
const docList = ref<DocumentMeta[]>([])
const createForm = ref({ name: '', description: '' })

const currentKb = computed(() => kbList.value.find(kb => kb.id === activeKbId.value))

function statusType(status: string) {
  if (status === 'READY') return 'success'
  if (status === 'PROCESSING') return 'warning'
  return 'danger'
}

function formatSize(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function fetchKbList() {
  try {
    kbList.value = await knowledgeApi.list()
  } catch { /* 已 toast */ }
}

async function fetchDocList() {
  if (!activeKbId.value) { docList.value = []; return }
  try {
    docList.value = await knowledgeApi.documents(activeKbId.value)
  } catch { /* 已 toast */ }
}

function handleSelectKb(id: string) {
  activeKbId.value = id
  fetchDocList()
}

async function handleCreateKb() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creating.value = true
  try {
    await knowledgeApi.create(createForm.value.name, createForm.value.description)
    showCreateDialog.value = false
    createForm.value = { name: '', description: '' }
    await fetchKbList()
  } catch { /* 已 toast */ }
  creating.value = false
}

async function handleDeleteKb(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该知识库及其所有文档？', '确认', { type: 'warning' })
  } catch { return }
  try {
    await knowledgeApi.remove(id)
    if (activeKbId.value === id) { activeKbId.value = ''; docList.value = [] }
    await fetchKbList()
  } catch { /* 已 toast */ }
}

function handleUploadClick() {
  fileInputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !activeKbId.value) return

  uploading.value = true
  try {
    const data = await knowledgeApi.upload(file, activeKbId.value)
    ElMessage.success(`上传成功：${data.message}（${data.embeddingCount} 个分段）`)
    await fetchDocList()
  } catch { /* 已 toast */ }
  uploading.value = false
  input.value = ''
}

async function handleDeleteDoc(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该文档及其向量数据？', '确认', { type: 'warning' })
  } catch { return }
  try {
    await knowledgeApi.deleteDocument(id)
    await fetchDocList()
  } catch { /* 已 toast */ }
}

onMounted(() => {
  fetchKbList()
})
</script>

<style scoped lang="less">
.ks-layout {
  display: flex;
  height: 100vh;
  background-color: #fff;

  .ks-sidebar {
    width: 240px;
    background: #fff;
    border-right: 1px solid #e4e7ed;
    display: flex;
    flex-direction: column;

    .sidebar-header {
      padding: 20px 16px 16px;
      border-bottom: 1px solid #ebeef5;
      display: flex;
      justify-content: space-between;
      align-items: center;

      h2 {
        font-size: 20px;
        font-weight: 700;
        color: #1a1a2e;
        margin: 0;
      }
    }

    .ks-menu {
      border-right: none;
      flex: 1;
      overflow-y: auto;

      .el-menu-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 14px;
        color: #606266;

        .kb-name {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .delete-btn {
          visibility: hidden;
          flex-shrink: 0;
        }

        &:hover .delete-btn {
          visibility: visible;
        }

        &.is-active {
          color: #437dff;
          background-color: #ecf5ff;
        }
      }
    }
  }

  .ks-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .top-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 24px;
      border-bottom: 1px solid #ebeef5;
      background: #fff;

      .current-kb-title {
        font-size: 18px;
        font-weight: 600;
        color: #303133;
      }

      .top-actions {
        display: flex;
        gap: 8px;
      }
    }

    .content-area {
      flex: 1;
      padding: 16px 24px;
      overflow-y: auto;

      .empty-hint {
        text-align: center;
        color: #909399;
        margin-top: 80px;
        font-size: 14px;
      }
    }
  }
}
</style>
