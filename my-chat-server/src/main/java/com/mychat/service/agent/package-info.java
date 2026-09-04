/**
 * 主聊天 Agent 编排层（产品路径固定走 Orchestrator，不走 Routing）。
 * <p>
 * 怎么读：{@code ChatController}（HTTP 入口）
 * → {@link com.mychat.service.agent.ChatOrchestrateStreamService}（NDJSON 管道）
 * → {@link com.mychat.service.agent.AgentOrchestratorService}（decideNext 循环与 Worker）。
 * 决策 prompt 见 {@link com.mychat.service.agent.workflow}；
 * 最终答复拼装见 {@link com.mychat.service.agent.FinalAnswerComposer}。
 * {@link com.mychat.service.agent.AgentRoutingService} 仅 Demo（{@code POST /ai/agent/route}）。
 * <p>
 * 冻结契约（改行为前先对照）：入口 {@code format=ndjson}；kbId 请求优先否则会话绑定；
 * Worker 用 {@code orch-*} 临时 conversationId，不写会话 chatId；
 * 附件正文只进本轮编排，Memory 只留文件名+原问；质量环缺省开启。
 */
package com.mychat.service.agent;
