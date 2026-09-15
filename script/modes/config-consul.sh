#!/bin/sh
#
# Config mode 'consul': the Seata server loads its configuration from Consul KV.
#
# Seata reads the KV entry configured by 'config.consul.key' (default 'seata.properties').
#

set -eu
. "$(dirname "$0")/common.sh"

case "${1:-}" in
start)
    mode_start_container consul-test 'curl -sSf http://127.0.0.1:8500/v1/status/leader' -- \
        -p 8500:8500 hashicorp/consul:1.22.7 agent -dev -client 0.0.0.0
    ;;
seed)
    printf 'transport.type=TCP\n' |
        curl -fsS -X PUT --data-binary @- 'http://127.0.0.1:8500/v1/kv/seata.properties'
    echo "Seeded the Consul KV entry seata.properties."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=consul
SEATA_CONFIG_CONSUL_SERVERADDR=127.0.0.1:8500
SEATA_CONFIG_CONSUL_KEY=seata.properties
SEATA_REGISTRY_TYPE=file
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
