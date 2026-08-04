# AWS Console 수동 준비 가이드

이 문서는 사용자가 AWS Management Console에서 직접 수행한다. Codex는
AWS 계정이나 EC2에 접근하지 않는다. 리전은 모든 화면에서
`Asia Pacific (Seoul) / ap-northeast-2`인지 확인한다.

## [AWS Console 작업 필요]

현재 단계: 비용 경보와 EC2 보안 기반 준비

목적: 월 최대 비용과 최소 권한을 먼저 고정한다.

Console 경로:

1. `Billing and Cost Management > Budgets > Create budget`
2. `IAM > Roles > Create role`
3. `EC2 > Security Groups > Create security group`
4. `EC2 > Instances > Launch instances`

입력값:

### 1. Budget

- Budget setup: `Customize (advanced)`
- Budget type: `Cost budget`
- Period: `Monthly`
- Renewal: `Recurring`
- Budgeting method: `Fixed`
- Budget amount: `15 USD`
- Alert: `12 USD`, `Absolute value`, `Actual`
- 추가 권장 Alert: `12 USD`, `Forecasted`
- Email recipient: 사용자가 직접 입력하고 확인
- Budget action: 생성하지 않음

Budget는 강제 지출 제한이 아니며 결제 데이터 반영이 지연될 수 있다.

### 2. EC2 IAM Role

- Trusted entity: `AWS service`
- Use case: `EC2`
- Permission policy: `AmazonSSMManagedInstanceCore` 하나만
- Role name: `community-ec2-ssm-role`

`AdministratorAccess`, `PowerUserAccess`, S3·RDS·Secrets Manager 권한은
추가하지 않는다.

### 3. Security Group

- Name: `community-method-a-sg`
- VPC: 인스턴스를 생성할 VPC
- 초기 Inbound rule: 없음
- Outbound: 초기 설치와 SSM 통신이 가능한 기존 기본 Outbound 유지

배포 검증 직전 다음 규칙만 검토한다.

- HTTP 80: Dynu·Let's Encrypt 적용 시 `0.0.0.0/0`. HTTP-01 인증과
  HTTPS Redirect에 계속 필요
- HTTPS 443: 공개 HTTPS 적용 시 `0.0.0.0/0`
- SSH 22: 기본적으로 없음
- 8080, 3306: 절대 추가하지 않음
- IPv6를 사용하지 않으면 `::/0` 규칙을 추가하지 않음

Artifact 전송 때문에 SSH가 불가피하면 22를 현재 공인 IP `/32`에만
잠시 열고 전송 직후 삭제한다. `0.0.0.0/0`은 사용하지 않는다.

### 4. EC2

- Name: `community-method-a`
- AMI: `Ubuntu Server 24.04 LTS`, `64-bit (x86)`
- Instance type: `t3a.small`
- Key pair: Session Manager만 사용하면 없이 진행할 수 있다. 임시 SCP가
  필요하면 전용 Key Pair를 생성하고 Private Key는 로컬에서만 보호한다.
- Network: Public subnet, Auto-assign public IP 활성화
- Security Group: `community-method-a-sg`
- Storage: Root `20 GiB`, `gp3`, Encrypted 활성화
- IAM instance profile: `community-ec2-ssm-role`
- User data: 비워 둠
- Termination protection: 활성화
- Shutdown behavior: `Stop`
- Credit specification: `Standard`로 설정해 잉여 CPU Credit 비용 방지
- Metadata accessible: `Enabled`
- Metadata version: `V2 only (token required)`
- Metadata response hop limit: `1`
- Metadata tags: 비활성화

Tags:

- `Name=community-method-a`
- `Project=community`
- `Environment=test`
- `Method=direct-install`

보안 주의사항:

- Root 계정은 사용하지 않고 Console Identity에 MFA를 적용한다.
- User Data, Tag, Name에 Secret이나 개인정보를 입력하지 않는다.
- Key, 실제 환경 파일, DB/JWT Secret을 캡처하거나 공유하지 않는다.
- Public IPv4는 고정되지 않으며 Stop/Start 후 변경될 수 있다.
- Dynu 적용 후에는 systemd Timer가 바뀐 Public IPv4를 같은 `pulse` Host에
  반영한다. Dynu IP Update Password나 Hash는 Tag·User Data에 넣지 않는다.
- 22·8080·3306의 외부 공개는 완료 차단 `FAIL`이다.

비용 영향:

- `t3a.small`을 서울 리전에서 24시간 계속 실행하면 15달러 목표를
  초과할 가능성이 높다. Public IPv4와 gp3 비용도 별도다.
- Launch 화면 예상 비용을 확인하고 월 15달러 초과 예상이면 생성하지
  말고 결과를 전달한다.
- 비용 상한을 지키려면 사용하지 않을 때 인스턴스를 수동 Stop한다.
  Stop 중에도 EBS 비용은 계속 발생한다.
- Budget 경보는 즉시 차단 장치가 아니다.

완료 확인:

- Budget 15 USD와 12 USD Actual Alert가 보임
- Role에는 `AmazonSSMManagedInstanceCore`만 연결됨
- Root EBS에 `Encrypted: Yes`
- Metadata options에 `IMDSv2: Required`, Hop limit `1`
- Security Group에 22·8080·3306 규칙 없음
- `EC2 > Instances > Connect > Session Manager` 탭 연결 가능

완료 후 전달할 정보:

- Secret을 제외한 PASS/WARN 결과
- Budget/Role/EBS/IMDS/Security Group 항목별 상태
- Instance ID와 Public IP는 필요하면 마스킹
- 예상 월 비용
- Session Manager 연결 성공 여부

## 비용 상충 시 중단 기준

고정 사양 `t3a.small`, gp3 20GiB, Public IPv4의 Console 예상치가 월
15달러를 넘으면 비용 `WARN`이 아니라 요구사항 충돌이다. 인스턴스를
계속 실행하지 말고, 월 예상 실행 시간 또는 사양 변경 승인을 먼저
결정한다.

## 공식 참고

- <https://docs.aws.amazon.com/cost-management/latest/userguide/create-cost-budget.html>
- <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html>
- <https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html>
- <https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-getting-started-instance-profile.html>
