<template>
  <div class="model-page">
    <div class="page-header">
      <div>
        <h3>大模型管理</h3>
        <p class="hint">维护 OpenAI 兼容对话模型，三个能力层（编排 / 工具 / RAG）共用当前默认。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增模型</el-button>
    </div>

    <el-alert
      v-if="defaultModel"
      type="success"
      :closable="false"
      show-icon
      class="default-alert"
      :title="`当前默认：${defaultModel.name}（${defaultModel.modelId}）`"
    />
    <el-alert
      v-else
      type="warning"
      :closable="false"
      show-icon
      class="default-alert"
      title="尚未设置默认模型，对话将回退 YAML 配置。"
    />

    <el-table :data="models" v-loading="loading" stripe empty-text="暂无模型，点击右上角新增">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="供应商" width="120">
        <template #default="{ row }">
          {{ providerLabel(row.provider) }}
        </template>
      </el-table-column>
      <el-table-column prop="modelId" label="模型 ID" min-width="180" />
      <el-table-column prop="maxTokens" label="max tokens" width="110" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="warning" size="small">默认</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :disabled="row.isDefault || !row.enabled"
            :loading="actingId === row.id && actingKind === 'default'"
            @click="handleSetDefault(row)"
          >
            设为默认
          </el-button>
          <el-button
            link
            type="primary"
            size="small"
            :loading="actingId === row.id && actingKind === 'test'"
            @click="handleTestSaved(row)"
          >
            测通
          </el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            type="danger"
            size="small"
            :disabled="row.isDefault"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑模型' : '新增模型'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如 DeepSeek Flash" />
        </el-form-item>
        <el-form-item label="供应商" required>
          <el-select v-model="form.provider" placeholder="选择供应商" style="width: 100%" @change="onProviderChange">
            <el-option
              v-for="p in providers"
              :key="p.key"
              :label="p.label"
              :value="p.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item :label="editingId ? 'API Key' : 'API Key'" :required="!editingId">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? `留空则保持 ${editingMasked}` : 'sk-…'"
          />
        </el-form-item>
        <el-form-item label="模型 ID" required>
          <el-input v-model="form.modelId" :placeholder="modelPlaceholder" />
          <p v-if="currentHints.length" class="field-hint">常用：{{ currentHints.join('、') }}</p>
        </el-form-item>
        <el-form-item label="max tokens">
          <el-input-number v-model="form.maxTokens" :min="256" :max="128000" :step="256" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :disabled="editingDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="testingForm" @click="handleTestForm">测通</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { modelApi } from '@/api/model'
import type { LlmModel, LlmProviderPreset } from '@/types/settings/model'

const loading = ref(false)
const saving = ref(false)
const testingForm = ref(false)
const actingId = ref('')
const actingKind = ref<'default' | 'test' | ''>('')
const models = ref<LlmModel[]>([])
const providers = ref<LlmProviderPreset[]>([])
const dialogVisible = ref(false)
const editingId = ref('')
const editingMasked = ref('')
const editingDefault = ref(false)

const form = reactive({
  name: '',
  provider: 'deepseek',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  maxTokens: 8192,
  enabled: true,
})

const defaultModel = computed(() => models.value.find((m) => m.isDefault))

const currentPreset = computed(() => providers.value.find((p) => p.key === form.provider))

const currentHints = computed(() => currentPreset.value?.modelHints ?? [])

const modelPlaceholder = computed(() => currentHints.value[0] || '模型名称')

function providerLabel(key: string) {
  return providers.value.find((p) => p.key === key)?.label ?? key
}

