#!/usr/bin/env bash

set -Eeuo pipefail

TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
METHOD_ROOT="$(cd -- "${TEST_DIR}/.." && pwd)"
source "${METHOD_ROOT}/scripts/lib/common.sh"

valid_origins=(
  "http://127.0.0.1"
  "http://127.0.0.1:8080"
  "https://community.example.com"
  "https://community.example.com:8443"
  "http://[::1]:8080"
)

invalid_origins=(
  "http://127.0.0.1/"
  "https://community.example.com/path"
  "https://community.example.com?query=1"
  "https://community.example.com#fragment"
  "https://community..example.com"
  "https://community.example.com:0"
  "https://community.example.com:65536"
  "ftp://community.example.com"
)

for origin in "${valid_origins[@]}"; do
  if ! is_valid_http_origin "${origin}"; then
    echo "Expected valid origin: ${origin}" >&2
    exit 1
  fi
done

for origin in "${invalid_origins[@]}"; do
  if is_valid_http_origin "${origin}"; then
    echo "Expected invalid origin: ${origin}" >&2
    exit 1
  fi
done

valid_hostnames=(
  "pulse.dynu.com"
  "community-test.example.co.kr"
)

invalid_hostnames=(
  "localhost"
  ".example.com"
  "example.com."
  "community..example.com"
  "-community.example.com"
)

for hostname in "${valid_hostnames[@]}"; do
  if ! is_valid_hostname "${hostname}"; then
    echo "Expected valid hostname: ${hostname}" >&2
    exit 1
  fi
done

for hostname in "${invalid_hostnames[@]}"; do
  if is_valid_hostname "${hostname}"; then
    echo "Expected invalid hostname: ${hostname}" >&2
    exit 1
  fi
done

valid_pulse_hostnames=(
  "pulse.dynu.com"
  "pulse.freeddns.org"
  "pulse.example.co.kr"
)

invalid_pulse_hostnames=(
  "Pulse.dynu.com"
  "community.dynu.com"
  "pulse"
  "pulse..dynu.com"
)

for hostname in "${valid_pulse_hostnames[@]}"; do
  if ! is_hostname_with_label "${hostname}" pulse; then
    echo "Expected hostname with pulse label: ${hostname}" >&2
    exit 1
  fi
done

for hostname in "${invalid_pulse_hostnames[@]}"; do
  if is_hostname_with_label "${hostname}" pulse; then
    echo "Expected hostname without valid pulse label: ${hostname}" >&2
    exit 1
  fi
done

if ! is_valid_sha256_hash \
  "$(printf 'dynu-update-password' | sha256sum | awk '{ print $1 }')"; then
  echo "Expected a valid SHA-256 hash." >&2
  exit 1
fi

if is_valid_sha256_hash "not-a-sha256-hash"; then
  echo "Expected an invalid SHA-256 hash." >&2
  exit 1
fi

for response in "good" "good 203.0.113.10" "nochg" "nochg 203.0.113.10"; do
  if ! is_successful_dynu_response "${response}"; then
    echo "Expected a successful Dynu response: ${response}" >&2
    exit 1
  fi
done

for response in "badauth" "911" "dnserr" "unknown"; do
  if is_successful_dynu_response "${response}"; then
    echo "Expected a failed Dynu response: ${response}" >&2
    exit 1
  fi
done

valid_ipv4_addresses=(
  "1.1.1.1"
  "127.0.0.1"
  "255.255.255.255"
)

invalid_ipv4_addresses=(
  "01.1.1.1"
  "256.1.1.1"
  "999999999999999999999999.1.1.1"
  "1.1.1"
  "2001:db8::1"
)

for address in "${valid_ipv4_addresses[@]}"; do
  if ! is_valid_ipv4 "${address}"; then
    echo "Expected valid IPv4 address: ${address}" >&2
    exit 1
  fi
done

for address in "${invalid_ipv4_addresses[@]}"; do
  if is_valid_ipv4 "${address}"; then
    echo "Expected invalid IPv4 address: ${address}" >&2
    exit 1
  fi
done

temporary_config="$(mktemp /tmp/community-config-test.XXXXXX)"
trap 'rm -f -- "${temporary_config}"' EXIT
printf 'COMMUNITY_DOMAIN=pulse.dynu.com\n' >"${temporary_config}"

if [[ "$(read_config_value "${temporary_config}" COMMUNITY_DOMAIN)" != \
  "pulse.dynu.com" ]]; then
  echo "Expected exact config value lookup." >&2
  exit 1
fi

valid_jwt_secret="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
invalid_jwt_secrets=(
  "not@base64"
  "MTIz"
)

if ! is_valid_jwt_secret "${valid_jwt_secret}"; then
  echo "Expected a valid Base64 JWT secret." >&2
  exit 1
fi

for jwt_secret in "${invalid_jwt_secrets[@]}"; do
  if is_valid_jwt_secret "${jwt_secret}"; then
    echo "Expected an invalid Base64 JWT secret." >&2
    exit 1
  fi
done

valid_loopback_listeners=(
  "127.0.0.1:8080"
  "[::1]:8080"
  "[::ffff:127.0.0.1]:8080"
)

invalid_loopback_listeners=(
  "0.0.0.0:8080"
  "[::]:8080"
  "172.31.1.10:8080"
)

for listener_address in "${valid_loopback_listeners[@]}"; do
  if ! is_loopback_listener_address "${listener_address}" "8080"; then
    echo "Expected a valid loopback listener: ${listener_address}" >&2
    exit 1
  fi
done

for listener_address in "${invalid_loopback_listeners[@]}"; do
  if is_loopback_listener_address "${listener_address}" "8080"; then
    echo "Expected an invalid loopback listener: ${listener_address}" >&2
    exit 1
  fi
done

echo "Common deployment function tests passed."
