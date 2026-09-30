#!/bin/sh
set -e

echo "=== ORION Hybrid Lab ==="
echo "APP_ENV=${APP_ENV}"
echo
echo "DNS:"
getent hosts "${AWS_API_HOST}" || true
echo
echo "API:"
curl -s "http://${AWS_API_HOST}:${AWS_API_PORT}/api/v1/health" | jq .
echo
echo "Gateway:"
curl -s "http://${AWS_GATEWAY_HOST}/api/v1/health" | jq .
