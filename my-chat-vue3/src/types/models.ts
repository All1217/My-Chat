export interface Column {
  prop: string;
  label: string;
  width?: string | number;
  minWidth?: string | number;
  align?: "left" | "center" | "right";
  sortable?: boolean | "custom";
  hidden?: boolean;
  type?: "text" | "image" | "html";
  slot?: boolean;
  render?: any;
  formatter?: (row: any, column: any, cellValue: any, index: number) => any;
}

// * 请求响应参数(不含data)
export interface Result {
  code: number;
  message: string;
  success?: boolean;
}

// * 请求响应参数(包含data)
export interface ResultData<T = any> extends Result {
  data: T;
}
// * 分页响应参数
export interface PageRes<T> {
  records: T[];
  // 当前页
  current?: number;
  // 每页显示条数
  size?: number;
  total: number;
}

export interface TestWarehouseGood {
  id: string;
  itemName: string;
  itemCode?: string;
  price: number;
  description: string;
  status: number;
  delFlag: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}
