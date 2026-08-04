# EC2 Docker Compose deployment (B method)

재현 가능한 로컬 Compose 런타임 검증 결과는
[`VALIDATION_REPORT.md`](./VALIDATION_REPORT.md)에 기록한다.

This directory operates one Ubuntu 24.04 `linux/amd64` EC2 instance with three
containers. Only the frontend Nginx port is published. The backend and MySQL are
reachable only by service name on the Compose bridge network.

```text
host :80 -> frontend:80 -> backend:8080 -> mysql:3306
                         -> /data/community/uploads
                                      mysql -> /data/community/mysql
```

AWS Console and every EC2 command remain operator-owned. Run the scripts only
after reviewing them. Do not provide secret values, full `docker inspect`
output, database dumps, or private evidence when asking for help.

## EC2 filesystem layout

Place this directory at `/opt/community/deployment/ec2-compose`. Runtime data is
kept outside the checkout:

```text
/data/community/mysql
/data/community/uploads
/data/community/backup
/data/community/evidence
/data/community/releases
/etc/community/secrets
```

## One-time host preparation

In a Session Manager shell:

```bash
cd /opt/community/deployment/ec2-compose
sudo ./scripts/install-docker.sh
sudo ./scripts/prepare-directories.sh
```

Expected result: Docker and Compose versions are printed, the data directories
exist, and three empty secret files are reported. Fill the files with
`sudoedit` as described in `secrets/README.md`. Never pass a secret as a command
argument.

## Release inputs

Copy `compose.example.env` to `.env`, replace all angle-bracket placeholders,
and keep the file mode `0600`. It contains image tags and public configuration,
not secrets. Copy `manifests/release-manifest.example` to `release-manifest` and
record the actual commits and UTC deployment time.

Transfer the image tar files and `SHA256SUMS` to a private directory, then run:

```bash
sudo ARTIFACT_DIR=/opt/community/artifacts/<release-id> \
  ./scripts/load-images.sh
```

Expected result: every checksum reports `OK`, followed by Docker's loaded image
tags. A checksum failure is a stop condition.

## Deploy and verify

```bash
sudo ./scripts/deploy.sh
sudo ./scripts/verify.sh
sudo docker compose --env-file /data/community/releases/current.env \
  --file compose.yaml ps
sudo docker stats --no-stream
```

Deployment promotes the release state only after all three containers become
healthy. Verification checks architecture, health, non-root application users,
read-only root filesystems, host port exposure, H2 Console, secret metadata, and
upload permissions. It cannot verify Security Group, EBS encryption, IMDSv2,
reboot persistence, browser flows, or AWS cost settings.

If startup fails, use service-scoped logs without dumping environment or inspect
data:

```bash
sudo docker compose --env-file .env --file compose.yaml ps
sudo docker compose --env-file .env --file compose.yaml logs --tail=200 frontend
sudo docker compose --env-file .env --file compose.yaml logs --tail=200 backend
sudo docker compose --env-file .env --file compose.yaml logs --tail=200 mysql
```

Do not share logs until they have been reviewed for personal data.

## Backup and restore

Create an encrypted-at-rest private backup archive:

```bash
sudo ./scripts/backup.sh
```

The command prints the archive path under `/data/community/backup`. Copy backups
off the instance using an operator-approved encrypted channel. A backup that has
never passed a restore drill is not considered recoverable.

Restore is destructive and requires an explicit confirmation token:

```bash
sudo RESTORE_ARCHIVE=/data/community/backup/community-<timestamp>.tar.gz \
  RESTORE_CONFIRM=restore-community \
  ./scripts/restore.sh
sudo ./scripts/verify.sh
```

The previous upload directory is retained after a successful restore. Review it
before any later manual deletion.

## Rollback

Read `/data/community/releases/previous.manifest`, confirm the corresponding
images still exist locally, and run:

```bash
sudo ROLLBACK_CONFIRM=rollback-community ./scripts/rollback.sh
```

Rollback swaps `current.env` and `previous.env`, so the replaced release remains
available for a controlled roll-forward. Database migrations must remain
backward-compatible with the previous application image; an incompatible schema
requires a separately reviewed database restore.

## Stop and restart

Use the tracked release environment:

```bash
sudo docker compose --env-file /data/community/releases/current.env \
  --file compose.yaml restart
sudo docker compose --env-file /data/community/releases/current.env \
  --file compose.yaml down
sudo docker compose --env-file /data/community/releases/current.env \
  --file compose.yaml up --detach --wait
```

Bind-mounted MySQL and upload data survive `down` and container replacement.
Never use `docker compose down -v` as a routine command.
