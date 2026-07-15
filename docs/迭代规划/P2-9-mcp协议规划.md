# My-Chat MCP 协议集成 — 入门实践方案

> **目标读者**：AI 大模型应用开发初学者  
> **前置知识**：已掌握本项目的基础架构（Spring Boot + Spring AI + ChatClient + @Tool 注解）  
> **参考文档**：[Spring AI MCP 官方文档](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) | [MCP Java SDK](https://modelcontextprotocol.io/sdk/java/mcp-overview) | [本项目 AGENTS.md](./AGENTS.md)

---

## 一、什么是 MCP？先建立直觉

### 1.1 一句话概括

**MCP（Model Context Protocol）** 是一个标准化的协议，让 AI 模型能跨进程、跨语言地发现和调用外部工具。

你现在项目中的 `FileTools`（`@Tool` 注解的那些方法）是**进程内**的——它们和 ChatClient 跑在同一个 JVM 里。MCP 的目标是把这种"工具调用"标准化到**进程间**：

```
当前架构（进程内 Tool 调用）：          引入 MCP 后（进程间 Tool 调用）：

┌──────────┐                           ┌──────────┐    MCP 协议     ┌──────────────┐
│ My-Chat  │                           │ My-Chat  │ ←──────────→ │ MCP 天气服务器 │
│ ChatClient│ ───→ FileTools (同JVM)   │ ChatClient│   [tools/list] │ @McpTool 方法  │
│          │                           │          │   [tools/call] │               │
│ @Tool 方法│                           │ MCP Client│ ←──────────→ │               │
└──────────┘                           └──────────┘               └──────────────┘
```

### 1.2 MCP 的核心概念（对应官方示例）

结合你提供的 Tic-Tac-Toe 官方示例，MCP 有三个核心角色：

| 角色 | 类比 | 在示例中的体现 |
|------|------|--------------|
| **MCP Server** | 一个微服务，对外暴露工具 | `TicTacToeTools`（含 `@McpTool` 方法）+ `McpServerApplication` |
| **MCP Client** | 消费 MCP 工具的应用程序 | `McpClientApplication` + 两种客户模式 |
| **Transport（传输层）** | 通信方式 | STDIO（进程间管道）、Streamable-HTTP（HTTP + SSE） |

### 1.3 MCP Client 的两种使用模式（重点！）

官方示例展示了两种截然不同的客户模式：

| 模式 | 代表 | 谁决定调用什么工具 | 适用场景 |
|------|------|------------------|---------|
| **直接调用** | `GameService` | 你写的代码 | 确定性的、你已知工具名的调用 |
| **LLM 编排** | `AgentService` | AI 模型 | 用户自然语言 → AI 自主选择和调用工具 |

```java
// 模式 1：直接调用 ── 代码决定调用 "startNewGame"
ToolCallback tool = mcpTools.get("startNewGame");
String result = tool.call("{}");

// 模式 2：LLM 编排 ── AI 读工具描述后自主决定
ChatClient chatClient = ChatClient.builder(model)
    .defaultTools((Object[]) mcpToolCallbacks)  // ← MCP 工具注入到 ChatClient
    .build();
String reply = chatClient.prompt().user("帮我开一局井字棋").call().content();
```

---

## 二、本项目现状分析

### 2.1 已有的工具体系

My-Chat 已经有一套成熟的进程内工具调用：

```java
// FileTools.java —— 当前是 @Tool 注解（Spring AI 原生工具），不是 @McpTool
@Tool(description = "列出指定目录下的文件和子目录")
public String ls(@ToolParam(description = "目录路径") String path) { ... }
// ... 加上 cat, write, rm, mv, cp, mkdir, stat, grep, tree 共 9 个
```

```java
// AiConfiguration.java —— 工具注册到 ChatClient
return ChatClient.builder(model)
    .defaultTools(fileTools)  // ← FileTools 的一个 Bean 实例
    .build();
```

### 2.2 与 MCP 的关系

**当前状态**：`FileTools` 是进程内的 `@Tool` 工具，不是 MCP 工具。但这套架构**天然适合向 MCP 演进**，因为：

1. 工具方法已经拆分清晰（每个操作一个方法）
2. 每个方法有明确的 `@Tool(description=...)` + `@ToolParam(description=...)`
3. 工具通过 `.defaultTools()` 注册到 ChatClient
4. 只需把 `@Tool` 换成 `@McpTool`，加上 transport 配置，就能把 My-Chat 的本地工具变成对外发布的 MCP 服务

### 2.3 引入 MCP 后能获得什么

| 能力 | 当前 | 引入 MCP 后 |
|------|------|-----------|
| My-Chat 的工具被外部调用 | ❌ 不可用 | ✅ 其他应用可通过 MCP 协议调用 My-Chat 的 FileTools |
| My-Chat 调用外部工具 | ❌ 只有内置工具 | ✅ 可连接 MCP 天气/数据库/搜索服务器，扩展能力 |
| 工具热插拔 | ❌ 硬编码 | ✅ 在 application.yaml 中配置 MCP Server 连接即可 |
| 跨语言协作 | ❌ 仅 Java | ✅ MCP 协议与语言无关（Python/Node 服务器都能对接） |

---

## 三、入门实践路线图

建议分四个阶段，由浅入深。

### 阶段一：将现有 FileTools 升级为 MCP Server（2-3 小时）

**目标**：让 My-Chat 的 `FileTools` 成为标准的 MCP 服务器，可供任何 MCP 客户端调用。

**改动：**

#### 1.1 添加 Maven 依赖

```xml
<!-- pom.xml 新增 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

> Spring AI 2.0 BOM 自动管理版本，无需指定 version。

#### 1.2 新建 MCP 工具类（不修改原 FileTools）

```java
// 新建: tools/McpFileTools.java
// 路径: my-chat-server/src/main/java/com/mychat/tools/McpFileTools.java

package com.mychat.tools;

import com.mychat.utils.WorkspaceUtil;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FileTools 的 MCP 版本 —— 将原 @Tool 改为 @McpTool 后对外发布。
 * 
 * 注意：保留原 FileTools.java 不动，确保现有 ChatController 不受影响。
 * 外部 MCP Client 通过 tcp://localhost:8100/mcp 协议端点调用这些方法。
 */
@Component
public class McpFileTools {

    private final WorkspaceUtil workspaceUtil;

    public McpFileTools(WorkspaceUtil workspaceUtil) {
        this.workspaceUtil = workspaceUtil;
    }

    @McpTool(name = "ls", description = "列出指定目录下的文件和子目录，展示文件名、类型(DIR/FIL)和大小")
    public String ls(
            @McpToolParam(description = "目录路径，留空表示当前工作目录", required = false)
            String path) {
        // 复用 WorkspaceUtil 的现有逻辑
        ... 返回 JSON 字符串
    }

    @McpTool(name = "cat", description = "查看文本文件内容，最大读取 1MB")
    public String cat(
            @McpToolParam(description = "文件路径", required = true) String path) {
        ...
    }

    // ... 依次实现 tree, grep, write, mkdir, rm, mv, cp, stat
}
```

**核心差异**：`@McpTool` vs `@Tool`

| 维度 | @Tool（当前） | @McpTool（MCP） |
|------|-------------|----------------|
| 调用方 | 同 JVM 内的 ChatClient | 任何 MCP 客户端（跨进程/跨语言） |
| 传输 | Spring AI 内部 | JSON-RPC over HTTP/SSE |
| 发现机制 | Spring Bean 注入 | `tools/list` 响应自动公布 |
| 参数描述 | 仅 AI 可读 | AI + 客户端 UI 均可读 |

#### 1.3 配置 application.yaml

```yaml
spring:
  ai:
    mcp:
      server:
        type: SYNC               # 使用同步模式（与项目虚拟线程架构一致）
        protocol: STREAMABLE     # Streamable-HTTP 传输（推荐）
        annotation-scanner:
          enabled: true
```

不需要额外的 Java 配置类——Spring Boot Auto-Configuration 会自动扫描 `@McpTool` Bean 并注册到 MCP Server。

#### 1.4 验证

启动后端后，用 curl 验证 MCP 端点：

```bash
# 列出所有工具
curl -X POST http://localhost:8100/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# 调用 ls 工具
curl -X POST http://localhost:8100/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"ls","arguments":{"path":"."}}}'
```

---

### 阶段二：让 My-Chat 作为 MCP Client 连接外部工具（3-4 小时）

**目标**：让 My-Chat ChatClient 能调外部 MCP 服务器的工具。同时保留原有 FileTools。

**改动：**

#### 2.1 添加 Client 依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

#### 2.2 在 application.yaml 配置外部 MCP 服务器连接

```yaml
spring:
  ai:
    mcp:
      client:
        connections:
          weather-server:                          # 连接别名
            type: STREAMABLE
            url: http://localhost:8081/mcp         # 外部天气 MCP 服务器的地址
          calculator-server:                       # 可连接多个 MCP 服务器
            type: STREAMABLE  
            url: http://localhost:8082/mcp
```

#### 2.3 注入 MCP 工具到 ChatClient

修改 `AiConfiguration.java`：

```java
@Configuration
public class AiConfiguration {

    @Bean
    public ChatClient toolChatClient(
            ChatModel model,
            ChatMemory chatMemory,
            FileTools fileTools,              // 本地工具（保留）
            ToolCallbackProvider mcpTools     // ← Spring AI 自动扫描 mcp.client.connections 注入
    ) {
        // 合并本地工具和远程 MCP 工具
        ToolCallback[] allTools = Stream.concat(
                Arrays.stream(fileTools.getToolCallbacks()),
                Arrays.stream(mcpTools.getToolCallbacks())
        ).toArray(ToolCallback[]::new);

        return ChatClient.builder(model)
                .defaultSystem("...")
                .defaultTools((Object[]) allTools)   // ← 一次性注入所有工具
                .defaultAdvisors(...)
                .build();
    }
}
```

> **关键点**：`ToolCallbackProvider` 由 Spring AI 自动配置，启动时自动调用 `tools/list` 发现 MCP Server 的所有 `@McpTool` 方法。

#### 2.4 效果

```java
// 用户说："帮我查一下北京的天气，并把结果写到 weather.txt"
// AI 会自动：
// 1. 调用 MCP 天气服务器的 getWeather("北京") → 获取天气数据
// 2. 调用本地的 write("weather.txt", 天气数据) → 写入文件
// 两个工具来自不同进程，对 AI 来说是透明的
```

---

### 阶段三：构建一个独立的小型 MCP 工具服务器（2-3 小时）

**目标**：动手写一个最小、可运行的 MCP Server，巩固理解。

**推荐项目**：数据库查询 MCP Server —— 让 AI 能查数据库。

#### 3.1 关键代码

```java
// 新项目: my-chat-mcp-dbserver（独立 Maven 模块）
// 也可在 my-chat-server 内新建子模块：tools/mcp/

@SpringBootApplication
public class DbMcpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbMcpServerApplication.class, args);
    }
}

