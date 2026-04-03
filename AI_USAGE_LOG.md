# AI Usage Log

## 2026-03-31 - Milestone 0 - Analyze and align

### Prompt

- Read `AGENTS.md`, `PROMPT.md`, `PLAN.md`, `IMPLEMENT.md`, `README.md`, and all files under `references/`.
- Do only Milestone 0 from `PLAN.md`.
- Summarize the assignment in my own words.
- Propose the Java package structure.
- Define the core domain, DSL, policy, engine, loader, and explanation types.
- Encode all 7 required policies in the proposed DSL.
- Write the first solid draft of `DESIGN.md`.
- Do not implement runtime code yet.
- Update `AI_USAGE_LOG.md` for this step.
- When finished, report changed files, milestone acceptance status, and unresolved design decisions.

### Codex output summary

- Read the repository specification files and the reference documents before making changes.
- Added `DESIGN.md` with:
  - assignment summary
  - package structure
  - architecture and data flow diagrams
  - core type sketches for `domain`, `dsl`, `policy`, `engine`, `loader`, and `explain`
  - tri-state and deny-overrides semantics
  - all seven required policies encoded in the proposed JSON-friendly DSL
  - trade-offs, extensibility notes, and a Milestone 0 scope boundary
- Kept this milestone documentation-only. No runtime Java code, Maven files, or tests were added.
- Identified and documented a spec mismatch: the six scenario expectations imply at least one additional allow policy for ordinary project members that is not included in the seven mandatory policy statements.

### Human decisions / corrections

- Scope was limited to Milestone 0 only.
- `PLAN.md` was left unchanged because it already breaks the work into small milestones clearly enough.
- The scenario/policy mismatch was recorded as an unresolved design issue rather than being silently patched by inventing extra runtime behavior in this milestone.

### Accepted result

- `DESIGN.md` now exists as the first architecture draft.
- `AI_USAGE_LOG.md` now records the Milestone 0 work and design decisions.
- Runtime implementation remains deferred to later milestones.
## 2026-03-31 - Milestone 0.5 - Resolve assignment ambiguities

### Prompt

- Re-read the 6 scenario expectations carefully.
- Identify the minimum additional baseline allow policies required so that the scenarios are internally consistent.
- Do not weaken deny-overrides or default-deny.
- Preserve the original 7 mandatory policies exactly as assignment-required policies.
- Add only the smallest necessary supplemental policies or rule interpretations.
- Update `DESIGN.md` with:
  - an `Ambiguity Resolution` section
  - the supplemental policies
  - why they are necessary
  - why they do not contradict the mandatory 7 policies
- Update `AI_USAGE_LOG.md` with this design refinement step.
- Do not start Java implementation yet.

### Codex output summary

- Re-read all 6 scenario expectations in `README.md` and reconciled them against the 7 mandatory assignment policies.
- Updated `DESIGN.md` to:
  - preserve the 7 mandatory policies as the base assignment policy set
  - remove the earlier public-link carve-out from the mandatory private-project deny policy definition
  - add an `Ambiguity Resolution` section
  - add the minimum two supplemental allow policies:
    - `allow_project_member_view`
    - `allow_project_editor_or_admin_share`
  - document one narrow interpretation for the interaction between the private-project deny and public-link view allow
  - explain which scenarios each addition unlocks and why deny-overrides plus default-deny remain intact
- Kept this turn documentation-only. No Java source, Maven, or test files were added.

### Human decisions / corrections

- The original 7 mandatory policies remain the assignment-required base rule set.
- Scenario reconciliation was handled with the smallest possible design change:
  - 2 supplemental allow policies
  - 1 view-only interpretation for the policy 5 versus policy 7 overlap
- No broader "public project means world-readable" rule was added because the scenarios do not require it.

### Accepted result

- `DESIGN.md` now includes a concrete ambiguity-resolution strategy that makes the six scenarios internally consistent.
- Deny-overrides and default-deny remain unchanged.
- Runtime implementation is still deferred.

## 2026-03-31 - Milestone 1 - Project skeleton

### Prompt

- Proceed with Milestone 1 from `PLAN.md` only.
- Use the current `DESIGN.md` as the source of truth, including the ambiguity resolutions and supplemental policies.
- Create the Maven project skeleton.
- Add `pom.xml`.
- Create package directories and minimal type skeletons.
- Keep the code compile-friendly and simple.
- Do not implement evaluator or engine logic yet.
- Add or update tests so `./mvnw test` passes.
- Update `AI_USAGE_LOG.md`.

### Codex output summary

- Generated a proper Maven wrapper with `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties`.
- Added `pom.xml` for a plain Java 21 library with JUnit 5 and Jackson dependencies plus compiler and surefire configuration.
- Created the `src/main/java/com/example/authz/` package skeleton for:
  - `domain`
  - `dsl`
  - `policy`
  - `engine`
  - `loader`
  - `explain`
- Added minimal compile-friendly type skeletons matching the current design:
  - domain records and enums
  - DSL sealed interfaces and record nodes
  - policy enums and `PolicyDefinition`
  - `EvaluationResult`, `AuthorizationRequest`, and a stub `PolicyEngine`
  - loader DTOs and loader interface
  - explanation records
- Added placeholder tests in `dsl`, `policy`, `engine`, and `scenario`.
- Kept evaluator and engine behavior intentionally unimplemented; `PolicyEngine.authorize(...)` explicitly throws `UnsupportedOperationException` until Milestone 5.
- Ran `./mvnw test` successfully after allowing Maven to use `~/.m2` for wrapper and dependency downloads.

### Human decisions / corrections

- Wrapper generation and the test run were both escalated because Maven needed access to `~/.m2` outside the workspace sandbox.
- No policy registry, evaluator, or scenario authorization logic was implemented yet because those belong to later milestones.
- The type skeleton follows the current `DESIGN.md` layout and leaves actual policy encoding for Milestone 4.

### Accepted result

- The repository now has a working Maven project skeleton.
- Minimal types compile cleanly.
- `./mvnw test` passes with initial placeholder tests.

## 2026-03-31 - Milestone 2 - Expression DSL model

### Prompt

