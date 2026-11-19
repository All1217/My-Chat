<script setup lang="ts">
import { computed } from 'vue'
import { parseSignalTags } from '@/apps/paper-trade/utils/format'

const props = defineProps<{
  value: unknown
}>()

const tags = computed(() => parseSignalTags(props.value))
</script>

<template>
  <span v-if="!tags.length" class="tags-empty">—</span>
  <div v-else class="signal-tags">
    <el-tag
      v-for="tag in tags"
      :key="tag"
      size="small"
      type="info"
      effect="plain"
    >
      {{ tag }}
    </el-tag>
  </div>
</template>

<style scoped>
.signal-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;
  vertical-align: middle;
}

.tags-empty {
  color: #94a3b8;
}
</style>
