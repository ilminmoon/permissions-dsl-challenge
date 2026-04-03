package com.example.authz.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyJacksonRoundTripTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesPolicyDefinitionsUsingDslPermissionTokens() throws Exception {
        PolicyDefinition policy = DefaultPolicies.allPolicies().stream()
                .filter(candidate -> candidate.id().equals("allow_public_link_view"))
                .findFirst()
                .orElseThrow();

        String json = objectMapper.writeValueAsString(policy);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("allow_public_link_view", node.get("id").asText());
        assertEquals("ALLOW", node.get("effect").asText());
        assertEquals("can_view", node.get("permissions").get(0).asText());
        assertTrue(node.get("condition").isArray());
        assertEquals("document.publicLinkEnabled", node.get("requiredFacts").get(0).asText());
    }

    @Test
    void roundTripsDefaultPolicyCatalogAsJson() throws Exception {
        List<PolicyDefinition> policies = DefaultPolicies.allPolicies();

        String json = objectMapper.writeValueAsString(policies);
        List<PolicyDefinition> roundTripped = objectMapper.readValue(
                json,
                new TypeReference<>() {
                }
        );

        assertEquals(policies, roundTripped);
    }
}
