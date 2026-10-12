# Laboratorio Módulo 2 — Docker avanzado para ambientes productivos

## Proyecto: TechStore Container Lab

En este laboratorio vamos a contenerizar y ejecutar una aplicación empresarial utilizando **Docker directamente**, sin Docker Compose.

La aplicación está formada por:

* **product-service**: microservicio Java 21 / Spring Boot.
* **order-service**: microservicio Java 21 / Spring Boot.
* **web-app**: aplicación React.
* **PostgreSQL**: base de datos.

---

# 1. Objetivos

Al finalizar el laboratorio podrás:

* Construir imágenes Docker para aplicaciones Java 21.
* Construir una imagen Docker multistage para React.
* Aplicar layer cache.
* Utilizar `.dockerignore`.
* Configurar aplicaciones mediante variables de entorno.
* Crear una red Docker.
* Ejecutar PostgreSQL como contenedor.
* Ejecutar dos microservicios Java como contenedores independientes.
* Ejecutar una aplicación React como contenedor.
* Permitir comunicación entre contenedores mediante DNS de Docker.
* Utilizar health checks.
* Aplicar límites de CPU y memoria.
* Ejecutar contenedores como usuario non-root.
* Analizar imágenes con Trivy.
* Versionar imágenes.
* Publicar imágenes en Docker Hub.
* Firmar una imagen con Cosign.
* Comprender el concepto de inmutabilidad.

---

# 2. Arquitectura

La aplicación tendrá la siguiente arquitectura:

```text
                         NAVEGADOR
                             │
                             │ http://localhost:3000
                             ▼
                  ┌─────────────────────┐
                  │       React         │
                  │      web-app        │
                  │       :80           │
                  └──────────┬──────────┘
                             │
                 ┌───────────┴───────────┐
                 │                       │
                 ▼                       ▼
        ┌─────────────────┐      ┌─────────────────┐
        │ product-service │      │  order-service  │
        │     Java 21     │◄─────│     Java 21     │
        │      :8081      │ REST │      :8082      │
        └────────┬────────┘      └────────┬────────┘
                 │                        │
                 └───────────┬────────────┘
                             ▼
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │      :5432      │
                    └─────────────────┘
```

Todos los contenedores estarán conectados a una red Docker:

```text
techstore-net
```

---

# 3. Requisitos

Instalar:

* Docker Desktop.
* Git.
* Navegador web.

Verificar:

```bash
docker --version
```

```bash
docker network --version
```

También podemos verificar:

```bash
docker info
```

---

# 4. Estructura del proyecto

La estructura esperada es:

```text
lab-modulo2-docker-advanced/
│
├── product-service/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/
│
├── order-service/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/
│
└── web-app/
    ├── Dockerfile
    ├── .dockerignore
    ├── nginx.conf
    ├── package.json
    └── src/
```

---

# 5. Concepto clave: Dockerfile → Image → Container

Antes de ejecutar la aplicación debemos entender el flujo:

```text
Dockerfile
     │
     │ docker build
     ▼
   IMAGE
     │
     │ docker run
     ▼
 CONTAINER
```

El Dockerfile contiene las instrucciones para construir la imagen.

La imagen es el artefacto empaquetado.

El contenedor es una instancia ejecutable de esa imagen.

---

# 6. Crear la red Docker

Como no utilizaremos Docker Compose, nosotros mismos debemos crear la red.

Ejecutar:

```bash
docker network create techstore-net
```

Verificar:

```bash
docker network ls
```

Debemos encontrar:

```text
techstore-net
```

Inspeccionar:

```bash
docker network inspect techstore-net
```

---

# 7. ¿Por qué necesitamos una red?

Los contenedores necesitan comunicarse entre ellos.

Por ejemplo:

```text
order-service
      │
      │ HTTP
      ▼
product-service
```

Y:

```text
product-service
      │
      ▼
   postgres
```

En una red Docker podemos utilizar el nombre del contenedor como hostname.

Por ejemplo:

```text
postgres
```

