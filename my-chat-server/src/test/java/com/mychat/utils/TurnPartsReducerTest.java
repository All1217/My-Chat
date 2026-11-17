package com.mychat.utils;

import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import com.mychat.common.ChatStreamEvent;
import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.vo.MessagePartVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * step.args.citations 随 parts 落库，供刷新后回放引用。
 */
class TurnPartsReducerTest {

    /** retrieve_kb 的 citations 原样进入 parts.args。 */
    @Test
    void stepCitationsLandInPartsArgs() {
        KnowledgeRetrieveHit hit = new KnowledgeRetrieveHit();
        hit.setFilename("Java基础.md");
        hit.setDocumentId("doc-1");
        hit.setKind(KnowledgeRetrieveHit.KIND_CHUNK);
        ChatStreamEvent event = ChatStreamEvent.step(
                "t1",
                new AtomicInteger(0),
                1,
                "retrieve_kb",
                "检索知识库",
                "回答封装",
                "观察预览",
                List.of(hit),
                "vector");

        TurnPartsReducer.TurnSnapshot snap = TurnPartsReducer.reduce(List.of(event), false);
        assertEquals(1, snap.parts().size());
        MessagePartVO part = snap.parts().get(0);
        assertEquals("step", part.getType());
        assertEquals("retrieve_kb", part.getName());
        assertInstanceOf(Map.class, part.getArgs());
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) part.getArgs();
        assertTrue(args.containsKey("citations"));
        assertEquals("vector", args.get("kbScope"));
        @SuppressWarnings("unchecked")
        List<KnowledgeRetrieveHit> citations = (List<KnowledgeRetrieveHit>) args.get("citations");
        assertEquals(1, citations.size());
        assertEquals("Java基础.md", citations.get(0).getFilename());
        assertEquals("doc-1", citations.get(0).getDocumentId());
    }

    /** 无命中时 args 不含 citations 键。 */
    @Test
    void stepWithoutCitationsOmitsKey() {
        ChatStreamEvent event = ChatStreamEvent.step(
                "t1", new AtomicInteger(0), 1, "general", "闲聊", "问好", "你好");
        TurnPartsReducer.TurnSnapshot snap = TurnPartsReducer.reduce(List.of(event), false);
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) snap.parts().get(0).getArgs();
        assertTrue(!args.containsKey("citations"));
        assertTrue(!args.containsKey("kbScope"));
        assertEquals(1, args.get("stepIndex"));
    }

    /** 相同 stepId 的开始/完成事件原位更新，不追加重复项。 */
    @Test
    void sameStepIdUpdatesInPlace() {
        AtomicInteger sequence = new AtomicInteger();
        PublicProgressEvent running = new PublicProgressEvent(
                "run-1", "step-stable", null,
                PublicProgressEvent.Type.STEP, AgentStepResult.Status.RUNNING,
                "联网搜索", "", 0, Map.of("actionId", "search"));
        PublicProgressEvent completed = new PublicProgressEvent(
                "run-1", "step-stable", null,
                PublicProgressEvent.Type.STEP, AgentStepResult.Status.SUCCEEDED,
                "联网搜索", "完成", 30, Map.of("actionId", "search"));

        TurnPartsReducer.TurnSnapshot snapshot = TurnPartsReducer.reduce(List.of(
                ChatStreamEvent.publicProgress("turn-1", sequence, running),
                ChatStreamEvent.publicProgress("turn-1", sequence, completed)), false);

        assertEquals(1, snapshot.parts().size());
        assertEquals("step-stable", snapshot.parts().get(0).getId());
        assertEquals("done", snapshot.parts().get(0).getStatus());
        assertEquals(30L, snapshot.parts().get(0).getElapsedMs());
    }
}
