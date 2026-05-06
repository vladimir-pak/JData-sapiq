package com.gpb.replication.sapiq.dto;

import java.util.List;

public record ConstraintMeta(
        String constraintType,
        List<String> columns
) {
}
