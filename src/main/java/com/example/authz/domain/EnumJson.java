package com.example.authz.domain;

import java.util.Arrays;

final class EnumJson {
    private EnumJson() {
    }

    static <E extends Enum<E> & JsonTokenEnum> E fromJson(Class<E> enumType, String value, String label) {
        return Arrays.stream(enumType.getEnumConstants())
                .filter(constant -> constant.jsonValue().equalsIgnoreCase(value) || constant.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported " + label + ": " + value));
    }
}
