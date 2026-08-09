#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
for command_name in curl docker find head stat; do require_command "${command_name}"; done

release_env="${RELEASE_ENV:-${COMMUNITY_RELEASE_ROOT}/current.env}"
validate_release_env "${release_env}"
validate_secret_files
if [[ "${CI_MODE:-0}" != "1" ]]; then validate_tls_files; fi

failures=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }

compose_cmd "${release_env}" config --quiet && pass "Compose configuration" || fail "Compose configuration"
services="$(compose_cmd "${release_env}" config --services | sort | tr '\n' ' ')"
if [[ "${CI_MODE:-0}" == "1" ]]; then
  expected_services="backend frontend mysql nginx "
  runtime_services=(mysql backend frontend nginx)
else
  expected_services="backend frontend media-worker mysql nginx "
  runtime_services=(mysql backend media-worker frontend nginx)
fi
[[ "${services}" == "${expected_services}" ]] && pass "expected service topology" || fail "unexpected services: ${services}"

declare -A ids=()
for service in "${runtime_services[@]}"; do
  ids[${service}]="$(compose_cmd "${release_env}" ps --quiet "${service}")"
  container_id="${ids[${service}]}"
  [[ -n "${container_id}" ]] || { fail "${service} container exists"; continue; }
  running="$(docker inspect --format '{{.State.Running}}' "${container_id}")"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "${container_id}")"
  [[ "${running}" == true ]] && pass "${service} running" || fail "${service} running"
  [[ "${health}" == healthy ]] && pass "${service} healthy" || fail "${service} healthy (${health})"
  [[ "$(docker inspect --format '{{.HostConfig.Privileged}}' "${container_id}")" == false ]] && pass "${service} not privileged" || fail "${service} privileged"
  socket_mount="$(docker inspect --format '{{range .Mounts}}{{println .Destination}}{{end}}' "${container_id}" | grep -Fx /var/run/docker.sock || true)"
  [[ -z "${socket_mount}" ]] && pass "${service} has no Docker socket" || fail "${service} mounts Docker socket"
  platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$(docker inspect --format '{{.Image}}' "${container_id}")")"
  [[ "${platform}" == linux/amd64 ]] && pass "${service} linux/amd64" || fail "${service} platform ${platform}"
done

portless_services=(mysql backend frontend)
if [[ "${CI_MODE:-0}" != "1" ]]; then portless_services+=(media-worker); fi
for service in "${portless_services[@]}"; do
  bindings="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "${ids[${service}]}")"
  [[ "${bindings}" == '{}' || "${bindings}" == null ]] && pass "${service} has no host ports" || fail "${service} publishes host ports"
done

nginx_bindings="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "${ids[nginx]}")"
if [[ "${CI_MODE:-0}" == "1" ]]; then
  [[ "${nginx_bindings}" == *'"8080/tcp"'* && "${nginx_bindings}" != *'"8443/tcp"'* ]] && pass "CI edge exposes loopback HTTP only" || fail "CI edge port policy"
else
  [[ "${nginx_bindings}" == *'"8080/tcp"'* && "${nginx_bindings}" == *'"8443/tcp"'* ]] && pass "edge owns HTTP and HTTPS" || fail "edge port policy"
fi

secured_services=(backend frontend nginx)
if [[ "${CI_MODE:-0}" != "1" ]]; then secured_services+=(media-worker); fi
for service in "${secured_services[@]}"; do
  container_id="${ids[${service}]}"
  [[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")" == true ]] && pass "${service} read-only root" || fail "${service} read-only root"
  [[ "$(docker inspect --format '{{json .HostConfig.CapDrop}}' "${container_id}")" == *'"ALL"'* ]] && pass "${service} drops capabilities" || fail "${service} capabilities"
  [[ "$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "${container_id}")" == *'no-new-privileges'* ]] && pass "${service} no-new-privileges" || fail "${service} no-new-privileges"
  uid="$(compose_cmd "${release_env}" exec -T "${service}" id -u)"
  [[ "${uid}" != 0 ]] && pass "${service} non-root UID ${uid}" || fail "${service} runs as root"
done
[[ "$(compose_cmd "${release_env}" exec -T backend id -u)" == 10001 ]] && pass "backend UID 10001" || fail "backend UID"
[[ "$(compose_cmd "${release_env}" exec -T frontend id -u)" == 101 ]] && pass "frontend UID 101" || fail "frontend UID"
compose_cmd "${release_env}" exec -T backend sh -c 'test -w /var/lib/community/uploads' && pass "upload mount writable" || fail "upload mount writable"
find "${COMMUNITY_DATA_ROOT}/uploads" -perm -0002 -print -quit | grep -q . && fail "world-writable upload path" || pass "uploads not world-writable"
if [[ "${CI_MODE:-0}" != "1" ]]; then
  [[ "$(compose_cmd "${release_env}" exec -T media-worker id -u)" == 10001 ]] && pass "media-worker UID 10001" || fail "media-worker UID"
  compose_cmd "${release_env}" exec -T media-worker sh -c 'test -w /var/lib/community/media-worker' && pass "media scratch writable" || fail "media scratch writable"
  find "${COMMUNITY_DATA_ROOT}/media-worker" -perm -0002 -print -quit | grep -q . && fail "world-writable media scratch" || pass "media scratch not world-writable"
  [[ "$(docker inspect --format '{{.HostConfig.Memory}}' "${ids[media-worker]}")" == 536870912 ]] && pass "media-worker memory 512MiB" || fail "media-worker memory limit"
  [[ "$(docker inspect --format '{{.HostConfig.NanoCpus}}' "${ids[media-worker]}")" == 500000000 ]] && pass "media-worker CPU 0.5" || fail "media-worker CPU limit"
