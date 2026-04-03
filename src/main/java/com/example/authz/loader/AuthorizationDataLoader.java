package com.example.authz.loader;

import com.example.authz.engine.AuthorizationRequest;

public interface AuthorizationDataLoader {
    AuthorizationSnapshot load(AuthorizationRequest request, DataRequirement requirement);
}
