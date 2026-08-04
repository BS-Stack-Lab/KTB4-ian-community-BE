#!/usr/bin/env bash

set -Eeuo pipefail

readonly COMMUNITY_USER="community"
readonly COMMUNITY_GROUP="community"
readonly COMMUNITY_ROOT="/var/lib/community"
readonly BACKEND_ROOT="/opt/community/backend"
readonly FRONTEND_ROOT="/opt/community/frontend"
readonly OPERATIONS_ROOT="/opt/community/deployment/method-a"
readonly ENV_FILE="/etc/community/backend.env"
readonly DOMAIN_FILE="/etc/community/domain.env"
readonly DYNU_ENV_FILE="/etc/community/dynu.env"

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    echo "This script must be run as root." >&2
    exit 1
  fi
}

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command not found: ${command_name}" >&2
    exit 1
  fi
}

require_file() {
  local file_path="$1"
  if [[ ! -f "${file_path}" ]]; then
    echo "Required file not found: ${file_path}" >&2
    exit 1
  fi
}

file_has_exact_metadata() {
  local file_path="$1"
  local expected_owner="$2"
  local expected_group="$3"
  local expected_mode="$4"
  local actual_metadata

  actual_metadata="$(
    stat -c '%U:%G:%a' "${file_path}" 2>/dev/null
  )" || return 1

  [[ "${actual_metadata}" == \
    "${expected_owner}:${expected_group}:${expected_mode}" ]]
}

is_valid_http_origin() {
  local origin="$1"
  local authority
  local host
  local port=""

  if [[ ! "${origin}" =~ ^https?://([^/?#[:space:]]+)$ ]]; then
    return 1
  fi
  authority="${BASH_REMATCH[1]}"

  if [[ "${authority}" =~ ^\[([0-9A-Fa-f:]+)\](:(.+))?$ ]]; then
    host="${BASH_REMATCH[1]}"
    port="${BASH_REMATCH[3]:-}"
    [[ "${host}" == *:* ]] || return 1
  elif [[ "${authority}" =~ ^([A-Za-z0-9.-]+)(:([0-9]+))?$ ]]; then
    host="${BASH_REMATCH[1]}"
    port="${BASH_REMATCH[3]:-}"
    if [[ "${host}" == .* ||
      "${host}" == *. ||
      "${host}" == -* ||
      "${host}" == *- ||
      "${host}" == *..* ]]; then
      return 1
    fi
  else
    return 1
  fi

  if [[ -n "${port}" ]]; then
    [[ "${port}" =~ ^[0-9]{1,5}$ ]] || return 1
    local port_number=$((10#${port}))
    ((port_number >= 1 && port_number <= 65535)) || return 1
  fi
}

is_valid_hostname() {
  local hostname="$1"
  local label
  local -a labels

  if [[ -z "${hostname}" || "${#hostname}" -gt 253 ||
    ! "${hostname}" =~ ^[A-Za-z0-9.-]+$ ||
    "${hostname}" == .* || "${hostname}" == *. ||
    "${hostname}" == *..* ]]; then
    return 1
  fi

  IFS='.' read -r -a labels <<<"${hostname}"
  if [[ "${#labels[@]}" -lt 2 ]]; then
    return 1
  fi

  for label in "${labels[@]}"; do
    if [[ -z "${label}" || "${#label}" -gt 63 ||
      "${label}" == -* || "${label}" == *- ]]; then
      return 1
    fi
  done
}

is_hostname_with_label() {
  local hostname="$1"
  local expected_label="$2"

  is_valid_hostname "${hostname}" &&
    [[ "${expected_label}" =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$ ]] &&
    [[ "${hostname%%.*}" == "${expected_label}" ]]
}

is_valid_sha256_hash() {
  local value="$1"

  [[ "${value}" =~ ^[0-9a-f]{64}$ ]]
}

is_successful_dynu_response() {
  local response="$1"

  case "${response}" in
    good | good\ * | nochg | nochg\ *)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_valid_ipv4() {
  local address="$1"
  local octet
  local -a octets

  if [[ ! "${address}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    return 1
  fi

  IFS='.' read -r -a octets <<<"${address}"
  for octet in "${octets[@]}"; do
    if [[ "${#octet}" -gt 3 ]] ||
      [[ "${#octet}" -gt 1 && "${octet}" == 0* ]] ||
      ((10#${octet} > 255)); then
      return 1
    fi
  done
}

read_config_value() {
  local file_path="$1"
  local key="$2"

  awk -v key="${key}" '
    index($0, key "=") == 1 {
      count += 1
      value = substr($0, length(key) + 2)
    }
    END {
      if (count != 1 || value == "") {
        exit 1
      }
      print value
    }
  ' "${file_path}"
}

ec2_public_ipv4() {
  local metadata_token
  local public_ipv4

  metadata_token="$(
    curl --fail --silent --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      --noproxy '*' \
      --request PUT \
      --header 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
      http://169.254.169.254/latest/api/token
  )" || return 1

  public_ipv4="$(
    curl --fail --silent --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      --noproxy '*' \
      --header "X-aws-ec2-metadata-token: ${metadata_token}" \
      http://169.254.169.254/latest/meta-data/public-ipv4
  )" || return 1

  is_valid_ipv4 "${public_ipv4}" || return 1
  printf '%s\n' "${public_ipv4}"
}

is_valid_jwt_secret() {
  local encoded_secret="$1"
  local decoded_length
  local decode_flag

  if base64 --decode </dev/null >/dev/null 2>&1; then
    decode_flag="--decode"
  elif base64 -D </dev/null >/dev/null 2>&1; then
    decode_flag="-D"
  else
    return 1
  fi

  decoded_length="$(
    printf '%s' "${encoded_secret}" |
      base64 "${decode_flag}" 2>/dev/null |
      wc -c |
      tr -d '[:space:]'
  )" || return 1

  [[ "${decoded_length}" =~ ^[0-9]+$ ]] || return 1
  ((decoded_length >= 32))
}

assert_safe_path() {
  local candidate="$1"
  case "${candidate}" in
    /opt/community/* | /var/lib/community/* | /etc/community/* | /tmp/community-*)
      ;;
    *)
      echo "Refusing unsafe path: ${candidate}" >&2
      exit 1
      ;;
  esac
}

timestamp() {
  date -u '+%Y%m%dT%H%M%SZ'
}

is_loopback_listener_address() {
  local listener_address="$1"
  local port="$2"

  case "${listener_address}" in
    "127.0.0.1:${port}" | "[::1]:${port}" | \
      "[::ffff:127.0.0.1]:${port}")
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}
