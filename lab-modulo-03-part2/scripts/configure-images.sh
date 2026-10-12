#!/usr/bin/env bash
set -euo pipefail
AWS_REGION="${AWS_REGION:-us-east-1}"
ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"
TAG="${TAG:-1.1.0}"
REGISTRY_URI="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

sed -i -E "s|^([[:space:]]*image:).*$|\\1 ${REGISTRY_URI}/product-service:${TAG}|" k8s/05-product-service.yaml
sed -i -E "s|^([[:space:]]*image:).*$|\\1 ${REGISTRY_URI}/order-service:${TAG}|" k8s/06-order-service.yaml
sed -i -E "s|^([[:space:]]*image:).*$|\\1 ${REGISTRY_URI}/web-app:${TAG}|" k8s/07-web-app.yaml

echo "Imágenes configuradas con TAG=${TAG}"
grep 'image:' k8s/05-product-service.yaml
grep 'image:' k8s/06-order-service.yaml
grep 'image:' k8s/07-web-app.yaml
