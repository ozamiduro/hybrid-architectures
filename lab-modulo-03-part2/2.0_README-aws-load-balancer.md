# Instalar AWS Load Balancer Controller

El manifiesto `k8s/40-ingress.yaml` utiliza:

```yaml
ingressClassName: alb
```

Por ello, el clúster EKS debe contar con **AWS Load Balancer Controller**, que se encargará de crear y administrar los Application Load Balancers (ALB) a partir de los recursos `Ingress` de Kubernetes. AWS recomienda instalarlo mediante Helm.

En este laboratorio utilizaremos **IAM Roles for Service Accounts (IRSA)** para otorgar al controlador los permisos necesarios sobre AWS.

> **Importante:** el `cluster.yaml` ya habilita el proveedor OIDC mediante:
>
> ```yaml
> iam:
>   withOIDC: true
> ```
>
> Esto es un requisito para utilizar IRSA.

### 7.1. Crear la IAM Policy

Descarga la política oficial requerida por AWS Load Balancer Controller:

```bash
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.14.1/docs/install/iam_policy.json
```

Crea la política IAM:

```bash
aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json
```

> La documentación actual de AWS utiliza la política correspondiente a AWS Load Balancer Controller v2.14.1.

Obtén tu Account ID:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

Puedes verificar que la política existe con:

```bash
aws iam get-policy \
  --policy-arn arn:aws:iam::${ACCOUNT_ID}:policy/AWSLoadBalancerControllerIAMPolicy
```

### 7.2. Crear el IAM Role y el ServiceAccount

Ahora crea el **IAM Role** y el **ServiceAccount de Kubernetes** asociados.

```bash
eksctl create iamserviceaccount \
  --cluster=eks-lab-tec \
  --region=us-east-1 \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --role-name=AmazonEKSLoadBalancerControllerRole \
  --attach-policy-arn=arn:aws:iam::${ACCOUNT_ID}:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve
```

Este comando realiza las dos tareas:

1. Crea el IAM Role `AmazonEKSLoadBalancerControllerRole`.
2. Crea el ServiceAccount `aws-load-balancer-controller` en `kube-system` y lo asocia al IAM Role mediante la anotación `eks.amazonaws.com/role-arn`.

Verifica el ServiceAccount:

```bash
kubectl get serviceaccount \
  aws-load-balancer-controller \
  -n kube-system
```

Y verifica la asociación con IAM:

```bash
kubectl get serviceaccount \
  aws-load-balancer-controller \
  -n kube-system \
  -o yaml
```

Deberías encontrar una sección similar a:

```yaml
annotations:
  eks.amazonaws.com/role-arn: arn:aws:iam::<ACCOUNT_ID>:role/AmazonEKSLoadBalancerControllerRole
```

### 7.3. Instalar AWS Load Balancer Controller mediante Helm

Una vez creado el ServiceAccount, instala el controlador:

```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update
```

```bash
helm upgrade --install aws-load-balancer-controller \
  eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=eks-lab-tec \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set region=us-east-1
```

El parámetro:

```bash
--set serviceAccount.create=false
```

es importante porque el ServiceAccount **ya fue creado previamente por `eksctl`** y contiene la asociación con el IAM Role. AWS utiliza este mismo esquema en su procedimiento con Helm.

### 7.4. Validar la instalación

Verifica el Deployment:

```bash
kubectl get deployment \
  aws-load-balancer-controller \
  -n kube-system
```

Verifica los Pods:

```bash
kubectl get pods \
  -n kube-system \
  -l app.kubernetes.io/name=aws-load-balancer-controller
```

Finalmente, revisa los logs:

```bash
kubectl logs \
  -n kube-system \
  deployment/aws-load-balancer-controller
```

El controlador estará listo cuando sus Pods estén en estado `Running` y el Deployment aparezca disponible.

### Flujo completo

```text
cluster.yaml
     │
     ├── OIDC habilitado
     │
     ▼
IAM Policy
AWSLoadBalancerControllerIAMPolicy
     │
     ▼
eksctl create iamserviceaccount
     │
     ├── IAM Role
     │   AmazonEKSLoadBalancerControllerRole
     │
     └── Kubernetes ServiceAccount
         aws-load-balancer-controller
                │
                │ asociación IRSA
                ▼
        Helm Install
                │
                ▼
AWS Load Balancer Controller
                │
                ▼
          Ingress (ALB)
```
