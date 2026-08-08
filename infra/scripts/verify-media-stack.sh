#!/usr/bin/env bash
set -Eeuo pipefail

: "${STACK_NAME:?Set STACK_NAME}"

output() {
  aws cloudformation describe-stacks \
    --stack-name "${STACK_NAME}" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue | [0]" \
    --output text
}

bucket="$(output MediaBucketName)"
queue_url="$(output MediaQueueUrl)"
dlq_url="$(output MediaDeadLetterQueueUrl)"
distribution_id="$(output MediaDistributionId)"
cdn_base_url="$(output MediaCdnBaseUrl)"

aws s3api get-public-access-block --bucket "${bucket}"
aws sqs get-queue-attributes --queue-url "${queue_url}" --attribute-names All
aws sqs get-queue-attributes --queue-url "${dlq_url}" --attribute-names All
aws cloudfront get-distribution --id "${distribution_id}" \
  --query 'Distribution.{Status:Status,Enabled:DistributionConfig.Enabled,DomainName:DomainName}'

for forbidden_path in private/uploads/verification/source private/media/verification/master.r1.t1.webp; do
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' "${cdn_base_url}/${forbidden_path}")"
  if [[ "${status}" != "403" && "${status}" != "404" ]]; then
    echo "Expected a blocked private CDN path, got HTTP ${status}: ${forbidden_path}" >&2
    exit 1
  fi
done

echo "Static stack checks passed. End-to-end upload processing still requires a staging media job."
