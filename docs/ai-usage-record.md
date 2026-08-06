# AI 사용 기록

- 작성일: 2026-08-05
- 사용 도구: OpenAI Codex
- 대상 저장소: Backend `week4/community`, Frontend `community-FE`
- 기록 범위: 현재 작업 트리의 배포 문서, 북마크 설계 문서, 코드 리뷰 회고

## 1. 사용 목적

이번 작업에서는 AI를 완성된 결과를 그대로 제출하는 용도가 아니라, 두 저장소의
코드와 문서를 빠르게 탐색하고 근거를 정리하는 보조 도구로 사용했습니다. 주요
목적은 다음과 같습니다.

1. 실제 구현과 문서 사이의 불일치를 찾습니다.
2. 여러 파일에 흩어진 API, Database, 배포 정보를 하나의 문서로 구조화합니다.
3. 코드 리뷰에서 지적된 문제의 현재 반영 여부와 테스트 근거를 확인합니다.
4. 반복적인 표 작성, 문장 다듬기, 문서 간 Link 연결을 자동화합니다.

## 2. 작업별 활용 내용

| 작업 | AI에 제공한 정보 | AI가 지원한 내용 | 사람이 확인하거나 결정한 내용 |
| --- | --- | --- | --- |
| 배포 회고 정리 | A/B 방식 배포 파일, 검증 보고서, 실제 작업 순서 | 통합 회고 개편, A/B 방식별 글 분리, 설정과 검증 결과 비교, 출처별 참고 지점 정리 | A 방식은 Public IPv4와 HTTP로 검증했고 Dynu·HTTPS는 B 작업 단계에서 처음 적용했다는 사실관계를 확정했습니다. 실제 AWS Console과 EC2 작업 및 결과 확인은 직접 수행했습니다. |
| 인프라 대안 비교 | 비용 제약, 서비스 구성, Container 자원 사용량 | `t3.micro`와 `t3.small`, 무료 DDNS 후보, A/B 배포 구조의 장단점을 비교했습니다. | 가장 저렴한 `t3.micro` 대신 메모리 여유가 있는 `t3.small`을 선택했고, 첫 제안인 DuckDNS 대신 직접 비교한 Dynu를 선택했습니다. |
| 북마크 문서 작성 | Controller, Service, Entity, Repository, Flyway Migration | Endpoint, 인증·CSRF, 응답 Code, 멱등성, Pagination, ERD, Constraint, Index를 추출해 명세로 구조화했습니다. | 문서 내용이 현재 코드와 Migration을 벗어나지 않는지 대조하고, 구현되지 않은 동작을 임의로 추가하지 않았습니다. |
| 코드 리뷰 회고 | 리뷰 항목 4~7, Backend·Frontend 구현, 관련 테스트 | 개인정보 조회 인가, 회원가입 서버 검증, 오류 Code와 문구 Mapping, 검증 Message 보존 여부를 추적하고 항목별 회고 초안을 작성했습니다. | 각 지적이 현재 코드에 반영됐는지 확인하고, 완전 반영과 부분 반영을 구분했습니다. 전체 `fieldErrors` 응답은 아직 없다는 한계도 유지했습니다. |
| 문서 품질 정리 | 기존 Markdown과 저장소 경로 | 표와 제목 형식 통일, 중복 설명 축소, 관련 문서 Link 연결, 민감정보 노출 가능성 점검을 지원했습니다. | 공개 문서에 DB 비밀번호, JWT 원문, DDNS 갱신 비밀번호, AWS 자격증명, 개인 키를 넣지 않는 기준을 적용했습니다. |

## 3. AI에 요청한 내용의 예시

아래 문장은 실제 대화 원문 전체가 아니라, 작업 중 AI에 전달한 요청을 재현할 수
있도록 요약한 예시입니다.

- "현재 Backend와 Frontend 구현을 기준으로 코드 리뷰 4~7번의 반영 여부를
  확인하고, 항목별 기술 회고로 작성해 주세요."
- "북마크 관련 Controller, Service, Entity, Repository, Migration을 확인해
  현재 구현과 일치하는 API 명세와 ERD를 작성해 주세요."
- "A 방식과 B 방식의 실제 검증 범위를 구분하고, 배포 회고의 사실관계와 문서
  Link를 일관되게 정리해 주세요."
