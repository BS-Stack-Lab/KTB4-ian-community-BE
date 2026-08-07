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
done < <(grep -RhE '^[[:space:]]*-[[:space:]]+uses:' "${root}/.github/workflows")

if grep -RInE 'pull_request_target|:[[:space:]]*latest([[:space:]]|$)' "${root}/.github/workflows"; then
  echo "Forbidden workflow trigger or mutable image tag found." >&2
  failures=$((failures + 1))
fi
if grep -RInE 'merge_group:|repository_dispatch:|workflow_call:|PRODUCTION_DEPLOY_ENABLED|PRODUCTION_DISPATCH_ENABLED|BACKEND_DISPATCH_TOKEN' "${root}/.github/workflows"; then
  echo "Unsupported merge queue or automatic production deployment behavior found." >&2
  failures=$((failures + 1))
fi
ci_workflow="${root}/.github/workflows/ci.yml"
if ! grep -qE '^  pull_request:$' "${ci_workflow}" ||
  ! grep -qE '^    types: \[opened, synchronize, reopened, ready_for_review\]$' "${ci_workflow}" ||
  ! grep -qE '^  push:$' "${ci_workflow}" ||
  grep -qE '^  workflow_dispatch:$' "${ci_workflow}"; then
  echo "Backend CI must run automatically for main pull requests and main pushes only." >&2
  failures=$((failures + 1))
fi
if ! grep -qE '^    name: BE / required-gate$' "${ci_workflow}"; then
  echo "Backend required gate name changed." >&2
  failures=$((failures + 1))
fi
if ! grep -qE 'ghcr\.io/bs-stack-lab/ktb4-ian-community-be' "${root}/.github/workflows/publish-image.yml"; then
  echo "Backend publisher does not target the personal GHCR namespace." >&2
  failures=$((failures + 1))
fi
deploy_workflow="${root}/.github/workflows/deploy-production.yml"
if ! grep -qE '^  workflow_dispatch:$' "${deploy_workflow}" ||
  grep -qE '^  (push|pull_request|workflow_run|repository_dispatch|workflow_call|schedule):' "${deploy_workflow}"; then
  echo "Production deployment must remain manual-only." >&2
  failures=$((failures + 1))
fi
if ! grep -qF 'gh attestation verify' "${deploy_workflow}" ||
  ! grep -qF -- '--source-ref refs/heads/main' "${deploy_workflow}" ||
  ! grep -qF -- "--source-digest \"\${source_sha}\"" "${deploy_workflow}" ||
  ! grep -qF 'name: production' "${deploy_workflow}"; then
  echo "Production deployment must verify main provenance before Environment approval." >&2
  failures=$((failures + 1))
fi
[[ "${failures}" -eq 0 ]] || exit 1
echo "PASS: Backend workflows enforce PR CI, manual deployment, and pre-approval provenance checks."
