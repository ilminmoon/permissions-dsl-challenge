# Oso Cloud와 Polar 정책 언어

**출처:** Oso Cloud Documentation (https://www.osohq.com/docs)

---

## 개요

**Oso Cloud**는 애플리케이션을 위한 중앙화된 권한 부여 서비스입니다. **Polar**라는 선언적 로직 프로그래밍 언어를 사용하여 권한 정책을 정의합니다.

### 핵심 특징

- **다양한 권한 모델 지원**: RBAC, ReBAC, ABAC
- **중앙화된 정책 관리**: 정책을 코드와 분리
- **성능**: 로컬 권한 부여 및 캐싱 지원
- **통합 용이**: 다양한 언어 SDK (Node.js, Python, Ruby, Java, Go, .NET)
- **감사 및 디버깅**: 로그, 정책 미리보기, 성능 분석

---

## 1. 핵심 개념

### 1.1 Actor (행위자)

권한을 요청하는 주체입니다.

- **사용자 (User)**: 일반적인 사용자
- **서비스 (Service)**: API 키, 서비스 계정
- **디바이스 (Device)**: IoT 기기 등

**예시:**
```polar
actor User {
  roles = ["admin", "member", "guest"];
}
```

### 1.2 Resource (리소스)

보호되는 객체입니다.

- **문서 (Document)**
- **프로젝트 (Project)**
- **조직 (Organization)**
- **폴더 (Folder)**

**예시:**
```polar
resource Organization {
  roles = ["owner", "member", "viewer"];
  permissions = ["read", "update", "delete", "invite"];

  "read" if "viewer";
  "update" if "member";
  "delete" if "owner";
}
```

### 1.3 Roles (역할)

리소스에 대한 명명된 권한 세트입니다.

**일반적인 역할:**
- `owner`: 모든 권한
- `admin`: 관리 권한
- `member`: 일반 멤버 권한
- `viewer`: 읽기 전용 권한
- `guest`: 제한된 권한

### 1.4 Permissions (권한)

리소스에 대해 수행할 수 있는 구체적인 작업입니다.

**CRUD 권한:**
- `create`: 생성
- `read`: 읽기
- `update`: 수정
- `delete`: 삭제

**기타 권한:**
- `share`: 공유
- `invite`: 초대
- `manage`: 관리
- `execute`: 실행

### 1.5 Relations (관계)

리소스 간의 연결을 나타냅니다.

**계층 관계:**
```
Organization → Project → Document
```

**소유 관계:**
```
User owns Document
Document belongs to Organization
```

---

## 2. Polar 언어 기초

### 2.1 규칙 (Rules)

Polar의 기본 구성 요소는 **규칙**입니다.

**구문:**
```polar
head if body;
```

**예시:**
```polar
# 간단한 권한 규칙
allow(actor: User, "read", resource: Document) if
  has_role(actor, "viewer", resource);

# 역할 상속
has_role(user, "admin", resource) if
  has_role(user, "owner", resource);
```

### 2.2 팩트 (Facts)

팩트는 데이터베이스에 저장된 사실입니다.

**예시:**
```polar
# 사용자 alice는 document:1의 editor
has_role(User{id: "alice"}, "editor", Document{id: "1"});

# document:1은 org:acme에 속함
has_relation(Document{id: "1"}, "parent", Organization{id: "acme"});
```

**팩트 삽입 (SDK):**
```javascript
// Node.js
await oso.tell("has_role", alice, "editor", document1);
await oso.tell("has_relation", document1, "parent", orgAcme);
```

### 2.3 쿼리 (Queries)

권한을 확인하거나 데이터를 조회합니다.

**권한 확인:**
```javascript
// alice가 document1을 읽을 수 있는가?
const allowed = await oso.authorize(alice, "read", document1);
```

**데이터 조회:**
```javascript
// alice가 읽을 수 있는 모든 문서
const documents = await oso.list(alice, "read", Document);
```

### 2.4 변수와 타입

**변수:**
```polar
# 소문자로 시작
allow(actor, action, resource) if ...

# 언더스코어는 익명 변수
allow(_, "read", public_resource) if ...
```

**타입 지정:**
```polar
# 타입 제약
allow(actor: User, "read", resource: Document) if
  has_role(actor, "viewer", resource);
```

---

## 3. Resource Blocks

Resource blocks는 리소스 타입에 대한 역할과 권한을 정의합니다.

### 3.1 기본 구조

```polar
resource Organization {
  # 역할 정의
  roles = ["owner", "member", "viewer"];

  # 권한 정의
  permissions = ["read", "update", "delete", "invite"];

  # 권한-역할 매핑
  "read" if "viewer";
  "update" if "member";
  "delete" if "owner";
  "invite" if "member";

  # 역할 상속
  "member" if "owner";
  "viewer" if "member";
}
```

### 3.2 관계 기반 권한

```polar
resource Document {
  roles = ["owner", "editor", "viewer"];
  permissions = ["read", "write", "delete", "share"];

  # 관계 정의
  relations = {
    parent: Organization
  };

  # 권한 규칙
  "read" if "viewer";
  "write" if "editor";
  "delete" if "owner";
  "share" if "editor";

  # 역할 상속
  "editor" if "owner";
  "viewer" if "editor";

  # 부모 조직의 멤버는 문서를 볼 수 있음
  "viewer" if "member" on "parent";
}
```

**의미:**
- `"viewer" if "member" on "parent"`:
  - 부모(parent) 리소스에서 "member" 역할을 가진 사용자는
  - 이 문서에서 "viewer" 역할을 가짐

### 3.3 복잡한 상속 예시

```polar
resource Organization {
  roles = ["owner", "admin", "member", "billing_admin"];
  permissions = ["read", "update", "delete", "manage_billing"];

  "read" if "member";
  "update" if "admin";
  "delete" if "owner";
  "manage_billing" if "billing_admin";

  "admin" if "owner";
  "member" if "admin";
}

resource Project {
  roles = ["maintainer", "contributor", "viewer"];
  permissions = ["read", "write", "delete", "invite"];

  relations = {
    parent: Organization
  };

  "read" if "viewer";
  "write" if "contributor";
  "delete" if "maintainer";
  "invite" if "maintainer";

  "contributor" if "maintainer";
  "viewer" if "contributor";

  # 조직의 admin은 프로젝트의 maintainer
  "maintainer" if "admin" on "parent";

  # 조직의 member는 프로젝트의 viewer
  "viewer" if "member" on "parent";
}

resource Document {
  roles = ["owner", "editor", "commenter", "viewer"];
  permissions = ["read", "write", "comment", "delete", "share"];

  relations = {
    parent: Project
  };

  "read" if "viewer";
  "write" if "editor";
  "comment" if "commenter";
  "delete" if "owner";
  "share" if "editor";

  "editor" if "owner";
  "commenter" if "editor";
  "viewer" if "commenter";

  # 프로젝트 권한 상속
  "owner" if "maintainer" on "parent";
  "editor" if "contributor" on "parent";
  "viewer" if "viewer" on "parent";
}
```

**계층 구조:**
```
Organization (owner/admin/member)
  ↓
Project (maintainer/contributor/viewer)
  ↓
Document (owner/editor/commenter/viewer)
```

---

## 4. 고급 기능

### 4.1 조건부 역할

특정 조건에서만 역할을 부여합니다.

```polar
resource Document {
  roles = ["owner", "editor", "viewer", "public_viewer"];
  permissions = ["read", "write", "delete"];

  "read" if "viewer";
  "write" if "editor";
  "delete" if "owner";

  # 문서가 공개이면 누구나 볼 수 있음
  "public_viewer" if resource.is_public = true;
  "viewer" if "public_viewer";
}
```

**팩트:**
```polar
# document:1은 공개 문서
has_attribute(Document{id: "1"}, "is_public", true);
```

### 4.2 속성 기반 권한 (ABAC)

리소스나 사용자의 속성을 기반으로 권한을 결정합니다.

```polar
resource Document {
  roles = ["owner", "editor", "viewer"];
  permissions = ["read", "write", "delete"];

  "read" if "viewer";
  "write" if "editor";
  "delete" if "owner";

  # 같은 부서의 사용자는 읽을 수 있음
  "viewer" if actor.department = resource.department;

  # 민감하지 않은 문서는 누구나 읽을 수 있음
  "viewer" if resource.sensitivity = "low";
}
```

### 4.3 시간 기반 권한

시간 제약을 가진 권한입니다.

```polar
resource Document {
  roles = ["owner", "temporary_editor"];
  permissions = ["read", "write"];

  "read" if "temporary_editor";
  "write" if "temporary_editor";

  # 임시 편집자는 만료 시간 전까지만 권한 보유
  "temporary_editor" if
    has_role(actor, "temporary_editor", resource) and
    resource.editor_expires_at > now();
}
```

### 4.4 사용자 그룹

그룹을 통한 권한 관리입니다.

```polar
resource Group {
  roles = ["member"];
  permissions = ["read"];

  "read" if "member";
}

resource Document {
  roles = ["owner", "viewer"];
  permissions = ["read", "write"];

  relations = {
    shared_with: Group
  };

  "read" if "viewer";
  "write" if "owner";

  # 그룹의 멤버는 문서를 볼 수 있음
  "viewer" if "member" on "shared_with";
}
```

**팩트:**
```polar
# alice는 group:eng의 멤버
has_role(User{id: "alice"}, "member", Group{id: "eng"});

# document:1은 group:eng와 공유됨
has_relation(Document{id: "1"}, "shared_with", Group{id: "eng"});

# 결과: alice는 document:1을 읽을 수 있음
```

### 4.5 위임 (Impersonation)

관리자가 다른 사용자로서 행동하는 기능입니다.

```polar
allow(actor: User, action, resource) if
  has_permission(actor, action, resource);

allow(admin: User, action, resource) if
  has_role(admin, "admin", Organization{}) and
  impersonating(admin, user) and
  has_permission(user, action, resource);
```

### 4.6 커스텀 역할

사용자 정의 역할을 지원합니다.

```polar
resource Organization {
  roles = ["owner", "custom"];
  permissions = ["read", "write", "delete", "manage_users"];

  "read" if "custom";

  # 커스텀 역할의 권한은 동적으로 결정
  permission if
    has_role(actor, "custom", resource) and
    has_custom_permission(actor, resource, permission);
}
```

**팩트:**
```polar
# alice는 org:acme에서 커스텀 역할
has_role(User{id: "alice"}, "custom", Organization{id: "acme"});

# alice의 커스텀 권한
has_custom_permission(
  User{id: "alice"},
  Organization{id: "acme"},
  "read"
);
has_custom_permission(
  User{id: "alice"},
  Organization{id: "acme"},
  "write"
);
```

---

## 5. 정책 패턴

### 5.1 리소스 생성 권한

새 리소스를 생성할 수 있는 권한을 정의합니다.

```polar
# 조직의 멤버는 프로젝트를 생성할 수 있음
allow(actor: User, "create", resource: Project) if
  has_relation(resource, "parent", org) and
  has_role(actor, "member", org);
```

### 5.2 다단계 승인

여러 사람의 승인이 필요한 작업입니다.

```polar
resource Document {
  permissions = ["approve", "publish"];

  "publish" if
    count_approvals(resource) >= 2 and
    has_role(actor, "editor", resource);
}

# 승인 카운트 헬퍼 함수
count_approvals(resource) := count if
  count = count(
    approval:
    has_fact(Approval{resource_id: resource.id, status: "approved"})
  );
```

### 5.3 리소스 공유

리소스를 다른 사용자와 공유합니다.

```polar
resource Document {
  roles = ["owner", "editor", "viewer"];
  permissions = ["read", "write", "share"];

  "read" if "viewer";
  "write" if "editor";
  "share" if "owner";

  # 공유를 통한 권한 부여
  role if has_share(actor, resource, role);
}
```

**팩트:**
```polar
# alice가 bob에게 document:1을 editor로 공유
has_share(User{id: "bob"}, Document{id: "1"}, "editor");
```

### 5.4 조직 계층

여러 레벨의 조직 구조를 지원합니다.

```polar
resource Organization {
  roles = ["owner", "member"];
  permissions = ["read", "update"];

  relations = {
    parent: Organization
  };

  "read" if "member";
  "update" if "owner";

  # 부모 조직의 멤버는 하위 조직도 볼 수 있음
  "member" if "member" on "parent";
}
```

---

## 6. 로컬 권한 부여

### 6.1 로컬 권한 부여란?

권한 체크를 애플리케이션 내에서 직접 수행합니다.

**장점:**
- 낮은 지연 시간
- 네트워크 호출 불필요
- 오프라인 동작 가능

**단점:**
- 정책과 팩트를 로컬에서 관리
- 동기화 필요

### 6.2 구현

```javascript
// Oso 클라이언트 초기화
const { Oso } = require('oso-cloud');
const oso = new Oso('https://cloud.osohq.com', apiKey);

// 정책 다운로드
await oso.policy.download();

// 팩트 동기화
await oso.data.sync();

// 로컬 권한 부여
const allowed = await oso.authorizeLocal(actor, action, resource);
```

### 6.3 SQLite 통합

로컬 데이터베이스를 사용하여 팩트를 저장합니다.

```javascript
const { Oso } = require('oso-cloud');

const oso = new Oso({
  url: 'https://cloud.osohq.com',
  apiKey: apiKey,
  dataBindings: 'sqlite:///path/to/database.db'
});

// SQL 쿼리로 팩트 동기화
await oso.data.syncFromSQL(`
  SELECT user_id, role, resource_type, resource_id
  FROM user_roles
`);

// 로컬 권한 부여 (SQLite 쿼리 사용)
const allowed = await oso.authorizeLocal(user, 'read', document);
```

---

## 7. SDK 사용법

### 7.1 Node.js

**설치:**
```bash
npm install oso-cloud
```

**초기화:**
```javascript
const { Oso } = require('oso-cloud');

const oso = new Oso('https://cloud.osohq.com', process.env.OSO_API_KEY);
```

**권한 확인:**
```javascript
const user = { type: 'User', id: 'alice' };
const document = { type: 'Document', id: '1' };

const allowed = await oso.authorize(user, 'read', document);

if (allowed) {
  // 문서 읽기 허용
} else {
  // 접근 거부
}
```

**리스트 필터링:**
```javascript
// alice가 읽을 수 있는 모든 문서
const documents = await oso.list(user, 'read', 'Document');
```

**팩트 관리:**
```javascript
// 팩트 추가
await oso.tell('has_role', user, 'editor', document);

// 팩트 삭제
await oso.delete('has_role', user, 'editor', document);

// 팩트 조회
const roles = await oso.query('has_role', user, '_role', document);
```

### 7.2 Python

**설치:**
```bash
pip install oso-cloud
```

**초기화:**
```python
from oso_cloud import Oso

oso = Oso(url="https://cloud.osohq.com", api_key=os.environ["OSO_API_KEY"])
```

**권한 확인:**
```python
user = {"type": "User", "id": "alice"}
document = {"type": "Document", "id": "1"}

allowed = await oso.authorize(user, "read", document)

if allowed:
    # 문서 읽기 허용
else:
    # 접근 거부
```

**리스트 필터링:**
```python
# alice가 읽을 수 있는 모든 문서
documents = await oso.list(user, "read", "Document")
```

### 7.3 Ruby

**설치:**
```bash
gem install oso-cloud
```

**사용:**
```ruby
require 'oso-cloud'

oso = OsoCloud::Client.new(
  url: 'https://cloud.osohq.com',
  api_key: ENV['OSO_API_KEY']
)

user = { type: 'User', id: 'alice' }
document = { type: 'Document', id: '1' }

allowed = oso.authorize(user, 'read', document)

if allowed
  # 문서 읽기 허용
else
  # 접근 거부
end
```

---

## 8. 정책 미리보기 및 디버깅

### 8.1 Policy Preview

정책 변경의 영향을 미리 확인합니다.

```bash
# CLI 설치
npm install -g oso-cloud-cli

# 정책 미리보기
oso-cloud preview policy.polar

# 성능 벤치마크
oso-cloud benchmark policy.polar
```

**출력:**
```
Policy Preview Results:
- Total rules: 15
- Authorization checks: 1,234/s
- List queries: 567/s
- Average latency: 2.3ms
```

### 8.2 디버깅

**로그 활성화:**
```javascript
const oso = new Oso({
  url: 'https://cloud.osohq.com',
  apiKey: apiKey,
  logLevel: 'debug'
});
```

**쿼리 설명:**
```javascript
const explanation = await oso.explain(user, 'read', document);
console.log(explanation);
```

**출력:**
```
Authorization Decision: ALLOWED

Evaluation Path:
1. Checking: allow(User{id: "alice"}, "read", Document{id: "1"})
2. Rule matched: "read" if "viewer"
3. Checking: has_role(User{id: "alice"}, "viewer", Document{id: "1"})
4. Fact found: has_role(User{id: "alice"}, "editor", Document{id: "1"})
5. Rule matched: "viewer" if "editor"
6. Result: ALLOWED
```

### 8.3 테스트

정책 단위 테스트를 작성합니다.

```polar
# tests/document_policy.polar

test "owner can delete document" {
  setup {
    has_role(User{id: "alice"}, "owner", Document{id: "1"});
  }
  assert allow(User{id: "alice"}, "delete", Document{id: "1"});
}

test "viewer cannot delete document" {
  setup {
    has_role(User{id: "bob"}, "viewer", Document{id: "1"});
  }
  assert_not allow(User{id: "bob"}, "delete", Document{id: "1"});
}

test "parent org member can view document" {
  setup {
    has_role(User{id: "charlie"}, "member", Organization{id: "acme"});
    has_relation(Document{id: "1"}, "parent", Organization{id: "acme"});
  }
  assert allow(User{id: "charlie"}, "read", Document{id: "1"});
}
```

**테스트 실행:**
```bash
oso-cloud test tests/document_policy.polar
```

---

## 9. 모범 사례

### 9.1 정책 설계

**간단하게 시작:**
```polar
# 좋음: 명확한 역할 계층
resource Document {
  roles = ["owner", "editor", "viewer"];
  "editor" if "owner";
  "viewer" if "editor";
}

# 피하기: 너무 복잡한 규칙
resource Document {
  permission if
    (has_role(actor, "editor", resource) or
     has_role(actor, "viewer", resource)) and
    not resource.is_archived and
    resource.created_at > actor.joined_at;
}
```

**재사용 가능한 패턴:**
```polar
# 공통 패턴을 함수로 추출
is_org_member(user, resource) if
  has_relation(resource, "parent", org) and
  has_role(user, "member", org);

resource Document {
  "viewer" if is_org_member(actor, resource);
}

resource Project {
  "viewer" if is_org_member(actor, resource);
}
```

### 9.2 성능 최적화

**인덱싱:**
```polar
# 자주 조회되는 관계는 인덱스 추가
resource Document {
  relations = {
    parent: Organization,
    shared_with: [User]  # 인덱스 힌트
  };
}
```

**배치 처리:**
```javascript
// 좋음: 배치 권한 확인
const results = await oso.authorizeBatch([
  { actor: user, action: 'read', resource: doc1 },
  { actor: user, action: 'read', resource: doc2 },
  { actor: user, action: 'read', resource: doc3 }
]);

// 피하기: 개별 확인
for (const doc of documents) {
  await oso.authorize(user, 'read', doc);  // N+1 문제
}
```

**리스트 필터링 사용:**
```javascript
// 좋음: 서버 측 필터링
const docs = await oso.list(user, 'read', 'Document');

// 피하기: 클라이언트 측 필터링
const allDocs = await getAllDocuments();
const filtered = [];
for (const doc of allDocs) {
  if (await oso.authorize(user, 'read', doc)) {
    filtered.push(doc);
  }
}
```

### 9.3 보안

**최소 권한 원칙:**
```polar
# 기본적으로 거부, 명시적으로 허용
default allow(_, _, _) = false;

allow(actor, action, resource) if
  has_permission(actor, action, resource);
```

**민감한 작업 보호:**
```polar
resource User {
  permissions = ["read", "update", "delete"];

  "read" if "viewer";
  "update" if "owner";

  # 삭제는 추가 확인 필요
  "delete" if
    "owner" and
    actor.is_admin = true and
    not resource.is_protected;
}
```

---

## 10. Oso vs 다른 시스템 비교

### 10.1 vs Zanzibar

**Zanzibar (Google):**
- 관계 튜플 기반
- 글로벌 분산 스토리지 (Spanner)
- 매우 높은 확장성

**Oso:**
- Polar 정책 언어 (더 표현력 있음)
- 관리형 서비스 (설정 간단)
- 로컬 권한 부여 지원

### 10.2 vs OPA

**OPA (Open Policy Agent):**
- Rego 언어 (범용 정책)
- 자체 호스팅 필요
- Kubernetes 통합 강함

**Oso:**
- Polar 언어 (권한 특화)
- 관리형 클라우드 서비스
- 애플리케이션 권한에 최적화

### 10.3 vs 자체 구현

**자체 ACL 시스템:**
- 완전한 제어
- 초기 구현 빠름

**Oso:**
- 즉시 사용 가능
- 검증된 패턴
- 확장성 보장
- 감사 및 디버깅 도구

---

## 11. 실제 사용 사례

### 11.1 SaaS 애플리케이션

```polar
resource Organization {
  roles = ["owner", "admin", "member"];
  permissions = ["read", "update", "delete", "invite"];

  "read" if "member";
  "update" if "admin";
  "delete" if "owner";
  "invite" if "admin";

  "admin" if "owner";
  "member" if "admin";
}

resource Project {
  roles = ["maintainer", "contributor", "viewer"];
  permissions = ["read", "write", "delete"];

  relations = { parent: Organization };

  "read" if "viewer";
  "write" if "contributor";
  "delete" if "maintainer";

  "contributor" if "maintainer";
  "viewer" if "contributor";

  "maintainer" if "admin" on "parent";
  "viewer" if "member" on "parent";
}
```

### 11.2 헬스케어 애플리케이션

```polar
resource Patient {
  roles = ["patient", "doctor", "nurse"];
  permissions = ["read_basic", "read_medical", "write_medical"];

  "read_basic" if "patient";
  "read_medical" if "doctor";
  "write_medical" if "doctor";
  "read_medical" if "nurse";
}

resource MedicalRecord {
  roles = ["owner", "authorized_provider"];
  permissions = ["read", "write"];

  relations = { patient: Patient };

  "read" if "authorized_provider";
  "write" if "authorized_provider";

  # 환자 자신은 읽을 수만 있음
  "owner" if actor.id = resource.patient_id;
  "read" if "owner";

  # 환자의 담당 의사/간호사는 권한 있음
  "authorized_provider" if "doctor" on "patient";
  "authorized_provider" if "nurse" on "patient";
}
```

### 11.3 금융 애플리케이션

```polar
resource Account {
  roles = ["owner", "co_owner", "viewer"];
  permissions = ["view_balance", "transfer", "close"];

  "view_balance" if "viewer";
  "transfer" if "co_owner";
  "close" if "owner";

  "co_owner" if "owner";
  "viewer" if "co_owner";

  # 송금은 2FA 필요
  "transfer" if
    "co_owner" and
    actor.two_factor_verified = true;
}

resource Transaction {
  roles = ["initiator"];
  permissions = ["view", "approve"];

  relations = { account: Account };

  "view" if "viewer" on "account";

  # 큰 금액은 co_owner 이상만 승인 가능
  "approve" if
    "initiator" and
    "co_owner" on "account";

  # 매우 큰 금액은 owner만
  "approve" if
    "initiator" and
    "owner" on "account" and
    resource.amount > 10000;
}
```

---

## 12. 마이그레이션 가이드

### 12.1 기존 RBAC에서 마이그레이션

**기존 시스템:**
```javascript
if (user.role === 'admin') {
  // 허용
}
```

**Oso로 마이그레이션:**

**1단계: 정책 정의**
```polar
resource Application {
  roles = ["admin", "user"];
  permissions = ["manage", "use"];

  "manage" if "admin";
  "use" if "user";
}
```

**2단계: 팩트 동기화**
```javascript
// 기존 DB에서 역할 마이그레이션
const users = await db.query('SELECT user_id, role FROM users');
for (const user of users) {
  await oso.tell('has_role',
    { type: 'User', id: user.user_id },
    user.role,
    { type: 'Application', id: 'main' }
  );
}
```

**3단계: 권한 확인 교체**
```javascript
// 기존 코드
if (user.role === 'admin') {
  // 허용
}

// 새 코드
if (await oso.authorize(user, 'manage', application)) {
  // 허용
}
```

### 12.2 점진적 마이그레이션

```javascript
// 하이브리드 방식: 기존 시스템과 Oso 병행
async function hasPermission(user, action, resource) {
  // 새로운 리소스는 Oso 사용
  if (resource.type in ['Document', 'Project']) {
    return await oso.authorize(user, action, resource);
  }

  // 기존 리소스는 레거시 로직 사용
  return legacyHasPermission(user, action, resource);
}
```

---

## 13. 참고 자료

**공식 문서:**
- Oso Cloud Docs: https://www.osohq.com/docs
- Polar Language Reference: https://www.osohq.com/docs/reference/polar
- API Reference: https://www.osohq.com/docs/reference/api

**SDK:**
- Node.js: https://www.npmjs.com/package/oso-cloud
- Python: https://pypi.org/project/oso-cloud/
- Ruby: https://rubygems.org/gems/oso-cloud
- Go: https://pkg.go.dev/github.com/osohq/go-oso-cloud
- Java: https://mvnrepository.com/artifact/com.osohq/oso-cloud

**커뮤니티:**
- GitHub: https://github.com/osohq
- Slack: https://join-slack.osohq.com/
- 블로그: https://www.osohq.com/blog

---

## 용어집

- **Actor**: 권한을 요청하는 주체 (사용자, 서비스 등)
- **Resource**: 보호되는 객체
- **Role**: 명명된 권한 세트
- **Permission**: 구체적인 작업
- **Relation**: 리소스 간 연결
- **Fact**: 데이터베이스에 저장된 사실
- **Rule**: 권한 결정 로직
- **Policy**: 규칙의 집합
- **Polar**: Oso의 정책 언어
- **Resource Block**: 리소스 타입 정의
- **Local Authorization**: 로컬에서 수행하는 권한 부여
