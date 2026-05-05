package com.gpb.replication.sapiq.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gpb.replication.sapiq.logrepository.LogPartitionRepository;
import com.gpb.replication.sapiq.service.CefLogFileService;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogScheduler {
    private final SvoiCustomLogger svoiCustomLogger;
    private final LogPartitionRepository logPartitionRepository;
    private final CefLogFileService cefLogger;


    @Scheduled(cron = "${logs-database.task-create-partition}")
    public void createPartition() {
        logPartitionRepository.createTodayPartition();
    }

    @Scheduled(cron = "${clean-database-logs.task-cleaner-schedule}")
    public void cleanPartition() {
        logPartitionRepository.dropOldPartitions();
    }

    @Scheduled(cron = "${clean-database-logs.task-cleaner-schedule}")
    public void cleanupOldLogs() {
        cefLogger.rotateLogFile();
        cefLogger.cleanupOldLogs();
        svoiCustomLogger.sendInternal(
                "cleanLogs",
                "Clean Log Files",
                "Cleaned old log files",
                SvoiSeverityEnum.ONE
        );
    }
}
