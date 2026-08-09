#!/usr/bin/env bash

set -Eeuo pipefail

now="${NOW_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
mapfile -t stacks < <(aws cloudformation list-stacks \
  --stack-status-filter CREATE_IN_PROGRESS CREATE_COMPLETE CREATE_FAILED \
    ROLLBACK_IN_PROGRESS ROLLBACK_FAILED ROLLBACK_COMPLETE \
    DELETE_FAILED UPDATE_IN_PROGRESS UPDATE_COMPLETE_CLEANUP_IN_PROGRESS \
    UPDATE_COMPLETE UPDATE_FAILED UPDATE_ROLLBACK_IN_PROGRESS \
    UPDATE_ROLLBACK_FAILED UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS \
    UPDATE_ROLLBACK_COMPLETE \
  --query "StackSummaries[?starts_with(StackName, 'e2e-media-')].StackName" \
  --output text | tr '\t' '\n')

for stack in "${stacks[@]}"; do
  [[ "${stack}" =~ ^e2e-media-[a-z0-9-]+$ ]] || continue
  expires_at="$(aws cloudformation describe-stacks \
    --stack-name "${stack}" \
    --query "Stacks[0].Tags[?Key=='expires-at'].Value | [0]" \
    --output text)"
  [[ "${expires_at}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || continue
  [[ "${expires_at}" < "${now}" ]] || continue

  bucket="$(aws cloudformation describe-stacks \
    --stack-name "${stack}" \
    --query "Stacks[0].Outputs[?OutputKey=='MediaBucketName'].OutputValue | [0]" \
    --output text)"
  if [[ "${bucket}" == e2e-media-* ]]; then
    aws s3 rm "s3://${bucket}" --recursive
  fi
  aws cloudformation delete-stack --stack-name "${stack}"
  aws cloudformation wait stack-delete-complete --stack-name "${stack}"
done
