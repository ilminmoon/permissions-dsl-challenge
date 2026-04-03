# Open Policy Agent (OPA) 정책 언어 (Rego)

## 개요

OPA의 정책 언어인 **Rego**는 Datalog에서 영감을 받은 선언적 쿼리 언어로, JSON과 같은 구조화된 문서에 대해 추론하도록 설계되었습니다. 정책 작성자가 실행 메커니즘에 집중하지 않고 데이터를 검사하고 변환하는 규칙을 정의할 수 있게 해줍니다.

## 핵심 개념

### 왜 Rego인가?

Rego는 정책 정의의 가독성과 작성 용이성을 우선시합니다. 주요 장점은 다음과 같습니다:

- **강력한 중첩 문서 지원**: 명확한 구문
- **선언적 접근**: 작성자가 '어떻게'가 아닌 '무엇을' 쿼리가 반환해야 하는지 지정
- **쿼리 최적화**: 성능 향상

### 언어 구조

Rego 정책은 다음을 포함하는 **모듈**로 구성됩니다:

- 하나의 패키지 선언 (규칙의 네임스페이스)
- 0개 이상의 import 문
- 0개 이상의 규칙 정의

---

## 데이터 타입

### 스칼라 값

기본 타입에는 문자열, 숫자, boolean, null이 포함됩니다. Rego는 두 가지 문자열 구문을 지원합니다:

- **큰따옴표**: 이스케이프 시퀀스 포함 (`"hello\nworld"`)
- **백틱(Raw 문자열)**: 리터럴 텍스트, 정규식 패턴에 유용 (`` `hello\nworld` ``)

### 복합 값

#### 배열 (Arrays)

순서가 있는 0부터 시작하는 인덱스 컬렉션으로, 변수를 포함한 모든 값 타입을 지원합니다.

```rego
sites := [
  {"name": "prod", "region": "us-east"},
  {"name": "dev", "region": "us-west"}
]
```

#### 객체 (Objects)

순서가 없는 키-값 쌍으로, 모든 값 타입을 키로 사용할 수 있습니다. 문자열이 아닌 키는 JSON 직렬화 시 문자열로 변환됩니다.

```rego
servers := {
  "web": ["srv1", "srv2"],
  "db": ["srv3"]
}
```

#### 집합 (Sets)

순서가 없는 고유 값 컬렉션입니다.

- `set()`: 빈 집합 생성
- `{}`: 빈 객체 생성 (주의!)
- JSON에는 네이티브 집합 지원이 없으므로 배열로 직렬화됩니다

```rego
allowed_regions := {"us-east", "us-west", "eu-west"}
```

---

## 변수와 참조

### 변수

변수는 쿼리가 값을 제공하는지 여부에 따라 입력과 출력 역할을 동시에 수행합니다.

```rego
# x는 입력으로 사용됨
x == 10

# y는 출력으로 바인딩됨
y := 20
```

### 참조 (References)

참조는 점 표기법 또는 대괄호 표기법을 사용하여 중첩된 문서에 액세스합니다.

```rego
# 점 표기법
hostname := sites[0].servers[1].hostname

# 대괄호 표기법
hostname := sites[0]["servers"][1]["hostname"]
```

### 변수 키와 반복

참조는 변수를 키로 받아들여 컬렉션 요소 선택을 가능하게 합니다. 밑줄(`_`)은 변수 이름이 다른 곳에서 필요하지 않을 때 특수 반복자 플레이스홀더로 사용됩니다.

```rego
# 모든 서버 이름 반복
server_names[name] {
  some i, j
  name := sites[i].servers[j].name
}

# 밑줄 사용
server_names[name] {
  name := sites[_].servers[_].name
}
```

### 복합 키

집합 내의 참조는 복합 값을 키로 사용할 수 있으며, 멤버십 확인이나 집합 데이터의 패턴 추출에 유용합니다.

```rego
# 튜플을 사용한 집합
connections := {
  ["web", "api"],
  ["api", "db"]
}

# 멤버십 확인
["web", "api"] in connections  # true
```

