<template>
  <div class="test">
    <h2>CommonTable 组件使用示例</h2>
    <div class="table-container">
      <CommonTable :columns="columns" :data="tableData" :total="total" :current-page="currentPage" :page-size="pageSize"
        :show-pagination="true" @selection-change="handleSelectionChange" @delete="handleDelete"
        @batch-delete="handleBatchDelete" @size-change="handleSizeChange" @current-change="handleCurrentChange"
        @add="handleAdd" height="60vh" max-height="60vh" :show-child="true">
        <template #expand-child="{ row, index }">
          <div style="padding: 10px 25px 10px 25px;">
            <el-descriptions class="margin-top" :column="3" border>
              <el-descriptions-item>
                <template #label>
                  <div class="cell-item">
                    <el-icon :style="iconStyle">
                      <Money />
                    </el-icon> 订单金额
                  </div>
                </template>
                kooriookami
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div class="cell-item">
                    <el-icon :style="iconStyle">
                      <InfoFilled />
                    </el-icon> 订单ID
                  </div>
                </template>
                18100000000
              </el-descriptions-item>
              <el-descriptions-item>
                <template #label>
                  <div class="cell-item">
                    <el-icon :style="iconStyle">
                      <location />
                    </el-icon> 订单发货地点
                  </div>
                </template> Suzhou
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </template>
        <template #column-status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? "启用" : "禁用" }}
          </el-tag>
        </template>
        <template #actions="{ row, index }">
          <div class="action-buttons"
            style="display: flex; gap: 8px; flex-direction: column; justify-content: center; align-items: center;">
            <el-button type="primary" size="small" @click="handleEdit(row, index)" style="margin: 0">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row, index)" style="margin: 0">
              删除
            </el-button>
          </div>
        </template>
      </CommonTable>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="货物名称" prop="itemName">
          <el-input v-model="formData.itemName" placeholder="请输入货物名称" />
        </el-form-item>
        <el-form-item label="货物编码" prop="itemCode">
          <el-input v-model="formData.itemCode" placeholder="请输入货物编码" disabled />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="formData.price" :precision="2" :step="0.01" :min="0" placeholder="请输入价格" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmitEdit" :loading="submitting">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from "vue";
import CommonTable from "@/components/CommonTable.vue";
import { Column } from "@/types/models";
import http from "@/util/http";
import { TestWarehouseGood } from "@/types/models";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";

const iconStyle = computed(() => {
  const marginMap = {
    large: '8px',
    default: '6px',
    small: '4px',
  }
  return {
    marginRight: marginMap['default'] || marginMap.default,
  }
})
const columns: Column[] = [
  { prop: "id", label: "ID", width: 190, align: "center" },
  { prop: "itemName", label: "货物名称", width: 100 },
  { prop: "itemCode", label: "货物编码", width: 190, align: "center" },
  { prop: "price", label: "价格", width: 120, align: "center" },
  { prop: "description", label: "描述", minWidth: 180 },
  { prop: "status", label: "状态", width: 80, slot: true, align: "center" },
  { prop: "createBy", label: "创建人ID", width: 190 },
  { prop: "createTime", label: "创建时间", width: 180 },
  { prop: "updateBy", label: "更新人ID", width: 190 },
  { prop: "updateTime", label: "更新时间", width: 180 }
];

const tableData = ref<TestWarehouseGood[]>([]);

const selectedRows = ref<any[]>([]);
const total = ref(50);
const currentPage = ref(1);
const pageSize = ref(10);

const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const submitting = ref(false);
const isEditMode = ref(false);
const formData = reactive<TestWarehouseGood>({
  id: "",
  itemName: "",
  itemCode: "",
  price: 0.00,
  description: "",
  status: 1,
  delFlag: 0,
  createBy: "",
  createTime: "",
  updateBy: "",
  updateTime: ""
});

const dialogTitle = computed(() => isEditMode.value ? "编辑货物信息" : "新增货物");

const formRules: FormRules = {
  itemName: [
    { required: true, message: "请输入货物名称", trigger: "blur" }
  ],
  itemCode: [
    { required: false, message: "请输入货物编码", trigger: "blur" }
  ],
  price: [
    { required: true, message: "请输入价格", trigger: "blur" }
  ]
};

const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection;
};

