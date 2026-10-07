#!/usr/bin/env bash
# sap2026 主镜像构建（后端 + 两个前端 + OCR，单镜像 pllysun/sap）。
#
# 两条铁律（脚本已强制，务必走脚本，别手敲 docker build）：
#   ① 平台必须 linux/amd64 —— 生产服务器是 x86_64；在 Apple Silicon 上用普通
#      `docker build` 默认只出 arm64，服务器拉下来报 "no matching manifest for linux/amd64"。
#   ② 每次构建必须用「新的、递增的」不可变 tag —— 镜像加速器按 tag 缓存 manifest，
#      覆盖旧 tag 会继续返回旧缓存。脚本读 docker/IMAGE_VERSION 自动 patch +1，绝不覆盖旧 tag。
#
# 用法（任意目录，脚本自定位仓库根）：
#   ./docker/build-image.sh                 # 版本 patch +1（如 1.4.0 -> 1.4.1），buildx amd64 构建并推送
#   ./docker/build-image.sh --version 1.5.0 # 指定版本（次/主版本升级时）
#   ./docker/build-image.sh --also-latest   # 额外打 latest（默认不打：加速器对 latest 易缓存旧的）
#   ./docker/build-image.sh --no-push       # 只构建到本地(--load,当前架构)不推送，验证用
#   ./docker/build-image.sh --prebuilt-context /path/to/context --no-push
#                                         # 使用已测试的 JAR/网页产物组装镜像，仍强制 amd64 和新版本
#   --native-prebuilt                      # Linux x86_64 发布主机上使用已验证的预构建产物
set -euo pipefail

REPO="${IMAGE_REPO:-pllysun/sap}"
PLATFORM="linux/amd64"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # docker/
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"                          # 仓库根 sap2026/
VERSION_FILE="$SCRIPT_DIR/IMAGE_VERSION"

NEW_VERSION=""; PUSH=1; ALSO_LATEST=0; PREBUILT_CONTEXT=""; NATIVE_PREBUILT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version) NEW_VERSION="${2:-}"; shift 2 ;;
    --also-latest) ALSO_LATEST=1; shift ;;
    --no-push) PUSH=0; shift ;;
    --prebuilt-context) PREBUILT_CONTEXT="${2:?需要产物目录}"; shift 2 ;;
    --native-prebuilt) NATIVE_PREBUILT=1; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "✗ 未知参数: $1（-h 看用法）"; exit 1 ;;
  esac
done

CUR="$(tr -d '[:space:]' < "$VERSION_FILE" 2>/dev/null || true)"; CUR="${CUR:-1.0.0}"
if [ -z "$NEW_VERSION" ]; then
  case "$CUR" in
    *.*.*) MA="${CUR%%.*}"; r="${CUR#*.}"; MI="${r%%.*}"; PA="${r#*.}" ;;
    *) echo "✗ IMAGE_VERSION 应为 X.Y.Z，当前: '$CUR'（用 --version 指定）"; exit 1 ;;
  esac
  [ "$PA" -eq "$PA" ] 2>/dev/null || { echo "✗ patch 段非数字: '$PA'"; exit 1; }
  NEW_VERSION="${MA}.${MI}.$((PA + 1))"
fi
echo "▶ 镜像版本: ${CUR} → ${NEW_VERSION}   平台: ${PLATFORM}"

