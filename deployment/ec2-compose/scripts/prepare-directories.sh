#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
for command_name in getent groupadd install; do require_command "${command_name}"; done
assert_safe_path "${COMMUNITY_DATA_ROOT}"
assert_safe_path "${COMMUNITY_SECRETS_DIR}"
assert_safe_path "${COMMUNITY_TLS_DIR}"
assert_safe_path "${COMMUNITY_CONFIG_ROOT}"

ensure_group() {
  local gid="$1" name="$2" existing
  if getent group "${gid}" >/dev/null; then
    existing="$(getent group "${gid}" | cut -d: -f1)"
    [[ "${existing}" == "${name}" ]] || {
      echo "GID ${gid} is already used by ${existing}." >&2
      exit 1
    }
  else
    groupadd --system --gid "${gid}" "${name}"
  fi
}

ensure_group "${COMMUNITY_SECRET_GID}" community-secrets
ensure_group "${COMMUNITY_TLS_GID}" community-tls

install -d -o root -g root -m 0750 "${COMMUNITY_DATA_ROOT}"
install -d -o 999 -g 999 -m 0750 "${COMMUNITY_DATA_ROOT}/mysql"
install -d -o 10001 -g 10001 -m 0750 "${COMMUNITY_DATA_ROOT}/uploads"
install -d -o 10001 -g 10001 -m 0750 "${COMMUNITY_DATA_ROOT}/media-worker"
install -d -o root -g root -m 0755 "${COMMUNITY_DATA_ROOT}/acme" "${COMMUNITY_DATA_ROOT}/acme/.well-known" "${COMMUNITY_DATA_ROOT}/acme/.well-known/acme-challenge"
install -d -o root -g root -m 0700 "${COMMUNITY_DATA_ROOT}/backup" "${COMMUNITY_DATA_ROOT}/evidence" "${COMMUNITY_RELEASE_ROOT}" "${COMMUNITY_CONFIG_ROOT}"
install -d -o root -g community-secrets -m 0750 "${COMMUNITY_SECRETS_DIR}"
install -d -o root -g community-tls -m 0750 "${COMMUNITY_TLS_DIR}"

for secret_name in mysql-root-password mysql-app-password jwt-secret; do
  secret_path="${COMMUNITY_SECRETS_DIR}/${secret_name}"
  if [[ ! -e "${secret_path}" ]]; then
    install -o root -g community-secrets -m 0640 /dev/null "${secret_path}"
    echo "Created empty secret file: ${secret_path}"
  else
    chown root:community-secrets "${secret_path}"
    chmod 0640 "${secret_path}"
  fi
done

echo "Directories and groups are ready. Populate secrets and TLS through approved host procedures."
