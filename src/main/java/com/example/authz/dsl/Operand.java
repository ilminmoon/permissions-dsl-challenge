package com.example.authz.dsl;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = OperandDeserializer.class)
public sealed interface Operand permits LiteralOperand, FieldRefOperand {
}
