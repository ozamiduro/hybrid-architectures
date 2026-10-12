# Manifiestos Kubernetes - TechStore en Amazon EKS

Estos manifiestos despliegan la aplicación TechStore en el clúster EKS existente.

## Recursos

- Namespace `techstore`
- Secret para PostgreSQL
- StorageClass EBS gp3
- PostgreSQL como `StatefulSet`
- PVC creado automáticamente mediante `volumeClaimTemplates`
- `product-service` (2 réplicas)
- `order-service` (2 réplicas)
- `web-app` (2 réplicas)
- Ingress ALB

## PostgreSQL con StatefulSet

PostgreSQL se despliega como `StatefulSet` en lugar de `Deployment`. El StatefulSet usa el Service headless `postgres` para proporcionar identidad de red estable y crea automáticamente el PVC `postgres-data-postgres-0` a partir de `volumeClaimTemplates`.

El volumen utiliza la StorageClass `techstore-gp3`, que aprovisiona un volumen EBS gp3 mediante el AWS EBS CSI Driver.

Antes de aplicar los manifiestos, reemplaza los marcadores `PRODUCT_IMAGE`, `ORDER_IMAGE` y `WEB_IMAGE` por las URI reales de ECR. El README principal contiene el procedimiento recomendado.
