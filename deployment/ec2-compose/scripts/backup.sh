#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command docker
require_command gzip
require_command sha256sum
require_command tar

release_env="${RELEASE_ENV:-${COMMUNITY_RELEASE_ROOT}/current.env}"
backup_root="${COMMUNITY_DATA_ROOT}/backup"
backup_id="$(timestamp)"
stage_path="${backup_root}/.stage-${backup_id}"
archive_path="${backup_root}/community-${backup_id}.tar.gz"

validate_release_env "${release_env}"
assert_safe_path "${stage_path}"
assert_safe_path "${archive_path}"
install -d -o root -g root -m 0700 "${stage_path}"

cleanup() {
  rm -rf -- "${stage_path}"
}
trap cleanup EXIT

compose_cmd "${release_env}" exec -T mysql sh -ec '
  export MYSQL_PWD="$(cat /run/secrets/mysql-app-password)"
  exec mysqldump \
    --user="$MYSQL_USER" \
    --single-transaction \
    --no-tablespaces \
    --databases "$MYSQL_DATABASE"
' | gzip -9 >"${stage_path}/database.sql.gz"

tar -czf "${stage_path}/uploads.tar.gz" \
  -C "${COMMUNITY_DATA_ROOT}" uploads

(
  cd "${stage_path}"
  sha256sum database.sql.gz uploads.tar.gz >SHA256SUMS
)

tar -czf "${archive_path}" \
  -C "${stage_path}" \
  database.sql.gz uploads.tar.gz SHA256SUMS
chmod 0600 "${archive_path}"

echo "Backup created: ${archive_path}"
echo "The archive contains private data; keep it out of Git and public evidence."
