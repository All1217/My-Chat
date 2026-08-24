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
          <el-button :icon="Setting" :disabled="!currentKb" @click="openSettings">
            设置
          </el-button>
          <el-button type="primary" :icon="Upload" :disabled="!currentKb" @click="showUploadDialog = true">
            上传文档
          </el-button>
          <el-button type="success" :icon="ChatDotRound" :disabled="!currentKb" @click="goChat">
            开始问答
          </el-button>
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
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tooltip v-if="row.status === 'FAILED' && row.errorMessage" :content="row.errorMessage"
                placement="top">
                <el-tag type="danger" size="small">{{ row.status }}</el-tag>
              </el-tooltip>
              <el-tag v-else :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="180" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :icon="RefreshRight"
                :disabled="row.status === 'PROCESSING'" @click="handleReindexDoc(row)">
                重新向量化
              </el-button>
              <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteDoc(row.id)" />
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-hint">选择知识库后查看文档列表</div>
      </div>
    </main>

    <el-dialog v-model="showUploadDialog" title="上传文档" width="520px" @closed="fileList = []">
      <el-upload drag multiple :auto-upload="false" accept=".pdf,.docx,.xlsx,.html,.htm,.txt,.md"
        v-model:file-list="fileList">
        <el-icon class="el-icon--upload">
          <UploadFilled />
        </el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em>（最多 20 个）</div>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="fileList.length === 0" @click="submitUpload">
          开始入库
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSettingsDialog" title="知识库设置" width="520px">
      <el-form :model="settingsForm" label-width="120px">
        <el-form-item label="名称">
          <el-input v-model="settingsForm.name" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="settingsForm.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
        <el-divider content-position="left">入库切分</el-divider>
        <p class="settings-hint">单位为 token。修改后只影响之后新上传的文档。</p>
        <el-form-item label="切分大小">
          <el-input-number v-model="settingsForm.chunkSize" :min="64" :max="4000" :step="64" />
        </el-form-item>
        <el-form-item label="重叠大小">
          <el-input-number v-model="settingsForm.chunkOverlap" :min="0"
            :max="Math.max(0, settingsForm.chunkSize - 1)" />
        </el-form-item>
        <el-alert v-if="splitParamsChanged && docList.length > 0" type="warning" :closable="false" show-icon
          title="已入库文档不会自动按新切分重嵌，请在文档列表点击「重新向量化」。" class="settings-alert" />
        <el-divider content-position="left">检索</el-divider>
        <p class="settings-hint">立刻影响问答，无需重传文档。</p>
        <el-form-item label="返回条数 topK">
          <el-input-number v-model="settingsForm.topK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="相似度阈值">
          <el-input-number v-model="settingsForm.similarityThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSettingsDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingSettings" @click="handleSaveSettings">保存</el-button>
      </template>
    </el-dialog>

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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadUserFile } from 'element-plus'
import { Plus, Upload, UploadFilled, Delete, House, ArrowLeft, ChatDotRound, Setting, RefreshRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { knowledgeApi } from '@/api/knowledge'
import { useNotifyStore } from '@/stores/notify'
import type { KnowledgeBase, DocumentMeta } from '@/types/knowledgeStore/types'

const router = useRouter()
const notifyStore = useNotifyStore()

const submitting = ref(false)
const creating = ref(false)
const savingSettings = ref(false)
const showCreateDialog = ref(false)
const showUploadDialog = ref(false)
const showSettingsDialog = ref(false)
const fileList = ref<UploadUserFile[]>([])
const activeKbId = ref('')
const kbList = ref<KnowledgeBase[]>([])
const docList = ref<DocumentMeta[]>([])
const createForm = ref({ name: '', description: '' })
const settingsForm = ref({
  name: '',
  description: '',
  chunkSize: 800,
  chunkOverlap: 0,
  topK: 5,
  similarityThreshold: 0.5,
})
const settingsSnapshot = ref({ chunkSize: 800, chunkOverlap: 0 })

const currentKb = computed(() => kbList.value.find(kb => kb.id === activeKbId.value))
const splitParamsChanged = computed(() =>
  settingsForm.value.chunkSize !== settingsSnapshot.value.chunkSize
  || settingsForm.value.chunkOverlap !== settingsSnapshot.value.chunkOverlap)

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

function openSettings() {
  const kb = currentKb.value
  if (!kb) return
  settingsForm.value = {
    name: kb.name,
    description: kb.description ?? '',
    chunkSize: kb.chunkSize ?? 800,
    chunkOverlap: kb.chunkOverlap ?? 0,
    topK: kb.topK ?? 5,
    similarityThreshold: kb.similarityThreshold ?? 0.5,
  }
  settingsSnapshot.value = {
    chunkSize: settingsForm.value.chunkSize,
    chunkOverlap: settingsForm.value.chunkOverlap,
  }
  showSettingsDialog.value = true
}

async function handleSaveSettings() {
  if (!currentKb.value) return
  if (!settingsForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  if (settingsForm.value.chunkOverlap >= settingsForm.value.chunkSize) {
    ElMessage.warning('重叠 token 数须小于切分大小')
    return
  }
  savingSettings.value = true
  try {
    const updated = await knowledgeApi.update({
      id: currentKb.value.id,
      name: settingsForm.value.name.trim(),
      description: settingsForm.value.description,
      chunkSize: settingsForm.value.chunkSize,
      chunkOverlap: settingsForm.value.chunkOverlap,
      topK: settingsForm.value.topK,
      similarityThreshold: settingsForm.value.similarityThreshold,
    })
    const idx = kbList.value.findIndex(k => k.id === updated.id)
    if (idx >= 0) kbList.value[idx] = updated
    showSettingsDialog.value = false
    ElMessage.success('已保存')
  } catch { /* 已 toast */ }
  savingSettings.value = false
}

/** 跳转到聊天页面，并携带当前选中的知识库信息 */
function goChat() {
  if (!currentKb.value) return
  router.push({ name: 'chat', query: { kbId: currentKb.value.id, kbName: currentKb.value.name } })
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

async function submitUpload() {
  if (!activeKbId.value) return
  const files: File[] = []
  for (const item of fileList.value) {
    if (item.raw) files.push(item.raw)
  }
  if (files.length === 0) {
    ElMessage.warning('请选择文件')
    return
  }
  if (files.length > 20) {
    ElMessage.warning('单次最多上传 20 个文件')
    return
  }
  notifyStore.unlockSound()
  submitting.value = true
  try {
    const accepted = await knowledgeApi.uploadBatch(files, activeKbId.value)
    ElMessage.success(`已提交 ${accepted.length} 个文档，可离开本页`)
    showUploadDialog.value = false
    fileList.value = []
    await fetchDocList()
  } catch { /* 已 toast */ }
  submitting.value = false
}

let pollTimer: ReturnType<typeof setInterval> | undefined
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}
function syncPolling() {
  const hasProcessing = docList.value.some(d => d.status === 'PROCESSING')
  if (hasProcessing && !pollTimer) {
    pollTimer = setInterval(() => { fetchDocList() }, 3000)
  } else if (!hasProcessing) {
    stopPolling()
  }
}

watch(docList, syncPolling, { deep: true })

let unsubTerminal: (() => void) | undefined

async function handleDeleteDoc(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该文档及其向量数据？', '确认', { type: 'warning' })
  } catch { return }
  try {
    await knowledgeApi.deleteDocument(id)
    await fetchDocList()
  } catch { /* 已 toast */ }
}

async function handleReindexDoc(row: DocumentMeta) {
  try {
    await ElMessageBox.confirm(
      '将按当前知识库切分参数重新切段并覆盖向量，无需重新上传。',
      '重新向量化',
      { type: 'warning' },
    )
  } catch { return }
  try {
    await knowledgeApi.reindexDocument(row.id)
    ElMessage.success('已提交重新向量化')
    await fetchDocList()
  } catch { /* 已 toast */ }
}

onMounted(() => {
  fetchKbList()
  unsubTerminal = notifyStore.onJobTerminal((job) => {
    if (job.jobType === 'kb_ingest') fetchDocList()
  })
})

onUnmounted(() => {
  stopPolling()
  unsubTerminal?.()
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

.settings-hint {
  margin: 0 0 12px 120px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.settings-alert {
  margin: 0 0 16px 120px;
}
</style>