async function fetchAll() {
  loading.value = true
  try {
    const [list, preset] = await Promise.all([modelApi.list(), modelApi.providers()])
    models.value = list ?? []
    providers.value = preset ?? []
  } catch {
    /* 已 toast */
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = ''
  editingMasked.value = ''
  editingDefault.value = false
  form.name = ''
  form.provider = 'deepseek'
  form.baseUrl = currentPreset.value?.baseUrl || 'https://api.deepseek.com'
  form.apiKey = ''
  form.modelId = ''
  form.maxTokens = 8192
  form.enabled = true
}

function fillBaseUrlFromPreset() {
  const preset = providers.value.find((p) => p.key === form.provider)
  if (preset) {
    form.baseUrl = preset.baseUrl
  }
}

function onProviderChange() {
  fillBaseUrlFromPreset()
}

function openCreate() {
  resetForm()
  fillBaseUrlFromPreset()
  dialogVisible.value = true
}

function openEdit(row: LlmModel) {
  editingId.value = row.id
  editingMasked.value = row.apiKeyMasked
  editingDefault.value = row.isDefault
  form.name = row.name
  form.provider = row.provider
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.modelId = row.modelId
  form.maxTokens = row.maxTokens
  form.enabled = row.enabled
  dialogVisible.value = true
}

function validateForm(requireKey: boolean) {
  if (!form.name.trim()) {
    ElMessage.warning('请填写名称')
    return false
  }
  if (!form.baseUrl.trim()) {
    ElMessage.warning('请填写 Base URL')
    return false
  }
  if (requireKey && !form.apiKey.trim()) {
    ElMessage.warning('请填写 API Key')
    return false
  }
  if (!form.modelId.trim()) {
    ElMessage.warning('请填写模型 ID')
    return false
  }
  return true
}

async function handleSave() {
  const requireKey = !editingId.value
  if (!validateForm(requireKey)) return
  saving.value = true
  try {
    if (editingId.value) {
      await modelApi.update({
        id: editingId.value,
        name: form.name.trim(),
        provider: form.provider,
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey.trim() || undefined,
        modelId: form.modelId.trim(),
        maxTokens: form.maxTokens,
        enabled: form.enabled,
      })
      ElMessage.success('已保存')
    } else {
      await modelApi.create({
        name: form.name.trim(),
        provider: form.provider,
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey.trim(),
        modelId: form.modelId.trim(),
        maxTokens: form.maxTokens,
        enabled: form.enabled,
      })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await fetchAll()
  } catch {
    /* 已 toast */
  } finally {
    saving.value = false
  }
}

async function handleSetDefault(row: LlmModel) {
  actingId.value = row.id
  actingKind.value = 'default'
  try {
    await modelApi.setDefault(row.id)
    ElMessage.success(`已切换默认模型为 ${row.name}`)
    await fetchAll()
  } catch {
    /* 已 toast */
  } finally {
    actingId.value = ''
    actingKind.value = ''
  }
}

async function handleDelete(row: LlmModel) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '删除模型', { type: 'warning' })
  } catch {
    return
  }
  try {
    await modelApi.remove(row.id)
    ElMessage.success('已删除')
    await fetchAll()
  } catch {
    /* 已 toast */
  }
}

async function handleTestSaved(row: LlmModel) {
  actingId.value = row.id
  actingKind.value = 'test'
  try {
    const result = await modelApi.test({ id: row.id })
    if (result.ok) {
      ElMessage.success(result.reply ? `测通成功：${result.reply}` : '测通成功')
    } else {
      ElMessage.error(result.message || '测通失败')
    }
  } catch {
    /* 已 toast */
  } finally {
    actingId.value = ''
    actingKind.value = ''
  }
}

async function handleTestForm() {
  const requireKey = !editingId.value
  if (!validateForm(requireKey)) return
  testingForm.value = true
  try {
    const result = await modelApi.test({
      id: editingId.value || undefined,
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim() || undefined,
      modelId: form.modelId.trim(),
      maxTokens: form.maxTokens,
    })
    if (result.ok) {
      ElMessage.success(result.reply ? `测通成功：${result.reply}` : '测通成功')
    } else {
      ElMessage.error(result.message || '测通失败')
    }
  } catch {
    /* 已 toast */
  } finally {
    testingForm.value = false
  }
}

onMounted(fetchAll)
</script>

<style scoped>
.model-page {
  padding: 24px 28px 40px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

h3 {
  font-size: 22px;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.hint,
.field-hint {
  color: #909399;
  font-size: 13px;
  margin: 0;
  line-height: 1.5;
}

.field-hint {
  margin-top: 6px;
}

.default-alert {
  margin-bottom: 16px;
}

.muted {
  color: #c0c4cc;
}
</style>
