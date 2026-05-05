package com.gpb.replication.sapiq.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gpb.replication.sapiq.dto.ReplicationRequestDto;
import com.gpb.replication.sapiq.log.SvoiCustomLogger;
import com.gpb.replication.sapiq.service.ReplicationService;

@RestController
@RequestMapping("/api/replication")
@RequiredArgsConstructor
@Tag(name = "Replication", description = "API запуска репликации")
public class ReplicationController {
    private final ReplicationService replicationService;
    private final SvoiCustomLogger logger;

    @PostMapping("/start")
    @Operation(summary = "Запуск репликации по наименованию сервиса")
    public ResponseEntity<String> startReplication(@RequestBody ReplicationRequestDto dto, HttpServletRequest httpServletRequest) {
        try {
            logger.logApiCall(httpServletRequest, "startReplicationSapIQ", dto);
            replicationService.startReplicationAsync(dto.getServiceName());
            return ResponseEntity.ok(String.format("Replication for %s started", dto.getServiceName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to start replication: " + e.getMessage());
        }
    }
}
