#!/bin/sh
#
# Store mode 'redis': the Seata server keeps sessions and locks in Redis.
#

set -eu
. "$(dirname "$0")/common.sh"

case "${1:-}" in
start)
    mode_start_container redis-test 'docker exec redis-test redis-cli ping' -- \
        -p 6379:6379 redis:7.2
    ;;
seed)
    echo "Redis needs no pre-seeded data for the store mode."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=file
SEATA_REGISTRY_TYPE=file
SEATA_STORE_MODE=redis
SEATA_STORE_REDIS_MODE=single
SEATA_STORE_REDIS_TYPE=lua
SEATA_STORE_REDIS_SINGLE_HOST=127.0.0.1
SEATA_STORE_REDIS_SINGLE_PORT=6379
SEATA_STORE_REDIS_DATABASE=0
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
