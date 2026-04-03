package com.example.authz.domain;

public record User(
        String id,
        String email,
        String name
) {
}
