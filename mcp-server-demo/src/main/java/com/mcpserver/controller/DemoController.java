package com.mcpserver.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 极简测试控制器 — 确认服务可访问
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "code", 200,
                "message", "pong",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
