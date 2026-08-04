# 무료 고정 도메인과 HTTPS 적용

작성 기준일: 2026-08-04

## 결론

이 배포에는 `Dynu 무료 DDNS + pulse Hostname + Let's Encrypt + Nginx`를
적용한다. Dynu Control Panel에서 Host는 `pulse`로 입력하고, 사용 가능한
Top Level을 선택한다. 최종 주소는 다음 형식이다.

```text
https://pulse.<Dynu에서 선택한 Top Level>/
```

Top Level은 Dynu 생성 화면에서 실제 선택한 값을 사용해야 하며 저장소에서
`pulse.dynu.com`처럼 임의로 확정하지 않는다. EC2 Stop/Start로 Public IPv4가
바뀌어도 systemd Timer가 Dynu 레코드를 갱신하므로 전체 Hostname은 유지된다.

무료 Hostname은 직접 소유한 도메인 자산이 아니며 Dynu의 가용성과 정책에
의존한다. 과제·포트폴리오·테스트 서비스에는 적합하지만 SLA와 브랜드
소유권이 필요한 상용 서비스에서는 직접 소유한 도메인으로 교체해야 한다.

## 선택 근거와 대안

| 방식 | 도메인 구매 | 주소 고정 | 현재 구조 적합성 | 판단 |
|---|---:|---:|---|---|
| Dynu 무료 DDNS | 불필요 | 예 | `pulse` 이름 선택, Public IP 변경 자동 추적 | 선택 |
| DuckDNS 무료 DDNS | 불필요 | 예 | 기술적으로 적합하지만 선택한 공급자가 아님 | 제외 |
| `sslip.io` + 현재 Public IP | 불필요 | 아니요 | IP가 바뀌면 Hostname도 바뀜 | 제외 |
| `sslip.io` + Elastic IP | 불필요 | 예 | Stop 중에도 Elastic IP 비용 발생 | 비용상 제외 |
| Cloudflare Quick Tunnel | 불필요 | 아니요 | 임시 URL은 무작위, 고정 Hostname은 소유 Domain 필요 | 제외 |
| ngrok 무료 Dev Domain | 불필요 | 예 | Traffic 한도와 Browser 경고 화면이 있음 | 제외 |

Dynu는 무료 3단계 Domain과 별도의 IP Update Password를 제공한다. 공식 IP
Update Protocol은 Password 원문 대신 MD5 또는 SHA-256 Hash도 허용한다.
이 구현은 계정 비밀번호가 아닌 별도 IP Update Password를 입력받고 원문을
즉시 폐기한 뒤 SHA-256만 EC2에 저장한다.

## Dynu 사전 설정

1. <https://www.dynu.com/>에 가입하고 Email 인증을 완료한다.
2. `DDNS Services > Add`에서 `Use Our Domain Name`을 선택한다.
3. Host에 `pulse`를 입력한다.
4. 사용 가능한 Top Level을 선택하고 생성한다.
5. 생성된 전체 Hostname을 정확히 기록한다.
6. `My Account > Change Username/Password`에서 계정 Password와 다른 16자
   이상의 별도 IP Update Password를 만든다.
7. IP Update Password를 채팅·Git·문서에 기록하지 않는다.
8. EC2 Security Group에서 TCP 80·443을 `0.0.0.0/0`에 허용한다.
9. 22, 8080, 3306은 공개하지 않는다.

Dynu 무료 Hostname은 계정당 최대 수와 기능 제한이 있을 수 있다. 현재 공식
FAQ 기준 무료 Hostname은 최대 4개다.

## 구현된 동작

`08-configure-free-domain-https.sh`가 다음을 수행한다.

1. `pulse`로 시작하는 Dynu 전체 Hostname과 Let's Encrypt Email을 입력받는다.
2. 별도 IP Update Password를 두 번 숨김 입력받아 SHA-256으로 변환한다.
3. Hostname과 Hash를 `/etc/community/dynu.env`에 `root:root 600`으로 저장한다.
4. IMDSv2로 현재 EC2 Public IPv4를 읽어 Dynu HTTPS API로 갱신한다.
5. `community-dynu.timer`를 켜 Boot 후와 10분마다 레코드를 갱신한다.
6. HTTP-01 Webroot 방식으로 Let's Encrypt 인증서를 발급한다.
7. Nginx 80을 Canonical HTTPS로 Redirect하고 443에 TLS 1.2/1.3을 적용한다.
8. Backend `FRONTEND_ORIGIN`을 HTTPS로 바꾸고 Secure Cookie를 켠다.
9. Certbot 자동 갱신 Timer, Nginx Reload Hook과 Renewal Dry-run을 적용한다.

Dynu API 인증값은 Curl 명령행에 넣지 않고 표준 입력의 Curl Config로
전달한다. 로그에는 Hostname과 성공 여부만 남기며 Password Hash를 출력하지
않는다. Frontend는 운영 Host에서 Same-Origin `/api`와 `/uploads`를 사용하므로
별도 코드 변경이 필요 없다.

## EC2 실행

최신 배포 Bundle을 `/opt/community/deployment/method-a`에 설치한다. 기존
EC2를 갱신하는 경우 `01`을 다시 실행해 Certbot을 먼저 설치한다.

```bash
cd /opt/community/deployment/method-a
sudo scripts/01-install-packages.sh
sudo scripts/08-configure-free-domain-https.sh
sudo scripts/verify.sh
```

입력값:

- Dynu full hostname: `pulse.<실제 선택한 Top Level>`
- Let's Encrypt Email: 만료·보안 알림을 받을 주소
- Dynu separate IP update password: 계정 Password와 다른 값

성공 후 외부 Browser에서 확인한다.

```text
http://pulse.<top-level>/  -> 301 HTTPS Redirect
https://pulse.<top-level>/ -> 유효한 인증서와 애플리케이션
```

재부팅과 EC2 Stop/Start 후에는 DNS Cache 때문에 잠시 이전 IP가 남을 수 있다.

```bash
sudo systemctl status community-dynu.timer --no-pager
sudo systemctl status certbot.timer --no-pager
sudo scripts/verify.sh
```

## 공식 참고

- Dynu 무료 DDNS와 별도 IP Update Password: <https://www.dynu.com/en-US/dynamicdns>
- Dynu 무료 Hostname 생성: <https://www.dynu.com/Resources/Tutorials/DynamicDNS/GettingStarted>
- Dynu HTTPS IP Update Protocol과 응답 코드: <https://www.dynu.com/DynamicDNS/IP-Update-Protocol>
- Dynu 무료 Hostname·DNS 제한 FAQ: <https://www.dynu.com/en-US/FAQ/Dynamic-DNS-Service>
- Certbot Webroot와 자동 갱신: <https://eff-certbot.readthedocs.io/en/stable/using.html#webroot>
- EC2 Public IPv4 변경 조건: <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-instance-addressing.html>
- Elastic IP 특성과 요금: <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/elastic-ip-addresses-eip.html>
