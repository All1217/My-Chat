import axios from "axios";
import type { AxiosInstance, AxiosError, AxiosRequestConfig, AxiosResponse } from "axios";
import { ElMessage } from "element-plus";
import { ResultEnum } from '@/types/enums.ts';
import type { ResultData } from '@/types/models.ts';

// ========== 工厂：创建一个 service + 对应的请求方法 ==========
interface HttpMethods {
  get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<ResultData<T>>;
  post<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<ResultData<T>>;
  put<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<ResultData<T>>;
  delete<T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<ResultData<T>>;
}
function createHttp(baseURL: string): { service: AxiosInstance; methods: HttpMethods } {
  const service: AxiosInstance = axios.create({
    baseURL,
    timeout: ResultEnum.TIMEOUT as number,
  });

  // ----- 响应拦截器：直接返回 response.data -----
  service.interceptors.response.use(
    (response: AxiosResponse) => response.data,   // 脱去 Axios 外层
    (error: AxiosError) => {
      ElMessage.error(error.message);
      return Promise.reject(error);
    },
  );

  // ----- 请求方法 -----
  const methods: HttpMethods = {
    get(url, params?, config?) {
      return service.get(url, { params, ...config });
    },
    post(url, data?, config?) {
      return service.post(url, data, config);
    },
    put(url, data?, config?) {
      return service.put(url, data, config);
    },
    delete(url, data?, config?) {
      return service.delete(url, { data, ...config });
    },
  };

  return { service, methods };
}

// ========== 创建各后端服务 ==========

const { service: crmService, methods: crmHttp } = createHttp('/api');
const { service: ragService, methods: ragHttp } = createHttp('/rag');

// ========== 统一请求拦截器 ==========
([crmService, ragService] as AxiosInstance[]).forEach(svc => {
  svc.interceptors.request.use(
    (config) => {
      config.headers["X-Access-Token"] =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6IjgwMTAwMzk2IiwiY2xpZW50VHlwZSI6IlBDIiwiZXhwIjoxNzc2OTc2MTU0fQ.CjRzmrPp9hcW3fd5if6kD6htN24gyHiemLaw0r-gx4Y";
      return config;
    },
    (error: AxiosError) => {
      ElMessage.error(error.message);
      return Promise.reject(error);
    },
  );
});

export { crmService, ragService, crmHttp, ragHttp };