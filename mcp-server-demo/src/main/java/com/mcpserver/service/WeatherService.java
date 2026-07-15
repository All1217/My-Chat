package com.mcpserver.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * Demo 天气 MCP 工具：对外暴露 get_weather，供 My-Chat Client 通过 MCP 协议发现与调用。
 */
@Service
public class WeatherService {

    @McpTool(
            name = "get_weather",
            description = "查询指定城市的天气（摄氏度），用于回答用户关于天气的问题"
    )
    public String getWeather(
            @McpToolParam(description = "城市名称，如 北京、上海", required = true) String city) {
        // Demo 假数据：真实环境可替换为第三方天气 API
        return """
                {"city":"%s","temperature_celsius":26,"condition":"晴","humidity_percent":45}
                """.formatted(city);
    }
}
