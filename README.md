# authz-policy-engine

가독성과 테스트 용이성을 위해 두 가지 버전의 작업물을 준비했습니다.  
현재 `main` 브랜치는 과제 시나리오를 reviewer가 가장 직접적으로 따라가볼 수 있도록, 시나리오 JSON을 바로 보내며 정책 결과를 확인하는 제출용 버전입니다. 과제 요구사항과 시나리오 재현성을 우선해 읽기 쉽고 검증하기 쉬운 형태로 정리했습니다.

반면 [`codex/internal-authz-loader`](https://github.com/ilminmoon/permissions-dsl-challenge/tree/codex/internal-authz-loader) 브랜치는 같은 코어 정책 엔진을 더 production-like한 경계로 감싼 대안 버전입니다. 이 브랜치에는 JWT principal, PostgreSQL-backed minimal contract, server-side data loading, reviewer용 write API까지 포함되어 있으니, “실서비스에 더 가까운 HTTP/DB 경계에서는 어떻게 바꿀 수 있는가”를 함께 보시려면 해당 브랜치를 참고해주시면 감사하겠습니다.

## 프로젝트 개요

이 프로젝트는 협업형 문서 플랫폼을 위한 권한 DSL 시스템 구현입니다.  
정책을 JSON-friendly DSL로 표현하고, 데이터 로딩과 권한 평가를 분리하며, deny-overrides와 default deny를 적용합니다.

현재 저장소에는 다음이 포함되어 있습니다.

- 권한 표현식 DSL
- three-valued evaluator (`EvaluationResult`: `TRUE`, `FALSE`, `UNKNOWN`)
- policy engine
- decision trace
- reviewer가 과제 시나리오 JSON을 바로 보내볼 수 있는 얇은 REST API wrapper

상세 설계 결정과 정책 해석은 [DESIGN.md](DESIGN.md)에 정리되어 있습니다.
AI를 사용한 작업 프롬프트, 결과 요약, 사람의 보정 내용은 [AI_USAGE_LOG.md](AI_USAGE_LOG.md)에 기록되어 있습니다.

## 설치 및 실행 방법

요구사항:

- Java 21
- 또는 Docker

### Docker로 실행

macOS / Linux:

```bash
./scripts/docker-run.sh
```

Windows:

```powershell
.\scripts\docker-run.ps1
```

기본 포트는 `8080`이며 Docker가 필요합니다.

### Java로 직접 실행

제출물에 포함된 jar로 바로 실행:

```bash
java -jar dist/authz-policy-engine-0.0.1-SNAPSHOT.jar
```

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

권한 확인 요청:

```bash
curl -X POST http://localhost:8080/v1/permission-checks \
  -H 'Content-Type: application/json' \
  --data @examples/rest/scenario-1-can-view.json
```

예제 요청 body:

```json
{
  "user": { "id": "u1", "email": "user@example.com", "name": "User" },
  "team": { "id": "t1", "name": "Team t1", "plan": "pro" },
  "project": { "id": "p1", "name": "Project p1", "teamId": "t1", "visibility": "private" },
  "document": {
    "id": "d1",
    "title": "Document d1",
    "projectId": "p1",
    "creatorId": "u2",
    "deletedAt": null,
    "publicLinkEnabled": false
  },
  "teamMembership": { "userId": "u1", "teamId": "t1", "role": "viewer" },
  "projectMembership": { "userId": "u1", "projectId": "p1", "role": "editor" },
  "permission": "can_view",
  "requestedAt": "2026-03-31T00:00:00Z"
}
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

샘플 요청 파일은 [examples/rest](examples/rest/) 아래에 있으며, 6개 시나리오 각각에 대해 4개 permission 요청 예제가 들어 있습니다.

## 개선 가능한 부분 및 제약사항

- 현재 HTTP 레이어는 reviewer가 과제 시나리오를 그대로 JSON으로 보내볼 수 있도록 만든 demo contract이며, production service 구조는 아닙니다.
- 현재 `/v1/permission-checks` 엔드포인트는 `user`, `team`, `project`, `document`, `teamMembership`, `projectMembership`를 직접 받습니다.
- 이 형태를 선택한 이유는 reviewer가 과제 시나리오를 거의 그대로 복사해 바로 재현할 수 있고, 별도 인증 인프라나 seed database 없이도 정책 동작을 검증할 수 있기 때문입니다.
- 따라서 이 HTTP contract를 public API처럼 그대로 노출하면 안 됩니다. 현재 wrapper는 payload의 shape와 내부 일관성만 검증할 뿐, caller가 보낸 membership / role / creator 정보의 진실성까지 검증하지는 않습니다.
- 실제로는 보통 앱 서버나 API gateway가 먼저 사용자 토큰을 검증한 뒤, 내부적으로 authorization service 또는 authorization library를 호출합니다.
- 클라이언트가 authorization service에 직접 membership / role / visibility 같은 문맥 전체를 보내는 구조보다는, 앱 서버가 신뢰 경계 역할을 하며 최소 식별자만 정제해서 넘기고, 나머지 권한 판단용 사실은 서버 내부 loader가 trusted source에서 읽는 방식이 더 일반적입니다.
- production 형태로 발전시키려면 다음 순서가 자연스럽습니다.
  - 외부 클라이언트는 앱 서버에만 요청하고, 사용자 identity는 토큰이나 세션으로 확정합니다.
  - 앱 서버는 `userId`, `documentId`, `permission`, 필요 시 `requestedAt` 같은 최소 식별자만 내부 authz 계층에 전달합니다.
  - authz 계층의 loader가 DB, repository, 또는 내부 서비스에서 `document`, `project`, `team`, `memberships`를 조회해 trusted facts를 구성합니다.
  - 현재의 full-context reviewer contract는 debug/demo 전용 경계로만 남기거나 제거합니다.
- 현재 reviewer-facing REST wrapper는 payload로 받은 도메인 엔티티들을 AuthorizationRequestContext.java로 묶고, RequestContextDataLoader.java 가 코어 엔진이 사용하는 normalized facts로 변환합니다. 실제 배포에서는 이 demo contract 대신 별도 DB, repository, 또는 내부 서비스 기반 loader를 붙여서, 최소 식별자 요청만 받고 서버가 trusted data를 직접 조합하는 방향이 권장됩니다.
- HTTP 레이어는 필수 필드, blank 값, enum, timestamp 정도만 얇게 검증하고, 정책 판단 자체는 그대로 코어 엔진에 위임합니다.
- 이번 제출에서는 reviewer가 `curl`과 JSON만으로 바로 검증할 수 있도록 gRPC 대신 REST를 선택했습니다.
- 코어 정책 엔진은 transport-independent하게 유지했기 때문에, 이후에는 앱 서버 뒤의 내부 gRPC 서비스나 해당 회사의 내부 프로토콜로 감싸는 방향으로 바꾸기 쉽습니다.
- production용 데이터 로더는 없고, 현재 REST wrapper는 payload로 받은 context를 request-scoped loader로 변환해 사용합니다.
- 다만 이 request-scoped loader도 `requiredFacts`를 보고 요청된 permission에 필요한 normalized facts만 materialize 하도록 구현되어 있습니다.
- trace는 policy 단위 설명에 초점이 맞춰져 있고, 모든 subexpression을 재귀적으로 풀어주지는 않습니다.
- `requiredFacts`는 정책에 명시적으로 적고 있으며, expression tree에서 자동 추론하지는 않습니다.
- bulk authorization, list filtering, cache, external policy store는 아직 구현하지 않았습니다.

## 설계 메모

- 과제 예시의 `boolean | null` 형태와 달리, 현재 evaluator는 `null`을 직접 반환하지 않고 더 명시적인 `EvaluationResult.UNKNOWN`을 내부 표현으로 사용합니다.
- 이 차이는 평가 불가 상태를 더 명시적으로 다루기 위한 설계 선택입니다.
- 왜 이렇게 바꿨는지와 그에 따른 trade-off 등 추가적인 내용은 [DESIGN.md](DESIGN.md)에 자세히 정리해두었습니다.
