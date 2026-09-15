#!/bin/sh
#
# Registry mode 'etcd3': the Seata server registers itself in etcd.
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
    echo "etcd needs no pre-seeded data for the registry mode."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=file
SEATA_REGISTRY_TYPE=etcd3
SEATA_REGISTRY_ETCD3_SERVERADDR=http://127.0.0.1:2379
SEATA_REGISTRY_ETCD3_CLUSTER=default
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
