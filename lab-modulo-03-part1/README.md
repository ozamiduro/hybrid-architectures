# TechStore - Laboratorio Kubernetes con Minikube

## 1. Objetivo

Desplegar en un clúster Kubernetes local con **Minikube** la misma aplicación TechStore utilizada en el laboratorio Docker:

- PostgreSQL 16
- `product-service` - Java 21 / Spring Boot
- `order-service` - Java 21 / Spring Boot
- `web-app` - React + Nginx

El laboratorio introduce los objetos básicos de Kubernetes:

- Namespace
- Pod
- Deployment
- Service
- ConfigMap
- Secret
- PersistentVolumeClaim (PVC)
- Probes de readiness/liveness
- Requests y limits
- DNS interno de Kubernetes

> **Importante:** este laboratorio está pensado para aprendizaje. No representa una arquitectura productiva completa.

---

## 2. Arquitectura

```text
                         ┌─────────────────────────┐
                         │       Minikube           │
                         │      namespace:          │
                         │        techstore         │
                         │                          │
 Browser ──NodePort─────►│  web-app :80             │
                         │      │                   │
                         │      │ Nginx proxy       │
                         │      ├──────────────┐    │
                         │      ▼              ▼    │
                         │ product-service   order-service
                         │    :8081             :8082 │
                         │       │                │
                         │       └──────┬─────────┘
                         │              ▼
                         │          PostgreSQL
                         │             :5432
                         │              │
                         │           PVC 2Gi
                         └─────────────────────────┘
```

El frontend no llama directamente a `localhost:8081` o `localhost:8082`. Nginx recibe `/api/products` y `/api/orders` y los envía a los Services Kubernetes mediante DNS:

```text
product-service:8081
order-service:8082
postgres:5432
```

Esto permite que los Pods cambien de IP sin modificar la configuración de la aplicación.

---

## 3. Requisitos

Instala y verifica:

```bash
minikube version
kubectl version --client
Docker --version
```

Se recomienda:

- Docker Desktop
- Minikube
- kubectl
- Git

Verifica Docker:

```bash
docker info
```

---

# PARTE A - Preparar Minikube

## 4. Crear el clúster

Inicia Minikube con Docker como driver:

```bash
minikube start --driver=docker
```

Verifica:

```bash
minikube status
kubectl get nodes
```

Debes obtener un nodo en estado `Ready`.

Opcionalmente:

```bash
kubectl cluster-info
```

---

# PARTE B - Construir las imágenes

## 5. Construir las imágenes Docker

Desde la raíz del proyecto:

```bash
docker build -t techstore/product-service:v1.0.0 ./product-service
docker build -t techstore/order-service:v1.0.0 ./order-service
docker build -t techstore/web-app:v1.0.0 ./web-app
```

Comprueba:

```bash
docker images | grep techstore
```

Deberías ver:

```text
techstore/product-service   v1.0.0
techstore/order-service     v1.0.0
techstore/web-app           v1.0.0
```

### ¿Por qué no usamos Docker Hub?

Para este laboratorio las imágenes se ejecutarán únicamente dentro de Minikube. Por eso las cargaremos directamente al nodo de Minikube.

---

## 6. Cargar las imágenes en Minikube

Ejecuta:

```bash
minikube image load techstore/product-service:v1.0.0
minikube image load techstore/order-service:v1.0.0
minikube image load techstore/web-app:v1.0.0
```

Verifica que Minikube las conozca:

```bash
minikube image ls | grep techstore
```

Los Deployments utilizan:

```yaml
imagePullPolicy: Never
```

Esto obliga a Kubernetes a usar la imagen que ya está disponible en el nodo y evita que intente descargarla de Docker Hub.

---

# PARTE C - Crear los recursos Kubernetes

## 7. Crear el Namespace

```bash
kubectl apply -f k8s/00-namespace.yaml
```

Verifica:

```bash
kubectl get namespaces
```

A partir de aquí podemos trabajar explícitamente con:

```bash
kubectl get all -n techstore
```

---

## 8. Crear el Secret de PostgreSQL

```bash
kubectl apply -f k8s/01-postgres-secret.yaml
```

Verifica:

```bash
kubectl get secret -n techstore
```

El Secret contiene:

- Base de datos: `techstore`
- Usuario: `techstore`
- Password: `techstore_dev`

> En producción no conviene guardar credenciales de esta forma en un repositorio Git. 

---

## 9. Crear el almacenamiento de PostgreSQL

```bash
kubectl apply -f k8s/02-postgres-pvc.yaml
```

Verifica:

```bash
kubectl get pvc -n techstore
```

Debes esperar que el PVC pase a `Bound`.

También puedes revisar:

```bash
kubectl describe pvc postgres-pvc -n techstore
```

---

## 10. Desplegar PostgreSQL

```bash
kubectl apply -f k8s/03-postgres.yaml
```

Verifica:

```bash
kubectl get pods -n techstore
kubectl get svc -n techstore
```

