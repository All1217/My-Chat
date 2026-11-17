<template>
  <div class="canvas-wrap">
    <VueFlow
      :nodes="flowNodes"
      :edges="flowEdges"
      :node-types="nodeTypes"
      :nodes-draggable="false"
      :nodes-connectable="false"
      :elements-selectable="true"
      :pan-on-drag="true"
      :zoom-on-scroll="true"
      fit-view-on-init
      class="flow"
      @node-click="onNodeClick"
    >
      <Background :gap="18" :size="1" pattern-color="#cbd5e1" />
      <Controls :show-interactive="false" />
    </VueFlow>

    <aside v-if="selectedAi" class="agent-panel">
      <div class="agent-head">
        <strong>AI 步骤摘要</strong>
        <el-button link type="primary" @click="selectedAi = false">关闭</el-button>
      </div>
      <p v-if="!agentSteps.length" class="empty">本轮无 agent.steps</p>
      <ul v-else>
        <li v-for="(s, i) in agentSteps" :key="i">
          <span class="idx">#{{ s.index ?? i }}</span>
          <span class="act">{{ s.action || '—' }}</span>
          <span class="st">{{ s.status || '' }}</span>
          <p v-if="s.observation">{{ s.observation }}</p>
        </li>
      </ul>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch } from 'vue'
import { VueFlow, type Edge, type Node, type NodeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import WorkflowStepNode from './WorkflowStepNode.vue'
import type { PaperWorkflowGraph } from '@/apps/paper-trade/types'

const props = defineProps<{
  graph: PaperWorkflowGraph | null
}>()

const nodeTypes = { step: markRaw(WorkflowStepNode) }
const selectedAi = ref(false)

const stepMap = computed(() => {
  const map = new Map<string, { status: string; summary?: string; error?: string }>()
  for (const s of props.graph?.latest?.domainSteps ?? []) {
    if (s.step) {
      map.set(s.step, {
        status: (s.status || 'IDLE').toUpperCase(),
        summary: s.summary || undefined,
        error: s.error || undefined,
      })
    }
  }
  return map
})

const agentSteps = computed(() => props.graph?.latest?.agentSteps ?? [])

const flowNodes = computed<Node[]>(() => {
  const nodes = props.graph?.nodes ?? []
  return nodes.map((n) => {
    const hit = stepMap.value.get(n.id)
    return {
      id: n.id,
      type: 'step',
      position: { x: n.x ?? 0, y: n.y ?? 0 },
      data: {
        label: n.label,
        kind: n.type || 'process',
        runStatus: hit?.status || 'IDLE',
        summary: hit?.summary,
        error: hit?.error,
      },
      draggable: false,
      connectable: false,
    }
  })
})

const flowEdges = computed<Edge[]>(() =>
  (props.graph?.edges ?? []).map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    type: 'smoothstep',
    animated: false,
    style: { stroke: '#94a3b8' },
  })),
)

watch(
  () => props.graph?.id,
  () => {
    selectedAi.value = false
  },
)

function onNodeClick(ev: NodeMouseEvent) {
  const kind = (ev.node.data as { kind?: string })?.kind
  if (kind === 'ai') {
    selectedAi.value = true
  }
}
</script>

<style scoped lang="less">
.canvas-wrap {
  position: relative;
  height: 100%;
  min-height: 420px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  background: #f8fafc;
}

.flow {
  width: 100%;
  height: 100%;
}

.agent-panel {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 280px;
  max-height: calc(100% - 20px);
  overflow: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
  z-index: 5;
  font-size: 12px;

  .agent-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .empty {
    margin: 0;
    color: #94a3b8;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  li {
    padding: 8px 0;
    border-top: 1px solid #f1f5f9;

    .idx {
      color: #94a3b8;
      margin-right: 6px;
    }
    .act {
      font-weight: 600;
      color: #14532d;
    }
    .st {
      float: right;
      color: #64748b;
    }
    p {
      margin: 4px 0 0;
      color: #64748b;
      line-height: 1.35;
      word-break: break-word;
    }
  }
}
</style>