fi

base_url="${VERIFY_PUBLIC_URL:-$(env_value "${release_env}" FRONTEND_ORIGIN)}"
curl --fail --silent --show-error "${base_url}/healthz" | grep -qx ok && pass "edge health" || fail "edge health"
curl --fail --silent --show-error "${base_url}/" | grep -q 'id="root"' && pass "React index" || fail "React index"
curl --fail --silent --show-error "${base_url}/login" | grep -q 'id="root"' && pass "SPA deep link" || fail "SPA deep link"
curl --fail --silent --show-error --output /dev/null "${base_url}/api/csrf" && pass "API through edge" || fail "API through edge"

index_body="$(curl --fail --silent --show-error "${base_url}/")"
hashed_assets=()
while IFS= read -r asset; do
  hashed_assets+=("${asset}")
done < <(printf '%s' "${index_body}" \
  | grep -oE '/dist/app\.[0-9a-f]{12}\.(js|css)' \
  | sort -u || true)

asset_headers=''
if [[ "${#hashed_assets[@]}" -eq 2 ]]; then
  for asset in "${hashed_assets[@]}"; do
    headers="$(curl --fail --silent --show-error --dump-header - \
      --output /dev/null "${base_url}${asset}" | tr -d '\r')"
    grep -Eiq '^Cache-Control: .*max-age=31536000.*immutable' <<<"${headers}" \
      && pass "immutable cache ${asset}" || fail "immutable cache ${asset}"
    asset_headers+="${headers}"$'\n'
  done
  version_json="$(curl --fail --silent --show-error "${base_url}/version.json")"
  version="$(sed -nE 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' \
    <<<"${version_json}")"
  expected_frontend_commit="$(env_value "${release_env}" FRONTEND_COMMIT)"
  image_revision="$(docker inspect --format \
    '{{index .Config.Labels "org.opencontainers.image.revision"}}' \
    "${ids[frontend]}")"
  [[ "${version}" == "${expected_frontend_commit}" \
    && "${image_revision}" == "${expected_frontend_commit}" ]] \
    && pass "version manifest and OCI revision" \
    || fail "version manifest/OCI revision mismatch"
  version_headers="$(curl --silent --show-error --dump-header - --output /dev/null \
    "${base_url}/version.json" | tr -d '\r')"
  grep -Eiq '^Cache-Control: .*no-store' <<<"${version_headers}" \
    && pass "version no-store" || fail "version no-store"
  for legacy_asset in /dist/app.js /dist/app.css; do
    status="$(curl --silent --show-error --output /dev/null \
      --write-out '%{http_code}' "${base_url}${legacy_asset}")"
    [[ "${status}" == 404 ]] && pass "legacy asset absent ${legacy_asset}" \
      || fail "legacy asset present ${legacy_asset} (${status})"
  done
else
  # Transitional compatibility for a Backend-first rollout. This branch is
  # removed naturally once the approved Frontend baseline uses content hashes.
  asset_headers="$(curl --silent --show-error --dump-header - --output /dev/null \
    "${base_url}/dist/app.js" | tr -d '\r')"
  grep -Eiq '^Cache-Control: .*immutable' <<<"${asset_headers}" \
    && pass "legacy immutable dist cache" || fail "legacy immutable dist cache"
fi
index_headers="$(curl --silent --show-error --dump-header - --output /dev/null "${base_url}/" | tr -d '\r')"
grep -Eiq '^Cache-Control: .*no-(store|cache)' <<<"${index_headers}" \
  && pass "index no-store/no-cache" || fail "index no-store/no-cache"
if grep -Eiq '^X-Content-Type-Options: nosniff' <<<"${asset_headers}" &&
  grep -Eiq '^X-Content-Type-Options: nosniff' <<<"${index_headers}"; then
  pass "security headers"
else
  fail "security headers"
fi

for blocked in /actuator /h2-console /.env /backup.sql /config.yaml /api/.env; do
  status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${base_url}${blocked}")"
  [[ "${status}" == 404 ]] && pass "blocked ${blocked}" || fail "blocked ${blocked} (${status})"
done
body_status="$(head -c 12000000 /dev/zero | curl --silent --show-error --output /dev/null --write-out '%{http_code}' -H 'Content-Type: application/octet-stream' --data-binary @- "${base_url}/api/posts")"
[[ "${body_status}" == 413 ]] && pass "11MB edge body limit" || fail "11MB edge body limit (${body_status})"