@Component
public class DatabaseTools {

    private final JdbcTemplate jdbc;

    @McpTool(name = "db-query", 
             description = "执行只读 SQL 查询（仅支持 SELECT），返回 JSON 数组结果")
    public String query(
            @McpToolParam(description = "SQL SELECT 语句", required = true) String sql) {
        // 白名单校验：仅允许 SELECT 开头
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return "{\"error\": \"仅允许 SELECT 查询\"}";
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return new ObjectMapper().writeValueAsString(rows);
    }
}
```

#### 3.2 application.yaml

```yaml
server.port: 8082
spring.ai.mcp.server:
  type: SYNC
  protocol: STREAMABLE
```

#### 3.3 在 My-Chat 中配置连接

```yaml
spring.ai.mcp.client.connections:
  my-dbserver:
    type: STREAMABLE
    url: http://localhost:8082/mcp
```

完成后，用户可以自然语言对话："帮我看下 users 表有多少条记录"——AI 会自动调用 `db-query` 工具。

---

### 阶段四：接入第三方 MCP 服务（3-4 小时）

**目标**：连接社区已有的 MCP 服务器，扩展 AI 能力（网页抓取、搜索、计算等），无需自己编写工具代码。

#### 4.1 哪里找第三方 MCP 服务？

| 来源 | 说明 | 例子 |
|------|------|------|
| **npm registry** | 最多 MCP Server 的生态，以 `@modelcontextprotocol/server-*` 命名 | `server-filesystem`、`server-puppeteer` |
| **GitHub** | 社区贡献的各种 MCP 实现 | mcp-servers 合集 |
| **官方 MCP 列表** | [modelcontextprotocol.io](https://modelcontextprotocol.io) 官方收录 | Brave Search、Playwright |

Spring AI 官方文档中推荐了以下示例：

```
spring-ai-examples/
  model-context-protocol/
    web-search/brave-chatbot/    ← Brave Search MCP 客户端示例
    filesystem/                   ← 文件系统 MCP 示例（含 Windows 跨平台配置）
