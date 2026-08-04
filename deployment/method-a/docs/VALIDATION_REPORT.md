# Method A 검증 보고서

작성일: 2026-08-04

이 보고서는 로컬 검증 결과와 AWS/EC2에서 사용자가 확인한 결과를 구분한다.
운영 Secret과 개인 데이터는 증빙에 기록하지 않는다.

## Local

| Control | Status | Evidence |
|---|---|---|
| 기본 Profile | PASS | 별도 지정 없을 때 `local` 선택 |
| H2 Embedded | PASS | local은 H2 in-memory, Hibernate `ddl-auto=update`, Flyway 비활성 |
| H2 Migration | PASS | 일반 local 실행과 분리하고 Migration 통합 테스트에서만 H2 SQL 적용 |
| Local H2 Console | PASS | local에서만 활성 |
| Test H2 Console | PASS | 비활성 Profile에서 인증 없이 공개되지 않음 |
| Backend Test | PASS | 57 tests, failures/errors/skipped 0 |
| Backend JAR | PASS | `clean test bootJar` 성공 |
| Frontend Format | PASS | Prettier check 성공 |
| Frontend Unit | PASS | 125 tests |
| Frontend Integration | PASS | 19 tests |
| Frontend UI E2E | PASS | Playwright 105 tests |
| Frontend–Backend E2E | PASS | H2 Embedded Backend 연동 Playwright 12 tests |
| Frontend Production Build | PASS/WARN | 성공, 대용량 Asset 경고 3건 |
| Script Syntax | PASS | 모든 Shell Script `bash -n` 성공 |
| Deployment Function Test | PASS | Origin 형식·경로·포트 경계값 검사 성공 |
| JWT Secret Input | PASS | Base64 디코딩 성공과 디코딩 결과 최소 32바이트 검사 |
| ShellCheck | WARN | 로컬 명령 미설치로 미실행 |
| Source Map | PASS | Production Build에 `.map` 미생성 |
| Diff whitespace | PASS | Backend와 Frontend `git diff --check` 성공 |
| Git 상태 정리 | PASS submission scope | Method A/B 배포 파일만 선별하고 Frontend 시각 테스트 산출물은 제출 Commit에서 제외 |

## Security

| Control | Status | Evidence / Remediation |
|---|---|---|
| 운영 JWT Secret 환경변수화 | PASS in working tree | `JWT_SECRET` 필수 환경변수 |
| Current tree Secret | PASS | AWS Key·Private Key·추적 `.env`·Dump/H2 DB·Frontend Bundle Secret 지표 없음 |
| Git History Secret | PASS with user confirmation | 과거 literal 값은 Push·공유되지 않았고 운영에 사용되지 않았음을 2026-08-03 확인. 현재 트리에서 제거됐으며 향후 재사용 금지 |
| Environment file | PASS | EC2에서 root:community 640 확인, `verify.sh` 통과 |
| 운영 H2 Console | PASS | aws Profile에서 비활성, EC2 HTTP 검증 통과 |
| 8080/3306 Loopback | PASS | EC2에서 Spring Boot와 MySQL의 loopback 전용 Listener 확인 |
| Upload traversal/name | PASS local | 실체 경로 확인과 UUID 파일명 |
| Upload MIME/signature/size | PASS local | MIME+magic bytes, 10MiB actual stream 제한 |
| Upload permission | PASS | 업로드 디렉터리 community:community 750, world-writable 경로 없음 |
| MySQL runtime privilege | PASS | 상시 계정은 DML만 사용하고 배포 중 Flyway DDL 권한을 임시 Grant 후 Revoke |
| Backup/Dump Git exclusion | PASS working tree | ignore 규칙과 600 Backup Archive |
| Authorization/Cookie logging | PASS static | Header/Request body 로깅 코드 미발견 |
| Actuator exposure | PASS | `aws` Profile은 health만 포함하고 상세 정보를 숨기며, Host Nginx는 `/actuator`를 외부 Proxy하지 않음 |

현재 확인된 치명적 보안 `FAIL`은 없다. 과거 Git History의 literal 값은
운영 자격 증명으로 재사용하지 않으며, 원격 공유 전에 Current tree Secret
검사를 다시 수행한다.

## AWS / EC2

사용자가 제공한 2026-08-03~04 Console·Session Manager·외부 포트 검사 결과:

