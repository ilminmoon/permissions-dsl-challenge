# Project guidance for Codex

## What this repo is
This repository is a hiring take-home assignment for designing a policy-based authorization system with a serializable DSL.
The goal is to demonstrate strong software design, clear architecture, explainability, and sound Java implementation choices.

## Source of truth
Read these files first and treat them as the specification:
1. `README.md`
2. `references/*`
3. `agents/PROMPT.md`
4. `agents/PLAN.md`

If instructions conflict, use this priority:
- `agents/PROMPT.md`
- `agents/PLAN.md`
- this `AGENTS.md`
- anything inferred from existing code

## Tech stack
- Java 21
- Maven Wrapper
- JUnit 5
- Jackson for JSON serialization
- No Spring Boot
- Core authorization engine as a plain Java library, plus a thin reviewer-facing HTTP/Docker demo adapter

## Non-goals
- Do not add databases, UI, or additional service frameworks beyond the existing thin HTTP/Docker demo adapter.
- Do not over-engineer with unnecessary frameworks.
- Do not introduce complex abstractions unless they directly improve clarity or extensibility.

## Required architecture rules
- Separate data loading from policy evaluation completely.
- Policies must be declarative and serializable to a JSON-friendly DSL.
- Keep the structure language-independent at the architecture level.
- Deny policies must override allow policies.
- Default decision is deny.
- Favor immutable models.
- Prefer `record`, `enum`, and `sealed interface` where appropriate.

## Java implementation rules
- Internal expression evaluation must use a tri-state result:
  - `TRUE`
  - `FALSE`
  - `UNKNOWN`
- Use `UNKNOWN` instead of Java `null` inside the evaluator.
- Only adapt to `Boolean | null` style semantics at boundaries if needed for README compatibility.
- Use `Instant` for date/time values unless a clearly better alternative is required.
- Keep packages small and interview-friendly.

## Expected package structure
src/main/java/com/example/authz/
  domain/
  dsl/
  policy/
  engine/
  loader/
  explain/
  http/

src/test/java/com/example/authz/
  dsl/
  policy/
  engine/
  scenario/

## Output expectations
You must produce or update:
- `DESIGN.md` (mandatory, high quality)
- `README.md` (project overview, run/test instructions, usage)
- `AI_USAGE_LOG.md` (log prompts, outputs, and human edits)
- Java source and tests

## Coding quality bar
- Keep names precise and domain-oriented.
- Every public type should have a clear responsibility.
- Prefer straightforward code over clever code.
- When changing architecture, update docs immediately.
- Add tests for every meaningful behavior.

## Validation rules
After each milestone:
1. run `./mvnw test`
2. fix failing tests before moving on
3. update docs if behavior or structure changed
4. record the step in `AI_USAGE_LOG.md`

## Decision trace requirement
Authorization results should include an explanation or decision trace showing:
- matching policies
- evaluation outcomes
- final allow/deny reason

## Important domain rules from the assignment
- Deleted documents cannot be edited, deleted, or shared.
- Document creators have all permissions.
- Project editor/admin can edit.
- Team admin can view/edit/share docs in all projects of the team.
- Private project documents are accessible only to project members or team admin.
- Free plan teams cannot change sharing settings.
- Documents with `publicLinkEnabled = true` can be viewed by anyone.

## How to work
- First inspect the repository and summarize the task in your own words.
- Then create or refine `agents/PLAN.md` into small milestones.
- Do not start broad implementation before the plan is written.
- Keep diffs scoped to the active milestone.
- If uncertain, choose the simplest design that satisfies the assignment well.
