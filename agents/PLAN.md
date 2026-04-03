# Execution plan

## Milestone 0 — Analyze and align
### Tasks
- Read `README.md` and all files in `references/`.
- Summarize the assignment constraints in writing.
- Propose the package structure and main design decisions.
- Draft `DESIGN.md` before coding.

### Acceptance criteria
- `DESIGN.md` draft exists.
- All 7 policies appear in a DSL representation.
- Package structure and responsibilities are explicit.

### Validation
- Manual review of `DESIGN.md`

---

## Milestone 0.5

resolve assignment ambiguities before implementation.

The 6 scenario expectations are part of the source of truth and must be reconciled with the 7 mandatory policies under deny-overrides + default-deny semantics.

For this turn:
1. Re-read the 6 scenario expectations carefully.
2. Identify the minimum additional baseline allow policies required so that the scenarios are internally consistent.
3. Do not weaken deny-overrides or default-deny.
4. Preserve the original 7 mandatory policies exactly as assignment-required policies.
5. Add only the smallest necessary supplemental policies or rule interpretations.
6. Update DESIGN.md with:
   - an "Ambiguity Resolution" section,
   - the supplemental policies,
   - why they are necessary,
   - why they do not contradict the mandatory 7 policies,
   - which scenarios each supplemental policy is needed for.
7. Record this refinement step in AI_USAGE_LOG.md.
8. Do not move these interpretation details into README.md. README.md, if added later, should only briefly point readers to DESIGN.md for policy decisions and trade-offs.

Important interpretation target:
- project members should likely be able to view documents in their project unless explicitly denied
- on paid plans, eligible project members may be allowed to change sharing settings unless explicitly denied
- publicLinkEnabled=true should grant can_view without granting edit/delete/share
- private project deny should still block non-members except team admins and the public-link view carve-out

Output:
- the exact supplemental policies you added
- which scenarios they unlock
- confirmation that deny-overrides and default-deny are still preserved

Do not start Java implementation yet.


## Milestone 1 — Project skeleton

Use the current DESIGN.md as the source of truth, including the ambiguity resolutions and supplemental policies.

Tasks:
- create the Maven project skeleton
- add `pom.xml`
- create package directories and minimal type skeletons
- keep the code compile-friendly and simple
- do not implement evaluator or engine logic yet
- add or update tests so `./mvnw test` passes
- update `AI_USAGE_LOG.md`

At the end, report changed files and test results.
---

## Milestone 2 — Expression DSL model

Proceed with Milestone 2 from `agents/PLAN.md` only.

Implement the expression DSL model in Java 21, using the current DESIGN.md as the source of truth.

Scope for this milestone:
- implement the DSL type model only
- do not implement expression evaluation logic yet
- do not implement policy engine behavior yet

Requirements:
- `sealed interface Expression`
- comparison expressions
- logical expressions: `and`, `or`, `not`
- field reference support for values such as `{ "ref": "user.id" }`
- JSON-friendly modeling with Jackson
- immutable design using records / enums / sealed interfaces where appropriate
- keep the DSL structure aligned with the assignment examples and DESIGN.md

Modeling goals:
- support serializable forms equivalent to:
  - `["user.id", "=", "123"]`
  - `{ "and": [ ... ] }`
  - `{ "or": [ ... ] }`
  - `{ "not": ... }`
  - `{ "ref": "field.name" }`
- support value types needed by the assignment:
  - string
  - number
  - boolean
  - null
  - date-compatible representation for later evaluation

Tests:
- add serialization/deserialization round-trip tests with Jackson
- add shape/contract tests for:
  - simple comparison
  - nested and/or/not
  - field reference operand
  - null literal
  - date-compatible literal representation
- keep `./mvnw test` passing

Constraints:
- keep the model interview-friendly and simple
- avoid premature evaluator logic
- avoid over-engineering custom serializers unless truly necessary
- if a design trade-off is needed for Jackson compatibility, choose the simpler shape and note it briefly in AI_USAGE_LOG.md

Update `AI_USAGE_LOG.md`.

At the end, report:
- changed files
- any DSL shape decisions made for Jackson compatibility
- test results

---

## Milestone 3 — Expression evaluator

Implement the expression evaluator, using the current DESIGN.md and DSL model as the source of truth.

Scope:
- implement evaluator behavior only
- do not implement policy engine behavior yet

Requirements:
- tri-state result: TRUE, FALSE, UNKNOWN
- support `and`, `or`, `not`
- support field reference resolution
- support comparisons for:
  - string
  - number
  - boolean
  - null
  - date/time