será resoluble desde otros contenedores conectados a:

```text
techstore-net
```

Por eso NO debemos utilizar:

```text
localhost
```

para comunicarnos con otro contenedor.

---

# 8. Crear el volumen para PostgreSQL

PostgreSQL necesita almacenamiento persistente.

Crear:

```bash
docker volume create techstore-postgres-data
```

Verificar:

```bash
docker volume ls
```

---

# 9. Ejecutar PostgreSQL

Primero descargaremos la imagen:

```bash
docker pull postgres:16-alpine
```

Ejecutar:

```bash
docker run -d \
  --name techstore-postgres \
  --network techstore-net \
  -e POSTGRES_DB=techstore \
  -e POSTGRES_USER=techstore \
  -e POSTGRES_PASSWORD=techstore_dev \
  -p 5432:5432 \
  -v techstore-postgres-data:/var/lib/postgresql/data \
  --health-cmd="pg_isready -U techstore -d techstore" \
  --health-interval=10s \
  --health-timeout=5s \
  --health-retries=5 \
  postgres:16-alpine
```

En Windows PowerShell, si el comando multilinea anterior genera problemas, utilizarlo en una sola línea:

```powershell
docker run -d --name techstore-postgres --network techstore-net -e POSTGRES_DB=techstore -e POSTGRES_USER=techstore -e POSTGRES_PASSWORD=techstore_dev -p 5432:5432 -v techstore-postgres-data:/var/lib/postgresql/data --health-cmd="pg_isready -U techstore -d techstore" --health-interval=10s --health-timeout=5s --health-retries=5 postgres:16-alpine
```

Verificar:

```bash
docker ps
```

Ver el health:

```bash
docker inspect \
  --format="{{.State.Health.Status}}" \
  techstore-postgres
```

Debe llegar a:

```text
healthy
```

---

# 10. Revisar PostgreSQL

Ver logs:

```bash
docker logs techstore-postgres
```

Seguir logs:

```bash
docker logs -f techstore-postgres
```

Salir de los logs:

```text
CTRL + C
```

---

# 11. Construir product-service

Entrar a:

```text
product-service/
```

Construir:

```bash
docker build \
  -t techstore/product-service:v1.0.0 \
  .
```

Desde la raíz también podemos ejecutar:

```bash
docker build \
  -t techstore/product-service:v1.0.0 \
  ./product-service
```

---

# 12. Analizar el Dockerfile

El Dockerfile utiliza dos etapas:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
```

y:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

La primera etapa es el:

```text
BUILD
```

La segunda:

```text
RUNTIME
```

Esto se conoce como:

```text
Multi-Stage Build
```

---

# 13. ¿Por qué utilizar Multi-Stage?

Durante la compilación necesitamos:

* JDK.
* Maven.
* dependencias.
* herramientas de compilación.

Pero para ejecutar:

```text
app.jar
```

no necesitamos todo ese entorno.

Por eso:

```text
BUILD IMAGE
    │
    │ Maven
    │ JDK
    │ dependencies
    ▼
  app.jar
    │
    ▼
RUNTIME IMAGE
    │
    │ JRE
    │ app.jar
    ▼
 Container
```

Esto reduce el tamaño y la superficie de ataque.

---

# 14. Layer Cache

El Dockerfile contiene:

```dockerfile
COPY pom.xml .
RUN apk add --no-cache maven \
    && mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests
```

Observa que:

```text
pom.xml
```

se copia antes de:

```text
src/
```

Esto permite aprovechar el cache.

Construir:

```bash
docker build -t techstore/product-service:v1.0.0 ./product-service
```

Construir nuevamente:

```bash
docker build -t techstore/product-service:v1.0.1 ./product-service
```

Observar:

```text
CACHED
```

Pregunta:

> ¿Qué ocurriría si modificamos solamente una clase Java?

---

# 15. Verificar la imagen

```bash
docker images
```

También:

```bash
docker image ls techstore/product-service
```

Inspeccionar:

```bash
docker image inspect techstore/product-service:v1.0.0
```

---

# 16. Ejecutar product-service

Ejecutar:

```bash
docker run -d \
  --name techstore-product-service \
  --network techstore-net \
  -e SERVER_PORT=8081 \
  -e DB_HOST=techstore-postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=techstore \
  -e DB_USER=techstore \
  -e DB_PASSWORD=techstore_dev \
  -p 8081:8081 \
  --memory=512m \
  --cpus=1 \
  techstore/product-service:v1.0.0