- "문서에 Secret이나 확인되지 않은 운영 정보가 포함되지 않았는지 점검해
  주세요."

## 4. 생성·수정에 AI가 관여한 주요 산출물

### 배포 문서

- [`deployment/DEPLOYMENT_TECH_BLOG.md`](../deployment/DEPLOYMENT_TECH_BLOG.md)
- [`deployment/DEPLOYMENT_METHOD_A_TECH_BLOG.md`](../deployment/DEPLOYMENT_METHOD_A_TECH_BLOG.md)
- [`deployment/DEPLOYMENT_METHOD_B_TECH_BLOG.md`](../deployment/DEPLOYMENT_METHOD_B_TECH_BLOG.md)
- [`deployment/DEPLOYMENT_TECH_BLOG_SOURCES.md`](../deployment/DEPLOYMENT_TECH_BLOG_SOURCES.md)
- A/B 방식의 README, 구현 과정, 수동 가이드, 검증 보고서

### 기능 설계 문서

- [`bookmark-api-spec.md`](./bookmark-api-spec.md)
- [`bookmark-erd.md`](./bookmark-erd.md)

### 코드 리뷰 회고

- [`code-review-retrospective/README.md`](./code-review-retrospective/README.md)
- 개인정보 조회 인가, 회원가입 서버 검증, 오류 Message Mapping, 검증 Message
  보존에 관한 4개의 회고 문서

현재 작업 트리에서 AI가 직접 생성하거나 수정한 내용은 문서가 중심이며, 이
기록은 AI가 현재 제품 코드를 새로 구현했다고 주장하지 않습니다.

## 5. 검증 방법

AI의 설명만으로 완료를 판단하지 않고 다음 근거를 사용했습니다.

1. 문서의 API 설명을 실제 Controller와 Service Method에 대조했습니다.
2. ERD의 Column, Foreign Key, Unique Constraint, Index를 JPA Entity와 MySQL·H2
   Flyway Migration에 대조했습니다.
3. 배포 글의 상태를 A/B 방식별 검증 보고서와 실행 기록에 대조했습니다.
4. 코드 리뷰 회고는 관련 Backend 통합 테스트와 Frontend 단위 테스트 결과를
   근거로 작성했습니다. 구체적인 명령과 통과 개수는
   [`code-review-retrospective/README.md`](./code-review-retrospective/README.md)에
   기록했습니다.
5. 마지막으로 Git Diff와 Markdown Link를 확인해 기존 사용자 변경을 보존하고
   문서 간 표현이 충돌하지 않는지 점검했습니다.

## 6. 사람과 AI의 역할 구분

AI는 저장소 탐색, 초안 작성, 대안 비교, 로그와 테스트 결과 해석, 문서 형식
정리에 사용했습니다. 반면 요구사항의 우선순위, 비용을 감수할 범위, 인프라의
최종 선택, 공개 가능한 정보의 범위, 작업 완료 기준은 사람이 결정했습니다.

또한 AI에는 운영 Secret을 제공하지 않았으며, AI가 AWS 계정이나 EC2에 대신
접속해 운영 작업을 수행하지 않았습니다. 실제 환경의 작업 결과는 사용자가 직접
확인한 기록만 문서에 반영했습니다.

## 7. 한계와 보완

- AI는 저장소에 기록되지 않은 운영 상태를 알 수 없으므로 실제 EC2 상태를
  추측하지 않았습니다.
- 문서와 코드가 이후 변경되면 이 기록의 설명도 달라질 수 있습니다.
- AI가 만든 초안에는 사실관계 혼합이나 과도한 일반화가 생길 수 있어 코드,
  Migration, 테스트, 검증 보고서를 기준으로 다시 확인했습니다.
- 검증하지 못한 내용은 완료로 표현하지 않고 제한 사항이나 후속 작업으로
  남겼습니다.

이번 작업에서 AI의 핵심 가치는 결정을 대신하는 데 있지 않았습니다. 흩어진
근거를 빠르게 모아 비교 가능한 형태로 만들고, 사람이 더 정확하게 판단할 수
있도록 작업 속도와 문서 품질을 높이는 데 활용했습니다.
