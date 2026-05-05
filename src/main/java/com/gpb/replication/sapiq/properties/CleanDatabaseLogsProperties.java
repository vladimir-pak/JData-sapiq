package com.gpb.replication.sapiq.properties;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "clean-database-logs")
@RequiredArgsConstructor
@Data
public class CleanDatabaseLogsProperties {
    private String taskCleanerSchedule;
    private int cleanPeriod;
}