```

En PowerShell:

```powershell
docker run -d --name techstore-product-service --network techstore-net -e SERVER_PORT=8081 -e DB_HOST=techstore-postgres -e DB_PORT=5432 -e DB_NAME=techstore -e DB_USER=techstore -e DB_PASSWORD=techstore_dev -p 8081:8081 --memory=512m --cpus=1 techstore/product-service:v1.0.0
```

---

# 17. ¿Por qué DB_HOST no es localhost?

Tenemos:

```text
product-service
       │
       │
       ▼
techstore-postgres
```

Los dos contenedores están en:

```text
techstore-net
```

Por lo tanto:

```text
DB_HOST=techstore-postgres
```

Docker proporciona resolución DNS entre contenedores conectados a la misma red.

Si utilizáramos:

```text
DB_HOST=localhost
```

el microservicio intentaría buscar PostgreSQL dentro de su propio contenedor.

---

# 18. Verificar product-service

Ver contenedores:

```bash
docker ps
```

Logs:

```bash
docker logs techstore-product-service
```

Health:

```bash
curl http://localhost:8081/actuator/health
```

En PowerShell también:

```powershell
Invoke-WebRequest http://localhost:8081/actuator/health
```

Productos:

```text
http://localhost:8081/api/products
```

---

# 19. Construir order-service

Ejecutar:

```bash
docker build \
  -t techstore/order-service:v1.0.0 \
  ./order-service
```

Verificar:

```bash
docker images
```

---

# 20. Ejecutar order-service

Ejecutar:

```bash
docker run -d \
  --name techstore-order-service \
  --network techstore-net \
  -e SERVER_PORT=8082 \
  -e DB_HOST=techstore-postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=techstore \
  -e DB_USER=techstore \
  -e DB_PASSWORD=techstore_dev \
  -e PRODUCT_SERVICE_URL=http://techstore-product-service:8081 \
  -p 8082:8082 \
  --memory=512m \
  --cpus=1 \
  techstore/order-service:v1.0.0
```

En PowerShell:

```powershell
docker run -d --name techstore-order-service --network techstore-net -e SERVER_PORT=8082 -e DB_HOST=techstore-postgres -e DB_PORT=5432 -e DB_NAME=techstore -e DB_USER=techstore -e DB_PASSWORD=techstore_dev -e PRODUCT_SERVICE_URL=http://techstore-product-service:8081 -p 8082:8082 --memory=512m --cpus=1 techstore/order-service:v1.0.0
```

---

# 21. Comunicación entre microservicios

Tenemos:

```text
order-service
     │
     │ HTTP
     │
     ▼
http://techstore-product-service:8081
     │
     ▼
product-service
```

La variable:

```text
PRODUCT_SERVICE_URL
```

contiene:

```text
http://techstore-product-service:8081
```

No:

```text
http://localhost:8081
```

---

# 22. Verificar order-service

```bash
docker ps
```

Logs:

```bash
docker logs techstore-order-service
```

Health:

```bash
curl http://localhost:8082/actuator/health
```

En PowerShell:

```powershell
Invoke-WebRequest http://localhost:8082/actuator/health
```

Consultar órdenes:

```text
http://localhost:8082/api/orders
```

---

# 23. Construir React

Construir:

```bash
docker build \
  --build-arg VITE_PRODUCT_API_URL=http://localhost:8081 \
  --build-arg VITE_ORDER_API_URL=http://localhost:8082 \
  -t techstore/web-app:v1.0.0 \
  ./web-app
