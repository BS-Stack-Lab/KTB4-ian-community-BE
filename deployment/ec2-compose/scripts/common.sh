#!/usr/bin/env bash

set -Eeuo pipefail

readonly COMMUNITY_DATA_ROOT="${DATA_ROOT:-/data/community}"
readonly COMMUNITY_SECRETS_DIR="${SECRETS_DIR:-/etc/community/secrets}"
readonly COMMUNITY_SECRET_GID="20000"
readonly COMMUNITY_COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/community/deployment/ec2-compose}"
readonly COMMUNITY_COMPOSE_FILE="${COMMUNITY_COMPOSE_ROOT}/compose.yaml"
readonly COMMUNITY_RELEASE_ROOT="${COMMUNITY_DATA_ROOT}/releases"

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    echo "This script must be run as root." >&2
    exit 1
  fi
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
}

require_nonempty_file() {
  require_file "$1"
  if [[ ! -s "$1" ]]; then
    echo "Required file is empty: $1" >&2
    exit 1
  fi
}

assert_safe_path() {
  case "$1" in
    /data/community | /data/community/* | /etc/community | \
      /etc/community/* | /opt/community | /opt/community/* | \
      /tmp/community-*)
      ;;
    *)
      echo "Refusing unsafe path: $1" >&2
      exit 1
      ;;
  esac
}

timestamp() {
  date -u '+%Y%m%dT%H%M%SZ'
}

compose_cmd() {
  local env_file="$1"
  shift
  docker compose \
    --env-file "${env_file}" \
    --file "${COMMUNITY_COMPOSE_FILE}" \
    "$@"
}

env_value() {
  local env_file="$1"
  local key="$2"
  awk -F= -v key="${key}" \
    '$1 == key { sub(/^[^=]*=/, ""); print; exit }' \
    "${env_file}"
}

validate_release_env() {
  local env_file="$1"
  local key
  local value

  require_nonempty_file "${env_file}"

  if grep -Eq \
    '^(DB_PASSWORD|MYSQL_ROOT_PASSWORD|JWT_SECRET|AWS_SECRET_ACCESS_KEY)=' \
    "${env_file}"; then
    echo "Secret keys are forbidden in the Compose environment file." >&2
    exit 1
  fi

  for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE FRONTEND_ORIGIN; do
    value="$(env_value "${env_file}" "${key}")"
    if [[ -z "${value}" ]]; then
      echo "Missing required release setting: ${key}" >&2
      exit 1
    fi
  done

  for key in FRONTEND_IMAGE BACKEND_IMAGE; do
    value="$(env_value "${env_file}" "${key}")"
    if [[ "${value}" == "latest" || "${value}" == *:latest ]]; then
      echo "${key} must use a commit-addressed tag, not latest." >&2
      exit 1
    fi
  done
}

validate_secret_files() {
  local secret_name
  local secret_path
  local metadata

  for secret_name in \
    mysql-root-password \
    mysql-app-password \
    jwt-secret; do
    secret_path="${COMMUNITY_SECRETS_DIR}/${secret_name}"
    require_nonempty_file "${secret_path}"
    metadata="$(stat -c '%u:%g:%a' "${secret_path}")"
    if [[ "${metadata}" != "0:${COMMUNITY_SECRET_GID}:640" ]]; then
      echo "Invalid owner or mode for ${secret_path}; expected 0:${COMMUNITY_SECRET_GID}:640." >&2
      exit 1
    fi
  done
}

validate_local_images() {
  local env_file="$1"
  local key
  local image
  local platform

  for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE; do
    image="$(env_value "${env_file}" "${key}")"
    if ! docker image inspect "${image}" >/dev/null 2>&1; then
      echo "Required local image is missing: ${image}" >&2
      exit 1
    fi
    platform="$(
      docker image inspect \
        --platform linux/amd64 \
        --format '{{.Os}}/{{.Architecture}}' \
        "${image}"
    )"
    if [[ "${platform}" != "linux/amd64" ]]; then
      echo "Image ${image} has unsupported platform ${platform}." >&2
      exit 1
    fi
  done
}
