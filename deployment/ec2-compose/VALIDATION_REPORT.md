# Method B 검증 보고서

작성일: 2026-08-04

## 범위

React와 Spring의 멀티스테이지 Docker Image, MySQL 8.4, Docker Compose
통합 구성, Frontend Nginx Reverse Proxy를 운영 데이터와 분리된 로컬
Compose Project에서 검증했다. Apple Silicon Host에서 EC2 대상
`linux/amd64` Image를 에뮬레이션해 실행했다.

검증 Image:

- Backend: `community-backend:a2b2e5a`
- Frontend: `community-frontend:296b311`
- MySQL: `mysql:8.4.7`

테스트용 Secret과 데이터는 임시 디렉터리만 사용했으며 검증 후 Container,
Network, 데이터와 Secret 파일을 삭제했다. 운영 Secret은 사용하거나
출력하지 않았다.

## 결과

| Control | Status | Evidence |
|---|---|---|
| Backend Source Test | PASS | Gradle 57 tests, failures·errors·skipped 0 |
| Frontend Format | PASS | Prettier 대상 전체 통과 |
| Frontend Unit Test | PASS | 34 files, 127 tests |
| Frontend Integration Test | PASS | 10 files, 19 tests |
| Frontend UI Test | PASS | Playwright 108 tests 중 99개 1차 통과, macOS `ERR_NETWORK_IO_SUSPENDED` 영향 9개를 단일 Worker로 재실행해 전부 통과 |
| Frontend Production Build | PASS/WARN | Webpack Build 성공, 기존 대용량 Asset 경고 3건 |
| Backend 멀티스테이지 Build | PASS | JDK builder에서 Test·Boot JAR 생성 후 JRE runtime으로 복사, `linux/amd64` Image 생성 |
| Frontend 멀티스테이지 Build | PASS | Node builder에서 React Production Build 후 Nginx runtime으로 정적 산출물만 복사 |
| Frontend Image 단독 검증 | PASS | Non-root, SPA fallback, `/api` Proxy, immutable Asset Cache, runtime Node 부재, `nginx -t` 성공 |
| Compose 구성 | PASS | `docker compose config --quiet` 성공 |
| MySQL Container | PASS | running·healthy, UID/GID `999:999`, privileged false, Host Port 미공개 |
| Backend Container | PASS | running·healthy, UID/GID `10001:10001`, privileged false, read-only root filesystem, Host Port 미공개 |
| Frontend Container | PASS | running·healthy, User `nginx`, privileged false, read-only root filesystem, 검증용 `127.0.0.1:18088`만 공개 |
| Image Platform | PASS | Backend·Frontend·MySQL 모두 `linux/amd64` |
| Upload Mount | PASS | Backend에서 `/var/lib/community/uploads` 쓰기 가능 |
| Backend Health | PASS | Actuator body `status=UP`, components/details 미노출 |
| H2 Console | PASS | `/h2-console` 응답 401로 운영 접근 차단 |
| React 정적 서빙 | PASS | `/` 응답에서 React root 확인 |
| Nginx Health | PASS | `/healthz` HTTP 200 |
| Nginx Reverse Proxy | PASS | Frontend `/api/csrf` → Backend HTTP 200 |
| 재시작 영속성 | PASS | 전체 Compose 재시작 후 임시 MySQL Record와 Upload Marker 유지 |
| 재시작 후 Health | PASS | 세 Container healthy, Frontend health HTTP 200 |
| 비정상 재시작 | PASS | 세 Container `RestartCount=0` |
| Shell·Diff | PASS | 배포 Script `bash -n`, Backend·Frontend `git diff --check` 성공 |

검증 시점 Resource Snapshot:

| Service | Memory | Limit | Usage |
|---|---:|---:|---:|
| MySQL | 311.6 MiB | 640 MiB | 48.68% |
| Backend | 537.4 MiB | 900 MiB | 59.71% |
| Frontend | 17.7 MiB | 128 MiB | 13.82% |

이 수치는 Apple Silicon의 amd64 에뮬레이션 환경에서 한 번 측정한
Snapshot이므로 EC2 성능 결론으로 사용하지 않는다. 실제 EC2에서는 B 방식
Frontend Container가 port 80에서 healthy인 상태까지 확인한 뒤 A 방식 Host
Nginx로 전환하기 위해 `restart=no`로 중지했다. A와 B가 동일 Host port 80을
동시에 점유할 수 없으므로 현재 공개 서비스는 A 방식이다.

## 제출 판정

B 방식 Source 요구사항인 React·Spring 멀티스테이지 Dockerfile, 세 Service
Compose 통합, Nginx Reverse Proxy와 런타임 격리·Health·Persistence 검증은
PASS다. B 방식의 별도 공개 URL을 동시에 요구하는 과제라면 추가 EC2 또는
별도 Port/Domain 구성이 필요하지만, 구성과 재현 가능한 실행 증빙에는
영향이 없다.
