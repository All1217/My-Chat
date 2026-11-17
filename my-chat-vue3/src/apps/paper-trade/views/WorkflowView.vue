<template>
  <div class="workflow-page">
    <div class="page-head">
      <div>
        <h1>可视化工作流</h1>
        <p>只读蓝图 · 叠加最近一轮运行状态（非可编辑编排）</p>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="activeId" class="tabs">
      <el-tab-pane
        v-for="g in graphs"
        :key="g.id"
        :label="g.title"
        :name="g.id"
      />
    </el-tabs>

    <div v-if="current" class="meta-bar">
      <template v-if="current.latest">
        <span>最近一轮 <code>{{ current.latest.runId }}</code></span>
        <el-tag size="small" :type="tagType(current.latest.status)">
          {{ current.latest.status || '—' }}
        </el-tag>
        <span class="muted">创建 {{ current.latest.createdAt || '—' }}</span>
        <span class="muted">结束 {{ current.latest.finishedAt || '—' }}</span>
        <span v-if="current.latest.errorMessage" class="err">{{ current.latest.errorMessage }}</span>
      </template>
      <span v-else class="muted">暂无运行记录，仅展示固定蓝图</span>
    </div>

    <div class="canvas-box">
      <WorkflowCanvas :graph="current" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { paperTradeApi } from '@/apps/paper-trade/api'
import type { PaperWorkflowGraph } from '@/apps/paper-trade/types'
import WorkflowCanvas from '@/apps/paper-trade/components/WorkflowCanvas.vue'

const loading = ref(false)
const graphs = ref<PaperWorkflowGraph[]>([])
const activeId = ref('researcher')

const current = computed(
  () => graphs.value.find((g) => g.id === activeId.value) ?? graphs.value[0] ?? null,
)

async function load() {
  loading.value = true
  try {
    const data = await paperTradeApi.workflows()
    graphs.value = data?.graphs ?? []
    if (!graphs.value.some((g) => g.id === activeId.value) && graphs.value[0]) {
      activeId.value = graphs.value[0].id
    }
  } catch {
    graphs.value = []
  } finally {
    loading.value = false
  }
}

function tagType(status?: string | null) {
  const s = (status || '').toUpperCase()
  if (s === 'SUCCEEDED') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING' || s === 'PENDING') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<style scoped lang="less">
.workflow-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 8px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;

  h1 {
    margin: 0;
    font-size: 20px;
    color: #14532d;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: #64748b;
  }
}

.tabs {
  :deep(.el-tabs__header) {
    margin: 0;
  }
}

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;

  code {
    font-size: 11px;
    background: #f1f5f9;
    padding: 1px 4px;
    border-radius: 4px;
  }

  .muted {
    color: #94a3b8;
  }

  .err {
    color: #b91c1c;
  }
}

.canvas-box {
  flex: 1;
  min-height: 440px;
}
</style>
