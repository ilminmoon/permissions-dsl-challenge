package com.example.authz.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ComparisonOperator {
    EQ("eq", "="),
    NE("ne", "<>"),
    GT("gt", ">"),
    GTE("gte", ">="),
    LT("lt", "<"),
    LTE("lte", "<=");

    private final String jsonToken;
    private final String symbolAlias;

    ComparisonOperator(String jsonToken, String symbolAlias) {
        this.jsonToken = jsonToken;
        this.symbolAlias = symbolAlias;
    }

    @JsonValue
    public String jsonToken() {
        return jsonToken;
    }

    @JsonCreator
    public static ComparisonOperator fromJson(String token) {
        return Arrays.stream(values())
                .filter(operator -> operator.jsonToken.equalsIgnoreCase(token) || operator.symbolAlias.equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported comparison operator: " + token));
    }
}
