/**
 * Agent 调试旁路（非产品主聊天）。
 * <p>
 * {@link com.mychat.service.agent.demo.AgentRoutingService} 单次 classify + switch；
 * {@link com.mychat.service.agent.demo.AgentRouteDemoStreamService} 为 {@code POST /ai/agent/route} 的 NDJSON 管道。
 * 主聊天走 {@link com.mychat.service.agent.AgentOrchestratorService}，不经过本包。
 */
package com.mychat.service.agent.demo;