- Proceed with Milestone 2 from `PLAN.md` only.
- Implement the expression DSL model in Java 21 using the current `DESIGN.md` as the source of truth.
- Implement the DSL type model only.
- Do not implement expression evaluation logic yet.
- Do not implement policy engine behavior yet.
- Include:
  - `sealed interface Expression`
  - comparison expressions
  - logical expressions: `and`, `or`, `not`
  - field reference support for values such as `{ "ref": "user.id" }`
  - JSON-friendly modeling with Jackson
  - immutable design using records / enums / sealed interfaces
- Add Jackson round-trip tests and shape/contract tests.
- Keep `./mvnw test` passing.
- Update `AI_USAGE_LOG.md`.

### Codex output summary

- Reworked the `dsl` package from placeholder records into a JSON-friendly expression model:
  - `Expression` remains a sealed interface
  - `ComparisonExpression` uses array JSON shape
  - `AndExpression`, `OrExpression`, and `NotExpression` use object JSON shape with `and`, `or`, and `not`
  - `Operand` remains sealed with `LiteralOperand` and `FieldRefOperand`
- Added Jackson integration for the mixed DSL shapes:
  - annotation-driven serialization for the concrete records
  - a small `ExpressionDeserializer` for array/object polymorphism
  - a small `OperandDeserializer` for scalar/null versus `{ "ref": ... }`
- Added operator JSON handling in `ComparisonOperator`.
- Added literal validation so operands stay within the assignment’s value set: string, number, boolean, or null.
- Added DSL tests covering:
  - simple comparison round-trip
  - nested `and` / `or` / `not`
  - field reference operand
  - null literal
  - date-compatible literal representation as an ISO-8601 string
- Kept evaluator and policy engine behavior untouched.

### Human decisions / corrections

- For Jackson compatibility, the canonical serialized operator tokens remain the textual forms from `DESIGN.md`: `eq`, `ne`, `gt`, `gte`, `lt`, `lte`.
- The deserializer also accepts assignment-style symbolic aliases such as `=`, `<>`, `>`, `<`, `>=`, `<=` so the model stays compatible with the original prompt examples.
- Date literals are modeled as plain JSON strings for now; later milestones can parse them into `Instant` during evaluation rather than complicating the DSL model.
- Small custom deserializers were used only where necessary because the DSL mixes array, object, scalar, and null shapes. Serialization remains mostly annotation-driven.

### Accepted result

- The expression DSL model is now implemented and Jackson round-trips it successfully.
- No evaluator or policy engine logic was added.
- `./mvnw test` passes.

## 2026-03-31 - Milestone 3 - Expression evaluator

### Prompt

- Proceed with Milestone 3 from `PLAN.md` only.
- Implement the expression evaluator using the current `DESIGN.md` and DSL model as the source of truth.
- Implement evaluator behavior only.
- Do not implement policy engine behavior yet.
- Support:
  - tri-state `TRUE`, `FALSE`, `UNKNOWN`
  - `and`, `or`, `not`
  - field reference resolution
  - comparisons for string, number, boolean, null, and date/time
  - missing data -> `UNKNOWN`
- Keep the evaluator independent from the policy engine.
- Add the assignment example tests and at least 8 more unit tests.
- Keep `./mvnw test` passing.
- Update `AI_USAGE_LOG.md`.

### Codex output summary

- Added a standalone evaluator in the `engine` package:
  - `EvaluationContext` as the evaluator input shape
  - `ExpressionEvaluator` for DSL execution
- Kept the evaluator independent from policy orchestration.
- Implemented tri-state behavior for:
  - comparison expressions
  - `and`
  - `or`
  - `not`
- Implemented field resolution against the chosen fact map shape:
  - nested map traversal by dotted paths such as `user.id`
  - exact dotted-key lookup as a convenience fallback
- Implemented comparisons for:
  - strings
  - numbers
  - booleans
  - null
  - date/time values
- Added evaluator unit tests covering:
  - the assignment examples
  - ref-to-ref comparisons
  - numeric comparisons
  - boolean comparisons
  - null handling
  - ISO string date comparison
  - `Instant` versus date-string comparison
  - nested logic
  - mixed known/unknown cases for `and`, `or`, and `not`
- Left `PolicyEngine` itself unchanged; it still throws `UnsupportedOperationException`.

### Human decisions / corrections

- Evaluator input shape:
  - `EvaluationContext(Map<String, Object> facts)`
  - expected primary shape is nested maps, for example `Map.of("user", Map.of("id", "123"))`
  - exact dotted keys such as `"user.id"` are also accepted as a small compatibility convenience
- Date/time rule:
  - if both operands are `Instant` values or ISO-8601 strings parseable by `Instant.parse`, compare them as `Instant`
  - otherwise strings compare as strings and malformed date strings do not get special coercion
- Type coercion was kept intentionally minimal:
  - numbers compare numerically
  - booleans support only equality/inequality
  - ordered comparisons on unsupported type combinations return `UNKNOWN`

### Accepted result

- The expression evaluator is now implemented and tested.
- Policy engine behavior remains deferred.
- `./mvnw test` passes.

## 2026-03-31 - Milestone 4 - Policy model and registry

### Prompt

- Proceed with Milestone 4 from `PLAN.md` only.
- Implement the policy system and policy engine using the current `DESIGN.md` as the source of truth.
- Include:
  - the 7 mandatory assignment policies
  - the approved supplemental policies
  - deny-overrides semantics
  - default deny
  - explanation / trace support
- Keep data loading separate from policy evaluation.
- Preserve the standalone expression evaluator and call it from the policy engine.
- Add scenario-based tests for the 6 assignment scenarios.
- Add explicit tests for deny-overrides, free-plan share deny, deleted-document mutation deny, creator allow, team-admin private-project access, and private-project non-member denial with the public-link `can_view` carve-out.
- Keep `./mvnw test` passing.
- Update `AI_USAGE_LOG.md`.

### Codex output summary

- Added first-class executable policy definitions in `DefaultPolicies`:
  - 7 mandatory assignment policies
  - 2 approved supplemental policies from the ambiguity-resolution step
- Implemented `PolicyEngine.authorize(...)` as readable orchestration around the existing `ExpressionEvaluator`:
  - filter policies by requested permission
  - union the required fact paths from applicable policies
  - exclude `request.*` from loader requirements
  - call the loader once per request
  - inject request-scoped facts such as `request.permission`
  - evaluate deny policies first
  - short-circuit on the first decisive deny
  - if no deny matched, evaluate allow policies
  - if no allow matched, deny by default
