#!/bin/bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "No command provided."
  echo "Usage: $0 {ca|controller|host <hostname>|ping <hostname>}"
  exit 1
fi

generate_ca() {
  if [[ -f ca.pem || -f ca-key.pem ]]; then
    echo "CA already exists. Aborting to prevent overwrite." >&2
    exit 1
  fi

  echo "Generating CA..."
  openssl genrsa -out ca-key.pem 4096
  openssl req -x509 -new -nodes -key ca-key.pem -sha256 -days 3650 \
    -out ca.pem -subj "/CN=Uniqorn Cluster Root CA"
  echo "CA created: ca.pem, ca-key.pem"
}

generate_controller() {
  if [[ -f controller-cert.pem || -f controller-key.pem ]]; then
    echo "Controller cert already exists. Aborting to prevent overwrite." >&2
    exit 1
  fi

  echo "Generating controller cert..."
  openssl genrsa -out controller-key.pem 4096
  openssl req -new -key controller-key.pem -out controller.csr \
    -subj "/CN=Uniqorn Cluster Manager"
  openssl x509 -req -in controller.csr -CA ca.pem -CAkey ca-key.pem \
    -CAcreateserial -out controller-cert.pem -days 1825 -sha256
  rm controller.csr
  echo "Controller cert created: controller-cert.pem, controller-key.pem"
}

generate_host() {
  local HOSTNAME="$1"
  local REMOTE="$HOSTNAME.cluster.uniqorn.dev"

  echo "Generating certs for $HOSTNAME ($REMOTE)..."

  openssl genrsa -out "${HOSTNAME}-key.pem" 4096
  openssl req -new -key "${HOSTNAME}-key.pem" -out "${HOSTNAME}.csr" \
    -subj "/CN=${HOSTNAME}"

  echo "subjectAltName=DNS:${REMOTE}" > "${HOSTNAME}-ext.cnf"
  openssl x509 -req -in "${HOSTNAME}.csr" -CA ca.pem -CAkey ca-key.pem \
    -CAcreateserial -out "${HOSTNAME}-cert.pem" -days 365 -sha256 \
    -extfile "${HOSTNAME}-ext.cnf"
  rm "${HOSTNAME}-ext.cnf"
  rm "${HOSTNAME}.csr"

  echo "Copying certs to $REMOTE:/etc/docker/tls/ ..."
  scp "${HOSTNAME}-key.pem" "debian@$REMOTE:/etc/docker/tls/server-key.pem"
  scp "${HOSTNAME}-cert.pem" "debian@$REMOTE:/etc/docker/tls/server-cert.pem"
  scp ca.pem "debian@$REMOTE:/etc/docker/tls/ca.pem"

  echo "Done for $HOSTNAME"
}

ping_host() {
  local HOSTNAME="$1"
  local REMOTE="$HOSTNAME.cluster.uniqorn.dev"
  local PORT=34986

  echo "Pinging $REMOTE:$PORT..."

  if curl -fsS --cert controller-cert.pem --key controller-key.pem --cacert ca.pem \
      "https://$REMOTE:$PORT/_ping" | grep -q OK; then
    echo "Host $HOSTNAME is alive"
  else
    echo "Host $HOSTNAME did not respond properly"
    return 1
  fi
}

# --- CLI dispatch ---

case "${1:-}" in
  ca)
    generate_ca
    ;;
  controller)
    generate_controller
    ;;
  host)
    if [[ -z "${2:-}" ]]; then
      echo "Usage: $0 host <hostname>" >&2
      exit 1
    fi
    generate_host "$2"
    ;;
  ping)
    if [[ -z "${2:-}" ]]; then
      echo "Usage: $0 ping <hostname>" >&2
      exit 1
    fi
    ping_host "$2"
    ;;
  *)
    echo "Unknown command: $1" >&2
    echo "Usage: $0 {ca|controller|host <hostname>|ping <hostname>}" >&2
    exit 1
    ;;
esac
