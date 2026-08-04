# EC2 수동 설치·배포 가이드

모든 명령은 사용자가 AWS Console의 Session Manager에서 직접 실행한다.
명령 출력에 Secret을 포함하지 말고 `env`, `printenv`, 실제 환경 파일,
SQL Dump를 공유하지 않는다.

## 1. 로컬 Artifact 준비

백엔드 저장소에서 사용자가 실행:

```bash
./gradlew clean test bootJar
mkdir -p /tmp/community-artifacts
cp build/libs/community-0.0.1-SNAPSHOT.jar \
  /tmp/community-artifacts/community-backend.jar
tar -czf /tmp/community-method-a.tar.gz \
  -C deployment method-a
```

프론트엔드 저장소에서 사용자가 실행:

```bash
npm run format:check
npm run test:unit
npm run test:integration
npm run build:react
tar -czf /tmp/community-artifacts/community-frontend.tar.gz \
  index.html dist
```

예상 결과:

- `/tmp/community-artifacts/community-backend.jar`
- `/tmp/community-artifacts/community-frontend.tar.gz`
- `/tmp/community-method-a.tar.gz`

## 2. Artifact 전송

S3는 사용하지 않는다. Session Manager에는 일반적인 브라우저 파일
업로드 기능이 없으므로, 별도의 승인된 전송 경로가 없다면 임시 SCP를
사용한다.

사용자가 직접 수행:

1. EC2 상세 정보에서 Key Pair 이름을 확인
2. 해당 Private Key를 실제로 보유한 경우에만 다음 단계 진행
3. Key Pair가 없거나 Private Key를 보유하지 않았다면 22를 열지 말고 중단
4. Security Group의 22를 현재 공인 IP `/32`에만 임시 허용
5. 전용 Private Key 권한을 로컬에서 `600`으로 제한
6. 아래 Placeholder 명령을 사용자 로컬 터미널에서 실행

```bash
scp -i <private-key-path> \
  /tmp/community-method-a.tar.gz \
  /tmp/community-artifacts/community-backend.jar \
  /tmp/community-artifacts/community-frontend.tar.gz \
  ubuntu@<public-ip>:/tmp/
```

7. 전송 직후 Security Group의 22 규칙 삭제
8. 이후 작업은 Session Manager만 사용

Private Key나 실제 Public IP를 Codex에 전달하지 않는다.

## 3. EC2 Preflight

Session Manager에서 사용자가 실행:

```bash
uname -a
uname -m
df -h /
free -h
sudo ss -lntup
```

예상 결과:

- Architecture: `x86_64`
- Root disk: 약 20GiB
- 초기 8080·3306 외부 listener 없음

실패 확인:

- `aarch64`이면 잘못된 AMI이므로 중단
- 디스크가 20GiB보다 작거나 암호화 여부가 Console에서 불명확하면 중단

## 4. 배포 Bundle 준비

```bash
sudo install -d -o root -g root -m 0755 /tmp/community-setup
sudo install -d -o root -g root -m 0700 /tmp/community-artifacts
sudo tar -xzf /tmp/community-method-a.tar.gz \
  -C /tmp/community-setup
sudo install -o root -g root -m 0600 \
  /tmp/community-backend.jar \
  /tmp/community-artifacts/community-backend.jar
sudo install -o root -g root -m 0600 \
  /tmp/community-frontend.tar.gz \
  /tmp/community-artifacts/community-frontend.tar.gz
cd /tmp/community-setup/method-a
```

## 5. 패키지·계정·MySQL

```bash
sudo scripts/01-install-packages.sh
sudo scripts/02-configure-user-and-directories.sh
sudo scripts/03-configure-mysql.sh
sudo scripts/install-operations.sh
cd /opt/community/deployment/method-a
```

`03`은 MySQL 애플리케이션 비밀번호를 숨김 입력으로 한 번 요청한다.
값을 명령행 인자나 채팅에 쓰지 않는다.

목적:

- Java 21 JDK Headless, Nginx, MySQL 설치
- `community` 비로그인 서비스 계정 생성
- MySQL을 `127.0.0.1:3306`에만 바인딩
- `community` DB와 최소 권한 `community_app` 사용자 생성

실패 확인:

```bash
sudo systemctl status mysql --no-pager
sudo ss -ltnp | grep 3306
```

`0.0.0.0:3306` 또는 `[::]:3306`이면 즉시 중단한다.

## 6. 운영 환경 파일

```bash
sudo scripts/configure-backend-env.sh
sudo stat -c '%U %G %a %n' /etc/community/backend.env
```

