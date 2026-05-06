package com.gpb.replication.sapiq.dto;

public record ColumnMeta (
        int ordinalPosition,
        String name,
        String dataType,
        String dataTypeDisplay,
        Integer dataLength,
        String constraint,
        String description
) {
}
