# Method A: EC2 직접 설치 배포

Ubuntu Server 24.04 LTS x86_64 한 대에 Java 21, Nginx, MySQL,
Spring Boot를 직접 설치하는 수동 배포 자료다. RDS, S3, Elastic IP,
ALB, NAT Gateway 및 B 방식 파일을 사용하지 않는다. 공개 운영 주소는
Dynu의 `pulse` 무료 고정 호스트와 Let's Encrypt HTTPS를 사용한다.

2026-08-04 검증된 제출 배포 주소: <https://pulse.gleeze.com/>

## 구조

```text
Internet -> Dynu pulse host -> Nginx :80 redirect / :443 TLS
             |- static frontend
             `- /api, /uploads -> 127.0.0.1:8080
                                      `- MySQL 127.0.0.1:3306
```

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
- `scripts/08-configure-free-domain-https.sh`: Dynu·TLS·Secure Cookie 전환
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
