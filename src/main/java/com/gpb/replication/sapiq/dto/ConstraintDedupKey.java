package com.gpb.replication.sapiq.dto;

import java.util.List;

public record ConstraintDedupKey (
        long tableId,
        String constraintType,
        List<String> columns
) {
}