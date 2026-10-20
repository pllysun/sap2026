#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(cat "$TASK_ROOT/VERSION")"
NAMESPACE="${DOCKER_NAMESPACE:-pllysun}"
(cd "$TASK_ROOT" && docker buildx build --platform linux/amd64 --build-arg "VERSION=$VERSION" --target server -t "$NAMESPACE/lume-server:$VERSION" --load .)
(cd "$TASK_ROOT" && docker buildx build --platform linux/amd64 --build-arg "VERSION=$VERSION" --target worker -t "$NAMESPACE/lume-worker:$VERSION" --load .)
(cd "$TASK_ROOT" && docker buildx build --platform linux/amd64 --build-arg "VERSION=$VERSION" --target bundle -t "$NAMESPACE/lume:$VERSION" --load .)
echo '三份镜像已构建，源代码构建无需预先发布中间镜像。先验收再推送版本标签。'
