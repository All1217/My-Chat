package com.mcpserver.config;

import com.mcpserver.catalog.CatalogDialect;
import com.mcpserver.catalog.MysqlCatalogDialect;
import com.mcpserver.catalog.PostgresCatalogDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** 按 app.database.dialect 装配目录方言。 */
@Configuration
public class CatalogDialectConfiguration {

    /** 单一 CatalogDialect Bean。 */
    @Bean
    public CatalogDialect catalogDialect(DatabaseMcpProperties properties, JdbcTemplate jdbcTemplate) {
        if (properties.isMysql()) {
            return new MysqlCatalogDialect(jdbcTemplate);
        }
        return new PostgresCatalogDialect(jdbcTemplate);
    }
}