- Added authorization trace output:
  - final outcome
  - requested permission
  - decisive policy id when present
  - final reason text
  - ordered policy traces with `TRUE | FALSE | UNKNOWN` results
- Kept the public-link behavior explicit and policy-driven by encoding the approved policy-5 / policy-7 interaction inside the executable private-project deny condition using `request.permission` plus `document.publicLinkEnabled`.
- Added real policy-engine and scenario tests:
  - six assignment scenarios
  - required-data selection
  - creator allow trace
  - deny-overrides short-circuit
  - default deny
- Removed the earlier placeholder policy-engine and scenario smoke tests.

### Human decisions / corrections

- Policies are represented directly as immutable `PolicyDefinition` records rather than introducing an extra registry abstraction.
- The executable private-project deny remains one policy definition; the view-only public-link carve-out is expressed in that policy condition instead of being implemented as hidden engine branching.
- Loader requirements are intentionally conservative and static:
  - the engine unions `requiredFacts` from all applicable policies for the requested permission
  - request-derived facts such as `request.permission` are injected by the engine and are not requested from the loader
- Test fixtures normalize enum values to the DSL string tokens such as `free`, `private`, `admin`, and `editor` so policy comparisons remain language-independent and JSON-aligned.

### Accepted result

- The policy registry and policy engine are now implemented.
- Deny-overrides and default deny are enforced by runtime tests.
- Scenario coverage for all 6 assignment scenarios now exists.
- `./mvnw test` passes.

## 2026-03-31 - Final hardening and submission prep

### Prompt

- Proceed with the final hardening and submission-prep milestone only.
- Use the current repository state as the source of truth.
- Make the submission reviewer-friendly.
- Align `DESIGN.md`, `README.md`, tests, and code.
- Clearly explain the final design, trade-offs, and limitations.
- Review code, docs, tests, and `AI_USAGE_LOG.md` for consistency.
- Update `DESIGN.md` with the final architecture, data flow, DSL syntax, mandatory and supplemental policies, ambiguity resolution, deny/default semantics, required-data loading strategy, trace design, trade-offs, and future improvements.
- Create or refine `README.md` with project overview, build/test instructions, package overview, example usage, current scope and limitations, and a pointer to `DESIGN.md`.
- Review test names and comments for clarity.
- Make only small cleanup refactors if they improve readability.
- Run `./mvnw test`.
- Update `AI_USAGE_LOG.md`.

### Codex output summary

- Rewrote `README.md` from the original assignment handout into a reviewer-facing project README.
- Replaced milestone-era `DESIGN.md` content with an implementation-aligned design document that now describes:
  - the actual architecture and class boundaries
  - request/data flow
  - DSL syntax and JSON shapes
  - tri-state evaluator semantics
  - the 7 mandatory policies
  - the 2 approved supplemental policies
  - the executable public-link/private-project ambiguity resolution
  - required-data loading strategy
  - authorization trace contents
  - trade-offs, limitations, and future improvements
- Reviewed the test suite for clarity and kept the current test names because they already describe behavior directly.
- Added a small hardening refactor in `PolicyEngine`:
  - constructor arguments are now null-checked
  - `authorize(request)` now null-checks the request explicitly
- Re-ran the full Maven test suite successfully.

### Human decisions / corrections

- No major feature work was added in this pass.
- The design doc now distinguishes between:
  - the seven mandatory assignment policies as the base policy set
  - the executable private-project deny form used by the implementation to keep public-link view explicit and policy-driven
- The README example intentionally shows a minimal loader response for a `can_view` request rather than pretending the loader always returns every possible fact.
- Test names were reviewed but not renamed because they were already short and behavior-specific.

### Accepted result

- The submission is now documentation-complete and reviewer-friendly.
- `DESIGN.md`, `README.md`, code, and tests are aligned.
- `./mvnw test` passes.


## 2026-04-02 - Docker packaging convenience

### Prompt

- Add a reviewer-convenience Docker path for this project.
- Provide:
  - a Docker image that can build and package the project
  - a script that automates building and running the image
  - jar artifact packaging as part of that flow
- Keep the solution lightweight and appropriate for a take-home submission.

### Codex output summary

- Added a multi-stage `Dockerfile` that:
  - runs `./mvnw test package` in a builder image
  - copies the built jar into a slim runtime image
- Added `.dockerignore` to keep the Docker build context small.
- Added `scripts/container-entrypoint.sh` for image commands:
  - `info`
  - `export-jar`
  - `shell`
- Added `scripts/docker-package-and-run.sh` to:
  - build the Docker image
  - run the image to export the jar into `./dist`
  - run the image again in `info` mode
- Updated `README.md` with local packaging and Docker-based packaging instructions.

### Human decisions / corrections

- Kept Docker as an optional reviewer-convenience path rather than making it the primary way to use the project.
- Did not turn the library into a long-running service container.
- Used the container to package and export the jar instead of inventing a synthetic runtime main class.

### Accepted result

- The project can now be packaged and artifact-exported through Docker for reviewers who do not have a local Java setup.
- Local Maven wrapper usage remains supported.

## 2026-04-02 - REST-ready service contract direction

### Prompt

- Adopt REST as the preferred transport direction for reviewer accessibility.
- Record the rationale explicitly in the documentation, especially that REST is easier for assignment reviewers to inspect and exercise than gRPC.
- Make the codebase REST-ready at the contract level without over-expanding beyond the core architecture.

### Codex output summary

- Kept the assignment-compliant plain-library architecture instead of adding an actual HTTP server.
- Added `AuthorizationJson` as a small Jackson configuration helper for JSON request/decision contracts.
- Added Jackson Java Time support so `AuthorizationRequest.requestedAt` can round-trip cleanly as an ISO-8601 string.
- Added JSON contract tests for:
  - `AuthorizationRequest`
  - `AuthorizationDecision` with nested trace data
- Updated `README.md` and `DESIGN.md` to record that:
  - if the core were wrapped as a service for this take-home, REST would be preferred over gRPC for reviewer convenience
  - the repository still intentionally stops at the library boundary because the assignment spec excludes building a REST server

### Human decisions / corrections

- Did not add a REST server because `PROMPT.md` explicitly scopes the assignment to the core library and says not to build a REST server.
- Chose the smallest useful implementation that still makes the code REST-ready:
  - JSON-stable request/response contracts
  - documented transport choice rationale

### Accepted result

