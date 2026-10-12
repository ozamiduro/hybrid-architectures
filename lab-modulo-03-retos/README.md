# Laboratorio avanzado de Kubernetes – TechStore

## Objetivo

Practicar sobre el mismo namespace `techstore` los mecanismos más importantes de operación, disponibilidad, scheduling, seguridad y despliegue progresivo de Kubernetes:

1. HPA
2. VPA
3. Affinity y anti-affinity
4. Taints y tolerations
5. Rolling Update
6. Blue-Green
7. Canary
8. Rolling Update controlado con `maxSurge`, `maxUnavailable` y rollback
9. RBAC con mínimo privilegio, Role, RoleBinding y ServiceAccount
10. Network Policies

> Este laboratorio complementa el proyecto EKS TechStore. Se asume que ya tienes un cluster EKS funcional, el namespace `techstore`, los deployments de la aplicación y acceso con `kubectl`.

---

## 0. Preparación

### Variables

Linux/macOS/Git Bash:

```bash
export NS=techstore
export ECR_REGISTRY=123456789012.dkr.ecr.us-east-1.amazonaws.com
export PRODUCT_IMAGE=$ECR_REGISTRY/product-service:1.0.0
export PRODUCT_IMAGE_V2=$ECR_REGISTRY/product-service:2.0.0
```

PowerShell:

```powershell
$env:NS="techstore"
$env:ECR_REGISTRY="123456789012.dkr.ecr.us-east-1.amazonaws.com"
$env:PRODUCT_IMAGE="$env:ECR_REGISTRY/product-service:1.0.0"
$env:PRODUCT_IMAGE_V2="$env:ECR_REGISTRY/product-service:2.0.0"
```

Reemplaza las cadenas `PRODUCT_IMAGE`, `PRODUCT_IMAGE_BLUE`, `PRODUCT_IMAGE_GREEN`, `PRODUCT_IMAGE_STABLE` y `PRODUCT_IMAGE_CANARY` de los YAML por imágenes reales de ECR antes de aplicarlos.

### Verificación inicial

```bash
kubectl config current-context
kubectl get nodes -o wide
kubectl get ns techstore
kubectl get pods -n techstore -o wide
kubectl get deploy -n techstore
```

Si la aplicación todavía no está desplegada, primero aplica los manifiestos base del proyecto TechStore.

---

# Reto 1 – HPA

## Objetivo

Escalar horizontalmente `product-service` según el consumo promedio de CPU.

## Dependencia

El HPA necesita métricas. En EKS debes tener Metrics Server operativo.

### Verificar Metrics Server

VPA utiliza información histórica y actual de consumo para calcular recomendaciones. Comprueba que Metrics Server esté funcionando:

```bash
kubectl get deployment metrics-server -n kube-system
kubectl top nodes
kubectl top pods -n techstore
```

Si `kubectl top` no devuelve métricas, resuelve primero Metrics Server.

Si Metrics Server no está instalado, puedes desplegarlo con el manifiesto oficial de Kubernetes SIGs:

kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

Espera a que esté operativo y repite los comandos de verificación.

Nota: en EKS también puedes gestionar Metrics Server mediante los mecanismos de instalación compatibles con tu clúster. No instales una segunda instancia si ya existe una operativa.


## Paso 1: revisar requests

```bash
kubectl get deploy product-service -n techstore -o yaml
```

El HPA basado en porcentaje de CPU necesita `resources.requests.cpu`.

## Paso 2: aplicar HPA

```bash
kubectl apply -f k8s/01-hpa/hpa-product.yaml
kubectl get hpa -n techstore
kubectl describe hpa product-service-hpa -n techstore
```

## Paso 3: generar carga

```bash
kubectl apply -f k8s/01-hpa/load-generator.yaml
kubectl get pod hpa-load-generator -n techstore
```

Observar:

```bash
watch kubectl get hpa,pods -n techstore
```

Si no tienes `watch`:

