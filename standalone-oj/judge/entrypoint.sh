#!/bin/sh
set -eu
mkdir -p /run/judger /app/data
umask 077
cp "${NODE_TOKEN_FILE:-/run/secrets/judger-token}" /run/judger/token
exec env -i PATH=/usr/local/bin:/usr/bin:/bin HOME=/run/judger /usr/local/bin/python3 /opt/judger/agent.py \
  --bind 0.0.0.0 --token-file /run/judger/token --state-dir /app/data \
  --cgroup-prefix "${NODE_CGROUP_PREFIX:-lume-node}" \
  --max-concurrency "${NODE_CONCURRENCY:-2}" --cpu "${NODE_CPU:-2}" \
  --memory-mb "${NODE_MEMORY_MB:-1280}" --cpu-sets "${NODE_CPU_SETS:-}" \
  --push-url "${NODE_PUSH_URL:-}" --tls-cert "${NODE_TLS_CERT:-}" --tls-key "${NODE_TLS_KEY:-}"
