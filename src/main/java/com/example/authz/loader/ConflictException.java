package com.example.authz.loader;

public final class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