---

## 규칙과 가상 문서

규칙은 가상 문서 콘텐츠를 생성합니다. 세 가지 규칙 타입이 있습니다:

### 1. 부분 규칙 (Partial Rules)

집합 또는 객체의 일부를 정의합니다.

```rego
<name> <key>? <value>? <body>?
```

**예시: 집합 생성**
```rego
# admin 역할을 가진 모든 사용자
admins[user.id] {
  some user in data.users
  user.role == "admin"
}
```

**예시: 객체 생성**
```rego
# 각 팀별 멤버 수
team_sizes[team_id] := count {
  some team_id
  members := [m | some m in data.members; m.team_id == team_id]
  count := count(members)
}
```

### 2. 완전 규칙 (Complete Rules)

키를 생략하여 단일 값을 반환하는 문서를 완전히 정의합니다. 여러 정의가 동시에 다른 값을 생성하면 충돌이 발생합니다.

```rego
# 사용자가 admin인지 확인
is_admin {
  input.user.role == "admin"
}

default is_admin := false
```

### 3. 증분 규칙 (Incremental Rules)

동일한 이름으로 반복 정의를 허용하여 정의 전체에서 유니온 결과를 생성합니다.

```rego
allow {
  input.user.role == "admin"
}

allow {
  input.user.id == data.resource.owner_id
}

# 위 두 조건 중 하나라도 true면 allow는 true
```

---

## 함수

### 사용자 정의 함수

임의의 입력을 받지만 정확히 하나의 출력을 반환합니다.

```rego
# 간단한 함수
trim_and_lower(s) := lower(trim(s, " "))

# 사용
result := trim_and_lower("  HELLO  ")  # "hello"
```

**여러 출력이 필요한 경우 배열, 객체 또는 집합 반환:**

```rego
get_user_info(user_id) := info {
  some user in data.users
  user.id == user_id
  info := {
    "name": user.name,
    "email": user.email,
    "role": user.role
  }
}
```

**함수 특징:**
- 증분 정의 지원
- 매개변수 개수에 따른 오버로딩 불가

---

## 제어 흐름

### 부정 (Negation)

부정(`not`)은 데이터에 상태가 존재하지 않아야 함을 확인합니다.

```rego
# 삭제되지 않은 문서만
valid_documents[doc] {
  some doc in data.documents
  not doc.deleted
}

# 안전한 부정: x는 다른 비부정 표현식에도 나타나야 함
deny {
  some x
  x := input.user.id
  not x in data.allowed_users
}
```

**안전성 규칙:**
- 부정된 표현식의 변수는 비부정 동등성 표현식에도 나타나야 합니다
- OPA는 일치하는 변수가 있는 다른 표현식 이후에 부정을 평가하도록 표현식을 재정렬합니다

### 전체 한정 (Universal Quantification)

`every` 키워드는 모든 도메인 요소에 대해 본문이 참임을 명시적으로 주장합니다.

```rego
every [key,] value in domain { body }
```

**예시:**
```rego
# 모든 서버가 HTTPS를 사용하는지 확인
all_servers_use_https {
  every server in data.servers {
    server.protocol == "https"
  }
}

# 키와 값 모두 사용
all_users_have_email {
  every user_id, user in data.users {
    user.email != ""
  }
}
```

**특징:**
- 빈 도메인은 true로 평가
- `every`는 규칙 평가에 새로운 바인딩을 도입하지 않음

### 조건부 로직 (Else)

`else` 키워드는 규칙 평가를 체인으로 연결하며, 첫 번째 매치 이후 중단됩니다.

```rego
authorization := "admin" {
  input.user.role == "admin"
} else := "editor" {
  input.user.role == "editor"
} else := "viewer" {
  true  # 기본값
}
```

순서 의존적인 시스템을 Rego로 포팅할 때 유용합니다.

---

## 컴프리헨션 (Comprehensions)

컴프리헨션은 서브 쿼리에서 복합 값을 간결한 구문으로 구축합니다.

