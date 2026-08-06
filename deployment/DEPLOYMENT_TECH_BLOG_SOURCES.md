# 배포 회고 기술 블로그 출처별 참고 지점

- 정리일: 2026-08-05
- 대상 문서:
  - [통합 회고](./DEPLOYMENT_TECH_BLOG.md)
  - [A 방식 독립 회고](./DEPLOYMENT_METHOD_A_TECH_BLOG.md)
  - [B 방식 독립 회고](./DEPLOYMENT_METHOD_B_TECH_BLOG.md)

## 문서 목적

이 문서는 배포 회고 본문에서 참고한 링크와 구체적인 참고 지점을 분리해
기록합니다. 참고 글의 문장이나 코드를 복사한 것이 아니라, 문제를 설명하는
순서와 선택 기준을 세우는 방법, 공식 기능 정보를 확인하는 데 사용했습니다.

본문의 배포 구조와 Script, 설정값, 오류 및 검증 결과는 PULSE 프로젝트에서
직접 구현하고 확인한 내용입니다. 아래 자료의 대규모 운영 사례나 성능 수치를
PULSE의 결과인 것처럼 사용하지 않습니다.

## 1. Toss Tech — 레거시 인프라 작살내고 하이브리드 클라우드 만든 썰

- 링크: <https://toss.tech/article/payments-legacy-9>
- 참고한 부분:
  - `레거시 인프라의 참상`에서 기존 상태의 문제를 먼저 구체적으로 드러내는
    전개를 참고했습니다.
  - ‘첫 번째 해결책: 퍼블릭 클라우드’와 ‘하지만 퍼블릭 클라우드에도 한계가
    있었습니다’에서 첫 선택의 장점뿐 아니라 한계까지 이어서 설명하는 방식을
    참고했습니다.
  - ‘우리의 결론: 프라이빗 클라우드 + 퍼블릭 클라우드’와 ‘프라이빗
    클라우드가 만족해야 할 3가지’에서 대안을 선택하기 전에 요구 조건을 먼저
    고정하는 구조를 참고했습니다.
  - 이후 반복되는 `고민 → 문제점 → 해결책 → 최종 구성` 흐름을 참고했습니다.
- 본문에 반영한 위치:
  - `과제 요구사항에 따라 두 가지 방식으로 나누어 진행했습니다`에서 과제의
    요구사항을 밝히고, A를 수행한 뒤 B에서 편리해진 점을 비교했습니다.
  - A 방식과 B 방식의 최종 구조를 각각 그림으로 제시하기 전에 기존 문제와
    선택 기준을 설명했습니다.
  - A와 B의 각 방식 안에 실패·수정 과정을 배치해 성공한 결과만 나열하지 않고
    실제 오류가 다음 실행과 설계에 어떤 영향을 주었는지 연결했습니다.
- 참고하지 않은 것:
  - OpenStack, Kubernetes, Active-Active 구성 자체는 이번 단일 EC2 배포에
    적용하지 않았습니다.

## 2. Toss Tech — OpenZFS로 성능과 비용, 두 마리 토끼 잡기

- 링크: <https://toss.tech/article/engineering-note-8>
- 참고한 부분:
  - `문제: Warm 존에서 발생한 성능 저하`에서 저렴한 구성을 포기하지 않으면서
    성능 문제를 해결할 방법을 찾는 비용·성능 관점을 참고했습니다.
  - `성능 최적화 과정`에서 사용 가능한 선택지를 설명한 후 실제 사용 패턴을
    확인하고 대안을 비교하는 순서를 참고했습니다.
  - `OpenZFS 설정 최적화 테스트`의 스토리지·서버·설정·측정 도구처럼 실험
    조건을 분리해 기록하는 방식을 참고했습니다.
  - 마지막에 기존 구성과 변경 구성을 수치로 비교해 결론을 확인하는 방식을
    참고했습니다.
- 본문에 반영한 위치:
  - `비용을 아끼되 t3.micro를 고집하지 않은 이유`에서 가장 저렴한 옵션과
    운영 여유 사이의 선택 과정을 설명했습니다.
  - `무엇을 보고 배포 성공이라고 판단했을까요?`에서 명령 실행 여부가 아니라
    테스트 개수, Listener, 보안 속성, 메모리 Snapshot을 표로 기록했습니다.
  - B 방식 Resource Snapshot은 측정 환경의 한 시점 값이며 EC2 성능으로
    일반화하지 않는다고 범위를 명시했습니다.
