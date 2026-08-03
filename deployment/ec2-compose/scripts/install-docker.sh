#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_root
require_command apt-get
require_command dpkg

architecture="$(dpkg --print-architecture)"
if [[ "${architecture}" != "amd64" ]]; then
  echo "Expected Ubuntu amd64 for t3a.small, found ${architecture}." >&2
  exit 1
fi

source /etc/os-release
if [[ "${ID:-}" != "ubuntu" || "${VERSION_ID:-}" != "24.04" ]]; then
  echo "This installer supports Ubuntu 24.04 only." >&2
  exit 1
fi

apt-get update
apt-get install --yes ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl --fail --silent --show-error --location \
  https://download.docker.com/linux/ubuntu/gpg \
  --output /etc/apt/keyrings/docker.asc
chmod 0644 /etc/apt/keyrings/docker.asc

cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${VERSION_CODENAME}
Components: stable
Architectures: ${architecture}
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt-get update
apt-get install --yes \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

systemctl enable --now docker
docker --version
docker compose version

echo "Docker is installed. Continue with prepare-directories.sh."
