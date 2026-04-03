package com.example.authz.http;

import com.example.authz.domain.MembershipRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WriteRequestValidatorTest {
    @Test
    void createTeamRequiresPlan() {
        CreateTeamRequest request = new CreateTeamRequest("t7", "Team Seven", null);

        assertThrows(IllegalArgumentException.class, () -> WriteRequestValidator.validate(request));
    }

    @Test
    void createProjectRequiresTeamId() {
        CreateProjectRequest request = new CreateProjectRequest(
                "p7",
                "Project Seven",
                " ",
                com.example.authz.domain.ProjectVisibility.PRIVATE
        );

        assertThrows(IllegalArgumentException.class, () -> WriteRequestValidator.validate(request));
    }

    @Test
    void createUserRejectsDuplicateTeamMembershipTargets() {
        CreateUserRequest request = new CreateUserRequest(
                "u3",
                "u3@example.com",
                "User Three",
                List.of(
                        new CreateUserRequest.TeamMembershipInput("t1", MembershipRole.VIEWER),
                        new CreateUserRequest.TeamMembershipInput("t1", MembershipRole.ADMIN)
                ),
                List.of()
        );

        assertThrows(IllegalArgumentException.class, () -> WriteRequestValidator.validate(request));
    }

    @Test
    void createDocumentRequiresCreatorIdentity() {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "d7",
                "Document Seven",
                "p1",
                null,
                null,
                false
        );

        assertThrows(IllegalArgumentException.class, () -> WriteRequestValidator.validate(request));
    }

    @Test
    void createUserAcceptsOptionalEmptyMembershipLists() {
        CreateUserRequest request = new CreateUserRequest(
                "u3",
                "u3@example.com",
                "User Three",
                null,
                null
        );

        assertDoesNotThrow(() -> WriteRequestValidator.validate(request));
    }
}
