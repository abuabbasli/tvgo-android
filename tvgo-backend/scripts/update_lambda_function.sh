#!/usr/bin/env bash
set -euo pipefail

: "${AWS_REGION:?Set AWS_REGION (e.g. eu-central-1)}"
: "${FUNCTION_NAME:?Set FUNCTION_NAME (Lambda name)}"
: "${IMAGE_URI:?Set IMAGE_URI (ECR image URI)}"

TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-30}"
MEMORY_MB="${MEMORY_MB:-512}"
ENV_FILE="${ENV_FILE:-.env}"

echo "Updating Lambda function ${FUNCTION_NAME} to ${IMAGE_URI}"
aws lambda update-function-code \
  --function-name "${FUNCTION_NAME}" \
  --image-uri "${IMAGE_URI}" \
  --region "${AWS_REGION}" >/tmp/update-code.json

if [[ -f "${ENV_FILE}" ]]; then
  echo "Loading environment variables from ${ENV_FILE}"
  while IFS='=' read -r key value; do
    [[ -z "${key}" || "${key}" =~ ^# ]] && continue
    export "${key}"="${value}"
  done <"${ENV_FILE}"
fi

ENVIRONMENT_JSON=$(
  python - <<'PY'
import json
import os

prefixes = (
    "APP",
    "API",
    "MONGO",
    "ADMIN",
    "AWS",
    "S3",
    "SECRET",
    "TOKEN",
    "JWT",
    "BRAND",
    "FEATURE",
)

payload = {k: v for k, v in os.environ.items() if k.startswith(prefixes)}
print(json.dumps({"Variables": payload}))
PY
)

echo "Refreshing Lambda configuration (timeout=${TIMEOUT_SECONDS}s, memory=${MEMORY_MB}MB)"
aws lambda update-function-configuration \
  --function-name "${FUNCTION_NAME}" \
  --timeout "${TIMEOUT_SECONDS}" \
  --memory-size "${MEMORY_MB}" \
  --environment "${ENVIRONMENT_JSON}" \
  --region "${AWS_REGION}" >/tmp/update-config.json

echo "Lambda function updated successfully."
