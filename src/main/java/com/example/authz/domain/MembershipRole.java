package com.example.authz.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MembershipRole implements JsonTokenEnum {
    VIEWER("viewer"),
    EDITOR("editor"),
    ADMIN("admin");

    private final String jsonValue;

    MembershipRole(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static MembershipRole fromJson(String value) {
        return EnumJson.fromJson(MembershipRole.class, value, "membership role");
    }
}