- 참고하지 않은 것:
  - OpenZFS 설정이나 토스페이먼츠의 스토리지 성능 수치는 PULSE에 적용하거나
    인용하지 않았습니다.

## 3. Toss Tech — 유연하고 안전하게 배포 Pipeline 운영하기

- 링크: <https://toss.tech/article/slash23-devops>
- 참고한 부분:
  - `첫 번째 어려움: 가시성`에서 UI에만 존재하는 설정은 전체 흐름과 변경
    이력을 파악하기 어렵다는 문제 정의를 참고했습니다.
  - `Pipeline as Code`에서 운영 설정을 파일로 만들고 Git에서 변경 이력을
    관리하는 관점을 참고했습니다.
  - `GoCD Template`, `Helm Template`에서 공통 부분과 환경별 값을 분리하는
    방식을 참고했습니다.
  - `CI`에서 변경사항을 자동으로 검증하고 성공했을 때만 반영하는 원칙을
    참고했습니다.
- 본문에 반영한 위치:
  - A 방식 설치 순서를 번호가 붙은 Script로 만들고 systemd·Nginx 설정을
    저장소에서 관리했습니다.
  - B 방식의 공통 구조는 `compose.yaml`, 공개 가능한 환경별 값은 `.env`,
    비밀값은 별도 Secret 파일로 분리했습니다.
  - B 방식 `deploy.sh`는 모든 Container가 healthy일 때만 새 Release를
    `current.env`로 승격하도록 구성했습니다.
- 참고하지 않은 것:
  - GoCD, Helm, 토스뱅크의 Pipeline Template은 이 프로젝트에 직접 도입하지
    않았습니다. 설정을 코드로 관리하고 검증 후 반영한다는 원칙만
    참고했습니다.

## 4. AWS — Amazon EC2 T3 Instances

- 링크: <https://aws.amazon.com/ec2/instance-types/t3/>
- 참고한 부분:
  - `Product Details T3`의 인스턴스 표에서 `t3.micro`와 `t3.small`이 모두
    2 vCPU라는 점을 확인했습니다.
  - 같은 표에서 `t3.micro`는 1GiB, `t3.small`은 2GiB 메모리라는 차이를
    확인했습니다.
  - T3가 일시적으로 CPU 사용량이 높아지는 범용 Burstable Workload를 위한
    계열이라는 설명을 확인했습니다.
- 본문에 반영한 위치:
  - AI가 비용을 이유로 `t3.micro`를 제안했지만, JVM·MySQL·Nginx·운영체제를
    함께 실행할 메모리 여유를 위해 사용자가 `t3.small`을 선택한 근거로
    사용했습니다.
- 주의 사항:
  - AWS T3 제품 페이지에 표시된 가격은 특정 리전 기준일 수 있으므로 서울
    리전의 확정 월 비용으로 본문에 옮기지 않았습니다.

## 5. AWS — EC2 On-Demand Instance Pricing

- 링크: <https://aws.amazon.com/ec2/pricing/on-demand/>
- 참고한 부분:
  - On-Demand Instance는 장기 약정 없이 사용한 Compute Capacity에 따라
    과금된다는 설명을 확인했습니다.
  - 데이터 전송, Public IPv4, EBS 등 Compute 외 항목도 비용에 영향을 줄 수
    있음을 확인했습니다.
- 본문에 반영한 위치:
  - 월 Budget 15 USD와 12 USD 알림은 비용 상한을 강제로 막는 장치가 아니라
    확인을 위한 기준이라고 구분했습니다.
  - 가장 작은 인스턴스를 무조건 유지하기보다, 사용하지 않는 시간에
    인스턴스를 중지하면서 `t3.small`의 운영 여유를 선택했다고 설명했습니다.

## 6. DuckDNS — HTTP API Specification

- 링크: <https://www.duckdns.org/spec.jsp>
- 참고한 부분:
  - Token과 Domain을 포함한 한 번의 HTTP/HTTPS GET 요청으로 IP를 갱신할 수
    있다는 점을 확인했습니다.
  - IPv4 자동 감지, IPv4·IPv6 입력, 여러 Domain 갱신 옵션을 확인했습니다.
  - 별도의 `TXT Record API`가 제공된다는 점을 확인했습니다.
