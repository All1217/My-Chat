/**
 * Agent 侧 LLM 决策 prompt（结构化输出），不是通用基础设施。
 * <p>
 * {@link com.mychat.service.agent.workflow.OrchestratorWorkflow} 主路多步决策；
 * {@link com.mychat.service.agent.workflow.RoutingWorkflow} 仅 Demo 单次分类；
 * {@link com.mychat.service.agent.workflow.EvaluatorOptimizerWorkflow} 质量环评价。
 * 由对应 Service 构造器 {@code new}，不是 Spring Bean。
 */
package com.mychat.service.agent.workflow;
