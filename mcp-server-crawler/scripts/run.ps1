# 启动爬虫 MCP（http://localhost:8103/mcp）。可由 scripts\start.bat 双击调用。
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 python，请先安装 Python 3.11+"
}

$envFile = Join-Path $Root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        Set-Item -Path "Env:$key" -Value $val
    }
    Write-Host "已加载 $envFile"
}

# 国内网络建议清华源；可改回默认 PyPI
$pipIndex = "https://pypi.tuna.tsinghua.edu.cn/simple"
python -m pip install -e ".[dev]" -i $pipIndex --trusted-host pypi.tuna.tsinghua.edu.cn

$port = "8103"
if ($env:CRAWLER_PORT) { $port = $env:CRAWLER_PORT }
Write-Host "启动 mcp-server-crawler → http://localhost:${port}/mcp"
Write-Host "关闭本窗口即可停止。"
python -m crawler_mcp.server