```

#### 4.2 STDIO 模式接入（通过 npx 运行）

大多数社区 MCP Server 发布为 npm 包，通过 `npx` 以 STDIO 协议运行。

**application.yaml 配置：**

```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          connections:
            # 网页抓取（mcp-web-fetcher）
            web-fetcher:
              command: cmd.exe
              args:
                - /c
                - npx
                - -y
                - @anthropic/mcp-web-fetcher
            # 文件系统（限定目录范围）
            filesystem:
              command: cmd.exe
              args:
                - /c
                - npx
                - -y
                - @modelcontextprotocol/server-filesystem
                - "D:\\projects"
```

> **Windows 注意**：`npx` 是 `.cmd` 批处理文件，必须用 `cmd.exe /c` 包裹，否则 Java `ProcessBuilder` 无法直接执行。Linux/macOS 可直接写 `command: npx`。

从 Spring AI 官方文档引用的跨平台示例：

```java
// 跨平台 MCP 客户端配置
@Bean(destroyMethod = "close")
public McpSyncClient mcpClient() {
    ServerParameters stdioParams;
    if (isWindows()) {
        stdioParams = ServerParameters.builder("cmd.exe")
                .args("/c", "npx", "-y", "@modelcontextprotocol/server-filesystem", "target")
                .build();
    } else {
        stdioParams = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", "target")
                .build();
    }
    return McpClient.sync(new StdioClientTransport(stdioParams))
            .requestTimeout(Duration.ofSeconds(10))
            .build()
            .initialize();
}
private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
}
```

#### 4.3 Streamable-HTTP 模式接入（远程服务）

对于已经部署为 HTTP 服务的 MCP Server，使用 Streamable-HTTP 连接：

```yaml
spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            weather-service:
              url: http://localhost:8081
              endpoint: /mcp
            calculator:
              url: http://localhost:8082
              endpoint: /mcp
