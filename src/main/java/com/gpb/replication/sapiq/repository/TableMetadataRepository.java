package com.gpb.replication.sapiq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gpb.replication.sapiq.model.EntityId;
import com.gpb.replication.sapiq.model.TableMetadata;

import jakarta.transaction.Transactional;

public interface TableMetadataRepository extends JpaRepository<TableMetadata, EntityId> {
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sapiq_metadata.table_metadata WHERE service_name = :service", nativeQuery = true)
    void deleteByServiceName(@Param("service") String service);
}
