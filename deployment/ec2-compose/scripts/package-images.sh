#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_command docker
require_command mkdir

release_env="${RELEASE_ENV:?Set RELEASE_ENV to the completed Compose environment file}"
output_dir="${OUTPUT_DIR:-${SCRIPT_DIR}/../artifacts}"
validate_release_env "${release_env}"

mysql_image="$(env_value "${release_env}" MYSQL_IMAGE)"
if ! docker image inspect "${mysql_image}" >/dev/null 2>&1; then
  docker pull --platform linux/amd64 "${mysql_image}"
fi

validate_local_images "${release_env}"
mkdir -p "${output_dir}"

checksum_manifest="${output_dir}/SHA256SUMS"
: >"${checksum_manifest}"

for key in FRONTEND_IMAGE BACKEND_IMAGE MYSQL_IMAGE; do
  image="$(env_value "${release_env}" "${key}")"
  archive_name="${image//\//-}"
  archive_name="${archive_name//:/-}.tar"
  archive_path="${output_dir}/${archive_name}"
  docker save --output "${archive_path}" "${image}"
  if command -v sha256sum >/dev/null 2>&1; then
    (
      cd "${output_dir}"
      sha256sum "${archive_name}"
    ) >>"${checksum_manifest}"
  else
    (
      cd "${output_dir}"
      shasum -a 256 "${archive_name}"
    ) >>"${checksum_manifest}"
  fi
done

echo "Packaged linux/amd64 image archives under ${output_dir}."
echo "Transfer the .tar files and SHA256SUMS together; do not add them to Git."
