#!/usr/bin/env bash
set -euo pipefail
: "${ECR_REGISTRY:?Ejecuta: export ECR_REGISTRY=123456789012.dkr.ecr.us-east-1.amazonaws.com}"
PRODUCT_REPO="${PRODUCT_REPO:-product-service}"
TAG1="${TAG1:-1.0.0}"
TAG2="${TAG2:-2.0.0}"
BASE="$(cd "$(dirname "$0")" && pwd)"
find "$BASE/k8s/advanced" -type f -name '*.yaml' -print0 | xargs -0 sed -i \
  -e "s#PRODUCT_IMAGE_BLUE#${ECR_REGISTRY}/${PRODUCT_REPO}:${TAG1}#g" \
  -e "s#PRODUCT_IMAGE_GREEN#${ECR_REGISTRY}/${PRODUCT_REPO}:${TAG2}#g" \
  -e "s#PRODUCT_IMAGE_STABLE#${ECR_REGISTRY}/${PRODUCT_REPO}:${TAG1}#g" \
  -e "s#PRODUCT_IMAGE_CANARY#${ECR_REGISTRY}/${PRODUCT_REPO}:${TAG2}#g" \
  -e "s#PRODUCT_IMAGE_V2#${ECR_REGISTRY}/${PRODUCT_REPO}:${TAG2}#g" \
  -e "s#PRODUCT_IMAGE#${ECR_REGISTRY}/${PRODUCT_REPO}:${TAG1}#g"
echo "Imágenes configuradas en k8s/advanced/"