- 본문에 반영한 위치:
  - AI가 DuckDNS를 첫 번째 대안으로 제시한 이유를 “시작과 IP 갱신이
    단순하다”라고 설명한 근거로 사용했습니다.
- 비교 범위:
  - DuckDNS가 기능적으로 부족하다고 단정하지 않습니다. 공식 Spec에 공개된
    IP 갱신과 TXT 기능을 Dynu 공식 문서의 선택지와 비교했습니다.

## 7. Dynu — Free Dynamic DNS

- 링크: <https://freeddns.dynu.com/>
- 참고한 부분:
  - 무료 3단계 Hostname 또는 자체 Domain을 사용할 수 있다는 설명을
    확인했습니다.
  - Client, Script, API 등 여러 IP 갱신 방법을 선택할 수 있다는 설명을
    확인했습니다.
  - Web Redirect, Wildcard Alias, Offline 설정과 다양한 DNS Record를
    지원한다는 설명을 확인했습니다.
  - 계정 비밀번호와 별도의 IP Update Password를 사용할 수 있다는 보안
    설명을 확인했습니다.
- 본문에 반영한 위치:
  - DuckDNS보다 초기 설정이 조금 더 필요하지만 이후 선택할 수 있는 DNS와
    갱신 방식이 넓어 B 방식 작업 단계에서 Dynu를 선택했다는 판단에
    사용했습니다.
  - B 방식의 실패·수정 과정에서 계정 비밀번호를 입력해 갱신이 거부된 뒤 별도
    IP Update Password로 수정한 시행착오와 연결했습니다.

## 8. Dynu — API Documentation과 IP Update Protocol

- 링크:
  - <https://downloads.dynu.com/en-US/Resources/API/Documentation>
  - <https://www.dynu.com/en-US/DynamicDNS/IP-Update-Protocol>
- 참고한 부분:
  - API 문서에서 A, AAAA, CAA, CNAME, LOC, MX, PTR, SPF, TXT 등 여러 DNS
    Record를 관리할 수 있음을 확인했습니다.
  - IP Update Protocol에서 Primary IP, Alias/Subdomain, Group, 여러 Hostname,
    IPv4·IPv6 갱신 방식을 확인했습니다.
  - IP Update Password 원문 대신 MD5 또는 SHA-256 Hash를 전달할 수 있다는
    설명을 확인했습니다.
- 본문에 반영한 위치:
  - B 방식 작업에서 Dynu를 처음 적용한 이유와 `community-dynu.timer`,
    `update-dynu.sh`의 역할을 설명하는 근거로 사용했습니다.
  - PULSE Script에서는 갱신 비밀번호를 SHA-256으로 바꾸고 제한된 권한의
    파일에 저장하도록 구성했습니다.

## 사실과 프로젝트 판단의 구분

| 내용                                             | 구분                                              |
| ------------------------------------------------ | ------------------------------------------------- |
| `t3.micro` 1GiB, `t3.small` 2GiB                 | AWS 공식 사양                                     |
| `t3.small`을 선택함                              | PULSE의 메모리 여유와 비용을 비교한 사용자 판단   |
| DuckDNS의 HTTPS IP 갱신과 TXT API                | DuckDNS 공식 문서                                 |
| Dynu의 다양한 DNS Record와 갱신 방식             | Dynu 공식 문서                                    |
| DuckDNS 대신 Dynu를 선택함                       | 현재 요구와 이후 확장 가능성을 비교한 사용자 판단 |
| A는 Public IPv4 HTTP, B 단계에서 Dynu·HTTPS 추가 | 실제 작업 순서에 따른 프로젝트 사실               |
| B를 격리 검증한 뒤 Container를 중지함            | 한 EC2의 80번 포트 충돌을 반영한 프로젝트 판단    |

## 공개하지 않는 정보

다음 값은 출처 확인과 무관하며 저장소에도 기록하지 않습니다.

- AWS Access Key와 Session Token
- SSH Private Key와 PEM 파일
- MySQL Root·Application 비밀번호
- JWT Secret 원문
- Dynu 계정 비밀번호와 IP Update Password
