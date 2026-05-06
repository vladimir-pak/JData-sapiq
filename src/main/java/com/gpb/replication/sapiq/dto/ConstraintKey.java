package com.gpb.replication.sapiq.dto;

public record ConstraintKey(
        long tableId,
        long indexId
) {
}
