#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
for command_name in curl docker install tar; do require_command "${command_name}"; done
acquire_deploy_lock

config_sha="${CONFIG_SHA:?Set CONFIG_SHA to a trusted Backend main commit}"
[[ "${config_sha}" =~ ^[0-9a-f]{40}$ ]] || { echo "CONFIG_SHA must be a full commit SHA." >&2; exit 1; }

if [[ "${CONFIG_PREPARED:-0}" != "1" ]]; then
  assert_safe_path "${COMMUNITY_CONFIG_ROOT}"
  install -d -o root -g root -m 0700 "${COMMUNITY_CONFIG_ROOT}"
  config_release="${COMMUNITY_CONFIG_ROOT}/${config_sha}"
  if [[ ! -d "${config_release}" ]]; then
    archive="/tmp/community-config-${config_sha}.tar.gz"
    stage="$(mktemp -d "${COMMUNITY_CONFIG_ROOT}/.stage-${config_sha}.XXXXXX")"
    assert_safe_path "${archive}"
    assert_safe_path "${stage}"
    trap 'rm -f -- "${archive}"; rm -rf -- "${stage}"' EXIT
    curl --fail --silent --show-error --location \
      "https://github.com/BS-Stack-Lab/KTB4-ian-community-BE/archive/${config_sha}.tar.gz" \
      --output "${archive}"
    tar -xzf "${archive}" --strip-components=1 -C "${stage}"
    require_file "${stage}/deployment/ec2-compose/compose.yaml"
    require_file "${stage}/deployment/ec2-compose/scripts/registry-deploy.sh"
    printf '%s\n' "${config_sha}" >"${stage}/.config-sha"
    chmod 0600 "${stage}/.config-sha"
    mv -- "${stage}" "${config_release}"
    rm -f -- "${archive}"
    trap - EXIT
  fi
  [[ "$(<"${config_release}/.config-sha")" == "${config_sha}" ]] || {
    echo "Prepared config SHA marker mismatch." >&2
    exit 1
  }
  new_root="${config_release}/deployment/ec2-compose"
  exec env CONFIG_PREPARED=1 COMMUNITY_LOCK_HELD=1 COMPOSE_ROOT="${new_root}" \
    CONFIG_ROOT="${COMMUNITY_CONFIG_ROOT}" \
    "${new_root}/scripts/registry-deploy.sh" "$@"
fi

target_component="${TARGET_COMPONENT:?Set TARGET_COMPONENT to frontend, backend, or both}"
[[ "${target_component}" =~ ^(frontend|backend|both)$ ]] || {
  echo "Unsupported TARGET_COMPONENT: ${target_component}" >&2
  exit 1
}

install -d -o root -g root -m 0700 "${COMMUNITY_RELEASE_ROOT}"
candidate_env="$(mktemp "${COMMUNITY_RELEASE_ROOT}/.candidate-env.XXXXXX")"
candidate_manifest="$(mktemp "${COMMUNITY_RELEASE_ROOT}/.candidate-manifest.XXXXXX")"
trap 'rm -f -- "${candidate_env}" "${candidate_manifest}"' EXIT
current_env="${COMMUNITY_RELEASE_ROOT}/current.env"

if is_registry_release_env "${current_env}"; then
  install -o root -g root -m 0600 "${current_env}" "${candidate_env}"
else
  [[ "${target_component}" == both ]] || {
    echo "The first registry deployment requires TARGET_COMPONENT=both with both approved pairs." >&2
    exit 1
  }
  if [[ -s "${current_env}" ]]; then
    echo "Legacy release state detected; building a fresh four-service candidate."
  fi
  production_origin="${PRODUCTION_ORIGIN:?Set PRODUCTION_ORIGIN for the first deployment}"
  cat >"${candidate_env}" <<EOF
