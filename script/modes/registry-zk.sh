#!/bin/sh
#
# Registry mode 'zk': the Seata server registers itself in ZooKeeper.
#

set -eu
. "$(dirname "$0")/common.sh"

ZK_READY='docker exec zk-test bash -c "exec 3<>/dev/tcp/127.0.0.1/2181; printf ruok >&3; head -c 4 <&3 | grep -q imok"'

case "${1:-}" in
start)
    mode_start_container zk-test "${ZK_READY}" -- \
        -p 2181:2181 -e ZOO_4LW_COMMANDS_WHITELIST='srvr,ruok,stat,mntr' zookeeper:3.9
    ;;
seed)
    echo "ZooKeeper needs no pre-seeded data for the registry mode."
    ;;
env)
    cat <<'EOF'
SEATA_CONFIG_TYPE=file
SEATA_REGISTRY_TYPE=zk
SEATA_REGISTRY_ZK_SERVERADDR=127.0.0.1:2181
SEATA_REGISTRY_ZK_CLUSTER=default
SEATA_STORE_MODE=file
EOF
    ;;
*)
    echo "usage: $0 <start|seed|env>" >&2
    exit 2
    ;;
esac
