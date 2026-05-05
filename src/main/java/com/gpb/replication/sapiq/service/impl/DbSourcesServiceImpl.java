package com.gpb.replication.sapiq.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpb.replication.sapiq.dto.SourceDbConnections;
import com.gpb.replication.sapiq.service.DbSourcesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbSourcesServiceImpl implements DbSourcesService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceLoader resourceLoader;

    @Value("${sources.connections.file-path:classpath:db-connections.json}")
    private String filePath;

    @Override
    public List<SourceDbConnections> getDbConnections() {
        Resource resource = resourceLoader.getResource(filePath);

        log.debug("Serching file with connections...");
        log.debug("Filepath with connections: {}", filePath);

        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<List<SourceDbConnections>>() {});
        } catch (IOException e) {
            log.error(e.getMessage());
            return Collections.emptyList();
        }
    }
}