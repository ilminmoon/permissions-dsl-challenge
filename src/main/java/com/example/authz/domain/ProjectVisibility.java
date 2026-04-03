package com.example.authz.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProjectVisibility implements JsonTokenEnum {
    PRIVATE("private"),
    PUBLIC("public");

    private final String jsonValue;

    ProjectVisibility(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static ProjectVisibility fromJson(String value) {
        return EnumJson.fromJson(ProjectVisibility.class, value, "project visibility");
    }
}