```

---

# 24. Dockerfile del frontend

El frontend también utiliza Multi-Stage Build.

Primera etapa:

```dockerfile
FROM node:22-alpine AS builder
```

Aquí necesitamos:

```text
Node
npm
dependencias
código fuente
```

Después:

```dockerfile
RUN npm run build
```

Se genera:

```text
dist/
```

La segunda etapa utiliza:

```dockerfile
FROM nginx:alpine
```

y solamente copia:

```text
dist/
```

al servidor Nginx.

---

# 25. Ejecutar React

```bash
docker run -d \
  --name techstore-web-app \
  --network techstore-net \
  -p 3000:80 \
  --memory=128m \
  --cpus=0.5 \
  techstore/web-app:v1.0.0
```

En PowerShell:

```powershell
docker run -d --name techstore-web-app --network techstore-net -p 3000:80 --memory=128m --cpus=0.5 techstore/web-app:v1.0.0
```

---

# 26. Verificar la aplicación completa

Ejecutar:

```bash
docker ps
```

Debemos tener:

```text
techstore-postgres
techstore-product-service
techstore-order-service
techstore-web-app
```

Abrir:

```text
http://localhost:3000
```

---

# 27. Flujo completo

La aplicación funciona así:

```text
Browser
   │
   │ localhost:3000
   ▼
web-app
   │
   ├──────────────► localhost:8081
   │                       │
   │                       ▼
   │                product-service
   │                       │
   │                       ▼
   │                  PostgreSQL
   │
   └──────────────► localhost:8082
                           │
                           ▼
                     order-service
                           │
                           │
                           └────► product-service
```

---

# 28. Ver la red Docker

Ejecutar:

```bash
docker network inspect techstore-net
```

Buscar:

```text
Containers
```

Deberían aparecer los cuatro contenedores.

Esto demuestra que todos están conectados a la misma red.

---

# 29. Probar resolución DNS

Entrar al contenedor de `order-service`:

```bash
docker exec -it techstore-order-service sh
```

Dentro:

```bash
wget -qO- http://techstore-product-service:8081/api/products
```

Salir:

```bash
exit
```

Esto demuestra que:

```text
techstore-order-service
```

puede resolver:

```text
techstore-product-service
```

mediante DNS interno de Docker.

---

# 30. Inspeccionar variables de entorno

Ejecutar:

```bash
docker inspect techstore-order-service
```

Buscar:

```text
Env
```

Aquí podemos observar las variables configuradas.

### Importante

Las variables de entorno son apropiadas para configuración no sensible.

No debemos asumir que colocar un secreto en:

```text
ENV
```

lo convierte en un secreto seguro.

---

# 31. Health Check

El contenedor PostgreSQL tiene un health check.

Consultar:

```bash
docker inspect \
  --format="{{.State.Health.Status}}" \
  techstore-postgres
```

También podemos revisar:

```bash
docker inspect techstore-postgres
```

Buscar:

```text
Health
```

---

# 32. Reto 1 — Non-Root

Actualmente debemos analizar:

```text
¿Con qué usuario se ejecuta product-service?
```

Ejecutar:

```bash
docker exec techstore-product-service id
```

Si aparece:

```text
uid=0(root)
```

tenemos un problema de seguridad.

Modificar el Dockerfile para crear:

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
```

Asignar propietario:

```dockerfile
RUN chown appuser:appgroup app.jar
```

Y ejecutar:

```dockerfile
USER appuser
```

Reconstruir:

```bash
docker build \
  -t techstore/product-service:v1.0.1 \
  ./product-service
```

Eliminar el contenedor anterior:

```bash
docker rm -f techstore-product-service
```

Ejecutar nuevamente:

```bash
docker run -d \
  --name techstore-product-service \
  --network techstore-net \
  -e SERVER_PORT=8081 \
  -e DB_HOST=techstore-postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=techstore \
  -e DB_USER=techstore \
  -e DB_PASSWORD=techstore_dev \
  -p 8081:8081 \
  --memory=512m \
  --cpus=1 \
  techstore/product-service:v1.0.1
```