const handleDelete = async (row: any, index: number) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除货物"${row.itemName}"吗？`,
      "删除确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    tableData.value.splice(index, 1);
    await deleteOne(row.id.toString());
  } catch {
    ElMessage.info("已取消删除");
  }
};

const handleBatchDelete = async (selection: any[]) => {
  if (selection.length === 0) {
    ElMessage.warning("请选择要删除的数据");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selection.length} 条数据吗？`,
      "批量删除确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    const ids = selection.map((item) => item.id);
    tableData.value = tableData.value.filter((item) => !ids.includes(item.id));
    selectedRows.value = [];
    await deleteBatch(ids.join(","));
  } catch {
    ElMessage.info("已取消批量删除");
  }
};

async function deleteOne(id: string) {
  try {
    // 必须要换成这种写法，不能用大括号传参，原因有待考察
    const res = await http.delete(`/inventory/testWarehouseGoods/delete?id=${id}`);
    if (res.data.code === 200) {
      ElMessage.success("删除成功");
      getPageList(currentPage.value, pageSize.value);
    } else {
      console.error("删除失败:", res.data);
      ElMessage.error("删除失败!");
    }
  } catch (error) {
    console.log('删除失败！', error);
    ElMessage.error("删除失败!");
  }
}

async function deleteBatch(ids: string) {
  try {
    const res = await http.delete(`/inventory/testWarehouseGoods/deleteBatch?ids=${ids}`);
    if (res.data.code === 200) {
      ElMessage.success("删除成功");
      getPageList(currentPage.value, pageSize.value);
    } else {
      console.error("删除失败:", res.data);
      ElMessage.error("删除失败!");
    }
  } catch (error) {
    console.log('删除失败！', error);
    ElMessage.error("删除失败!");
  }
}

const handleEdit = async (row: any, index: number) => {
  isEditMode.value = true;
  try {
    const res = await http.get<TestWarehouseGood>(
      "/inventory/testWarehouseGoods/queryById",
      { id: row.id },
    );
    if (res.data.code === 200) {
      Object.assign(formData, res.data.result);
      dialogVisible.value = true;
    } else {
      ElMessage.error("获取数据失败");
    }
  } catch (error) {
    console.error("获取数据失败:", error);
    ElMessage.error("获取数据失败");
  }
};

const handleAdd = () => {
  isEditMode.value = false;
  Object.assign(formData, {
    id: "",
    itemName: "",
    itemCode: "",
    price: 0.00,
    description: "",
    status: 1,
    delFlag: 0,
    createBy: "",
    createTime: "",
    updateBy: "",
    updateTime: ""
  });
  dialogVisible.value = true;
};

const handleSubmitEdit = async () => {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
    submitting.value = true;

    if (isEditMode.value) {
      const res = await http.put("/inventory/testWarehouseGoods/edit", formData);
      if (res.data.code === 200) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        getPageList(currentPage.value, pageSize.value);
      } else {
        ElMessage.error(res.data.message || "更新失败");
      }
    } else {
      formData.itemCode = '0';
      const res = await http.post("/inventory/testWarehouseGoods/add", formData);
      if (res.data.code === 200) {
        ElMessage.success("新增成功");
        dialogVisible.value = false;
        getPageList(currentPage.value, pageSize.value);
      } else {
        console.error("新增失败:", res.data);
        ElMessage.error(res.data.message || "新增失败");
      }
    }
  } catch (error) {
    console.error("操作失败:", error);
    ElMessage.error(isEditMode.value ? "更新失败" : "新增失败");
  } finally {
    submitting.value = false;
  }
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  getPageList(currentPage.value, size);
};

const handleCurrentChange = (page: number) => {
  currentPage.value = page;
  getPageList(page, pageSize.value);
};

async function getPageList(page: number, pageSize: number) {
  try {
    const res = await http.get("/inventory/testWarehouseGoods/list", {
      testWarehouseGood: defaultObj,
      pageNo: page,
      pageSize: pageSize
    });
    if (res.data.code === 200) {
      tableData.value = res.data.result.records;
      total.value = res.data.result.total;
    } else {
      console.error("分页请求失败:", res.data);
      ElMessage.error("分页请求失败!");
    }
  } catch (error) {
    console.error("分页请求失败:", error);
    ElMessage.error("分页请求失败!");
  }
}

const defaultObj = ref<TestWarehouseGood>({
  id: "",
  itemName: "",
  itemCode: "",
  price: 0.00,
  description: "",
  status: 1,
  delFlag: 0,
  createBy: "",
  createTime: "",
  updateBy: "",
  updateTime: ""
});

onMounted(() => {
  getPageList(1, 10);
});
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