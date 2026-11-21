package com.mcpserver;

import com.mcpserver.config.DatabaseMcpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 数据库元数据 / SQL MCP 服务启动类（PostgreSQL 或 MySQL）。 */
@SpringBootApplication
@EnableConfigurationProperties(DatabaseMcpProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
