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
        </div>
        <!-- Orchestrator / 质量环步骤 -->
        <div v-else-if="part.type === 'step'" class="step-row">
          <span class="tool-title">
            <el-icon v-if="part.status === 'running'" class="is-loading" :size="14"><Loading /></el-icon>
            步骤 {{ part.stepIndex }} → {{ part.displayName || actionLabel(part.action) }}
            <el-tag size="small" :type="statusTagType(part.status || 'done')" effect="plain">
              {{ statusLabel(part.status || 'done') }}
            </el-tag>
            <el-tag v-if="part.action === 'retrieve_kb' && part.kbScope" size="small"
              :type="part.kbScope === 'catalog' ? 'info' : 'success'" effect="plain">
              {{ part.kbScope === 'catalog' ? '文档目录' : '向量检索' }}
            </el-tag>
          </span>
          <span v-if="part.elapsedMs != null" class="elapsed">{{ part.elapsedMs }} ms</span>
          <KbCitationTags v-if="part.action === 'retrieve_kb'" :citations="part.citations" />
          <el-collapse v-if="hasStepDetail(part)">
            <el-collapse-item title="查看结果摘要" :name="part.id">
              <div v-if="part.observation" class="detail-block">
                <div class="detail-label">结果摘要</div>
                <pre class="detail-pre">{{ part.observation }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
          <div v-if="childTools(part.id).length" class="tool-children">
            <div v-for="tool in childTools(part.id)" :key="tool.id" class="tool-child">
              <el-icon v-if="tool.status === 'running'" class="is-loading" :size="13"><Loading /></el-icon>
              <span>{{ tool.displayName || toolDisplayName(tool.name) }}</span>
              <el-tag size="small" :type="statusTagType(tool.status)" effect="plain">
                {{ statusLabel(tool.status) }}
              </el-tag>
              <span v-if="tool.elapsedMs != null" class="elapsed">{{ tool.elapsedMs }} ms</span>
            </div>
          </div>
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
              <div v-if="part.resultPreview" class="detail-block">
                <div class="detail-label">结果摘要</div>
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
      p.type === 'route' || p.type === 'step'
      || (p.type === 'tool' && !p.parentStepId),
  ),
)

function partKey(part: TimelinePart) {
  return part.id
}

function isRunning(part: TimelinePart) {
  return (part.type === 'tool' || part.type === 'step') && part.status === 'running'
}

function itemType(part: TimelinePart) {
  if (part.type === 'route') return 'info'
  if (part.type === 'step') return timelineType(part.status || 'done')
  return timelineType(part.status)
}

function routeLabel(route: string) {
  return ROUTE_LABELS[route] ?? route
}

function actionLabel(action: string) {
  return ACTION_LABELS[action] ?? action
}

function hasStepDetail(part: StepMessagePart) {
  return !!part.observation
}

function childTools(stepId: string): ToolMessagePart[] {
  return (props.parts ?? []).filter(
    (part): part is ToolMessagePart =>
      part.type === 'tool' && part.parentStepId === stepId,
  )
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
  return !!part.resultPreview
}

function detailTitle(part: ToolMessagePart) {
  if (part.status === 'running') return '查看参数'
  return '查看参数 / 结果'
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

.elapsed {
  font-size: 11px;
  color: #909399;
}

.tool-children {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 5px 8px;
  background: #f7f8fa;
  border-radius: 6px;
}

.tool-child {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
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
