<template>
  <el-dialog v-model="visible" title="选择目录" width="550" append-to-body teleported @open="handleOpen">
    <div class="dir-picker">
      <div class="dir-picker-breadcrumb">
        <el-breadcrumb>
          <el-breadcrumb-item>
            <span class="bread-link" @click="navigateToRoot">根目录</span>
          </el-breadcrumb-item>
          <el-breadcrumb-item v-for="(seg, idx) in breadcrumbSegments" :key="idx">
            <span class="bread-link" @click="navigateToBreadcrumb(idx)">{{ seg }}</span>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <div class="dir-picker-current">
        <el-icon><FolderOpened /></el-icon>
        <span>{{ currentDisplayPath }}</span>
      </div>
      <div class="dir-picker-body">
        <el-scrollbar max-height="320px">
          <div v-if="loading" class="dir-picker-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <div v-else-if="items.length === 0" class="dir-picker-empty">
            <span>该目录下没有子目录</span>
          </div>
          <div v-else class="dir-picker-list">
            <div v-for="item in items" :key="item.path"
              class="dir-picker-item"
              @dblclick="enterItem(item)">
              <el-icon><Folder /></el-icon>
              <span class="dir-picker-item-name">{{ item.name }}</span>
              <el-icon class="dir-picker-item-enter" @click.stop="enterItem(item)"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { workspaceApi } from '@/api/workspace'
import type { FileInfo } from '@/types/settings/types'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'selected': [path: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

const loading = ref(false)
const items = ref<FileInfo[]>([])
/** 当前浏览的绝对路径 */
const currentPath = ref('')

const breadcrumbSegments = computed(() => {
  if (!currentPath.value) return []
  const path = currentPath.value.replace(/\\/g, '/')
  const parts = path.split('/').filter(Boolean)
  // 移除驱动器字母后的空段（如 C: → ['C:']）
  return parts
})

const currentDisplayPath = computed(() => {
  return currentPath.value || '（根目录）'
})

async function handleOpen() {
  loading.value = true
  try {
    const roots = await workspaceApi.listRoots()
    items.value = roots.map(r => ({
      name: r,
      path: r,
      directory: true,
      size: 0,
      createdAt: '',
      modifiedAt: '',
    }))
    currentPath.value = ''
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function navigateToRoot() {
  await handleOpen()
}

async function navigateToBreadcrumb(index: number) {
  const path = currentPath.value.replace(/\\/g, '/')
  const parts = path.split('/').filter(Boolean)
  const targetParts = parts.slice(0, index + 1)
  const targetPath = targetParts.join('/')
  // 如果是 Windows 路径如 C: 则补全为 C:/
  const fullPath = targetPath.includes(':') ? targetPath + '/' : '/' + targetPath
  await browseDirectory(fullPath)
}

async function enterItem(item: FileInfo) {
  await browseDirectory(item.path)
}

async function browseDirectory(path: string) {
  loading.value = true
  try {
    items.value = await workspaceApi.browse(path)
    currentPath.value = path
  } catch (e: any) {
    const msg = e?.message || ''
    if (msg.includes('禁止浏览系统目录') || msg.includes('不可作为工作区')) {
      ElMessage.error('该目录不可作为工作区，请更换其他目录！')
    } else {
      ElMessage.error('无法访问该目录')
    }
  } finally {
    loading.value = false
  }
}

function handleConfirm() {
  if (!currentPath.value) {
    ElMessage.warning('请选择一个目录')
    return
  }
  emit('selected', currentPath.value)
  visible.value = false
}
</script>

<style scoped lang="less">
.dir-picker {
  min-height: 200px;

  .dir-picker-breadcrumb {
    margin-bottom: 8px;
    padding: 4px 0;
    border-bottom: 1px solid #ebeef5;

    .bread-link {
      color: #409eff;
      cursor: pointer;
      font-size: 13px;

      &:hover {
        color: #66b1ff;
      }
    }
  }

  .dir-picker-current {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    background: #f5f7fa;
    border-radius: 4px;
    margin-bottom: 8px;
    font-size: 13px;
    color: #606266;

    .el-icon {
      flex-shrink: 0;
    }
  }

  .dir-picker-body {
    .dir-picker-loading,
    .dir-picker-empty {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 40px 0;
      color: #909399;
    }

    .dir-picker-list {
      .dir-picker-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 8px;
        border-radius: 4px;
        cursor: pointer;
        transition: background-color 0.2s;

        &:hover {
          background-color: #f0f5ff;

          .dir-picker-item-enter {
            opacity: 1;
          }
        }

        .el-icon {
          flex-shrink: 0;
          color: #437dff;
        }

        .dir-picker-item-name {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          font-size: 14px;
        }

        .dir-picker-item-enter {
          opacity: 0;
          transition: opacity 0.2s;
          color: #909399;
          font-size: 14px;

          &:hover {
            color: #409eff;
          }
        }
      }
    }
  }
}
</style>