### 배열 컴프리헨션
```rego
[ <term> | <body> ]
```

**예시:**
```rego
# admin 사용자의 ID만 추출
admin_ids := [user.id |
  some user in data.users
  user.role == "admin"
]
```

### 객체 컴프리헨션
```rego
{ <key>: <term> | <body> }
```

**예시:**
```rego
# 사용자 ID를 키로, 이름을 값으로
user_names := {user.id: user.name |
  some user in data.users
}
```

### 집합 컴프리헨션
```rego
{ <term> | <body> }
```

**예시:**
```rego
# 모든 고유한 역할
unique_roles := {user.role |
  some user in data.users
}
```

---

## 연산자

### 동등성 형식

#### 1. 할당 (`:=`)
값을 로컬 변수에 바인딩하며 컴파일러 안전성 검사 포함

```rego
x := 10
y := input.user.name
```

#### 2. 비교 (`==`)
양쪽이 모두 할당되어야 하는 동등성 확인

```rego
input.user.role == "admin"
10 == 10  # true
```

#### 3. 통합 (`=`)
할당과 비교를 결합하여 표현식을 만족하도록 변수 바인딩

```rego
x = 10  # x를 10에 바인딩
input.user.name = "Alice"  # 이름이 Alice인 경우에만 true
```

**모범 사례:** 명확한 의도와 향상된 컴파일러 검사를 위해 `:=`과 `==` 사용 권장

### 멤버십과 반복 (`in`)

`in` 연산자는 컬렉션(배열, 집합, 객체) 멤버십을 확인하여 true/false를 반환합니다.

```rego
# 배열
"apple" in ["apple", "banana"]  # true

# 집합
"admin" in {"admin", "editor"}  # true

# 객체 (키 확인)
"name" in {"name": "Alice", "age": 30}  # true
```

**`some`과 결합하여 변수 도입:**
```rego
some x in collection
```

### 비교 연산자

표준 연산자로 변수를 바인딩하지 않고 값을 비교합니다:

- `==`: 같음
- `!=`: 다름
- `<`: 작음
- `<=`: 작거나 같음
- `>`: 큼
- `>=`: 크거나 같음

```rego
input.age >= 18
input.count != 0
```

---

## 내장 함수 (Built-in Functions)

OPA는 산술, 집계, 문자열 조작 등을 위한 네임스페이스화된 내장 함수를 제공합니다.

### 주요 내장 함수 카테고리

**산술:**
- `plus(x, y)`: 덧셈
- `minus(x, y)`: 뺄셈
- `mul(x, y)`: 곱셈
- `div(x, y)`: 나눗셈

**집계:**
- `count(collection)`: 요소 개수
- `sum(array)`: 배열 합계
- `max(array)`: 최댓값
- `min(array)`: 최솟값

**문자열:**
- `concat(delimiter, array)`: 문자열 결합
- `contains(string, search)`: 포함 여부
- `startswith(string, prefix)`: 접두사 확인
- `endswith(string, suffix)`: 접미사 확인
- `lower(string)`: 소문자 변환
- `upper(string)`: 대문자 변환

**배열/집합:**
- `array.slice(array, start, end)`: 배열 슬라이스
- `union(set1, set2)`: 집합 합집합
- `intersection(set1, set2)`: 집합 교집합

**타입:**
- `is_string(x)`: 문자열 확인
- `is_number(x)`: 숫자 확인
- `is_boolean(x)`: boolean 확인
- `is_array(x)`: 배열 확인
- `is_object(x)`: 객체 확인
- `is_set(x)`: 집합 확인

**예시:**
```rego
# 활성 사용자 수
active_user_count := count([user |
  some user in data.users
  user.active == true
])

# 이름 정규화
normalized_name := lower(trim(input.user.name, " "))
```

**에러 처리:**
기본적으로 내장 함수의 런타임 에러는 평가를 중단하지 않고 undefined로 평가됩니다. `--strict-builtin-errors` 플래그는 에러를 예외로 처리하도록 이 동작을 반전시킵니다.

---

