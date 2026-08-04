#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
METHOD_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

readonly DYNU_SERVICE_SOURCE="${METHOD_ROOT}/systemd/community-dynu.service"
readonly DYNU_TIMER_SOURCE="${METHOD_ROOT}/systemd/community-dynu.timer"
readonly CERTBOT_HOOK_SOURCE="${METHOD_ROOT}/letsencrypt/reload-nginx.sh"
readonly DYNU_SERVICE_TARGET="/etc/systemd/system/community-dynu.service"
readonly DYNU_TIMER_TARGET="/etc/systemd/system/community-dynu.timer"
readonly CERTBOT_HOOK_TARGET="/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh"

require_root
require_command certbot
require_command curl
require_command getent
require_command nginx
require_command sha256sum
require_file "${ENV_FILE}"
require_file "${DYNU_SERVICE_SOURCE}"
require_file "${DYNU_TIMER_SOURCE}"
require_file "${CERTBOT_HOOK_SOURCE}"

read -r -p \
  "Dynu full hostname created with Host 'pulse' (for example pulse.dynu.com): " \
  dynu_hostname
dynu_hostname="${dynu_hostname,,}"
if ! is_hostname_with_label "${dynu_hostname}" pulse; then
  echo "Dynu hostname must be a valid hostname beginning with pulse." >&2
  exit 1
fi

read -r -p "Let's Encrypt notification email: " letsencrypt_email
if [[ ! "${letsencrypt_email}" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  echo "A valid notification email is required." >&2
  exit 1
fi

read -r -s -p "Dynu separate IP update password: " dynu_ip_update_password
echo
read -r -s -p "Confirm Dynu IP update password: " dynu_ip_update_password_confirm
echo
if [[ "${#dynu_ip_update_password}" -lt 16 ||
  "${dynu_ip_update_password}" != "${dynu_ip_update_password_confirm}" ]]; then
  echo "Dynu IP update password must be at least 16 characters and match." >&2
  exit 1
fi

dynu_password_sha256="$(
  printf '%s' "${dynu_ip_update_password}" |
    sha256sum |
    awk '{ print $1 }'
)"
if ! is_valid_sha256_hash "${dynu_password_sha256}"; then
  echo "Unable to hash the Dynu IP update password." >&2
  exit 1
fi
unset dynu_ip_update_password dynu_ip_update_password_confirm

community_domain="${dynu_hostname}"
temporary_dynu_env="$(mktemp /tmp/community-dynu-env.XXXXXX)"
temporary_domain_env="$(mktemp /tmp/community-domain-env.XXXXXX)"
temporary_backend_env="$(mktemp /tmp/community-backend-env.XXXXXX)"
assert_safe_path "${temporary_dynu_env}"
assert_safe_path "${temporary_domain_env}"
assert_safe_path "${temporary_backend_env}"
cleanup() {
  unset dynu_password_sha256
  unset dynu_ip_update_password dynu_ip_update_password_confirm
  rm -f -- \
    "${temporary_dynu_env}" \
    "${temporary_domain_env}" \
    "${temporary_backend_env}"
}
trap cleanup EXIT

awk -v origin="https://${community_domain}" '
  /^FRONTEND_ORIGIN=/ {
    origin_count += 1
    print "FRONTEND_ORIGIN=" origin
    next
  }
  /^COOKIE_SECURE=/ {
    cookie_count += 1
    print "COOKIE_SECURE=true"
    next
  }
  { print }
  END {
    if (origin_count != 1 || cookie_count != 1) {
      exit 1
    }
  }
' "${ENV_FILE}" >"${temporary_backend_env}" || {
  echo "Backend environment must contain one FRONTEND_ORIGIN and COOKIE_SECURE." >&2
  exit 1
}

{
  printf 'DYNU_HOSTNAME=%s\n' "${dynu_hostname}"
  printf 'DYNU_PASSWORD_SHA256=%s\n' "${dynu_password_sha256}"
} >"${temporary_dynu_env}"
printf 'COMMUNITY_DOMAIN=%s\n' "${community_domain}" \
  >"${temporary_domain_env}"

install -o root -g root -m 0600 \
  "${temporary_dynu_env}" \
  "${DYNU_ENV_FILE}"
install -o root -g root -m 0644 \
  "${temporary_domain_env}" \
  "${DOMAIN_FILE}"
unset dynu_password_sha256

"${SCRIPT_DIR}/install-operations.sh"

install -o root -g root -m 0644 \
  "${DYNU_SERVICE_SOURCE}" \
  "${DYNU_SERVICE_TARGET}"
install -o root -g root -m 0644 \
  "${DYNU_TIMER_SOURCE}" \
  "${DYNU_TIMER_TARGET}"
install -d -o root -g root -m 0755 \
  "$(dirname -- "${CERTBOT_HOOK_TARGET}")"
install -o root -g root -m 0755 \
  "${CERTBOT_HOOK_SOURCE}" \
  "${CERTBOT_HOOK_TARGET}"

systemctl daemon-reload
systemctl enable --now community-dynu.timer
"${SCRIPT_DIR}/update-dynu.sh"

public_ipv4="$(ec2_public_ipv4)" || {
  echo "Unable to read the EC2 public IPv4 address through IMDSv2." >&2
  exit 1
}

dns_ready=false
for _ in {1..30}; do
  if getent ahostsv4 "${community_domain}" |
    awk '{ print $1 }' |
    grep -Fqx "${public_ipv4}"; then
    dns_ready=true
    break
  fi
  sleep 2
done
if [[ "${dns_ready}" != true ]]; then
  echo "Dynu did not resolve to this EC2 public IPv4 within 60 seconds." >&2
  exit 1
fi

"${SCRIPT_DIR}/07-configure-nginx.sh"

certbot certonly \
  --webroot \
  --webroot-path /var/www/letsencrypt \
  --cert-name "${community_domain}" \
  --domain "${community_domain}" \
  --email "${letsencrypt_email}" \
  --agree-tos \
  --no-eff-email \
  --non-interactive \
  --keep-until-expiring

require_file "/etc/letsencrypt/live/${community_domain}/fullchain.pem"
require_file "/etc/letsencrypt/live/${community_domain}/privkey.pem"
"${SCRIPT_DIR}/07-configure-nginx.sh"

install -o root -g "${COMMUNITY_GROUP}" -m 0640 \
  "${temporary_backend_env}" \
  "${ENV_FILE}"

systemctl enable --now certbot.timer
systemctl restart community-backend.service
systemctl reload nginx

certbot renew \
  --cert-name "${community_domain}" \
  --dry-run \
  --run-deploy-hooks

echo "HTTPS configured: https://${community_domain}/"
echo "Run verify.sh, then verify the site from an external browser."
