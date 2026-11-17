<template>
  <div class="mcp-page">
    <div class="page-head">
      <div>
        <h3>MCP 工具管理</h3>
        <p>全局开关立即生效于下一轮主聊天 / Agent 工具发现；本地 MCP 需本机进程已启动。</p>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div v-loading="loading" class="cards">
      <article v-for="item in items" :key="item.id" class="card">
        <div class="card-head">
          <div class="card-title">
            <h4>{{ item.name }}</h4>
            <div class="tags">
              <el-tag size="small" effect="plain">{{ typeLabel(item.type) }}</el-tag>
              <code class="id">{{ item.id }}</code>
            </div>
          </div>
          <el-switch
            :model-value="item.enabled"
            :loading="savingId === item.id"
            @change="(v: boolean) => onToggle(item, v)"
          />
        </div>
        <p class="desc">{{ item.description }}</p>
        <div class="tools">
          <span class="label">主要工具</span>
          <el-tag
            v-for="t in item.tools"
            :key="t"
            size="small"
            type="info"
            effect="plain"
            class="tool-tag"
          >
            {{ t }}
          </el-tag>
        </div>
        <p v-if="item.updatedAt" class="meta">更新 {{ item.updatedAt }}</p>
      </article>
      <el-empty v-if="!loading && !items.length" description="暂无连接配置" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mcpToolsApi, type McpToolCatalogItem } from '@/views/settings/api/mcpTools'

const loading = ref(false)
const savingId = ref('')
const items = ref<McpToolCatalogItem[]>([])

async function load() {
  loading.value = true
  try {
    items.value = (await mcpToolsApi.list()) ?? []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function onToggle(item: McpToolCatalogItem, enabled: boolean) {
  savingId.value = item.id
  try {
    const saved = await mcpToolsApi.setEnabled(item.id, enabled)
    const idx = items.value.findIndex((x) => x.id === item.id)
    if (idx >= 0) {
      items.value[idx] = saved
    }
    ElMessage.success(enabled ? `已启用 ${item.name}` : `已关闭 ${item.name}`)
  } catch {
    /* toast */
  } finally {
    savingId.value = ''
  }
}

function typeLabel(type: string) {
  if (type === 'local-mcp') return '本地 MCP'
  if (type === 'remote-mcp') return '远端 MCP'
  if (type === 'rest') return 'REST'
  return type || '—'
}

onMounted(load)
</script>

<style scoped lang="less">
.mcp-page {
  width: 100%;
  box-sizing: border-box;
  padding: 24px 28px 40px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;

  h3 {
    margin: 0;
    font-size: 22px;
    color: #1a1a2e;
  }

  p {
    margin: 8px 0 0;
    font-size: 13px;
    color: #64748b;
    line-height: 1.5;
    max-width: 52rem;
  }
}

.cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1100px) {
  .cards {
    grid-template-columns: 1fr;
  }
}

.card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px 20px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  min-height: 168px;
  display: flex;
  flex-direction: column;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 10px;

  .card-title {
    min-width: 0;
  }

  h4 {
    margin: 0 0 8px;
    font-size: 16px;
    color: #0f172a;
  }

  .tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .id {
    font-size: 11px;
    color: #94a3b8;
    background: #f8fafc;
    padding: 2px 6px;
    border-radius: 4px;
  }
}

.desc {
  margin: 0 0 14px;
  font-size: 13px;
  color: #475569;
  line-height: 1.55;
  flex: 1;
}

.tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;

  .label {
    font-size: 12px;
    color: #94a3b8;
    margin-right: 2px;
  }
}

.tool-tag {
  margin: 0;
}

.meta {
  margin: 14px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