- The core remains assignment-compliant.
- The public contract is now easier to expose through a thin REST wrapper later without changing the engine API.

## 2026-04-02 - Thin REST wrapper and runnable jar

### Prompt

- Add a thin REST API wrapper around the existing authorization engine.
- Keep the authorization core transport-independent.
- Make the project runnable so reviewers can exercise the system directly over HTTP with minimal setup.

### Codex output summary

- Added a thin REST wrapper around the existing policy engine using JDK `HttpServer`.
- Added runnable entrypoints and packaging support so the project now starts with:
  - `java -jar target/authz-policy-engine-0.0.1-SNAPSHOT.jar`
  - Docker container default command `serve`
- Added:
  - `GET /health`
  - `POST /authorize`
- Added scenario-style REST payload records:
  - `AuthorizationApiPayload`
  - `AuthorizationApiContext`
- Kept data loading separate from evaluation by introducing a request-scoped `AuthorizationPayloadLoader` that converts the HTTP payload into an `AuthorizationSnapshot`.
- Made `TeamPlan`, `ProjectVisibility`, and `MembershipRole` serialize and deserialize with lowercase JSON tokens to match the assignment examples.
- Added HTTP integration tests and a sample REST request file.
- Updated Docker scripts so the image can now run the REST API directly.

### Human decisions / corrections

- Chose REST instead of gRPC because reviewer verification is simpler with `curl` and JSON files.
- Kept the HTTP layer intentionally thin so the main architectural focus stays on the policy engine.
- Used JDK `HttpServer` instead of a heavier framework to avoid distracting from the assignment core.

### Accepted result

- The project now supports direct REST testing locally and through Docker.
- The authorization core remains transport-independent under the thin HTTP adapter.

## 2026-04-02 - Log4j2 logging and HTTP test refactor

### Prompt

- Replace ad hoc console output with Log4j2 using a current stable release.
- Update the runnable HTTP layer to use structured logger-based output instead of `System.out.println`.
- If the current HTTP tests rely on brittle environment assumptions, refactor them toward a more deterministic structure.

### Codex output summary

- Added Log4j2 dependencies using the latest stable `2.25.4` line from Maven Central.
- Replaced the REST app startup `System.out.println(...)` with Log4j2 logging.
- Added a minimal `log4j2.xml` console configuration for the runnable jar and Docker flow.
- Refactored the HTTP layer by extracting `AuthorizationHttpApi`, so the request-to-decision behavior can be tested without opening sockets.
- Removed the earlier socket-binding test gate based on `assumeTrue(...)` and replaced it with deterministic API tests.
- Updated the README example to stop showing `System.out.println(...)` as the primary usage pattern.

### Human decisions / corrections

- Used Log4j2 directly rather than adding another facade layer because the user explicitly requested Log4j2.
- Chose the latest stable `2.x` release visible from Maven Central rather than a `3.0.0-beta` build.
- Replaced socket-bound HTTP tests with API-level tests because `assumeTrue` would hide test behavior behind environment conditions.

### Accepted result

- The runnable REST wrapper now uses Log4j2 instead of `System.out.println(...)`.
- The HTTP behavior is covered by deterministic tests without environment-specific assumptions.

## 2026-04-02 - Broad codebase refactoring pass

### Prompt

- Review the Java codebase comprehensively and apply a readability-focused refactoring pass.
- Improve naming, method boundaries, responsibility separation, and dead-code cleanup where appropriate.
- Remove low-value comments and simplify unclear control flow, but do not change the intended authorization behavior.
- If you identify additional low-risk refactoring opportunities that materially improve reviewability, include them as well.

### Codex output summary

- Reviewed the Java codebase and applied a behavior-preserving cleanup pass focused on readability and responsibility boundaries.
- Refactored `PolicyEngine`:
  - removed `null` sentinel returns in favor of `Optional`
  - renamed helper methods to make the decision flow easier to read
  - simplified required-fact collection without stream-heavy logic
- Refactored `ExpressionEvaluator`:
  - improved helper names
  - made comparison flow more explicit
  - renamed the operand-resolution record to better reflect its purpose
- Refactored `ExpressionDeserializer` into smaller parsing helpers for:
  - comparison expressions
  - logical expressions
  - right-side operands
- Refactored the HTTP layer:
  - added validation directly to `AuthorizationApiContext`, `AuthorizationApiPayload`, and `PolicyDefinition`
  - simplified `AuthorizationPayloadLoader` by normalizing memberships once
  - reduced repeated null checks and extracted smaller fact-building helpers
  - improved `AuthorizationHttpServer` method naming and error-writing helpers
  - added a shutdown hook in the HTTP app entrypoint
- Preserved the earlier Log4j2 change and deterministic API-level tests.

### Human decisions / corrections

- Kept the refactor intentionally conservative:
  - no DSL semantics changes
  - no policy behavior changes
  - no transport contract changes
- Prioritized removal of `null`-driven internal control flow and repeated validation over more aggressive architecture splits.

### Accepted result

- The codebase is cleaner and easier to review without changing the authorization behavior.
- `./mvnw test` passes after the refactor.

## 2026-04-02 - Final cleanup follow-up

### Prompt

- Complete the remaining follow-up refactors discussed in the prior review.
- Continue to prioritize clarity, consistency, and behavior-preserving cleanup rather than architectural churn.
- Finish the smaller cleanup items that were intentionally deferred in the previous refactoring pass.

### Codex output summary

- Removed repeated enum JSON parsing logic by introducing:
  - `JsonTokenEnum`
  - `EnumJson`
- Updated `TeamPlan`, `ProjectVisibility`, and `MembershipRole` to share that parsing path.
- Refined `DefaultPolicies` for readability:
  - extracted permission-set constants
  - extracted role/token constants
  - named helper expressions for team-admin checks, project editor/admin checks, and the private-project deny condition
  - replaced manual list assembly with a simple stream concat
- Added a small structured HTTP error type:
  - `AuthorizationApiError`
  - kept the outward JSON shape stable with the same `error` field

### Human decisions / corrections

- Kept these changes behavior-preserving and focused on readability.
- Avoided larger framework-style abstractions so the code still reads like a take-home rather than a platform rewrite.

### Accepted result

- Enum mapping duplication is removed.
- `DefaultPolicies` is easier to read and review.
- The HTTP error path is slightly more structured without changing the public contract.

