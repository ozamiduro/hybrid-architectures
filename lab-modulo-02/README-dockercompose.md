# Laboratorio Módulo 2 — Docker avanzado para ambientes productivos

## Proyecto: TechStore Container Lab

En este laboratorio vamos a contenerizar una pequeña aplicación empresarial compuesta por:

- `product-service`: microservicio Java 21 / Spring Boot que administra productos.
- `order-service`: microservicio Java 21 / Spring Boot que registra órdenes y consulta `product-service`.
- `web-app`: frontend React que consume los microservicios.
- `postgres`: base de datos PostgreSQL.

El laboratorio está diseñado para **2 horas** y parte de una base en conocimientos de Docker, imágenes, contenedores y comandos básicos.

### Relación con la clase

El laboratorio practica directamente los conceptos de la presentación:

1. Arquitectura y funcionamiento de Docker.
2. Construcción de imágenes backend y frontend.
3. Dockerfiles multietapa.
4. Optimización de imágenes y tiempos de construcción.
5. Variables de ambiente y configuración.
6. Health checks y límites de recursos.
7. Seguridad y análisis de vulnerabilidades con Trivy.
8. Versionado y publicación de imágenes.
9. Ejecución non-root, inmutabilidad y firma.
10. Contenerización de microservicios.

---

# 1. Objetivos

Al finalizar el laboratorio, podrás:

- Construir imágenes Docker para aplicaciones Java 21.
- Construir una imagen multistage para React.
- Aplicar layer cache correctamente.
- Utilizar `.dockerignore`.
- Separar configuración del código mediante variables de ambiente.
- Levantar una aplicación completa con Docker Compose.
- Agregar health checks.
- Aplicar límites de CPU y memoria.
- Ejecutar contenedores con un usuario no root.
- Analizar imágenes con Trivy.
- Versionar imágenes con SemVer y Git SHA.
- Crear una cuenta/repository en Docker Hub.
- Publicar imágenes versionadas.
- Firmar una imagen con Cosign.
- Comprender el concepto de imagen inmutable.

---

# 2. Arquitectura

```text
                         ┌─────────────────────┐
                         │      React SPA       │
                         │       web-app        │
                         │      :3000           │
                         └──────────┬──────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
                    ▼                               ▼
          ┌──────────────────┐             ┌──────────────────┐
          │ product-service  │             │  order-service   │
          │ Java 21           │◄────────────│ Java 21          │
          │ :8081             │  REST       │ :8082            │
          └────────┬─────────┘             └────────┬─────────┘
                   │                                │
                   └──────────────┬─────────────────┘
                                  ▼
                         ┌─────────────────┐
                         │   PostgreSQL    │
                         │      :5432      │
                         └─────────────────┘
```

> En una arquitectura real, cada microservicio podría tener su propia base de datos. Para este laboratorio usamos una PostgreSQL compartida para reducir la complejidad y concentrarnos en Docker.

---

# 3. Requisitos

Instalar:

- Docker Desktop actualizado.
- Docker Compose incluido en Docker Desktop.
- Git.
- Cuenta Docker Hub.
- Navegador web.

Verificar:

```bash
docker --version
docker compose version
git --version
```

Opcional para ejecutar los proyectos Java/React fuera de Docker:

```bash
java -version
mvn -version
node --version
npm --version
```

Para la primera parte del laboratorio **no es obligatorio tener Java ni Node instalados**, porque la compilación se realizará dentro de los Dockerfiles multistage.

---

# 4. Estructura del proyecto

```text
lab-modulo2-docker-advanced/
│
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
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
    ├── vite.config.js
    ├── index.html
    └── src/
```

---

# 5. Parte A — Entender antes de construir

## 5.1 Dockerfile → Image → Container

El flujo que vamos a utilizar es:

```text
Dockerfile
    │
    │ docker build
    ▼
  IMAGE
    │
    │ docker run / docker compose
    ▼
CONTAINER
```

> ¿Dónde está la aplicación: en el Dockerfile, en la imagen o en el contenedor?

---

# 6. Parte B — Revisar el Dockerfile Java

