# authz-policy-engine

## 프로젝트 개요

이 브랜치는 `main`의 제출용 형태와 같은 코어 DSL / policy engine을 유지하면서, HTTP 경계를 조금 더 실서비스에 가깝게 바꿔본 대안 구현입니다.

- `main`: 과제 시나리오를 reviewer가 바로 재현하기 쉽도록, assignment-shaped example payload와 단순한 HTTP wrapper에 초점을 둔 제출용 버전
- `codex/internal-authz-loader` (현재 브랜치): JWT principal + PostgreSQL-backed loader를 사용해, 더 현실적인 permission-check 경계와 server-side data loading을 보여주는 실험 브랜치

이 프로젝트는 협업형 문서 플랫폼을 위한 권한 DSL 시스템 구현입니다.  
정책을 JSON-friendly DSL로 표현하고, 데이터 로딩과 권한 평가를 분리하며, `deny-overrides`와 `default deny`를 적용합니다.

현재 브랜치에는 아래가 포함되어 있습니다.

- 권한 표현식 DSL
- `EvaluationResult(TRUE / FALSE / UNKNOWN)` 기반 evaluator
- policy engine
- decision trace
- JWT principal + PostgreSQL-backed loader를 사용하는 reviewer-friendly REST wrapper
- demo seed data를 추가해볼 수 있는 작은 write API

설계 결정, 정책 해석, 트레이드오프는 [DESIGN.md](DESIGN.md)에 정리되어 있습니다.  
AI 사용 프롬프트, 결과 요약, 사람의 보정 내용은 [AI_USAGE_LOG.md](AI_USAGE_LOG.md)에 기록되어 있습니다.

## 설치 및 실행 방법

요구사항:

- Java 21
- 또는 Docker / Docker Compose

### Docker로 실행

macOS / Linux:

```bash
./scripts/docker-run.sh
```

Windows:

```powershell
.\scripts\docker-run.ps1
```

기본 포트는 `8080`입니다.

Compose는 named volume(`postgres_data`)를 사용하므로, reviewer가 추가한 팀/프로젝트/사용자/문서가 컨테이너 재시작 후에도 유지됩니다.  
seed 상태로 완전히 초기화하려면:

```bash
docker compose down -v
```

### Java로 직접 실행

PostgreSQL이 먼저 실행 중이어야 합니다. 가장 간단한 방법은 DB만 compose로 올리는 것입니다.

```bash
docker compose up -d postgres
java -jar dist/authz-policy-engine-0.0.1-SNAPSHOT.jar
```

기본 JWT secret은 local demo 용으로 `local-dev-jwt-secret`을 사용합니다.  
다른 secret을 쓰려면 `AUTHZ_JWT_SECRET` 환경변수로 덮어쓸 수 있습니다.

소스에서 다시 빌드해 확인하려면:

```bash
./mvnw package
java -jar target/authz-policy-engine-0.0.1-SNAPSHOT.jar
```

## 예제 사용법

헬스체크:

```bash
curl http://localhost:8080/health
```

데모 JWT:

```bash
export DEMO_TOKEN='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1MSIsImlhdCI6MTc3NDkxMTYwMCwiZXhwIjo0MTAyNDQ0ODAwfQ.CamontEZemyd1HDeWeh9HoMgkEzaHMO4WP0GIYwgBXE'
```

권한 확인 요청:

```bash
curl \
  -H "Authorization: Bearer $DEMO_TOKEN" \
  http://localhost:8080/v1/documents/d1/permissions/can_view
```

예상 응답 예시:

```json
{
  "allowed": true,
  "permission": "can_view",
  "decisivePolicyId": "allow_project_member_view",
  "finalReason": "Allowed by policy allow_project_member_view: Project members may view documents in their project unless another deny policy matches."
}
```

reviewer가 seed data를 보강해볼 수 있도록 아래 write API도 제공합니다.

팀 추가:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  --data @examples/write/create-team-t7.json \
  http://localhost:8080/v1/teams
```

프로젝트 추가:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  --data @examples/write/create-project-p7.json \
  http://localhost:8080/v1/projects
```

사용자 추가:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  --data @examples/write/create-user-u3.json \
  http://localhost:8080/v1/users
```

문서 추가:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  --data @examples/write/create-document-d7.json \
  http://localhost:8080/v1/documents
```

