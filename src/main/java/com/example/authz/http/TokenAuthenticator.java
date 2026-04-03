package com.example.authz.http;

interface TokenAuthenticator {
    AuthenticatedPrincipal authenticate(String authorizationHeader);
}
