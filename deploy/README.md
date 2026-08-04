# Single EC2 deployment

This deployment keeps H2 and uploaded images on the EC2 instance. It is
appropriate for a single-instance demo, not for horizontal scaling or an
availability-sensitive production service.

## Required instance setup

1. Install Java 21 and confirm that `java` is available at `/usr/bin/java`.
2. Create the service account and persistent directories:

   ```bash
   sudo useradd --system --home /opt/ian-community --shell /usr/sbin/nologin ian-community
   sudo install -d -o ian-community -g ian-community -m 0750 /opt/ian-community
   sudo install -d -o ian-community -g ian-community -m 0700 /var/lib/ian-community/data
   sudo install -d -o ian-community -g ian-community -m 0700 /var/lib/ian-community/storage/images
   sudo install -d -o root -g root -m 0700 /etc/ian-community
   ```

3. Build with `./gradlew bootJar` and copy the resulting JAR to
   `/opt/ian-community/backend.jar`.
4. Copy `deploy/env/backend.env.example` to
   `/etc/ian-community/backend.env`, replace the domain, and set a unique JWT
   signing key. Generate it without storing it in Git:

   ```bash
   openssl rand -base64 48
   ```

5. Protect the environment file:

   ```bash
   sudo chown root:root /etc/ian-community/backend.env
   sudo chmod 0600 /etc/ian-community/backend.env
   ```

6. Install and start the service:

   ```bash
   sudo cp deploy/systemd/ian-community-backend.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now ian-community-backend
   sudo systemctl status ian-community-backend
   ```

The production profile binds Spring Boot to `127.0.0.1:8080`. Only the local
reverse proxy should reach it; do not expose port 8080 or `/h2-console` through
the EC2 security group or Nginx.

## Persistence and backup

- H2 data: `/var/lib/ian-community/data`
- Uploaded images: `/var/lib/ian-community/storage/images`

Both paths must be on a persistent EBS volume. Stop the service before copying
the H2 database files for a consistent backup. Test restoration regularly.

## Start gate

Before opening public traffic, verify:

- `SPRING_PROFILES_ACTIVE=prod`
- HTTPS is active at the reverse proxy
- `FRONTEND_ORIGIN` exactly matches the public HTTPS origin
- `JWT_SECRET` is non-empty and unique to this environment
- ports 8080 and 22 are not open to the entire internet
- IMDSv2 is required on the EC2 instance
- an EBS snapshot or file backup and restore procedure exists