Verificar:

```bash
docker exec techstore-product-service id
```

---

# 33. Reto 2 — Trivy

Instalar Trivy según el sistema operativo.

Analizar:

```bash
trivy image techstore/product-service:v1.0.1
```

Filtrar:

```bash
trivy image \
  --severity HIGH,CRITICAL \
  techstore/product-service:v1.0.1
```

Preguntas:

1. ¿Qué vulnerabilidades aparecen?
2. ¿Son del código Java?
3. ¿Son de la imagen base?
4. ¿Qué versión de la imagen base estamos utilizando?
5. ¿Deberíamos bloquear un build con vulnerabilidades críticas?


---

# 34. Reto 3 — Inmutabilidad

Ejecutar un contenedor con filesystem read-only:

```bash
docker run -d \
  --name product-readonly \
  --network techstore-net \
  --read-only \
  --tmpfs /tmp \
  -e SERVER_PORT=8081 \
  -e DB_HOST=techstore-postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=techstore \
  -e DB_USER=techstore \
  -e DB_PASSWORD=techstore_dev \
  -p 8083:8081 \
  techstore/product-service:v1.0.1
```

Intentar escribir:

```bash
docker exec product-readonly sh -c "touch /test.txt"
```

¿Qué debería ocurrir?

El filesystem principal debería ser de solo lectura.

---

# 35. Reto 4 — Eliminar capacidades

Ejecutar:

```bash
docker run --rm \
  --cap-drop=ALL \
  techstore/product-service:v1.0.1
```

Pregunta:

> ¿Qué significa `--cap-drop=ALL`?


---

# 36. Reto 5 — Versionado

Crear una nueva versión:

```bash
docker tag \
  techstore/product-service:v1.0.1 \
  techstore/product-service:v1.1.0
```

Verificar:

```bash
docker images techstore/product-service
```

Obtener el Git SHA:

```bash
git rev-parse --short HEAD
```

Por ejemplo:

```text
a1b2c3d
```

Crear:

```bash
docker tag \
  techstore/product-service:v1.1.0 \
  techstore/product-service:a1b2c3d
```

Ahora tenemos:

```text
v1.1.0
a1b2c3d
```

La primera representa una versión funcional.

La segunda permite rastrear el artefacto hasta un commit específico.

---

# 37. ¿Por qué evitar latest?

Evitar:

```text
techstore/product-service:latest
```

en producción.

Problema:

```text
latest
   │
   ├── hoy → código A
   │
   └── mañana → código B
```

El tag no nos dice exactamente qué código está ejecutándose.

Es preferible:

```text
v1.1.0
```

y/o:

```text
a1b2c3d
```

---

# 38. Reto 6 — Docker Hub

Crear una cuenta en:

```text
https://hub.docker.com/
```

Crear el Docker ID.

Por ejemplo:

```text
miusuario
```

Crear los repositories:

```text
miusuario/techstore-product-service
miusuario/techstore-order-service
miusuario/techstore-web-app
```

---

# 39. Login

Ejecutar:

```bash
docker login
```

Ingresar las credenciales solicitadas.

Para automatización empresarial se recomienda utilizar un mecanismo de autenticación apropiado, como un Access Token, y no colocar contraseñas directamente en scripts.

---

# 40. Tag para Docker Hub

Ejemplo:

```bash
docker tag \
  techstore/product-service:v1.1.0 \
  TU_DOCKER_ID/techstore-product-service:v1.1.0
```

Para order-service:

```bash
docker tag \
  techstore/order-service:v1.0.0 \
  TU_DOCKER_ID/techstore-order-service:v1.0.0
```

Para React:

```bash
docker tag \
  techstore/web-app:v1.0.0 \
  TU_DOCKER_ID/techstore-web-app:v1.0.0
```

---

# 41. Push a Docker Hub

```bash
docker push \
  TU_DOCKER_ID/techstore-product-service:v1.1.0
```

