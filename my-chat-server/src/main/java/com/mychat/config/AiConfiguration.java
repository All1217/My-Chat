package com.mychat.config;

import com.mychat.tools.FileTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {
    @Autowired
    JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(64)
                .build();
    }

    /** 普通对话：支持文件工具调用 */
    @Bean
    public ChatClient toolChatClient(OpenAiChatModel model, ChatMemory chatMemory, FileTools fileTools) {
        return ChatClient.builder(model)
                .defaultSystem("""
                        所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，务必积极调用可用工具实际执行。
                        不能在回复中假装执行了文件操作。
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(fileTools)
                .build();
    }

    /** 知识库 RAG：不注册任何工具，强制基于检索上下文回答 */
    @Bean
    public ChatClient ragChatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem("""
                        你是知识库问答助手。请严格基于系统提供的检索上下文回答问题。
                        如果上下文中没有足够信息，请明确告知用户"知识库中未找到相关信息"，不要编造。
                        不要尝试调用任何外部工具或命令。
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