- missing data must produce UNKNOWN
- evaluator must remain independent from the policy engine

Evaluation contract:
- define a clear evaluator input shape for facts/data
- support expressions such as:
  - `["user.id", "eq", "123"]`
  - `{ "and": [ ... ] }`
  - `{ "or": [ ... ] }`
  - `{ "not": ... }`
  - `{ "ref": "field.name" }`
- support ref-to-literal and ref-to-ref comparisons
- null handling must be explicit and testable
- date/time literals may be represented as strings in the DSL, but evaluator behavior must define how they are parsed and compared
- if parsing or type coercion rules are needed, keep them minimal, deterministic, and documented in code/tests

Constraints:
- do not couple evaluator to authorization policies
- do not add ad-hoc policy-specific shortcuts
- keep implementation simple and interview-friendly
- prefer small focused helper methods over over-abstraction

Tests:
- include the assignment examples
- add at least 8 additional unit tests
- cover:
  - missing data -> UNKNOWN
  - null comparison
  - date comparison
  - nested logic
  - ref-to-ref comparison
  - numeric comparison
  - boolean comparison
  - mixed known/unknown cases for and/or/not
- keep `./mvnw test` passing

Update `AI_USAGE_LOG.md`.

At the end, report:
- changed files
- evaluator input/data shape you chose
- date/time comparison rule you implemented
- test results

---

## Milestone 4 — Policy model and registry

Implement the policy system and policy engine using the current DESIGN.md as the source of truth, including:
- the 7 mandatory assignment policies
- the approved supplemental policies from the ambiguity-resolution step
- deny-overrides semantics
- default deny
- explanation/trace support

Scope:
- implement policy definitions
- implement policy selection and evaluation orchestration
- implement loader/engine separation
- do not add unrelated features

Requirements:
1. Implement first-class policy definitions for:
   - the 7 mandatory policies
   - the approved supplemental policies
2. Implement policy filtering by requested permission.
3. Determine required data from the applicable policies.
4. Keep data loading separate from policy evaluation.
5. Evaluate deny policies first.
6. If no deny matched, evaluate allow policies.
7. If no allow matched, return deny by default.
8. Produce a usable authorization decision with trace/explanation output.
9. Keep the public-link can_view behavior explicit and policy-driven, not as an undocumented hidden shortcut.
10. Keep the free-plan can_share deny explicit and overriding.
11. Preserve the current standalone evaluator; the policy engine should call it, not duplicate its logic.

Implementation guidance:
- keep `AuthorizationDataLoader` as a boundary
- use a simple fake/in-memory loader for tests
- keep the engine interview-friendly and readable
- prefer simple orchestration over premature optimization
- short-circuit where appropriate once a final deny decision is known

Tests:
- implement scenario-based tests for the 6 assignment scenarios
- add explicit tests for:
  - deny overrides allow
  - free-plan share deny
  - deleted document denies edit/delete/share
  - creator allow
  - team admin access to private project documents
  - non-member denied for private project unless public-link can_view applies
- keep `./mvnw test` passing

Update `AI_USAGE_LOG.md`.

At the end, report:
- changed files
- how policies are represented in code
- how required-data determination works
- what the authorization decision / trace output contains
- test results

---

## Milestone 5 — Policy engine
### Tasks
- Implement authorization request/context objects.
- Implement data loader contract.
- Implement policy evaluation orchestration.
- Evaluate deny policies first, then allow policies, else default deny.
- Add explanation trace output.

### Acceptance criteria
- Engine works with fake loader.
- Final decisions include policy reasoning.

### Validation
- `./mvnw test`

---

## Milestone 6 — Scenario tests
### Tasks
- Implement the 6 assignment scenarios as integration tests.
- Add extra tests for deny precedence and free-plan share denial.

### Acceptance criteria
- All scenario expectations pass.

### Validation
- `./mvnw test`

---

## Milestone 7 — Documentation and submission polish
### Tasks
- Finalize `DESIGN.md`.
- Write `README.md`.
- Update `AI_USAGE_LOG.md`.
- Review package names, comments, and examples.

### Acceptance criteria
- The repository is ready for reviewer handoff.

### Validation
- `./mvnw test`
- quick manual read of docs

---

## Stop-and-fix rule
If validation fails at any milestone, stop and repair before continuing.
Do not continue with failing tests.

## Scope control
Do not expand scope beyond the assignment.
Prefer a strong, reviewable core implementation over partial extra features.
