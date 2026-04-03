package com.example.authz.http;

import com.example.authz.engine.AuthorizationJson;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.policy.DefaultPolicies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuthorizationHttpServer implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger(AuthorizationHttpServer.class);
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final HttpServer server;
    private final ObjectMapper objectMapper;
    private final AuthorizationRequestBodyReader requestBodyReader;
    private final AuthorizationHttpFacade facade;

    public AuthorizationHttpServer(int port) throws IOException {
        this(port, AuthorizationJson.newObjectMapper(), DefaultPolicies.allPolicies(), new ExpressionEvaluator());
    }

    AuthorizationHttpServer(
            int port,
            ObjectMapper objectMapper,
            List<com.example.authz.policy.PolicyDefinition> policies,
            ExpressionEvaluator expressionEvaluator
    ) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.requestBodyReader = new AuthorizationRequestBodyReader(this.objectMapper);
        this.facade = new AuthorizationHttpFacade(policies, expressionEvaluator);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext(AuthorizationHttpPaths.ROOT, this::handleRoot);
        this.server.createContext(AuthorizationHttpPaths.HEALTH, this::handleHealth);
        this.server.createContext(AuthorizationHttpPaths.PERMISSION_CHECKS, this::handleAuthorize);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            writeMethodNotAllowed(exchange, "GET");
            return;
        }

        writeJson(exchange, 200, facade.rootDocument());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            writeMethodNotAllowed(exchange, "GET");
            return;
        }

        writeJson(exchange, 200, facade.healthDocument());
    }

    private void handleAuthorize(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            writeMethodNotAllowed(exchange, "POST");
            return;
        }

        try (InputStream inputStream = exchange.getRequestBody()) {
            writeJson(exchange, 200, facade.authorize(readRequestBody(inputStream)));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Authorization request rejected: {}", exception.getMessage());
            writeError(exchange, 400, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Authorization request failed due to an internal error.", exception);
            writeError(exchange, 500, "Internal server error");
        }
    }

    private AuthorizationRequestBody readRequestBody(InputStream requestBody) throws IOException {
        return requestBodyReader.read(requestBody);
    }

    private boolean isMethod(HttpExchange exchange, String expectedMethod) {
        return expectedMethod.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private void writeMethodNotAllowed(HttpExchange exchange, String allowedMethod) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethod);
        writeError(exchange, 405, "Method not allowed");
    }

    private void writeError(HttpExchange exchange, int statusCode, String message) throws IOException {
        writeJson(exchange, statusCode, new AuthorizationErrorResponse(message));
    }

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", JSON_CONTENT_TYPE);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
