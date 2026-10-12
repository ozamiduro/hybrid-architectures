# syntax=docker/dockerfile:1

# =========================
# STAGE 1 - BUILD
# =========================
FROM node:22-alpine AS builder

WORKDIR /app

# Layer cache: package manifests first
COPY package*.json ./
RUN npm i

COPY . .

ARG VITE_PRODUCT_API_URL=http://localhost:8081
ARG VITE_ORDER_API_URL=http://localhost:8082

ENV VITE_PRODUCT_API_URL=${VITE_PRODUCT_API_URL}
ENV VITE_ORDER_API_URL=${VITE_ORDER_API_URL}

RUN npm run build

# =========================
# STAGE 2 - RUNTIME
# =========================
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
