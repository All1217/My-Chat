# 阿里云 Jenkins 部署指引（CentOS 7.9）

样例：[`deploy/`](../deploy/)。复用宿主机已有 **`pg`**（pgvector:pg18，`/data/pgdata`）；Compose 只跑 **app + nginx**；Jenkins 用 compose profile，不 yum 安装。

---

## 1. 服务器配置现状与建议

| 项 | 现状 / 建议 |
|----|-------------|
| 规格 | 2C4G；已加 Swap ≥2GB |
| Docker | 已装；Compose v2（`docker compose`） |
| 已有 `pg` | **复用**；卷 `/data/pgdata`；为本项目建空库 `mychat` |
| 已有博客 `nginx` | **卸掉**（让出 80）；旧 `/data/nginx` 可留档 |
| 数据目录 | 统一 `/data/my-chat/`（workspace、ingest、jenkins_home、repo、`.env`） |
| 安全组 | 放行 22、80；**不要**对公网开 5432 / 8100 / 8080 |
| Jenkins | 镜像 `jenkins/jenkins:lts-jdk21`，只绑 `127.0.0.1:8080`，SSH 隧道访问 |
| 发布 | 推 GitHub 后 Jenkins **Build Now**（不做 Webhook） |
| MCP | 云上 `APP_MCP_ENABLED=false` |

CentOS 7 已 EOL：不要在宿主机装 JDK 25 / Node；构建与运行都进容器。

---

## 2. 首次安装全流程（一条龙）

每条命令单独执行；已是 `root` 无需 `sudo`。

```bash
# 确认 Swap（应有约 2G）
free -h

# 卸掉博客 nginx，释放 80/443（配置目录 /data/nginx 仍保留，不删数据）
docker stop nginx && docker rm nginx

# 确认 pg 在跑
docker ps --filter name=pg
docker exec pg pg_isready -U postgres

# 在已有 pg 里建空库 mychat（给 Flyway V1–V5；不动旧 postgres 库）
docker exec -i pg psql -U postgres -d postgres -c "CREATE DATABASE mychat;"

# 建议：5432 勿对公网（安全组去掉 5432；容器仍可 -p 5432 供本机/host-gateway 访问）

# 本项目目录
mkdir -p /data/my-chat/{workspace,ingest,jenkins_home,repo}

# 克隆代码（换成你的 GitHub 地址）
git clone https://github.com/<你的账号>/My-Chat.git /data/my-chat/repo

# 密钥文件（PGSQL_PASS = 创建 pg 时的 POSTGRES_PASSWORD）
cp /data/my-chat/repo/deploy/.env.example /data/my-chat/.env
chmod 600 /data/my-chat/.env
vi /data/my-chat/.env

# 进入仓库根
cd /data/my-chat/repo
export ENV_FILE=/data/my-chat/.env

# 串行构建（2C4G 勿并行，防 OOM）
docker compose -f deploy/docker-compose.yml --env-file "$ENV_FILE" build app
docker compose -f deploy/docker-compose.yml --env-file "$ENV_FILE" build nginx

# 启动业务（连宿主机 pg，不另起数据库容器）
docker compose -f deploy/docker-compose.yml --env-file "$ENV_FILE" up -d app nginx

# 浏览器打开 http://<公网IP>/ 验收；首次启动 Flyway 会迁 mychat

# （可选）启动 Jenkins 容器
docker compose -f deploy/docker-compose.yml --env-file "$ENV_FILE" --profile jenkins up -d jenkins

# 本机隧道后打开 http://127.0.0.1:8080
# ssh -L 8080:127.0.0.1:8080 root@<公网IP>
# 初始密码：
docker exec $(docker ps -qf name=jenkins) cat /var/jenkins_home/secrets/initialAdminPassword
```

Jenkins 任务：Pipeline from SCM → Script Path `deploy/Jenkinsfile` → 凭据用 GitHub PAT → 日常点 **Build Now**。

若 `CREATE DATABASE mychat` 报已存在，可跳过该行。

---

## 3. 日常发布全流程

```bash
# 本机：改完代码推 GitHub
git push origin main

# 浏览器：SSH 隧道连上 Jenkins → 打开对应 Pipeline → Build Now
# （或服务器上若已配好凭据，等价于跑 Jenkinsfile：checkout → build app/nginx → up -d）

# 发布后快速看状态
docker ps --filter name=my-chat
curl -sI http://127.0.0.1/ | head -5
```

无需在服务器上手动 `git pull`（Jenkinsfile 会 checkout 到 `/data/my-chat/repo`）。

---

## 4. 内存与排障

| 现象 | 处理 |
|------|------|
| 构建 Exit 137 / 卡死 | 确认 Swap；串行 build；已设 MAVEN_OPTS / NODE_OPTIONS；考虑升 8G |
| 80 端口占用 | `docker ps` 是否还有旧 nginx；`ss -lntp \| grep :80` |
| app 连不上库 | `pg` 是否 Up；`.env` 里 `PGSQL_PASS` 是否与创建 pg 时一致；URL 是否 `host.docker.internal:5432/mychat` |
| Flyway 失败 | 确认连的是空库 `mychat`；勿对空库开 baseline |
| 前端通、接口 404 | 查 nginx 容器与 `/rag/`；`docker logs` app |
| Jenkins 构建找不到上下文 | 代码必须在 `/data/my-chat/repo`，且 compose 已挂载同路径 |

---

## 5. 环境变量清单

| 变量 | 必需 | 说明 |
|------|------|------|
| `PGSQL_PASS` | 是 | 宿主机 `pg` 的密码 |
| `SPRING_DATASOURCE_URL` | 是 | 默认 `jdbc:postgresql://host.docker.internal:5432/mychat?currentSchema=public` |
| `SPRING_DATASOURCE_USERNAME` | 否 | 默认 `postgres` |
| `OPENAI_API_KEY` | 是 | 对话 |
| `EMBEDDING_MODEL_API_KEY` | 是 | 向量 |
| `APP_MCP_ENABLED` | 建议 `false` | 云上关 MCP |
| `FLYWAY_BASELINE_ON_MIGRATE` | 默认 `false` | 空库保持 false |
| `MYCHAT_DATA` | 否 | 默认 `/data/my-chat` |
| `MYCHAT_REPO` | 否 | 默认 `/data/my-chat/repo` |

模板：[`deploy/.env.example`](../deploy/.env.example)。
