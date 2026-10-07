#!/bin/sh
# Isolated reviewer environment on the judge host. No production business DB,
# credentials, ports, or engine control state are modified.
set -eu
mkdir -p /app/data/redis /app/logs /app/uploads
cp /run/judger/token /app/data/candidate-agent.token
chmod 600 /app/data/candidate-agent.token
chown -R sapapp:sapapp /app/data /app/logs /app/uploads
setpriv --reuid=sapapp --regid=sapapp --init-groups --bounding-set=-all --no-new-privs redis-server \
  --bind 127.0.0.1 --port 16379 --dir /app/data/redis --appendonly yes --save '' \
  > /app/logs/redis.log 2>&1 &
nginx -t
nginx
exec setpriv --reuid=sapapp --regid=sapapp --init-groups --bounding-set=-all --no-new-privs \
  java -Xms128m -Xmx512m -XX:ActiveProcessorCount=2 -jar /app/app.jar \
  --spring.profiles.active=docker --server.address=127.0.0.1 --server.port=18081 \
  --spring.data.redis.port=16379 --file.upload.path=/app/uploads \
  --judger.enabled=true --judger.token-file=/app/data/candidate-agent.token \
  --judger.agent-endpoint=http://127.0.0.1:5051
