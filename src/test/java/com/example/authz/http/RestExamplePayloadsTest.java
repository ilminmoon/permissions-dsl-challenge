package com.example.authz.http;

import com.example.authz.engine.AuthorizationJson;
import com.example.authz.engine.ExpressionEvaluator;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.policy.DefaultPolicies;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestExamplePayloadsTest {
    private static final Path EXAMPLES_DIR = Path.of("examples", "rest");

    private static final Map<String, Boolean> EXPECTED_ALLOWED = Map.ofEntries(
            Map.entry("scenario-1-can-view.json", true),
            Map.entry("scenario-1-can-edit.json", true),
            Map.entry("scenario-1-can-delete.json", false),
            Map.entry("scenario-1-can-share.json", true),
            Map.entry("scenario-2-can-view.json", true),
            Map.entry("scenario-2-can-edit.json", false),
            Map.entry("scenario-2-can-delete.json", false),
            Map.entry("scenario-2-can-share.json", false),
            Map.entry("scenario-3-can-view.json", true),
            Map.entry("scenario-3-can-edit.json", true),
            Map.entry("scenario-3-can-delete.json", false),
            Map.entry("scenario-3-can-share.json", false),
            Map.entry("scenario-4-can-view.json", true),
            Map.entry("scenario-4-can-edit.json", true),
            Map.entry("scenario-4-can-delete.json", false),
            Map.entry("scenario-4-can-share.json", true),
            Map.entry("scenario-5-can-view.json", false),
            Map.entry("scenario-5-can-edit.json", false),
            Map.entry("scenario-5-can-delete.json", false),
            Map.entry("scenario-5-can-share.json", false),
            Map.entry("scenario-6-can-view.json", true),
            Map.entry("scenario-6-can-edit.json", false),
            Map.entry("scenario-6-can-delete.json", false),
            Map.entry("scenario-6-can-share.json", false)
    );

    private final AuthorizationHttpFacade facade = new AuthorizationHttpFacade(
            DefaultPolicies.allPolicies(),
            new ExpressionEvaluator()
    );

    @Test
    void allRestExamplesDeserializeAndMatchScenarioExpectations() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(EXAMPLES_DIR)) {
            files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }

        assertEquals(EXPECTED_ALLOWED.size(), files.size());

        for (Path file : files) {
            AuthorizationRequestBody payload = AuthorizationJson.newObjectMapper()
                    .readValue(file.toFile(), AuthorizationRequestBody.class);

            AuthorizationDecision decision = facade.authorize(payload);
            String fileName = file.getFileName().toString();

            assertNotNull(payload.user(), fileName);
            assertNotNull(payload.team(), fileName);
            assertNotNull(payload.project(), fileName);
            assertNotNull(payload.document(), fileName);
            assertEquals(EXPECTED_ALLOWED.get(fileName), decision.allowed(), fileName);
        }
    }
}
