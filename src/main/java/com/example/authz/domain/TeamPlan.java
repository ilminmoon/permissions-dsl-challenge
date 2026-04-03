package com.example.authz.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TeamPlan implements JsonTokenEnum {
    FREE("free"),
    PRO("pro"),
    ENTERPRISE("enterprise");

    private final String jsonValue;

    TeamPlan(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static TeamPlan fromJson(String value) {
        return EnumJson.fromJson(TeamPlan.class, value, "team plan");
    }
}
