package com.gpb.replication.sapiq.log;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SvoiOutcomeEnum {
    SUCCESS("success"),
    FAIL("fail");

    private final String value;
}
