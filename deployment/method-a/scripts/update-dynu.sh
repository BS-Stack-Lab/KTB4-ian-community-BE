#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

require_root
require_command curl
require_file "${DOMAIN_FILE}"
require_file "${DYNU_ENV_FILE}"

if ! file_has_exact_metadata \
  "${DYNU_ENV_FILE}" \
  root \
  root \
  600; then
  echo "Dynu credentials must be root:root with mode 600." >&2
  exit 1
fi

dynu_hostname="$(
  read_config_value "${DYNU_ENV_FILE}" DYNU_HOSTNAME
)" || {
  echo "Missing Dynu hostname configuration." >&2
  exit 1
}
dynu_password_sha256="$(
  read_config_value "${DYNU_ENV_FILE}" DYNU_PASSWORD_SHA256
)" || {
  echo "Missing Dynu IP update password hash." >&2
  exit 1
}
community_domain="$(
  read_config_value "${DOMAIN_FILE}" COMMUNITY_DOMAIN
)" || {
  echo "Missing community domain configuration." >&2
  exit 1
}

if ! is_hostname_with_label "${dynu_hostname}" pulse; then
  echo "Dynu hostname must be a valid hostname beginning with pulse." >&2
  exit 1
fi
if [[ "${community_domain}" != "${dynu_hostname}" ]]; then
  echo "Dynu hostname and community domain do not match." >&2
  exit 1
fi
if ! is_valid_sha256_hash "${dynu_password_sha256}"; then
  echo "Invalid Dynu IP update password hash." >&2
  exit 1
fi

public_ipv4="$(ec2_public_ipv4)" || {
  echo "Unable to read the EC2 public IPv4 address through IMDSv2." >&2
  exit 1
}

response="$(
  {
    printf 'url = "https://api.dynu.com/nic/update"\n'
    printf 'get\n'
    printf 'data-urlencode = "hostname=%s"\n' "${dynu_hostname}"
    printf 'data-urlencode = "myip=%s"\n' "${public_ipv4}"
    printf 'data-urlencode = "myipv6=no"\n'
    printf 'data-urlencode = "password=%s"\n' "${dynu_password_sha256}"
  } | curl --fail --silent --show-error \
    --connect-timeout 5 \
    --max-time 20 \
    --user-agent 'community-method-a-ddns/1.0' \
    --config -
)" || {
  echo "Dynu update request failed." >&2
  exit 1
}

unset dynu_password_sha256
response="${response//$'\r'/}"
if is_successful_dynu_response "${response}"; then
  echo "Dynu record update succeeded for ${dynu_hostname}."
else
  case "${response}" in
    911 | 911\ *)
      echo "Dynu is temporarily unavailable; retry after at least ten minutes." >&2
      exit 1
      ;;
    *)
      echo "Dynu rejected the update request." >&2
      exit 1
      ;;
  esac
fi
