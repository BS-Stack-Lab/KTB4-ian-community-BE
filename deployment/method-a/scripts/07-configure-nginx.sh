#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
METHOD_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

readonly NGINX_SOURCE="${METHOD_ROOT}/nginx/community.conf"
readonly NGINX_SNIPPET_SOURCE="${METHOD_ROOT}/nginx/community-app.conf"
readonly NGINX_HTTP_TEMPLATE="${METHOD_ROOT}/nginx/community-domain-http.conf.template"
readonly NGINX_HTTPS_TEMPLATE="${METHOD_ROOT}/nginx/community-domain-https.conf.template"
readonly NGINX_AVAILABLE="/etc/nginx/sites-available/community.conf"
readonly NGINX_ENABLED="/etc/nginx/sites-enabled/community.conf"
readonly NGINX_SNIPPET="/etc/nginx/snippets/community-app.conf"

require_root
require_command nginx
require_file "${NGINX_SOURCE}"
require_file "${NGINX_SNIPPET_SOURCE}"

install -d -o root -g root -m 0755 /var/www/letsencrypt
install -o root -g root -m 0644 \
  "${NGINX_SNIPPET_SOURCE}" \
  "${NGINX_SNIPPET}"

temporary_config="$(mktemp /tmp/community-nginx.XXXXXX)"
temporary_previous="$(mktemp /tmp/community-nginx-previous.XXXXXX)"
assert_safe_path "${temporary_config}"
assert_safe_path "${temporary_previous}"
cleanup() {
  rm -f -- "${temporary_config}" "${temporary_previous}"
}
trap cleanup EXIT

if [[ -f "${DOMAIN_FILE}" ]]; then
  community_domain="$(read_config_value "${DOMAIN_FILE}" COMMUNITY_DOMAIN)" || {
    echo "Invalid domain configuration: ${DOMAIN_FILE}" >&2
    exit 1
  }
  if ! is_valid_hostname "${community_domain}"; then
    echo "Invalid community domain: ${community_domain}" >&2
    exit 1
  fi

  if [[ -f "/etc/letsencrypt/live/${community_domain}/fullchain.pem" &&
    -f "/etc/letsencrypt/live/${community_domain}/privkey.pem" ]]; then
    require_file "${NGINX_HTTPS_TEMPLATE}"
    sed "s/__COMMUNITY_DOMAIN__/${community_domain}/g" \
      "${NGINX_HTTPS_TEMPLATE}" >"${temporary_config}"
    echo "Rendering HTTPS Nginx configuration for ${community_domain}."
  else
    require_file "${NGINX_HTTP_TEMPLATE}"
    sed "s/__COMMUNITY_DOMAIN__/${community_domain}/g" \
      "${NGINX_HTTP_TEMPLATE}" >"${temporary_config}"
    echo "Rendering temporary HTTP Nginx configuration for certificate issuance."
  fi
else
  cp "${NGINX_SOURCE}" "${temporary_config}"
fi

previous_config_exists=false
if [[ -f "${NGINX_AVAILABLE}" ]]; then
  cp "${NGINX_AVAILABLE}" "${temporary_previous}"
  previous_config_exists=true
fi

install -o root -g root -m 0644 \
  "${temporary_config}" \
  "${NGINX_AVAILABLE}"
ln -sfn "${NGINX_AVAILABLE}" "${NGINX_ENABLED}"

default_site_target=""
if [[ -L /etc/nginx/sites-enabled/default ]]; then
  default_site_target="$(readlink /etc/nginx/sites-enabled/default)"
  unlink /etc/nginx/sites-enabled/default
fi

if ! nginx -t; then
  if [[ "${previous_config_exists}" == true ]]; then
    install -o root -g root -m 0644 \
      "${temporary_previous}" \
      "${NGINX_AVAILABLE}"
  else
    unlink "${NGINX_ENABLED}" || true
    unlink "${NGINX_AVAILABLE}" || true
  fi
  if [[ -n "${default_site_target}" ]]; then
    ln -s "${default_site_target}" /etc/nginx/sites-enabled/default
  fi
  echo "Nginx configuration validation failed; the previous site was restored." >&2
  exit 1
fi
systemctl enable nginx
systemctl reload nginx

echo "Nginx configuration installed and reloaded."
