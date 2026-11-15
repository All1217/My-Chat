# 启动爬虫 MCP（http://localhost:8103/mcp）
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 python，请先安装 Python 3.11+"
}

# 国内网络建议清华源；可改回默认 PyPI
$pipIndex = "https://pypi.tuna.tsinghua.edu.cn/simple"
python -m pip install -e ".[dev]" -i $pipIndex --trusted-host pypi.tuna.tsinghua.edu.cn
python -m crawler_mcp.server
