<template>
  <div class="hist">
    <div class="hist-head">
      <h1>历史记录</h1>
      <p>管理阶段总结、AI 决策、搜集轮次与研究卡片</p>
    </div>

    <div class="hist-body">
      <el-menu :default-active="activeKey" class="res-menu" @select="onSelect">
        <el-menu-item v-for="r in resources" :key="r.key" :index="r.key">
          {{ r.title }}
        </el-menu-item>
      </el-menu>

      <div class="res-panel">
        <div class="toolbar">
          <h2>{{ current?.title }}</h2>
          <div class="toolbar-actions">
            <el-button size="small" :loading="loading" @click="loadList">刷新</el-button>
            <el-button
              v-if="current?.creatable"
              size="small"
              type="primary"
              @click="openCreate"
            >
              新建
            </el-button>
          </div>
        </div>

        <div class="table-wrap">
          <el-table
            v-loading="loading"
            :data="rows"
            stripe
            size="small"
            height="100%"
            empty-text="暂无数据"
          >
          <el-table-column
            v-for="col in current?.columns ?? []"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :show-overflow-tooltip="col.overflow"
            :formatter="
              col.prop === 'kept' || col.prop === 'watchlist' ? boolFormatter : undefined
            "
          />
          <el-table-column
            label="操作"
            :width="current?.key === 'research-notes' ? 200 : 140"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="current?.key === 'research-notes'"
                link
                type="success"
                @click="openQuote(row)"
              >
                看行情
              </el-button>
              <el-button link type="primary" @click="openEdit(row)">详情</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="quoteDrawerOpen"
      :title="quoteDrawerTitle"
      size="720px"
      destroy-on-close
    >
      <StockBoardPanel :symbol="quoteSymbol" />
    </el-drawer>

    <el-drawer v-model="drawerOpen" :title="drawerTitle" size="420px" destroy-on-close>
      <el-form label-position="top" class="edit-form">
        <el-form-item
          v-for="field in editableFields"
          :key="field.key"
          :label="field.label"
        >
          <el-input
            v-if="field.type === 'textarea'"
            v-model="form[field.key]"
            type="textarea"
            :rows="4"
            :disabled="isFieldReadonly(field.key)"
          />
          <el-switch
            v-else-if="field.type === 'switch'"
            v-model="form[field.key]"
            :disabled="isFieldReadonly(field.key)"
          />
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="form[field.key]"
            :controls="false"
            style="width: 100%"
            :disabled="isFieldReadonly(field.key)"
          />
          <el-input
            v-else
            v-model="form[field.key]"
            :disabled="isFieldReadonly(field.key)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button
          v-if="canSave"
          type="primary"
          :loading="saving"
          @click="onSave"
        >
          保存
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StockBoardPanel from '@/apps/paper-trade/components/StockBoardPanel.vue'
import { historyResources, type HistoryResource } from '@/apps/paper-trade/history/resources'

const resources = historyResources
const activeKey = ref(resources[0]?.key ?? '')
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const saving = ref(false)
const drawerOpen = ref(false)
const creating = ref(false)
const form = reactive<Record<string, any>>({})
const editingId = ref('')
const quoteDrawerOpen = ref(false)
const quoteSymbol = ref('')

const quoteDrawerTitle = computed(() =>
  quoteSymbol.value ? `行情 · ${quoteSymbol.value}` : '行情',
)

const current = computed<HistoryResource | undefined>(() =>
  resources.find((r) => r.key === activeKey.value),
)

const drawerTitle = computed(() => {
  const title = current.value?.title ?? ''
  return creating.value ? `新建 · ${title}` : `详情 · ${title}`
})

const editableFields = computed(() => current.value?.formFields ?? [])

const canSave = computed(() => {
  if (creating.value) return !!current.value?.creatable && !!current.value?.create
  return !!current.value?.update
})

function isFieldReadonly(key: string) {
  if (creating.value) return false
  // 研究卡片主键不可改；搜集轮次只读查看
  if (current.value?.key === 'research-notes' && key === 'symbol') return true
  if (current.value?.key === 'research-runs') return true
  return false
}

function boolFormatter(_row: unknown, _col: unknown, cell: unknown) {
  return cell ? '是' : '否'
}

function onSelect(key: string) {
  activeKey.value = key
  loadList()
}

async function loadList() {
  if (!current.value) return
  loading.value = true
  try {
    rows.value = await current.value.list(80)
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function resetForm(seed: Record<string, unknown> = {}) {
  for (const k of Object.keys(form)) {
    delete form[k]
  }
  for (const field of editableFields.value) {
    const v = seed[field.key]
    if (field.type === 'switch') {
      form[field.key] = v === true
    } else if (field.type === 'number') {
      form[field.key] = v == null || v === '' ? undefined : Number(v)
    } else {
      form[field.key] = v == null ? '' : String(v)
    }
  }
}

function openCreate() {
  creating.value = true
  editingId.value = ''
  // 人工新建研究卡片：留下 + 自选默认开启
  resetForm(
    current.value?.key === 'research-notes' ? { kept: true, watchlist: true } : { kept: true },
  )
  drawerOpen.value = true
}

async function openEdit(row: Record<string, unknown>) {
  if (!current.value) return
  creating.value = false
  const id = String(row[current.value.idField] ?? '')
  editingId.value = id
  try {
    const detail = await current.value.get(id)
    resetForm(detail)
    drawerOpen.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

/** 研究卡片：打开可复用行情看板。 */
function openQuote(row: Record<string, unknown>) {
  const symbol = String(row.symbol ?? '').trim()
  if (!symbol) {
    ElMessage.warning('无股票代码')
    return
  }
  quoteSymbol.value = symbol
  quoteDrawerOpen.value = true
}

async function onSave() {
  if (!current.value) return
  saving.value = true
  try {
    const body = { ...form }
    if (creating.value) {
      await current.value.create?.(body)
      ElMessage.success('已创建')
    } else {
      await current.value.update?.(editingId.value, body)
      ElMessage.success('已保存')
    }
    drawerOpen.value = false
    await loadList()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Record<string, unknown>) {
  if (!current.value) return
  const id = String(row[current.value.idField] ?? '')
  try {
    await ElMessageBox.confirm(`确定删除 ${id}？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await current.value.remove(id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped lang="less">
.hist {
  /* 占满内容区且不撑出外层滚动条，只让表格内部滚动 */
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.hist-head {
  margin-bottom: 10px;
  flex-shrink: 0;

  h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 800;
    color: #166534;
  }

  p {
    margin: 2px 0 0;
    font-size: 12px;
    color: #64748b;
  }
}

.hist-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 10px;
}

.res-menu {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: auto;

  .el-menu-item {
    height: 40px;
    line-height: 40px;
    font-size: 13px;

    &.is-active {
      color: #15803d;
      background: #ecfdf5;
    }
  }
}

.res-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  flex-shrink: 0;

  h2 {
    margin: 0;
    font-size: 14px;
    color: #14532d;
  }

  .toolbar-actions {
    display: flex;
    gap: 6px;
  }
}

.edit-form {
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}
</style>
