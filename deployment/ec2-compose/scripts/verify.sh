#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command curl
require_command docker
require_command find
require_command stat

release_env="${RELEASE_ENV:-${COMMUNITY_RELEASE_ROOT}/current.env}"
validate_release_env "${release_env}"
validate_secret_files

failures=0

pass() {
  echo "PASS: $1"
}

fail() {
  echo "FAIL: $1" >&2
  failures=$((failures + 1))
}

compose_cmd "${release_env}" config --quiet &&
  pass "Compose configuration" || fail "Compose configuration"

for service in mysql backend frontend; do
  container_id="$(compose_cmd "${release_env}" ps --quiet "${service}")"
  if [[ -z "${container_id}" ]]; then
    fail "${service} container exists"
    continue
  fi

  running="$(docker inspect --format '{{.State.Running}}' "${container_id}")"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "${container_id}")"
  [[ "${running}" == "true" ]] && pass "${service} is running" || fail "${service} is running"
  [[ "${health}" == "healthy" ]] && pass "${service} is healthy" || fail "${service} is healthy (${health})"

  privileged="$(docker inspect --format '{{.HostConfig.Privileged}}' "${container_id}")"
  [[ "${privileged}" == "false" ]] && pass "${service} is not privileged" || fail "${service} is privileged"

  socket_mount="$(
    docker inspect --format '{{range .Mounts}}{{println .Destination}}{{end}}' \
      "${container_id}" |
      grep -Fx '/var/run/docker.sock' || true
  )"
  [[ -z "${socket_mount}" ]] && pass "${service} has no Docker socket" || fail "${service} mounts the Docker socket"

  image_id="$(docker inspect --format '{{.Image}}' "${container_id}")"
  image_platform="$(
    docker image inspect \
      --platform linux/amd64 \
      --format '{{.Os}}/{{.Architecture}}' \
      "${image_id}"
  )"
  [[ "${image_platform}" == "linux/amd64" ]] && pass "${service} image is linux/amd64" || fail "${service} image is ${image_platform}"
done

frontend_id="$(compose_cmd "${release_env}" ps --quiet frontend)"
backend_id="$(compose_cmd "${release_env}" ps --quiet backend)"
mysql_id="$(compose_cmd "${release_env}" ps --quiet mysql)"

for service_id in "backend:${backend_id}" "mysql:${mysql_id}"; do
  service="${service_id%%:*}"
  container_id="${service_id#*:}"
  port_bindings="$(
    docker inspect --format '{{json .HostConfig.PortBindings}}' \
      "${container_id}"
  )"
  if [[ "${port_bindings}" == "{}" || "${port_bindings}" == "null" ]]; then
    pass "${service} has no published host ports"
  else
    fail "${service} has published host ports"
  fi
done

frontend_bindings="$(
  docker inspect --format '{{json .HostConfig.PortBindings}}' \
    "${frontend_id}"
)"
if [[ "${frontend_bindings}" == *'"80/tcp"'* ]]; then
  pass "frontend port 80 is published"
else
  fail "frontend port 80 is published"
fi

for service_id in "frontend:${frontend_id}" "backend:${backend_id}"; do
  service="${service_id%%:*}"
  container_id="${service_id#*:}"
  if [[ -n "${container_id}" ]] &&
    [[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")" == "true" ]]; then
    pass "${service} root filesystem is read-only"
  else
    fail "${service} root filesystem is read-only"
  fi
done

if [[ -n "${frontend_id}" ]] &&
  [[ "$(docker inspect --format '{{.Config.User}}' "${frontend_id}")" != "0" ]] &&
  [[ -n "$(docker inspect --format '{{.Config.User}}' "${frontend_id}")" ]]; then
  pass "frontend runs as a configured non-root user"
else
  fail "frontend runs as a configured non-root user"
fi

if [[ -n "${backend_id}" ]] &&
  [[ "$(compose_cmd "${release_env}" exec -T backend id -u)" == "10001" ]]; then
  pass "backend runs as UID 10001"
else
  fail "backend runs as UID 10001"
fi

if compose_cmd "${release_env}" exec -T backend \
  sh -c 'test -w /var/lib/community/uploads'; then
  pass "backend upload bind mount is writable"
else
  fail "backend upload bind mount is writable"
fi

if find "${COMMUNITY_DATA_ROOT}/uploads" -perm -0002 -print -quit |
  grep -q .; then
  fail "uploads contain world-writable paths"
else
  pass "uploads are not world-writable"
fi

http_port="$(env_value "${release_env}" HTTP_PORT)"
http_port="${http_port:-80}"
base_url="http://127.0.0.1:${http_port}"

if curl --fail --silent --show-error --output /dev/null \
  "${base_url}/healthz"; then
  pass "frontend health endpoint"
else
  fail "frontend health endpoint"
fi

if curl --fail --silent --show-error "${base_url}/" |
  grep -q 'id="root"'; then
  pass "React index is served"
else
  fail "React index is served"
fi

backend_health="$(
  compose_cmd "${release_env}" exec -T backend \
    env -u JAVA_TOOL_OPTIONS java -Xms16m -Xmx32m \
    -cp /app/healthcheck HealthCheck --body \
    2>/dev/null || true
)"
if [[ "${backend_health}" == *'"status":"UP"'* ]] &&
  [[ "${backend_health}" != *'"components"'* ]] &&
  [[ "${backend_health}" != *'"details"'* ]]; then
  pass "backend health is UP without details"
else
  fail "backend health is unavailable or exposes details"
fi

h2_status="$(
  compose_cmd "${release_env}" exec -T backend \
    env -u JAVA_TOOL_OPTIONS java -Xms16m -Xmx32m \
    -cp /app/healthcheck HealthCheck \
    --status http://127.0.0.1:8080/h2-console \
    2>/dev/null || true
)"
if [[ "${h2_status}" == "200" || "${h2_status}" == "302" ]]; then
  fail "H2 Console is disabled"
else
  pass "H2 Console is disabled"
fi

if [[ "${failures}" -ne 0 ]]; then
  echo "Verification failed: ${failures} control(s)." >&2
  exit 1
fi

echo "B-method Compose runtime verification passed."
echo "Check Security Group, EBS encryption, IMDSv2, costs, and EC2 reboot separately."
