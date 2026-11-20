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
            <el-dropdown trigger="click" :disabled="exporting" @command="onExportTable">
              <el-button size="small" :loading="exporting">
                导出
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="current?.tableName"
                    :command="current.tableName"
                  >
                    当前表（{{ current.tableName }}）
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-for="t in exportTableOptions"
                    :key="t"
                    :command="t"
                    :divided="t === exportTableOptions[0]"
                  >
                    {{ t }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
            :show-overflow-tooltip="col.overflow && !isTagColumn(col)"
            :formatter="isTagColumn(col) ? undefined : columnFormatter(col)"
          >
            <template v-if="isTagColumn(col)" #default="{ row }">
              <EnumTag
                v-if="col.format === 'action'"
                kind="action"
                :value="row[col.prop]"
              />
              <EnumTag
                v-else-if="col.format === 'execStatus'"
                kind="execStatus"
                :value="row[col.prop]"
              />
              <EnumTag
                v-else-if="col.format === 'runStatus'"
                kind="runStatus"
                :value="row[col.prop]"
              />
              <SignalTags
                v-else-if="col.format === 'signalTags'"
                :value="row[col.prop]"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            :width="current?.key === 'research-notes' ? 280 : 140"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="current?.key === 'research-notes'"
                link
                type="warning"
                :loading="refreshingSymbol === String(row.symbol ?? '')"
                :disabled="!!refreshingSymbol && refreshingSymbol !== String(row.symbol ?? '')"
                @click="onRefreshNote(row)"
              >
                更新数据
              </el-button>
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

    <el-dialog
      v-model="drawerOpen"
      :title="drawerTitle"
      width="820px"
      align-center
      destroy-on-close
      :close-on-click-modal="false"
      class="hist-detail-dialog"
      append-to-body
    >
      <div class="detail-shell">
        <div v-if="!creating && detailMeta.length" class="detail-meta">
          <span v-for="m in detailMeta" :key="m.label" class="meta-chip">
            <em>{{ m.label }}</em>
            <EnumTag v-if="m.kind" :kind="m.kind" :value="m.raw" />
            <template v-else>{{ m.value }}</template>
          </span>
        </div>

        <el-form label-position="top" class="edit-form">
          <div v-if="compactFields.length" class="field-grid">
            <el-form-item
              v-for="field in compactFields"
              :key="field.key"
              :label="field.label"
            >
              <el-switch
                v-if="field.type === 'switch'"
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
              <el-select
                v-else-if="field.type === 'valueTier'"
                v-model="form[field.key]"
                style="width: 100%"
                :disabled="isFieldReadonly(field.key)"
              >
                <el-option
                  v-for="opt in VALUE_TIER_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <div v-else-if="field.type === 'signalTags'" class="signal-tags-field">
                <SignalTags :value="form[field.key]" />
                <el-input
                  v-model="form[field.key]"
                  type="textarea"
                  :rows="2"
                  :disabled="isFieldReadonly(field.key)"
                  placeholder='JSON 数组或逗号分隔，如 ["低吸","ETF"]'
                  resize="vertical"
                />
              </div>
              <el-input
                v-else
                v-model="form[field.key]"
                :disabled="isFieldReadonly(field.key)"
              />
            </el-form-item>
          </div>

          <div v-if="blockFields.length" class="field-blocks">
            <el-form-item
              v-for="field in blockFields"
              :key="field.key"
              :label="field.label"
            >
              <el-input
                v-model="form[field.key]"
                type="textarea"
                :rows="textareaRows(field.key)"
                :disabled="isFieldReadonly(field.key)"
                resize="vertical"
              />
            </el-form-item>
          </div>
        </el-form>
      </div>

      <template #footer>
        <div class="detail-footer">
          <span class="footer-hint">{{ canSave ? '可编辑字段保存后立即生效' : '只读查看' }}</span>
          <div class="footer-actions">
            <el-button @click="drawerOpen = false">关闭</el-button>
            <el-button
              v-if="canSave"
              type="primary"
              :loading="saving"
              @click="onSave"
            >
              保存
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StockBoardPanel from '@/apps/paper-trade/components/StockBoardPanel.vue'
import { paperTradeApi } from '@/apps/paper-trade/api'
import {
  historyResources,
  type HistoryColumn,
  type HistoryResource,
} from '@/apps/paper-trade/history/resources'
import EnumTag from '@/apps/paper-trade/components/EnumTag.vue'
import SignalTags from '@/apps/paper-trade/components/SignalTags.vue'
import {
  formatDateTime,
  type EnumTagKind,
} from '@/apps/paper-trade/utils/format'
import {
  VALUE_TIER_OPTIONS,
  formatValueTier,
} from '@/apps/paper-trade/utils/valueTier'

const resources = historyResources
const activeKey = ref(resources[0]?.key ?? '')
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const drawerOpen = ref(false)
const creating = ref(false)
const form = reactive<Record<string, any>>({})
const editingId = ref('')
const quoteDrawerOpen = ref(false)
const quoteSymbol = ref('')
const refreshingSymbol = ref('')
let refreshTimer: ReturnType<typeof setInterval> | null = null

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

const compactFields = computed(() =>
  editableFields.value.filter((f) => f.type !== 'textarea'),
)

const blockFields = computed(() =>
  editableFields.value.filter((f) => f.type === 'textarea'),
)

const canSave = computed(() => {
  if (creating.value) return !!current.value?.creatable && !!current.value?.create
  return !!current.value?.update
})

/** 详情顶栏摘要：优先展示状态 / 代码 / ID。 */
const detailMeta = computed(() => {
  const chips: {
    label: string
    value?: string
    raw?: unknown
    kind?: EnumTagKind
  }[] = []
  const pushText = (label: string, key: string) => {
    const raw = form[key]
    if (raw == null || raw === '') return
    chips.push({ label, value: String(raw) })
  }
  const pushTag = (label: string, key: string, kind: EnumTagKind) => {
    const raw = form[key]
    if (raw == null || raw === '') return
    chips.push({ label, raw, kind })
  }
  pushTag('状态', 'status', 'runStatus')
  pushTag('执行', 'execStatus', 'execStatus')
  pushText('代码', 'symbol')
  pushTag('动作', 'action', 'action')
  if (form.valueTier != null && form.valueTier !== '') {
    chips.push({ label: '价值等级', value: formatValueTier(form.valueTier) })
  }
  pushText('ID', 'id')
  return chips.slice(0, 4)
})

function isFieldReadonly(key: string) {
  if (creating.value) return false
  if (!canSave.value) return true
  // 研究卡片主键不可改
  if (current.value?.key === 'research-notes' && key === 'symbol') return true
  return false
}

/** 长文本按字段给足行高，便于充实阅读。 */
function textareaRows(key: string) {
  if (['contextJson', 'rawResponse', 'frameworkTrace', 'sourcesJson'].includes(key)) {
    return 12
  }
  if (['summaryText', 'story', 'risks', 'quoteSummary', 'rationale'].includes(key)) {
    return 8
  }
  return 5
}

function boolFormatter(_row: unknown, _col: unknown, cell: unknown) {
  return cell ? '是' : '否'
}

function isTagColumn(col: HistoryColumn) {
  return (
    col.format === 'action' ||
    col.format === 'execStatus' ||
    col.format === 'runStatus' ||
    col.format === 'signalTags'
  )
}

/** 按列 format 选择 formatter；Tag 列走自定义插槽。 */
function columnFormatter(col: HistoryColumn) {
  if (col.format === 'bool') return boolFormatter
  if (col.format === 'datetime') {
    return (_row: unknown, _c: unknown, cell: unknown) => formatDateTime(cell)
  }
  if (col.format === 'valueTier') {
    return (_row: unknown, _c: unknown, cell: unknown) => formatValueTier(cell)
  }
  return undefined
}

function onSelect(key: string) {
  activeKey.value = key
  loadList()
}

function stopRefreshPoll() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

/** 对这一行补搜集员资料，短轮询结束后刷新列表。 */
async function onRefreshNote(row: Record<string, unknown>) {
  const symbol = String(row.symbol ?? '')
  if (!symbol || refreshingSymbol.value) return
  refreshingSymbol.value = symbol
  try {
    const sub = await paperTradeApi.adminRefreshResearchNote(symbol)
    stopRefreshPoll()
    let tries = 0
    refreshTimer = setInterval(async () => {
      tries += 1
      try {
        const run = await paperTradeApi.researchRunDetail(sub.runId)
        if (run.status === 'SUCCEEDED' || run.status === 'FAILED') {
          stopRefreshPoll()
          refreshingSymbol.value = ''
          if (run.status === 'FAILED') {
            ElMessage.error(run.errorMessage || '更新失败')
          } else {
            ElMessage.success(`已更新 ${symbol}`)
            if (current.value?.key === 'research-notes') await loadList()
          }
        }
      } catch {
        /* 单次轮询失败忽略 */
      }
      if (tries > 45 && refreshingSymbol.value) {
        stopRefreshPoll()
        refreshingSymbol.value = ''
        ElMessage.warning('更新仍在运行，可到搜集轮次查看')
      }
    }, 2000)
  } catch {
    refreshingSymbol.value = ''
  }
}

const exportTables = ref<string[]>([])

const exportTableOptions = computed(() => {
  const cur = current.value?.tableName
  return exportTables.value.filter((t) => t && t !== cur)
})

/** 加载可导出表白名单（含 warehouse / evaluator）。 */
async function loadExportTables() {
  try {
    const map = await paperTradeApi.adminExportTables()
    exportTables.value = Object.keys(map ?? {}).sort()
  } catch {
    exportTables.value = [
      'paper_warehouse_analyst_run',
      'paper_warehouse_cleanup_run',
      'paper_trade_evaluator_run',
    ]
  }
}

/** 下载指定表全量 INSERT.sql。 */
async function onExportTable(table: string) {
  if (!table) return
  exporting.value = true
  try {
    const file = await paperTradeApi.adminExportSql(table)
    const blob = new Blob([file.content], { type: 'application/sql;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.filename
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${file.filename}`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    exporting.value = false
  }
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
    } else if (field.type === 'number' || field.type === 'valueTier') {
      form[field.key] = v == null || v === '' ? undefined : Number(v)
    } else {
      form[field.key] = v == null ? '' : String(v)
    }
  }
}

function openCreate() {
  creating.value = true
  editingId.value = ''
  // 人工新建研究卡片：留下 + 自选默认开启；价值等级默认继续观察
  resetForm(
    current.value?.key === 'research-notes'
      ? { kept: true, watchlist: true, valueTier: 2 }
      : { kept: true },
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
  } catch (e: unknown) {
    // HTTP 错误由拦截器提示；本地 throw 需补 toast
    if (e && typeof e === 'object' && 'isAxiosError' in e) return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(() => {
  loadList()
  loadExportTables()
})

onUnmounted(() => {
  stopRefreshPoll()
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
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    margin-bottom: 4px;
    font-size: 12px;
    font-weight: 600;
    color: #475569;
    line-height: 1.2;
  }
}

.detail-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: min(68vh, 640px);
  overflow: auto;
  padding-right: 4px;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 6px 10px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #dcfce7;
  font-size: 12px;
  color: #14532d;
  word-break: break-all;

  em {
    font-style: normal;
    font-size: 11px;
    font-weight: 600;
    color: #64748b;
  }
}

.signal-tags-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
}

.field-blocks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.footer-hint {
  font-size: 12px;
  color: #94a3b8;
}

.footer-actions {
  display: flex;
  gap: 8px;
}
</style>

<style lang="less">
/* append-to-body 弹窗需非 scoped 才能命中 */
.hist-detail-dialog.el-dialog {
  border-radius: 14px;
  overflow: hidden;

  .el-dialog__header {
    margin: 0;
    padding: 16px 20px 12px;
    border-bottom: 1px solid #e2e8f0;
  }

  .el-dialog__title {
    font-size: 16px;
    font-weight: 700;
    color: #14532d;
  }

  .el-dialog__body {
    padding: 16px 20px;
  }

  .el-dialog__footer {
    padding: 12px 20px 16px;
    border-top: 1px solid #e2e8f0;
  }

  .el-textarea__inner {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
    color: #334155;
  }
}
</style>
