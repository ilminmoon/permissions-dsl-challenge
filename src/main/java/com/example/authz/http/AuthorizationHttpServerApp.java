package com.example.authz.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AuthorizationHttpServerApp {
    private static final Logger LOGGER = LogManager.getLogger(AuthorizationHttpServerApp.class);

    private AuthorizationHttpServerApp() {
    }

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);
        AuthorizationHttpServer server = new AuthorizationHttpServer(port);
        registerShutdownHook(server);
        server.start();
        LOGGER.info("Authorization HTTP server listening on http://0.0.0.0:{}", server.port());
    }

    private static void registerShutdownHook(AuthorizationHttpServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Stopping authorization HTTP server.");
            server.close();
        }));
    }

    static int resolvePort(String[] args) {
        return resolvePort(args, System.getenv("PORT"));
    }

    static int resolvePort(String[] args, String envPort) {
        if (args.length >= 2 && "--port".equals(args[0])) {
            return parsePort(args[1], "--port");
        }
        if (envPort != null && !envPort.isBlank()) {
            return parsePort(envPort, "PORT");
        }
        return 8080;
    }

    private static int parsePort(String rawValue, String source) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(source + " must be a valid integer port", exception);
        }
    }
}
