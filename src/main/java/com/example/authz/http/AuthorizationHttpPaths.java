package com.example.authz.http;

final class AuthorizationHttpPaths {
    static final String ROOT = "/";
    static final String HEALTH = "/health";
    static final String TEAMS = "/v1/teams";
    static final String PROJECTS = "/v1/projects";
    static final String USERS = "/v1/users";
    static final String DOCUMENTS = "/v1/documents";
    static final String DOCUMENTS_PREFIX = "/v1/documents/";
    static final String PERMISSION_CHECK_TEMPLATE = "/v1/documents/{documentId}/permissions/{permission}";

    private AuthorizationHttpPaths() {
    }
}
