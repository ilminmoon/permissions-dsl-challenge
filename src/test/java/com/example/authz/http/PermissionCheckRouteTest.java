package com.example.authz.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PermissionCheckRouteTest {
    @Test
    void parseAcceptsDocumentPermissionPath() {
        PermissionCheckRoute route = PermissionCheckRoute.tryParse("/v1/documents/d1/permissions/can_view").orElseThrow();

        assertEquals("d1", route.documentId());
        assertEquals("can_view", route.permissionToken());
    }

    @Test
    void parseRejectsMalformedPath() {
        assertFalse(PermissionCheckRoute.tryParse("/v1/permission-checks").isPresent());
        assertFalse(PermissionCheckRoute.tryParse("/v1/documents/d1").isPresent());
    }
}
