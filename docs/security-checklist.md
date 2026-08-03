# B-method security checklist

Record each control as PASS, WARN, or FAIL for every release.

## Image and secret controls

- Dockerfiles contain no credentials or secret build arguments.
- Frontend `dist` contains no credentials, server secrets, or source maps.
- Application images use commit-addressed tags; `latest` is rejected.
- All loaded images report `linux/amd64`.
- Backend and frontend runtime users are non-root.
- `/etc/community/secrets/*` is `root:community-secrets 0640` and non-empty.
- Compose environment and release manifest contain no secret values.
- Dumps, archives, `.env`, private evidence, and keys are not Git-tracked.

## Runtime controls

- Only frontend port 80 is published; 8080 and 3306 have no host mapping.
- No service is privileged, uses host networking, or mounts the Docker socket.
- `no-new-privileges` and capability drops are applied.
- Frontend and backend root filesystems are read-only.
- Backend H2 Console is disabled and health details are hidden.
- MySQL application traffic uses the application user, not root.
- Upload and MySQL bind mounts are not world-writable; no `777` paths exist.
- JSON logs rotate and access logs do not include Authorization, Cookie, JWT, or
  request bodies.

Any credential in an image/configuration, privileged container, Docker socket
mount, host-published 8080/3306, application root DB usage, public H2 Console,
world-writable upload path, Git-tracked dump, or frontend-bundled secret is an
immediate FAIL and blocks deployment.

## AWS evidence controls

- Region is `ap-northeast-2`; instance type is `t3a.small` on Ubuntu 24.04 amd64.
- Root EBS is encrypted gp3 20 GiB.
- IMDSv2 is required.
- Session Manager works through an instance role; inbound SSH/22 is absent.
- Security Group exposes only the chosen public HTTP/HTTPS ports.
- No RDS, S3, Elastic IP, ALB, or NAT Gateway was added for this design.
- A $12 monthly AWS Budget is active; expected spend is reviewed against the $15
  ceiling.
- EC2 reboot, container recreation, backup/restore, and rollback evidence exists.