```bash
kubectl get hpa,pods -n techstore -w
```

## Qué debes observar

Inicialmente:

```text
TARGETS    REPLICAS
...        2
```

Durante la carga, el HPA puede aumentar hasta 6 réplicas.

Después de retirar la carga:

```bash
kubectl delete pod hpa-load-generator -n techstore
```

El número de réplicas debería disminuir después de la ventana de estabilización.

## Preguntas

- ¿Por qué el HPA no crea pods directamente?
- ¿Qué diferencia existe entre HPA y Cluster Autoscaler/Karpenter?
- ¿Qué ocurre si el HPA necesita más pods pero no existen recursos en los nodos?

---

# Reto 2 – VPA

## Objetivo

Obtener recomendaciones de CPU/memoria para `product-service` de forma vertical.

## Importante

VPA no viene necesariamente instalado en EKS. Debes instalar sus componentes en el cluster antes de aplicar el recurso `VerticalPodAutoscaler`.

Verifica:

```bash
kubectl api-resources | grep -i verticalpodautoscaler
```

Si no aparece, instala VPA siguiendo el mecanismo aprobado para tu laboratorio/cluster y vuelve a ejecutar el comando.

### Descargar el proyecto oficial de VPA

Clona el repositorio de Kubernetes Autoscaler:

```bash
git clone https://github.com/kubernetes/autoscaler.git
cd autoscaler/vertical-pod-autoscaler
```

Si ya existe una instalación previa de VPA, revisa su versión y configuración antes de continuar. No ejecutes el script de eliminación sobre una instalación compartida sin autorización.

### Instalar los componentes de VPA

Ejecuta el script de instalación proporcionado por el proyecto:

```bash
./hack/vpa-up.sh
```

El despliegue instala los tres componentes principales:

```text
Recommender: analiza el consumo y calcula las recomendaciones de CPU y memoria.

Updater: identifica pods que requieren cambios de recursos y gestiona su actualización según la política configurada.

Admission Controller: aplica las recomendaciones correspondientes cuando se crean pods.
```

En Windows, ejecuta el script desde Git Bash o WSL.

### Validar la instalación

Comprueba que los tres componentes estén operativos:

```bash
kubectl get pods -n kube-system
```

Para filtrar sus nombres en Linux o Git Bash:

```bash
kubectl get pods -n kube-system | grep vpa
```

También verifica que se haya registrado el recurso personalizado:

```bash
kubectl get crd verticalpodautoscalers.autoscaling.k8s.io
```

Y confirma que la API esté disponible:

```bash
kubectl api-resources | grep verticalpodautoscalers
```

Resultado esperado: los pods vpa-recommender, vpa-updater y vpa-admission-controller deben estar en estado Running, y debe existir el CRD de VPA.

## Paso 1: aplicar VPA en modo observación

```bash
kubectl apply -f k8s/02-vpa/vpa-product.yaml
kubectl get vpa -n techstore
kubectl describe vpa product-service-vpa -n techstore
```

El YAML utiliza:

```yaml
updateMode: "Off"
```

Esto es deliberado: primero observamos recomendaciones sin que VPA reinicie/recree pods.

## Paso 2: revisar recomendación

```bash
kubectl get vpa product-service-vpa -n techstore -o yaml
```

Busca:

```yaml
status:
  recommendation:
```

## Discusión

Compara la recomendación de VPA con los `requests` actuales.

### Preguntas

- ¿HPA aumenta/disminuye réplicas; VPA modifica recursos por pod. ¿Qué problema resuelve cada uno?
- ¿Por qué no conviene activar VPA en modo automático sin entender primero su impacto?
- ¿Qué sucede si HPA y VPA intentan controlar simultáneamente CPU/memoria de una forma incompatible?

---

# Reto 3 – Affinity y anti-affinity

## Objetivo

Controlar dónde Kubernetes puede colocar los pods.

## Paso 1: etiquetar nodos

Lista nodos:

