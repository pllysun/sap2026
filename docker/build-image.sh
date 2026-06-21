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
set -euo pipefail

REPO="${IMAGE_REPO:-pllysun/sap}"
PLATFORM="linux/amd64"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # docker/
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"                          # 仓库根 sap2026/
VERSION_FILE="$SCRIPT_DIR/IMAGE_VERSION"

NEW_VERSION=""; PUSH=1; ALSO_LATEST=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version) NEW_VERSION="${2:-}"; shift 2 ;;
    --also-latest) ALSO_LATEST=1; shift ;;
    --no-push) PUSH=0; shift ;;
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

# buildx builder（容器驱动，支持跨架构 + --push）；缺则用 default
BUILDER="${BUILDX_BUILDER:-}"
if [ -z "$BUILDER" ] && docker buildx inspect ops-builder >/dev/null 2>&1; then BUILDER="ops-builder"; fi
BARG=(); [ -n "$BUILDER" ] && BARG=(--builder "$BUILDER")

TAGS=(-t "${REPO}:${NEW_VERSION}")
[ "$ALSO_LATEST" -eq 1 ] && TAGS+=(-t "${REPO}:latest")
OUT=(--push); [ "$PUSH" -eq 0 ] && OUT=(--load)

echo "▶ docker buildx build ${BARG[*]} --platform ${PLATFORM} ${TAGS[*]} ${OUT[*]}"
( cd "$ROOT" && docker buildx build "${BARG[@]}" --platform "$PLATFORM" \
    "${TAGS[@]}" -f "$SCRIPT_DIR/Dockerfile" "${OUT[@]}" . )

echo "$NEW_VERSION" > "$VERSION_FILE"     # 仅构建成功才写回版本号

echo
echo "✅ 镜像完成: ${REPO}:${NEW_VERSION}  (${PLATFORM})"
if [ "$PUSH" -eq 1 ]; then
  echo "   架构校验:"
  docker buildx imagetools inspect "${REPO}:${NEW_VERSION}" 2>/dev/null | grep -iE "Platform:" | head -2 || true
  echo "   部署：把 docker run 的镜像 tag 换成 ${REPO}:${NEW_VERSION}（端口/环境变量/卷不变）。"
fi
