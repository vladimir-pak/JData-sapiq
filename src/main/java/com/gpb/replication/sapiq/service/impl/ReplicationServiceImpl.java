package com.gpb.replication.sapiq.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gpb.replication.sapiq.dto.ColumnMeta;
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

                    String schemaName = rs.getString("schema_name");
                    String tableName = rs.getString("table_name");
                    String tableType = rs.getString("table_type");
                    String description = rs.getString("description");
                    String viewDefinition = rs.getString("view_definition");

                    String fqn = getFqn(List.of(
                            source.getServiceName(),
                            dbName,
                            schemaName,
                            tableName
                    ));

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

        root.put("tableType", tableType);

        if (viewDefinition == null || viewDefinition.isBlank()) {
            root.putNull("viewDefinition");
        } else {
            root.put("viewDefinition", viewDefinition);
        }

        ArrayNode columnsArray = root.putArray("columns");

        for (ColumnMeta column : columns) {
            ObjectNode columnNode = columnsArray.addObject();

            columnNode.put("ordinalPosition", column.ordinalPosition());

            String columnFqn = getFqn(List.of(
                    source.getServiceName(),
                    dbName,
                    schemaName,
                    tableName,
                    column.name()
            ));

            columnNode.put("fqn", columnFqn);
            columnNode.put("name", column.name());
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
                constraintColumns.add(columnName);
            }

            constraintNode.put("constraintType", constraint.constraintType());
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

                ColumnMeta column = new ColumnMeta(
                        rs.getInt("ordinal_position"),
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getString("data_type_display"),
                        dataLength,
                        rs.getString("column_constraint"),
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
                String constraintType = rs.getString("constraint_type");
                String columnName = rs.getString("column_name");

                ConstraintKey key = new ConstraintKey(tableId, indexId);

                ConstraintMetaBuilder builder = builders.computeIfAbsent(
                        key,
                        ignored -> new ConstraintMetaBuilder(tableId, constraintType)
                );

                builder.columns.add(columnName);
            }
        }

        Map<Long, List<ConstraintMeta>> constraintsByTableId = new HashMap<>();

        for (ConstraintMetaBuilder builder : builders.values()) {
            ConstraintMeta constraint = new ConstraintMeta(
                    builder.constraintType,
                    List.copyOf(builder.columns)
            );

            constraintsByTableId
                    .computeIfAbsent(builder.tableId, ignored -> new ArrayList<>())
                    .add(constraint);
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
