package com.example.authz.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

final class AuthorizationRequestBodyReader {
    private final ObjectMapper objectMapper;

    AuthorizationRequestBodyReader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    AuthorizationRequestBody read(InputStream requestBody) throws IOException {
        try {
            return objectMapper.readValue(requestBody, AuthorizationRequestBody.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(buildMessage(exception), exception);
        }
    }

    private String buildMessage(JsonProcessingException exception) {
        Throwable rootCause = rootCause(exception);
        String path = inferredPath(pathFor(exception), rootCause);

        if (isInstantFailure(exception)) {
            return "Invalid timestamp at " + path + ". Expected an ISO-8601 instant.";
        }

        return "Invalid request body at " + path + ": " + rootCause.getMessage();
    }

    private String pathFor(JsonProcessingException exception) {
        if (exception instanceof JsonMappingException jsonMappingException) {
            return jsonMappingException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .reduce((left, right) -> left + "." + right)
                    .orElse("request body");
        }
        return "request body";
    }

    private boolean isInstantFailure(JsonProcessingException exception) {
        if (exception instanceof InvalidFormatException invalidFormatException) {
            return Instant.class.equals(invalidFormatException.getTargetType());
        }
        if (exception instanceof ValueInstantiationException valueInstantiationException) {
            return Instant.class.equals(valueInstantiationException.getType().getRawClass());
        }
        return false;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String inferredPath(String path, Throwable rootCause) {
        if (!"request body".equals(path) || rootCause.getMessage() == null) {
            return path;
        }

        String message = rootCause.getMessage();
        int separator = message.indexOf(' ');
        if (separator <= 0) {
            return path;
        }

        String candidate = message.substring(0, separator);
        return candidate.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_')
                ? candidate
                : path;
    }
}
