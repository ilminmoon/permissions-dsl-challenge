package com.example.authz.http;

import com.example.authz.engine.AuthorizationJson;
import com.example.authz.loader.ConflictException;
import com.example.authz.loader.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

public final class AuthorizationHttpServer implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger(AuthorizationHttpServer.class);
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final HttpServer server;
    private final ObjectMapper objectMapper;
    private final AuthorizationHttpFacade facade;

    public AuthorizationHttpServer(int port) throws IOException {
        this(port, AuthorizationJson.newObjectMapper(), AuthorizationHttpFacade.createDefault(System.getenv(), Clock.systemUTC()));
    }

    AuthorizationHttpServer(
            int port,
            ObjectMapper objectMapper,
            AuthorizationHttpFacade facade
    ) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.facade = Objects.requireNonNull(facade, "facade must not be null");
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext(AuthorizationHttpPaths.ROOT, this::handleRoot);
        this.server.createContext(AuthorizationHttpPaths.HEALTH, this::handleHealth);
        this.server.createContext(AuthorizationHttpPaths.TEAMS, this::handleTeamCreate);
        this.server.createContext(AuthorizationHttpPaths.PROJECTS, this::handleProjectCreate);
        this.server.createContext(AuthorizationHttpPaths.USERS, this::handleUserCreate);
        this.server.createContext(AuthorizationHttpPaths.DOCUMENTS, this::handleDocumentCreate);
        this.server.createContext(AuthorizationHttpPaths.DOCUMENTS_PREFIX, this::handlePermissionCheck);
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
        facade.close();
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

    private void handlePermissionCheck(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            writeMethodNotAllowed(exchange, "GET");
            return;
        }

        var route = PermissionCheckRoute.tryParse(exchange.getRequestURI().getPath());
        if (route.isEmpty()) {
            writeError(exchange, 404, "Not found");
            return;
        }

        try {
            writeJson(
                    exchange,
                    200,
                    facade.authorize(
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            route.get().documentId(),
                            route.get().permissionToken()
                    )
            );
        } catch (UnauthorizedException exception) {
            LOGGER.warn("Authorization request rejected: {}", exception.getMessage());
            writeUnauthorized(exchange, exception.getMessage());
        } catch (ResourceNotFoundException exception) {
            LOGGER.warn("Authorization request rejected: {}", exception.getMessage());
            writeError(exchange, 404, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Authorization request rejected: {}", exception.getMessage());
            writeError(exchange, 400, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Authorization request failed due to an internal error.", exception);
            writeError(exchange, 500, "Internal server error");
        }
    }

    private void handleUserCreate(HttpExchange exchange) throws IOException {
        handleJsonPost(
                exchange,
                AuthorizationHttpPaths.USERS,
                CreateUserRequest.class,
                "User creation",
                facade::createUser
        );
    }

    private void handleTeamCreate(HttpExchange exchange) throws IOException {
        handleJsonPost(
                exchange,
                AuthorizationHttpPaths.TEAMS,
                CreateTeamRequest.class,
                "Team creation",
                facade::createTeam
        );
    }

    private void handleProjectCreate(HttpExchange exchange) throws IOException {
        handleJsonPost(
                exchange,
                AuthorizationHttpPaths.PROJECTS,
                CreateProjectRequest.class,
                "Project creation",
                facade::createProject
        );
    }

    private void handleDocumentCreate(HttpExchange exchange) throws IOException {
        handleJsonPost(
                exchange,
                AuthorizationHttpPaths.DOCUMENTS,
                CreateDocumentRequest.class,
                "Document creation",
                facade::createDocument
        );
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

    private void writeUnauthorized(HttpExchange exchange, String message) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"authz-policy-engine\"");
        writeError(exchange, 401, message);
    }

    private <T> void handleJsonPost(
            HttpExchange exchange,
            String exactPath,
            Class<T> requestType,
            String operationName,
            Function<T, Object> handler
    ) throws IOException {
        if (!exactPath.equals(exchange.getRequestURI().getPath())) {
            writeError(exchange, 404, "Not found");
            return;
        }
        if (!isMethod(exchange, "POST")) {
            writeMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            T request = readJson(exchange, requestType);
            writeJson(exchange, 201, handler.apply(request));
        } catch (ResourceNotFoundException exception) {
            logClientFailure(operationName, exception);
            writeError(exchange, 404, exception.getMessage());
        } catch (ConflictException exception) {
            logClientFailure(operationName, exception);
            writeError(exchange, 409, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            logClientFailure(operationName, exception);
            writeError(exchange, 400, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("{} failed due to an internal error.", operationName, exception);
            writeError(exchange, 500, "Internal server error");
        }
    }

    private void logClientFailure(String operationName, RuntimeException exception) {
        LOGGER.warn("{} rejected: {}", operationName, exception.getMessage());
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) {
        try {
            return objectMapper.readValue(exchange.getRequestBody(), type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid JSON request body", exception);
        }
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
