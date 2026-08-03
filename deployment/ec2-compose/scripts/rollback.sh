#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command docker
require_command install

if [[ "${ROLLBACK_CONFIRM:-}" != "rollback-community" ]]; then
  echo "Set ROLLBACK_CONFIRM=rollback-community after reviewing previous.manifest." >&2
  exit 1
fi

current_env="${COMMUNITY_RELEASE_ROOT}/current.env"
previous_env="${COMMUNITY_RELEASE_ROOT}/previous.env"
current_manifest="${COMMUNITY_RELEASE_ROOT}/current.manifest"
previous_manifest="${COMMUNITY_RELEASE_ROOT}/previous.manifest"

require_nonempty_file "${current_env}"
require_nonempty_file "${previous_env}"
validate_release_env "${previous_env}"
validate_secret_files
validate_local_images "${previous_env}"
compose_cmd "${previous_env}" config --quiet

if ! compose_cmd "${previous_env}" up \
  --detach \
  --remove-orphans \
  --wait \
  --wait-timeout 240; then
  compose_cmd "${previous_env}" ps || true
  echo "Rollback failed; release state files were not changed." >&2
  exit 1
fi

swap_env="$(mktemp /tmp/community-rollback-env.XXXXXX)"
trap 'rm -f -- "${swap_env}"' EXIT
install -o root -g root -m 0600 "${current_env}" "${swap_env}"
install -o root -g root -m 0600 "${previous_env}" "${current_env}"
install -o root -g root -m 0600 "${swap_env}" "${previous_env}"

if [[ -f "${current_manifest}" && -f "${previous_manifest}" ]]; then
  swap_manifest="$(mktemp /tmp/community-rollback-manifest.XXXXXX)"
  install -o root -g root -m 0600 \
    "${current_manifest}" "${swap_manifest}"
  install -o root -g root -m 0600 \
    "${previous_manifest}" "${current_manifest}"
  install -o root -g root -m 0600 \
    "${swap_manifest}" "${previous_manifest}"
  rm -f -- "${swap_manifest}"
fi

RELEASE_ENV="${current_env}" "${SCRIPT_DIR}/verify.sh"

echo "Rollback completed. The replaced release is now previous.env."
