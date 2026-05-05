package com.gpb.replication.sapiq.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "table_metadata", schema = "sapiq_metadata")
@EntityListeners(AuditingEntityListener.class)
public class TableMetadata {
    @EmbeddedId
    private EntityId id;
    
    @Column(name = "fqn")
    private String fqn;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "description")
    private String description;

    @Column(name = "name")
    private String name;

    @Column(name = "service_name")
    private String serviceName;

    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    // @Convert(converter = JsonNodeConverter.class)
    private JsonNode data;

    @Column(name = "hash_data")
    private String hashData;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

