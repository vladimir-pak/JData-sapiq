package com.gpb.replication.sapiq.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "logs-database")
@RequiredArgsConstructor
@Data
public class LogsDatabaseProperties {
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private String table;

    @PostConstruct
    public void validate() {
        if (enabled) {
            if (table == null || table.trim().isEmpty()) {
                throw new IllegalStateException("""
                    Конфигурация logs-database.table не задана!
                    Укажи полное имя таблицы для записи логов в конфигурации
                    """);
            }
        }
    }
}
