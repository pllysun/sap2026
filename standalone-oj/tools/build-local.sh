#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for frontend in user admin; do
  (cd "$TASK_ROOT/$frontend" && npm ci --no-audit --no-fund && npm test && npm run build)
done
(cd "$TASK_ROOT/backend" && mvn -B clean test)
mkdir -p "$TASK_ROOT/backend/target/classes/static/admin"
cp -R "$TASK_ROOT/user/dist/." "$TASK_ROOT/backend/target/classes/static/"
cp -R "$TASK_ROOT/admin/dist/." "$TASK_ROOT/backend/target/classes/static/admin/"
(cd "$TASK_ROOT/backend" && mvn -B -DskipTests package)
echo "已构建独立网站 JAR：$TASK_ROOT/backend/target/sap-oj.jar"
