# Flyway 多机数据库对齐指引

Flyway **可以**在多台开发机之间稳定使用。跨机要对齐的是「同一套迁移脚本 + 每台库各自准确的执行历史」，不是靠 baseline 假装两边结构一样。

迁移文件：`my-chat-server/src/main/resources/db/migration/`  
配置：`application.yaml` → `spring.flyway`（默认 `baseline-on-migrate=false`）

---

## 先记住三句话

1. **脚本随 Git 走，库状态不随 Git 走。** 每台机器有自己的 Postgres 和 `flyway_schema_history`。
2. **日常保持空库或已由本仓库脚本迁出来的库，`FLYWAY_BASELINE_ON_MIGRATE` 默认 false。** 启动后自动补跑未执行版本。
3. **已发布的 `Vn__*.sql` 禁止改内容。** 结构变更只新增更高版本；改旧文件会在其它机器上 checksum 校验失败。

---

## 什么跟着仓库，什么不跟着

| 随仓库 | 不随仓库 |
|--------|----------|
| `V1`…`Vn` 迁移脚本 | 本机 Postgres 数据与表结构 |
| Flyway 相关应用配置 | `flyway_schema_history`（存在库里） |

每台机器流程：拉代码 → 连本机库 → 按历史表执行尚未跑过的版本。

---

## 按场景怎么做

### 日常开发 / 新电脑只要结构

1. 安装 PostgreSQL，并确保已有 **pgvector**（及 `uuid-ossp`，也可由 V1 `CREATE EXTENSION IF NOT EXISTS` 创建）。
2. 使用**空库**（或可丢数据的开发库），**不要**开 baseline。
3. 启动后端，让 Flyway 从 V1 跑到最新。
4. 默认账户、策略等仍由应用 SeedRunner 写入，不靠迁移脚本灌业务种子（除非脚本里已写明）。

### 换机且要带走数据

在源机器导出，在目标机器还原（**包含** `flyway_schema_history`）：

```powershell
# 源机
pg_dump -h localhost -U postgres -d postgres -F c -f my-chat.dump

# 目标机（库先空或按需重建）
pg_restore -h localhost -U postgres -d postgres --clean --if-exists my-chat.dump
```

还原后不要再 baseline；直接启动，只应补跑源库尚未有的更高版本（若有）。

### 仅一次：接管「手工建过、且与某版本结构完全一致」的旧库

1. 先备份。
2. 确认真实表结构 **等于** 某版本（例如完整 V3），再临时：

```powershell
$env:FLYWAY_BASELINE_ON_MIGRATE="true"
```

当前配置 `baseline-version` 为 `3`：会把现有库标成 3，跳过 V1–V3，再执行 V4 及以后。

3. 确认 `flyway_schema_history` 已生成且后续迁移成功后，**立刻关掉**（环境变量删掉或设为 `false`）。

**禁止**在「半套旧表 / 与 baseline 版本不一致」的库上开 baseline——本机曾因此缺 `paper_ai_run` 导致 V5 失败。

### 本机开发库已乱、数据可丢

清空 `public` 后重建扩展，再正常 migrate（勿 baseline）：

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

也可用 Maven Wrapper（在 `my-chat-server` 下）对空库执行 migrate，再启动应用。

---

## 不要做的事

- 长期默认打开 `FLYWAY_BASELINE_ON_MIGRATE=true`
- 假定「另一台有的表，这台一定有」，却跳过对应 `Vn` 脚本
- 修改已在其它环境执行过的迁移文件内容
- 两台机器共用「口头对齐」的版本号，却不核对本机真实表与 `flyway_schema_history`

---

## 自检清单

启动前或换机后可快速确认：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

- 最新 `version` 应等于仓库里最高的 `Vn`
- 全部 `success = true`
- 抽样核对应由该版本创建的关键表（例如 V3 含 `paper_ai_run`，V5 含 `paper_research_note`）

IDEA：Run Configuration 里不要长期保留 `FLYWAY_BASELINE_ON_MIGRATE=true`；仅一次性接管旧库时使用。

---

## 和 README 的关系

空库初始化、baseline 环境变量示例见根目录 [README.md](../README.md)「初始化数据库」一节。本文只补充 **多机 / 换机对齐** 约定。