compose_cmd "${release_env}" exec -T mysql sh -ec '
  export MYSQL_PWD="$(cat /run/secrets/mysql-app-password)"
  mysql --batch --skip-column-names --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SELECT 1"
' | grep -qx 1 && pass "MySQL application connection" || fail "MySQL application connection"
compose_cmd "${release_env}" exec -T mysql sh -ec '
  export MYSQL_PWD="$(cat /run/secrets/mysql-app-password)"
  mysql --batch --skip-column-names --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SELECT COUNT(*) FROM flyway_schema_history"
' | grep -Eq '^[1-9][0-9]*$' && pass "Flyway history" || fail "Flyway history"

compose_cmd "${release_env}" exec -T nginx nginx -t >/dev/null && pass "edge nginx configuration" || fail "edge nginx configuration"

if [[ "${CI_MODE:-0}" != "1" && "$(env_value "${release_env}" MEDIA_V2_ENABLED)" == true ]]; then
  for command_name in aws jq; do require_command "${command_name}"; done
  worker_role="$(env_value "${release_env}" MEDIA_WORKER_ROLE_ARN)"
  queue_url="$(env_value "${release_env}" MEDIA_QUEUE_URL)"
  credentials="$(aws sts assume-role \
    --role-arn "${worker_role}" \
    --role-session-name community-media-readonly-smoke \
    --duration-seconds 900 \
    --output json)"
  access_key="$(jq -r '.Credentials.AccessKeyId' <<<"${credentials}")"
  secret_key="$(jq -r '.Credentials.SecretAccessKey' <<<"${credentials}")"
  session_token="$(jq -r '.Credentials.SessionToken' <<<"${credentials}")"
  queue_attributes="$(AWS_ACCESS_KEY_ID="${access_key}" \
    AWS_SECRET_ACCESS_KEY="${secret_key}" AWS_SESSION_TOKEN="${session_token}" \
    aws sqs get-queue-attributes --queue-url "${queue_url}" \
      --attribute-names ApproximateNumberOfMessages \
        ApproximateNumberOfMessagesNotVisible RedrivePolicy --output json)"
  dlq_arn="$(jq -r '.Attributes.RedrivePolicy | fromjson | .deadLetterTargetArn' \
    <<<"${queue_attributes}")"
  dlq_name="${dlq_arn##*:}"
  dlq_url="$(AWS_ACCESS_KEY_ID="${access_key}" \
    AWS_SECRET_ACCESS_KEY="${secret_key}" AWS_SESSION_TOKEN="${session_token}" \
    aws sqs get-queue-url --queue-name "${dlq_name}" --query QueueUrl --output text)"
  AWS_ACCESS_KEY_ID="${access_key}" AWS_SECRET_ACCESS_KEY="${secret_key}" \
    AWS_SESSION_TOKEN="${session_token}" aws sqs get-queue-attributes \
      --queue-url "${dlq_url}" --attribute-names ApproximateNumberOfMessages \
      --output json >/dev/null \
    && pass "Media SQS and DLQ read-only smoke" \
    || fail "Media SQS and DLQ read-only smoke"
  unset access_key secret_key session_token credentials
fi

if [[ "${VERIFY_PERSISTENCE:-0}" == "1" ]]; then
  marker="ci-persistence-$(date +%s)-$$"
  compose_cmd "${release_env}" exec -T backend sh -c "printf '%s' '${marker}' > /var/lib/community/uploads/.ci-persistence-probe"
  compose_cmd "${release_env}" exec -T --env PERSISTENCE_MARKER="${marker}" mysql sh -ec '
    export MYSQL_PWD="$(cat /run/secrets/mysql-app-password)"
    mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="CREATE TABLE IF NOT EXISTS ci_persistence_probe (value VARCHAR(128) PRIMARY KEY); REPLACE INTO ci_persistence_probe VALUES (\"$PERSISTENCE_MARKER\");"
  '
  compose_cmd "${release_env}" restart mysql backend
  compose_cmd "${release_env}" up --detach --wait --wait-timeout 300
  compose_cmd "${release_env}" exec -T backend grep -qx "${marker}" /var/lib/community/uploads/.ci-persistence-probe && pass "upload restart persistence" || fail "upload restart persistence"
  compose_cmd "${release_env}" exec -T --env PERSISTENCE_MARKER="${marker}" mysql sh -ec '
    export MYSQL_PWD="$(cat /run/secrets/mysql-app-password)"
    mysql --batch --skip-column-names --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SELECT value FROM ci_persistence_probe WHERE value=\"$PERSISTENCE_MARKER\"; DROP TABLE ci_persistence_probe;"
  ' | grep -qx "${marker}" && pass "database restart persistence" || fail "database restart persistence"
  compose_cmd "${release_env}" exec -T backend rm -f /var/lib/community/uploads/.ci-persistence-probe
fi

[[ "${failures}" -eq 0 ]] || {
  echo "Verification failed: ${failures} control(s)." >&2
  exit 1
}
echo "Compose runtime verification passed."
