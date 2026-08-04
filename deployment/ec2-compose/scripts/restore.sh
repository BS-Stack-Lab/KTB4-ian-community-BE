#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command docker
require_command gzip
require_command realpath
require_command sha256sum
require_command tar

if [[ "${RESTORE_CONFIRM:-}" != "restore-community" ]]; then
  echo "Set RESTORE_CONFIRM=restore-community after reviewing the archive." >&2
  exit 1
fi

release_env="${RELEASE_ENV:-${COMMUNITY_RELEASE_ROOT}/current.env}"
restore_archive="${RESTORE_ARCHIVE:?Set RESTORE_ARCHIVE under /data/community/backup}"
archive_real_path="$(realpath -e -- "${restore_archive}")"
case "${archive_real_path}" in
  "${COMMUNITY_DATA_ROOT}"/backup/community-*.tar.gz)
    ;;
  *)
    echo "Restore archive is outside the approved backup directory." >&2
    exit 1
    ;;
esac

validate_release_env "${release_env}"

if tar -tzf "${archive_real_path}" |
  awk 'BEGIN{bad=0} /^\// || /(^|\/)\.\.(\/|$)/ {bad=1} END{exit bad ? 0 : 1}'; then
  echo "Restore archive contains an unsafe path." >&2
  exit 1
fi

restore_stage="$(mktemp -d /tmp/community-restore.XXXXXX)"
assert_safe_path "${restore_stage}"
trap 'rm -rf -- "${restore_stage}"' EXIT

tar -xzf "${archive_real_path}" -C "${restore_stage}"
require_nonempty_file "${restore_stage}/database.sql.gz"
require_nonempty_file "${restore_stage}/uploads.tar.gz"
require_nonempty_file "${restore_stage}/SHA256SUMS"
(
  cd "${restore_stage}"
  sha256sum --check SHA256SUMS
)

compose_cmd "${release_env}" stop backend

if ! gzip -dc "${restore_stage}/database.sql.gz" |
  compose_cmd "${release_env}" exec -T mysql sh -ec '
    export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"
    exec mysql \
      --user=root \
      --database="$MYSQL_DATABASE"
  '; then
  echo "Database restore failed; backend remains stopped." >&2
  exit 1
fi

previous_uploads="${COMMUNITY_DATA_ROOT}/uploads.pre-restore-$(timestamp)"
assert_safe_path "${previous_uploads}"
mv "${COMMUNITY_DATA_ROOT}/uploads" "${previous_uploads}"
install -d -o 10001 -g 10001 -m 0750 \
  "${COMMUNITY_DATA_ROOT}/uploads"

if ! tar -xzf "${restore_stage}/uploads.tar.gz" \
  --no-same-owner \
  -C "${COMMUNITY_DATA_ROOT}"; then
  rm -rf -- "${COMMUNITY_DATA_ROOT}/uploads"
  mv "${previous_uploads}" "${COMMUNITY_DATA_ROOT}/uploads"
  echo "Upload restore failed; previous uploads were put back." >&2
  exit 1
fi

chown -R 10001:10001 "${COMMUNITY_DATA_ROOT}/uploads"
find "${COMMUNITY_DATA_ROOT}/uploads" -type d -exec chmod 0750 {} +
find "${COMMUNITY_DATA_ROOT}/uploads" -type f -exec chmod 0640 {} +

compose_cmd "${release_env}" up --detach --wait --wait-timeout 180 backend

echo "Restore completed. Previous uploads remain at ${previous_uploads}."
