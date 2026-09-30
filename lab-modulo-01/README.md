# Laboratorio Práctico — Módulo 1
## Simulador de Arquitectura Híbrida On-Premise ↔ AWS para ORION

**Duración estimada:** 2 horas  
**Modalidad:** Virtual  
**Nivel:** Inicial–intermedio  
**Tecnologías:** Docker, Docker Compose, Nginx, Alpine Linux, curl, jq

---

## 1. Objetivos

Al finalizar este laboratorio podrás:

1. Simular la segmentación de red entre un Datacenter On-Premise y una VPC de AWS.
2. Implementar conectividad entre ambos ambientes mediante una red híbrida simulada.
3. Publicar una API simulada en el ambiente AWS y consumirla desde On-Premise.
4. Separar configuración por ambiente mediante variables de entorno.
5. Simular una arquitectura con API Gateway/Ingress, microservicio y base de datos.
6. Ejecutar pruebas de conectividad, DNS, disponibilidad y recuperación ante una caída del enlace.
7. Relacionar el laboratorio con conceptos de VPN IPSec, Direct Connect, Transit Gateway, HA y RTO/RPO.

> **Importante:** Docker no crea una VPN, Direct Connect ni Transit Gateway reales. En este laboratorio usamos redes Docker para **simular** la separación de ambientes y una conexión híbrida.

---

## 2. Relación con la clase

La arquitectura de referencia del módulo utiliza:

- On-Premise: `10.0.0.0/16`
- AWS VPC: `172.31.0.0/16`
- Conectividad híbrida mediante Direct Connect y VPN IPSec.
- Transit Gateway como hub de enrutamiento.
- Microservicios en AWS/EKS.
- Componentes core y datos sensibles en On-Premise.
- Gestión de configuración por ambiente.
- Alta disponibilidad y Disaster Recovery.
- RTO objetivo < 15 minutos y RPO cercano a 0 en el escenario conceptual de ORION.

En este laboratorio simplificaremos esos componentes para poder ejecutarlos localmente con Docker.

---

# 3. Arquitectura del laboratorio

```text
                  SIMULACIÓN DE ARQUITECTURA HÍBRIDA

 ┌──────────────────────── ON-PREMISE ────────────────────────┐
 │ Red: 10.10.0.0/16                                         │
 │                                                            │
 │  ┌─────────────────┐       ┌───────────────────────────┐   │
 │  │ ORION Client    │       │ Core DB simulada          │   │
 │  │ 10.10.0.10      │       │ 10.10.0.50                │   │
 │  └────────┬────────┘       └───────────────────────────┘   │
 │           │                                                │
 └───────────┼────────────────────────────────────────────────┘
             │
             │   ENLACE HÍBRIDO SIMULADO
             │   VPN IPSec / Direct Connect
             │
 ┌───────────┼──────────────── AWS VPC ───────────────────────┐
 │           │ Red: 10.20.0.0/16                              │
 │           ▼                                                 │
 │  ┌─────────────────┐                                       │
 │  │ Kong / Ingress  │  10.20.0.20                           │
 │  └────────┬────────┘                                       │
 │           │                                                │
 │  ┌────────▼────────┐       ┌───────────────────────────┐   │
 │  │ ORION API       │       │ AWS Service / Health      │   │
 │  │ 10.20.0.10      │       │ /api/v1/health            │   │
 │  └─────────────────┘       └───────────────────────────┘   │
 │                                                            │
 └────────────────────────────────────────────────────────────┘

                 En AWS real:
                 VPC → Route Tables → TGW/DX/VPN → On-Prem
```

---

# 4. Requisitos previos

Instala:

- Docker Engine 24+ o Docker Desktop.
- Docker Compose v2.
- Git (opcional).
- Terminal Bash, PowerShell o WSL.

Verifica:

```bash
docker --version
docker compose version
```

Debe aparecer una versión funcional de Docker y Compose.

### Comprobar que Docker funciona

```bash
docker run --rm hello-world
```

Si aparece el mensaje de bienvenida de Docker, puedes continuar.

---

# 5. Estructura del laboratorio

Después de descargar los archivos tendrás:

