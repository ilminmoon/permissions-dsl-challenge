package com.example.authz.dsl;

import java.util.Objects;

public record FieldRefOperand(String ref) implements Operand {
    public FieldRefOperand {
        Objects.requireNonNull(ref, "ref must not be null");
    }
}
