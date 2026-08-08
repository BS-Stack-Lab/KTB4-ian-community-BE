#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

test_root="$(mktemp -d /tmp/community-release-compat.XXXXXX)"
trap 'rm -rf -- "${test_root}"' EXIT
legacy_root="${test_root}/bootstrap"
archive_root="${test_root}/archive"
legacy_env="${test_root}/legacy.env"
registry_env="${test_root}/registry.env"
install -d -m 0700 "${legacy_root}"
printf 'name: legacy\n' >"${legacy_root}/compose.yaml"

cat >"${legacy_env}" <<'EOF'
FRONTEND_IMAGE=community-frontend:legacy
BACKEND_IMAGE=community-backend:legacy
MYSQL_IMAGE=mysql:legacy
EOF

if ALLOW_LOCAL_IMAGES=1 ALLOW_HTTP_ORIGIN=1 is_registry_release_env "${legacy_env}"; then
  echo "FAIL: legacy release state was accepted as a registry release." >&2
  exit 1
fi

preserve_legacy_compose "${legacy_root}" "${legacy_env}" "${archive_root}" >/dev/null
preserved_root="$(env_value "${legacy_env}" LEGACY_COMPOSE_ROOT)"
[[ -f "${preserved_root}/compose.yaml" ]]
[[ "$(<"${preserved_root}/compose.yaml")" == 'name: legacy' ]]
preserve_legacy_compose "${legacy_root}" "${legacy_env}" "${archive_root}" >/dev/null
[[ "$(find "${archive_root}" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')" == 1 ]]

cat >"${registry_env}" <<'EOF'
FRONTEND_IMAGE=community-frontend:test
BACKEND_IMAGE=community-backend:test
MYSQL_IMAGE=mysql:8.4.11@sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0
NGINX_IMAGE=nginx:1.28.3-alpine3.23@sha256:0dcc88822d45581e65ae329f8be769762bf628d3b2bb7d2a077d4aa5c98b30e3
FRONTEND_ORIGIN=http://127.0.0.1:18080
FRONTEND_COMMIT=1111111111111111111111111111111111111111
BACKEND_COMMIT=2222222222222222222222222222222222222222
CONFIG_SHA=3333333333333333333333333333333333333333
COMPOSE_ROOT=/tmp/community-release-compat/config
MEDIA_V2_ENABLED=true
MEDIA_BUCKET=community-media-test
MEDIA_QUEUE_URL=https://sqs.ap-northeast-2.amazonaws.com/000000000000/community-media-test
MEDIA_API_ROLE_ARN=arn:aws:iam::000000000000:role/community-media-api
MEDIA_WORKER_ROLE_ARN=arn:aws:iam::000000000000:role/community-media-worker
MEDIA_CDN_BASE_URL=https://example.cloudfront.net
MEDIA_DISTRIBUTION_ID=E0000000000000
MEDIA_ENVIRONMENT=test
MEDIA_TRANSFORM_VERSION=1
EOF

ALLOW_LOCAL_IMAGES=1 ALLOW_HTTP_ORIGIN=1 is_registry_release_env "${registry_env}"
set_env_value "${registry_env}" NGINX_IMAGE nginx:mutable
if ALLOW_LOCAL_IMAGES=1 ALLOW_HTTP_ORIGIN=1 is_registry_release_env "${registry_env}"; then
  echo "FAIL: invalid registry release state was accepted." >&2
  exit 1
fi

echo "PASS: legacy release state is isolated and its Compose assets are preserved once."