```bash
kubectl get nodes --show-labels
```

Selecciona dos nodos worker y etiquétalos:

```bash
kubectl label node <NODE-1> workload=application --overwrite
kubectl label node <NODE-2> workload=application --overwrite
```

Verifica:

```bash
kubectl get nodes -l workload=application
```

## Paso 2: configurar imagen

En `03-affinity/affinity-product.yaml`, reemplaza:

```text
PRODUCT_IMAGE
```

por tu imagen real.

## Paso 3: desplegar

```bash
kubectl apply -f k8s/03-affinity/affinity-product.yaml
kubectl get pods -n techstore -l app=product-service-affinity -o wide
```

## Qué estás probando

`nodeAffinity` exige que el pod vaya a un nodo con:

```text
workload=application
```

`podAntiAffinity` intenta distribuir los pods entre diferentes hosts (`kubernetes.io/hostname`).

## Prueba

```bash
kubectl describe pod -n techstore -l app=product-service-affinity | grep -A20 -i affinity
```

### Pregunta

¿Qué diferencia hay entre `requiredDuringSchedulingIgnoredDuringExecution` y `preferredDuringSchedulingIgnoredDuringExecution`?

---

# Reto 4 – Taints y tolerations

## Objetivo

Reservar un nodo para cargas concretas.

## Paso 1: aplicar taint a un nodo

Usa un nodo dedicado:

```bash
kubectl taint node <NODE-1> dedicated=techstore:NoSchedule
```

Comprueba:

```bash
kubectl describe node <NODE-1> | grep -i taint
```

## Paso 2: observar el comportamiento

Crea un deployment normal o intenta programar una carga que no tenga toleration. Kubernetes no debería programarla en ese nodo.

## Paso 3: aplicar el deployment con toleration

Edita `04-taints-tolerations/taint-demo.yaml` con tu imagen y aplica:

```bash
kubectl apply -f k8s/04-taints-tolerations/taint-demo.yaml
kubectl get pods -n techstore -l app=product-service-taint-demo -o wide
```

Ahora el scheduler sí puede usar el nodo tainted porque el pod declara:

```yaml
tolerations:
  - key: dedicated
    operator: Equal
    value: techstore
    effect: NoSchedule
```

## Limpieza

```bash
kubectl delete -f k8s/04-taints-tolerations/taint-demo.yaml
kubectl taint node <NODE-1> dedicated=techstore:NoSchedule-
```

### Pregunta

¿Una toleration obliga al scheduler a colocar el pod en ese nodo? No. Solo permite que el pod sea elegible para tolerar ese taint.

---

# Reto 5 – Rolling Update

## Objetivo

Actualizar una aplicación gradualmente sin detener todos los pods.

Edita `05-rolling-update/rolling-product.yaml` y coloca una imagen real.

```bash
kubectl apply -f k8s/05-rolling-update/rolling-product.yaml
kubectl rollout status deployment/product-service-rolling -n techstore
```

Ver historial:

```bash
kubectl rollout history deployment/product-service-rolling -n techstore
```

Actualiza imagen:

```bash
kubectl set image deployment/product-service-rolling \
  product-service=$PRODUCT_IMAGE_V2 -n techstore
```

Observa:

```bash
kubectl get pods -n techstore -l app=product-service-rolling -w
```

### Preguntas

- ¿Qué pasa con los pods antiguos?
- ¿Por qué readinessProbe es importante durante un Rolling Update?
- ¿Qué diferencia hay entre Deployment y ReplicaSet durante el rollout?

---

# Reto 6 – Blue-Green Deployment

## Objetivo

Mantener dos versiones completas y cambiar el tráfico de una a otra modificando el selector del Service.

## Paso 1: preparar imágenes

En `06-blue-green/blue-green.yaml` sustituye:

```text
PRODUCT_IMAGE_BLUE
PRODUCT_IMAGE_GREEN
```

por dos versiones reales.

