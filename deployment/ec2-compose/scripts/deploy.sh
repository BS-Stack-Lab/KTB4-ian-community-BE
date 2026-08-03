#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command docker
require_command install
require_file "${COMMUNITY_COMPOSE_FILE}"

release_env="${RELEASE_ENV:-${COMMUNITY_COMPOSE_ROOT}/.env}"
release_manifest="${RELEASE_MANIFEST:-${COMMUNITY_COMPOSE_ROOT}/release-manifest}"
current_env="${COMMUNITY_RELEASE_ROOT}/current.env"
previous_env="${COMMUNITY_RELEASE_ROOT}/previous.env"
current_manifest="${COMMUNITY_RELEASE_ROOT}/current.manifest"
previous_manifest="${COMMUNITY_RELEASE_ROOT}/previous.manifest"

validate_release_env "${release_env}"
validate_secret_files
validate_local_images "${release_env}"
require_nonempty_file "${release_manifest}"

if grep -Eq \
  '(PASSWORD|SECRET|PRIVATE_KEY|ACCESS_KEY)=' \
  "${release_manifest}"; then
  echo "Release manifests must not contain secrets." >&2
  exit 1
fi

compose_cmd "${release_env}" config --quiet

echo "Starting commit-addressed release images."
if ! compose_cmd "${release_env}" up \
  --detach \
  --remove-orphans \
  --wait \
  --wait-timeout 240; then
  compose_cmd "${release_env}" ps || true
  echo "Deployment failed. current.env was not promoted; inspect service logs." >&2
  exit 1
fi

if [[ -f "${current_env}" ]]; then
  install -o root -g root -m 0600 "${current_env}" "${previous_env}"
fi
if [[ -f "${current_manifest}" ]]; then
  install -o root -g root -m 0600 \
    "${current_manifest}" "${previous_manifest}"
fi
install -o root -g root -m 0600 "${release_env}" "${current_env}"
install -o root -g root -m 0600 \
  "${release_manifest}" "${current_manifest}"

RELEASE_ENV="${current_env}" "${SCRIPT_DIR}/verify.sh"

echo "Deployment completed and release state was promoted."