## 2026-04-02 - README simplification

### Prompt

- Rewrite `README.md` into a concise submission-oriented document.
- Limit it to these sections:
  - project overview
  - installation and execution
  - example usage
  - limitations and future improvement areas
- Keep the content aligned with the current implementation and avoid duplicating deeper design material from `DESIGN.md`.

### Codex output summary

- Rewrote `README.md` into a short submission-oriented document with only the four requested sections.
- Kept the content aligned with the current codebase:
  - local test/build commands
  - runnable jar command
  - Docker REST startup script
  - sample `curl` request using the included example payload

### Human decisions / corrections

- Removed the earlier long-form README structure and kept only the sections needed for submission review.
- Left detailed architecture and policy rationale in `DESIGN.md` instead of duplicating it in the README.

### Accepted result

- `README.md` now contains only the requested reviewer-facing essentials.

## 2026-04-02 - Cross-platform run scripts

### Prompt

- Improve the reviewer-facing run scripts so the project can be launched cleanly across macOS, Linux, and Windows.
- Add platform-aware script handling for Docker-based and direct-jar execution paths.
- Make the scripts fail clearly on unsupported shells rather than ambiguously.
- Update the README so the platform-specific commands are easy to follow.

### Codex output summary

- Added a local runnable-jar launcher script:
  - `scripts/run-rest.sh`
- Added Windows wrappers for all reviewer-facing run paths:
  - `scripts/run-rest.ps1`
  - `scripts/run-rest.cmd`
  - `scripts/docker-run-rest.ps1`
  - `scripts/docker-run-rest.cmd`
  - `scripts/docker-package-and-run.ps1`
  - `scripts/docker-package-and-run.cmd`
- Updated the Unix shell scripts to detect unsupported Windows shell environments and redirect users toward the Windows variants instead of failing ambiguously.
- Updated `README.md` so the install/run section now shows:
  - macOS/Linux commands
  - Windows `.cmd` / `.ps1` commands
  - Docker-based and direct-jar paths separately

### Human decisions / corrections

- Kept the Docker and jar entry points as the primary reviewer convenience paths instead of adding more runtime modes.
- Used separate Windows script files rather than trying to force one shell syntax to work everywhere.

### Accepted result

- The repository now exposes explicit reviewer-facing launch scripts for macOS, Linux, and Windows.
- The README now documents platform-specific commands for both Docker and direct jar execution.

## 2026-04-02 - Add full REST example payload set

### Prompt

- Populate `examples/rest` with a complete set of reviewer-facing REST payloads for the assignment scenarios.
- Include all six scenarios and all four permissions so the example set is comprehensive.
- Keep the example shapes aligned with the implemented HTTP contract and validate them through automated tests.

### Codex output summary

- Added REST example payloads for all 6 assignment scenarios and all 4 permissions under `examples/rest`.
- Kept the existing JSON contract shape and naming pattern:
  - `scenario-<n>-can-view.json`
  - `scenario-<n>-can-edit.json`
  - `scenario-<n>-can-delete.json`
  - `scenario-<n>-can-share.json`
- Added a test that loads every example payload, deserializes it through the real Jackson contract, and verifies the resulting authorization outcome against the documented scenario expectation.
- Updated `README.md` so the examples section now points readers at the full `examples/rest` set instead of a single file.

### Human decisions / corrections

- Used explicit `exists: false` membership facts for absent team/project memberships so the REST examples stay structurally uniform.
- Kept the example set aligned to the implemented scenario tests and policy semantics instead of inventing extra non-assignment cases.

### Accepted result

- `examples/rest` now covers all assignment scenarios across all permissions.
- The example payloads are now test-validated as part of the repository.

## 2026-04-02 - Add thin HTTP input validation

### Prompt

- Add thin HTTP-boundary validation for the highest-priority input hygiene concerns.
- Specifically cover:
  - required-field validation
  - blank-string rejection
  - enum parsing failures
  - timestamp parsing failures
- Keep this validation at the transport boundary only; do not push it into the core evaluator or policy engine.

### Codex output summary

- Added `AuthorizationApiPayloadValidator` in the HTTP layer to validate required fields and blank-string inputs after request deserialization.
- Added `AuthorizationApiPayloadReader` in the HTTP layer to centralize JSON parsing and turn enum / timestamp deserialization failures into consistent `IllegalArgumentException` messages for 400 responses.
- Kept this validation scoped to the HTTP wrapper instead of moving it into the core engine or domain models.
- Added HTTP-layer tests covering:
  - blank request fields
  - missing required timestamp
  - membership role consistency
  - invalid permission enum
  - invalid team plan enum
  - invalid timestamp parsing

### Human decisions / corrections

- Chose boundary-only validation so the policy engine and evaluator remain transport-independent.
- Kept validation intentionally thin: presence, blank checks, enum parsing, and timestamp parsing only.

### Accepted result

- The REST input boundary now rejects malformed requests more explicitly while the core authorization engine remains unchanged.

## 2026-04-02 - Refine package and class naming

### Prompt

- Review the package structure and class names for clarity and architectural fit.
- If any names are misleading, too generic, or inconsistent with their actual role, rename them to something more precise.
- Prefer targeted naming improvements over broad structural churn.

### Codex output summary

- Reviewed the package layout and kept it unchanged because the top-level split (`domain`, `dsl`, `policy`, `engine`, `loader`, `explain`, `http`) was already clear and interview-friendly.
- Refined the `http` package naming to make transport-boundary responsibilities more obvious:
  - `AuthorizationApiContext` -> `AuthorizationRequestContext`
  - `AuthorizationApiPayload` -> `AuthorizationRequestBody`
  - `AuthorizationApiPayloadReader` -> `AuthorizationRequestBodyReader`
  - `AuthorizationApiPayloadValidator` -> `AuthorizationRequestBodyValidator`
  - `AuthorizationApiError` -> `AuthorizationErrorResponse`
  - `AuthorizationPayloadLoader` -> `RequestContextDataLoader`
  - `AuthorizationHttpApi` -> `AuthorizationHttpFacade`
- Renamed the related HTTP tests to match the new terminology.

### Human decisions / corrections

- Did not rename the broader package structure because it was already aligned with the assignment and easy to review.
- Focused only on names that were mixing `Api`, `Http`, and `Payload` in a way that obscured their actual role.

### Accepted result

