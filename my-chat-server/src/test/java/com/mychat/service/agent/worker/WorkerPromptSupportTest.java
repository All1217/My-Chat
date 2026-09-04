package com.mychat.service.agent.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worker 消息拼装、会话截断与 kb 检索 query（不依赖 Spring 上下文）。
 */
class WorkerPromptSupportTest {

    @Test
    void workerUserMessagePrefixesHistoryAndTask() {
        String msg = WorkerPromptSupport.buildWorkerUserMessage(
                "USER: 写入 notes/todo.md\nASSISTANT: 已完成",
                "在该文件末尾追加 done");
        assertTrue(msg.contains("【会话上下文"));
        assertTrue(msg.contains("notes/todo.md"));
        assertTrue(msg.contains("【本步任务】"));
        assertTrue(msg.contains("在该文件末尾追加 done"));
    }

    @Test
    void workerUserMessageEmptyHistoryShowsPlaceholder() {
        String msg = WorkerPromptSupport.buildWorkerUserMessage(null, "列出根目录");
        assertTrue(msg.contains("（无）"));
        assertTrue(msg.contains("列出根目录"));
    }

    @Test
    void workerUserMessageTruncatesLongHistory() {
        String longHist = "x".repeat(WorkerPromptSupport.WORKER_DIALOGUE_HISTORY_MAX_CHARS + 50);
        String msg = WorkerPromptSupport.buildWorkerUserMessage(longHist, "task");
        assertTrue(msg.contains("…[会话摘要已截断]"));
        assertTrue(msg.length() < longHist.length() + 200);
    }

    @Test
    void truncateDialoguePrefersSummaryBlock() {
        String summary = "【会话摘要｜较早轮次压缩，可能有损】\n"
                + "用户约定 path=src/App.vue；结论：已改路由。\n\n";
        String recent = "【近期对话原文】\n"
                + "用户：" + "y".repeat(WorkerPromptSupport.WORKER_DIALOGUE_HISTORY_MAX_CHARS)
                + "\n助手：ok\n";
        String full = summary + recent;
        String truncated = WorkerPromptSupport.truncateDialogueForWorker(full);
        assertTrue(truncated.contains("path=src/App.vue"));
        assertTrue(truncated.contains("【会话摘要"));
        assertTrue(truncated.contains("…[近期原文已截断]")
                || truncated.length() <= WorkerPromptSupport.WORKER_DIALOGUE_HISTORY_MAX_CHARS + 40);
        assertTrue(truncated.indexOf("【会话摘要") < truncated.indexOf("【近期对话原文】"));
    }

    /** 检索 query 用用户原问，不含会话历史与 Worker 任务拼装。 */
    @Test
    void kbSearchQueryUsesUserGoalNotHistory() {
        String query = WorkerPromptSupport.kbSearchQuery(
                "这个知识库总体而言讲了些什么？",
                "总结整体内容框架和主要章节");
        assertEquals("这个知识库总体而言讲了些什么？", query);
        assertFalse(query.contains("会话上下文"));
        assertFalse(query.contains("Java"));
    }

    @Test
    void kbSearchQueryFallsBackToInstruction() {
        assertEquals("总结文档", WorkerPromptSupport.kbSearchQuery("  ", "总结文档"));
    }

    @Test
    void kbWorkerPromptKeepsHistoryOutOfSearchAndAppendsContext() {
        String workerMsg = WorkerPromptSupport.buildWorkerUserMessage(
                "USER: Java 三大特性是什么\nASSISTANT: 封装继承多态",
                "总结整体内容");
        String prompt = WorkerPromptSupport.buildKbWorkerUserPrompt(workerMsg, "【文档目录】Java基础.md");
        assertTrue(prompt.contains("封装继承多态"));
        assertTrue(prompt.contains("【检索上下文】"));
        assertTrue(prompt.contains("文档目录"));
        assertEquals("这个知识库总体而言讲了些什么？",
                WorkerPromptSupport.kbSearchQuery("这个知识库总体而言讲了些什么？", "总结整体内容"));
    }
}
