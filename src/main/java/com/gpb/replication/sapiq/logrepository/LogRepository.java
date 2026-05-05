package com.gpb.replication.sapiq.logrepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gpb.replication.sapiq.properties.LogsDatabaseProperties;
import com.gpb.replication.sapiq.service.CefLogFileService;

import java.sql.Timestamp;


@Repository
@Slf4j
public class LogRepository {
    private final JdbcTemplate logsJdbcTemplate;
    private final CefLogFileService cefLogFileService;
    private final LogsDatabaseProperties logsDatabaseProperties;

    public LogRepository(
            @Qualifier("logsJdbcTemplate") JdbcTemplate logsJdbcTemplate,
            CefLogFileService cefLogFileService,
            LogsDatabaseProperties logsDatabaseProperties
    ) {
        this.logsJdbcTemplate = logsJdbcTemplate;
        this.cefLogFileService = cefLogFileService;
        this.logsDatabaseProperties = logsDatabaseProperties;
    }

    public Log findLatestByType(String type, String host) {
        String table = logsDatabaseProperties.getTable().trim();
        String sql = String.format("""
                SELECT l.id, l.type, l.log, l.created
                FROM %s l
                WHERE l.type = ?
                  AND l.log LIKE ?
                ORDER BY l.created DESC
                LIMIT 1
                """, table);

        return logsJdbcTemplate.query(sql, new Object[]{type, "%" + host + "%"}, rs -> {
            if (rs.next()) {
                Log logEntity = new Log();
                logEntity.setId(rs.getObject("id", Integer.class));
                logEntity.setType(rs.getString("type"));
                logEntity.setLog(rs.getString("log"));
                logEntity.setCreated(rs.getTimestamp("created").toLocalDateTime());
                return logEntity;
            }
            return null;
        });
    }

    public void save(Log logEntity) {
        String table = logsDatabaseProperties.getTable().trim();
        String sql = String.format("""
                INSERT INTO %s (created, log, type)
                VALUES (?, ?, ?)
                """, table);

        logsJdbcTemplate.update(sql,
                Timestamp.valueOf(logEntity.getCreated()),
                logEntity.getLog(),
                logEntity.getType());

        cefLogFileService.writeToFile(logEntity.getCreated(), logEntity.getLog());
    }
}