Espera hasta que PostgreSQL esté `Running` y `READY 1/1`:

```bash
kubectl get pods -n techstore -w
```

Presiona `Ctrl+C` cuando esté listo.

Revisa los logs:

```bash
kubectl logs deployment/postgres -n techstore
```

---

# PARTE D - Microservicios

## 11. Desplegar Product Service

```bash
kubectl apply -f k8s/04-product-service.yaml
```

Verifica:

```bash
kubectl get deployment -n techstore
kubectl get pods -n techstore
kubectl get svc -n techstore
```

El Service debe llamarse:

```text
product-service
```

Dentro del clúster será accesible como:

```text
http://product-service:8081
```

Y su health check:

```text
http://product-service:8081/actuator/health
```

---

## 12. Desplegar Order Service

```bash
kubectl apply -f k8s/05-order-service.yaml
```

El punto importante está en:

```yaml
PRODUCT_SERVICE_URL: http://product-service:8081
```

Kubernetes resuelve `product-service` mediante DNS interno. No necesitamos conocer la IP del Pod.

Verifica:

```bash
kubectl get pods -n techstore
kubectl get svc -n techstore
```

---

# PARTE E - Frontend

## 13. Desplegar Web App

```bash
kubectl apply -f k8s/06-web-app.yaml
```

Verifica:

```bash
kubectl get deployment -n techstore
kubectl get pods -n techstore
kubectl get svc -n techstore
```

El Service `web-app` es de tipo `NodePort` y utiliza el puerto:

```text
30080
```

---

# PARTE F - Validar el despliegue

## 14. Ver todos los recursos

```bash
kubectl get all -n techstore
```

También:

```bash
kubectl get configmap -n techstore
kubectl get secret -n techstore
kubectl get pvc -n techstore
```

La situación esperada es aproximadamente:

```text
NAME                                  READY   STATUS
pod/postgres-...                      1/1     Running
pod/product-service-...               1/1     Running
pod/order-service-...                 1/1     Running
pod/web-app-...                       1/1     Running
```

Y:

```text
NAME                     TYPE        PORT(S)
service/postgres        ClusterIP   5432/TCP
service/product-service ClusterIP   8081/TCP
service/order-service   ClusterIP   8082/TCP
service/web-app         NodePort    80:30080/TCP
```

---

## 15. Abrir la aplicación

La forma más sencilla y compatible con Minikube es:

```bash
minikube service web-app -n techstore --url
```

Minikube mostrará una URL. Ábrela en el navegador.

También puedes ejecutar:

```bash
minikube service web-app -n techstore
```

para solicitar que Minikube abra el servicio.

---

# PARTE G - Probar la aplicación

## 16. Ver los productos

Puedes consultar el API desde dentro del clúster. Una forma sencilla es ejecutar `curl` desde el Pod de product-service:

```bash
kubectl exec -n techstore deployment/product-service -- wget -qO- http://product-service:8081/api/products
```

Si la imagen de Java no incluye `wget`, utiliza port-forward desde tu máquina:

```bash
kubectl port-forward -n techstore service/product-service 8081:8081
```

Luego, en otra terminal:

```bash
curl http://localhost:8081/api/products
```

---

## 17. Probar creación de una orden

Con el port-forward activo:

```bash
curl -X POST http://localhost:8081/api/products/1/decrease-stock \
  -H "Content-Type: application/json" \
  -d '{"quantity":1}'
```

> Esta llamada directa sirve únicamente para demostrar el endpoint de inventario. Para el flujo real de negocio utiliza Order Service desde el frontend.

Para probar Order Service directamente:

```bash
kubectl port-forward -n techstore service/order-service 8082:8082
```

En otra terminal:

```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

Luego consulta el producto:

```bash
curl http://localhost:8081/api/products/1
```

El stock debe disminuir en 2 unidades.

---

# PARTE H - Entender la comunicación entre Pods

## 18. ¿Cómo encuentra Order Service a Product Service?

No utilizamos:

```text
localhost:8081
```

ni la IP del Pod.

Utilizamos:

```text
product-service:8081
```

Kubernetes crea DNS para los Services.

El flujo es:

```text
Order Service Pod
       |
       | HTTP
       v
product-service:8081
       |
       v
