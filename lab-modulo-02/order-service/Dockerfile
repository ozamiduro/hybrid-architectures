# syntax=docker/dockerfile:1

# =========================
# STAGE 1 - BUILD
# =========================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY zscaler-root-ca.crt /tmp/ca-certificates.crt

ENV SSL_CERT_FILE=/tmp/ca-certificates.crt

RUN apk add --no-cache ca-certificates

COPY zscaler-root-ca.crt /usr/local/share/ca-certificates/

RUN update-ca-certificates

# Layer cache: dependencias antes del código fuente
COPY pom.xml .
RUN apk add --no-cache maven \
    && mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests

# =========================
# STAGE 2 - RUNTIME
# =========================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
