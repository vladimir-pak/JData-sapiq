package com.gpb.replication.sapiq.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gpb.replication.sapiq.dto.ColumnMeta;
import com.gpb.replication.sapiq.dto.ConstraintDedupKey;
import com.gpb.replication.sapiq.dto.ConstraintKey;
import com.gpb.replication.sapiq.dto.ConstraintMeta;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                String schemaName = trimToNull(rs.getString("schema_name"));
                long oid = rs.getLong("oid");

                if (schemaName == null) {
                    log.warn("Пропущена схема с пустым schema_name, oid={}", oid);
                    continue;
                }

                String fqn = getFqn(source.getServiceName(), dbName, schemaName);
                String parentFqn = fqn.substring(0, fqn.lastIndexOf("."));

                SchemaMetadata schema = new SchemaMetadata();
                schema.setId(new EntityId(oid, parentFqn));
                schema.setFqn(fqn);
                schema.setDbName(dbName);
                schema.setName(schemaName);
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

    private void tableReplication(SourceDbConnections source, String dbName) throws SQLException {
        String url = buildDbUrl(source.getUrl(), dbName);
        LocalDateTime currentTime = LocalDateTime.now();

        try (Connection conn = DriverManager.getConnection(url, source.getUsername(), source.getPassword())) {

            Map<Long, List<ColumnMeta>> columnsByTableId = loadColumns(conn);
            Map<Long, List<ConstraintMeta>> constraintsByTableId = loadConstraints(conn);

            List<TableMetadata> entities = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql.getTableSql());
                ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    long tableId = rs.getLong("table_id");

                    String schemaName = trimToNull(rs.getString("schema_name"));
                    String tableName = trimToNull(rs.getString("table_name"));
                    if (schemaName == null || tableName == null) {
                        log.warn("Пропущена таблица с пустым schema/table name: table_id={}, schema={}, table={}",
                                tableId, schemaName, tableName);
                        continue;
                    }

                    String tableType = trimToNull(rs.getString("table_type"));
                    String description = rs.getString("description");
                    String viewDefinition = rs.getString("view_definition");

                    String fqn = getFqn(
                            source.getServiceName(),
                            dbName,
                            schemaName,
                            tableName
                    );

                    String parentFqn = fqn.substring(0, fqn.lastIndexOf("."));
                    EntityId id = new EntityId(rs.getLong("oid"), parentFqn);

                    List<ColumnMeta> columns = columnsByTableId.getOrDefault(tableId, List.of());
                    List<ConstraintMeta> constraints = constraintsByTableId.getOrDefault(tableId, List.of());

                    JsonNode tableStructure = buildTableStructureJson(
                            source,
                            dbName,
                            schemaName,
                            tableName,
                            tableType,
                            viewDefinition,
                            columns,
                            constraints
                    );

                    String tableStructureJson = toJsonString(tableStructure);

                    TableMetadata table = new TableMetadata();
                    table.setId(id);
                    table.setFqn(fqn);
                    table.setDbName(dbName);
                    table.setSchemaName(schemaName);
                    table.setDescription(description);
                    table.setName(tableName);
                    table.setServiceName(source.getServiceName());
                    table.setCreatedAt(currentTime);
                    table.setData(tableStructure);

                    String hashString = fqn
                            + "|"
                            + (description == null ? "" : description)
                            + "|"
                            + tableStructureJson;

                    table.setHashData(DigestUtils.md5Hex(hashString));

                    entities.add(table);
                }
            }

            tableRep.saveAll(entities);

            log.info("Реплицировано {} таблиц SapIQ для DB {}", entities.size(), dbName);

        } catch (SQLException e) {
            log.error("Ошибка при получении таблиц для {}: {}",
                    source.getName(), e.getMessage(), e);
            throw e;
        }
    }

    private JsonNode buildTableStructureJson(
            SourceDbConnections source,
            String dbName,
            String schemaName,
            String tableName,
            String tableType,
            String viewDefinition,
            List<ColumnMeta> columns,
            List<ConstraintMeta> constraints
    ) {
        ObjectNode root = objectMapper.createObjectNode();

        String normalizedTableType = trimToNull(tableType);
        root.put("tableType", normalizedTableType == null ? "OTHER" : normalizedTableType);

        if (viewDefinition == null || viewDefinition.isBlank()) {
            root.putNull("viewDefinition");
        } else {
            root.put("viewDefinition", viewDefinition);
        }

        ArrayNode columnsArray = root.putArray("columns");

        String normalizedSchemaName = requireMetadataValue(schemaName, "schemaName", tableName);
        String normalizedTableName = requireMetadataValue(tableName, "tableName", schemaName);

        for (ColumnMeta column : columns) {
            ObjectNode columnNode = columnsArray.addObject();

            columnNode.put("ordinalPosition", column.ordinalPosition());
            
            String normalizedColumnName = requireMetadataValue(column.name(), "columnName", schemaName + "." + tableName);

            String columnFqn = getFqn(
                    source.getServiceName(),
                    dbName,
                    normalizedSchemaName,
                    normalizedTableName,
                    normalizedColumnName
            );

            columnNode.put("fqn", columnFqn);
            columnNode.put("name", normalizedColumnName);
            columnNode.put("dataType", column.dataType());
            columnNode.put("dataTypeDisplay", column.dataTypeDisplay());

            if (column.dataLength() == null) {
                columnNode.putNull("dataLength");
            } else {
                columnNode.put("dataLength", column.dataLength());
            }

            if (column.constraint() == null) {
                columnNode.putNull("constraint");
            } else {
                columnNode.put("constraint", column.constraint());
            }

            if (column.description() == null) {
                columnNode.putNull("description");
            } else {
                columnNode.put("description", column.description());
            }
        }

        ArrayNode constraintsArray = root.putArray("tableConstraints");

        for (ConstraintMeta constraint : constraints) {
            ObjectNode constraintNode = constraintsArray.addObject();

            ArrayNode constraintColumns = constraintNode.putArray("columns");
            for (String columnName : constraint.columns()) {
                String normalizedColumnName = trimToNull(columnName);
                if (normalizedColumnName != null) {
                    constraintColumns.add(normalizedColumnName);
                }
            }
            
            String constraintType = trimToNull(constraint.constraintType());
            constraintNode.put("constraintType", constraintType == null ? "OTHER" : constraintType);
        }

        return root;
    }

    private Map<Long, List<ColumnMeta>> loadColumns(Connection conn) throws SQLException {
        Map<Long, List<ColumnMeta>> columnsByTableId = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql.getColumnSql());
            ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                long tableId = rs.getLong("table_id");

                Integer dataLength = null;
                int rawLength = rs.getInt("data_length");
                if (!rs.wasNull()) {
                    dataLength = rawLength;
                }

                String columnName = trimToNull(rs.getString("column_name"));

                if (columnName == null) {
                    log.warn("Пропущена колонка с пустым column_name: table_id={}, ordinal_position={}",
                            tableId, rs.getInt("ordinal_position"));
                    continue;
                }

                ColumnMeta column = new ColumnMeta(
                        rs.getInt("ordinal_position"),
                        columnName,
                        trimToNull(rs.getString("data_type")),
                        trimToNull(rs.getString("data_type_display")),
                        dataLength,
                        trimToNull(rs.getString("column_constraint")),
                        rs.getString("description")
                );

                columnsByTableId
                        .computeIfAbsent(tableId, ignored -> new ArrayList<>())
                        .add(column);
            }
        }

        return columnsByTableId;
    }

    private Map<Long, List<ConstraintMeta>> loadConstraints(Connection conn) throws SQLException {
        Map<ConstraintKey, ConstraintMetaBuilder> builders = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql.getConstraintSql());
            ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                long tableId = rs.getLong("table_id");
                long indexId = rs.getLong("index_id");

                String constraintType = trimToNull(rs.getString("constraint_type"));
                String columnName = trimToNull(rs.getString("column_name"));

                ConstraintKey key = new ConstraintKey(tableId, indexId);

                ConstraintMetaBuilder builder = builders.computeIfAbsent(
                        key,
                        ignored -> new ConstraintMetaBuilder(tableId, constraintType)
                );

                if (columnName != null) {
                    builder.columns.add(columnName);
                }
            }
        }

        Map<ConstraintDedupKey, ConstraintMeta> deduplicated = new LinkedHashMap<>();

        for (ConstraintMetaBuilder builder : builders.values()) {
            String constraintType = trimToNull(builder.constraintType);
            if (constraintType == null) {
                constraintType = "OTHER";
            }

            List<String> columns = builder.columns.stream()
                    .map(this::trimToNull)
                    .filter(column -> column != null && !column.isBlank())
                    .toList();

            ConstraintDedupKey dedupKey = new ConstraintDedupKey(
                    builder.tableId,
                    constraintType,
                    columns
            );

            deduplicated.putIfAbsent(
                    dedupKey,
                    new ConstraintMeta(constraintType, columns)
            );
        }

        Map<Long, List<ConstraintMeta>> constraintsByTableId = new HashMap<>();

        for (Map.Entry<ConstraintDedupKey, ConstraintMeta> entry : deduplicated.entrySet()) {
            constraintsByTableId
                    .computeIfAbsent(entry.getKey().tableId(), ignored -> new ArrayList<>())
                    .add(entry.getValue());
        }

        return constraintsByTableId;
    }

    private static class ConstraintMetaBuilder {
        private final long tableId;
        private final String constraintType;
        private final List<String> columns = new ArrayList<>();

        private ConstraintMetaBuilder(long tableId, String constraintType) {
            this.tableId = tableId;
            this.constraintType = constraintType;
        }
    }

    private String toJsonString(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ошибка сериализации JsonNode", e);
        }
    }

    private String getFqn(String... names) {
        List<String> parts = new ArrayList<>();

        for (String name : names) {
            String normalized = trimToNull(name);
            if (normalized == null) {
                throw new IllegalArgumentException("FQN contains null/blank part");
            }
            parts.add(normalized);
        }

        return String.join(".", parts);
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireMetadataValue(String value, String fieldName, String context) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalStateException("Пустое metadata-поле " + fieldName + " для " + context);
        }
        return trimmed;
    }
}
