# Figma의 맞춤형 권한 DSL 구축 사례

**게시일:** 2024년 3월 13일
**저자:** Jorge Silva (Figma 소프트웨어 엔지니어)
**태그:** Inside Figma, Engineering, Infrastructure

**일러스트:** Chantal Jahchan

---

## 개요

권한 시스템이 한계에 다다랐을 때, 모든 것이 멈췄습니다. 이는 성능, 정확성, 그리고 개발자 경험을 개선하면서 이 문제를 해결한 이야기입니다.

협업은 Figma의 핵심입니다. 이것이 우리가 Figma를 웹에 구축한 이유이며, [멀티플레이어](https://www.figma.com/blog/how-figmas-multiplayer-technology-works/)가 Figma 에디터에서 매우 중요한 부분인 이유입니다. 협업에 대한 이러한 집중은 권한 규칙이 복잡하다는 것을 의미하며, 그 복잡성은 기만적입니다. 2021년 초, 엔지니어링 팀은 이 복잡성에 뿌리를 둔 수많은 버그, 지원 티켓, 지연된 프로젝트를 마주했습니다. 과제는 무엇이었을까요? 엔지니어들이 무언가를 망가뜨릴 걱정 없이 규칙을 추가, 수정, 제거할 수 있도록 권한 엔진의 기술적 기반을 재고해야 했습니다.

"구축(build)" 대 "구매(buy)" 논쟁에서 우리는 기존의 오픈 소스나 기성 솔루션 사용을 선호하는 경향이 있습니다. 이 경우, 우리는 자체 권한 도메인 특화 언어(DSL), 맞춤형 크로스 플랫폼 로직 엔진을 만들고, 가장 중요한 모든 권한 규칙을 이 시스템으로 마이그레이션하기로 결정했습니다.

---

## Figma의 권한 및 인증

이 솔루션에 도달한 이유를 설명하기 전에, 이것이 Figma 사용자 경험과 어떻게 연결되는지 이해하는 것이 중요합니다. 우리가 "공유(share)" 모달이라고 부르는 것부터 시작하겠습니다. 이것은 권한이 사용되는 유일한 곳은 아니지만, 가장 눈에 띄고 가장 복잡한 곳 중 하나입니다.

Figma 파일에서 "Share" 버튼을 클릭하면 공유 모달이 나타나며, 이는 파일에 액세스할 수 있는 사람과 해당하는 작성 권한 수준을 제어합니다. 파일에 액세스하는 두 가지 주요 방법이 있습니다: 역할을 통한 액세스와 링크를 통한 액세스입니다. 역할은 계층적이므로 상위 폴더, 팀 또는 조직에서의 역할을 통해 파일에 액세스할 수 있습니다. 각 컨테이너의 상태와 사용자의 역할에 따라 액세스가 차단되거나 허용되는 규칙이 있습니다. 링크 액세스도 파일에 액세스할 수 있는 사람, 액세스 유형, 기간, 비밀번호 필요 여부, 심지어 파일의 상위 조직이 파일 공유 방법에 제한을 가하는지 여부를 제한하는 다양한 규칙을 가질 수 있습니다.

초기에는 Figma의 모든 백엔드 비즈니스 로직(권한 포함)이 ActiveRecord(Ruby용 가장 인기 있는 ORM)를 사용하는 Ruby 모놀리스 애플리케이션에 있었습니다. 거의 모든 비즈니스 로직은 ActiveRecord 호출이 있는 간단한 HTTP 엔드포인트로 구성되었습니다. ActiveRecord 모델에는 `has_access?` 메서드가 있었으며, 이는 사용자(단 한 명!)가 특정 리소스에 액세스할 수 있는 로직을 정의했습니다. 이 단일 메서드가 백엔드 애플리케이션에서 액세스 제어의 기반 역할을 했으며, 데이터베이스 호출을 하고 boolean을 반환하는 "if/else" 문이 있는 함수에 불과했습니다. 이 함수는 삭제된 파일, 계층적 액세스, 모든 다양한 유형의 링크 액세스, 조직 제한, 청구 규칙 등을 처리했습니다. 사용자가 특정 리소스에 액세스할 수 있는지 확인하기 위해 제품 엔지니어는 컨트롤러에서 적절한 시점에 이 함수를 호출해야 했습니다. 이 간단한 설정은 오랫동안 잘 작동했지만, 우리가 이를 능가했다는 것이 분명해졌습니다.

시간이 지나면서 엔지니어들은 권한 작업이 얼마나 어려운지에 대해 목소리를 높였습니다. 이로 인해 심층 조사가 시작되었고, 해결해야 할 네 가지 핵심 문제에 대해 합의할 수 있었습니다.

---

### 문제 1: 불필요한 복잡성과 디버깅 어려움

이러한 `has_access?` 메서드는 많은 선택적 매개변수가 있어 매우 길고 복잡했습니다. 엔지니어들은 이를 수정하는 것을 두려워했습니다. 종종 "`has_access?` 외부에서 할 수 있을까요?" 또는 "클라이언트에서 하면 안 될까요?"와 같은 제안을 듣곤 했습니다. 그러나 이러한 함수의 버그는 Figma의 모든 파일에 대한 액세스를 유출할 수 있습니다.

이 복잡성의 흥미로운 이유 중 하나는 이러한 함수를 디버깅할 때 엔지니어들이 해당 리소스에 대한 모든 권한 로직을 동시에 디버깅하고 있었다는 것입니다. 로직을 분리하는 쉬운 방법이 없었습니다. 결과적으로 엔지니어들이 기존 로직을 모두 생각하지 않고 자신의 기능에 대한 새로운 독립적인 규칙을 작성하기가 어려웠습니다.

디버깅은 복잡하고 시간이 많이 걸리게 되었으며, 종종 이러한 함수 전체에 수십 개의 print 문을 추가하고 권한 로직의 전체 본문에 대한 컨텍스트를 로드해야 했습니다.

---

### 문제 2: 계층적 권한

권한에 대한 정수 값을 기반으로 한 계층적 시스템이 있었지만, 엔지니어들은 종종 `has_access?` 메서드에 추가된 boolean 플래그를 통해 이러한 동작의 변형을 도입했습니다. 이러한 메서드는 종종 다음과 같이 보였습니다:

```ruby
def has_access?(
  user,                        # T.nilable(User)
  level,                       # Integer
  ignore_link_access: false,   # Boolean
  org_candidate: false,        # Boolean
  ignore_archived_branch: false # Boolean
)
```

이는 사용자가 `300 access` 레벨(편집 액세스)을 가질 수 있지만 `100 (view) + ignore_link_access: true` 액세스를 가지지 못할 수 있음을 의미했습니다. 이는 이해하기 어렵고 디버깅하기 더 어려운 매우 복잡한 멘탈 모델을 만들었습니다. 또한 이러한 플래그는 리소스마다 다양했습니다. 엔지니어들은 이러한 모든 변형, 의미, 고려 시점을 알아야 했습니다.

완전히 독립적일 수 있는 계층적이지 않은 세분화된 권한을 생성할 수 있는 더 유연한 시스템이 필요하다는 것이 분명해졌습니다. 계층적 권한에는 어느 정도 유용성이 있지만, 우리는 여전히 확립된 계층 구조를 벗어나거나 새로운 계층 구조를 만들고 싶었습니다.

---

### 문제 3: 데이터베이스 부하

Figma는 빠르게 성장하고 있었습니다. 이러한 성장과 함께 데이터베이스에 대한 부담이 증가했으며, 권한 확인으로 인한 데이터베이스 부하가 이 중 큰 비중(약 20%)을 차지했습니다. 이는 [데이터베이스를 얼마나 크게 만들 수 있는지](https://www.figma.com/blog/how-figma-scaled-to-multiple-databases/)에 물리적 제한이 있었기 때문에 실존적 문제였습니다. 데이터베이스 팀이 수직 및 수평 샤딩을 작업하는 동안, 백엔드 엔지니어에게 권한을 위한 데이터 레이어에 대한 더 많은 제어권을 제공할 수 있도록 해야 했습니다.

이를 조사하기 시작했을 때, `has_access?`가 ActiveRecord 호출이 있는 일반 Ruby 함수에 불과했기 때문에 데이터베이스 쿼리와 권한 로직이 결합되어 있다는 것을 깨달았습니다. 실수로 권한 로직을 변경하지 않고 데이터베이스 쿼리를 수정할 방법이 없었습니다. 권한에 대한 깊은 지식이 없는 백엔드 엔지니어라면 무언가를 변경하려고 하는 것이 위험하게 느껴졌습니다. 우리는 데이터베이스 쿼리와 정책 로직을 완전히 분리하는 방법을 원했습니다.

---

### 문제 4: 여러 진실의 원천

두 시스템에서 권한을 구현해야 했습니다: Sinatra(HTTP 백엔드)와 [LiveGraph](https://www.figma.com/blog/livegraph-real-time-data-fetching-at-figma/)(실시간 API 레이어). 새로운 권한 로직이 도입될 때마다 엔지니어들은 이 두 코드베이스 모두에서 변경 사항을 적용하라는 지시를 받았습니다. 실제로 많은 규칙이 제대로 마이그레이션되지 않았고 이 두 시스템 간의 불일치로 인해 버그가 발생했습니다. 엔지니어들이 정책 로직을 한 번 작성하고 두 시스템 모두에서 올바르게 작동하도록 하는 방법이 필요했습니다.

이 네 가지 문제를 바탕으로, 우리는 어떤 솔루션이든 다음을 충족해야 한다는 것을 깨달았습니다:

1. 엔지니어가 기존 권한 규칙에 대해 신경 쓰지 않고 권한 규칙을 작성할 수 있도록 허용합니다. 이러한 규칙을 분리하면 디버깅하고 수정하기 쉬워집니다.
2. 정책 로직과 데이터 로딩 간의 완전한 분리가 있어야 합니다. 정책 작성자는 데이터가 어떻게 로드되는지 또는 정책을 최적화하는 것에 대해 걱정할 필요가 없어야 합니다. 권한 엔진을 작업하는 백엔드 엔지니어는 정책 로직에 대해 걱정할 필요가 없어야 합니다.
3. 크로스 플랫폼이어야 합니다. 정책 작성자에게 추가 작업을 도입하지 않고 Ruby, TypeScript 및 기타 언어에서 작동할 수 있어야 합니다.
4. 사용자가 리소스에 대해 수행할 수 있는 작업을 보다 정확하게 모델링하기 위해 세분화된 권한을 생성할 수 있도록 허용합니다.

---

## 정책: 초기 통찰력과 개념 증명

권한 규칙을 선언하기 위한 일종의 추상화 레이어가 필요하다는 것이 분명했습니다. 권한 역할을 정의하는 선언적 방법에 대한 영감으로 [AWS IAM](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies.html) 정책을 살펴보기 시작했습니다. IAM 정책은 기본적으로 몇 가지 주요 속성을 가집니다: 권한 또는 작업 세트, 효과(허용 또는 거부), 적용되는 리소스, 그리고 정책이 적용되는 일부 조건. 일반적으로 JSON으로 작성되며 다음과 같습니다:

```json
{
  "Version": "2012-10-17",
  "Statement": {
    "Effect": "Allow",
    "Action": "EC2:*",
    "Resource": "*",
    "Condition": {
      "IpAddress": {
        "aws:SourceIp": ["192.168.0.1"]
      }
    }
  }
}
```

명확히 하자면: AWS IAM 정책은 작업하기 어렵기로 악명 높으며 보편적으로 사랑받지는 않습니다. 하지만 우리에게는 세분성과 격리를 제공하며, 이는 시스템의 핵심 부분입니다: 정책 작성자는 IAM 정책을 작성하고 다른 모든 기존 IAM 정책에 대해 걱정할 필요가 없습니다. 효과(허용 또는 거부), 권한 세트(작업 또는 동사), 정책이 적용될 일련의 조건과 같은 추가 속성은 모두 우리가 시스템을 가져가고자 하는 방향과 일치하는 것처럼 보였습니다. 이를 영감으로 삼아 우리는 이를 해결하는 데 사용할 수 있는 잠재적 솔루션을 찾기 시작했지만 필요한 것을 수용할 수 있는 것은 없었습니다. Open Policy Agent, Zanzibar, Oso가 고려한 일부 솔루션이었지만 실제로 해결하려는 핵심에 도달한 것은 없었습니다. 철저한 조사 후, 우리는 자체 솔루션을 탐색하기 시작했습니다.

첫 번째 시도에서 우리는 다음과 같은 정책 클래스를 개발했습니다:

```ruby
class DeletedFileACP < AccessControlPolicy
  # 이 정책이 적용되는 리소스 유형
  resource_type :file

  # 적용될 경우 지정된 권한이 허용 또는 거부되어야 함
  # (DENY가 ALLOW를 재정의함)
  effect :deny

  # 이 정책이 허용/거부될 때 허용/거부되는 작업
  permissions [
    :can_edit,
    :can_view
  ]

  # 이 정책을 평가하기 위해 로드해야 하는 데이터
  # 이 경우 리소스는 파일 객체가 됨
  # (결국 이 아이디어를 없앴습니다!)
  attached_through :resource

  # 이 정책을 적용하거나 무시해야 하는지
  # 적용되면 권한이 허용/거부됨
  def apply?(resource: nil, **_)
    resource.deleted?
  end
end
```

효과, 권한 세트, 리소스 유형, 그리고 로드된 리소스 세트가 주어지면 Ruby 코드를 실행하는 `apply?` 메서드가 포함되어 있습니다. 또한 `attached_through`를 통해 사용자, 역할, 상위 리소스(프로젝트, 팀, 조직) 또는 유사한 리소스를 연결할 수 있었습니다. 이 마지막 개념은 직관적이지 않고 제한적(리소스 하나만 로드할 수 있나요?)이며 반복하고 싶었습니다.

한 가지 질문이 우리를 괴롭혔습니다: 이 정책 모델을 사용하여 Figma의 많은 기존 권한 규칙을 모두 표현할 수 있을까요? 알아내는 방법은 단 하나뿐이었습니다! 우리는 모든 로직을 정책으로 나누는 작업을 시작했습니다. 이러한 함수에 모든 로직이 정의되어 있고 모든 기존 테스트가 통과하는지 확인하는 것은 큰 작업이었습니다. CI와 몇 주간의 힘든 반복 끝에 필요한 것을 얻었습니다: 모든 권한이 정책으로 표현되고 CI/CD에서 모든 테스트가 통과하는 브랜치. 수년에 걸쳐 작성된 수백 개의 테스트였으므로 우리가 가진 것이 작동한다고 상당히 확신했습니다.

여러 면에서 이것은 프로젝트의 위험을 제거하는 가장 큰 단계가 되었습니다. 이 작업을 통해 엔지니어들이 수년 동안 추가한 권한 규칙의 모든 작은 복잡함과 그 뒤에 있는 제품 추론을 배울 수 있었습니다.

이 시점에서 우리는 한 언어로 작동하는 개념 증명과 녹색 브랜치를 가지고 있었지만 여전히 해결해야 할 몇 가지 문제가 있었습니다:

1. 정책은 여전히 작성하기 복잡했습니다. 리소스를 "연결"하고 이러한 복잡한 관계를 갖는 전체 아이디어는 정책에 필요한 리소스를 지정하는 것보다 훨씬 어려워 보였습니다.
2. 명령형 Ruby 함수를 작성하고 있다는 사실은 `apply?` 메서드 내에서 실제로 일어나는 일에 대한 통제가 거의 없다는 것을 의미했습니다. 정책이 네트워크 호출(예: 데이터 로드)이나 부작용 없이 boolean 로직만 실행하도록 하는 방법을 원했습니다.
3. 이러한 정책을 플랫폼 간에 작동시키는 방법에 대한 좋은 스토리가 여전히 없었습니다. AST(Abstract Syntax Tree) 파싱으로 실험하기 시작했지만 한 언어에 대해서만 구현하는 것도 신뢰할 수 없는 솔루션처럼 느껴졌으며 개발자 경험이 좋지 않고 많은 골칫거리가 있었습니다.

---

## DSL 도입: 개발자 경험 및 인트로스펙션 개선

초기 개념 증명을 개발한 후, 크로스 플랫폼 지원 문제를 해결하고 싶었습니다. 그렇게 하기 위해 모든 정책을 JSON 직렬화 가능하게 만들어 Sinatra와 LiveGraph 모두에서 쉽게 읽을 수 있도록 하기로 결정했습니다. 또한 정책은 완전히 추상적이며 코드 실행을 포함하지 않습니다. AST 파싱이 필요하지 않습니다.

LiveGraph 팀이 사용하는 `ExpressionDef`(또는 "Expression Definitions")라는 기존 DSL을 확장하는 것으로 시작했습니다. 이 boolean 로직 DSL은 양쪽에 두 개의 변수와 중간에 작업을 포함하는 트리플을 기반으로 했으며, boolean으로 평가됩니다(`[1, "=", 1]`은 `true`와 같고 `[2, ">", 3]`은 `false`와 같습니다). 이러한 트리플을 기반으로 세 가지 상위 수준 작업(`and`, `or`, `not`)으로 더 복잡한 boolean 로직을 구성할 수 있습니다. 이를 통해 다음과 같은 것을 표현할 수 있습니다:

- `{ "and": [[1, "=", 1], [1701439764, ">", 1701439775]] }`
- `{ "or": [[1, "=", 1], [1701439764, ">", 1701439775]] }`

(각각 `true`와 `false`로 평가됨)

이 JSON 직렬화 가능한 DSL의 두 번째 부분은 제공된 데이터를 참조하는 기능이었습니다. 이를 위해 두 가지 작은 수정을 했습니다: 첫째, 트리플의 왼쪽은 항상 데이터 필드에 대한 참조가 되고, 둘째, 특수 `ref` 객체를 사용하여 오른쪽에서 항상 데이터 필드를 참조할 수 있습니다. 데이터 필드는 `.`으로 구분된 테이블 이름과 열 이름을 통해 문자열로 표현됩니다. 다음과 같은 문을 작성할 수 있습니다:

- `["team.permission", "=", "open"]`
- `["file.deleted_at", "<>", null]`
- `["file.team_id", "=", { "ref": "user.blocked_team_id" }]`

여기서 `team`, `file`, `user`와 같은 일반 용어를 사용하여 필드를 참조한다는 것을 알 수 있습니다. 이것은 실제로 정책에서 사용하는 정확한 명명법입니다. 이러한 문자열을 처리하는 것은 엔진에 달려 있지만 이 모델의 단순성을 좋아합니다.

Expression Definitions는 우리에게 많은 문을 열어주었습니다:

1. 원하는 언어나 환경에서 정책과 로직을 쉽게 사용할 수 있습니다. AST 파싱이 필요하지 않습니다.
2. 매우 간단한 파싱을 통해 필요한 모든 데이터 종속성을 정적으로 알 수 있습니다.
3. 정책 작성자가 필요한 모든 데이터를 직접 참조할 수 있는 간단하고 직관적인 API를 제공합니다.

Figma에서 이미 널리 사용되고 있고, 타입 시스템이 있으며, 객체를 JSON으로 직렬화하기 쉽기 때문에 TypeScript를 이러한 정책을 작성하는 언어로 선택했습니다. 또한 우리 작업의 기반이 된 원래 `ExpressionDefs`도 TypeScript로 작성되어 LiveGraph 및 기타 기존 시스템과 통합하기가 더 쉬웠습니다. TypeScript를 사용하여 이러한 정책을 작성하기 쉽게 만들기 위해 타입과 변수를 추가했습니다. 모든 것에 타입을 추가하고, 사용 가능한 모든 필드에 대한 변수를 추가하고, 가장 일반적인 작업에 대한 편의 함수를 추가했습니다. 결국 정책은 다음과 같이 보였습니다:

```typescript
class DenyEditsForRestrictedTeamUser extends DenyFilePermissionsPolicy {
  description = 'This user has a viewer-restricted seat in a Pro plan, so will not be able to edit the file.'

  applyFilter: ExpressionDef = {
    and: [
      not(isOrgFile(File)),
      teamUserHasPaidStatusOnFile(File, TeamUser, '=', AccountType.RESTRICTED),
    ],
  }

  // 다음과 같이 컴파일됨:
  applyFilter: ExpressionDef = {
    and: [
      not(["file.orgId", "<>", null]),
      or([
        and([
          "file.editor_type", "=", "design"],
          ["team_user.design_paid_status", "=", "restricted"
        ]),
        and([
          "file.editor_type", "=", "figjam"],
          ["team_user.figjam_paid_status", "=", "restricted"
        ]),
      ])
    ],
  }

  permissions = [FilePermission.CAN_EDIT_CANVAS]
}
```

대부분의 값이 `enum` 또는 `const` 객체를 사용하여 강력하게 타입이 지정되어 있음을 알 수 있습니다. 오타가 없습니다! 또한 일반적으로 사용되는 로직 스니펫은 `ExpressionDefs`를 반환하는 함수로 작성되어 다양한 정책 간에 쉽게 구성할 수 있습니다. 이는 또한 정책 간의 일관성을 유지하는 데 도움이 됩니다. 마지막으로 `and`, `not`, `or`, `exists`와 같은 편의 함수는 이러한 정책을 작성하는 경험을 더 우아하게 만들기 위해 구문 설탕을 추가합니다.

---

## 백엔드 구현

이 정책 DSL을 작동시키기 위해 논리적 정의에서 실제 구현으로 전환해야 했습니다. 두 가지가 최우선 순위였습니다. 첫째, boolean 로직 평가 엔진이 필요했습니다. 이것은 필요한 데이터를 나타내는 딕셔너리의 딕셔너리와 JSON 정책 선언을 받아 정책이 `true`인지 `false`인지 반환할 수 있는 작은 라이브러리였습니다. 이를 `ApplyEvaluator`라고 불렀습니다. 두 번째 구성 요소는 데이터베이스 로더였습니다. `file.id` 및 `team.created_at`과 같은 문자열에서 로직 엔진에 공급할 적절한 데이터를 로드하는 방법이 필요했습니다. 이 구성 요소를 창의적으로 `DatabaseLoader`라고 불렀습니다.

### ApplyEvaluator: Boolean 로직 엔진

boolean 로직 DSL은 TypeScript에서 다음과 같이 표현될 수 있습니다:

- `FieldName`: `.`으로 구분된 테이블 이름과 열 이름을 나타내는 문자열 (예: `file.name`, `user.email`)
- `Value`: 기본 데이터 타입을 나타냄

```typescript
export type FieldName = string;
export type Value = string | boolean | number | Date | null;
```

이를 기반으로 왼쪽에 `FieldName`, 중앙에 원하는 작업을 나타내는 문자열, 오른쪽에 `Value` 또는 필드에 대한 참조(`ExpressionArgumentRef`)가 있는 트리플인 `BinaryExpressionDef`가 있습니다. `ExpressionArgumentRef`는 문자열 리터럴과 `FieldNames`에 대한 참조를 구별하기 위해 객체로 래핑된 `FieldNames`입니다.

```typescript
export type BinaryExpressionDef = [
  FieldName,
  '=' | '<>' | '>' | '<' | '>=' | '<=',
  Value | ExpressionArgumentRef,
]

const type ExpressionArgumentRef = { type: 'field'; ref: FieldName }
```

마지막으로 이러한 `BinaryExpressionDef`는 `and` 및 `or` 연산자를 사용하여 결합할 수 있습니다. `ExpressionDef`는 이 세 가지 타입의 유니온입니다.

```typescript
export type ExpressionDef =
  | BinaryExpressionDef
  | OrExpressionDef
  | AndExpressionDef

export type OrExpressionDef = { or: ExpressionDef[] }
export type AndExpressionDef = { and: ExpressionDef[] }
```

이제 `ExpressionDef`가 있으므로 그 안의 로직을 평가하는 간단한 함수를 작성할 수 있습니다:

```typescript
interface Dictionary<T> { [Key: string]: T; }

function evalExpressionDef(expr: ExpressionDef, data: Dictionary<Dictionary<Value>>) {
  // ExpressionDefs를 재귀적으로 탐색
  if (expr.and) {
    return expr.and.every(subExpr => evalExpressionDef(subExpr, data)
  }
  if (expr.or) {
    return expr.or.some(subExpr => evalExpressionDef(subExpr, data)
  }

  // BinaryExpressionDef 평가
  const [leftKey, operation, rightKeyOrValue] = expr;

  // 제공된 키를 사용하여 데이터에서 값 찾기
  const leftValue : Value = getValueFromKey(leftKey, data);
  const rightValue : Value = getValueFromKey(rightKeyOrValue, data);

  // 표현식 평가
  switch operation {
    case '=' return leftValue === rightValue
    // ...
  }
}
```

`ExpressionDefs`가 JSON 직렬화 가능하다는 사실을 고려하면 TypeScript, Ruby, 그리고 결국 Go를 위해 이를 다른 언어로 쉽게 작성할 수 있다는 것을 상상할 수 있습니다. 여러 언어로 여러 구현을 유지하는 것이 무섭게 보일 수 있지만 `ExpressionDefs`의 단순성 덕분에 여러 언어로 이러한 작은 라이브러리를 상당히 빠르게(시니어 엔지니어의 경우 2~3일) 작성할 수 있었습니다. 또한 모든 구현에서 정확히 동일한 테스트 스위트를 사용할 수 있어 일관성을 보장할 수 있었습니다.

### 데이터 로딩

`ExpressionDefs`의 주요 장점 중 하나는 주어진 권한 이름에 대해 필요한 모든 데이터 종속성을 계산할 수 있다는 것입니다. 특정 권한이 있는 모든 정책을 반복하고 `ExpressionDef`를 재귀적으로 탐색하여 참조된 모든 데이터 필드 목록을 반환할 수 있습니다. 이는 테이블 이름을 키로, 열을 배열의 값으로 사용하는 배열의 딕셔너리를 출력하며 다음과 같이 보입니다:

```json
{
  "file": ["id", "name", "created_at", "deleted_at"],
  "team": ["id", "permission", "created_at"],
  "org": ["id", "public_link_permission"],
  "user": ["id", "email"],
  "team_role": ["id", "level"],
  "org_user": ["id", "role"]
}
```

위에서 쿼리할 테이블과 열을 알고 있지만 실제로 쿼리할 **행**을 모릅니다. 이를 쿼리하는 방법을 이해하려면 권한 함수의 API로 돌아가야 합니다.

권한 함수는 리소스, 사용자, 권한 이름의 세 가지 인수를 받습니다:

```ruby
file.has_permission?(user, CAN_EDIT)
```

리소스(이 경우 `file`)와 `user` 객체에서 필요한 모든 리소스를 로드하는 데 필요한 모든 쿼리를 유추할 수 있습니다. 네 그룹의 리소스가 있다고 상상할 수 있습니다:

1. 호출 시점에 알려진 리소스 (`file` 및 `user`)
2. 메인 리소스 객체의 외래 키를 통해 로드된 리소스 (`file`을 통해)
3. `user` 객체의 열을 통해 로드된 리소스
4. 메인 리소스와 `user` 객체 모두의 열을 통해 로드된 리소스

```typescript
{
  // `has_permission?` 호출 시 알려진 리소스
  "file": ["id", "name", "created_at", "deleted_at"],
  "user": ["id", "email"],

  // `file` 객체를 통해 로드된 리소스
  "team": ["id", "permission", "created_at"],  // file.team_id
  "org": ["id", "public_link_permission"],     // file.org_id

  // `file`과 `user`의 조합을 통해 로드된 리소스
  "team_role": ["id", "level"],  // file.team_id + user.id
  "org_user": ["id", "role"]     // file.org + user.id
}
```

이 아이디어를 염두에 두고 ActiveRecord 모델에서 간단한 함수를 사용하여 이러한 ID를 정의하고 모든 모델을 쿼리할 수 있습니다:

```ruby
class File
  def context_path
    {
      :project => self.project_id,
      :team => self.team_id,
      :org => self.org_id,
      :file => self.file_id,
    }
  end
end

class User
  def context_path
    { :user => self.user_id }
  end
end

def get_context_path(resource, user)
  context_path = {}.merge(resource.context_path).merge(user.context_path)

  if context_path[:org] && context_path[:user]
    context_path[:org_user] = [context_path[:org], context_path[:user]]
  end

  if context_path[:team] && context_path[:user]
    context_path[:team_role] = [context_path[:team], context_path[:user]]
  end

  return context_path
end
```

이제 `context_path` 객체에는 주어진 권한 확인을 실행하는 데 필요한 모든 데이터를 로드하는 데 필요한 모든 ID가 포함됩니다. 새로운 로드 가능한 리소스가 추가되면 해당 컨텍스트 경로가 어떻게 채워질지 지정해야 합니다. 정책 리소스가 추가되면 컨텍스트 경로를 지정해야 합니다.

이 시스템의 큰 장점은 특정 리소스를 쿼리하는 방법을 설정한 후에는 엔지니어가 이러한 관계와 쿼리 방법에 대해 걱정할 필요가 없다는 것입니다. `file`, `user`, `org`, `team_role`을 참조하는 정책을 쉽게 작성할 수 있으며 **어떤** 특정 행을 참조하는지 걱정할 필요가 없습니다.

이 시스템의 다른 장점은 백엔드 엔진에 데이터를 로드하는 방법을 결정할 수 있는 완전한 제어권을 제공한다는 것입니다: 어떤 순서로, 어떤 쿼리를 사용하여, 복제본 또는 기본 데이터베이스를 사용하여, 데이터베이스에 대한 어떤 인터페이스를 사용하여, 캐싱을 사용하는 등. 정책 작성자는 이 중 어느 것도 걱정하거나 신경 쓸 필요가 없습니다.

이 모든 조각들이 함께 모이면 다음과 같은 기본 알고리즘을 상상할 수 있습니다:

```typescript
function hasPermission(resource, user, permissionName) {
  // 관련된 모든 정책 찾기
  const policies = ALL_POLICIES
    .filter(p => p.permissions.include(permissionName))

  // 정책에서 필요한 모든 리소스 파싱
  const resourcesToLoad = policies.reduce((memo, p) => {
    const dataDependencies = parseDependences(p.applyFilter)
    return memo.merge(dataDependencies)
  }, {})

  // 필요한 모든 데이터 로드
  const loadedResources = DatabaseLoader.load(resourceToLoad)

  // 정책을 DENY 및 ALLOW 정책으로 분리
  const [denyPolicies, allowPolicies] = policies
    .bisect(p => p.effect === DENY)

  // DENY 정책 중 하나라도 true로 평가되면 false 반환
  const shouldDeny = denyPolicies.any(p => {
    return ApplyEvaluator.evaluate(loadedResources, p.applyFilter)
  })
  if (shouldDeny) {
    return false
  }

  // ALLOW 정책 중 하나라도 true로 평가되면 true 반환
  return allowPolicies.any(p => {
    return ApplyEvaluator.evaluate(loadedResources, p.applyFilter)
  })
}
```

정책 정의 DSL, `ApplyEvaluator`, `DatabaseLoader`가 전체 시스템의 주요 구성 요소가 되었습니다. 이러한 구성 요소를 통해 시스템의 성능을 개선하기 위해 시스템을 반복할 수 있었습니다. 예를 들어 이미 false로 평가된 정책을 다시 평가하지 않거나 이미 메모리에 있는 데이터(함수에 전달된)를 사용하는 것과 같은 간단한 것들입니다.

---

## DSL: 추가 결과

초기 구현이 작동하고 시스템이 가동되고 실행된 후, 성능을 개선하고 사용자 피드백에 응답하며 시스템을 더 안전하게 만들기 위해 계속 반복했습니다. 이를 통해 DSL의 특성과 우리가 구축한 시스템 덕분에 가능해진 세 가지 정말 흥미로운 기능을 발견했습니다.

### 디버깅

Sinatra 데이터베이스 로더와 TypeScript `ApplyEvaluator` 및 정책을 사용하여 프론트엔드 디버거를 구축했습니다. 엔지니어링 및 지원 팀의 Figma 직원은 사용자 ID와 리소스 ID를 입력할 수 있으며, 백엔드에서 필요한 모든 데이터를 로드합니다. 그런 다음 HTTP 경로를 통해 해당 데이터를 프론트엔드로 로드하고 재귀 컴포넌트를 통해 `ApplyEvaluator`를 React에 통합했습니다. 이미 TypeScript 구현이 있었고 데이터 로딩과 논리적 평가가 상당히 분리되어 있었기 때문에 결국 간단했습니다. 또한 모든 종류의 디버깅 정보를 제시할 수 있는 완전한 자유를 제공했습니다. 사용자는 `and` 및 `or` 권한 규칙을 확장하고 축소하고, 평가된 데이터를 읽고, 규칙이 `true` 또는 `false`로 평가되었는지 표시하고, 이를 통해 잘못되었거나 잘못된 것처럼 보이는 정책의 정확한 라인을 찾을 수 있습니다.

또한 이 원칙을 명령줄로 확장하여 사용자가 특정 정책에서 디버깅을 활성화하고, 테스트를 실행할 때 환경 변수를 전달하고, 해당 정책이 어떻게 평가되었는지에 대한 자세한 분석을 얻을 수 있습니다.

```typescript
[DenyEditsForNonPaidOrgUser] Filter passed to should_apply:
[AND] true:
  - ["file.parent_org_id"]: 5281 <> null : true
  [NOT] true:
    [AND] false:
      - ["file.parent_org_id"]: 5281 <> null : true
      - ["file.team_id"]: 6697 = null : false
      - ["file.folder_id"]: 21654 <> null : true
      - ["org_user.drafts_folder_id"]: 21652 = { "ref": "file.folder_id"} : false
  [OR] true:
    [OR] true:
      [AND] true:
        - ["file.editor_type"]: "design" = "design" : true
        - ["org_user.account_type"]: "restricted" = "restricted" : true
```

다시 한 번, 우리가 여기에 있었던 DSL은 이러한 종류의 도구를 상당히 쉽게 구축하는 데 필요한 모든 유연성을 제공했습니다.

### 성능: 지연 로딩, 단락 평가

플랫폼의 초기 버전이 작동한 후, 구현의 성능을 최적화하고 개선하는 데 시간을 투자했습니다. 특정 권한이 실제로 12개 이상의 다른 테이블을 로드할 수 있다는 것을 알고 있었습니다. 이것은 이 모든 데이터가 필요했기 때문이 아니라 사용자가 리소스에 대한 액세스를 얻을 수 있는 많은 가능한 방법(여러 허용 정책)이 있었기 때문입니다. 우리 시스템에서 모든 거부 정책은 `false`로 평가**되어야** 하지만 사용자에게 권한을 부여하려면 하나의 허용 정책만 `true`로 평가되면 됩니다.

이 때문에 필요한 데이터의 일부만 로드하고 해당 정보로 정책을 평가하고 싶었습니다. 확실한 `true` 또는 `false` 평가가 있으면 데이터베이스에 대한 추가 이동 없이 일찍 종료할 수 있습니다. 정책을 `true` 또는 `false`로 확실하게 평가할 수 없으면 확실한 답을 얻을 때까지 데이터베이스에서 데이터를 계속 로드합니다. 이 최적화를 수행하려면 `ApplyEvaluator`로부터 하나의 새로운 기능이 필요했습니다: ApplyEvaluator가 정책의 일부를 확실하게 평가할 수 없을 때 알려주어야 했습니다.

예를 들어 다음 `ExpressionDef`를 사용하면 아래 데이터와 다음 `ExpressionDef`가 있을 때 실제로 정책을 계속 평가할 필요가 없습니다. 논리적으로 이 표현식이 항상 true로 평가될 것을 알기 때문입니다.

```typescript
{
  // 데이터
  "team": { "permission": "secret" },
  "file": PENDING_LOAD,    // 이 행을 로드하지 않았습니다!
  "project": PENDING_LOAD,
}

{
  // ExpressionDef
  "and": [
    ["file.id", "<>", null],           // ?
    ["team.permission", "=", "open"],  // false
    ["project.deleted_at", "<>", null], // ?
  ]
}
```

동일한 데이터가 주어졌을 때 상위 문을 `or`로 변경하면 이제 이 정책이 실제로 `true` 또는 `false`로 평가된다고 말하려면 `file.id`와 `project.deleted_at`를 알아야 합니다.

```typescript
{
  "or": [
    ["file.id", "<>", null],
    ["team.permission", "=", "open"],  // false
    ["project.deleted_at", "<>", null],
  ]
}
```

이것은 정말 true 및 false와 완전히 다른 세 번째 불확정 상태입니다. 우리는 이 불확정 상태를 `null`로 표현했으며, 이는 정책을 결정적으로 평가할 수 없다는 것을 의미합니다.

우리는 이 새로운 상태를 사용하여 데이터베이스 로딩을 최적화했습니다. 정책에 대한 모든 데이터 요구 사항을 수집한 후, 모든 테이블 종속성을 순차적으로 로드할 개별 로드 단계 세트로 분할했습니다. 어떤 테이블이 평가된 권한 결정으로 가장 자주 이어지는지에 대한 휴리스틱을 사용하여 이 목록을 나누는 방법을 결정했습니다. 파일, 폴더, 팀 역할은 사용자가 리소스에 대한 액세스를 얻는 두 번째로 일반적인 방법(링크 액세스 후)이며, 로드 계획에서 이를 우선순위로 지정했습니다. 이 배열을 생성한 후, 모든 리소스를 로드하고 `ApplyEvaluator`에 공급하기 위해 순서대로 반복했습니다. `ApplyEvaluator`가 `true` 또는 `false`를 반환하면 실행을 단락하고 결과를 반환할 수 있음을 의미했습니다. `null`을 반환하면 필요한 다음 리소스 배치를 로드했습니다. 이 상당히 간단한 최적화는 권한 평가 시간의 총 실행 시간을 절반 이상 줄였고 데이터베이스 사용을 최소화할 수 있게 했습니다.

### 정적 분석

마지막으로 DSL을 사용하는 또 다른 큰 장점은 정책에 대한 정적 분석을 쉽게 수행할 수 있다는 것이었습니다. 새로운 기능 개발의 여러 순간에 정책에서 발견된 논리적 문제와 관련된 여러 버그를 발견했습니다. 발견한 가장 명백한 문제는 중앙에 `=` 작업이 있고 `ExpressionDef`의 오른쪽에 필드 참조가 있는 `BinaryExpressionDefs`였으며, 여기서 두 값 모두 `null`로 평가되었습니다.

```typescript
{ "file": { "team_id": null }, "team": { "id": null } }
```

```typescript
["file.id", "=", { "ref": "team.id" }]
```

이 조건은 실제로 `true`로 평가되지만 아마도 정책 작성자가 표현하려고 한 것이 아닐 것입니다.

정책 작성자가 대신 해야 했던 것은 이러한 필드 중 하나가 `null`이 아닌지 확인하는 것입니다. `and` 아래의 형제 확인으로 쉽게 수행할 수 있습니다:

```typescript
{
  "and": {
    ["team.id", "<>", null],
    ["file.id", "=", { "ref": "team.id" }]
  }
}
```

이를 기반으로 단위 테스트에 린터를 도입했으며, 이는 모든 정책 `ExpressionDefs`를 반복하고 `=` 작업이 있는 오른쪽 필드 참조에 `and` `ExpressionDef` 아래에 `<> null` 확인이 함께 없으면 오류를 발생시킵니다. 기본적으로 참조 중 하나가 `null`이 아닌지 확인하지 않은 경우 두 필드 이름을 비교하는 것을 허용하지 않았습니다. `<>` 확인과 같은 유사한 작업과 관련된 유사한 규칙도 구현했습니다.

DSL이 JSON 직렬화 가능하기 때문에 이 린터를 작성하는 데 특수한 AST 파싱이나 유사한 것이 필요하지 않았습니다. 대신 `ExpressionDefs`를 재귀적으로 반복하는 간단한 TypeScript가 있었습니다. CI/CD 파이프라인에 이 린터 확인을 추가한 후, 이 린터는 프로덕션에 나타날 수 있었던 몇 가지 다른 버그를 잡을 수 있었습니다.

어느 시점에 엔진 자체에 이러한 유형의 확인을 추가하는 것을 고려했지만 두 가지 이유로 정적 분석을 선택했습니다. 첫째, 빌드 시간에만 평가되므로 한 번만 구현하면 되고 크로스 플랫폼일 필요가 없었습니다. 둘째, 빌드 시간 확인이었기 때문에 엔지니어들이 테스트하거나(더 나쁘게는!) 프로덕션이나 스테이징에서 이를 만날 때까지 기다릴 필요 없이 버그를 더 빠르게 발견할 수 있었습니다. 마지막으로 여러 엔진을 갖는 접근 방식은 엔진이 매우 간단하기 때문에만 실행 가능하다는 것을 이해했습니다. 정말 필요한 경우가 아니면 엔진을 변경하고 싶지 않았습니다.

---

## 호기심을 유지하며

이 프로젝트를 시작할 때 우리가 자체 맞춤형 인증 DSL을 설계하고 구현하게 될 것이라고 말했다면 믿지 않았을 것입니다. 아마도 제 자신의 편견이 작용하는 것 같습니다. 저는 일반적으로 "구축"보다 "구매"를 선호합니다(또는 "구축"보다 "설치"를 선호합니다). 하지만 문제가 우리를 그곳으로 데려갈 것이라고는 진심으로 생각하지 않았습니다. 그러나 당면한 문제에 집중하고 다양한 솔루션에 열려 있음으로써 Figma에 정말로 진정으로 효과가 있는 참신한 것을 생각해 냈습니다.

Ruby와 LiveGraph 코드베이스 간 로직의 차이로 인한 인시던트와 버그를 거의 제거했으며, 디버거를 통해 엔지니어링 및 지원 팀에 권한 확인을 예상하거나 이해하지 못할 때 스스로 차단을 해제할 수 있는 도구와 이러한 권한 규칙을 이해하고 깊이 파고들 수 있는 능력을 제공했습니다. 자체 DSL을 개발함으로써 매우 근본적인 수준에서 문제에 접근할 수 있었고 완전한 유연성을 제공할 수 있었으며, 이는 오늘날에도 계속해서 효과를 발휘하고 있습니다.
