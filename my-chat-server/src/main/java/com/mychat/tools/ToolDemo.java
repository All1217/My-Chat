package com.mychat.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class ToolDemo {
    @Tool(description = "获取当前时间")
    public String getCurrentTime(@ToolParam(description = "测试查询的条件，必须传数字1", required = true) int testParam) {
        if (testParam != 1) {
            return "获取失败！";
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