```

#### 4.4 工具名称冲突处理

当多个 MCP 服务器定义了同名工具时，Spring AI 的 `DefaultMcpToolNamePrefixGenerator` 自动处理：

| 场景 | 行为 |
|------|------|
| 同一个工具名只出现一次 | 使用原名，如 `search` |
| 不同服务器出现同名工具 | 自动加前缀区分，如 `alt_1_search`、`alt_2_search` |
| 自定义前缀策略 | 实现 `McpToolNamePrefixGenerator` 接口注册为 Bean |

自定义前缀生成器示例：

```java
@Component
public class CustomToolNamePrefix implements McpToolNamePrefixGenerator {
    @Override
    public String prefixedToolName(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        String serverName = connectionInfo.initializeResult().serverInfo().name();
        return serverName + "_" + tool.name();
    }
}
```

#### 4.5 工具过滤：选择性暴露

通过 `McpToolFilter` 接口，让 AI 只看到特定的 MCP 工具，避免暴露不需要的或危险的操作：

```java
@Component
public class SafeMcpToolFilter implements McpToolFilter {
    @Override
    public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        // 只允许 filesystem 服务中的 read 类操作
        if (connectionInfo.clientInfo().name().equals("filesystem")) {
            return tool.name().startsWith("read_");
        }
        return true;
    }
}
```

也可在 `application.yaml` 中通过全局开关禁用 MCP 工具回调：

```yaml
spring:
  ai:
    mcp:
      client:
        toolcallback:
          enabled: true        # 设为 false 则 MCP 工具不注入 ChatClient
```

#### 4.6 完整集成示例：引入 Web Fetch + Brave Search

```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          connections:
            web-fetcher:
              command: cmd.exe
              args: [/c, npx, -y, @anthropic/mcp-web-fetcher]
            brave-search:
              command: cmd.exe
              args: [/c, npx, -y, @anthropic/mcp-brave-search]
        streamable-http:
          connections:
            my-calculator:
              url: http://localhost:9090
              endpoint: /mcp
        toolcallback:
          enabled: true
```

配置后，AI 可以做出以下响应：

```
用户："帮我查一下最近关于 Spring AI 的新闻，然后把结果汇总保存到 workspace 的 report.txt"

