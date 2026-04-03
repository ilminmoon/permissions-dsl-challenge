package com.example.authz.loader;

import java.util.Set;

public record DataRequirement(Set<String> factPaths) {
    public DataRequirement {
        factPaths = Set.copyOf(factPaths);
    }
}