Una forma sencilla es editar el archivo para que `product-blue` use la versión actual y `product-green` la nueva.

## Paso 2: desplegar

```bash
kubectl apply -f k8s/06-blue-green/blue-green.yaml
kubectl get pods -n techstore -l app=product
kubectl get endpoints product-bluegreen -n techstore
```

El Service inicialmente tiene:

```yaml
selector:
  app: product
  version: blue
```

## Paso 3: cambiar tráfico a green

```bash
kubectl patch service product-bluegreen -n techstore \
  -p '{"spec":{"selector":{"app":"product","version":"green"}}}'
```

Verifica:

```bash
kubectl get endpoints product-bluegreen -n techstore
```

## Rollback

```bash
kubectl patch service product-bluegreen -n techstore \
  -p '{"spec":{"selector":{"app":"product","version":"blue"}}}'
```

### Concepto clave

Blue-Green no necesita reemplazar pods progresivamente. Mantiene ambos entornos y mueve el tráfico cambiando el selector del Service.

---

# Reto 7 – Canary Deployment

## Objetivo

Enviar una pequeña proporción del tráfico a una nueva versión antes de ampliar su exposición.

> Este ejercicio usa una aproximación didáctica basada en réplicas: 3 pods stable + 1 pod canary detrás del mismo Service. Kubernetes no garantiza un porcentaje exacto de tráfico; el reparto depende de la implementación del Service y del comportamiento de las conexiones.

## Paso 1

Edita `07-canary/canary.yaml` y usa imágenes diferentes para stable y canary.

## Paso 2

```bash
kubectl apply -f k8s/07-canary/canary.yaml
kubectl get pods -n techstore -l app=product-canary-demo -o wide
kubectl get endpoints product-canary-demo -n techstore
```

## Paso 3: observar la distribución

Ejecuta varias llamadas desde un pod temporal:

```bash
kubectl run canary-client -n techstore --rm -it --restart=Never \
  --image=curlimages/curl -- sh
```

Dentro:

```bash
for i in $(seq 1 30); do curl -s http://product-canary-demo:8081/actuator/health; echo; done
```

Para una demostración visual más clara, puedes modificar temporalmente las aplicaciones para devolver su versión en un endpoint.

## Promover canary

Aumenta canary y reduce stable:

```bash
kubectl scale deployment/product-canary --replicas=3 -n techstore
kubectl scale deployment/product-stable --replicas=0 -n techstore
```

## Rollback

```bash
kubectl scale deployment/product-canary --replicas=0 -n techstore
kubectl scale deployment/product-stable --replicas=3 -n techstore
```

### Pregunta

¿Por qué un canary productivo normalmente utiliza un Ingress/Gateway/service mesh o un controlador de progressive delivery cuando se necesita controlar exactamente 5%, 10%, 25%, etc.?

---

# Reto 8 – Rolling Update controlado: maxSurge, maxUnavailable y rollback

## Objetivo

Controlar exactamente cuánto se puede incrementar y reducir la capacidad durante una actualización.

El manifiesto de `05-rolling-update` usa:

```yaml
replicas: 4
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0
```

Interpretación:

- `maxSurge: 1`: durante la actualización puede existir como máximo 1 pod adicional sobre las 4 réplicas deseadas.
- `maxUnavailable: 0`: Kubernetes no debe dejar pods no disponibles durante el rollout, siempre que existan recursos y las probes permitan avanzar.

## Paso 1: crear versión inicial

```bash
kubectl apply -f k8s/05-rolling-update/rolling-product.yaml
kubectl rollout status deployment/product-service-rolling -n techstore
```

## Paso 2: actualizar

```bash
kubectl set image deployment/product-service-rolling \
  product-service=$PRODUCT_IMAGE_V2 -n techstore
```

Observa simultáneamente:

```bash
kubectl get rs,pods -n techstore -l app=product-service-rolling -w
```

## Paso 3: provocar un despliegue incorrecto

Puedes apuntar deliberadamente a una imagen inexistente:

