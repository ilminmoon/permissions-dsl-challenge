package com.example.authz.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Permission {
    CAN_VIEW("can_view"),
    CAN_EDIT("can_edit"),
    CAN_DELETE("can_delete"),
    CAN_SHARE("can_share");

    private final String dslValue;

    Permission(String dslValue) {
        this.dslValue = dslValue;
    }

    @JsonValue
    public String dslValue() {
        return dslValue;
    }

    @JsonCreator
    public static Permission fromJson(String value) {
        return Arrays.stream(values())
                .filter(permission -> permission.dslValue.equalsIgnoreCase(value) || permission.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported permission: " + value));
    }
}
