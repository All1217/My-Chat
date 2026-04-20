import axios from "axios";
import type {
  AxiosInstance,
  AxiosError,
  AxiosRequestConfig,
//   AxiosResponse,
} from "axios";
import { ElMessage } from "element-plus";
import { ResultEnum } from '@/types/enums.ts';
import { ResultData } from '@/types/models.ts';

export const crmService: AxiosInstance = axios.create({
  baseURL: "/api",
  timeout: ResultEnum.TIMEOUT as number,
});
export const ragService: AxiosInstance = axios.create({
  baseURL: "/rag",
  timeout: ResultEnum.TIMEOUT as number,
});
/**
 * @description: 导出封装的请求方法
 * @returns {*}
 */
export const crmHttp = {
  get<T>(
    url: string,
    params?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return crmService.get(url, { params, ...config });
  },

  post<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return crmService.post(url, data, config);
  },

  put<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return crmService.put(url, data, config);
  },

  delete<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return crmService.delete(url, { data, ...config });
  },
};
export const ragHttp = {
  get<T>(
    url: string,
    params?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return ragService.get(url, { params, ...config });
  },

  post<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return ragService.post(url, data, config);
  },

  put<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return ragService.put(url, data, config);
  },

  delete<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return ragService.delete(url, { data, ...config });
  },
};

/**
 * @description: 请求拦截器
 * @returns {*}
 */
crmService.interceptors.request.use(
  (config) => {
    config.headers["X-Access-Token"] = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6IjgwMTAwMzk2IiwiY2xpZW50VHlwZSI6IlBDIiwiZXhwIjoxNzc2OTc2MTU0fQ.CjRzmrPp9hcW3fd5if6kD6htN24gyHiemLaw0r-gx4Y";
    return config;
  },
  (error: AxiosError) => {
    ElMessage.error(error.message);
    return Promise.reject(error);
  },
);
/**
 * @description: 响应拦截器
 * @returns {*}
 */
// service.interceptors.response.use(
//   async (response: AxiosResponse) => {
//     const { data } = response
//     return data
//   },
//   async (error: AxiosError) => {
//     return Promise.reject(error)
//   },
// )
