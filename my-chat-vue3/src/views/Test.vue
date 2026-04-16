<template>
  <div class="test">
    <h2>CommonTable 组件使用示例</h2>
    <div class="table-container">
      <CommonTable
        :columns="columns"
        :data="tableData"
        :total="total"
        :current-page="currentPage"
        :page-size="pageSize"
        :show-pagination="true"
        @selection-change="handleSelectionChange"
        @delete="handleDelete"
        @batch-delete="handleBatchDelete"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      >
        <template #column-status="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
            {{ row.status === "active" ? "启用" : "禁用" }}
          </el-tag>
        </template>

        <template #actions="{ row, index }">
          <div
            class="action-buttons"
            style="
              display: flex;
              gap: 8px;
              flex-direction: column;
              justify-content: center;
              align-items: center;
            "
          >
            <el-button
              type="primary"
              size="small"
              @click="handleEdit(row, index)"
              style="margin: 0"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row, index)"
              style="margin: 0"
            >
              删除
            </el-button>
          </div>
        </template>
      </CommonTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import CommonTable from "@/components/CommonTable.vue";
import { Column } from "@/types/models.ts";
import http from "@/util/http";
import { TestWarehouseGood } from "@/types/models.ts";

const columns: Column[] = [
  { prop: "id", label: "ID", width: 80, align: "center" },
  { prop: "name", label: "姓名", minWidth: 120 },

  { prop: "age", label: "年龄", width: 100, align: "center" },
  { prop: "gender", label: "性别", width: 100, align: "center" },
  { prop: "email", label: "邮箱", minWidth: 180 },
  { prop: "avatar", label: "头像", width: 100, type: "image" },
  { prop: "status", label: "状态", width: 100, slot: true },
  { prop: "description", label: "描述", minWidth: 200 },
  { prop: "createTime", label: "创建时间", width: 160 },
];

const tableData = ref([
  {
    id: 1,
    name: "张三",
    age: 25,
    gender: "男",
    email: "zhangsan@example.com",
    avatar: "https://via.placeholder.com/60",
    status: "active",
    description: "这是一段描述文字",
    createTime: "2024-01-15 10:30:00",
  },
  {
    id: 2,
    name: "李四",
    age: 30,
    gender: "女",
    email: "lisi@example.com",
    avatar: "https://via.placeholder.com/60",
    status: "inactive",
    description: '<span style="color: #f56c6c">红色HTML文本</span>',
    createTime: "2024-01-16 14:20:00",
  },
  {
    id: 3,
    name: "王五",
    age: 28,
    gender: "男",
    email: "wangwu@example.com",
    avatar: "",
    status: "active",
    description: "另一段描述",
    createTime: "2024-01-17 09:15:00",
  },
  {
    id: 4,
    name: "赵六",
    age: 35,
    gender: "女",
    email: "zhaoliu@example.com",
    avatar: "https://via.placeholder.com/60",
    status: "active",
    description: "测试数据",
    createTime: "2024-01-18 16:45:00",
  },
  {
    id: 5,
    name: "孙七",
    age: 22,
    gender: "男",
    email: "sunqi@example.com",
    avatar: "https://via.placeholder.com/60",
    status: "inactive",
    description: "青年用户",
    createTime: "2024-01-19 11:10:00",
  },
]);

const selectedRows = ref<any[]>([]);
const total = ref(50);
const currentPage = ref(1);
const pageSize = ref(10);

const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection;
  console.log("选择变化:", selection);
};

const handleDelete = (row: any, index: number) => {
  console.log("删除行:", row, index);
  tableData.value.splice(index, 1);
};

const handleBatchDelete = (selection: any[]) => {
  console.log("批量删除:", selection);
  const ids = selection.map((item) => item.id);
  tableData.value = tableData.value.filter((item) => !ids.includes(item.id));
  selectedRows.value = [];
};

const handleEdit = (row: any, index: number) => {
  getItemById("7450367564569382934");
};
async function getItemById(id: string) {
  try {
    const res = await http.get<TestWarehouseGood>(
      "/inventory/testWarehouseGoods/queryById",
      { id: id },
    );
    console.log("请求成功:", res);
  } catch (error) {
    console.error("请求失败:", error);
  }
}

const handleSizeChange = (size: number) => {
  console.log("每页大小变化:", size);
  pageSize.value = size;
};

const handleCurrentChange = (page: number) => {
  console.log("当前页变化:", page);
  currentPage.value = page;
};
</script>

<style lang="less" scoped>
.test {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  .table-container {
    width: 100%;
    max-width: 98vw;
  }

  h2 {
    margin-bottom: 20px;
    color: #303133;
  }

  .selection-info {
    margin-top: 30px;
    padding: 20px;
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);

    h3 {
      margin-bottom: 10px;
      color: #409eff;
    }

    pre {
      background-color: #f5f7fa;
      padding: 15px;
      border-radius: 4px;
      overflow: auto;
      font-size: 14px;
      line-height: 1.5;
    }
  }
}
</style>
