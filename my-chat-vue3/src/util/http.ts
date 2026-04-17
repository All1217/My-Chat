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

export const service: AxiosInstance = axios.create({
  baseURL: "/api",
  timeout: ResultEnum.TIMEOUT as number,
});
/**
 * @description: 导出封装的请求方法
 * @returns {*}
 */
const http = {
  get<T>(
    url: string,
    params?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return service.get(url, { params, ...config });
  },

  post<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return service.post(url, data, config);
  },

  put<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return service.put(url, data, config);
  },

  delete<T>(
    url: string,
    data?: object,
    config?: AxiosRequestConfig,
  ): Promise<ResultData<T>> {
    return service.delete(url, { data, ...config });
  },
};

/**
 * @description: 请求拦截器
 * @returns {*}
 */
service.interceptors.request.use(
  (config) => {
    config.headers["X-Access-Token"] = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6IjgwMTAwMzk2IiwiY2xpZW50VHlwZSI6IlBDIiwiZXhwIjoxNzc2Njg4OTE5fQ.WPhsu8en_KUZfdQvEK7Tn2Z1KqzcBDOuZzhXdOWmMOs";
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
export default http;
