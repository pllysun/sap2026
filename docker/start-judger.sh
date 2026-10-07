#!/bin/sh
set -eu
if [ "${JUDGER_ENABLED:-false}" != true ]; then exit 0; fi
mkdir -p /run/judger
chown root:sapapp /run/judger
chmod 750 /run/judger
umask 027
head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > /run/judger/token
chown root:sapapp /run/judger/token
chmod 640 /run/judger/token
# A small control process survives engine stops; code execution remains isolated.
mkdir -p /run/judger/agent
chmod 700 /run/judger/agent
env -i PATH=/usr/bin:/bin HOME=/run/judger \
    /usr/bin/python3 /opt/judger/agent.py --state-dir /run/judger/agent --initial-stopped \
    > /app/logs/judger-agent.log 2>&1 &
for attempt in $(seq 1 30); do
    if wget -qO /dev/null --header="Authorization: Bearer $(cat /run/judger/token)" http://127.0.0.1:5051/status; then
        echo "[JUDGER] 判题节点控制服务已就绪"
        exit 0
    fi
    sleep 1
done
echo "[JUDGER] 判题节点控制服务启动失败" >&2
exit 1
