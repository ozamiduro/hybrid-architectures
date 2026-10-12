# Manifiestos Kubernetes

Los YAML están separados por responsabilidad para identificar Namespace, Secret, PVC, Deployment, Service y ConfigMap.

Orden recomendado:

1. `00-namespace.yaml`
2. `01-postgres-secret.yaml`
3. `02-postgres-pvc.yaml`
4. `03-postgres.yaml`
5. `04-product-service.yaml`
6. `05-order-service.yaml`
7. `06-web-app.yaml`

También se pueden aplicar todos con `kubectl apply -f k8s/`.