```bash
kubectl set image deployment/product-service-rolling \
  product-service=$ECR_REGISTRY/product-service:no-existe -n techstore
```

Comprueba:

```bash
kubectl rollout status deployment/product-service-rolling -n techstore
kubectl get pods -n techstore -l app=product-service-rolling
kubectl describe pod -n techstore -l app=product-service-rolling
```

## Paso 4: rollback

```bash
kubectl rollout history deployment/product-service-rolling -n techstore
kubectl rollout undo deployment/product-service-rolling -n techstore
kubectl rollout status deployment/product-service-rolling -n techstore
```

### Preguntas

- ¿Por qué `maxUnavailable: 0` puede hacer que un rollout se quede esperando?
- ¿Qué pasa si los nuevos pods nunca pasan readiness?
- ¿Qué información proporciona `rollout history`?

---

# Reto 9 – RBAC y mínimo privilegio

## Objetivo

Dar a una identidad de Kubernetes solamente los permisos necesarios.

El YAML crea:

- ServiceAccount: `product-reader`
- Role namespace-scoped: `product-reader`
- RoleBinding
- Permisos únicamente para `pods`: `get` y `list`

## Paso 1

```bash
kubectl apply -f k8s/08-rbac/rbac.yaml
```

## Paso 2: comprobar permiso permitido

```bash
kubectl auth can-i get pods \
  --as=system:serviceaccount:techstore:product-reader \
  -n techstore
```

Resultado esperado:

```text
yes
```

También:

```bash
kubectl auth can-i list pods \
  --as=system:serviceaccount:techstore:product-reader \
  -n techstore
```

Debe responder `yes`.

## Paso 3: comprobar permisos prohibidos

```bash
kubectl auth can-i delete pods \
  --as=system:serviceaccount:techstore:product-reader \
  -n techstore
```

Debe responder `no`.

Prueba también:

```bash
kubectl auth can-i get secrets \
  --as=system:serviceaccount:techstore:product-reader \
  -n techstore
```

Debe responder `no`.

## Principio de mínimo privilegio

No se entrega `cluster-admin`.

El permiso está limitado a:

```yaml
resources: ["pods"]
verbs: ["get", "list"]
```

### Preguntas

- ¿Cuándo usarías Role y cuándo ClusterRole?
- ¿Qué función cumple RoleBinding?
- ¿Por qué una ServiceAccount no debería recibir permisos administrativos por defecto?

---

# Reto 10 – Network Policies

## Objetivo

Controlar qué pods pueden comunicarse con otros pods.

> Antes de empezar, confirma que tu CNI de EKS aplica NetworkPolicy. Crear un recurso NetworkPolicy no garantiza por sí solo que cualquier red subyacente lo haga cumplir.

## Paso 1: revisar etiquetas

```bash
kubectl get pods -n techstore --show-labels
```

Deben existir etiquetas como:

```text
app=web-app
app=product-service
app=order-service
app=postgres
```

## Paso 2: aplicar políticas

```bash
kubectl apply -f k8s/09-network-policies/network-policy.yaml
kubectl get networkpolicy -n techstore
```

La primera política aplica un `default-deny-ingress`.

Luego se agregan excepciones explícitas:

- entrada al `web-app` por TCP/80
- `web-app` → `product-service:8081`
- `web-app` → `order-service:8082`
- `order-service` → `product-service:8081`
- `product-service` → PostgreSQL:5432
- `order-service` → PostgreSQL:5432

## Paso 3: prueba desde web-app

Obtén un pod:

```bash
kubectl get pods -n techstore -l app=web-app
```

Prueba:

```bash
kubectl exec -n techstore deploy/web-app -- \
  wget -qO- http://product-service:8081/api/products
```

Debe funcionar si la imagen contiene `wget`.

## Paso 4: prueba desde un pod no autorizado

Crea un cliente:

