package com.example.authz.engine;

import com.example.authz.dsl.ComparisonExpression;
import com.example.authz.dsl.ComparisonOperator;
import com.example.authz.dsl.LiteralOperand;
import com.example.authz.explain.AuthorizationDecision;
import com.example.authz.explain.DecisionTrace;
import com.example.authz.explain.ExpressionTrace;
import com.example.authz.explain.PolicyTrace;
import com.example.authz.policy.Permission;
import com.example.authz.policy.PolicyEffect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationJsonContractTest {
    private final ObjectMapper objectMapper = AuthorizationJson.newObjectMapper();

    @Test
    void roundTripsAuthorizationRequestAsJson() throws Exception {
        AuthorizationRequest request = new AuthorizationRequest(
                "u1",
                "d1",
                Permission.CAN_VIEW,
                Instant.parse("2026-03-31T00:00:00Z")
        );

        String json = objectMapper.writeValueAsString(request);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("u1", node.get("userId").asText());
        assertEquals("d1", node.get("documentId").asText());
        assertEquals("can_view", node.get("permission").asText());
        assertEquals("2026-03-31T00:00:00Z", node.get("requestedAt").asText());
        assertEquals(request, objectMapper.readValue(json, AuthorizationRequest.class));
    }

    @Test
    void roundTripsAuthorizationDecisionAsJson() throws Exception {
        AuthorizationDecision decision = new AuthorizationDecision(
                true,
                Permission.CAN_VIEW,
                "allow_project_member_view",
                "Allowed by policy allow_project_member_view: Project members may view documents in their project unless another deny policy matches.",
                new DecisionTrace(
                        new AuthorizationRequest(
                                "u1",
                                "d1",
                                Permission.CAN_VIEW,
                                Instant.parse("2026-03-31T00:00:00Z")
                        ),
                        List.of(
                                new PolicyTrace(
                                        "allow_project_member_view",
                                        PolicyEffect.ALLOW,
                                        EvaluationResult.TRUE,
                                        "Condition matched.",
                                        List.of(
                                                new ExpressionTrace(
                                                        new ComparisonExpression(
                                                                "projectMembership.exists",
                                                                ComparisonOperator.EQ,
                                                                new LiteralOperand(true)
                                                        ),
                                                        EvaluationResult.TRUE,
                                                        "projectMembership.exists == true"
                                                )
                                        )
                                )
                        )
                )
        );

        String json = objectMapper.writeValueAsString(decision);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.get("allowed").asBoolean());
        assertEquals("can_view", node.get("permission").asText());
        assertEquals("allow_project_member_view", node.get("decisivePolicyId").asText());
        assertEquals(
                "projectMembership.exists",
                node.get("trace").get("policyTraces").get(0).get("expressionTraces").get(0).get("expression").get(0).asText()
        );
        assertEquals(decision, objectMapper.readValue(json, AuthorizationDecision.class));
    }
}
