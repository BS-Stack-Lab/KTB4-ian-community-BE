#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

require_root
require_command apt-get

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates \
  certbot \
  curl \
  gzip \
  mysql-client \
  mysql-server \
  nginx \
  openjdk-21-jdk-headless \
  openssl \
  rsync \
  tar

systemctl enable mysql nginx

java -version
nginx -v
mysql --version