MySQL 단계와 같은 DB 비밀번호, Base64로 디코딩했을 때 32바이트 이상인
별도의 JWT Secret, Frontend Origin을 숨김 입력한다. Origin에는 Scheme,
Host와 선택적 Port만 입력하고 마지막 `/`, Path, Query, Fragment를 붙이지
않는다. 실제 값은 출력하지 않는다.

예상 권한:

```text
root community 640 /etc/community/backend.env
```

HTTP IP로 임시 검증하면 Secure Cookie가 비활성화된다는 경고가 나온다.
도메인과 TLS 적용 후 다시 실행해 HTTPS Origin과 Secure Cookie를
활성화한다.

## 7. 배포

```bash
sudo scripts/deploy.sh
sudo scripts/verify.sh
```

예상 결과:

- MySQL, community-backend, Nginx active
- Spring Boot `127.0.0.1:8080`
- MySQL `127.0.0.1:3306`
- Nginx health 200
- 운영 H2 Console 접근 불가
- 환경변수는 값이 아닌 `SET` 여부만 표시

실패 확인:

```bash
sudo systemctl status community-backend --no-pager
sudo journalctl -u community-backend -n 100 --no-pager
sudo nginx -t
```

로그를 전달할 때 Authorization, Cookie, Token, 사용자 데이터, DB URL의
사용자명을 마스킹한다.

## 8. 기능 검증

공개 기능 검증 전 무료 고정 도메인과 HTTPS를 적용한다. 먼저 Dynu에서
Host를 `pulse`로 입력하고 사용할 Top Level을 선택해 무료 Hostname을 만든다.
생성된 전체 주소(`pulse.<선택한-suffix>`)를 확인하고, 계정 비밀번호와 다른
별도 IP Update Password를 `My Account > Change Username/Password`에서
설정한다. 이후 Security Group의 80·443을
`0.0.0.0/0`에 허용한 뒤:

```bash
cd /opt/community/deployment/method-a
sudo scripts/01-install-packages.sh
sudo scripts/08-configure-free-domain-https.sh
sudo scripts/verify.sh
```

Dynu 전체 Hostname, Let's Encrypt 알림 Email, Dynu IP Update Password를
차례로 입력한다. Password는 숨김 입력 후 즉시 SHA-256 처리되며 원문은
저장하지 않는다. Hash는 `/etc/community/dynu.env`에 `root:root 600`으로만
저장된다.
세부 선택 근거와 실패 확인은 `docs/FREE_DOMAIN_HTTPS.md`를 따른다.

브라우저에서 사용자가 직접 확인:

1. 회원가입·로그인·로그아웃
2. 게시글·댓글·좋아요·북마크
3. PNG/JPEG/WebP 업로드
4. 잘못된 확장자·MIME와 10MiB 초과 거부
5. 재시작 후 데이터와 업로드 유지

EC2에서 사용자가 실행:

```bash
sudo systemctl restart mysql community-backend nginx
sudo scripts/verify.sh
```

## 9. 롤백

이전 Backend와 Frontend Release가 모두 있을 때:

```bash
sudo scripts/rollback.sh
sudo scripts/verify.sh
```

## 10. 백업

```bash
sudo scripts/backup.sh
```

Backup/복구 관리 작업은 Ubuntu MySQL의 로컬 root socket 인증을 사용하며
애플리케이션은 MySQL root를 사용하지 않는다. Backup은 개인정보를
포함할 수 있으며 Git·Evidence에 첨부하지 않는다.

## 11. 복구

복구 전 별도 최신 Backup 존재 여부를 확인한다. 사용자가 정확한 파일을
선택한 뒤 실행:

```bash
sudo env \
  RESTORE_ARCHIVE=/var/lib/community/backup/<approved-archive>.tar.gz \
  RESTORE_CONFIRM=restore-community \
  scripts/restore.sh
sudo scripts/verify.sh
```

`RESTORE_ARCHIVE`는 Secret이 아니지만 승인된 Backup 경로여야 한다.
복구 후 회원·게시글·업로드 정합성을 브라우저에서 확인한다.

## 12. 재부팅

```bash
sudo reboot
```

Session Manager 재연결 후:

```bash
cd /opt/community/deployment/method-a
sudo scripts/verify.sh
```

재부팅으로 Public IP가 바뀌지 않는 경우가 많지만 Stop/Start 시에는
바뀔 수 있다. Dynu Timer가 같은 `pulse` Hostname에 새 IP를 반영할 때까지
기다린 뒤 `verify.sh`를 다시 실행한다.