```text
lab-modulo-01/
│
├── README.md
├── docker-compose.yml
├── .env.example
│
├── onprem/
│   ├── Dockerfile
│   └── app/
│       └── check.sh
│
├── aws-api/
│   ├── nginx.conf
│   └── Dockerfile
│
├── aws-gateway/
│   └── nginx.conf
│
└── docs/
    └── entregable.md
```

---

# 6. Paso 1 — Preparar el proyecto

Clona o descomprime el laboratorio.

Entra a la carpeta:

```bash
cd lab-modulo-01
```

Crea el archivo de variables:

```bash
cp .env.example .env
```

En Windows PowerShell puedes usar:

```powershell
Copy-Item .env.example .env
```

Revisa:

```bash
cat .env
```

En PowerShell:

```powershell
Get-Content .env
```

---

# 7. Paso 2 — Revisar la configuración por ambiente

Abre `.env`.

Encontrarás:

```env
APP_ENV=staging
AWS_API_HOST=orion-api
AWS_API_PORT=80
AWS_GATEWAY_HOST=orion-gateway
RTO_SECONDS=15
```

Principio de configuración desacoplada:

```text
                 MISMO CÓDIGO
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
       DEV         STAGING       PROD
          │           │           │
       config       config       config
```

El código de la aplicación no debería cambiar únicamente porque cambia el ambiente.

---

# 8. Paso 3 — Crear la red On-Premise y AWS

El `docker-compose.yml` define dos redes:

```text
net-onprem
10.10.0.0/16

net-aws
10.20.0.0/16
```

Levanta el laboratorio:

```bash
docker compose up -d --build
```

Verifica los contenedores:

```bash
docker compose ps
```

Debes observar servicios similares a:

```text
orion-onprem-client
orion-aws-api
orion-gateway
```

Verifica las redes:

```bash
docker network ls
```

Inspecciona las redes:

```bash
docker network inspect net-onprem
docker network inspect net-aws
```

---

# 9. Paso 4 — Identificar los componentes

## On-Premise

El cliente ORION representa una aplicación que continúa en el Datacenter:

```text
ORION Client
IP: 10.10.0.10
```

También simularemos una base de datos local:

```text
Core DB
IP: 10.10.0.50
```

La base de datos representa un workload que podría mantenerse On-Premise por:

- latencia;
- dependencia legacy;
- soberanía o regulación;
- costos de migración.

Estos criterios forman parte de la matriz de decisión presentada en clase.

## AWS

La API ORION representa un microservicio que puede desplegarse en AWS:

```text
ORION API
IP: 10.20.0.10
```

El Gateway representa conceptualmente una capa de entrada pública:

```text
Gateway / Kong
IP: 10.20.0.20
```

En AWS real esta capa podría integrarse con servicios de red, balanceadores, API Gateway, Kong o ingress según el diseño.

---

# 10. Paso 5 — Probar la API directamente

Desde el cliente On-Premise:

```bash
docker exec -it orion-onprem-client sh
```

Dentro del contenedor:

```bash
curl http://orion-api/api/v1/health
```

Debes recibir una respuesta similar a:

```json
{
  "status": "UP",
  "service": "ORION-Core",
  "environment": "AWS",
  "region": "us-east-1"
}
```

Sal:

```bash
exit
```

---

# 11. Paso 6 — Probar resolución de nombres

Desde On-Premise:

```bash
docker exec -it orion-onprem-client getent hosts orion-api
```

La resolución debe devolver la dirección del servicio.

En una arquitectura real, el DNS híbrido puede utilizar mecanismos como Route 53 Resolver y DNS interno.

---

# 12. Paso 7 — Probar el Gateway

Ejecuta:

```bash
docker exec -it orion-onprem-client \
  curl http://orion-gateway/api/v1/health
```

Flujo de red:

```text
ORION Client
     │
     │ HTTP
     ▼
Gateway / Kong
     │
     │ routing
     ▼
ORION API
```

---

# 13. Paso 8 — Simular una consulta al Core On-Premise

Ejecuta:

```bash
docker exec -it orion-onprem-client \
  curl http://orion-api/api/v1/core
```

La API devolverá información simulada del Core:

```json
{
  "service": "ORION-Core",
  "core_location": "ON-PREMISE",
  "database": "10.10.0.50",
  "status": "connected"
}
```

