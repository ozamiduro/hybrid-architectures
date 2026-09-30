FROM alpine:3.20

COPY zscaler-root-ca.crt /tmp/ca-certificates.crt

ENV SSL_CERT_FILE=/tmp/ca-certificates.crt

RUN apk add --no-cache ca-certificates

COPY zscaler-root-ca.crt /usr/local/share/ca-certificates/

RUN update-ca-certificates

RUN apk add --no-cache curl jq bind-tools

COPY app/check.sh /usr/local/bin/check-orion

RUN chmod +x /usr/local/bin/check-orion

CMD ["sh", "-c", "while true; do sleep 3600; done"]