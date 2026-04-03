package com.example.authz.http;

final class UnauthorizedException extends RuntimeException {
    UnauthorizedException(String message) {
        super(message);
    }
}
