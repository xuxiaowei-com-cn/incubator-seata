#!/bin/sh
#
# Registry mode 'eureka': the Seata server registers itself in Eureka.
#
# The upstream image is only published for linux/amd64, so this mode is excluded
# from the ARM64 runner in the workflow matrix.
#

set -eu
. "$(dirname "$0")/common.sh"

case "${1:-}" in
start)
    mode_start_container eureka-test 'curl -sSf -H "Accept: application/json" http://127.0.0.1:8761/eureka/apps' -- \
        -p 8761:8761 steeltoeoss/eureka-server:4.1.1
    ;;
seed)
    echo "Eureka needs no pre-seeded data for the registry mode."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=file
SEATA_REGISTRY_TYPE=eureka
SEATA_REGISTRY_EUREKA_SERVICEURL=http://127.0.0.1:8761/eureka
SEATA_REGISTRY_EUREKA_APPLICATION=default
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
