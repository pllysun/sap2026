#!/bin/sh
set -eu
if [ "${JUDGER_NODE_ONLY:-false}" != true ]; then
    wget -qO /dev/null http://127.0.0.1:8081/api/ping
fi
if [ "${JUDGER_ENABLED:-false}" = true ] || [ "${JUDGER_NODE_ONLY:-false}" = true ]; then
    if [ -n "${NODE_TLS_CERT:-}" ]; then
        # Only the container loopback health probe skips hostname verification.
        wget --no-check-certificate -qO /dev/null --header="Authorization: Bearer $(cat /run/judger/token)" https://127.0.0.1:5051/status
    else
        wget -qO /dev/null --header="Authorization: Bearer $(cat /run/judger/token)" http://127.0.0.1:5051/status
    fi
fi
