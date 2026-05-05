package com.gpb.replication.sapiq.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.gpb.replication.sapiq.properties.LogsDatabaseProperties;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class LogsDbConfig {
    private final LogsDatabaseProperties logsDatabaseProperties;

    @Bean(name = "logsDataSource")
    public DataSource logsDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(logsDatabaseProperties.getUrl());
        dataSource.setUsername(logsDatabaseProperties.getUsername());
        dataSource.setPassword(logsDatabaseProperties.getPassword());
        return dataSource;
    }

    @Bean(name = "logsJdbcTemplate")
    public JdbcTemplate logsJdbcTemplate(@Qualifier("logsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
