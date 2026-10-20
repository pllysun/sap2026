#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$TASK_ROOT/.env" ] || { echo '先运行 python3 tools/init.py 并填写外部 MySQL 配置'; exit 1; }
VERSION="$(cat "$TASK_ROOT/VERSION")"
exec docker run -d --name "${LUME_CONTAINER_NAME:-lume}" --restart unless-stopped \
  --cgroupns=private --cap-add SYS_ADMIN --cap-add SYS_PTRACE --cap-add SYS_RESOURCE \
  --security-opt "seccomp=$TASK_ROOT/judge/judger-seccomp.json" \
  --security-opt apparmor=unconfined --security-opt systempaths=unconfined \
  --memory=4g --cpus=4 --pids-limit=1024 --shm-size=256m --stop-timeout=45 \
  --env-file "$TASK_ROOT/.env" -p "${LUME_HOST_BIND:-0.0.0.0}:${LUME_HOST_PORT:-8080}:8080" \
  -v "${LUME_DATA_VOLUME:-lume-data}:/app/data" "${LUME_IMAGE:-pllysun/lume:$VERSION}"
