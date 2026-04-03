package com.example.authz.loader;

import com.example.authz.domain.MembershipRole;

public record MembershipFact(
        boolean exists,
        MembershipRole role
) {
}