MYSQL_IMAGE=mysql:8.4.11@sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0
NGINX_IMAGE=nginx:1.28.3-alpine3.23@sha256:0dcc88822d45581e65ae329f8be769762bf628d3b2bb7d2a077d4aa5c98b30e3
MYSQL_DATABASE=community
DB_USERNAME=community
DATA_ROOT=/data/community
SECRETS_DIR=/etc/community/secrets
TLS_DIR=/etc/community/tls
ACME_ROOT=/data/community/acme
FRONTEND_ORIGIN=${production_origin}
COOKIE_SECURE=true
HTTP_BIND_ADDRESS=0.0.0.0
HTTP_PORT=80
HTTPS_BIND_ADDRESS=0.0.0.0
HTTPS_PORT=443
DB_POOL_MAX_SIZE=8
DB_POOL_MIN_IDLE=1
JAVA_TOOL_OPTIONS=-Xms256m -Xmx640m -XX:+ExitOnOutOfMemoryError
IMAGE_PULL_POLICY=always
EOF
fi

set_env_value "${candidate_env}" CONFIG_SHA "${config_sha}"
set_env_value "${candidate_env}" COMPOSE_ROOT "${COMMUNITY_COMPOSE_ROOT}"
set_env_value "${candidate_env}" IMAGE_PULL_POLICY always

if [[ "${target_component}" == frontend || "${target_component}" == both ]]; then
  frontend_image="${TARGET_FRONTEND_IMAGE:?Set TARGET_FRONTEND_IMAGE}"
  frontend_commit="${TARGET_FRONTEND_COMMIT:?Set TARGET_FRONTEND_COMMIT}"
  set_env_value "${candidate_env}" FRONTEND_IMAGE "${frontend_image}"
  set_env_value "${candidate_env}" FRONTEND_COMMIT "${frontend_commit}"
fi

media_runtime_env="${MEDIA_RUNTIME_ENV:-/etc/community/media-v2.env}"
require_nonempty_file "${media_runtime_env}"
for key in MEDIA_V2_ENABLED MEDIA_BUCKET MEDIA_QUEUE_URL MEDIA_API_ROLE_ARN MEDIA_WORKER_ROLE_ARN MEDIA_CDN_BASE_URL MEDIA_DISTRIBUTION_ID MEDIA_ENVIRONMENT MEDIA_TRANSFORM_VERSION; do
  media_value="$(env_value "${media_runtime_env}" "${key}")"
  [[ -n "${media_value}" ]] || {
    echo "Missing Media V2 runtime setting: ${key}" >&2
    exit 1
  }
  set_env_value "${candidate_env}" "${key}" "${media_value}"
done
if [[ "${target_component}" == backend || "${target_component}" == both ]]; then
  backend_image="${TARGET_BACKEND_IMAGE:?Set TARGET_BACKEND_IMAGE}"
  backend_commit="${TARGET_BACKEND_COMMIT:?Set TARGET_BACKEND_COMMIT}"
  set_env_value "${candidate_env}" BACKEND_IMAGE "${backend_image}"
  set_env_value "${candidate_env}" BACKEND_COMMIT "${backend_commit}"
fi

validate_release_env "${candidate_env}"
validate_secret_files
validate_tls_files
compose_cmd "${candidate_env}" config --quiet
pull_and_verify_images "${candidate_env}"

cat >"${candidate_manifest}" <<EOF
RELEASE_ID=$(timestamp)-${config_sha:0:12}
CONFIG_SHA=${config_sha}
FRONTEND_COMMIT=$(env_value "${candidate_env}" FRONTEND_COMMIT)
BACKEND_COMMIT=$(env_value "${candidate_env}" BACKEND_COMMIT)
FRONTEND_IMAGE=$(env_value "${candidate_env}" FRONTEND_IMAGE)
BACKEND_IMAGE=$(env_value "${candidate_env}" BACKEND_IMAGE)
MYSQL_IMAGE=$(env_value "${candidate_env}" MYSQL_IMAGE)
NGINX_IMAGE=$(env_value "${candidate_env}" NGINX_IMAGE)
DEPLOYED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
EOF
chmod 0600 "${candidate_manifest}"
validate_release_manifest "${candidate_manifest}"

RELEASE_ENV="${candidate_env}" "${SCRIPT_DIR}/smoke-release.sh"
COMMUNITY_LOCK_HELD=1 RELEASE_ENV="${candidate_env}" RELEASE_MANIFEST="${candidate_manifest}" \
  "${SCRIPT_DIR}/deploy.sh"
