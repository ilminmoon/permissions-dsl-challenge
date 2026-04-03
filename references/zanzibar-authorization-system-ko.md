# Zanzibar: Google의 일관된 글로벌 권한 부여 시스템

**출처:** USENIX Annual Technical Conference (USENIX ATC '19), 2019

**저자:** Ruoming Pang, Ramon Caceres, Mike Burrows, Zhifeng Chen, Pratik Dave, Nathan Germer, Alexander Golynski, Kevin Graney, Nina Kang, Lea Kissner, Jeffrey L. Korn, Abhishek Parmar, Christina D. Richards, Mengzhi Wang (Google)

**논문 링크:** https://research.google/pubs/zanzibar-googles-consistent-global-authorization-system/

---

## 요약

Zanzibar는 Google의 수백 개 클라이언트 서비스(Calendar, Cloud, Drive, Maps, Photos, YouTube 등)에서 광범위한 액세스 제어 정책을 표현하기 위한 통일된 데이터 모델과 구성 언어를 제공하는 글로벌 권한 부여 시스템입니다.

### 핵심 특징

- **일관성**: 사용자 작업의 인과 순서를 존중하는 외부 일관성 제공
- **규모**: 수조 개의 ACL 관리, 초당 수백만 건의 권한 요청 처리
- **성능**: 95 백분위수 지연 시간 10ms 미만 유지
- **신뢰성**: 3년 이상 프로덕션에서 99.999% 이상의 가용성 달성

---

## 1. 동기 및 배경

### 1.1 Google의 권한 부여 문제

Google의 다양한 서비스들은 각각 복잡한 권한 모델을 가지고 있습니다:

- **Gmail**: 폴더 기반 권한
- **Drive**: 문서 공유 및 상속
- **Calendar**: 이벤트 공유 및 대리인
- **Cloud**: IAM 정책 및 역할
- **YouTube**: 채널 및 비디오 권한

### 1.2 기존 시스템의 한계

각 서비스가 독립적으로 권한 시스템을 구축하면:

1. **중복 구현**: 동일한 로직을 반복 구현
2. **일관성 부족**: 서비스 간 권한 모델 불일치
3. **확장성 문제**: 각 서비스가 개별적으로 확장 문제 해결
4. **신뢰성 문제**: 권한 체크 실패 시 서비스 전체 영향

### 1.3 Zanzibar의 목표

- **통합된 권한 시스템**: 모든 Google 서비스에서 사용 가능
- **강력한 일관성**: 외부 일관성 보장
- **높은 성능**: 낮은 지연 시간 및 높은 처리량
- **높은 가용성**: 99.999% 이상의 SLA
- **유연성**: 다양한 권한 모델 표현 가능

---

## 2. 핵심 개념

### 2.1 관계 튜플 (Relation Tuples)

Zanzibar의 기본 데이터 구조는 **관계 튜플**입니다:

```
<object, relation, subject>
```

**구성 요소:**
- **object**: 권한이 적용되는 리소스 (예: `document:readme`)
- **relation**: 관계의 이름 (예: `owner`, `editor`, `viewer`)
- **subject**: 권한을 가진 엔티티 (사용자, 그룹, 다른 객체)

**예시:**

```
# 사용자 Alice가 document:readme의 소유자
<document:readme, owner, user:alice>

# 사용자 Bob이 document:readme의 편집자
<document:readme, editor, user:bob>

# group:eng가 document:readme의 뷰어
<document:readme, viewer, group:eng>

# 사용자 Charlie가 group:eng의 멤버
<group:eng, member, user:charlie>
```

### 2.2 네임스페이스 구성 (Namespace Configuration)

네임스페이스는 객체 타입에 대한 관계와 권한을 정의합니다.

**구조:**

```yaml
name: "document"

relation {
  name: "owner"
}

relation {
  name: "editor"
}

relation {
  name: "viewer"

  # 상속 규칙
  userset_rewrite {
    union {
      child { _this {} }
      child { computed_userset { relation: "editor" } }
      child { computed_userset { relation: "owner" } }
    }
  }
}
```

**의미:**
- `owner`는 직접 할당된 관계
- `editor`는 직접 할당된 관계
- `viewer`는 `viewer` 직접 할당 OR `editor` OR `owner` (상속)

### 2.3 주체 타입 (Subject Types)

주체는 다양한 형태를 가질 수 있습니다:

1. **직접 사용자**: `user:alice`
2. **사용자 집합**: `group:eng#member` (그룹의 모든 멤버)
3. **와일드카드**: `user:*` (모든 사용자)
4. **간접 관계**: `document:other#viewer` (다른 문서의 뷰어)

**예시: 중첩된 그룹**

```
<group:eng, member, user:alice>
<group:eng, member, user:bob>
<group:all-staff, member, group:eng#member>
<document:readme, viewer, group:all-staff#member>

# 결과: alice와 bob은 document:readme를 볼 수 있음
```

### 2.4 간접 관계 (Indirect Relations)

폴더와 문서의 상속 관계 예시:

```yaml
# Folder 네임스페이스
name: "folder"
relation { name: "owner" }
relation { name: "viewer" }

# Document 네임스페이스
name: "document"

relation {
  name: "parent"
  # parent는 folder 타입만 가능
}

relation {
  name: "owner"
}

relation {
  name: "viewer"
  userset_rewrite {
    union {
      child { _this {} }  # 직접 viewer
      child {
        # parent 폴더의 viewer도 이 문서의 viewer
        tuple_to_userset {
          tupleset { relation: "parent" }
          computed_userset {
            object: $TUPLE_USERSET_OBJECT
            relation: "viewer"
          }
        }
      }
    }
  }
}
```

**관계 튜플:**

```
<folder:root, viewer, user:alice>
<document:readme, parent, folder:root>

# alice는 document:readme의 viewer (상속을 통해)
```

---

## 3. Zanzibar API

### 3.1 Check API

사용자가 특정 객체에 대한 권한이 있는지 확인합니다.

**요청:**
```protobuf
message CheckRequest {
  string object = 1;        // "document:readme"
  string relation = 2;      // "viewer"
  string subject = 3;       // "user:alice"
  Zookie at_zookie = 4;     // 일관성 토큰 (선택)
}
```

**응답:**
```protobuf
message CheckResponse {
  bool allowed = 1;         // true/false
  Zookie zookie = 2;        // 새로운 일관성 토큰
}
```

**예시:**

```
Check(document:readme, viewer, user:alice)
→ allowed: true

Check(document:readme, owner, user:bob)
→ allowed: false
```

**평가 알고리즘:**

1. 객체의 네임스페이스 구성 로드
2. 관계의 userset_rewrite 규칙 평가
3. 재귀적으로 모든 관계 확인
4. Union/Intersection/Exclusion 계산
5. 최종 결과 반환

### 3.2 Read API

특정 관계에 속한 모든 주체 또는 특정 주체가 가진 모든 객체를 읽습니다.

**요청:**
```protobuf
message ReadRequest {
  string object = 1;        // "document:*" (와일드카드 가능)
  string relation = 2;      // "viewer"
  string subject = 3;       // "user:alice" (와일드카드 가능)
  Zookie at_zookie = 4;
}
```

**응답:**
```protobuf
message ReadResponse {
  repeated RelationTuple tuples = 1;
  Zookie zookie = 2;
}
```

**예시:**

```
# Alice가 뷰어인 모든 문서
Read(document:*, viewer, user:alice)
→ [<document:readme, viewer, user:alice>,
   <document:design, viewer, user:alice>]

# document:readme의 모든 뷰어
Read(document:readme, viewer, *)
→ [<document:readme, viewer, user:alice>,
   <document:readme, viewer, user:bob>]
```

### 3.3 Expand API

주어진 객체와 관계에 대해 모든 주체를 재귀적으로 확장합니다.

**요청:**
```protobuf
message ExpandRequest {
  string object = 1;        // "document:readme"
  string relation = 2;      // "viewer"
  Zookie at_zookie = 3;
}
```

**응답:**
```protobuf
message ExpandResponse {
  UsersetTree tree = 1;     // 사용자 집합 트리
  Zookie zookie = 2;
}

message UsersetTree {
  oneof node_type {
    LeafNode leaf = 1;
    UnionNode union = 2;
    IntersectionNode intersection = 3;
    ExclusionNode exclusion = 4;
  }
}
```

**예시:**

```
Expand(document:readme, viewer)
→ UsersetTree:
    Union {
      This: [user:alice, user:bob]
      ComputedUserset(editor): [user:charlie]
      ComputedUserset(owner): [user:dave]
    }
```

**사용 사례:**
- UI에서 권한 목록 표시
- 권한 상속 관계 시각화
- 디버깅 및 감사

### 3.4 Write API

관계 튜플을 추가하거나 삭제합니다.

**요청:**
```protobuf
message WriteRequest {
  repeated RelationTupleUpdate updates = 1;
}

message RelationTupleUpdate {
  enum Operation {
    CREATE = 0;
    DELETE = 1;
    TOUCH = 2;  // 이미 존재하면 타임스탬프만 업데이트
  }
  Operation operation = 1;
  RelationTuple tuple = 2;
}
```

**예시:**

```
Write([
  CREATE: <document:readme, viewer, user:alice>,
  DELETE: <document:readme, editor, user:bob>
])
```

### 3.5 Watch API

관계 튜플 변경 사항을 실시간으로 스트리밍합니다.

**요청:**
```protobuf
message WatchRequest {
  string object = 1;        // "document:*" (와일드카드 가능)
  Zookie start_zookie = 2;  // 시작 지점
}
```

**응답 (스트림):**
```protobuf
message WatchResponse {
  repeated RelationTupleUpdate updates = 1;
  Zookie zookie = 2;
}
```

**사용 사례:**
- 캐시 무효화
- 실시간 권한 업데이트
- 감사 로그

---

## 4. 일관성 모델

### 4.1 외부 일관성 (External Consistency)

Zanzibar는 **외부 일관성**을 보장합니다:

> "사용자가 관찰할 수 있는 모든 작업은 실제 시간 순서를 존중합니다"

**예시 시나리오:**

```
시간 t1: Alice가 문서 공유 (Write 완료)
시간 t2: Bob이 문서 확인 (Check 요청)

t1 < t2이면, Bob의 Check는 Alice의 공유를 반영해야 함
```

### 4.2 Zookie (일관성 토큰)

**Zookie**는 일관성을 보장하기 위한 불투명한 토큰입니다.

**구조:**
```
Zookie = {
  version: logical_timestamp,
  datacenter_id: string,
  shard_id: string
}
```

**작동 방식:**

1. **Write 작업**: 새로운 Zookie 반환
2. **Read/Check 작업**: Zookie를 전달하여 최소한 그 시점 이후의 데이터 보장

**예시:**

```
# 1. Alice가 문서 공유
zookie1 = Write(<document:readme, viewer, user:bob>)

# 2. Alice가 Bob에게 Zookie 전달 (대역 외)

# 3. Bob이 Zookie를 사용하여 확인
Check(document:readme, viewer, user:bob, at_zookie=zookie1)
→ allowed: true (보장됨)
```

### 4.3 Snapshot Reads

**스냅샷 읽기**는 특정 시점의 일관된 뷰를 제공합니다.

```
# 시간 t1의 스냅샷
zookie_t1 = getCurrentZookie()

# t1 시점의 모든 권한 확인
Check(obj1, rel1, subj1, at_zookie=zookie_t1)
Check(obj2, rel2, subj2, at_zookie=zookie_t1)
# 두 확인 모두 동일한 시점의 데이터 사용
```

### 4.4 인과 일관성 (Causal Consistency)

Zanzibar는 인과 관계를 존중합니다:

```
Write1 → Read1 (Zookie1 획득)
  ↓
Write2 (Zookie1 사용)
  ↓
Read2 → Write2의 결과를 볼 수 있음
```

---

## 5. 시스템 아키텍처

### 5.1 전체 구조

```
┌─────────────────────────────────────────────────────┐
│                  Client Services                     │
│  (Calendar, Drive, Photos, YouTube, Cloud, ...)     │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│              Zanzibar API Layer                      │
│  (Check, Read, Expand, Write, Watch)                │
└────────────┬────────────────────────────────────────┘
             │
    ┌────────┴────────┐
    ▼                 ▼
┌─────────┐      ┌──────────┐
│ Aclserve│      │ Leopard  │
│  (ACL)  │      │ (Index)  │
└────┬────┘      └─────┬────┘
     │                 │
     ▼                 ▼
┌─────────────────────────┐
│   Spanner (Storage)     │
└─────────────────────────┘
```

### 5.2 Aclserve

**역할:** ACL 평가 및 관리

**기능:**
- Check 요청 평가
- Expand 트리 생성
- Write 요청 처리
- Zookie 관리

**특징:**
- 무상태 (Stateless)
- 수평 확장 가능
- 지리적으로 분산

### 5.3 Leopard (인덱싱 시스템)

**역할:** 역방향 인덱스 제공

**필요성:**
- Read 쿼리 성능 향상
- Watch API 효율적 구현
- 대규모 객체 집합 스캔 방지

**인덱스 구조:**

```
# 정방향 관계
<document:readme, viewer, user:alice>

# 역방향 인덱스
user:alice → {
  (document:readme, viewer),
  (document:design, viewer),
  (folder:work, owner)
}
```

**업데이트 프로세스:**

1. Write 작업이 Spanner에 커밋
2. Spanner가 변경 로그 생성
3. Leopard가 변경 로그 구독
4. Leopard가 역방향 인덱스 업데이트

**지연 시간:**
- 인덱스 업데이트: 일반적으로 수 초 이내
- 최종 일관성 (Eventually Consistent)

### 5.4 Spanner (저장소)

**Google Spanner**를 ACL 저장소로 사용:

**장점:**
- 글로벌 분산
- 강력한 일관성
- 외부 일관성 보장
- 높은 가용성
- 자동 샤딩

**스키마:**

```sql
CREATE TABLE RelationTuples (
  namespace STRING,
  object_id STRING,
  relation STRING,
  subject_namespace STRING,
  subject_object_id STRING,
  subject_relation STRING,
  commit_timestamp TIMESTAMP,
  PRIMARY KEY (namespace, object_id, relation,
               subject_namespace, subject_object_id,
               subject_relation)
)
```

---

## 6. 성능 최적화

### 6.1 캐싱 전략

**다층 캐시:**

1. **네임스페이스 캐시**: 구성은 거의 변경되지 않음
2. **ACL 캐시**: 자주 액세스되는 ACL
3. **Check 결과 캐시**: 동일한 Check 요청

**무효화:**
- Watch API를 사용하여 변경 감지
- Zookie를 사용하여 캐시 일관성 보장

### 6.2 체크 최적화

**단락 평가 (Short-Circuit Evaluation):**
```
Union(A, B, C)에서:
- A가 true이면 B, C 평가 안 함
- A가 false이면 B 평가, B가 true이면 C 평가 안 함
```

**병렬 평가:**
```
Union(A, B, C)를 병렬로 평가:
- 첫 번째 true 결과가 나오면 즉시 반환
- 다른 평가 취소
```

**배치 처리:**
```
여러 Check 요청을 배치로 묶어 처리
→ 네트워크 왕복 시간 감소
```

### 6.3 인덱싱 최적화

**선택적 인덱싱:**
- 모든 관계를 인덱싱하지 않음
- Read/Watch가 필요한 관계만 인덱싱
- 스토리지 비용 절감

**증분 업데이트:**
- 전체 재인덱싱 대신 변경 사항만 적용
- Spanner의 변경 로그 활용

---

## 7. 확장성

### 7.1 데이터 파티셔닝

**수평 샤딩:**
```
Shard = hash(namespace, object_id) % num_shards
```

**장점:**
- 로드 균등 분산
- 독립적 확장
- 장애 격리

### 7.2 지리적 분산

**멀티 리전 배포:**
```
US: us-east, us-west, us-central
EU: eu-west, eu-central
ASIA: asia-east, asia-southeast
```

**지역성 (Locality):**
- 클라이언트와 가까운 리전에서 서비스
- 낮은 지연 시간
- Spanner의 글로벌 일관성 유지

### 7.3 처리량 확장

**수직 확장:**
- 더 강력한 머신 사용
- Aclserve 인스턴스당 처리량 증가

**수평 확장:**
- Aclserve 인스턴스 추가
- 로드 밸런서로 트래픽 분산
- 선형 확장

---

## 8. 가용성 및 신뢰성

### 8.1 고가용성 설계

**다중 복제:**
- Spanner: 5개 복제본 (쿼럼 기반)
- Aclserve: 무상태, 다중 인스턴스

**장애 조치 (Failover):**
- 리전 장애 시 자동 전환
- 데이터 손실 없음 (Spanner 동기 복제)

### 8.2 장애 처리

**Degraded Mode:**
```
Leopard 장애 시:
- Read/Watch API 제한
- Check/Expand는 정상 동작 (Spanner에서 직접 읽기)
```

**순환 종속성 방지:**
```
최대 재귀 깊이 제한:
- Check 평가 시 무한 루프 방지
- 시간 초과 설정
```

### 8.3 모니터링 및 알림

**메트릭:**
- 요청률 (QPS)
- 지연 시간 (p50, p95, p99)
- 에러율
- 캐시 히트율

**경고:**
- SLO 위반
- 지연 시간 급증
- 에러율 증가
- 용량 한계 도달

---

## 9. 실제 사용 사례

### 9.1 Google Drive

**권한 모델:**

```yaml
# Folder
<folder:root, owner, user:alice>
<folder:root, editor, group:team#member>

# Document
<document:readme, parent, folder:root>
<document:readme, editor, user:bob>
```

**상속:**
- 문서는 부모 폴더의 권한 상속
- 문서별 권한 오버라이드 가능

**공유:**
- 링크 공유: `<document:readme, viewer, user:*>`
- 특정 사용자: `<document:readme, editor, user:charlie>`

### 9.2 Google Calendar

**권한 모델:**

```yaml
# Calendar
<calendar:work, owner, user:alice>
<calendar:work, reader, user:bob>

# Event
<event:meeting, parent, calendar:work>
<event:meeting, attendee, user:charlie>
```

**대리인 (Delegate):**
```
<calendar:work, delegate, user:assistant>

# delegate는 calendar의 모든 권한 상속
```

### 9.3 YouTube

**권한 모델:**

```yaml
# Channel
<channel:tech, owner, user:creator>
<channel:tech, moderator, user:mod1>

# Video
<video:tutorial, parent, channel:tech>
<video:tutorial, visibility, public>
```

**가시성:**
- `public`: `<video:tutorial, viewer, user:*>`
- `unlisted`: URL을 가진 사람만
- `private`: 소유자만

### 9.4 Google Cloud IAM

**권한 모델:**

```yaml
# Project
<project:my-app, owner, user:alice>

# IAM Binding
<project:my-app, roles/viewer, group:developers#member>
<project:my-app, roles/editor, user:bob>
```

**역할 상속:**
- `roles/owner` ⊃ `roles/editor` ⊃ `roles/viewer`

---

## 10. 성능 특성

### 10.1 지연 시간

**Check API:**
- p50: < 5ms
- p95: < 10ms
- p99: < 20ms

**Read API (Leopard 사용):**
- p50: < 10ms
- p95: < 50ms
- p99: < 100ms

**Expand API:**
- p50: < 20ms
- p95: < 100ms
- p99: < 200ms

### 10.2 처리량

**글로벌 규모:**
- 수백만 QPS (Queries Per Second)
- 수조 개의 관계 튜플
- 수십억 개의 객체

**단일 Aclserve 인스턴스:**
- ~10,000 QPS (Check)
- ~1,000 QPS (Read/Expand)

### 10.3 스토리지

**효율성:**
- 관계 튜플: ~100 bytes
- 압축률: ~10:1
- 총 스토리지: 수십 PB

---

## 11. 제한 사항 및 트레이드오프

### 11.1 제한 사항

**최대 재귀 깊이:**
- Check 평가 시 무한 루프 방지
- 일반적으로 10-20 수준

**최대 그룹 크기:**
- Expand 시 너무 큰 그룹은 타임아웃
- 클라이언트가 페이지네이션 필요

**Leopard 지연:**
- 인덱스 업데이트에 수 초 소요
- Read가 최신 Write를 즉시 반영 안 함

### 11.2 트레이드오프

**일관성 vs 성능:**
- 외부 일관성을 위해 Spanner 사용
- Spanner는 NoSQL보다 느림
- 캐싱으로 완화

**유연성 vs 복잡성:**
- 강력한 표현력 (userset_rewrite)
- 복잡한 구성 관리
- 디버깅 어려움

**가용성 vs 일관성:**
- Spanner의 강력한 일관성
- 리전 장애 시 가용성 영향
- 읽기 전용 모드로 degraded service

---

## 12. 모범 사례

### 12.1 네임스페이스 설계

**단순하게 시작:**
```yaml
# 좋음
name: "document"
relation { name: "owner" }
relation { name: "viewer" }

# 피하기 (너무 복잡)
relation {
  name: "viewer"
  userset_rewrite {
    union {
      child { ... 10 levels deep ... }
    }
  }
}
```

**재사용 가능한 패턴:**
```yaml
# 공통 패턴을 템플릿화
- Owner/Editor/Viewer 계층
- Parent-child 상속
- Group 멤버십
```

### 12.2 성능 최적화

**캐시 친화적 설계:**
```
# 좋음: 자주 변경되지 않는 ACL
<document:readme, owner, user:alice>

# 피하기: 자주 변경되는 ACL
<document:readme, viewer, timestamp:2024-01-01>
```

**배치 요청:**
```javascript
// 좋음: 배치 Check
const results = await batchCheck([
  {obj: "doc1", rel: "viewer", subj: "alice"},
  {obj: "doc2", rel: "viewer", subj: "alice"}
]);

// 피하기: 개별 Check
const result1 = await check("doc1", "viewer", "alice");
const result2 = await check("doc2", "viewer", "alice");
```

### 12.3 일관성 관리

**Zookie 전파:**
```javascript
// 1. Write 후 Zookie 획득
const zookie = await write([...]);

// 2. 사용자에게 Zookie 전달 (쿠키, 응답 헤더 등)
response.setHeader('X-Zanzibar-Zookie', zookie);

// 3. 다음 요청에서 Zookie 사용
const allowed = await check(obj, rel, subj, { atZookie: zookie });
```

**스냅샷 일관성:**
```javascript
// 동일 시점의 여러 확인
const zookie = await getCurrentZookie();

const canView = await check(doc1, "viewer", user, { atZookie: zookie });
const canEdit = await check(doc2, "editor", user, { atZookie: zookie });
// 두 확인 모두 동일한 스냅샷 사용
```

### 12.4 디버깅

**Expand 사용:**
```javascript
// 권한이 예상과 다를 때
const tree = await expand("document:readme", "viewer");
console.log(JSON.stringify(tree, null, 2));
// 누가 viewer인지, 어떤 경로로 권한을 가지는지 확인
```

**Watch로 변경 추적:**
```javascript
// 특정 객체의 권한 변경 모니터링
watchStream("document:readme", startZookie)
  .on('data', (update) => {
    console.log('ACL changed:', update);
  });
```

---

## 13. Zanzibar vs 다른 접근 방식

### 13.1 vs RBAC (Role-Based Access Control)

**RBAC:**
```
User → Role → Permissions
```

**Zanzibar (ReBAC - Relationship-Based):**
```
User ↔ Object (via Relations)
```

**장점:**
- 더 세밀한 권한 제어
- 객체 간 관계 표현 가능
- 상속 및 간접 관계 지원

**단점:**
- 더 복잡한 모델
- 더 많은 스토리지 필요

### 13.2 vs ABAC (Attribute-Based Access Control)

**ABAC:**
```
if (user.department == "engineering" &&
    resource.sensitivity == "low") {
  allow
}
```

**Zanzibar:**
```
<resource:doc, viewer, group:engineering#member>
```

**장점:**
- 명시적 관계 (추적 가능)
- 캐싱 가능
- 일관성 보장

**단점:**
- 동적 속성 평가 제한적
- 사전에 관계 정의 필요

### 13.3 vs 자체 구현

**자체 ACL 시스템:**
- 서비스별 최적화 가능
- 초기 구현 간단

**Zanzibar:**
- 통합된 모델
- 검증된 확장성
- 중앙화된 감사
- 일관성 보장

**트레이드오프:**
- 초기 마이그레이션 비용
- Zanzibar 의존성

---

## 14. 구현 고려사항

### 14.1 오픈소스 구현

**SpiceDB** (by AuthZed):
- Zanzibar 영감의 오픈소스 구현
- PostgreSQL, MySQL, CockroachDB 지원
- gRPC API
- https://github.com/authzed/spicedb

**Ory Keto**:
- Zanzibar 스타일 권한 서버
- 더 단순한 모델
- https://github.com/ory/keto

**Google Zanzibar 자체는 오픈소스가 아님**

### 14.2 마이그레이션 전략

**단계적 마이그레이션:**

1. **Phase 1: 읽기 전용**
   - Zanzibar에 ACL 복사
   - 기존 시스템과 병렬 운영
   - 결과 비교 및 검증

2. **Phase 2: 읽기 트래픽 이동**
   - Zanzibar로 Check 요청 라우팅
   - 기존 시스템은 쓰기만
   - 모니터링 및 성능 검증

3. **Phase 3: 쓰기 트래픽 이동**
   - Zanzibar로 Write 요청 라우팅
   - 기존 시스템 단계적 종료

4. **Phase 4: 완전 이전**
   - 기존 시스템 제거
   - Zanzibar 전용

### 14.3 테스트 전략

**단위 테스트:**
```yaml
test_cases:
  - name: "Owner can view"
    setup:
      - <doc:1, owner, user:alice>
    checks:
      - check(doc:1, viewer, user:alice) → true

  - name: "Viewer cannot edit"
    setup:
      - <doc:1, viewer, user:bob>
    checks:
      - check(doc:1, editor, user:bob) → false
```

**통합 테스트:**
```javascript
// 복잡한 시나리오
await write([
  ['folder:root', 'owner', 'user:alice'],
  ['document:readme', 'parent', 'folder:root']
]);

const allowed = await check('document:readme', 'viewer', 'user:alice');
expect(allowed).toBe(true); // 상속 확인
```

**성능 테스트:**
```
- 목표 지연 시간 달성 확인
- 부하 테스트 (QPS 증가)
- 장애 시나리오 (리전 다운)
```

---

## 15. 결론

### 15.1 핵심 기여

Zanzibar는 다음을 입증했습니다:

1. **통합 권한 시스템의 실현 가능성**
   - 수백 개 서비스에서 단일 시스템 사용
   - 다양한 권한 모델 표현 가능

2. **대규모 일관성과 성능 양립**
   - 외부 일관성 보장
   - 10ms 미만 지연 시간
   - 수백만 QPS 처리

3. **관계 기반 접근의 우수성**
   - 복잡한 권한 그래프 표현
   - 간접 관계 및 상속
   - 명시적 감사 추적

### 15.2 영향

**산업 표준:**
- 많은 기업이 Zanzibar 패턴 채택
- 오픈소스 구현 등장 (SpiceDB, Ory Keto)
- "Zanzibar-style" 권한 시스템이 용어로 정착

**설계 원칙:**
- 관계 튜플의 단순성
- 네임스페이스 구성의 유연성
- 일관성 토큰 (Zookie)의 우아함

### 15.3 미래 방향

**예상되는 발전:**
- 더 풍부한 쿼리 언어
- 기계 학습 기반 권한 추천
- 더 나은 시각화 도구
- 정책 검증 및 테스트 도구

**연구 과제:**
- 더 낮은 지연 시간
- 더 높은 처리량
- 더 복잡한 권한 표현
- 프라이버시 보존 권한 체크

---

## 참고 자료

**논문:**
- Zanzibar: Google's Consistent, Global Authorization System (USENIX ATC '19)
- https://research.google/pubs/zanzibar-googles-consistent-global-authorization-system/

**오픈소스 구현:**
- SpiceDB: https://github.com/authzed/spicedb
- Ory Keto: https://github.com/ory/keto

**관련 논문:**
- Spanner: Google's Globally-Distributed Database
- Chubby: The lock service for loosely-coupled distributed systems

**추가 자료:**
- Google Cloud IAM: https://cloud.google.com/iam/docs
- AuthZed Documentation: https://docs.authzed.com/

---

## 용어집

- **ACL**: Access Control List (접근 제어 목록)
- **ReBAC**: Relationship-Based Access Control (관계 기반 접근 제어)
- **Relation Tuple**: 권한의 기본 단위 (객체, 관계, 주체)
- **Namespace**: 객체 타입과 관계 정의
- **Userset**: 주체의 집합
- **Zookie**: Zanzibar의 일관성 토큰
- **Leopard**: Zanzibar의 역방향 인덱싱 시스템
- **Aclserve**: Zanzibar의 ACL 평가 서버
- **External Consistency**: 외부 관찰자 관점에서의 일관성
- **Snapshot Read**: 특정 시점의 일관된 읽기