```bash
docker push \
  TU_DOCKER_ID/techstore-order-service:v1.0.0
```

```bash
docker push \
  TU_DOCKER_ID/techstore-web-app:v1.0.0
```

Ingresar a Docker Hub y verificar los repositories.

---

# 42. Reto 7 — Firma con Cosign

Instalar Cosign.

Generar una pareja de claves:

```bash
cosign generate-key-pair
```

Se generan:

```text
cosign.key
cosign.pub
```

Agregar al `.gitignore`:

```text
cosign.key
```

Nunca subir la clave privada al repositorio Git.

Firmar:

```bash
cosign sign \
  --key cosign.key \
  TU_DOCKER_ID/techstore-product-service:v1.1.0
```

Verificar:

```bash
cosign verify \
  --key cosign.pub \
  TU_DOCKER_ID/techstore-product-service:v1.1.0
```

Pregunta:

> ¿Qué problema intenta resolver una firma digital de imagen?


---

# 43. Reto final — Diagnóstico

> `order-service` está levantado, pero no puede comunicarse con `product-service`.

Solución:

### Paso 1

Verificar:

```bash
docker ps
```

### Paso 2

Verificar la red:

```bash
docker network inspect techstore-net
```

### Paso 3

Verificar logs:

```bash
docker logs techstore-order-service
```

### Paso 4

Entrar al contenedor:

```bash
docker exec -it techstore-order-service sh
```

### Paso 5

Probar:

```bash
wget -qO- http://techstore-product-service:8081/api/products
```

### Posibles causas

#### Caso 1

`order-service` no está conectado a la red:

```text
techstore-net
```

Solución:

```bash
docker network connect techstore-net techstore-order-service
```

#### Caso 2

La variable apunta a localhost:

```text
PRODUCT_SERVICE_URL=http://localhost:8081
```

Debe ser:

```text
PRODUCT_SERVICE_URL=http://techstore-product-service:8081
```

#### Caso 3

El nombre del contenedor es incorrecto.

Verificar:

```bash
docker ps --format "{{.Names}}"
```

---

# 44. Preguntas conceptuales:

### Pregunta 1

¿Por qué `localhost` funciona desde el navegador pero no desde `order-service`?

### Respuesta

El navegador está fuera de los contenedores.

Desde el navegador:

```text
localhost:8081
```

significa el host.

Desde `order-service`:

```text
localhost
```

significa el propio contenedor `order-service`.

---

### Pregunta 2

¿Por qué usamos:

```text
techstore-product-service
```

?

### Respuesta

Porque Docker proporciona resolución DNS para contenedores conectados a la misma red.

---

### Pregunta 3

¿Por qué usamos:

```text
-p 8081:8081
```

?

### Respuesta

Porque queremos publicar el puerto del contenedor hacia el host:

```text
HOST:8081
     │
     ▼
CONTAINER:8081
```

---

# 45. Ver todos los contenedores

```bash
docker ps
```

Resultado esperado:

```text
techstore-postgres
techstore-product-service
techstore-order-service
techstore-web-app
```

---

# 46. Ver consumo de recursos

Ejecutar:

```bash
docker stats
```

Podemos observar:

```text
CPU
MEM USAGE
MEM %
NET I/O
BLOCK I/O
PIDS
```

Esto permite observar el efecto de los límites:

```text
--memory
--cpus
```

---

# 47. Detener la aplicación

Detener:

```bash
docker stop techstore-web-app
docker stop techstore-order-service
docker stop techstore-product-service
docker stop techstore-postgres
```

---

# 48. Eliminar contenedores

```bash
docker rm techstore-web-app
docker rm techstore-order-service
docker rm techstore-product-service
docker rm techstore-postgres
```

---

# 49. Eliminar la red

Después de eliminar los contenedores:

```bash
docker network rm techstore-net
```

---

# 50. Eliminar el volumen

Si queremos eliminar también los datos de PostgreSQL:

```bash
docker volume rm techstore-postgres-data
```

> Atención: esto elimina los datos persistidos de PostgreSQL.

---
