package com.example.authz.policy;

import com.example.authz.dsl.Expression;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record PolicyDefinition(
        String id,
        String description,
        PolicyEffect effect,
        Set<Permission> permissions,
        Expression condition,
        Set<String> requiredFacts
) {
    public PolicyDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(effect, "effect must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(requiredFacts, "requiredFacts must not be null");
        permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        requiredFacts = Collections.unmodifiableSet(new LinkedHashSet<>(requiredFacts));
        PolicyFactPathValidator.validate(condition, requiredFacts);
    }

    public boolean appliesTo(Permission permission) {
        return permissions.contains(permission);
    }
}