Product Service Pod
```

Si Kubernetes reemplaza el Pod de Product Service y cambia su IP, el Service sigue siendo el punto estable de comunicación.

---

# PARTE I - Inspección y troubleshooting

## 19. Ver detalles de un Pod

```bash
kubectl describe pod -n techstore <NOMBRE_DEL_POD>
```

Obtén primero el nombre:

```bash
kubectl get pods -n techstore
```

---

## 20. Ver logs

Product Service:

```bash
kubectl logs deployment/product-service -n techstore
```

Order Service:

```bash
kubectl logs deployment/order-service -n techstore
```

Web App:

```bash
kubectl logs deployment/web-app -n techstore
```

PostgreSQL:

```bash
kubectl logs deployment/postgres -n techstore
```

---

## 21. Ver los eventos del Namespace

Cuando un Pod no inicia, este comando es muy útil:

```bash
kubectl get events -n techstore --sort-by=.lastTimestamp
```

Busca errores como:

- `ImagePullBackOff`
- `CrashLoopBackOff`
- `FailedMount`
- `FailedScheduling`
- problemas de probes

---

## 22. Error ImagePullBackOff

Si aparece:

```text
ImagePullBackOff
```

comprueba que la imagen esté cargada en Minikube:

```bash
minikube image ls | grep techstore
```

Si no aparece:

```bash
minikube image load techstore/product-service:v1.0.0
minikube image load techstore/order-service:v1.0.0
minikube image load techstore/web-app:v1.0.0
```

Luego reinicia los Deployments:

```bash
kubectl rollout restart deployment -n techstore
```

---

## 23. Verificar el rollout

```bash
kubectl rollout status deployment/postgres -n techstore
kubectl rollout status deployment/product-service -n techstore
kubectl rollout status deployment/order-service -n techstore
kubectl rollout status deployment/web-app -n techstore
```

---

# PARTE J - Escalamiento

## 24. Escalar Product Service

Prueba Kubernetes horizontalmente:

```bash
kubectl scale deployment/product-service --replicas=2 -n techstore
```

Verifica:

```bash
kubectl get pods -n techstore -l app=product-service
```

Ahora habrá dos Pods.

El Service continúa siendo:

```text
product-service:8081
```

El cliente no necesita saber cuántos Pods existen.

Para volver a una réplica:

```bash
kubectl scale deployment/product-service --replicas=1 -n techstore
```

---

# PARTE K - Persistencia

## 25. ¿Qué ocurre si eliminamos PostgreSQL?

Observa el PVC:

```bash
kubectl get pvc -n techstore
```

Elimina el Pod de PostgreSQL:

```bash
kubectl delete pod -n techstore -l app=postgres
```

El Deployment creará un nuevo Pod.

Verifica:

```bash
kubectl get pods -n techstore -w
```

El objetivo es demostrar que los datos están asociados al almacenamiento persistente, no al ciclo de vida del Pod.

---

# PARTE L - Ejercicios

## Reto 1 - Identificar la arquitectura

Responde:

1. ¿Cuál es la diferencia entre un Pod y un Deployment?
2. ¿Por qué Product Service utiliza un Service `ClusterIP`?
3. ¿Por qué Web App utiliza `NodePort`?
4. ¿Qué problema resuelve el ConfigMap?
5. ¿Qué información debería estar en Secret?
6. ¿Por qué PostgreSQL necesita un PVC?

## Reto 2 - Escalamiento

Escala Order Service a 3 réplicas:

```bash
kubectl scale deployment/order-service --replicas=3 -n techstore
```

Comprueba los Pods:

```bash
kubectl get pods -n techstore -l app=order-service
```

Pregunta: ¿el frontend necesita conocer las tres IP de los Pods?

## Reto 3 - Configuración

Ejecuta:

```bash
kubectl describe configmap order-service-config -n techstore
```

Identifica:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
PRODUCT_SERVICE_URL
```

Explica por qué `PRODUCT_SERVICE_URL` apunta a `product-service` y no a una IP.

## Reto 4 - Fallo de un Pod

Elimina un Pod de Product Service:

```bash
kubectl delete pod -n techstore -l app=product-service
```

Observa:

```bash
kubectl get pods -n techstore -w
```

Pregunta: ¿quién crea el nuevo Pod?

---

# PARTE M - Limpieza

## 26. Eliminar toda la aplicación

```bash
kubectl delete namespace techstore
```

Esto elimina los recursos del laboratorio dentro del Namespace.

Comprueba:

```bash
kubectl get namespaces
```

---

## 27. Detener Minikube

```bash
minikube stop
```

Para eliminar completamente el clúster:

```bash
minikube delete
```

---

# 28. Resumen de objetos

| Archivo | Recurso | Función |
|---|---|---|
| `00-namespace.yaml` | Namespace | Aísla la aplicación |
| `01-postgres-secret.yaml` | Secret | Credenciales de PostgreSQL |
| `02-postgres-pvc.yaml` | PVC | Persistencia |
| `03-postgres.yaml` | Deployment + Service | Base de datos |
| `04-product-service.yaml` | ConfigMap + Deployment + Service | Inventario |
| `05-order-service.yaml` | ConfigMap + Deployment + Service | Órdenes |
| `06-web-app.yaml` | Deployment + NodePort Service | Frontend |

---

# 29. Flujo completo

```text
1. Docker build
       ↓
2. minikube image load
       ↓
3. Namespace
       ↓
4. Secret + PVC
       ↓
5. PostgreSQL
       ↓
6. Product Service
       ↓
7. Order Service
       ↓
8. Web App
       ↓
9. Service Discovery por DNS
       ↓
10. Usuario accede mediante NodePort / minikube service
```
