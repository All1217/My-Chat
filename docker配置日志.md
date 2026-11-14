用户名：root

密码：KonnoYuuki.1114

公网IP：8.138.177.144

```
# 拉取
docker pull nginx:stable

# 查看镜像
docker images | grep nginx

# 运行
docker run -d --name nginx -p 80:80 nginx:stable

# 查看容器
docker ps | grep nginx

# 查看日志
docker logs -f nginx

# 重载配置（修改 conf 后）
docker exec nginx nginx -s reload

# 停止/删除
docker stop nginx && docker rm nginx
```



# PGSQL

```
docker run -d \
  --name pg \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=Asuna_1224 \
  -e POSTGRES_DB=postgres \
  -p 5432:5432 \
  -v /data/pgdata:/var/lib/postgresql \
  -v /data/temp/my-chat-schema.sql:/docker-entrypoint-initdb.d/schema.sql \
  pgvector/pgvector:pg18
```

## PostgreSQL 快速查阅小手册（无 pgAdmin 版）

> 适用：Docker 部署的 PostgreSQL + pgvector  
> 容器名示例：`pg`，用户 `postgres`，数据库 `postgres`

---

### 1. 进入交互式 psql

```bash
docker exec -it pg psql -U postgres -d postgres
```

退出：`\q`

---

### 2. 元命令速查（psql 内执行）

| 命令        | 作用                         |
| :---------- | :--------------------------- |
| `\l`        | 列出所有数据库               |
| `\c 库名`   | 切换数据库                   |
| `\dt`       | 列出当前库所有表             |
| `\dt+`      | 表列表 + 大小 + 描述         |
| `\d 表名`   | 查看表结构（列、索引、外键） |
| `\d+ 表名`  | 更详细结构 + 存储参数 + 描述 |
| `\di`       | 列出所有索引                 |
| `\di+`      | 索引详情 + 大小              |
| `\dx`       | 已安装扩展                   |
| `\df`       | 函数列表                     |
| `\dv`       | 视图列表                     |
| `\dn`       | 模式（schema）列表           |
| `\du`       | 角色/用户列表                |
| `\conninfo` | 当前连接信息                 |
| `\timing`   | 开启/关闭 SQL 计时           |
| `\x`        | 切换扩展显示（宽表友好）     |
| `\encoding` | 显示字符集                   |

---

### 3. 单条命令直接执行（不进交互）

```bash
# 所有数据库
docker exec -it pg psql -U postgres -d postgres -c '\l'

# 所有表
docker exec -it pg psql -U postgres -d postgres -c '\dt'

# 扩展
docker exec -it pg psql -U postgres -d postgres -c '\dx'

# 表结构
docker exec -it pg psql -U postgres -d postgres -c '\d vector_store'

# 表大小（按总大小降序）
docker exec -it pg psql -U postgres -d postgres -c "SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC;"
```

---

### 4. 常用 SQL 查询

**表是否存在**

```sql
SELECT to_regclass('public.users');
```

**表行数（估算，快）**

```sql
SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC;
```

**精确行数**

```sql
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM vector_store;
```

**数据库大小**

```sql
SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database;
```

**表大小（含索引）**

```sql
SELECT
  relname AS table_name,
  pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
  pg_size_pretty(pg_relation_size(relid)) AS table_size,
  pg_size_pretty(pg_total_relation_size(relid) - pg_relation_size(relid)) AS index_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

**索引定义**

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'vector_store';
```

**扩展版本**

```sql
SELECT extname, extversion FROM pg_extension;
```

**当前连接与活动**

```sql
SELECT pid, usename, datname, client_addr, state, query
FROM pg_stat_activity;
```

**锁**

```sql
SELECT * FROM pg_locks;
```

**验证 pgvector 可用**

```sql
SELECT '[1,2,3]'::vector;
```

---

### 5. 宿主机直连（无需进容器）

宿主机安装 `psql` 后：

```bash
psql -h 127.0.0.1 -p 5432 -U postgres -d postgres
```

