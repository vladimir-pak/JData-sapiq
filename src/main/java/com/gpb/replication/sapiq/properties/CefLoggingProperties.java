package com.gpb.replication.sapiq.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "logging.cef")
public class CefLoggingProperties {
    private String path = "logs/cef";
    private int retentionDays = 30;
}
