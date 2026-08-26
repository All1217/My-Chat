<template>
  <div v-if="visible.length" class="citation-tags">
    <el-tag
      v-for="c in visible"
      :key="tagKey(c)"
      size="small"
      :type="c.kind === 'catalog' ? 'info' : 'success'"
      effect="plain"
    >
      引用 {{ c.filename || '未命名' }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { KbCitation } from '@/types/AiModule/streamEvents'
import { dedupeKbCitations } from '@/types/AiModule/streamEvents'

const props = defineProps<{
  citations?: KbCitation[] | null
}>()

const visible = computed(() => dedupeKbCitations(props.citations))

function tagKey(c: KbCitation) {
  return (c.documentId?.trim()) || (c.filename?.trim()) || 'anon'
}
</script>

<style scoped lang="less">
.citation-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
</style>
