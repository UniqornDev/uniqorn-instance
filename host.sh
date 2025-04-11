#!/bin/bash

apt-get update
apt-get install ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update
apt install -y iptables-persistent docker-ce docker-ce-cli containerd.io
mkdir -p /etc/docker/tls
chmod 777 /etc/docker/tls
echo ">>> Copy TLS certs: ca.pem, server-cert.pem, server-key.pem into /etc/docker/tls"
read -p "Press [Enter] when ready..."
chown root:root /etc/docker/tls/*
chmod 700 /etc/docker/tls
chmod 400 /etc/docker/tls/*

# ---------- uncomment with the real controller IP address
#iptables -A INPUT -p tcp --dport 34986 -s 1.2.3.4 -j ACCEPT
#iptables -A INPUT -p tcp --dport 34986 -j DROP
#netfilter-persistent save

tee /etc/docker/daemon.json > /dev/null <<EOF
{
  "userns-remap": "default",
  "live-restore": true,
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:34986"],
  "tls": true,
  "tlsverify": true,
  "tlscacert": "/etc/docker/tls/ca.pem",
  "tlscert": "/etc/docker/tls/server-cert.pem",
  "tlskey": "/etc/docker/tls/server-key.pem"
}
EOF
mkdir -p /etc/systemd/system/docker.service.d
tee /etc/systemd/system/docker.service.d/override.conf > /dev/null <<EOF
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd
EOF
systemctl daemon-reexec
systemctl restart docker
docker pull debian:latest