Abrir:

```text
product-service/Dockerfile
```

y:

```text
order-service/Dockerfile
```

El patrón principal es:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## ¿Por qué hay dos FROM?

Porque estamos usando **Multi-Stage Build**.

La primera etapa contiene:

- JDK.
- Maven.
- herramientas de compilación.
- dependencias necesarias para compilar.

La segunda contiene solamente lo necesario para ejecutar el JAR.

---

# 7. Parte C — Layer Cache

Observa este orden:

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests
```

¿Por qué no hacemos directamente?

```dockerfile
COPY . .
RUN mvn clean package
```

Porque si modificamos solamente:

```text
src/main/java/...
```

queremos conservar la capa donde descargamos las dependencias.

La idea es:

```text
pom.xml
   │
   └── dependencia cacheada
              │
              ▼
           source code
```

## Demostración

Construye:

```bash
docker build -t techstore/product-service:v1.0.0 ./product-service
```

Vuelve a construir:

```bash
docker build -t techstore/product-service:v1.0.1 ./product-service
```

Observa qué pasos aparecen como `CACHED`.

> ¿Qué cambio debería obligarnos a descargar nuevamente las dependencias?

---

# 8. Parte D — .dockerignore

Revisa:

```text
product-service/.dockerignore
order-service/.dockerignore
web-app/.dockerignore
```

Ejemplo:

```text
.git
.gitignore
target
node_modules
.env
*.log
.idea
.vscode
README.md
```

La finalidad es evitar enviar al contexto de build:

- repositorio Git;
- artefactos compilados;
- `node_modules`;
- archivos `.env`;
- logs;
- archivos del IDE.

> ¿Por qué `.env` debe estar en `.dockerignore`?

---

# 9. Parte E — Variables de configuración

No colocar credenciales dentro del Dockerfile.

En Compose:

```yaml
environment:
  DB_HOST: postgres
  DB_PORT: 5432
  DB_NAME: techstore
  DB_USER: techstore
  DB_PASSWORD: techstore_dev
```

La aplicación Java lee:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

Esto permite cambiar la configuración sin reconstruir la imagen.

## Importante

Una variable de entorno **no es un mecanismo de secreto por sí misma**.

---

# 10. Parte F — Construir las imágenes

Desde la raíz:

```bash
docker build -t techstore/product-service:v1.0.0 ./product-service
```

```bash
docker build -t techstore/order-service:v1.0.0 ./order-service
```

```bash
docker build -t techstore/web-app:v1.0.0 ./web-app
```

Listar:

```bash
docker images | findstr techstore
```

En Linux/macOS:

```bash
docker images | grep techstore
```

Inspeccionar:

```bash
docker image inspect techstore/product-service:v1.0.0
```

---

# 11. Parte G — Ejecutar todo con Docker Compose

Construir y arrancar:

```bash
docker compose up -d --build
```

Ver servicios:

```bash
docker compose ps
```

Ver logs:

```bash
docker compose logs -f
```

Ver solamente product-service:

```bash
docker compose logs -f product-service
```

Ver solamente order-service:

```bash
docker compose logs -f order-service
```

---

# 12. Parte H — Probar la aplicación

## Productos

Abrir:

```text
http://localhost:8081/api/products
```

## Health del product-service

```text
http://localhost:8081/actuator/health
```

## Health del order-service

```text
http://localhost:8082/actuator/health
```

## Frontend

Abrir:

```text
http://localhost:3000
```

El frontend permite:

1. consultar productos;
2. seleccionar producto;
3. indicar cantidad;
4. crear una orden.

---

# 13. Parte I — Docker Compose

Revisar:

```yaml
services:
  product-service:
    build: ./product-service
    ports:
      - "8081:8081"
    environment:
      ...
    depends_on:
      postgres:
        condition: service_healthy
```

Pregunta:

> ¿Por qué la aplicación Java utiliza `postgres` como DB_HOST y no `localhost`?

---

# 14. Parte J — Health Checks

Compose contiene health checks.

Ejemplo:

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health | grep -q 'UP'"]
  interval: 30s
  timeout: 5s
  retries: 3
  start_period: 20s
```

