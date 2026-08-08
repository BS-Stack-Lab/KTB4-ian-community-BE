#!/usr/bin/env bash
set -Eeuo pipefail

: "${STACK_NAME:?Set STACK_NAME}"

drift_id="$(aws cloudformation detect-stack-drift \
  --stack-name "${STACK_NAME}" \
  --query StackDriftDetectionId \
  --output text)"

aws cloudformation wait stack-drift-detection-complete \
  --stack-drift-detection-id "${drift_id}"

aws cloudformation describe-stack-drift-detection-status \
  --stack-drift-detection-id "${drift_id}" \
  --query '{Status:DetectionStatus,Drift:StackDriftStatus,Reason:DetectionStatusReason}'
