# Method A: EC2 직접 설치 배포

Ubuntu Server 24.04 LTS x86_64 한 대에 Java 21, Nginx, MySQL,
Spring Boot를 직접 설치하는 수동 배포 자료다. RDS, S3, Elastic IP,
ALB, NAT Gateway 및 B 방식 파일을 사용하지 않는다. A 방식 과제 검증은 EC2
Public IPv4의 HTTP 80에서 완료했으며 Dynu와 HTTPS를 사용하지 않았다.

이 디렉터리에는 이후 B 방식 작업 단계에서 공통 Host 진입점으로 처음 적용한
Dynu·Let's Encrypt 운영 Script도 함께 보관한다. 따라서 Script의 저장 위치와
A 방식 과제 검증 범위를 구분해야 한다. 최종 제출 주소는 이 후속 Host 설정을
사용한 <https://pulse.gleeze.com/>이다.

A 방식만 독립적으로 정리한 기술 블로그 문서는
[`../DEPLOYMENT_METHOD_A_TECH_BLOG.md`](../DEPLOYMENT_METHOD_A_TECH_BLOG.md)를
참고합니다. A/B 방식을 연결한 통합 회고는
[`../DEPLOYMENT_TECH_BLOG.md`](../DEPLOYMENT_TECH_BLOG.md)에 기록합니다.
외부 글과 공식 문서에서 참고한 구체적인 지점은
[`../DEPLOYMENT_TECH_BLOG_SOURCES.md`](../DEPLOYMENT_TECH_BLOG_SOURCES.md)에
분리해 기록합니다.

## 구조

```text
Browser -> EC2 Public IPv4 :80 -> Host Nginx
                                  |- static frontend
                                  `- /api, /uploads -> 127.0.0.1:8080
                                                           `- MySQL 127.0.0.1:3306
```

위 구조가 A 방식 검증 범위다. B 작업 단계에서 추가한 최종 Host 진입점은
`Dynu Hostname -> Nginx :80 Redirect / :443 TLS`이며, 애플리케이션 연결은
같은 Host Nginx 설정을 재사용한다.

## 사용자 소유 작업

- AWS Console의 모든 작업
- Session Manager 접속과 EC2 내부 명령
- Secret 입력과 Artifact 전송
- 서비스 시작·재시작·롤백·백업·복구
- 비용·보안·기능 검증

Codex는 AWS나 EC2에 접속하지 않는다.

## 파일

- `scripts/01-install-packages.sh`: OS 패키지
- `scripts/02-configure-user-and-directories.sh`: 최소 권한 계정·경로
- `scripts/03-configure-mysql.sh`: Loopback MySQL과 앱 계정
- `scripts/mysql-migration-access.sh`: 배포 중에만 Flyway DDL 권한
- `scripts/configure-backend-env.sh`: Secret 비노출 환경 파일 생성
- `scripts/install-operations.sh`: 재부팅 후에도 사용할 운영 도구 영구 설치
- `scripts/04-deploy-backend.sh`: 버전 JAR과 symlink
- `scripts/05-deploy-frontend.sh`: 정적 파일 버전과 symlink
- `scripts/06-configure-systemd.sh`: hardened service
- `scripts/07-configure-nginx.sh`: SPA 및 reverse proxy
- `scripts/08-configure-free-domain-https.sh`: B 작업 단계에서 추가한 공통 Host
  Dynu·TLS·Secure Cookie 전환
- `scripts/update-dynu.sh`: EC2 Public IPv4 변경을 Dynu에 반영
- `scripts/deploy.sh`: 배포 적용
- `scripts/rollback.sh`: 이전 Backend/Frontend 동시 롤백
- `scripts/backup.sh`, `restore.sh`: DB·업로드 Backup/복구
- `scripts/verify.sh`: 서비스·포트·H2·권한 검사
- `docs/AWS_CONSOLE_GUIDE.md`: Console 수동 절차
- `docs/EC2_MANUAL_GUIDE.md`: EC2 명령·예상 결과·실패 확인
- `docs/DB_COMPATIBILITY.md`: H2/MySQL 위험
- `docs/IMPLEMENTATION_PROCESS.md`: 구현·배포 과정, 코드 변경과 운영 환경
- `docs/FREE_DOMAIN_HTTPS.md`: 무료 도메인 대안 조사와 선택 근거

## 실행 전

각 스크립트는 `set -Eeuo pipefail`을 사용하고 `set -x`를 사용하지
않는다. 실제 Secret은 예제·Git·로그·Evidence에 기록하지 않는다.

먼저 `docs/AWS_CONSOLE_GUIDE.md`, 이후 `docs/EC2_MANUAL_GUIDE.md`
순서로 사용자가 직접 수행한다.