Consultar:

```bash
docker compose ps
```

También:

```bash
docker inspect --format='{{json .State.Health}}' techstore-product-service
```

El objetivo es comprobar el estado real de la aplicación y no únicamente que el proceso exista.

---

# 15. Parte K — Límites de recursos

Ejemplo:

```yaml
mem_limit: 512m
cpus: 1.0
```

Aplicarlo a los microservicios.

Después:

```bash
docker compose up -d
```

Inspeccionar:

```bash
docker stats
```

> ¿Por qué es peligroso permitir que un contenedor consuma toda la memoria disponible del host?

---

# 16. RETO 1 — Mejorar el Dockerfile Java

```text
product-service/Dockerfile
```

1. ¿Dónde está el layer cache?
2. ¿Qué archivos llegan a la imagen final?
3. ¿El runtime necesita Maven?
4. ¿El runtime necesita el JDK completo?
5. ¿Qué usuario ejecuta el proceso?

---

# 17. RETO 2 — Ejecutar como Non-Root

La presentación indica que los contenedores no deberían ejecutarse con privilegios administrativos innecesarios.

Modificar el Dockerfile de `product-service`.

### Pregunta

¿Por qué non-root reduce el impacto de una vulnerabilidad?

---

# 18. RETO 3 — Trivy

Instalar Trivy siguiendo la documentación oficial correspondiente a su sistema operativo.

Analizar:

```bash
trivy image techstore/product-service:v1.0.1
```

Filtrar:

```bash
trivy image --severity HIGH,CRITICAL techstore/product-service:v1.0.1
```

Interpretar:

```text
CRITICAL
HIGH
MEDIUM
LOW
UNKNOWN
```

### Preguntas

1. ¿Una vulnerabilidad encontrada significa automáticamente que nuestra aplicación está explotada?
2. ¿Debemos analizar solamente el código Java?
3. ¿Qué más puede analizar Trivy?

---

# 19. RETO 4 — Inmutabilidad

Ejecutar:

```bash
docker run -d \
  --name product-readonly \
  --read-only \
  --tmpfs /tmp \
  techstore/product-service:v1.0.1
```

Comprobar:

```bash
docker exec product-readonly sh -c "touch /test.txt"
```

### ¿Qué esperamos?

El proceso no debería poder escribir libremente en el filesystem del contenedor.

### Pregunta

¿Por qué necesitamos `tmpfs /tmp`?

---

# 20. RETO 5 — Capacidades Linux

Ejecutar:

```bash
docker run --rm \
  --cap-drop=ALL \
  techstore/product-service:v1.0.1
```

> ¿Qué significa `--cap-drop=ALL`?

---

# 21. RETO 6 — Versionado


```text
v1.2.3
```

Además de asociar la imagen con el Git commit.

Crear:

```bash
docker tag techstore/product-service:v1.0.1 techstore/product-service:v1.1.0
```

Y una etiqueta basada en commit:

```bash
git rev-parse --short HEAD
```

Ejemplo:

```text
a1b2c3d
```

Crear:

```bash
docker tag techstore/product-service:v1.1.0 techstore/product-service:a1b2c3d
```

¿Qué problema evita usar solamente `latest`?

---

# 22. RETO 7 — Crear Docker Hub

Crear una cuenta en Docker.

Proceso:

1. Entrar a Docker Hub.
2. Seleccionar Sign up.
3. Crear Docker ID.
4. Verificar el correo.
5. Iniciar sesión.
6. Crear un repository.

Ejemplo:

```text
techstore-product-service
techstore-order-service
techstore-web-app
```

Para una cuenta personal, el namespace será:

```text
TU_DOCKER_ID
```

Por ejemplo:

```text
kattydev
```

Entonces:

```text
kattydev/techstore-product-service
```

Docker documenta actualmente que el Docker ID es el identificador de usuario de Docker Hub y que el correo debe verificarse antes de completar el acceso. 

---

# 23. Login

Ejecutar:

```bash
docker login
```

Introducir las credenciales solicitadas.

