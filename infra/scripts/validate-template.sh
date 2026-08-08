#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
template_file="${1:-${script_dir}/../cloudformation/media-v2.yaml}"

if command -v cfn-lint >/dev/null 2>&1; then
  cfn-lint "${template_file}"
else
  echo "cfn-lint is not installed; skipping local lint" >&2
fi

aws cloudformation validate-template --template-body "file://${template_file}"
