#!/bin/sh
#
# Registry mode 'redis': the Seata server registers itself in Redis.
#

set -eu
. "$(dirname "$0")/common.sh"

case "${1:-}" in
start)
    mode_start_container redis-test 'docker exec redis-test redis-cli ping' -- \
        -p 6379:6379 redis:7.2
    ;;
seed)
    echo "Redis needs no pre-seeded data for the registry mode."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=file
SEATA_REGISTRY_TYPE=redis
SEATA_REGISTRY_REDIS_SERVERADDR=127.0.0.1:6379
SEATA_REGISTRY_REDIS_DB=0
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