BUILD_CONTEXT="$ROOT"
DOCKERFILE="$SCRIPT_DIR/Dockerfile"
if [ -n "$PREBUILT_CONTEXT" ]; then
  BUILD_CONTEXT="$(cd "$PREBUILT_CONTEXT" && pwd)"
  DOCKERFILE="$BUILD_CONTEXT/Dockerfile"
  for artifact in Dockerfile app.jar static/user/index.html static/admin/index.html; do
    [ -s "$BUILD_CONTEXT/$artifact" ] || { echo "✗ 缺少构建产物: $artifact"; exit 1; }
  done
  # 管理端由 /admin/ 提供服务；错误的 Vite base 会让入口脚本和样式变成 /assets/*，上线后 404。
  if ! grep -Eq 'src="/admin/assets/[^" ]+\.js"' "$BUILD_CONTEXT/static/admin/index.html" ||
     ! grep -Eq 'href="/admin/assets/[^" ]+\.css"' "$BUILD_CONTEXT/static/admin/index.html"; then
    echo "✗ 管理端入口未使用 /admin/assets/，请以 VITE_BASE_URL=/admin/ 重新构建"
    exit 1
  fi
  # 本地 Maven 的增量输出可能残留开发配置；预构建镜像必须先通过归档检查。
  if command -v unzip >/dev/null 2>&1; then
    ARCHIVE_ENTRIES="$(unzip -Z1 "$BUILD_CONTEXT/app.jar")"
  else
    ARCHIVE_ENTRIES="$(python3 -c 'import sys,zipfile; print("\n".join(zipfile.ZipFile(sys.argv[1]).namelist()))' "$BUILD_CONTEXT/app.jar")"
  fi
  if grep -Eq '(^|/)(application-(dev|local|prod)\.ya?ml($|\.)|[^/]*\.local\.[^/]+|\._[^/]*)$' <<< "$ARCHIVE_ENTRIES"; then
    echo "✗ JAR 含本地环境配置或元数据，拒绝构建/推送；请使用最新 Maven 排除规则重新打包"
    exit 1
  fi
  # 本机 dist 可能指向外置构建卷；Docker COPY 不会把链接目标打包进去。
  # 预构建目录必须是自包含的实体文件，提前拒绝软链接，避免发布缺页镜像。
  if [ -n "$(find "$BUILD_CONTEXT/static" -type l -print -quit)" ] || [ -L "$BUILD_CONTEXT/app.jar" ]; then
    echo "✗ 预构建产物包含软链接，请使用 cp -RL 复制实际产物后重试"
    exit 1
  fi
  if docker image inspect "${REPO}:${NEW_VERSION}" >/dev/null 2>&1; then
    echo "✗ 镜像版本已存在: ${REPO}:${NEW_VERSION}，请使用新的版本号"
    exit 1
  fi
fi

# Recovery path for a native x86_64 release host with an already verified parent.
# Retains the same artifact, architecture and immutable-tag checks above.
if [ "$NATIVE_PREBUILT" -eq 1 ]; then
  [ -n "$PREBUILT_CONTEXT" ] && [ "$(uname -s)" = Linux ] && [ "$(uname -m)" = x86_64 ] || {
    echo "✗ --native-prebuilt 仅适用于 Linux x86_64 的预构建产物"; exit 1;
  }
  NATIVE_TAGS=(-t "${REPO}:${NEW_VERSION}")
  [ "$ALSO_LATEST" -eq 1 ] && NATIVE_TAGS+=(-t "${REPO}:latest")
  DOCKER_BUILDKIT=0 docker build --platform "$PLATFORM" "${NATIVE_TAGS[@]}" -f "$DOCKERFILE" "$BUILD_CONTEXT"
  if [ "$PUSH" -eq 1 ]; then
    docker push "${REPO}:${NEW_VERSION}"
    [ "$ALSO_LATEST" -eq 0 ] || docker push "${REPO}:latest"
  fi
  echo "$NEW_VERSION" > "$VERSION_FILE"
  echo "✅ 镜像完成: ${REPO}:${NEW_VERSION} (${PLATFORM}, native prebuilt)"
  exit 0
fi

# buildx builder（容器驱动，支持跨架构 + --push）；缺则用 default
BUILDER="${BUILDX_BUILDER:-}"
if [ -z "$BUILDER" ] && docker buildx inspect ops-builder >/dev/null 2>&1; then BUILDER="ops-builder"; fi
BUILD_CMD=(docker buildx build)
[ -n "$BUILDER" ] && BUILD_CMD+=(--builder "$BUILDER")

TAGS=(-t "${REPO}:${NEW_VERSION}")
[ "$ALSO_LATEST" -eq 1 ] && TAGS+=(-t "${REPO}:latest")
OUT=(--push); [ "$PUSH" -eq 0 ] && OUT=(--load)

echo "▶ ${BUILD_CMD[*]} --platform ${PLATFORM} ${TAGS[*]} ${OUT[*]}"
( cd "$ROOT" && "${BUILD_CMD[@]}" --platform "$PLATFORM" \
    "${TAGS[@]}" -f "$DOCKERFILE" "${OUT[@]}" "$BUILD_CONTEXT" )

echo "$NEW_VERSION" > "$VERSION_FILE"     # 仅构建成功才写回版本号

echo
echo "✅ 镜像完成: ${REPO}:${NEW_VERSION}  (${PLATFORM})"
if [ "$PUSH" -eq 1 ]; then
  echo "   架构校验:"
  docker buildx imagetools inspect "${REPO}:${NEW_VERSION}" 2>/dev/null | grep -iE "Platform:" | head -2 || true
  echo "   部署：把 docker run 的镜像 tag 换成 ${REPO}:${NEW_VERSION}（端口/环境变量/卷不变）。"
fi