```bash
kubectl run network-test -n techstore --rm -it --restart=Never \
  --image=curlimages/curl -- sh
```

Dentro intenta:

```bash
curl -m 3 http://product-service:8081/api/products
```

La conexión debería ser bloqueada por la política, siempre que tu CNI esté aplicando NetworkPolicy.

## Paso 5: prueba el flujo permitido order → product

El flujo real de TechStore necesita que `order-service` pueda llamar a `product-service` para descontar stock.

Por ello existe una regla específica para:

```text
order-service -> product-service:8081
```

Puedes comprobar el flujo haciendo una orden desde el frontend y verificando que el stock disminuya.

## Preguntas

- ¿Por qué NetworkPolicy se considera una defensa de red interna?
- ¿Qué diferencia existe entre NetworkPolicy y Security Groups de AWS?
- ¿Qué ocurre si aplicas `default-deny` y olvidas permitir DNS/egress cuando también restringes salida?

---

# Limpieza general

Eliminar todos los recursos de los retos:

```bash
kubectl delete -f k8s/01-hpa/ --ignore-not-found
kubectl delete -f k8s/02-vpa/ --ignore-not-found
kubectl delete -f k8s/03-affinity/ --ignore-not-found
kubectl delete -f k8s/04-taints-tolerations/ --ignore-not-found
kubectl delete -f k8s/05-rolling-update/ --ignore-not-found
kubectl delete -f k8s/06-blue-green/ --ignore-not-found
kubectl delete -f k8s/07-canary/ --ignore-not-found
kubectl delete -f k8s/08-rbac/ --ignore-not-found
kubectl delete -f k8s/09-network-policies/ --ignore-not-found
```

Quitar labels de nodos:

```bash
kubectl label node <NODE-1> workload-
kubectl label node <NODE-2> workload-
```

---

# Mapa conceptual del laboratorio

| Concepto | Controla | Objeto principal | Pregunta que responde |
|---|---|---|---|
| HPA | Cantidad de pods | HorizontalPodAutoscaler | ¿Cuántas réplicas necesito? |
| VPA | CPU/memoria por pod | VerticalPodAutoscaler | ¿Cuántos recursos necesita cada pod? |
| Affinity | Ubicación deseada/obligatoria | Pod spec | ¿Dónde quiero ejecutar el pod? |
| Anti-affinity | Separación entre pods | Pod spec | ¿Cómo evito poner réplicas juntas? |
| Taints | Restricción del nodo | Node | ¿Qué pods NO deberían entrar aquí? |
| Tolerations | Excepción a taints | Pod spec | ¿Qué pods pueden entrar? |
| Rolling Update | Actualización progresiva | Deployment | ¿Cómo actualizo sin caída? |
| Blue-Green | Cambio completo de versión | Deployments + Service | ¿Cómo cambio de entorno de forma rápida? |
| Canary | Exposición gradual | Deployments + Service | ¿Cómo pruebo una versión con pocos usuarios? |
| RBAC | Permisos API | Role/Binding/SA | ¿Qué puede hacer esta identidad? |
| NetworkPolicy | Tráfico pod-a-pod | NetworkPolicy | ¿Quién puede hablar con quién? |

---

# Reto final integrador

Como ejercicio final, combina:

1. `product-service` con HPA.
2. VPA en modo `Off` para obtener recomendaciones.
3. Anti-affinity para distribuir réplicas.
4. Un nodo dedicado con taint para una carga específica.
5. Rolling Update con `maxSurge: 1` y `maxUnavailable: 0`.
6. RBAC para una ServiceAccount de solo lectura.
7. NetworkPolicy con default deny y reglas explícitas.
8. Simula un despliegue canary y luego promuévelo.
9. Provoca una versión defectuosa y ejecuta `rollout undo`.

El objetivo no es solamente que los recursos estén en `kubectl get`: debes poder explicar **qué decisión operativa resuelve cada mecanismo, qué dependencia tiene y cómo comprobarías que realmente está funcionando**.
