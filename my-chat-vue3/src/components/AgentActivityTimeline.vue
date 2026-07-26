<template>
  <div v-if="toolParts.length" class="agent-timeline">
    <el-timeline>
      <el-timeline-item v-for="part in toolParts" :key="part.id" :type="timelineType(part.status)"
        :hollow="part.status === 'running'">
        <div class="tool-row">
          <span class="tool-title">
            <el-icon v-if="part.status === 'running'" class="is-loading" :size="14">
              <Loading />
            </el-icon>
            {{ toolDisplayName(part.name) }}
            <el-tag size="small" :type="statusTagType(part.status)" effect="plain">
              {{ statusLabel(part.status) }}
            </el-tag>
          </span>
          <el-collapse v-if="hasDetail(part)">
            <el-collapse-item :title="detailTitle(part)" :name="part.id">
              <div v-if="part.args != null" class="detail-block">
                <div class="detail-label">参数</div>
                <pre class="detail-pre">{{ formatJson(part.args) }}</pre>
              </div>
              <div v-if="part.resultPreview" class="detail-block">
                <div class="detail-label">结果预览</div>
                <pre class="detail-pre">{{ part.resultPreview }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { MessagePart, ToolMessagePart } from '@/types/AiModule/streamEvents'
import { toolDisplayName } from '@/utils/toolDisplayNames'

const props = defineProps<{
  parts?: MessagePart[] | null
}>()

const toolParts = computed(() =>
  (props.parts ?? []).filter((p): p is ToolMessagePart => p.type === 'tool'),
)

function timelineType(status: ToolMessagePart['status']) {
  switch (status) {
    case 'done':
      return 'success'
    case 'error':
      return 'danger'
    case 'cancelled':
      return 'warning'
    default:
      return 'primary'
  }
}

function statusTagType(status: ToolMessagePart['status']) {
  switch (status) {
    case 'done':
      return 'success'
    case 'error':
      return 'danger'
    case 'cancelled':
      return 'warning'
    default:
      return 'info'
  }
}

function statusLabel(status: ToolMessagePart['status']) {
  switch (status) {
    case 'running':
      return '执行中'
    case 'done':
      return '完成'
    case 'error':
      return '失败'
    case 'cancelled':
      return '已取消'
    default:
      return status
  }
}

function hasDetail(part: ToolMessagePart) {
  return part.args != null || !!part.resultPreview
}

function detailTitle(part: ToolMessagePart) {
  if (part.status === 'running') return '查看参数'
  return '查看参数 / 结果'
}

function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
</script>

<style scoped lang="less">
.agent-timeline {
  margin-bottom: 10px;
  padding: 4px 0 0 0;
  max-width: 100%;

  :deep(.el-timeline) {
    padding-left: 4px;
  }

  :deep(.el-timeline-item__wrapper) {
    padding-left: 18px;
  }

  :deep(.el-collapse-item__header) {
    font-size: 12px;
    height: 32px;
    line-height: 32px;
    color: #606266;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 8px;
  }
}

.tool-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.detail-block {
  margin-top: 4px;
}

.detail-label {
  font-size: 11px;
  color: #909399;
  margin-bottom: 2px;
}

.detail-pre {
  margin: 0;
  padding: 8px;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f5f7fa;
  border-radius: 6px;
  max-height: 200px;
  overflow: auto;
}
</style>
