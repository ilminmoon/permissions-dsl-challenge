package com.example.authz.loader;

import com.example.authz.domain.Document;
import com.example.authz.domain.Project;
import com.example.authz.domain.ProjectMembership;
import com.example.authz.domain.Team;
import com.example.authz.domain.TeamMembership;
import com.example.authz.domain.User;

import java.util.List;

public interface AuthorizationMutationService {
    void createTeam(Team team);

    void createProject(Project project);

    void createUser(
            User user,
            List<TeamMembership> teamMemberships,
            List<ProjectMembership> projectMemberships
    );

    DocumentCreationResult createDocument(Document document, User creator);
}
