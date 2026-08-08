#!/usr/bin/env bash
set -Eeuo pipefail

: "${STACK_NAME:?Set STACK_NAME}"
: "${PARAMETERS_FILE:?Set PARAMETERS_FILE}"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
template_file="${TEMPLATE_FILE:-${script_dir}/../cloudformation/media-v2.yaml}"
change_set_name="${CHANGE_SET_NAME:-media-v2-$(date -u +%Y%m%dT%H%M%SZ)}"

aws cloudformation create-change-set \
  --stack-name "${STACK_NAME}" \
  --change-set-name "${change_set_name}" \
  --change-set-type "${CHANGE_SET_TYPE:-UPDATE}" \
  --template-body "file://${template_file}" \
  --parameters "file://${PARAMETERS_FILE}" \
  --capabilities CAPABILITY_NAMED_IAM

aws cloudformation wait change-set-create-complete \
  --stack-name "${STACK_NAME}" \
  --change-set-name "${change_set_name}"

aws cloudformation describe-change-set \
  --stack-name "${STACK_NAME}" \
  --change-set-name "${change_set_name}" \
  --query '{Status:Status,ExecutionStatus:ExecutionStatus,Changes:Changes}'

echo "Change Set was created for review only; it was not executed."
