#!/usr/bin/env bash

set -Eeuo pipefail

readonly COMMUNITY_DATA_ROOT="${DATA_ROOT:-/data/community}"
readonly COMMUNITY_SECRETS_DIR="${SECRETS_DIR:-/etc/community/secrets}"
readonly COMMUNITY_TLS_DIR="${TLS_DIR:-/etc/community/tls}"
readonly COMMUNITY_SECRET_GID="20000"
readonly COMMUNITY_TLS_GID="20001"
readonly COMMUNITY_COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/community/deployment/ec2-compose}"
readonly COMMUNITY_COMPOSE_FILE="${COMMUNITY_COMPOSE_ROOT}/compose.yaml"
readonly COMMUNITY_RELEASE_ROOT="${COMMUNITY_DATA_ROOT}/releases"
readonly COMMUNITY_CONFIG_ROOT="${CONFIG_ROOT:-/opt/community/configs}"
readonly COMMUNITY_DEPLOY_LOCK="${DEPLOY_LOCK:-/run/lock/community-deploy.lock}"

require_root() {
  [[ "${EUID}" -eq 0 ]] || {
    echo "This script must be run as root." >&2
    exit 1
  }
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_file() {
  [[ -f "$1" ]] || {
    echo "Required file not found: $1" >&2
    exit 1
  }
}

require_nonempty_file() {
  require_file "$1"
  [[ -s "$1" ]] || {
    echo "Required file is empty: $1" >&2
    exit 1
  }
}

assert_safe_path() {
  case "$1" in
    /data/community | /data/community/* | /etc/community | /etc/community/* | \
      /opt/community | /opt/community/* | /tmp/community-*)
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

acquire_deploy_lock() {
  if [[ "${COMMUNITY_LOCK_HELD:-0}" == "1" ]]; then
    return
  fi
  require_command flock
  install -d -o root -g root -m 0755 "$(dirname -- "${COMMUNITY_DEPLOY_LOCK}")"
  exec 9>"${COMMUNITY_DEPLOY_LOCK}"
  flock --wait "${DEPLOY_LOCK_TIMEOUT:-900}" 9 || {
    echo "Timed out waiting for the community deployment lock." >&2
    exit 1
  }
  export COMMUNITY_LOCK_HELD=1
}

compose_cmd() {
  local env_file="$1"
  shift
  local compose_root
  compose_root="$(env_value "${env_file}" COMPOSE_ROOT)"
  compose_root="${compose_root:-${COMMUNITY_COMPOSE_ROOT}}"
  require_file "${compose_root}/compose.yaml"
  local args=(--env-file "${env_file}" --file "${compose_root}/compose.yaml")
  if [[ -n "${COMPOSE_OVERRIDE_FILE:-}" ]]; then
    args+=(--file "${COMPOSE_OVERRIDE_FILE}")
  fi
  docker compose "${args[@]}" "$@"
}

env_value() {
  local env_file="$1"
  local key="$2"
  awk -F= -v key="${key}" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "${env_file}"
}

set_env_value() {
  local env_file="$1"
  local key="$2"
  local value="$3"
  [[ "${key}" =~ ^[A-Z][A-Z0-9_]*$ ]]
  [[ "${value}" != *$'\n'* && "${value}" != *$'\r'* ]]
  local replacement
  replacement="$(mktemp "$(dirname -- "${env_file}")/.env-value.XXXXXX")"
  awk -F= -v key="${key}" -v value="${value}" '
    BEGIN { found = 0 }
    $1 == key { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' "${env_file}" >"${replacement}"
  chmod 0600 "${replacement}"
  mv -f -- "${replacement}" "${env_file}"
}

atomic_copy() {
  local source="$1"
  local target="$2"
  local mode="${3:-0600}"
  local temp
  install -d -o root -g root -m 0700 "$(dirname -- "${target}")"
  temp="$(mktemp "$(dirname -- "${target}")/.promote.XXXXXX")"
  install -o root -g root -m "${mode}" "${source}" "${temp}"
  mv -f -- "${temp}" "${target}"
}

validate_release_env() {
  local env_file="$1"
  local key value expected_root
  require_nonempty_file "${env_file}"

  if grep -Eq '^(DB_PASSWORD|MYSQL_ROOT_PASSWORD|MYSQL_PASSWORD|JWT_SECRET|AWS_SECRET_ACCESS_KEY|PRIVATE_KEY)=' "${env_file}"; then
    echo "Secret keys are forbidden in the Compose environment file." >&2
    exit 1
  fi

  for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE NGINX_IMAGE FRONTEND_ORIGIN FRONTEND_COMMIT BACKEND_COMMIT CONFIG_SHA COMPOSE_ROOT MEDIA_V2_ENABLED MEDIA_BUCKET MEDIA_QUEUE_URL MEDIA_API_ROLE_ARN MEDIA_WORKER_ROLE_ARN MEDIA_CDN_BASE_URL MEDIA_DISTRIBUTION_ID MEDIA_ENVIRONMENT MEDIA_TRANSFORM_VERSION; do
    value="$(env_value "${env_file}" "${key}")"
    [[ -n "${value}" ]] || {
      echo "Missing required release setting: ${key}" >&2
      exit 1
    }
  done

  [[ "$(env_value "${env_file}" MEDIA_V2_ENABLED)" == true ]] || {
    echo "MEDIA_V2_ENABLED must be true for the Media V2 runtime release." >&2
    exit 1
  }

  value="$(env_value "${env_file}" COMPOSE_ROOT)"
  [[ "${value}" == /* && "${value}" != *'/../'* && "${value}" != */.. ]] || {
    echo "COMPOSE_ROOT must be a normalized absolute path." >&2
    exit 1
  }
  if [[ "${ALLOW_LOCAL_IMAGES:-0}" != "1" ]]; then
    expected_root="${COMMUNITY_CONFIG_ROOT}/$(env_value "${env_file}" CONFIG_SHA)/deployment/ec2-compose"
    [[ "${value}" == "${expected_root}" ]] || {
      echo "COMPOSE_ROOT does not match CONFIG_SHA." >&2
      exit 1
    }
  fi

  for key in FRONTEND_COMMIT BACKEND_COMMIT CONFIG_SHA; do
    value="$(env_value "${env_file}" "${key}")"
    [[ "${value}" =~ ^[0-9a-f]{40}$ ]] || {
      echo "${key} must be a full lowercase commit SHA." >&2
      exit 1
    }
  done

  [[ "$(env_value "${env_file}" MYSQL_IMAGE)" == "mysql:8.4.11@sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0" ]] || {
    echo "MYSQL_IMAGE must match the approved fixed digest." >&2
    exit 1
  }
  [[ "$(env_value "${env_file}" NGINX_IMAGE)" == "nginx:1.28.3-alpine3.23@sha256:0dcc88822d45581e65ae329f8be769762bf628d3b2bb7d2a077d4aa5c98b30e3" ]] || {
    echo "NGINX_IMAGE must match the approved fixed digest." >&2
    exit 1
  }

  for key in FRONTEND_IMAGE BACKEND_IMAGE; do
    value="$(env_value "${env_file}" "${key}")"
    [[ "${value}" != "latest" && "${value}" != *:latest ]] || {
      echo "${key} must never use latest." >&2
      exit 1
    }
  done
  if [[ "${ALLOW_LOCAL_IMAGES:-0}" != "1" ]]; then
    [[ "$(env_value "${env_file}" FRONTEND_IMAGE)" =~ ^ghcr\.io/bs-stack-lab/ktb4-ian-community-fe@sha256:[0-9a-f]{64}$ ]] || {
      echo "FRONTEND_IMAGE must be the approved Frontend GHCR digest reference." >&2
      exit 1
    }
    [[ "$(env_value "${env_file}" BACKEND_IMAGE)" =~ ^ghcr\.io/bs-stack-lab/ktb4-ian-community-be@sha256:[0-9a-f]{64}$ ]] || {
      echo "BACKEND_IMAGE must be the approved Backend GHCR digest reference." >&2
      exit 1
    }
  fi

  value="$(env_value "${env_file}" FRONTEND_ORIGIN)"
  if [[ "${ALLOW_HTTP_ORIGIN:-0}" == "1" ]]; then
    [[ "${value}" =~ ^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$ ]]
  else
    [[ "${value}" =~ ^https://[A-Za-z0-9.-]+$ ]]
  fi || {
    echo "FRONTEND_ORIGIN is not an approved origin." >&2
    exit 1
  }
}

is_registry_release_env() {
  local env_file="$1"
  [[ -s "${env_file}" ]] || return 1
  (
    validate_release_env "${env_file}" >/dev/null 2>&1
  )
}

is_pre_media_registry_release_env() {
  local env_file="$1" key value config_sha compose_root expected_root
  [[ -s "${env_file}" ]] || return 1
  [[ -z "$(env_value "${env_file}" MEDIA_V2_ENABLED)" ]] || return 1

  for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE NGINX_IMAGE FRONTEND_ORIGIN FRONTEND_COMMIT BACKEND_COMMIT CONFIG_SHA COMPOSE_ROOT; do
    value="$(env_value "${env_file}" "${key}")"
    [[ -n "${value}" ]] || return 1
  done

  for key in FRONTEND_COMMIT BACKEND_COMMIT CONFIG_SHA; do
    value="$(env_value "${env_file}" "${key}")"
    [[ "${value}" =~ ^[0-9a-f]{40}$ ]] || return 1
  done

  config_sha="$(env_value "${env_file}" CONFIG_SHA)"
  compose_root="$(env_value "${env_file}" COMPOSE_ROOT)"
  [[ "${compose_root}" == /* && "${compose_root}" != *'/../'* && "${compose_root}" != */.. ]] || return 1
  if [[ "${ALLOW_LOCAL_IMAGES:-0}" != "1" ]]; then
    expected_root="${COMMUNITY_CONFIG_ROOT}/${config_sha}/deployment/ec2-compose"
    [[ "${compose_root}" == "${expected_root}" ]] || return 1
  fi
  [[ -f "${compose_root}/compose.yaml" && -x "${compose_root}/scripts/verify.sh" ]]
}

preserve_legacy_compose() {
  local bootstrap_root="$1"
  local current_env="$2"
  local archive_parent="$3"
  local existing_root legacy_root

  [[ -s "${current_env}" ]] || return 0
  is_registry_release_env "${current_env}" && return 0

  existing_root="$(env_value "${current_env}" LEGACY_COMPOSE_ROOT)"
  if [[ -n "${existing_root}" && -f "${existing_root}/compose.yaml" ]]; then
    return 0
  fi

  require_file "${bootstrap_root}/compose.yaml"
  install -d -m 0700 "${archive_parent}"
  legacy_root="$(mktemp -d "${archive_parent}/ec2-compose.XXXXXX")"
  cp -a "${bootstrap_root}/." "${legacy_root}/"
  set_env_value "${current_env}" LEGACY_COMPOSE_ROOT "${legacy_root}"
  echo "Preserved the legacy Compose assets at ${legacy_root}."
}

validate_release_manifest() {
  local manifest="$1"
  require_nonempty_file "${manifest}"
  if grep -Eqi '(PASSWORD|SECRET|PRIVATE_KEY|ACCESS_KEY)=' "${manifest}"; then
    echo "Release manifests must not contain secrets." >&2
    exit 1
  fi
}

validate_secret_files() {
  local secret_name secret_path metadata
  for secret_name in mysql-root-password mysql-app-password jwt-secret; do
    secret_path="${COMMUNITY_SECRETS_DIR}/${secret_name}"
    require_nonempty_file "${secret_path}"
    metadata="$(stat -c '%u:%g:%a' "${secret_path}")"
    [[ "${metadata}" == "0:${COMMUNITY_SECRET_GID}:640" ]] || {
      echo "Invalid metadata for ${secret_path}; expected 0:${COMMUNITY_SECRET_GID}:640." >&2
      exit 1
    }
  done
}

validate_tls_files() {
  local file metadata
  for file in fullchain.pem privkey.pem; do
    require_nonempty_file "${COMMUNITY_TLS_DIR}/${file}"
    metadata="$(stat -c '%u:%g:%a' "${COMMUNITY_TLS_DIR}/${file}")"
    [[ "${metadata}" == "0:${COMMUNITY_TLS_GID}:640" ]] || {
      echo "Invalid metadata for ${COMMUNITY_TLS_DIR}/${file}; expected 0:${COMMUNITY_TLS_GID}:640." >&2
      exit 1
    }
  done
}

validate_local_images() {
  local env_file="$1" key image platform expected_commit expected_source actual_revision actual_version actual_source
  for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE NGINX_IMAGE; do
    image="$(env_value "${env_file}" "${key}")"
    docker image inspect "${image}" >/dev/null 2>&1 || {
      echo "Required local image is missing: ${image}" >&2
      exit 1
    }
    platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "${image}")"
    [[ "${platform}" == "linux/amd64" ]] || {
      echo "Image ${image} has unsupported platform ${platform}." >&2
      exit 1
    }
  done

  if [[ "${ALLOW_LOCAL_IMAGES:-0}" != "1" ]]; then
    for key in FRONTEND BACKEND; do
      image="$(env_value "${env_file}" "${key}_IMAGE")"
      expected_commit="$(env_value "${env_file}" "${key}_COMMIT")"
      if [[ "${key}" == FRONTEND ]]; then
        expected_source="https://github.com/BS-Stack-Lab/KTB4-ian-community-FE"
      else
        expected_source="https://github.com/BS-Stack-Lab/KTB4-ian-community-BE"
      fi
      actual_revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "${image}")"
      actual_version="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "${image}")"
      actual_source="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.source"}}' "${image}")"
      [[ "${actual_revision}" == "${expected_commit}" && "${actual_version}" == "${expected_commit}" && "${actual_source}" == "${expected_source}" ]] || {
        echo "${key} image labels do not match the approved commit/source pair." >&2
        exit 1
      }
    done
  fi
}

pull_and_verify_images() {
  local env_file="$1" key image digest repo_digest
  for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE NGINX_IMAGE; do
    image="$(env_value "${env_file}" "${key}")"
    docker pull --platform linux/amd64 "${image}"
    if [[ "${image}" == *@sha256:* ]]; then
      digest="${image##*@}"
      repo_digest="$(docker image inspect --format '{{join .RepoDigests "\n"}}' "${image}")"
      grep -Fq "@${digest}" <<<"${repo_digest}" || {
        echo "Pulled image does not expose expected digest ${digest}: ${image}" >&2
        exit 1
      }
    fi
  done
  validate_local_images "${env_file}"
}
