# 生产镜像：FastMCP Streamable HTTP（:8103/mcp），供云上 Compose 常驻
FROM python:3.12-slim

WORKDIR /app

# 国内构建默认走阿里云 PyPI；海外可 --build-arg PIP_INDEX_URL=https://pypi.org/simple
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ARG PIP_TRUSTED_HOST=mirrors.aliyun.com
ENV PIP_INDEX_URL=${PIP_INDEX_URL} \
    PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST} \
    PIP_DEFAULT_TIMEOUT=120 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# 仅装运行依赖，不装 pytest
COPY pyproject.toml README.md ./
COPY src ./src
RUN pip install --no-cache-dir --retries 5 .

ENV CRAWLER_HOST=0.0.0.0 \
    CRAWLER_PORT=8103 \
    PYTHONUNBUFFERED=1

EXPOSE 8103

# 进程探活在 compose healthcheck；此处只启动服务
CMD ["python", "-m", "crawler_mcp.server"]
