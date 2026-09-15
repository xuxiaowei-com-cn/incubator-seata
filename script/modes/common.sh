#!/bin/sh
#
# Shared helpers for the Seata mode scripts used by the native metadata workflows.
#

# Every mode script implements the same three commands:
#
#   <mode>.sh start   start the dependency of the mode and wait until it is reachable
#   <mode>.sh seed    seed the config center (a no-op for non config modes)
#   <mode>.sh env     print the Seata settings of the mode as KEY=VALUE lines,
#                     ready to be appended to $GITHUB_ENV
#
# The workflow runs those commands on Linux runners only: Docker based dependencies
# cannot run on macOS runners, so there the server keeps its default
# file config / file registry / file store and collects baseline metadata.

set -eu

# mode_start_container <container> <ready-command> -- <docker run arguments...>
#
# The ready command is evaluated on the host and has to exit 0 once the
# dependency is reachable.
mode_start_container() {
    container="$1"
    ready_cmd="$2"
    shift 2
    if [ "${1:-}" = "--" ]; then
        shift
    fi

    echo "Starting container ${container}..."
    docker rm -f "${container}" >/dev/null 2>&1 || true
    docker run -d --name "${container}" "$@"

    echo "Waiting for ${container} to become ready..."
    i=1
    while [ "${i}" -le 60 ]; do
        if sh -c "${ready_cmd}" >/dev/null 2>&1; then
            echo "${container} is ready after ${i}s."
            return 0
        fi
        sleep 1
        i=$((i + 1))
    done

    echo "ERROR: ${container} was not ready within 60s."
    docker logs "${container}" --tail 50 || true
    return 1
}
