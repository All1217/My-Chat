<template>
  <div class="step-node" :class="[`kind-${data.kind}`, `st-${data.runStatus}`]">
    <Handle type="target" :position="Position.Left" :connectable="false" />
    <div class="step-head">
      <span class="kind-dot" />
      <strong>{{ data.label }}</strong>
      <span class="badge">{{ statusLabel }}</span>
    </div>
    <p v-if="data.summary" class="summary">{{ data.summary }}</p>
    <p v-else-if="data.error" class="summary err">{{ data.error }}</p>
    <Handle type="source" :position="Position.Right" :connectable="false" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{
  data: {
    label: string
    kind: string
    runStatus: string
    summary?: string
    error?: string
  }
}>()

const statusLabel = computed(() => {
  const s = (props.data.runStatus || 'IDLE').toUpperCase()
  if (s === 'SUCCEEDED') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'RUNNING') return '进行中'
  return '未执行'
})
</script>

<style scoped lang="less">
.step-node {
  min-width: 168px;
  max-width: 220px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #cbd5e1;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
  font-size: 12px;
  color: #0f172a;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;

  strong {
    flex: 1;
    font-size: 13px;
    font-weight: 700;
    line-height: 1.2;
  }
}

.kind-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #64748b;
  flex-shrink: 0;
}

.kind-start .kind-dot {
  background: #2563eb;
}
.kind-ai .kind-dot {
  background: #7c3aed;
}
.kind-end .kind-dot {
  background: #0f766e;
}
.kind-process .kind-dot {
  background: #0891b2;
}

.badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  white-space: nowrap;
}

.st-SUCCEEDED {
  border-color: #86efac;
  background: #f0fdf4;
  .badge {
    background: #dcfce7;
    color: #166534;
  }
}

.st-FAILED {
  border-color: #fca5a5;
  background: #fef2f2;
  .badge {
    background: #fee2e2;
    color: #991b1b;
  }
}

.st-RUNNING {
  border-color: #fde68a;
  background: #fffbeb;
  .badge {
    background: #fef3c7;
    color: #92400e;
  }
}

.summary {
  margin: 0;
  color: #64748b;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.summary.err {
  color: #b91c1c;
}
</style>