## 고급 기능

### With 키워드

`with` 키워드는 쿼리 평가 중에 input, data 또는 함수 값을 프로그래밍 방식으로 대체합니다.

```rego
# input을 테스트 값으로 대체
allow with input as {"user": {"role": "admin"}}

# data의 특정 경로를 대체
result with data.config as {"feature_enabled": true}

# 여러 대체 체인
allow
  with input.user as {"role": "admin"}
  with data.settings.strict_mode as false
```

**사용 사례:**
- 단위 테스트
- 시뮬레이션
- What-if 시나리오

### Default 키워드

완전 정의 규칙이 undefined일 때 대체 값을 제공합니다.

```rego
default allow := false

allow {
  input.user.role == "admin"
}

# allow가 정의되지 않으면 false 반환
```

### 메타데이터와 어노테이션

정책은 `# METADATA`로 시작하는 풍부한 메타데이터 블록을 지원합니다.

```rego
# METADATA
# title: User Authorization Policy
# description: |
#   이 정책은 사용자 역할과 리소스 소유권을 기반으로
#   문서 액세스를 제어합니다.
# authors:
# - name: Security Team
#   email: security@example.com
# scope: package
package authz

# METADATA
# scope: rule
# title: Admin Check
# description: 사용자가 admin 역할을 가지고 있는지 확인
is_admin {
  input.user.role == "admin"
}
```

**주요 어노테이션:**
- `scope`: package, rule, document, subpackages
- `title`, `description`: 문서화
- `related_resources`: 관련 문서 링크
- `authors`, `organizations`: 작성자 정보
- `schemas`: 스키마 정의와 값 경로 연결
- `entrypoint`: 정책 진입점 표시
- `custom`: 사용자 정의 데이터 매핑

---

## 타입 검사와 스키마

OPA는 `-s` 플래그를 통한 JSON Schema 통합으로 향상된 정적 타입 검사를 지원합니다.

### 스키마 제공 방법

**1. 단일 파일 (전역 input과 연결):**
```bash
opa eval -s schema.json -d policy.rego "data.authz.allow"
```

**2. 디렉토리 (경로별 스키마를 위한 어노테이션과 쌍):**
```bash
opa eval -s schemas/ -d policy.rego "data.authz.allow"
```

**3. Rego 파일 내 인라인:**
```rego
# METADATA
# schemas:
# - input: schema["input"]
package authz

schema := {
  "input": {
    "type": "object",
    "properties": {
      "user": {
        "type": "object",
        "properties": {
          "role": {"type": "string"},
          "id": {"type": "string"}
        },
        "required": ["role", "id"]
      }
    },
    "required": ["user"]
  }
}

allow {
  input.user.role == "admin"
}
```

**스키마 어노테이션 우선순위:**
rule > document > package > subpackages

---

## 모범 사례

### 1. 명시적 변수 선언
`some` 키워드를 사용하여 로컬 변수를 명시적으로 선언하고 의도하지 않은 캡처 방지

```rego
# 좋음
allow {
  some user in data.users
  user.role == "admin"
}

# 피하기 (암시적)
allow {
  data.users[_] == user
  user.role == "admin"
}
```

### 2. 명확한 동등성 연산자 사용
통합보다 `:=`과 `==` 선호

```rego
# 좋음
user_id := input.user.id
user_id == data.resource.owner_id

# 피하기 (불명확)
user_id = input.user.id
user_id = data.resource.owner_id
```

### 3. 집계와 그룹화에 컴프리헨션 활용

```rego
# 좋음: 컴프리헨션 사용
admin_count := count([user |
  some user in data.users
  user.role == "admin"
])

# 피하기: 수동 반복
admin_count := count {
  admins := {user |
    some user in data.users
    user.role == "admin"
  }
  count := count(admins)
}
```

### 4. 메타데이터 어노테이션 적용
문서화와 타입 안전성 향상

```rego
# METADATA
# title: Document Access Control
# description: 사용자 역할과 리소스 소유권 기반 액세스 제어
# schemas:
# - input: schema["request"]
package authz.documents
```

