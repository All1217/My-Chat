# deploy/

复用宿主机 **`pg`**，Compose 跑 **app + nginx**（Jenkins 为 profile）。步骤见 [docs/阿里云Jenkins部署指引.md](../docs/阿里云Jenkins部署指引.md)。

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | app / nginx；`jenkins` 用 `--profile jenkins`；**无**自带 postgres |
| `Dockerfile.app` / `Dockerfile.web` | JDK 25 / Node 20 多阶段构建 |
| `nginx.conf` | `/` 静态，`/rag/` 反代去前缀 |
| `.env.example` | 复制为 `/data/my-chat/.env` |
| `Jenkinsfile` | Script Path；检出 `/data/my-chat/repo` |