- The package structure remains stable.
- The HTTP layer now uses more specific and internally consistent type names.

## 2026-04-02 - Remove Docker entrypoint script

### Prompt

- Evaluate whether the separate Docker entrypoint shell script is still justified.
- If the image now has only one meaningful runtime mode, simplify the Docker runtime path by moving that responsibility directly into the `Dockerfile`.
- Remove the extra script if it no longer adds value.

### Codex output summary

- Simplified `Dockerfile` so the runtime image now starts the service directly with:
  - `ENTRYPOINT ["java", "-jar", "/opt/authz/authz-policy-engine-0.0.1-SNAPSHOT.jar"]`
- Removed `scripts/container-entrypoint.sh` because it no longer added meaningful value after the earlier cleanup.

### Human decisions / corrections

- Kept the image behavior equivalent by relying on `AuthorizationHttpServerApp` to read the `PORT` environment variable directly.
- Preferred the simpler Dockerfile because this image has only one meaningful runtime mode.

### Accepted result

- The Docker runtime path is simpler and easier to review.
- The extra entrypoint shell script has been removed.

## 2026-04-02 - Fix review findings around request validation and HTTP error handling

### Prompt

- Perform a review-style pass over the current codebase and identify any remaining correctness risks, missing validation, or boundary issues.
- Then implement the necessary fixes.
- Prioritize concrete issues that could lead to incorrect authorization decisions or misleading HTTP behavior.

### Codex output summary

- Tightened core request validation in `PolicyEngine` so direct library calls now fail with explicit `IllegalArgumentException` messages for blank IDs and null permission / timestamp values before any loader call runs.
- Expanded HTTP boundary validation so request/context identity and relationship mismatches are rejected:
  - `request.userId` must match `context.user.id`
  - `request.documentId` must match `context.document.id`
  - `context.project.teamId` must match `context.team.id`
  - `context.document.projectId` must match `context.project.id`
  - membership roles must be null when `exists=false`
- Refined HTTP parsing and error handling so JSON/enum/timestamp input errors remain `400`, while unexpected runtime failures during authorization now produce `500 Internal server error`.
- Added tests for:
  - direct `PolicyEngine` request validation
  - new HTTP request/context consistency validation cases
  - real HTTP server `400` vs `500` behavior

### Human decisions / corrections

- Kept all additional validation at the REST boundary except for the minimal direct request validation needed by the core `PolicyEngine`.
- Added a small end-to-end HTTP server test instead of relying only on facade tests so the status-code split is verified at the actual transport boundary.

### Accepted result

- The previously identified review findings are fixed.
- Core library usage now fails earlier and more clearly on invalid requests.
- The REST wrapper now distinguishes client input errors from internal server failures.

## 2026-04-02 - Clarify production trust boundary in README and DESIGN

### Prompt

- Update both `README.md` and `DESIGN.md` to reflect a more realistic production trust boundary.
- Capture the idea that, in practice, an application server or API gateway would authenticate the user first and then call the authorization layer internally with trusted context.
- Make it clear that direct end-user calls into the authorization service are less typical than an app-server-mediated flow.

### Codex output summary

- Updated `README.md` to explain that the current REST endpoint is a reviewer-facing convenience wrapper, not the preferred production trust boundary.
- Clarified that a more realistic deployment would authenticate users at the app server or API gateway first, then call the authorization layer internally with trusted principal / tenant / resource context.
- Updated `DESIGN.md` with a new deployment-boundary explanation and aligned the trade-offs and limitations sections to reflect that model.

### Human decisions / corrections

- Kept the existing reviewer-friendly REST wrapper in place for local execution and curl-based evaluation.
- Documented the preferred production trust model instead of changing the code path in this step.

### Accepted result

- Both reviewer-facing docs now distinguish the current demo wrapper from the more realistic production architecture.

## 2026-04-02 - Shift REST wrapper to internal-service style request shape

### Prompt

- Refactor the REST wrapper toward an internal-service-style request contract.
- Assume the upstream application server has already authenticated the user and now calls this service with only sanitized identifiers such as:
  - `userId`
  - `tenantId`
  - `documentId`
  - `permission`
  - `requestedAt`
- Move the richer authorization context resolution to the server side while keeping the core engine unchanged.

### Codex output summary

- Replaced the public `request + context` HTTP payload with a minimal internal-style request body:
  - `userId`
  - `tenantId`
  - `documentId`
  - `permission`
  - `requestedAt`
- Added a server-side `AuthorizationContextRepository` and a demo in-memory implementation that resolves the richer authorization context for the six assignment scenarios.
- Updated the HTTP facade so it now:
  - validates only the minimal request body
  - builds the core `AuthorizationRequest`
  - loads trusted authorization context server-side
  - passes that context through a loader into the existing policy engine
- Replaced the old example JSON files under `examples/rest/` with minimal identifier-based request payloads.
- Updated HTTP tests and example tests to match the new contract.
- Updated `README.md` and `DESIGN.md` to explain that this wrapper now resembles an internal authz-service contract more closely, while still using a demo repository for reviewer convenience.

### Human decisions / corrections

- Kept the core policy engine unchanged and confined the contract shift to the HTTP boundary and demo repository layer.
- Used `tenantId` as the trusted internal scoping identifier in the REST contract and mapped it to the demo team fixtures.

### Accepted result

- The REST wrapper no longer accepts caller-supplied authorization context.
- It now receives only sanitized identifiers and resolves the richer authorization facts server-side before evaluation.

## 2026-04-02 - Clarify demo repository vs recommended production data source in README

### Prompt

- Keep the current implementation shape for now.
- Update the README so it explicitly explains:
  - where the current demo data lives
  - how that data is being supplied today
  - what the recommended production data source would be instead
- Keep the change documentation-only.

### Codex output summary

- Kept the current in-memory demo repository approach unchanged.
- Updated `README.md` to explicitly point to `DemoAuthorizationContextRepository.java` as the current location of the reviewer/demo fixture data.
- Added a short explanation that this is convenient for the take-home but not the recommended production shape.
- Clarified that a DB-backed repository, internal service, or other trusted persistence-backed loader would be the recommended next step in a real deployment.

### Human decisions / corrections

- Did not convert the demo repository to JSON or a database-backed implementation in this step.
- Limited the change to reviewer-facing documentation only.

### Accepted result

- `README.md` now explains both the current fixture location and the recommended production direction.

