# Assignment specification for Codex

## Goal
Design and implement the core of a policy-based authorization system for a collaborative document platform.
The result should highlight software design quality, declarative policy modeling, explainability, and clean Java implementation.

## Assignment requirements that must be satisfied
1. Policy-based approach: authorization rules are defined as independent policies.
2. Declarative DSL: permission logic must be representable with a serializable DSL such as JSON.
3. Separation of data and logic: data loading and policy evaluation must be completely separated.
4. Cross-platform design: the architecture should remain language-independent.
5. `DESIGN.md` is mandatory.
6. If implementation is provided using an AI coding agent, the AI usage process must be documented clearly.

## Domain model
Use these entities:
- User
- Team
- Project
- Document
- TeamMembership
- ProjectMembership

Important domain fields:
- `Team.plan`: `free | pro | enterprise`
- `Project.visibility`: `private | public`
- `Document.deletedAt`: nullable date/time
- `Document.publicLinkEnabled`: boolean
- TeamMembership role: `viewer | editor | admin`
- ProjectMembership role: `viewer | editor | admin`

## Permissions
- `can_view`
- `can_edit`
- `can_delete`
- `can_share`

## Policies that must be represented
1. Deny edit/delete/share on deleted documents.
2. Allow all permissions to the document creator.
3. Allow edit to project editor/admin.
4. Allow view/edit/share to team admin for documents in the same team.
5. Deny access to private project documents for non-project-members who are not team admin.
6. Deny share on free-plan teams.
7. Allow view for anyone when `publicLinkEnabled` is true.

## Architecture requirements
Design the system as a plain Java library with clear layering.
The code should make it obvious how the same DSL could later be evaluated in other runtimes or compiled into filters.

## Chosen Java design direction
Use the following design unless there is a compelling reason not to:
- Java 21
- Maven Wrapper
- JUnit 5
- Jackson
- immutable models via `record`
- `sealed interface Expression`
- `TriState` or equivalent internal evaluation result
- policy engine with explanation trace
- data loader interface that can be implemented by any storage layer

## Deliverables
Produce the following:
1. `DESIGN.md`
2. source code for DSL evaluator and policy engine
3. unit tests
4. scenario/integration tests for the 6 provided scenarios
5. `README.md`
6. `AI_USAGE_LOG.md`

## Design expectations for `DESIGN.md`
It must contain:
- system architecture diagram
- data flow diagram
- DSL grammar/shape
- core types and responsibilities
- explanation of deny precedence
- explanation of tri-state evaluation
- explanation of data-loader separation
- explanation of future extensibility (other languages / SQL-like compilation / caching)
- trade-offs and rejected alternatives

## Implementation boundaries
Implement only the core library needed to demonstrate the architecture well.
Avoid unrelated infrastructure.
Do not build a REST server or persistence layer.
Use fake/in-memory data for tests.

## Done when
The task is complete only when all of the following are true:
- `DESIGN.md` is present and polished.
- The 7 policies are represented in DSL form.
- The evaluator supports comparison, logical operators, field references, null handling, and date handling.
- Missing data produces `UNKNOWN` internally and boundary-compatible output externally if needed.
- Policy evaluation honors deny-overrides.
- The 6 scenario tests pass.
- `./mvnw test` passes.
- `README.md` explains how to run and what is implemented.
- `AI_USAGE_LOG.md` documents prompts, outputs, and manual decisions.

