#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command getent
require_command groupadd
require_command install

assert_safe_path "${COMMUNITY_DATA_ROOT}"
assert_safe_path "${COMMUNITY_SECRETS_DIR}"

if getent group "${COMMUNITY_SECRET_GID}" >/dev/null; then
  existing_group="$(getent group "${COMMUNITY_SECRET_GID}" | cut -d: -f1)"
  if [[ "${existing_group}" != "community-secrets" ]]; then
    echo "GID ${COMMUNITY_SECRET_GID} is already used by ${existing_group}." >&2
    exit 1
  fi
else
  groupadd --system --gid "${COMMUNITY_SECRET_GID}" community-secrets
fi

install -d -o root -g root -m 0750 "${COMMUNITY_DATA_ROOT}"
install -d -o 999 -g 999 -m 0750 "${COMMUNITY_DATA_ROOT}/mysql"
install -d -o 10001 -g 10001 -m 0750 "${COMMUNITY_DATA_ROOT}/uploads"
install -d -o root -g root -m 0700 \
  "${COMMUNITY_DATA_ROOT}/backup" \
  "${COMMUNITY_DATA_ROOT}/evidence" \
  "${COMMUNITY_RELEASE_ROOT}"
install -d -o root -g community-secrets -m 0750 \
  "${COMMUNITY_SECRETS_DIR}"

for secret_name in \
  mysql-root-password \
  mysql-app-password \
  jwt-secret; do
  secret_path="${COMMUNITY_SECRETS_DIR}/${secret_name}"
  if [[ ! -e "${secret_path}" ]]; then
    install -o root -g community-secrets -m 0640 \
      /dev/null "${secret_path}"
    echo "Created empty secret file: ${secret_path}"
  else
    chown root:community-secrets "${secret_path}"
    chmod 0640 "${secret_path}"
  fi
done

echo "Directories are ready. Fill each secret file with sudoedit before deployment."