AI 的行动：
1. 调用 brave-search → 搜索 "Spring AI recent news"
2. 调用 web-fetcher → 抓取每条新闻的详细内容
3. 调用本地 FileTools.write → 将汇总结果写入 report.txt
```

三个工具来自三个不同的 MCP 服务器，AI 完全透明的调用它们。

#### 4.7 安全注意事项（补充阶段二/三）

| 风险 | 防护措施 |
|------|---------|
| 第三方 MCP Server 行为不可控 | 用 `McpToolFilter` 限制 AI 可调用的工具范围 |
| 工具名冲突导致调用混乱 | 用 `DefaultMcpToolNamePrefixGenerator` 自动去重 |
| STDIO 本地进程占用系统资源 | 设 `request-timeout: 10s` 防止长时间卡死 |
| 远程 MCP Server 不可用 | 用 `@ConditionalOnMissingBean` 做兜底 |

---

## 四、关键设计决策

### 4.1 为什么不直接改造 FileTools？

保留原 `FileTools`（`@Tool`）+ 新建 `McpFileTools`（`@McpTool`），理由：

1. **向后兼容**：现有 ChatController 仍然使用 `.defaultTools(fileTools)`，零风险
2. **职责分离**：`FileTools` 返回值是 AI 友好的 Markdown，`McpFileTools` 返回 JSON（MCP 协议要求结构化数据）
3. **渐进迁移**：将来可以逐步让 ChatClient 也通过 MCP 调用工具，再废弃原 FileTools

### 4.2 选哪种 Transport？

| Transport | 何时使用 |
|-----------|---------|
| **Streamable-HTTP**（推荐） | Web 应用，需要重启/热更新独立管理 |
| **STDIO** | CLI 工具、IDE 插件（进程间管道通信） |

My-Chat 是 Web 应用，选 **Streamable-HTTP**。用 `spring-ai-starter-mcp-server-webmvc`。

### 4.3 SYNC vs ASYNC

选 **SYNC**（同步模式）。理由：
- 项目已启用 Tomcat 虚拟线程，同步阻塞不消耗平台线程
- FileTools 的所有操作都是 NIO（非 CPU 密集），适合同步+虚拟线程
- 与现有代码风格一致（无 Reactor）

### 4.4 安全注意事项

| 风险 | 防护 |
|------|------|
| MCP 工具被越权调用 | 通过 `TransportContextExtractor` 提取 HTTP Header 中的 Token 校验 |
| SQL 注入（阶段三） | 白名单：仅允许 `SELECT` 开头 + 参数化查询 |
| 文件路径穿越 | 沿用现有 `WorkspaceUtil.resolveSafe()` 校验 |
| 未授权连接 | 用 Spring Security 保护 `/mcp` 端点 |

---

## 五、学习路径对照

| 步骤 | 官方文档章节 | 官方示例对应 |
|------|------------|------------|
| 理解 MCP Server | [MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) | `McpServerApplication.java` |
| 理解 @McpTool 注解 | [MCP Server Annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) | `TicTacToeTools.java` |
| 直接调用模式 | [MCP Client Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html) | `GameService.java` |
| LLM 编排模式 | [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) | `AgentService.java` |
| 工具发现机制 | `ToolCallbackProvider` 概念 | 官方示例中 `GameService` 构造方法 |
| Transport 选型 | [MCP Server Boot Starters](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) | `application.yaml` 中的 `protocol: STREAMABLE` |

---

## 六、问题自查清单（边做边对照）

- [ ] 我能用 curl 调用 `tools/list` 列出 MCP Server 的工具吗？
- [ ] 我能用 curl 调用 `tools/call` 执行一个具体的工具吗？
- [ ] 本地 `FileTools` 和远程 MCP 工具能同时在 ChatClient 中工作吗？
- [ ] AI 能在一次对话中交替使用本地工具和远程工具吗？
- [ ] 当我新增一个 `@McpTool` 方法后，客户端是否需要重新部署？（答案：不需要——由 `tools/list` 动态发现）
- [ ] 报错时能否区分"本地工具出错"和"MCP 服务器返回的远程错误"？

---

## 七、相关文件索引

| 文件 | 说明 |
|------|------|
| `my-chat-server/pom.xml` | 添加 MCP server/client starter 依赖 |
| `my-chat-server/src/main/resources/application.yaml` | 配置 `spring.ai.mcp.server/clients` |
| `my-chat-server/src/main/java/com/mychat/tools/McpFileTools.java` | **新建**：MCP 版 FileTools |
| `my-chat-server/src/main/java/com/mychat/config/AiConfiguration.java` | 注入 `ToolCallbackProvider` 到 ChatClient |
| `my-chat-server/src/main/java/com/mychat/controller/ChatController.java` | 保持不变 |
| `docs/迭代规划/P1-AI工作区上下文-技术分析.md` | 工作区功能的技术文档 |
