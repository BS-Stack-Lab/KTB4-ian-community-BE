#!/usr/bin/env bash

set -Eeuo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
failures=0
while IFS= read -r use_line; do
  [[ "${use_line}" == *'uses: ./'* ]] && continue
  if [[ ! "${use_line}" =~ @[0-9a-f]{40}[[:space:]]*#[[:space:]]*v[0-9] ]]; then
    echo "Unpinned or undocumented external Action: ${use_line}" >&2
    failures=$((failures + 1))
  fi
done < <(rg -N '^[[:space:]]*-[[:space:]]+uses:' "${root}/.github/workflows")

if rg -n 'pull_request_target|:[[:space:]]*latest([[:space:]]|$)' "${root}/.github/workflows"; then
  echo "Forbidden workflow trigger or mutable image tag found." >&2
  failures=$((failures + 1))
fi
if rg -n 'pull_request:|merge_group:|repository_dispatch:|workflow_call:|PRODUCTION_DEPLOY_ENABLED|PRODUCTION_DISPATCH_ENABLED|BACKEND_DISPATCH_TOKEN' "${root}/.github/workflows"; then
  echo "PR-only or automatic production deployment behavior found." >&2
  failures=$((failures + 1))
fi
if ! rg -q 'ghcr\.io/bs-stack-lab/ktb4-ian-community-be' "${root}/.github/workflows/publish-image.yml"; then
  echo "Backend publisher does not target the personal GHCR namespace." >&2
  failures=$((failures + 1))
fi
if ! rg -q '^  workflow_dispatch:$' "${root}/.github/workflows/deploy-production.yml"; then
  echo "Production deployment must remain manual-only." >&2
  failures=$((failures + 1))
fi
[[ "${failures}" -eq 0 ]] || exit 1
echo "PASS: external Actions use full SHAs and workflows avoid forbidden patterns."
