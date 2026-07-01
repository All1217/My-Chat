<template>
  <div class="knowledge-store-container">
    <div class="upload-section">
      <el-button type="primary" :icon="Upload" :loading="uploading" @click="handleUploadClick">
        上传文档并向量化
      </el-button>
      <input
        ref="fileInputRef"
        type="file"
        style="display: none"
        @change="handleFileChange"
      />
      <span class="upload-tip">支持 txt / md / pdf / docx 等文本文件</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { ragHttp } from '@/utils/http'
import { request } from '@/utils/request'

const fileInputRef = ref<HTMLInputElement>()
const uploading = ref(false)

function handleUploadClick() {
  fileInputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploading.value = true
  const formData = new FormData()
  formData.append('file', file)

  const data = await request(
    () => ragHttp.post<{ documentId: string; filename: string; message: string; embeddingCount: number }>(
      '/ai/file/upload',
      formData
    ),
    { errorMsg: '文件上传失败' }
  )

  uploading.value = false
  input.value = ''   // 清空 input，以便重复选择同一文件

  if (data) {
    ElMessage.success(`上传成功：${data.message}（${data.embeddingCount} 个分段）`)
  }
}
</script>

<style scoped>
.knowledge-store-container {
  padding: 24px;
}

.upload-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-tip {
  font-size: 13px;
  color: #909399;
}
</style>