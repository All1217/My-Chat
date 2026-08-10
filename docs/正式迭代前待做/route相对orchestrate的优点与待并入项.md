# route 相对 orchestrate 的优点与待并入项

> 用途：产品长期收敛为加厚 **Orchestrator** 时，把当前 **Routing（route）** 路径上已具备、而主聊天 orchestrate 仍缺的能力逐项并入。  
> **主聊天已移除** `agentMode=route` 回退，仅 Orchestrator（`format=ndjson`）。单次 Routing 对照仅保留调试入口 `POST /ai/agent/route`（NDJSON）。

## 优点对照（route 有 / 默认 orchestrate 暂缺）

| 优点 | 说明 | 并入 orchestrate 时建议 |
|------|------|------------------------|
| 工具旁路可观测 | `ObservabilityStreamAdvisor` → NDJSON `tool_call` / `tool_result`，前端时间线可回放 | 各 Worker 挂同一 Advisor，事件冒泡到主会话 `turnId` |
| 会话 Memory 顾问 | 真实 `chatId` + `MessageChatMemoryAdvisor` 写回 `spring_ai_chat_memory` | Orchestrator 已显式 persist 短 USER+ASSISTANT；若 Worker 也 stream，需统一 Memory 策略，避免 orch-* 泄漏与重复落库 |
| 单次分流延迟/成本更低 | 一轮 classify + 一个能力 Client；适合对照调试与低成本问答 | Orchestrator 可对「明显单步」任务提前 finish / 短路，不必每轮都跑满多步 |
| 质量环可挂接 | 主聊天 route NDJSON 可在写盘后跑 `qualityLoop` | 主聊天 orchestrate 已默认挂质量环；保持「可解析 write 路径才跑」即可 |

## 实测备忘（2026-08）

1. 开启 DeepSeek `thinking.type=enabled` 后，orchestrate 同步 `call()` 响应里常有非空 `reasoningContent` / `reasoning_tokens`，但默认编排 NDJSON **不发** `thinking_delta`。  
2. 切到 `agentMode=route` 后，工具时间线（`route` + `tool`）正常；主回答流式帧里 `reasoningContent` 常为 `""`，故思考时间线仍可能空白——属框架流式映射问题，不是「未开 thinking」。  
3. Demo `/ai/agent/route`：与主聊天 route **同构 NDJSON**，默认**不落** `chat_assistant_turns`（旁路调试）；可选 `chatId` 仅供本轮 Memory advisor。

## 长期方向

- 产品主聊天已只保留 **Orchestrator**；**切片 A（最终答复 token 流式）已并入**主聊天 `orchestrateNdjson`。  
- **切片 B（高复杂度）仍待办**：Worker `.stream()`、会话级 `ObservabilityStreamAdvisor`、`thinking_delta` 挂主 turn。  
- `route`：仅 Demo `/ai/agent/route` 对照；能力并入完成后可再评估是否下线该调试入口。