密码：`Asuna_1224`

---

### 6. 可视化工具：Adminer（轻量替代 pgAdmin）

```bash
docker run -d \
  --name adminer \
  -p 8080:8080 \
  --link pg:postgres \
  adminer
```

浏览器访问：`http://服务器公网IP:8080`

登录信息：
- 系统：PostgreSQL
- 服务器：`pg`
- 用户名：`postgres`
- 密码：`Asuna_1224`
- 数据库：`postgres`

> 注意：安全组放行 8080，用完可停掉或限制 IP。

---

### 7. 部署验证清单

| 检查项     | 命令                                                       | 期望                            |
| :--------- | :--------------------------------------------------------- | :------------------------------ |
| 容器运行   | `docker ps --filter name=pg`                               | STATUS `Up`                     |
| 数据库就绪 | `docker exec pg pg_isready -U postgres`                    | `accepting connections`         |
| 扩展       | `docker exec -it pg psql -U postgres -d postgres -c '\dx'` | 有 `vector`、`uuid-ossp`        |
| 表         | `docker exec -it pg psql -U postgres -d postgres -c '\dt'` | 有 `users`、`vector_store` 等   |
| 向量类型   | `SELECT '[1,2,3]'::vector;`                                | 返回 `[1,2,3]`                  |
| HNSW 索引  | 查 `pg_indexes`                                            | 有 `idx_vector_store_embedding` |

> 若 `\dt` 无表：手动执行  
> `docker exec -i pg psql -U postgres -d postgres < /data/temp/my-chat-schema.sql`

---

### 8. 常用组合速记

```bash
# 一键盘点：库、表、扩展、vector_store 结构
docker exec -it pg psql -U postgres -d postgres -c '\l' \
  && docker exec -it pg psql -U postgres -d postgres -c '\dt' \
  && docker exec -it pg psql -U postgres -d postgres -c '\dx' \
  && docker exec -it pg psql -U postgres -d postgres -c '\d vector_store'
```

```bash
# 查看所有表大小
docker exec -it pg psql -U postgres -d postgres -c "
SELECT relname,
       pg_size_pretty(pg_total_relation_size(relid)) AS total
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;"
```

```bash
# 查看活动连接
docker exec -it pg psql -U postgres -d postgres -c "
SELECT pid, usename, datname, state, query
FROM pg_stat_activity;"
```

---

**保存这份手册，日常查库、验表、看索引、排查连接，基本够用。**

# Nginx

```
# 拉取镜像
docker pull nginx:stable

# 快速运行容器临时查看版本
docker run --rm nginx:stable nginx -v 2>&1

# 创建挂载目录
mkdir -p /data/nginx/{conf.d,html,logs,certs}

# 运行
docker run -d \
  --name nginx \
  --restart unless-stopped \
  -p 80:80 \
  -p 443:443 \
  -v /data/nginx/conf.d:/etc/nginx/conf.d \
  -v /data/nginx/html:/usr/share/nginx/html \
  -v /data/nginx/logs:/var/log/nginx \
  -v /data/nginx/certs:/etc/nginx/certs \
  nginx:1.27.5
```

**参数说明**

| 参数                       | 作用                                 |
| :------------------------- | :----------------------------------- |
| `--name nginx`             | 容器名                               |
| `--restart unless-stopped` | 开机自启，手动停止后不自动拉起       |
| `-p 80:80`                 | HTTP                                 |
| `-p 443:443`               | HTTPS                                |
| `-v /data/nginx/conf.d`    | 你的反向代理 `*.conf`                |
| `-v /data/nginx/html`      | 前端构建后的静态文件                 |
| `-v /data/nginx/logs`      | 日志                                 |
| `-v /data/nginx/certs`     | SSL 证书                             |
| `nginx:1.27.5`             | 指定版本，避免浮动标签带来的意外升级 |

## 问题日志

