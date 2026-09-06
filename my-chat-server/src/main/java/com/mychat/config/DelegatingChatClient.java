package com.mychat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.function.Supplier;

/** 每次调用转到当前默认模型对应的真实 ChatClient，从而保留原 @Qualifier Bean 名。 */
public final class DelegatingChatClient implements ChatClient {

    private final Supplier<ChatClient> delegate;

    /** delegate 在每次 prompt/mutate 时解析，换默认后无需重建注入点。 */
    public DelegatingChatClient(Supplier<ChatClient> delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatClientRequestSpec prompt() {
        return delegate.get().prompt();
    }

    @Override
    public ChatClientRequestSpec prompt(String content) {
        return delegate.get().prompt(content);
    }

    @Override
    public ChatClientRequestSpec prompt(Prompt prompt) {
        return delegate.get().prompt(prompt);
    }

    @Override
    public Builder mutate() {
        return delegate.get().mutate();
    }
}
