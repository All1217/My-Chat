<template>
  <div class="common-table">
    <div v-if="showToolbar" class="table-toolbar">
      <slot name="toolbar">
        <div class="toolbar-left">
          <el-button v-if="showBatchActions" type="danger" :disabled="!hasSelection" @click="handleBatchDelete">
            批量删除
          </el-button>
          <el-button v-if="showBatchActions" type="primary" @click="handleAdd">
            新增
          </el-button>
          <slot name="batch-actions" :selection="selection" />
        </div>
        <div class="toolbar-right">
          <slot name="toolbar-right" />
        </div>
      </slot>
    </div>

    <el-table ref="tableRef" width="100%" v-bind="$attrs" :data="tableData" :border="border" :stripe="stripe"
      :size="size" :height="height" :max-height="maxHeight" :row-key="rowKey" @selection-change="handleSelectionChange">
      <el-table-column v-if="showSelection" type="selection" width="50" fixed align="center" />
      <el-table-column v-if="showChild" type="expand" width="50" fixed align="center">
        <template #default="scope">
          <slot name="expand-child" v-bind="scope">
          </slot>
        </template>
      </el-table-column>

      <template v-for="column in columns" :key="column.prop">
        <el-table-column v-if="!column.hidden" :prop="column.prop" :label="column.label" :width="column.width"
          :min-width="column.minWidth" :align="column.align || 'left'" :sortable="column.sortable"
          :formatter="column.formatter">
          <template #default="scope">
            <slot v-if="column.slot" :name="`column-${column.prop}`" :row="scope.row" :index="scope.$index" />
            <template v-else-if="column.render">
              <component :is="column.render" :row="scope.row" :prop="column.prop" :index="scope.$index" />
            </template>
            <template v-else-if="column.type === 'image'">
              <el-image v-if="scope.row[column.prop]" :src="scope.row[column.prop]"
                :preview-src-list="[scope.row[column.prop]]" fit="cover" style="width: 60px; height: 60px" />
              <span v-else>-</span>
            </template>
            <template v-else-if="column.type === 'html'">
              <div v-html="scope.row[column.prop]" />
            </template>
            <template v-else>
              {{ scope.row[column.prop] || "-" }}
            </template>
          </template>
        </el-table-column>
      </template>

      <el-table-column v-if="showActions" label="操作" align="center" fixed="right">
        <template #default="scope">
          <slot name="actions" :row="scope.row" :index="scope.$index">
            <el-button v-if="showDelete" type="danger" size="small" @click="handleDelete(scope.row, scope.$index)">
              删除
            </el-button>
            <slot name="custom-actions" :row="scope.row" :index="scope.$index" />
          </slot>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页条 -->
    <div v-if="showPagination" class="table-pagination">
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="pageSizes"
        :layout="paginationLayout" :total="total" @size-change="handleSizeChange"
        @current-change="handleCurrentChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import type { TableInstance } from "element-plus";
import { Column } from "@/types/models";

interface Props {
  columns: Column[];
  data: any[];
  border?: boolean;
  stripe?: boolean;
  size?: "large" | "default" | "small";
  height?: string | number;
  maxHeight?: string | number;
  rowKey?: string;
  showChild?: boolean;
  showSelection?: boolean;
  showActions?: boolean;
  showDelete?: boolean;
  showToolbar?: boolean;
  showBatchActions?: boolean;
  showPagination?: boolean;
  actionsWidth?: string | number;
  total?: number;
  currentPage?: number;
  pageSize?: number;
  pageSizes?: number[];
  paginationLayout?: string;
}

const props = withDefaults(defineProps<Props>(), {
  columns: () => [],
  data: () => [],
  border: true,
  stripe: true,
  size: "default",
  height: undefined,
  maxHeight: undefined,
  rowKey: "id",
  showSelection: true,
  showActions: true,
  showDelete: true,
  showToolbar: true,
  showBatchActions: true,
  showPagination: false,
  actionsWidth: 120,
  total: 0,
  currentPage: 1,
  pageSize: 10,
  pageSizes: () => [10, 20, 50, 100],
  paginationLayout: "total, sizes, prev, pager, next, jumper",
});

const emit = defineEmits<{
  "selection-change": [selection: any[]];
  delete: [row: any, index: number];
  "batch-delete": [selection: any[]];
  "size-change": [size: number];
  "current-change": [page: number];
  "add": [];
}>();

const tableRef = ref<TableInstance>();
const selection = ref<any[]>([]);
const currentPage = ref(props.currentPage);
const pageSize = ref(props.pageSize);

const tableData = computed(() => props.data);
const hasSelection = computed(() => selection.value.length > 0);

watch(
  () => props.currentPage,
  (val) => {
    currentPage.value = val;
  },
);

watch(
  () => props.pageSize,
  (val) => {
    pageSize.value = val;
  },
);

const handleSelectionChange = (val: any[]) => {
  selection.value = val;
  emit("selection-change", val);
};

const handleDelete = (row: any, index: number) => {
  emit("delete", row, index);
};

const handleBatchDelete = () => {
  if (selection.value.length > 0) {
    emit("batch-delete", selection.value);
  }
};

const handleAdd = () => {
  emit("add");
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  emit("size-change", size);
};

const handleCurrentChange = (page: number) => {
  currentPage.value = page;
  emit("current-change", page);
};

const clearSelection = () => {
  tableRef.value?.clearSelection();
};

defineExpose({
  tableRef,
  clearSelection,
  getSelection: () => selection.value,
});
</script>

<style scoped lang="less">
.common-table {
  width: 100%;
  height: 100%;

  .table-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding: 12px 16px;
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);

    .toolbar-left {
      display: flex;
      gap: 8px;
    }
  }

  .table-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
    padding: 12px 16px;
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  }

  :deep(.el-table) {
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);

    .el-table__header-wrapper {
      th {
        background-color: #f5f7fa;
        font-weight: 600;
      }
    }

    .el-table__body-wrapper {
      .el-table__row {
        &:hover {
          background-color: #f5f7fa;
        }
      }
    }
  }
}
</style>
