<template>
  <div v-if="timelineParts.length" class="agent-timeline">
    <el-timeline>
      <el-timeline-item v-for="part in timelineParts" :key="partKey(part)"
        :type="itemType(part)" :hollow="isRunning(part)">
        <!-- 路由决策 -->
        <div v-if="part.type === 'route'" class="route-row">
          <span class="tool-title">
            路由 → {{ routeLabel(part.route) }}
            <el-tag size="small" type="info" effect="plain">已分流</el-tag>
          </span>
          <div v-if="part.reasoning" class="route-reason">{{ part.reasoning }}</div>
        </div>
        <!-- Orchestrator / 质量环步骤 -->
        <div v-else-if="part.type === 'step'" class="step-row">
          <span class="tool-title">
            步骤 {{ part.stepIndex }} → {{ actionLabel(part.action) }}
            <el-tag size="small" type="warning" effect="plain">编排</el-tag>
          </span>
          <div v-if="part.reasoning" class="route-reason">{{ part.reasoning }}</div>
          <KbCitationTags v-if="part.action === 'retrieve_kb'" :citations="part.citations" />
          <el-collapse v-if="hasStepDetail(part)">
            <el-collapse-item title="查看指令 / 观察" :name="part.id">
              <div v-if="part.instruction" class="detail-block">
                <div class="detail-label">指令</div>
                <pre class="detail-pre">{{ part.instruction }}</pre>
              </div>
              <div v-if="part.observation" class="detail-block">
                <div class="detail-label">观察</div>
                <pre class="detail-pre">{{ part.observation }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <!-- 工具调用 -->
        <div v-else-if="part.type === 'tool'" class="tool-row">
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
import type {
  MessagePart,
  RouteMessagePart,
  StepMessagePart,
  ToolMessagePart,
} from '@/types/AiModule/streamEvents'
import { toolDisplayName } from '@/utils/toolDisplayNames'
import KbCitationTags from '@/components/KbCitationTags.vue'

const props = defineProps<{
  parts?: MessagePart[] | null
}>()

type TimelinePart = ToolMessagePart | RouteMessagePart | StepMessagePart

const ROUTE_LABELS: Record<string, string> = {
  file: '文件工具',
  kb: '知识库',
  search: '联网搜索',
  general: '普通对话',
  orchestrate: '多步编排',
}

const ACTION_LABELS: Record<string, string> = {
  retrieve_kb: '知识库检索',
  file: '文件工具',
  search: '联网搜索',
  general: '普通对话',
  finish: '完成',
  evaluate_optimize: '写盘质量环',
}

const timelineParts = computed(() =>
  (props.parts ?? []).filter(
    (p): p is TimelinePart =>
      p.type === 'tool' || p.type === 'route' || p.type === 'step',
  ),
)

function partKey(part: TimelinePart) {
  return part.id
}

function isRunning(part: TimelinePart) {
  return part.type === 'tool' && part.status === 'running'
}

function itemType(part: TimelinePart) {
  if (part.type === 'route') return 'info'
  if (part.type === 'step') return 'warning'
  return timelineType(part.status)
}

function routeLabel(route: string) {
  return ROUTE_LABELS[route] ?? route
}

function actionLabel(action: string) {
  return ACTION_LABELS[action] ?? action
}

function hasStepDetail(part: StepMessagePart) {
  return !!part.instruction || !!part.observation
}

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

.tool-row,
.route-row,
.step-row {
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

.route-reason {
  font-size: 12px;
  color: #909399;
  line-height: 1.45;
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