```
# Nginx 部署排错日志

**日期**：2026-09-14  
**环境**：阿里云 ECS CentOS + Docker + nginx:1.27.5  
**目标**：通过公网 IP 访问 Nginx 容器

---

## 问题现象
- 阿里云安全组已放行 80/443
- 容器运行正常，端口映射正确
- `curl -I http://localhost` 返回 `Connection reset by peer`
- 修复后变为 `403 Forbidden`

---

## 排查过程

1. **确认容器状态**
   ```bash
   docker ps | grep nginx
```
   结果：容器 `Up`，端口 `0.0.0.0:80->80`、`443->443` 映射正常。

2. **本地测试**
   ```bash
   curl -I http://localhost
   ```
   报错：`curl: (56) Recv failure: Connection reset by peer`

3. **查看 Nginx 日志**
   ```bash
   docker logs nginx
   ```
   关键信息：
   ```text
   /etc/nginx/conf.d/default.conf is not a file or does not exist
   ```
   **原因**：挂载了空的 `/data/nginx/conf.d` 目录，覆盖了镜像自带的默认配置，Nginx 没有加载任何 server 块。

4. **创建默认配置**
   ```bash
   cat > /data/nginx/conf.d/default.conf <<'EOF'
   server {
       listen       80;
       listen  [::]:80;
       server_name  _;
       root   /usr/share/nginx/html;
       index  index.html;
       location / {
           try_files $uri $uri/ /index.html;
       }
   }
   EOF
   ```

5. **重载 Nginx**
   ```bash
   docker exec nginx nginx -s reload
   ```
   再次测试：
   ```bash
   curl -I http://localhost
   ```
   返回：`HTTP/1.1 403 Forbidden`，说明 Nginx 已正常响应，但无可用首页。

---

## 当前状态
- Nginx 服务已正常运行
- 403 原因：`/usr/share/nginx/html/index.html` 不存在，或 SELinux 限制读取挂载目录

---

## 待办事项

1. **确认首页文件存在**
   ```bash
   ls -lha /data/nginx/html/
   echo "<h1>nginx 1.27.5 ok</h1>" > /data/nginx/html/index.html
   chmod 644 /data/nginx/html/index.html
   ```

2. **检查 SELinux**
   ```bash
   getenforce
   ```
   若为 `Enforcing`，二选一：
   - 临时放宽：`sudo setenforce 0`
   - 长期方案：容器挂载加 `:z`，例如 `-v /data/nginx/html:/usr/share/nginx/html:z`

3. **复测**
   ```bash
   curl -I http://localhost
   ```
   期望：`HTTP/1.1 200 OK`

---