Esto representa un patrón híbrido:

```text
                 AWS
       ┌─────────────────────┐
       │ Microservicio       │
       │ ORION API           │
       └──────────┬──────────┘
                  │
                  │ llamada híbrida
                  ▼
       ┌─────────────────────┐
       │ On-Premise          │
       │ Core / Legacy DB    │
       └─────────────────────┘
```

---

# 14. Paso 9 — Simular caída del enlace híbrido

Primero confirma que funciona:

```bash
docker exec -it orion-onprem-client \
  curl --connect-timeout 3 http://orion-api/api/v1/health
```

Ahora desconecta el componente AWS de la red híbrida simulada:

```bash
docker network disconnect net-onprem orion-aws-api
```

Prueba nuevamente:

```bash
docker exec -it orion-onprem-client \
  curl --connect-timeout 3 http://orion-api/api/v1/health
```

La llamada debe fallar.

Esto representa:

```text
              X
On-Premise -------- AWS
             VPN/DX DOWN
```

---

# 15. Paso 10 — Recuperar la conectividad

Reconecta el servicio:

```bash
docker network connect net-onprem orion-aws-api
```

Prueba nuevamente:

```bash
docker exec -it orion-onprem-client \
  curl http://orion-api/api/v1/health
```

La API debe volver a responder.

---

# 16. Paso 11 — Simular una falla del servicio

Detén la API:

```bash
docker stop orion-aws-api
```

Prueba:

```bash
docker exec -it orion-onprem-client \
  curl --connect-timeout 3 http://orion-api/api/v1/health
```

Ahora inicia nuevamente:

```bash
docker start orion-aws-api
```

Comprueba:

```bash
docker exec -it orion-onprem-client \
  curl http://orion-api/api/v1/health
```

---

# 17. Paso 12 — Alta disponibilidad - HA

Una arquitectura híbrida puede utilizar redundancia.

```text
                    DNS / Load Balancer
                           │
                  ┌────────┴────────┐
                  ▼                 ▼
              On-Premise          AWS
                  │                 │
              Service A          Service B
                  │                 │
                  └───────┬─────────┘
                          ▼
                    Datos / Core
```

En este laboratorio no implementamos Active-Active real.

El objetivo es identificar qué elemento debería ser redundante en una arquitectura productiva.

---

# 18. Paso 13 — RTO y RPO

En el laboratorio del curso se plantea para ORION:

```text
RTO < 15 minutos
RPO ≈ 0
```

Recordatorio:

### RTO

¿Cuánto tiempo puede estar interrumpido el servicio?

Ejemplo:

```text
Servicio cae 10:00
Servicio recuperado 10:12

RTO = 12 minutos
```

### RPO

¿Cuánta información podemos perder?

Ejemplo:

```text
Última copia válida: 09:59
Falla: 10:00

RPO = 1 minuto
```

---

# 19. Paso 14 — Identificar la estrategia de DR

Relaciona los siguientes escenarios:

|    Estrategia    |              Característica                  |
|------------------|----------------------------------------------|
| Backup & Restore | Recuperar desde respaldos                    |
| Pilot Light      | Infraestructura mínima preparada             |
| Warm Standby     | Ambiente reducido permanentemente disponible |
| Active-Active    | Ambos ambientes atienden tráfico             |


---

# 20. Paso 15 — Analizar dónde colocar cada workload

Completa la tabla:

|    Componente     | On-Premise | AWS | Híbrido | Justificación |
|-------------------|------------|-----|---------|---------------|
| Oracle Legacy     | ☐         | ☐   | ☐      |               |
| API Gateway       | ☐         | ☐   | ☐      |               |
| Microservicio web | ☐         | ☐   | ☐      |               |
| Datos regulados   | ☐         | ☐   | ☐      |               |
| Analítica Big Data| ☐         | ☐   | ☐      |               |
| Caché distribuida | ☐         | ☐   | ☐      |               |

Considera:

- latencia;
- soberanía de datos;
- cumplimiento;
- escalabilidad;
- costo;
- dependencia de sistemas legacy.

---

# 21. Paso 16 — Analizar CIDR