## 2026-04-02 - Restore reviewer-facing full-context REST contract

### Prompt

- Keep the HTTP and Docker paths in place.
- Restore the reviewer-facing REST contract so example requests can mirror the assignment scenarios with full context in one payload.
- At the same time, document the boundary clearly:
  - the current REST wrapper is a reviewer-convenience demo contract
  - in a production design, an authenticated app server would be expected to pass only trusted minimal identifiers into the internal authorization layer

### Codex output summary

- Replaced the internal-style `tenantId` / `documentId` HTTP contract with a reviewer-facing full-context payload that directly accepts `user`, `team`, `project`, `document`, and optional memberships.
- Removed the demo repository path and changed the REST wrapper to build an `AuthorizationRequestContext` directly from the posted payload.
- Replaced the repository-backed HTTP loader adapter with a request-scoped loader that normalizes the posted context into the facts expected by the core policy engine.
- Updated the example JSON files under `examples/rest/` so all six scenarios again resemble the assignment inputs.
- Updated HTTP tests to validate the full-context payload shape and its internal consistency.
- Updated `README.md` and `DESIGN.md` to clearly separate:
  - the current reviewer-facing demo contract
  - the recommended production trust boundary where an app server authenticates first and passes only trusted identifiers to an internal authorization layer

### Human decisions / corrections

- Kept HTTP and Docker in place for reviewer convenience.
- Chose to make the current `/authorize` contract intentionally assignment-shaped again, while documenting that it is not the preferred production boundary.

### Accepted result

- The REST wrapper now matches the assignment scenarios more closely.
- The docs now explicitly describe it as a demo contract and preserve the production recommendation separately.

## 2026-04-02 - Polish DESIGN.md for reviewer readability

### Prompt

- Refine `DESIGN.md` as a whole so it reads like a polished reviewer-facing design document.
- Improve structure, flow, terminology, and section ordering without changing the underlying implementation.
- Make the architecture, DSL, policy model, trust-boundary explanation, and trade-offs easier to scan quickly during review.

### Codex output summary

- Reworked `DESIGN.md` into a cleaner reviewer-facing structure:
  - purpose
  - requirement mapping
  - architecture overview
  - data flow
  - domain and authorization-facing models
  - DSL syntax
  - evaluation semantics
  - policy model
  - ambiguity resolution
  - engine behavior
  - explanation design
  - REST wrapper positioning
  - test coverage
  - trade-offs
  - limitations and future improvements
- Rewrote the architecture and sequence sections so the current implementation boundaries are easier to follow.
- Clarified the distinction between:
  - the core library
  - the reviewer-facing REST demo contract
  - the recommended production trust boundary
- Tightened wording around `EvaluationResult`, `requiredFacts`, normalized facts, and the supplemental policy rationale.

### Human decisions / corrections

- Kept the current implementation model unchanged and limited this step to documentation quality.
- Preserved the optional reviewer-facing HTTP layer in the design write-up instead of pretending the repository is library-only.

### Accepted result

- `DESIGN.md` is now more consistent with the current codebase and easier for a reviewer to scan quickly.

## 2026-04-02 - Improve Docker caching and build context

### Prompt

- Improve Docker caching and build context

### Codex output summary

- Added a BuildKit syntax directive to the `Dockerfile`.
- Split Maven dependency resolution from source-copy layers so dependency downloads can remain cached when only application code changes.
- Added Maven cache mounts for both the dependency prefetch step and the actual test/package step.
- Simplified the runtime jar path to `/opt/authz/authz-policy-engine.jar`.
- Reworked `.dockerignore` into a whitelist-style build context so Docker now sends only the files required for the image build:
  - `.mvn`
  - `mvnw`
  - `pom.xml`
  - `src`
  - `examples`
  - `Dockerfile`
  - `.dockerignore`

### Human decisions / corrections

- Kept tests inside the Docker build so the image still validates the same path it packages.
- Preserved `examples/` in the build context because `RestExamplePayloadsTest` reads those files during the image build.

### Accepted result

- Docker rebuilds should now get better cache reuse for Maven dependencies and a smaller build context overall.

## 2026-04-02 - Remediate Jackson advisory findings

### Prompt

- Resolve the reported transitive dependency vulnerability affecting `com.fasterxml.jackson.core:jackson-core:2.18.3`, including GHSA-72hv-8253-57qq and the related Mend advisory WS-2026-0003.
- Prefer the smallest safe Jackson upgrade that preserves compatibility with the existing codebase and tests.
- Verify the target version against authoritative advisory and release sources, then update Maven dependency management and re-run validation.

### Codex output summary

- Reviewed the current Maven dependency setup and confirmed that all Jackson modules are versioned through the shared `jackson.version` property.
- Determined that the reported `jackson-core` advisory can be remediated by moving from `2.18.3` to the patched `2.18.6` line, which keeps the project on the same compatible maintenance branch.
- Updated the shared Jackson version so `jackson-databind` and `jackson-datatype-jsr310` consistently resolve to the patched transitive `jackson-core` release.

### Human decisions / corrections

- Chose the minimal compatible patch upgrade on the existing Jackson line instead of a larger minor-version jump, to reduce regression risk for the take-home codebase.

### Accepted result

- The project now uses Jackson `2.18.6`, which remediates the reported `jackson-core` advisory without broader code or architecture changes.

## 2026-04-02 - Align internal guidance and demo loader behavior

### Prompt

- Clean up the remaining reviewer-facing rough edges identified in the final review.
- Remove internal-document contradictions that say the repository must not contain HTTP or Docker even though the final submission intentionally includes a thin reviewer-facing REST and Docker path.
- Make the demo request-scoped loader honor `requiredFacts` so the implemented data-loading path actually demonstrates permission-scoped fact loading instead of always materializing the full fact map.
- Reduce noisy test output so green test runs do not print intentional server ERROR stack traces.

### Codex output summary

- Updated `AGENTS.md` so it now describes the actual delivered shape of the repository: a plain Java authorization core plus a thin reviewer-facing HTTP/Docker demo adapter.
- Refactored `RequestContextDataLoader` to build only the normalized fact paths requested through `DataRequirement`, rather than always materializing the entire fact map.
- Added a focused unit test covering request-scoped selective fact materialization.
- Added a test-only `log4j2-test.xml` configuration to suppress intentional HTTP server error logging during green test runs.
- Updated `README.md` and `DESIGN.md` so the docs now accurately state that even the demo loader respects permission-scoped fact loading.

