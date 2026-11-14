# deploy/

复用宿主机 **`pg`**，Compose 跑 **app + nginx**。可选 profile：`jenkins`、`ops`（Dozzle + Adminer）。步骤见 [docs/部署与运维指引.md](../docs/部署与运维指引.md)。

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | app / nginx；`--profile jenkins`；`--profile ops`（日志+查库）；**无**自带 postgres |
| `Dockerfile.app` / `Dockerfile.web` | JDK 25 / Node 20 多阶段构建 |
| `nginx.conf` | `/` 静态，`/rag/` 反代去前缀 |
| `.env.example` | 复制为 `/data/my-chat/.env` |
| `Jenkinsfile` | Script Path；检出 `/data/my-chat/repo` |