En la arquitectura del laboratorio:

```text
On-Premise = 10.10.0.0/16
AWS        = 10.20.0.0/16
```

### ¿Por qué no debemos usar el mismo CIDR?

Ejemplo incorrecto:

```text
On-Premise = 10.10.0.0/16
AWS        = 10.10.0.0/16
```

El enrutamiento tendría una ambigüedad:

```text
¿10.10.20.15 está en AWS
o está On-Premise?
```

Por eso la planificación de direccionamiento es una decisión fundamental antes de interconectar ambientes.

---

# 22. Paso 17 — VPN, Direct Connect y Transit Gateway

En una arquitectura AWS real:

```text
                 ┌───────────────┐
                 │ Transit       │
                 │ Gateway       │
                 └───────┬───────┘
                         │
              ┌──────────┴──────────┐
              │                     │
            VPN                   DX
              │                     │
              ▼                     ▼
        On-Premise              On-Premise
```

### VPN IPSec

Túnel cifrado sobre Internet.

### Direct Connect

Enlace dedicado entre el entorno local y AWS.

### Transit Gateway

Hub central para conectar múltiples VPC y redes.

En este laboratorio:

```text
Docker network
      ↓
simula
      ↓
segmentación/conectividad híbrida
```

No sustituye la implementación real de estos servicios AWS.

---

# 23. Preguntas de análisis

### Pregunta 1
¿Por qué una organización podría mantener una base de datos legacy On-Premise mientras despliega nuevos microservicios en AWS?

### Pregunta 2
¿Qué problema aparece si On-Premise y AWS utilizan el mismo rango CIDR?

### Pregunta 3
¿Cuál es la diferencia entre VPN IPSec y Direct Connect?

### Pregunta 4
¿Qué función cumple un Transit Gateway?

### Pregunta 5
¿Por qué una API Gateway puede ser útil en una arquitectura de microservicios?

### Pregunta 6
¿Qué diferencia existe entre RTO y RPO?

### Pregunta 7
Si el servicio se recupera en 8 minutos pero se pierden 30 minutos de información, ¿qué métrica se cumple y cuál no?

### Pregunta 8
¿Por qué la configuración debe estar desacoplada del código?

### Pregunta 9
¿Qué ocurre si se cae el enlace híbrido pero los servicios individuales siguen funcionando?

### Pregunta 10
¿Qué estrategia de DR utilizarías para una aplicación que necesita recuperarse rápidamente pero tiene restricciones de presupuesto? Justifica.

---

# 24. Limpieza del laboratorio

Cuando termines:

```bash
docker compose down
```

Para eliminar también las redes creadas por Compose:

```bash
docker compose down --remove-orphans
```

Verifica:

```bash
docker ps -a
```

---

# 25. Reto opcional

## Reto 1 — Cambiar el ambiente

Modifica:

```env
APP_ENV=production
```

Recrea:

```bash
docker compose up -d --build
```

Comprueba que la API identifica el nuevo ambiente.

## Reto 2 — Cambiar la latencia simulada

Modifica la configuración de la API para agregar un retraso artificial.

Compara:

```text
latencia normal
vs.
latencia con enlace degradado
```

## Reto 3 — Segundo microservicio

Agrega un segundo servicio:

```text
inventory-service
```

y permite que:

```text
ORION Client
      │
      ▼
Gateway
   ┌──┴─────────────┐
   ▼                ▼
sales-service   inventory-service
```

## Reto 4 — Health Check

Agrega un endpoint:

```text
/api/v1/health
```

y otro:

```text
/api/v1/ready
```

Revisa la diferencia entre:

- liveness;
- readiness.

---

# 26. Diagrama de flujo final

```text
Usuario
   │
   ▼
API Gateway / Ingress
   │
   ▼
Microservicio AWS
   │
   │
   ├──────────────► Otro microservicio AWS
   │
   └──────────────► Core / DB On-Premise
                         │
                         ▼
                    Datos Legacy
```

> **¿Por qué este sistema es híbrido?**

Porque los componentes no están ubicados todos en un único ambiente: parte de la solución permanece On-Premise y otra parte se ejecuta en AWS, comunicándose mediante una conectividad entre ambos entornos.

---