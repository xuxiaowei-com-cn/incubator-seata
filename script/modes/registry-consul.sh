#!/bin/sh
#
# Registry mode 'consul': the Seata server registers itself in Consul.
#

set -eu
. "$(dirname "$0")/common.sh"

case "${1:-}" in
start)
    mode_start_container consul-test 'curl -sSf http://127.0.0.1:8500/v1/status/leader' -- \
        -p 8500:8500 hashicorp/consul:1.22.7 agent -dev -client 0.0.0.0
    ;;
seed)
    echo "Consul needs no pre-seeded data for the registry mode."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=file
SEATA_REGISTRY_TYPE=consul
SEATA_REGISTRY_CONSUL_SERVERADDR=127.0.0.1:8500
SEATA_REGISTRY_CONSUL_CLUSTER=default
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
