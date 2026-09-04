/**
 * 编排器按 action 调度的业务 Worker（知识库 / 文件 / 搜索 / 一般问答）。
 * <p>
 * 主路由 {@link com.mychat.service.agent.AgentOrchestratorService} 的 switch 调用；
 * Demo Routing 不共用本包，避免改调试 API 行为。
 */
package com.mychat.service.agent.worker;
