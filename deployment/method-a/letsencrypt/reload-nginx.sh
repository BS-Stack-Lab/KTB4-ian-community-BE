#!/usr/bin/env sh

set -eu

if ! nginx_test_output="$(nginx -t 2>&1)"; then
  printf '%s\n' "${nginx_test_output}" >&2
  exit 1
fi
unset nginx_test_output
systemctl reload nginx
