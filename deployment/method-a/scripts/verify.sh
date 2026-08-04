#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

require_root
require_command curl
require_command getent
require_command openssl
require_command ss
require_command stat
require_file "${ENV_FILE}"

failures=0

pass() {
  echo "PASS: $1"
}

fail() {
  echo "FAIL: $1" >&2
  failures=$((failures + 1))
}

for service_name in mysql community-backend nginx; do
  if systemctl is-active --quiet "${service_name}"; then
    pass "${service_name} is active"
  else
    fail "${service_name} is not active"
  fi
done

if nginx -t >/dev/null 2>&1; then
  pass "Nginx configuration syntax"
else
  fail "Nginx configuration syntax"
fi

listener_is_loopback_only() {
  local port="$1"
  local listener_address
  local listener_found=false

  while IFS= read -r listener_address; do
    listener_found=true
    if ! is_loopback_listener_address "${listener_address}" "${port}"; then
      return 1
    fi
  done < <(
    ss -ltnH |
      awk -v port="${port}" '$4 ~ (":" port "$") { print $4 }'
  )

  [[ "${listener_found}" == true ]]
}

if listener_is_loopback_only 8080; then
  pass "Spring Boot listens only on loopback port 8080"
else
  fail "Spring Boot listener is missing or externally bound"
fi

if listener_is_loopback_only 3306; then
  pass "MySQL listens only on loopback port 3306"
else
  fail "MySQL listener is missing or externally bound"
fi

if curl --fail --silent --show-error \
  --output /dev/null \
  http://127.0.0.1/healthz; then
  pass "Nginx health endpoint"
else
  fail "Nginx health endpoint"
fi

h2_status="$(
  curl --silent --output /dev/null --write-out '%{http_code}' \
    http://127.0.0.1:8080/h2-console
)"
if [[ "${h2_status}" == "200" || "${h2_status}" == "302" ]]; then
  fail "H2 Console appears accessible in the AWS profile"
else
  pass "H2 Console is not accessible"
fi

for required_key in \
  SPRING_PROFILES_ACTIVE \
  DB_URL \
  DB_USERNAME \
  DB_PASSWORD \
  JWT_SECRET \
  FRONTEND_ORIGIN; do
  if grep -Eq "^${required_key}=.+" "${ENV_FILE}"; then
    pass "${required_key}=SET"
  else
    fail "${required_key}=MISSING"
  fi
done

if file_has_exact_metadata \
  "${ENV_FILE}" \
  root \
  "${COMMUNITY_GROUP}" \
  640; then
  pass "Environment file ownership and permissions"
else
  fail "Environment file must be root:community with mode 640"
fi

if find "${COMMUNITY_ROOT}/uploads" -perm -0002 -print -quit |
  grep -q .; then
  fail "Uploads contain world-writable paths"
else
  pass "Uploads are not world-writable"
fi

if [[ -f "${DOMAIN_FILE}" ]]; then
  community_domain="$(read_config_value "${DOMAIN_FILE}" COMMUNITY_DOMAIN)" || {
    community_domain=""
    fail "Domain configuration contains exactly one non-empty value"
  }

  if [[ -n "${community_domain}" ]] &&
    is_valid_hostname "${community_domain}"; then
    pass "Community domain format"
  else
    fail "Community domain format"
  fi

  if file_has_exact_metadata "${DOMAIN_FILE}" root root 644; then
    pass "Domain configuration ownership and permissions"
  else
    fail "Domain configuration must be root:root with mode 644"
  fi

  if file_has_exact_metadata "${DYNU_ENV_FILE}" root root 600; then
    pass "Dynu credential ownership and permissions"
  else
    fail "Dynu credentials must be root:root with mode 600"
  fi

  if [[ -f "${DYNU_ENV_FILE}" ]]; then
    dynu_hostname="$(
      read_config_value "${DYNU_ENV_FILE}" DYNU_HOSTNAME
    )" || dynu_hostname=""
    dynu_password_sha256="$(
      read_config_value "${DYNU_ENV_FILE}" DYNU_PASSWORD_SHA256
    )" || dynu_password_sha256=""

    if [[ "${dynu_hostname}" == "${community_domain}" ]] &&
      is_hostname_with_label "${dynu_hostname}" pulse &&
      is_valid_sha256_hash "${dynu_password_sha256}"; then
      pass "Dynu pulse hostname and password hash configuration"
    else
      fail "Dynu hostname or password hash configuration"
    fi
    unset dynu_password_sha256
  fi

  for timer_name in community-dynu.timer certbot.timer; do
    if systemctl is-active --quiet "${timer_name}"; then
      pass "${timer_name} is active"
    else
      fail "${timer_name} is not active"
    fi
  done

  if [[ -n "${community_domain}" ]]; then
    certificate_path="/etc/letsencrypt/live/${community_domain}/cert.pem"
    if [[ -f "${certificate_path}" ]] &&
      openssl x509 -checkend 604800 -noout \
        -in "${certificate_path}" >/dev/null 2>&1; then
      pass "TLS certificate is valid for more than seven days"
    else
      fail "TLS certificate is missing or expires within seven days"
    fi

    if grep -Fqx \
      "FRONTEND_ORIGIN=https://${community_domain}" \
      "${ENV_FILE}" &&
      grep -Fqx "COOKIE_SECURE=true" "${ENV_FILE}"; then
      pass "Backend origin and secure cookie use HTTPS"
    else
      fail "Backend origin or secure cookie does not match HTTPS domain"
    fi

    if curl --fail --silent --show-error \
      --output /dev/null \
      --resolve "${community_domain}:443:127.0.0.1" \
      "https://${community_domain}/healthz"; then
      pass "HTTPS health endpoint and certificate trust"
    else
      fail "HTTPS health endpoint or certificate trust"
    fi

    redirect_result="$(
      curl --silent --show-error \
        --output /dev/null \
        --write-out '%{http_code} %{redirect_url}' \
        --resolve "${community_domain}:80:127.0.0.1" \
        "http://${community_domain}/healthz"
    )" || redirect_result=""
    if [[ "${redirect_result}" == \
      "301 https://${community_domain}/healthz" ]]; then
      pass "HTTP redirects to the canonical HTTPS domain"
    else
      fail "HTTP does not redirect to the canonical HTTPS domain"
    fi

    current_public_ipv4="$(ec2_public_ipv4)" || current_public_ipv4=""
    if [[ -n "${current_public_ipv4}" ]] &&
      getent ahostsv4 "${community_domain}" |
        awk '{ print $1 }' |
        grep -Fqx "${current_public_ipv4}"; then
      pass "Dynu resolves to the current EC2 public IPv4"
    else
      fail "Dynu does not resolve to the current EC2 public IPv4"
    fi
  fi
fi

if [[ "${failures}" -ne 0 ]]; then
  echo "Verification failed: ${failures} control(s)." >&2
  exit 1
fi

echo "Local EC2 verification controls passed."
echo "Security Group, EBS encryption, and IMDSv2 still require Console evidence."
