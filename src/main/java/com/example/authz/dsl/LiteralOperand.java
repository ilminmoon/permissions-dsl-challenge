package com.example.authz.dsl;

import com.fasterxml.jackson.annotation.JsonValue;

public record LiteralOperand(Object value) implements Operand {
    public LiteralOperand {
        if (!(value == null || value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw new IllegalArgumentException(
                    "LiteralOperand supports only string, number, boolean, or null values."
            );
        }
    }

    @JsonValue
    public Object jsonValue() {
        return value;
    }
}
