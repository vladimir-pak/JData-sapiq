package com.gpb.replication.sapiq.logrepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gpb.replication.sapiq.log.SvoiCustomLogger;
import com.gpb.replication.sapiq.log.SvoiSeverityEnum;
import com.gpb.replication.sapiq.properties.CleanDatabaseLogsProperties;
import com.gpb.replication.sapiq.properties.LogsDatabaseProperties;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Repository
public class LogPartitionRepository {
    private final JdbcTemplate logsJdbcTemplate;
    private final CleanDatabaseLogsProperties cleanDatabaseLogs;
    private final LogsDatabaseProperties logsDatabaseProperties;
    private final SvoiCustomLogger svoiCustomLogger;

    public LogPartitionRepository(
            @Qualifier("logsJdbcTemplate") JdbcTemplate logsJdbcTemplate,
            CleanDatabaseLogsProperties cleanDatabaseLogs,
            LogsDatabaseProperties logsDatabaseProperties,
            SvoiCustomLogger svoiCustomLogger
    ) {
        this.logsJdbcTemplate = logsJdbcTemplate;
        this.cleanDatabaseLogs = cleanDatabaseLogs;
        this.logsDatabaseProperties = logsDatabaseProperties;
        this.svoiCustomLogger = svoiCustomLogger;
    }

    public void createTodayPartition() {
        if (!logsDatabaseProperties.isEnabled()) {
            return;
        }

        String table = logsDatabaseProperties.getTable().trim();
        LocalDate currentDate = LocalDate.now();
        String partitionName = String.format("%s_%s",
                table,
                currentDate.format(DateTimeFormatter.ofPattern("yyyy_MM_dd")));

        String sql = String.format("""
                        CREATE TABLE IF NOT EXISTS %s
                        PARTITION OF %s
                        FOR VALUES FROM ('%s 00:00:00') TO ('%s 00:00:00')
                        """,
                partitionName,
                table,
                currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                currentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );

        logsJdbcTemplate.execute(sql);
    }

    public void dropOldPartitions() {
        if (!logsDatabaseProperties.isEnabled()) {
            return;
        }
        LocalDate dateToDelete = LocalDate.now().minusDays(cleanDatabaseLogs.getCleanPeriod());

        String fullTableName = logsDatabaseProperties.getTable().trim();
        String table = fullTableName;
        String schema = "public";
        if (fullTableName.contains(".")) {
            table = fullTableName.split("\\.")[1];
            schema = fullTableName.split("\\.")[0];
        }

        String sqlGetPartitions = String.format("""
                SELECT 
                    child.relname AS partition_name
                FROM pg_inherits
                    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
                    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
                    JOIN pg_namespace nmsp_parent ON nmsp_parent.oid = parent.relnamespace
                    JOIN pg_namespace nmsp_child ON nmsp_child.oid = child.relnamespace
                WHERE parent.relname = '%s'
                """, table);
        
        List<String> partitions = logsJdbcTemplate.queryForList(sqlGetPartitions, String.class);

        for (String partName : partitions) {
            String dateStr = partName.replace(table + "_", "");
            String formattedDate = dateStr.replace("_", "-");
            
            LocalDate partDate = LocalDate.parse(formattedDate);
            if (partDate.isBefore(dateToDelete)) {
                String sql = String.format("DROP TABLE IF EXISTS %s.%s", schema, partName);
                logsJdbcTemplate.execute(sql);
                svoiCustomLogger.sendInternal("cleanLogs", "Clean Logs", "Dropped partition " + partName, SvoiSeverityEnum.ONE);
            }
        }
    }
}