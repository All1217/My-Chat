<script setup lang="ts">
import { computed } from 'vue'
import {
  formatEnumLabel,
  tagTypeFor,
  type EnumTagKind,
} from '@/apps/paper-trade/utils/format'

const props = defineProps<{
  kind: EnumTagKind
  value: unknown
}>()

const label = computed(() => formatEnumLabel(props.kind, props.value))
const type = computed(() => tagTypeFor(props.kind, props.value))
const empty = computed(() => label.value === '—')
</script>

<template>
  <span v-if="empty" class="enum-empty">—</span>
  <el-tag v-else size="small" :type="type" effect="light">{{ label }}</el-tag>
</template>

<style scoped>
.enum-empty {
  color: #94a3b8;
}
</style>
