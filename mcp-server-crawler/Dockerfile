# 生产镜像：FastMCP Streamable HTTP（:8103/mcp），供云上 Compose 常驻
FROM python:3.12-slim

WORKDIR /app

# 仅装运行依赖，不装 pytest
COPY pyproject.toml README.md ./
COPY src ./src
RUN pip install --no-cache-dir .

ENV CRAWLER_HOST=0.0.0.0 \
    CRAWLER_PORT=8103 \
    PYTHONUNBUFFERED=1

EXPOSE 8103

# 进程探活在 compose healthcheck；此处只启动服务
CMD ["python", "-m", "crawler_mcp.server"]
