# B-method build and deployment workflow

## Local baseline

The backend requires Java 21 and Gradle Wrapper 9.5.1. The `aws` profile keeps
the A-method default listener at `127.0.0.1`; Compose explicitly overrides it to
`0.0.0.0` only inside the private container network. Local development continues
to use the unchanged H2 profile.

Run the source checks before building images:

```bash
./gradlew clean test bootJar
```

The deterministic boot artifact is `build/libs/community.jar`.

## Build linux/amd64 images

From the backend repository:

```bash
IMAGE_TAG=community-backend:<backend-commit> \
  ./deployment/ec2-compose/scripts/build-backend-image.sh
```

From the frontend repository:

```bash
IMAGE_TAG=community-frontend:<frontend-commit> \
  ./scripts/build-image.sh
IMAGE_TAG=community-frontend:<frontend-commit> \
  ./scripts/verify-image.sh
```

Create a local release environment from `compose.example.env`, using those exact
tags. Package the frontend, backend, and fixed MySQL image for offline transfer:

```bash
RELEASE_ENV=/absolute/path/to/release.env \
OUTPUT_DIR=/absolute/path/to/private/artifacts \
  ./deployment/ec2-compose/scripts/package-images.sh
```

The output consists of three tar files plus `SHA256SUMS`. The archives can
contain proprietary application code and must stay out of Git.

## Local Compose integration test

Use temporary, non-production secret files with restricted permissions and
override the bind address and data root in a private release env:

```text
HTTP_BIND_ADDRESS=127.0.0.1
HTTP_PORT=8088
DATA_ROOT=<private-absolute-test-directory>
SECRETS_DIR=<private-absolute-secret-directory>
FRONTEND_ORIGIN=http://127.0.0.1:8088
COOKIE_SECURE=false
```

Then validate and start:

```bash
docker compose --env-file <release-env> \
  --file deployment/ec2-compose/compose.yaml config --quiet
docker compose --env-file <release-env> \
  --file deployment/ec2-compose/compose.yaml up --detach --wait
```

Verify SPA refresh, authentication, posts, comments, bookmarks, image upload,
logout, invalid inputs, Korean/emoji content, browser console/network errors,
restart, `down`/`up`, and data survival. Confirm `docker compose port backend
8080` and `docker compose port mysql 3306` return no mapping. Do not use `down
-v`.

## Initial resource envelope

The Compose file starts with 128 MiB for Nginx, 900 MiB for the backend, and 640
MiB for MySQL. The backend heap is 256–640 MiB and the MySQL buffer pool is 320
MiB. These are starting constraints, not performance conclusions. On EC2 review:

```bash
free -h
docker stats --no-stream
docker inspect --format '{{.RestartCount}}' <container-id>
journalctl -k --grep='Out of memory\|Killed process'
```

Adjust only after recording host free memory, restart counts, OOM events, JVM
heap, MySQL memory, and P95 response time.
