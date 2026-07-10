package com.mychat.config;

import com.mychat.tools.ShellTool;
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

    /** 普通对话：支持 Shell 工具调用 */
    @Bean
    public ChatClient toolChatClient(OpenAiChatModel model, ChatMemory chatMemory, ShellTool shellTool) {
        return ChatClient.builder(model)
                .defaultSystem("请根据用户提问灵活回应。需要查看项目文件或执行只读命令时，可使用可用工具。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(shellTool)
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
