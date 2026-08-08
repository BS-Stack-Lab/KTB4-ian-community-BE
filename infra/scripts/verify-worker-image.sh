#!/usr/bin/env bash
set -Eeuo pipefail

: "${WORKER_IMAGE:?Set WORKER_IMAGE to the immutable Backend image reference}"

image_user="$(docker image inspect "${WORKER_IMAGE}" --format '{{.Config.User}}')"
if [[ "${image_user}" != "community:community" && "${image_user}" != "10001:10001" ]]; then
  echo "Worker image must default to the non-root community user: ${image_user}" >&2
  exit 1
fi

docker run --rm \
  --read-only \
  --user 10001:10001 \
  --cpus 0.5 \
  --memory 512m \
  --tmpfs /var/lib/community/media-worker:rw,noexec,nosuid,nodev,size=384m,uid=10001,gid=10001 \
  --entrypoint sh \
  "${WORKER_IMAGE}" \
  -ec '
    scratch=/var/lib/community/media-worker
    identify -list policy | grep -q "rights: None"
    printf "%s" "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=" | base64 -d >"${scratch}/input.png"
    convert "${scratch}/input.png" -resize "1x1!" -strip -colorspace sRGB -quality 82 "${scratch}/output.webp"
    test "$(identify -format %m "${scratch}/output.webp")" = WEBP
    if convert "https://example.com/image.png" "${scratch}/blocked.webp" 2>/dev/null; then
      echo "ImageMagick network/delegate access was not blocked" >&2
      exit 1
    fi
    mkdir "${scratch}/media-stale"
    rmdir "${scratch}/media-stale"
    test ! -e "${scratch}/media-stale"
  '

echo "Worker image policy, non-root user, resource limits, and scratch access passed."
