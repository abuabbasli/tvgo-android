#!/usr/bin/env bash
set -euo pipefail

: "${AWS_REGION:?Set AWS_REGION (e.g. eu-central-1)}"
: "${AWS_ACCOUNT_ID:?Set AWS_ACCOUNT_ID (the 12-digit AWS account number)}"

ECR_REPOSITORY="${ECR_REPOSITORY:-tvgo}"
IMAGE_NAME="${IMAGE_NAME:-tvgo}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
DOCKERFILE="${DOCKERFILE:-Dockerfile.lambda}"
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}"

echo "Logging in to Amazon ECR (${ECR_URI})"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"

echo "Building container image using ${DOCKERFILE}"
docker build -f "${DOCKERFILE}" -t "${FULL_IMAGE_NAME}" .

echo "Tagging image as ${ECR_URI}"
docker tag "${FULL_IMAGE_NAME}" "${ECR_URI}"

echo "Pushing image to ECR"
docker push "${ECR_URI}"

echo "Deployment image pushed successfully. Update the Lambda function to use ${ECR_URI}."
