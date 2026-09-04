/**
 * 写盘后的任务内质量环（Evaluator-Optimizer）。
 * <p>
 * {@link com.mychat.service.agent.quality.AgentEvaluatorOptimizerService} 由主聊天管道在解析到 write 路径时调用；
 * Demo {@code POST /ai/agent/evaluate-optimize} 也可直接触发。评价 prompt 见
 * {@link com.mychat.service.agent.workflow.EvaluatorOptimizerWorkflow}。
 */
package com.mychat.service.agent.quality;
