#!/bin/sh
#
# Config mode 'etcd3': the Seata server loads its configuration from etcd.
#
# Seata reads the key configured by 'config.etcd3.key' (default 'seata.properties'),
# without a leading slash.
#

set -eu
. "$(dirname "$0")/common.sh"

case "${1:-}" in
start)
    mode_start_container etcd-test 'curl -sSf http://127.0.0.1:2379/health' -- \
        -p 2379:2379 -p 2380:2380 gcr.io/etcd-development/etcd:v3.5.33 /usr/local/bin/etcd \
        --name etcd0 \
        --data-dir /etcd-data \
        --advertise-client-urls http://0.0.0.0:2379 \
        --listen-client-urls http://0.0.0.0:2379 \
        --listen-peer-urls http://0.0.0.0:2380 \
        --initial-advertise-peer-urls http://0.0.0.0:2380 \
        --initial-cluster etcd0=http://0.0.0.0:2380 \
        --initial-cluster-state new \
        --initial-cluster-token seata-native
    ;;
seed)
    # The etcd image has no shell, so run etcdctl directly (with a path fallback).
    if docker exec etcd-test etcdctl version >/dev/null 2>&1; then
        docker exec -e ETCDCTL_API=3 etcd-test etcdctl put seata.properties 'transport.type=TCP'
    else
        docker exec -e ETCDCTL_API=3 etcd-test /usr/local/bin/etcdctl put seata.properties 'transport.type=TCP'
    fi
    echo "Seeded the etcd key seata.properties."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=etcd3
SEATA_CONFIG_ETCD3_SERVERADDR=http://127.0.0.1:2379
SEATA_CONFIG_ETCD3_KEY=seata.properties
SEATA_REGISTRY_TYPE=file
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