## 结论
- 阿里云安全组配置无误
- 核心问题：挂载空 `conf.d` 导致 Nginx 无配置 + 首页文件缺失/SELinux 限制
- 解决路径：补 `default.conf` → 补 `index.html` → 处理 SELinux → 公网访问
```



## 部署实践

> # Nginx 部署前端项目实践与多项目方案
>
> > 环境：阿里云 ECS CentOS + Docker + nginx:1.27.5  
> > 挂载路径：`/data/nginx/{conf.d,html,logs,certs}`  
> > 容器名：`nginx`
>
> ---
>
> ## 1. 单项目部署流程
>
> ### 1.1 准备目录与默认配置
>
> ```bash
> mkdir -p /data/nginx/{conf.d,html,logs,certs}
> ```
>
> 创建 `/data/nginx/conf.d/default.conf`：
>
> ```nginx
> server {
>     listen       80;
>     listen  [::]:80;
>     server_name  _;
> 
>     root   /usr/share/nginx/html;
>     index  index.html;
> 
>     location / {
>         try_files $uri $uri/ /index.html;
>     }
> }
> ```
>
> > 挂载了 `conf.d` 就必须自己提供配置，否则镜像自带的 `default.conf` 会被空目录覆盖，导致 Nginx 无 server 块。
>
> ### 1.2 运行 Nginx 容器
>
> ```bash
> docker run -d \
>   --name nginx \
>   --restart unless-stopped \
>   -p 80:80 -p 443:443 \
>   -v /data/nginx/conf.d:/etc/nginx/conf.d \
>   -v /data/nginx/html:/usr/share/nginx/html \
>   -v /data/nginx/logs:/var/log/nginx \
>   -v /data/nginx/certs:/etc/nginx/certs \
>   nginx:1.27.5
> ```
>
> ### 1.3 本地打包并上传
>
> 本地项目根目录：
>
> ```bash
> npm run build
> ```
>
> 上传 `dist` 内容到服务器：
>
> ```bash
> scp -r dist/* root@公网IP:/data/nginx/html/
> ```
>
> 服务器上设置权限：
>
> ```bash
> chmod -R 755 /data/nginx/html
> ```
>
> ### 1.4 处理 SELinux（若 403）
>
> ```bash
> getenforce
> ```
>
> 若为 `Enforcing`，执行：
>
> ```bash
> chcon -Rt svirt_sandbox_file_t /data/nginx/html
> ```
>
> 或在运行容器时挂载加 `:z`：
>
> ```bash
> -v /data/nginx/html:/usr/share/nginx/html:z
> ```
>
> ### 1.5 重载与验证
>
> ```bash
> docker exec nginx nginx -s reload
> curl -I http://localhost
> ```
>
> 期望返回 `HTTP/1.1 200 OK`。
>
> 浏览器访问 `http://公网IP`。
>
> ---
>
> ## 2. 常见问题排查
>
> | 现象                       | 原因                               | 解决                                       |
> | :------------------------- | :--------------------------------- | :----------------------------------------- |
> | `Connection reset by peer` | `conf.d` 为空，无 server 块        | 补 `default.conf`，`nginx -s reload`       |
> | `403 Forbidden`            | `index.html` 不存在或 SELinux 限制 | 补文件、`chmod 644`、`chcon` 或挂载加 `:z` |
> | 资源 404（子目录）         | Vite `base` 未配置                 | 设 `base: '/子路径/'`，重新 build          |
> | 刷新 404（SPA）            | 缺少 `try_files`                   | 加 `try_files $uri $uri/ /index.html`      |
>
> 查看日志：
>
> ```bash
> docker logs nginx
> tail -f /data/nginx/logs/error.log
> ```
>
> ---
>
> ## 3. 多项目部署方案
>
> ### 3.1 方案一：子域名（推荐）
>
> **目录规划**
>
> ```text
> /data/nginx/html/
> ├── blog/          ← personal-blog 的 dist
> ├── anilink/       ← AniLink-Vue3 的 dist
> └── chat/          ← My-Chat 前端的 dist
> ```
>
> **上传**
>
> ```bash
> scp -r dist/* root@公网IP:/data/nginx/html/blog/
> scp -r dist/* root@公网IP:/data/nginx/html/anilink/
> ```
>
> **配置** `/data/nginx/conf.d/blog.conf`：
>
> ```nginx
> server {
>     listen 80;
>     server_name blog.你的域名.com;
> 
>     root /usr/share/nginx/html/blog;
>     index index.html;
> 
>     location / {
>         try_files $uri $uri/ /index.html;
>     }
> }
> ```
>
> `anilink.conf` 同理，只改 `server_name` 和 `root`。
>
> **域名解析**：将子域名 A 记录指向公网 IP。
>
> **重载**：
>
> ```bash
> docker exec nginx nginx -s reload
> ```
>
> ---
>
> ### 3.2 方案二：子目录（无域名时）
>
> **修改前端构建配置**
>
> Vite 项目 `vite.config.js`：
>
> ```js
> export default defineConfig({
>   base: '/blog/',
>   // ...
> })
> ```
>
> Vue CLI 项目 `vue.config.js`：
>
> ```js
> module.exports = {
>   publicPath: '/blog/'
> }
> ```
>
> **重新 build 并上传**
>
> ```bash
> mkdir -p /data/nginx/html/blog
> scp -r dist/* root@公网IP:/data/nginx/html/blog/
> ```
>
> **配置** `/data/nginx/conf.d/blog.conf`：
>
> ```nginx
> server {
>     listen 80;
>     server_name _;
> 
>     location /blog/ {
>         root /usr/share/nginx/html;
>         index index.html;
>         try_files $uri $uri/ /blog/index.html;
>     }
> }
> ```
>
> 多个项目依次增加 `location /anilink/` 等。
>
> **访问**：`http://公网IP/blog/`
>
> > 注意：用 `root` 而非 `alias`，`try_files` 最后一项写完整路径 `/blog/index.html`。
>
> ---
>
> ### 3.3 方案三：不同端口
>
> ```nginx
> server {
>     listen 80;
>     server_name _;
>     root /usr/share/nginx/html/blog;
>     index index.html;
>     location / { try_files $uri $uri/ /index.html; }
> }
> 
> server {
>     listen 81;
>     server_name _;
>     root /usr/share/nginx/html/anilink;
>     index index.html;
>     location / { try_files $uri $uri/ /index.html; }
> }
> ```
>
> 运行容器时映射多端口：
>
> ```bash
> -p 80:80 -p 81:81
> ```
>
> 安全组放行对应端口。访问 `http://公网IP:81`。
>
> > 缺点：URL 带端口，适合内部测试。
>
> ---
>
> ### 3.4 方案对比
>
> | 方案     | 优点                   | 缺点                  | 适用         |
> | :------- | :--------------------- | :-------------------- | :----------- |
> | 子域名   | 干净、隔离、易配 HTTPS | 需域名                | 有域名，推荐 |
> | 子目录   | 无域名可用             | 需改 `base`，路径易错 | 单 IP 多项目 |
> | 不同端口 | 配置最简单             | URL 不美观            | 临时测试     |
> | 多容器   | 资源隔离               | 管理成本高            | 项目差异大   |
>
> ---
>
> ## 4. 关键注意事项
>
> 1. **Vite `base`**：子目录部署必须设置，否则 JS/CSS 404。
> 2. **Vue Router**：history 模式用 `createWebHistory(import.meta.env.BASE_URL)`；hash 模式无需改。
> 3. **`try_files`**：SPA 必须加 `try_files $uri $uri/ /index.html` 或 `/子路径/index.html`。
> 4. **SELinux**：新目录若 403，执行 `chcon -Rt svirt_sandbox_file_t /data/nginx/html` 或挂载加 `:z`。
> 5. **重载**：每次改配置或上传新文件后执行 `docker exec nginx nginx -s reload`。
> 6. **HTTPS**：子域名方案可用 Let's Encrypt 通配符证书，一次覆盖多个子域。
>
> ---
>
> ## 5. 常用命令速查
>
> ```bash
> # 上传
> scp -r dist/* root@公网IP:/data/nginx/html/
> 
> # 权限
> chmod -R 755 /data/nginx/html
> 
> # SELinux 标签
> chcon -Rt svirt_sandbox_file_t /data/nginx/html
> 
> # 重载 Nginx
> docker exec nginx nginx -s reload
> 
> # 本地验证
> curl -I http://localhost
> 
> # 查看日志
> docker logs nginx
> tail -f /data/nginx/logs/error.log
> 
> # 查看容器状态
> docker ps | grep nginx
> ```
>
> ---
>
> ## 6. 目录结构示例（多项目）
>
> ```text
> /data/nginx/
> ├── conf.d/
> │   ├── default.conf
> │   ├── blog.conf
> │   └── anilink.conf
> ├── html/
> │   ├── blog/
> │   ├── anilink/
> │   └── chat/
> ├── logs/
> │   ├── access.log
> │   └── error.log
> └── certs/
>     └── 你的证书.pem
> ```
>
> ---
>
> **总结**：单项目部署 = 上传 `dist` + `default.conf` + 重载。多项目部署 = 在 `conf.d` 下为每个项目增加配置，配合子域名或子目录区分。一台 Nginx 可同时服务任意多个前端项目。0
