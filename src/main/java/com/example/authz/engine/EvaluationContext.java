package com.example.authz.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EvaluationContext(Map<String, Object> facts) {
    public EvaluationContext {
        facts = facts == null ? Map.of() : deepUnmodifiableMap(facts);
    }

    public LookupResult lookup(String path) {
        Objects.requireNonNull(path, "path must not be null");

        if (facts.containsKey(path)) {
            return new LookupResult(true, facts.get(path));
        }

        Object current = facts;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return LookupResult.missing();
            }
            if (!map.containsKey(segment)) {
                return LookupResult.missing();
            }
            current = map.get(segment);
        }

        return new LookupResult(true, current);
    }

    public record LookupResult(boolean found, Object value) {
        public static LookupResult missing() {
            return new LookupResult(false, null);
        }
    }

    private static Map<String, Object> deepUnmodifiableMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepUnmodifiableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object deepUnmodifiableValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("fact map keys must be strings");
                }
                copy.put(key, deepUnmodifiableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }

        if (value instanceof List<?> list) {
            ArrayList<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepUnmodifiableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }

        return value;
    }
}