- PASS: 서울 리전 `ap-northeast-2`, Ubuntu Server 24.04 LTS x86_64
- PASS: `t3.small`, Root EBS gp3 20GiB 암호화, 종료 시 삭제
- PASS: IMDSv2 Required, Metadata hop limit 1
- PASS: `community-ec2-ssm-role`과 `AmazonSSMManagedInstanceCore`
- PASS: Session Manager 연결과 SSM Agent active
- PASS: Security Group Inbound는 공개 서비스용 HTTP 80·HTTPS 443만 `0.0.0.0/0`에 허용
- PASS: 임시 SSH 22 규칙 삭제, 8080·3306 외부 미공개
- PASS: MySQL 127.0.0.1:3306, Spring Boot loopback:8080, Nginx 외부 80·443
- PASS: MySQL·Spring Boot·Nginx 활성, EC2 재부팅 후 영속성 검증
- PASS: 로그인·피드·업로드·영속성 기능 검증
- PASS: 두 릴리스 간 Rollback과 최신 릴리스 복귀 검증
- PASS: Backup·Restore와 내부 SHA-256 검증
- PASS: 최종 Backup `community-20260803T053936Z.tar.gz`, root:root 600
- PASS: 임시 SSH 공개키·22번 규칙·전송 Artifact·복구 임시 업로드 제거

계정 수준의 Root MFA·Root Access Key 부재, IAM Console Identity MFA,
Budget 15 USD와 Actual Alert 12 USD는 Billing/IAM Console에서 별도로 유지·점검한다.

## 2026-08-04 HTTPS 작업 트리 추가 검증

| Control | Status | Evidence |
|---|---|---|
| 무료 고정 주소 방식 | PASS design | Dynu의 `pulse` 무료 Hostname을 선택하고 `sslip.io`+Elastic IP, DuckDNS, Cloudflare Quick Tunnel, ngrok 대안 비교 |
| Dynu 인증정보 보호 | PASS static | 별도 IP Update Password 원문은 저장하지 않고 SHA-256만 `/etc/community/dynu.env`에 `root:root 600`으로 설치 |
| Public IP 갱신 | PASS | Dynu API 갱신 성공, `community-dynu.timer` active, IMDSv2 Public IPv4와 DNS A Record 일치 |
| HTTP Nginx 문법 | PASS local | Docker Nginx 1.28.2로 기본 HTTP와 인증서 발급 전 Domain Template `nginx -t` 성공 |
| TLS Nginx·인증서 | PASS | `pulse.gleeze.com` 인증서 발급, 만료일 2026-11-02, TLS health 200과 인증서 신뢰 검증 성공 |
| HTTP → HTTPS | PASS | 외부 `http://pulse.gleeze.com/healthz`가 Canonical HTTPS 주소로 301 Redirect |
| 자동 갱신 | PASS | `certbot.timer` active, `certbot renew --dry-run --run-deploy-hooks` 성공 |
| Backend HTTPS 전환 | PASS | `FRONTEND_ORIGIN=https://pulse.gleeze.com`, `COOKIE_SECURE=true`, Backend 재시작 후 검증 성공 |
| 최종 EC2 검증 | PASS | `scripts/verify.sh`의 서비스·Listener·Domain·Timer·인증서·Redirect·DNS Control 전체 통과 |
| Script Syntax | PASS local | 추가된 모든 Shell Script 포함 `bash -n` 성공 |
| Deployment Function Test | PASS local | 기존 Origin·JWT·Loopback과 추가 Hostname·`pulse` Label·SHA-256·IPv4·Config 판정 성공 |
| Backend AWS 설정 Test | PASS local | `AwsDeploymentConfigurationTest` 성공 |
| ShellCheck | WARN | 로컬 명령 미설치로 미실행 |

검증된 공개 주소는 `https://pulse.gleeze.com/`이다. 외부 검사에서 HTTPS
health는 200, 인증서 검증 결과는 정상, HTTP health는 같은 HTTPS 주소로
301 Redirect됐다. 운영 IP Update Password와 Hash는 증빙에 기록하지 않는다.

## Cost

무료 플랜 적용 여부와 잔여 Credit은 계정별로 다르다. 서울 리전
`t3.small`, Public IPv4, gp3 비용을 Billing과 Cost Explorer에서 확인하고,
Budget Actual Alert 12 USD와 월 최대 15 USD를 넘기기 전에 인스턴스를
중지한다. 중지 중에도 EBS와 Public IPv4 관련 비용은 별도로 확인한다.
