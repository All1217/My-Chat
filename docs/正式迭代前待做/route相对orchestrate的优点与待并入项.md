# route 相对 orchestrate 的优点与待并入项

> 用途：产品长期收敛为加厚 **Orchestrator** 时，把当前 **Routing（route）** 路径上已具备、而默认主聊天 orchestrate 仍缺的能力逐项并入。  
> 主聊天默认仍是 `agentMode=orchestrate`；`agentMode=route` 暂保留回退；调试入口 `POST /ai/agent/route` 已改为 NDJSON 流式。

## 优点对照（route 有 / 默认 orchestrate 暂缺）

| 优点 | 说明 | 并入 orchestrate 时建议 |
|------|------|------------------------|
| 模型 token 级流式 | `ChatClient.stream()` 连续推 `text_delta`；orchestrate Worker/决策多为同步 `.call()`，对外常是 `step` + **整段**最终 `text_delta` | Worker（至少 finish 前的回答 Client）改为 stream，并把增量挂到会话 turn |
| 工具旁路可观测 | `ObservabilityStreamAdvisor` → NDJSON `tool_call` / `tool_result`，前端时间线可回放 | 各 Worker 挂同一 Advisor，事件冒泡到主会话 `turnId` |
| 思考回显管道 | `emitTextEvents` + `ReasoningContentExtractor` → `thinking_delta`，可与 tool 交错写入 `parts` | 同上；注意流式路径下 Spring AI OpenAI 兼容层可能把 `reasoningContent` 弄成空串（实测已知坑） |
| 会话 Memory | route：真实 `chatId` + `MessageChatMemoryAdvisor` 写回 | **读**：`OrchestrateDialogueContextService` 惰性滚动摘要 + 近期原文窗口注入 prompt（不挂 Worker 上的 chatId Advisor）；**写**：回合末仍手动 `persist` 短 USER+最终 ASSISTANT。后续可评估 `VectorStoreChatMemoryAdvisor` 语义召回 |
| 单次分流延迟/成本更低 | 一轮 classify + 一个能力 Client；适合对照调试与低成本问答 | Orchestrator 可对「明显单步」任务提前 finish / 短路，不必每轮都跑满多步 |
| 质量环可挂接 | 主聊天 route NDJSON 可在写盘后跑 `qualityLoop` | 主聊天 orchestrate 已默认挂质量环；保持「可解析 write 路径才跑」即可 |

## 实测备忘（2026-08）

1. 开启 DeepSeek `thinking.type=enabled` 后，orchestrate 同步 `call()` 响应里常有非空 `reasoningContent` / `reasoning_tokens`，但默认编排 NDJSON **不发** `thinking_delta`。  
2. 切到 `agentMode=route` 后，工具时间线（`route` + `tool`）正常；主回答流式帧里 `reasoningContent` 常为 `""`，故思考时间线仍可能空白——属框架流式映射问题，不是「未开 thinking」。  
3. Demo `/ai/agent/route`：与主聊天 route **同构 NDJSON**，默认**不落** `chat_assistant_turns`（旁路调试）；可选 `chatId` 仅供本轮 Memory advisor。

## 长期方向

- 产品只保留一套先进模式：**加厚后的 Orchestrator**（吸收上表优点）。  
- `route`：过渡/回退 + Demo 对照；能力并入完成后可再评估是否删除主聊天 `agentMode=route`。
