#!/usr/bin/env bash
set -Eeuo pipefail

: "${COMMUNITY_JAR:?Set COMMUNITY_JAR to the verified Backend jar path}"
: "${DB_URL:?Set DB_URL to the target database JDBC URL}"
: "${DB_USERNAME:?Set DB_USERNAME}"
: "${DB_PASSWORD:?Set DB_PASSWORD through the approved secret source}"
: "${JWT_SECRET:?Set JWT_SECRET through the approved secret source}"
: "${FRONTEND_ORIGIN:?Set FRONTEND_ORIGIN}"

java -jar "${COMMUNITY_JAR}" \
  --spring.profiles.active=aws \
  --spring.main.web-application-type=none \
  --app.runtime=media-backfill-dry-run \
  --app.media.enabled=false