Para automatización/CI se recomienda utilizar un mecanismo de autenticación apropiado, como un access token, en lugar de guardar una contraseña directamente en scripts.

---

# 24. Tag para Docker Hub

Supongamos que el Docker ID es:

```text
TU_DOCKER_ID
```

Ejecutar:

```bash
docker tag techstore/product-service:v1.1.0 \
  TU_DOCKER_ID/techstore-product-service:v1.1.0
```

También:

```bash
docker tag techstore/order-service:v1.0.0 \
  TU_DOCKER_ID/techstore-order-service:v1.0.0
```

Y:

```bash
docker tag techstore/web-app:v1.0.0 \
  TU_DOCKER_ID/techstore-web-app:v1.0.0
```

---

# 25. Push

```bash
docker push TU_DOCKER_ID/techstore-product-service:v1.1.0
```

```bash
docker push TU_DOCKER_ID/techstore-order-service:v1.0.0
```

```bash
docker push TU_DOCKER_ID/techstore-web-app:v1.0.0
```

Docker Hub utiliza el namespace/repository/tag para identificar la imagen que se publica. 

Después revisar los repositories y sus tags en Docker Hub.

---

# 26. RETO 8 — Firma de imagen con Cosign


Instalar Cosign.

Crear una clave:

```bash
cosign generate-key-pair
```

Esto generará:

```text
cosign.key
cosign.pub
```

NO subir:

```text
cosign.key
```

al repositorio Git.

Agregar:

```text
cosign.key
```

al `.gitignore`.

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

---

# 27. RETO 9 — ¿Qué ocurre si cambiamos el código?


Cambiar el nombre de un producto o mensaje del backend.

Construir nuevamente:

```bash
docker build -t techstore/product-service:v1.2.0 ./product-service
```

Comparar:

```bash
docker image ls techstore/product-service
```

Pregunta:

> ¿La imagen `v1.1.0` debería cambiar?

---

# 28. Reto final — Mini desafío de arquitectura


> "La empresa quiere que `order-service` nunca pueda iniciar si PostgreSQL no está disponible y que el frontend solamente sea accesible por el puerto 3000."


1. Identificar qué componente modificar.
2. Agregar/ajustar healthcheck.
3. Usar `depends_on` con condición de salud.
4. Revisar los puertos publicados.
5. Probar el comportamiento.

---


# 29. Limpieza

Detener:

```bash
docker compose down
```

Eliminar también el volumen de PostgreSQL:

```bash
docker compose down -v
```

Eliminar imágenes del laboratorio:

```bash
docker image rm techstore/product-service:v1.0.0
docker image rm techstore/order-service:v1.0.0
docker image rm techstore/web-app:v1.0.0
```

---

# 30. Preguntas

1. ¿Cuál es la diferencia entre una imagen y un contenedor?
2. ¿Por qué usamos multistage?
3. ¿Qué problema resuelve layer cache?
4. ¿Por qué `.dockerignore` es una práctica de seguridad y rendimiento?
5. ¿Qué diferencia hay entre configuración y secreto?
6. ¿Por qué un healthcheck es mejor que verificar solamente que el proceso existe?
7. ¿Qué aporta ejecutar como non-root?
8. ¿Qué información entrega Trivy?
9. ¿Por qué evitar `latest` en producción?
10. ¿Qué problema resuelve firmar una imagen?
11. ¿Qué significa hacer un filesystem read-only?
12. ¿Por qué `localhost` no representa otro contenedor en Docker Compose?

---

# 31. Referencias oficiales

- Docker Multi-stage builds:
  https://docs.docker.com/build/building/multi-stage/

- Docker Hub:
  https://hub.docker.com/

- Crear cuenta Docker:
  https://docs.docker.com/accounts/individual/create-account/

- Crear repository:
  https://docs.docker.com/docker-hub/repos/create/

- Push de imágenes:
  https://docs.docker.com/docker-hub/repos/manage/hub-images/push/

- Trivy:
  https://aquasecurity.github.io/trivy/

- Cosign:
  https://docs.sigstore.dev/cosign/

---

