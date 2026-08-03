# AWS Console guide for the B method

Codex does not perform these actions. The AWS account owner must review costs and
security, click every Console control, run every Session Manager command, and
report only masked, non-secret results.

## Budget

[AWS Console work required]

Current step: Create a monthly cost budget before compute resources.

Purpose: Alert before the target monthly spend is exceeded.

Console path: Billing and Cost Management → Budgets → Create budget.

Input values: Cost budget, monthly recurring, USD 12. Add account-owner email
alerts at suitable thresholds such as 80% and 100%. The hard project ceiling is
USD 15; a Budget alerts but does not stop resources.

Cost impact: Creating the basic budget does not replace manual cost review.

Completion check: Budget status is active and the notification recipient is
verified.

## Session Manager instance role

[AWS Console work required]

Current step: Create or select an EC2 instance profile for Session Manager.

Purpose: Operate the instance without opening inbound SSH.

Console path: IAM → Roles → Create role → AWS service → EC2.

Input values: Attach `AmazonSSMManagedInstanceCore`; use a project-specific role
name. Do not attach administrator or broad application-data permissions.

Security note: No AWS access key is stored on the instance or in this repository.

Completion check: The role's trusted service is EC2 and only the reviewed
policies are attached.

## EC2 instance

[AWS Console work required]

Current step: Launch the single Compose host.

Purpose: Run frontend, backend, and MySQL containers on one low-cost host.

Console path: EC2 → Instances → Launch instances.

Input values:

- Region: Seoul (`ap-northeast-2`)
- AMI: Ubuntu Server 24.04 LTS, x86_64
- Instance type: `t3a.small`
- Key pair: proceed without one when Session Manager is the approved access path
- Instance profile: the reviewed Session Manager role
- Root volume: gp3, 20 GiB, encryption enabled, delete on termination only after
  backup/retention implications are accepted
- Metadata options: IMDSv2 required; response hop limit 1 unless a reviewed
  container use case requires otherwise
- Detailed monitoring: leave disabled unless its additional cost is accepted

Cost impact: EC2 compute and EBS are the main recurring charges. Public IPv4 may
also be billed even without an Elastic IP. Stopped instances retain EBS charges.

Completion check: Instance state is running, architecture is x86_64, the root
volume is encrypted, and Session Manager reports the node online.

## Security Group

[AWS Console work required]

Current step: Restrict public ingress to Nginx.

Purpose: Keep Spring Boot and MySQL private to the Compose network.

Console path: EC2 → Security Groups → select the instance group → Edit inbound
rules.

Input values: Allow TCP 80 from the intended audience for initial HTTP operation.
If HTTPS is separately configured with a domain and certificate, allow TCP 443.
Do not add inbound 22, 8080, or 3306. Restrict outbound rules further only after
confirming SSM, package, image, DNS, and time-sync dependencies.

Completion check: The effective rules have no inbound 22/8080/3306 and match the
chosen HTTP/HTTPS exposure.

## Session Manager handoff

Console path: EC2 → Instances → select instance → Connect → Session Manager →
Connect.

Run the preflight before installation:

```bash
uname -a
dpkg --print-architecture
lsb_release -a
free -h
df -h /
```

Proceed only when Ubuntu is 24.04, architecture is `amd64`, memory is about 2
GiB, and the root filesystem has sufficient free space. Then follow
`deployment/ec2-compose/README.md`. Share only versions, status, masked image
tags, HTTP status codes, and PASS/WARN/FAIL results—never secret values or full
inspection output.
