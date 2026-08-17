

# 主聊天总体时序图

从`my-chat-server/src/main/java/com/mychat/controller/ChatController.java`的`/ai/normalChat/chat`出发的时序图（成功路径；质量环在可解析 write 路径时才跑）：

![](../资源/Assets/main-chat-sequence.png)

要点：`sink` 边推 NDJSON 给前端；`accumulated` 回合结束写 Memory 与 `chat_assistant_turns`；编排本身是同步循环，最终答复再 token 流式。
