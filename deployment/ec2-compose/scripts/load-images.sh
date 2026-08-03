#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command docker
require_command find
require_command realpath
require_command sha256sum

artifact_dir="${ARTIFACT_DIR:?Set ARTIFACT_DIR to the transferred image directory}"
artifact_dir="$(realpath -e -- "${artifact_dir}")"
checksum_file="${artifact_dir}/SHA256SUMS"
require_nonempty_file "${checksum_file}"

(
  cd "${artifact_dir}"
  sha256sum --check SHA256SUMS
)

mapfile -t image_archives < <(
  find "${artifact_dir}" -maxdepth 1 -type f -name '*.tar' -print | sort
)
if [[ "${#image_archives[@]}" -eq 0 ]]; then
  echo "No .tar image archives found in ${artifact_dir}." >&2
  exit 1
fi

for image_archive in "${image_archives[@]}"; do
  docker load --input "${image_archive}"
done

echo "Loaded ${#image_archives[@]} verified image archive(s)."
