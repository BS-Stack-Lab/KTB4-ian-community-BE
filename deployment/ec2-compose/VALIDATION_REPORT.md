# Method B 검증 보고서

## 2026-08-06 Gate 1 CI/CD 검증 추가 기록

기존 아래 기록은 초기 3서비스/Host Nginx 방식의 과거 검증 이력으로
보존한다. 현재 구현은 `mysql`, `backend`, `frontend`, `nginx`의 정확한
4서비스 Compose와 edge Nginx TLS 종료 구조다.

| Control | Result |
| --- | --- |
| Backend source/image | Gradle 63 tests와 `bootJar` PASS, amd64 UID 10001 JRE-only 이미지 PASS |
| Frontend source/image | build PASS(기존 size warning 3건), unit 140, integration 19, amd64 UID 101 static-origin 이미지 PASS |
| Frontend UI/E2E | 기존 Chromium UI 111 PASS, 단일 edge origin Backend E2E 12/12 PASS |
| Compose runtime | 정확한 4서비스 healthy, 내부 서비스 Host port 미공개, edge HTTP/API/SPA/cache/body-limit/block 정책 PASS |
| Persistence | MySQL test row와 upload marker가 MySQL/Backend/Frontend 재시작 후 유지됨 |
| Static policy | workflow YAML parse, Action full SHA, gate truth table, Bash syntax, Compose render, `nginx -t`, `git diff --check` PASS |

로컬 전체 `format:check`는 CI/CD 범위 밖의 미추적 회고 Markdown 5개가 가진
기존 Prettier 불일치 때문에 실패한다. 해당 사용자 파일은 수정하지 않았고,
추적 대상 CI/CD 변경과 clean CI checkout에는 이 불일치가 포함되지 않는다.

Gate 1에서 commit, push, PR, GitHub/AWS 설정, 운영 배포는 수행하지 않았다.

작성일: 2026-08-04

## 범위

React와 Spring의 멀티스테이지 Docker Image, MySQL 8.4, Docker Compose
통합 구성, Frontend Nginx Reverse Proxy를 운영 데이터와 분리된 로컬
Compose Project에서 검증했다. Apple Silicon Host에서 EC2 대상
`linux/amd64` Image를 에뮬레이션해 실행했다.

Dynu와 HTTPS는 이 B 작업 단계에서 EC2 공통 Host 진입점으로 처음 적용했으며,
Compose 격리 검증과 구분해 아래에 별도로 기록한다.

검증 Image:

- Backend: `community-backend:a2b2e5a`
- Frontend: `community-frontend:296b311`
- MySQL: `mysql:8.4.7`

테스트용 Secret과 데이터는 임시 디렉터리만 사용했으며 검증 후 Container,
Network, 데이터와 Secret 파일을 삭제했다. 운영 Secret은 사용하거나
출력하지 않았다.

## 결과

