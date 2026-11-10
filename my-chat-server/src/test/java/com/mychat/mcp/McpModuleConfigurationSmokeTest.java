package com.mychat.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 模块上下文冒烟：关闭总开关或无 SMITHERY_API_KEY 时配置类可安全加载。
 */
class McpModuleConfigurationSmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpModuleConfiguration.class));

    @Test
    void disabledModuleDoesNotRegisterBridge() {
        runner.withPropertyValues(
                        "app.mcp.enabled=false",
                        "spring.ai.mcp.client.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(McpToolBridge.class);
                    assertThat(ctx).hasNotFailed();
                });
    }

    @Test
    void enabledWithoutSmitheryKeyStillLoads() {
        runner.withPropertyValues(
                        "app.mcp.enabled=true",
                        "app.mcp.smithery.api-key=",
                        "app.mcp.smithery.enabled=true",
                        "app.mcp.weather.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(McpToolBridge.class);
                    assertThat(ctx).hasSingleBean(McpProperties.class);
                    McpProperties props = ctx.getBean(McpProperties.class);
                    assertThat(props.getSmithery().getApiKey()).isNullOrEmpty();
                });
    }
}
