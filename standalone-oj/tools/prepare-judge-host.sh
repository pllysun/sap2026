#!/bin/sh
# Dedicated cgroup v2 subtree only. Does not change Docker, networking or existing containers.
set -eu
[ "$(id -u)" -eq 0 ] || { echo '请用 sudo 运行'; exit 1; }
[ "$(uname -m)" = x86_64 ] || { echo '五语言判题镜像需要原生 Linux amd64'; exit 1; }
[ -f /sys/fs/cgroup/cgroup.controllers ] || { echo '需要 cgroup v2；不要关闭资源隔离作为替代'; exit 1; }
CGROUP_NAME="${NODE_CGROUP_PREFIX:-${OJ_CGROUP_PREFIX:-lume-node}}"
case "$CGROUP_NAME" in ''|*[!a-zA-Z0-9_-]*) echo 'cgroup 名称无效'; exit 1;; esac
CGROUP_DIR="/sys/fs/cgroup/$CGROUP_NAME"
for controller in cpu memory pids; do
  grep -qw "$controller" /sys/fs/cgroup/cgroup.controllers || { echo "缺少 $controller controller"; exit 1; }
  printf '+%s\n' "$controller" > /sys/fs/cgroup/cgroup.subtree_control
done
mkdir -p "$CGROUP_DIR"
if grep -q "^populated 1$" "$CGROUP_DIR/cgroup.events"; then echo '专用 cgroup 已有进程，拒绝调整'; exit 1; fi
printf '200000 100000\n' > "$CGROUP_DIR/cpu.max"
printf '1610612736\n' > "$CGROUP_DIR/memory.max"
printf '0\n' > "$CGROUP_DIR/memory.swap.max"
printf '512\n' > "$CGROUP_DIR/pids.max"
printf '+cpu +memory +pids\n' > "$CGROUP_DIR/cgroup.subtree_control"
echo "专用判题 cgroup 已就绪：$CGROUP_DIR（2 CPU / 1536 MiB / 512 processes）"