| Control                     | Status    | Evidence                                                                                                            |
| --------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------- |
| Backend Source Test         | PASS      | Gradle 57 tests, failures·errors·skipped 0                                                                          |
| Frontend Format             | PASS      | Prettier 대상 전체 통과                                                                                             |
| Frontend Unit Test          | PASS      | 34 files, 127 tests                                                                                                 |
| Frontend Integration Test   | PASS      | 10 files, 19 tests                                                                                                  |
| Frontend UI Test            | PASS      | Playwright 108 tests 중 99개 1차 통과, macOS `ERR_NETWORK_IO_SUSPENDED` 영향 9개를 단일 Worker로 재실행해 전부 통과 |
| Frontend Production Build   | PASS/WARN | Webpack Build 성공, 기존 대용량 Asset 경고 3건                                                                      |
| Backend 멀티스테이지 Build  | PASS      | JDK builder에서 Test·Boot JAR 생성 후 JRE runtime으로 복사, `linux/amd64` Image 생성                                |
| Frontend 멀티스테이지 Build | PASS      | Node builder에서 React Production Build 후 Nginx runtime으로 정적 산출물만 복사                                     |
| Frontend Image 단독 검증    | PASS      | Non-root, SPA fallback, `/api` Proxy, immutable Asset Cache, runtime Node 부재, `nginx -t` 성공                     |
| Compose 구성                | PASS      | `docker compose config --quiet` 성공                                                                                |
| MySQL Container             | PASS      | running·healthy, UID/GID `999:999`, privileged false, Host Port 미공개                                              |
| Backend Container           | PASS      | running·healthy, UID/GID `10001:10001`, privileged false, read-only root filesystem, Host Port 미공개               |
| Frontend Container          | PASS      | running·healthy, User `nginx`, privileged false, read-only root filesystem, 검증용 `127.0.0.1:18088`만 공개         |
| Image Platform              | PASS      | Backend·Frontend·MySQL 모두 `linux/amd64`                                                                           |
| Upload Mount                | PASS      | Backend에서 `/var/lib/community/uploads` 쓰기 가능                                                                  |
| Backend Health              | PASS      | Actuator body `status=UP`, components/details 미노출                                                                |
| H2 Console                  | PASS      | `/h2-console` 응답 401로 운영 접근 차단                                                                             |
| React 정적 서빙             | PASS      | `/` 응답에서 React root 확인                                                                                        |
| Nginx Health                | PASS      | `/healthz` HTTP 200                                                                                                 |
| Nginx Reverse Proxy         | PASS      | Frontend `/api/csrf` → Backend HTTP 200                                                                             |
| 재시작 영속성               | PASS      | 전체 Compose 재시작 후 임시 MySQL Record와 Upload Marker 유지                                                       |
| 재시작 후 Health            | PASS      | 세 Container healthy, Frontend health HTTP 200                                                                      |
| 비정상 재시작               | PASS      | 세 Container `RestartCount=0`                                                                                       |
| Shell·Diff                  | PASS      | 배포 Script `bash -n`, Backend·Frontend `git diff --check` 성공                                                     |

## B 작업 단계에서 추가한 공통 Host 진입점

B 방식 작업을 진행하면서 A 방식에는 없었던 Dynu 고정 Hostname과 HTTPS를 처음
추가했다. 인증서는 B Container 안에 넣지 않고 EC2 Host Nginx에서 종료하도록
구성했으며, 운영 Secret 원문은 검증 기록에 포함하지 않았다.

| Control       | Status | Evidence                                                               |
| ------------- | ------ | ---------------------------------------------------------------------- |
| Dynu DNS      | PASS   | `pulse.gleeze.com` A Record와 EC2 Public IPv4 일치                     |
| HTTPS         | PASS   | 외부 health 200, 인증서 신뢰 검증 성공                                 |
| HTTP Redirect | PASS   | 같은 경로의 HTTPS 주소로 301                                           |
| 자동 갱신     | PASS   | `community-dynu.timer`, `certbot.timer` active 및 Certbot dry-run 성공 |

검증 시점 Resource Snapshot:

| Service  |    Memory |   Limit |  Usage |
| -------- | --------: | ------: | -----: |
| MySQL    | 311.6 MiB | 640 MiB | 48.68% |
| Backend  | 537.4 MiB | 900 MiB | 59.71% |
| Frontend |  17.7 MiB | 128 MiB | 13.82% |

이 수치는 Apple Silicon의 amd64 에뮬레이션 환경에서 한 번 측정한
Snapshot이므로 EC2 성능 결론으로 사용하지 않는다. 실제 EC2에서는 B 방식
Frontend Container가 port 80에서 healthy인 상태까지 확인한 뒤
`127.0.0.1:18088` 격리 검증을 마치고 `restart=no`로 중지했다. A와 B가 동일
Host port 80을 동시에 점유할 수 없기 때문이다. 현재 최종 주소는 B 작업
단계에서 추가한 Dynu·HTTPS Host 진입점을 유지하면서 Host Nginx가 제공한다.

## 제출 판정

B 방식 Source 요구사항인 React·Spring 멀티스테이지 Dockerfile, 세 Service
Compose 통합, Nginx Reverse Proxy와 런타임 격리·Health·Persistence 검증은
PASS다. B 방식의 별도 공개 URL을 동시에 요구하는 과제라면 추가 EC2 또는
별도 Port/Domain 구성이 필요하지만, 구성과 재현 가능한 실행 증빙에는
영향이 없다.
