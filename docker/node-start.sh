#!/bin/sh
set -eu
mkdir -p /run/judger /app/data/judger-agent
umask 077
if [ ! -f "${NODE_TOKEN_FILE:-/run/secrets/judger-token}" ]; then
    echo '[JUDGER] Mount the node token file before starting.' >&2
    exit 1
fi
cp "${NODE_TOKEN_FILE:-/run/secrets/judger-token}" /run/judger/token
# No business processes or application credentials are required in node-only mode.
exec env -i PATH=/usr/bin:/bin HOME=/run/judger /usr/bin/python3 /opt/judger/agent.py \
    --bind 0.0.0.0 --token-file /run/judger/token \
    --cgroup-prefix "${NODE_CGROUP_PREFIX:-sapjudger}" \
    --max-concurrency "${NODE_CONCURRENCY:-1}" --cpu "${NODE_CPU:-1}" \
    --cpu-sets "${NODE_CPU_SETS:-}" \
    --memory-mb "${NODE_MEMORY_MB:-640}" --push-url "${NODE_PUSH_URL:-}" \
    --tls-cert "${NODE_TLS_CERT:-}" --tls-key "${NODE_TLS_KEY:-}"
