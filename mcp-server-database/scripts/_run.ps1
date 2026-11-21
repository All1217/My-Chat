# 内部启动脚本：加载 .env，按 DbType 启动 Spring Boot
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("postgres", "mysql")]
    [string]$DbType
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

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
} else {
    Write-Host "未找到 .env，将仅使用当前进程环境变量（可参考 .env.example）"
}

$env:DB_TYPE = $DbType
$env:SPRING_PROFILES_ACTIVE = $DbType

if ($DbType -eq "postgres") {
    if (-not $env:PGSQL_PASS) {
        Write-Error "缺少 PGSQL_PASS。请在 .env 中配置或先 `$env:PGSQL_PASS='...'`"
        exit 1
    }
} else {
    if (-not $env:MYSQL_PASS) {
        Write-Error "缺少 MYSQL_PASS。请在 .env 中配置或先 `$env:MYSQL_PASS='...'`"
        exit 1
    }
}

$mvnw = Join-Path $Root "mvnw.cmd"
if (-not (Test-Path $mvnw)) {
    Write-Error "未找到 mvnw.cmd：$mvnw"
    exit 1
}

Write-Host "启动 mcp-server-database（DB_TYPE=$DbType）→ http://localhost:8102/mcp"
Write-Host "关闭本窗口或运行 scripts\stop.bat 可停止。"
& $mvnw spring-boot:run
