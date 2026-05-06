package com.gpb.replication.sapiq.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpb.replication.sapiq.dto.SourceDbConnections;
import com.gpb.replication.sapiq.log.SvoiCustomLogger;
import com.gpb.replication.sapiq.log.SvoiSeverityEnum;
import com.gpb.replication.sapiq.model.DatabaseMetadata;
import com.gpb.replication.sapiq.model.EntityId;
import com.gpb.replication.sapiq.model.SchemaMetadata;
import com.gpb.replication.sapiq.model.TableMetadata;
import com.gpb.replication.sapiq.properties.SqlTemplates;
import com.gpb.replication.sapiq.repository.DatabaseMetadataRepository;
import com.gpb.replication.sapiq.repository.SchemaMetadataRepository;
import com.gpb.replication.sapiq.repository.TableMetadataRepository;
import com.gpb.replication.sapiq.service.DbSourcesService;
import com.gpb.replication.sapiq.service.ReplicationService;
import com.gpb.replication.sapiq.service.VaultSecretService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicationServiceImpl implements ReplicationService {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DbSourcesService dbSourcesService;
    private final SvoiCustomLogger svoiCustomLogger;
    private final DatabaseMetadataRepository databaseRep;
    private final SchemaMetadataRepository schemaRep;
    private final TableMetadataRepository tableRep;
    private final VaultSecretService vault;
    private final SqlTemplates sql;

    @Async
    public void startReplicationAsync(String serviceName) {
        startReplication(serviceName);
    }

    public void startReplication(String serviceName) {
        SourceDbConnections source;

        if (vault.isVaultConnected() && vault.serviceSecretsExist(serviceName)) {
            source = vault.getServiceSecrets(serviceName);
        } else {
            source = dbSourcesService.getDbConnections()
                    .stream()
                    .filter(s -> s.getName().equals(serviceName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Не найден сервис: " + serviceName));
        }

        truncateTables(serviceName);

        try {
            svoiCustomLogger.logConnectToSource(
                    source.getHostFromUrl(),
                    source.getPortFromUrl(),
                    source.getDbType(),
                    source.getUsername()
            );

            String dbFqn = String.format("%s.%s", source.getServiceName(), source.getSchema());
            DatabaseMetadata db = new DatabaseMetadata();
                db.setId(new EntityId(1L, source.getServiceName()));
                db.setFqn(dbFqn);
                db.setServiceName(source.getServiceName());
                db.setName(source.getSchema());
                db.setCreatedAt(LocalDateTime.now());
                db.setHashData(DigestUtils.md5Hex(dbFqn));

            databaseRep.save(db);

            try {
                schemaReplication(source, source.getSchema());
                tableReplication(source, source.getSchema());
            } catch (SQLException e) {
                svoiCustomLogger.logDbConnectionError(
                        source.getHostFromUrl(),
                        source.getPortFromUrl(),
                        source.getDbType(),
                        source.getUsername(),
                        e
                );
            }

            log.info("Репликация завершена успешно для источника {}", serviceName);
            svoiCustomLogger.sendInternal(
                    "replicationJob",
                    "Replication Finished",
                    String.format("Replicated source: [%s];",
                            serviceName),
                    SvoiSeverityEnum.ONE
            );

        } catch (Exception e) {
            svoiCustomLogger.logDbConnectionError(
                    source.getHostFromUrl(),
                    source.getPortFromUrl(),
                    source.getDbType(),
                    source.getUsername(),
                    e
            );

            log.error("Ошибка при подключении к источнику {}", source.getName(), e);
            throw new RuntimeException("Ошибка при подключении к источнику: " + source.getName(), e);
        }
    }

    private void truncateTables(String serviceName) {
        svoiCustomLogger.sendInternal(
                "replicationDataReset",
                "replication Data Reset",
                "serviceName=" + serviceName,
                SvoiSeverityEnum.ONE
        );

        databaseRep.deleteByServiceName(serviceName);
        schemaRep.deleteByServiceName(serviceName);
        tableRep.deleteByServiceName(serviceName);
        log.info("Truncated metadata tables for service={}", serviceName);
    }

    private void schemaReplication(SourceDbConnections source, String dbName) throws SQLException {
        List<SchemaMetadata> schemas = new ArrayList<>();
        String url = buildDbUrl(source.getUrl(), dbName);
        log.info("URL of database: {}", url);
        LocalDateTime currentTime = LocalDateTime.now();

        try (Connection conn = DriverManager.getConnection(url, source.getUsername(), source.getPassword());
             PreparedStatement stmt = conn.prepareStatement(sql.getSchemaSql());
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String schemaName = rs.getString("schema_name");
                long oid = rs.getLong("oid");
                String fqn = getFqn(List.of(source.getServiceName(), dbName, schemaName));
                String parentFqn = fqn.substring(0, fqn.lastIndexOf("."));

                SchemaMetadata schema = new SchemaMetadata();
                schema.setId(new EntityId(oid, parentFqn));
                schema.setFqn(fqn);
                schema.setDbName(dbName);
                schema.setName(rs.getString("schema_name"));
                schema.setServiceName(source.getServiceName());
                schema.setCreatedAt(currentTime);
                schema.setHashData(DigestUtils.md5Hex(fqn));

                schemas.add(schema);
            }
            schemaRep.saveAll(schemas);
            log.info("Реплицировано {} схем SapIQ для DB {}", schemas.size(), dbName);
        } catch (SQLException e) {
            log.error("Ошибка при подключении и получении схем SapIQ для {}: {}", 
                    dbName, e.getMessage(), e);
            throw e;
        }
    }

    private void tableReplication(SourceDbConnections source, String dbName) {
        String url = buildDbUrl(source.getUrl(), dbName);
        LocalDateTime currentTime = LocalDateTime.now();

        try (Connection conn = DriverManager.getConnection(url, source.getUsername(), source.getPassword());
             PreparedStatement stmt = conn.prepareStatement(sql.getTableSql());
             ResultSet rs = stmt.executeQuery()) {

            List<TableMetadata> entities = new ArrayList<>();

            while (rs.next()) {
                try {
                    TableMetadata table = new TableMetadata();
                    String fqn = getFqn(List.of(source.getServiceName(), dbName, rs.getString("schema_name"), rs.getString("table_name")));
                    String parentFqn = fqn.substring(0, fqn.lastIndexOf("."));
                    EntityId id = new EntityId(rs.getLong("oid"), parentFqn);

                    table.setId(id);
                    table.setFqn(fqn);
                    table.setDbName(dbName);
                    table.setSchemaName(rs.getString("schema_name"));
                    table.setDescription(rs.getString("description"));
                    table.setName(rs.getString("table_name"));
                    table.setServiceName(source.getServiceName());
                    table.setCreatedAt(currentTime);

                    String jsonString = rs.getString("table_structure");
                    String hashString = fqn + rs.getString("description");
                    table.setHashData(DigestUtils.md5Hex(jsonString + hashString));

                    JsonNode columnsNode = objectMapper.readTree(jsonString);
                    JsonNode jsonNode = objectMapper.valueToTree(columnsNode);
                    table.setData(jsonNode);

                    entities.add(table);
                } catch (JsonProcessingException e) {
                    log.error("Ошибка при преобразовании JSON для таблицы {}: {}",
                            rs.getString("table_name"), e.getMessage(), e);
                }
            }
            tableRep.saveAll(entities);

        } catch (SQLException e) {
            log.error("Ошибка при получении таблиц для {}: {}", 
                    source.getName(), e.getMessage(), e);
        }
    }

    private String getFqn(List<String> names) {
        return String.join(".", names);
    }

    private String buildDbUrl(String originalUrl, String dbName) {
        String url = originalUrl.trim();
        if (!url.matches(".*/[^/]+$")) {
            if (!url.endsWith("/")) url += "/";
            url += dbName;
        } else {
            url = url.replaceFirst("/[^/]+$", "/" + dbName);
        }
        return url;
    }
}