### Human decisions / corrections

- Kept the reviewer-facing full-context REST contract, but tightened the implementation so it better matches the architectural claims already made in the docs.
- Chose a test-only logging configuration instead of changing production logger behavior just to keep CI output cleaner.

### Accepted result

- Internal guidance no longer contradicts the final submission shape.
- The reviewer/demo adapter now better demonstrates the intended `requiredFacts` optimization.
- Test output is cleaner while preserving the same behavior coverage.

## 2026-04-02 - Adopt Zanzibar-style allowed boolean in authorization responses

### Prompt

- Replace the external authorization response contract so it matches a simpler Zanzibar-style `check -> allowed` shape.
- Remove the `outcome: ALLOW | DENY` field from the HTTP/JSON contract and use `allowed: true | false` instead, while keeping policy effects as internal `ALLOW` / `DENY` policy metadata.
- Refactor the Java model, tests, and docs consistently so the naming is clearer to reviewers and less abstract than `outcome`.

### Codex output summary

- Replaced the `AuthorizationDecision` response field `outcome` with a boolean `allowed`.
- Removed the now-unnecessary `DecisionOutcome` enum from the explain model.
- Updated engine construction paths so final authorization responses now serialize as `allowed: true` or `allowed: false`.
- Updated contract tests, scenario tests, REST example verification, and HTTP facade tests to assert the new response shape.
- Refined README and DESIGN wording so internal policy effects remain `ALLOW` / `DENY`, but the external decision contract is described as an `allowed` boolean.

### Human decisions / corrections

- Kept `PolicyEffect.ALLOW` and `PolicyEffect.DENY` unchanged because those belong to policy metadata and evaluation semantics, not the transport contract.
- Chose not to introduce a new enum for the final decision object, since a reviewer-facing boolean maps more directly to the assignment scenarios and Zanzibar-style `check` responses.

### Accepted result

- The authorization response contract is now easier to read: `allowed: true | false` instead of `outcome: ALLOW | DENY`.

## 2026-04-02 - Clarify demo-vs-production auth boundary in README

### Prompt

- Expand the README limitations section so it explains why the current HTTP layer accepts full domain context in the request.
- Make it clear that this was chosen for reviewer convenience, so the assignment scenarios can be replayed directly without additional auth infrastructure or a seeded database.
- Also describe how this should evolve in production: app server or gateway validates the user token, passes only minimal identifiers to the internal authorization layer, and the loader fetches trusted facts from a database or internal services.

### Codex output summary

- Refined the README limitations section to explain the rationale for the current full-context reviewer contract.
- Added an explicit migration path from the current demo wrapper to a production trust boundary based on authenticated caller identity and trusted server-side data loading.
- Preserved the existing description of the request-scoped demo loader while making the intended future architecture more concrete.

### Human decisions / corrections

- Kept the current reviewer-facing contract unchanged in code because it still serves the take-home demonstration well.
- Chose to document the production shape more explicitly rather than prematurely introducing a database or token-validation layer into the submission.

### Accepted result

- README now explains both why the current demo contract exists and how the system should change when moved behind a real app-server trust boundary.

## 2026-04-02 - Tighten DSL and policy validation

### Prompt

- Review the code for remaining reviewer-facing rough edges after the broader architecture work.
- Reject malformed DSL shapes that should never be treated as valid policies, especially empty logical expressions.
- Add explicit validation for policy fact paths so typos in expression field paths or `requiredFacts` fail deterministically instead of degrading into inconsistent runtime behavior.
- Clean up the runnable server entrypoint so invalid port configuration fails with a clear message instead of a raw `NumberFormatException`.

### Codex output summary

- Rejected empty `and` / `or` expressions both in the in-memory DSL records and during JSON deserialization.
- Added a centralized policy fact-path validator that checks:
  - every fact path referenced by a policy condition is supported
  - every `requiredFacts` entry is supported
  - `requiredFacts` fully covers the fact paths referenced by the condition
- Wired that validation into `PolicyDefinition` construction so invalid policies fail immediately.
- Improved HTTP server port parsing so bad `PORT` / `--port` input now fails with a clear `IllegalArgumentException`.
- Added tests for empty logical expressions, invalid policy fact paths, missing required facts, and invalid port configuration.
- Updated `DESIGN.md` so the stricter DSL and policy validation rules are now documented.

### Human decisions / corrections

- Kept the policy-path schema intentionally small and explicit, matching the current normalized fact model instead of introducing a larger dynamic registry abstraction.
- Chose constructor-time policy validation so authoring mistakes fail early and deterministically, which is more appropriate for an interview project than deferring validation to runtime.

### Accepted result

- The DSL is stricter, malformed policy artifacts fail earlier, and the runnable entrypoint now surfaces configuration mistakes more cleanly.

## 2026-04-02 - Polish documentation consistency and reviewer run path

### Prompt

- Review the repository documents for remaining mismatches between the shipped code and the written explanations.
- Prefer small documentation-only refinements that improve reviewer clarity.
- Clarify how a reviewer should run the checked-in jar artifact versus how to rebuild from source.
- Make the design data-flow wording consistent with the current `allowed: true | false` response contract.
- Correct any outdated statements about `requiredFacts` validation or other behavior that is already implemented.
- Update `AI_USAGE_LOG.md` with this documentation refinement step.

### Codex output summary

- Updated `README.md` so the primary Java run path now uses the checked-in `dist/...jar`, while source rebuild remains documented as an optional verification step.
- Split the dense README limitations explanation into smaller reviewer-friendly bullets without changing the technical meaning.
- Updated the `DESIGN.md` sequence diagram so the external response contract matches the current `allowed` boolean shape.
- Replaced an outdated limitation in `DESIGN.md`; the document now reflects that `requiredFacts` are validated eagerly, but still maintained manually.
- Adjusted the future-work wording so it focuses on deriving `requiredFacts` automatically rather than adding validation that already exists.

### Human decisions / corrections

- Kept the overall document structure unchanged because the content was already strong; this step focused only on consistency and readability.
- Left historical AI usage entries untouched, even where they mention older intermediate states, because the log is intended to preserve the actual implementation history.

### Accepted result

- Reviewer-facing documents are now more consistent with the shipped artifact, current HTTP contract, and current policy-validation behavior.