### 5. 전체 한정에 `every` 사용
"모든" 로직을 명확하게 표현

```rego
# 좋음
all_valid := true {
  every item in input.items {
    item.validated == true
  }
}

# 피하기 (불명확)
all_valid {
  count([item | some item in input.items; item.validated == false]) == 0
}
```

### 6. 기본값 제공
완전 규칙에 명시적 기본값 설정

```rego
default allow := false
default max_retry := 3
default timeout := 30
```

---

## 완전한 예시: 문서 권한 정책

```rego
# METADATA
# title: Document Access Control Policy
# description: |
#   팀 멤버십, 역할, 문서 상태를 기반으로
#   문서 액세스를 제어합니다.
# authors:
# - name: Platform Team
package authz.documents

import future.keywords.every
import future.keywords.in

# 기본값: 거부
default allow := false
default can_edit := false
default can_delete := false
default can_share := false

# 사용자는 삭제된 문서를 볼 수만 있음
allow {
  not is_deleted
  has_view_permission
}

# 편집 권한
can_edit {
  not is_deleted
  has_edit_permission
}

# 삭제 권한 (생성자만)
can_delete {
  not is_deleted
  is_creator
}

# 공유 권한 (Free 플랜 제외)
can_share {
  not is_deleted
  not is_free_plan
  has_admin_permission
}

## 헬퍼 규칙

# 문서가 삭제되었는지 확인
is_deleted {
  input.document.deletedAt != null
}

# 사용자가 생성자인지 확인
is_creator {
  input.user.id == input.document.creatorId
}

# Free 플랜인지 확인
is_free_plan {
  team := data.teams[input.document.teamId]
  org := data.organizations[team.orgId]
  org.plan == "free"
}

# 보기 권한 확인
has_view_permission {
  is_creator
}

has_view_permission {
  is_team_member
}

# 편집 권한 확인
has_edit_permission {
  is_creator
}

has_edit_permission {
  membership := get_team_membership
  membership.role in {"editor", "admin"}
}

# Admin 권한 확인
has_admin_permission {
  is_creator
}

has_admin_permission {
  membership := get_team_membership
  membership.role == "admin"
}

# 팀 멤버인지 확인
is_team_member {
  some membership in data.team_memberships
  membership.userId == input.user.id
  membership.teamId == input.document.teamId
}

# 팀 멤버십 가져오기
get_team_membership := membership {
  some membership in data.team_memberships
  membership.userId == input.user.id
  membership.teamId == input.document.teamId
}

## 검증 규칙

# 모든 관리자가 이메일을 가지고 있는지 확인
all_admins_have_email {
  every membership in data.team_memberships {
    membership.role == "admin"
    user := data.users[membership.userId]
    user.email != ""
  }
}

# 테스트용 데이터
test_data := {
  "users": {
    "u1": {"id": "u1", "email": "alice@example.com", "name": "Alice"},
    "u2": {"id": "u2", "email": "bob@example.com", "name": "Bob"}
  },
  "teams": {
    "t1": {"id": "t1", "orgId": "o1", "visibility": "private"}
  },
  "organizations": {
    "o1": {"id": "o1", "plan": "pro"}
  },
  "team_memberships": [
    {"userId": "u1", "teamId": "t1", "role": "admin"},
    {"userId": "u2", "teamId": "t1", "role": "editor"}
  ]
}
```

---

## 요약

Rego는 다음을 결합합니다:

- **Datalog의 검증된 기반**: 선언적 논리 프로그래밍
- **현대적 데이터 지원**: JSON, YAML 등 구조화된 문서
- **표현력**: 복잡한 권한과 정책을 명확하게 표현
- **성능**: 쿼리 최적화 및 효율적인 평가
- **타입 안전성**: JSON Schema 통합
- **유지보수성**: 메타데이터, 어노테이션, 명확한 구문

이를 통해 권한 부여 및 정책 시스템을 명확하고 유지 관리 가능하게 구축할 수 있습니다.