새로 만든 문서에 대한 권한 확인:

```bash
curl \
  -H "Authorization: Bearer $DEMO_TOKEN" \
  http://localhost:8080/v1/documents/d7/permissions/can_view
```

샘플 fixture:

- permission-check fixture: [examples/rest](examples/rest/)
- write request fixture: [examples/write](examples/write/)

`examples/rest`는 raw HTTP body가 아니라, reviewer와 테스트가 기대 결과를 읽기 쉽게 하기 위한 minimal request fixture입니다.
현재 형식은 body payload가 아니라:

- HTTP method
- permission-check path
- JWT subject
- expected allow/deny

를 담는 request-spec fixture입니다.

## 개선 가능한 부분 및 제약사항

- 현재 HTTP 레이어는 `GET /v1/documents/{documentId}/permissions/{permission}` 형태의 minimal contract를 사용하고, user principal은 JWT `sub` claim에서 읽습니다.
- PostgreSQL loader가 `document`, `project`, `team`, `teamMembership`, `projectMembership`를 서버 내부에서 조회하므로, 이전 full-context demo contract보다 실서비스 구조에 더 가깝습니다.
- 그래도 이 저장소의 JWT 검증은 reviewer가 바로 실행해볼 수 있도록 단순한 HS256 shared-secret demo 구성을 사용합니다. 실제 배포에서는 보통 앱 서버나 API gateway가 먼저 토큰을 검증한 뒤, 내부 authorization 계층에 최소 식별자만 전달하는 구성이 더 일반적입니다.
- reviewer 편의를 위해 `POST /v1/teams`, `POST /v1/projects`, `POST /v1/users`, `POST /v1/documents`를 추가했지만, 이것은 full CRUD가 아니라 demo data seeding 용도에 가깝습니다. 수정/삭제 API, team-project reassignment, membership removal 같은 관리 연산은 아직 없습니다.
- 현재 write API에는 별도의 write authorization을 아직 걸지 않았습니다. 즉 "누가 team/project/user/document를 생성할 수 있는가"라는 정책은 이번 제출 범위에서 다루지 않았고, reviewer가 seed 데이터를 쉽게 넣어보는 용도로만 열어둔 상태입니다.
- production으로 가져가려면 이 write 경로는 앱 서버나 admin API 뒤로 숨기고, caller principal에 대해 별도의 create/update 권한 정책을 적용해야 합니다. 예를 들면 team admin만 팀 멤버를 추가할 수 있게 하거나, project role에 따라 document creation을 제한하는 방식이 자연스럽습니다.
- 현재 PostgreSQL adapter는 reviewer 실행 편의를 위해 seed data 위에서 동작하지만, 내부적으로는 pooled datasource(HikariCP), batched insert rewrite, PostgreSQL session timeout 설정을 적용한 상태입니다. 다만 여전히 richer repository boundary, read replica, caching, batch authorization 같은 운영 최적화는 남아 있습니다.
- 현재 JDBC write path도 plain transaction + validation 수준에 머물러 있고, 운영 환경에서 기대되는 audit log, idempotency key, retry-safe semantics, richer admin authorization은 포함하지 않았습니다.
- 현재 코어 정책 엔진은 transport-independent하게 유지했기 때문에, 이후에는 내부 gRPC service나 회사 내부 프로토콜로 감싸는 방향으로 바꾸기 쉽습니다.
- trace는 policy 단위 설명에 초점이 맞춰져 있고, 모든 subexpression을 재귀적으로 풀어주지는 않습니다.
- `requiredFacts`는 정책에 명시적으로 적고 있으며, expression tree에서 자동 추론하지는 않습니다.
- bulk authorization, list filtering, cache, external policy store는 아직 구현하지 않았습니다.

## 설계 메모

- 과제 예시의 `boolean | null` 형태와 달리, 현재 evaluator는 `null`을 직접 반환하지 않고 더 명시적인 `EvaluationResult.UNKNOWN`을 내부 표현으로 사용합니다.
- 이 차이는 평가 불가 상태를 더 명시적으로 다루기 위한 설계 선택입니다.
- 추가 배경과 trade-off는 [DESIGN.md](DESIGN.md)에 자세히 정리해두었습니다.
