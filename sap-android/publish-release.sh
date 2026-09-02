#!/usr/bin/env bash
# 把正式签名 APK 发布到在线升级平台。
# 更新说明直接从 APK 对应的 Changelog.kt 条目提取，确保平台说明与 App 内说明一致。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHANGELOG_KT="$SCRIPT_DIR/app/src/main/java/edu/csuft/sap/update/Changelog.kt"
EXTRACT_CHANGELOG="$SCRIPT_DIR/scripts/extract_changelog.py"
BASE_URL="${SAP_BASE_URL:-https://csuftsap.top}"
APK=""
FORCE_UPDATE="false"
MIN_SUPPORTED=1
DRY_RUN=0

while [ $# -gt 0 ]; do
  case "$1" in
    --apk)
      [ -n "${2:-}" ] || { echo "✗ --apk 缺少文件路径"; exit 1; }
      APK="$2"; shift 2 ;;
    --force-update) FORCE_UPDATE="true"; shift ;;
    --min-supported)
      [ -n "${2:-}" ] || { echo "✗ --min-supported 缺少 versionCode"; exit 1; }
      MIN_SUPPORTED="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help)
      echo "用法：publish-release.sh --apk <正式APK> [--force-update] [--min-supported N] [--dry-run]"
      echo "认证：设置 SAP_ADMIN_TOKEN，或同时设置 SAP_ADMIN_ACCOUNT / SAP_ADMIN_PASSWORD。"
      exit 0 ;;
    *) echo "✗ 未知参数: $1"; exit 1 ;;
  esac
done

[ -n "$APK" ] || { echo "✗ 必须提供 --apk"; exit 1; }
[ -f "$APK" ] || { echo "✗ APK 不存在: $APK"; exit 1; }
[[ "$MIN_SUPPORTED" =~ ^[0-9]+$ ]] || { echo "✗ --min-supported 必须是非负整数"; exit 1; }
[ -f "$CHANGELOG_KT" ] || { echo "✗ 找不到 $CHANGELOG_KT"; exit 1; }
[ -f "$EXTRACT_CHANGELOG" ] || { echo "✗ 找不到 $EXTRACT_CHANGELOG"; exit 1; }
command -v curl >/dev/null || { echo "✗ 找不到 curl"; exit 1; }
command -v jq >/dev/null || { echo "✗ 找不到 jq"; exit 1; }
command -v python3 >/dev/null || { echo "✗ 找不到 python3"; exit 1; }

NEWER_SOURCE="$(find "$SCRIPT_DIR/app/src/main" "$SCRIPT_DIR/app/build.gradle.kts" \
  -type f -newer "$APK" -print -quit)"
if [ -n "$NEWER_SOURCE" ]; then
  echo "✗ APK 构建后 App 源码又发生了变化，请重新执行正式构建"
  echo "  较新的文件: $NEWER_SOURCE"
  exit 1
fi

AAPT="${AAPT:-}"
if [ -z "$AAPT" ]; then
  AAPT="$(ls -d "$HOME/Library/Android/sdk/build-tools/"*/aapt 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -x "$AAPT" ] || { echo "✗ 找不到 aapt，请设置 AAPT"; exit 1; }

BADGING="$($AAPT dump badging "$APK" | head -1)"
VERSION_CODE="$(printf '%s' "$BADGING" | sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p")"
VERSION_NAME="$(printf '%s' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
[ -n "$VERSION_CODE" ] && [ -n "$VERSION_NAME" ] || { echo "✗ 无法读取 APK 版本号"; exit 1; }

NEEDLE="versionCode = ${VERSION_CODE}, versionName = \"${VERSION_NAME}\""
if ! grep -Fq "$NEEDLE" "$CHANGELOG_KT"; then
  echo "✗ App 内更新日志没有 ${VERSION_NAME} (${VERSION_CODE})，拒绝发布"
  exit 1
fi

# 从 App 的唯一日志数据源提取并还原 Kotlin 转义，平台与软件内看到的文案完全一致。
APP_CHANGELOG="$(python3 "$EXTRACT_CHANGELOG" \
  --changelog "$CHANGELOG_KT" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME")"
[ -n "$APP_CHANGELOG" ] || { echo "✗ 更新日志 changes 为空"; exit 1; }

LOCAL_SHA="$(shasum -a 256 "$APK" | awk '{print $1}')"
LOCAL_SIZE="$(stat -f%z "$APK" 2>/dev/null || stat -c%s "$APK")"

TOKEN="${SAP_ADMIN_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  [ -n "${SAP_ADMIN_ACCOUNT:-}" ] || { echo "✗ 缺少 SAP_ADMIN_ACCOUNT"; exit 1; }
  [ -n "${SAP_ADMIN_PASSWORD:-}" ] || { echo "✗ 缺少 SAP_ADMIN_PASSWORD"; exit 1; }
  LOGIN_BODY="$(jq -nc --arg studentId "$SAP_ADMIN_ACCOUNT" --arg password "$SAP_ADMIN_PASSWORD" \
    '{studentId:$studentId,password:$password}')"
  LOGIN_RESPONSE="$(curl -sS --max-time 30 "$BASE_URL/api/auth/admin/login" \
    -H 'Content-Type: application/json' --data "$LOGIN_BODY")"
  TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | jq -r '.data.token // empty')"
  if [ -z "$TOKEN" ]; then
    printf '%s' "$LOGIN_RESPONSE" | jq '{code,message}'
    echo "✗ 管理端登录失败"
    exit 1
  fi
fi

CURRENT_RESPONSE="$(curl -sS --max-time 30 "$BASE_URL/api/app/version" -H "sap-token: $TOKEN")"
if [ "$(printf '%s' "$CURRENT_RESPONSE" | jq -r '.code // 0')" != "200" ]; then
  printf '%s' "$CURRENT_RESPONSE" | jq '{code,message}'
  echo "✗ 无法读取线上版本"
  exit 1
fi
ONLINE_CODE="$(printf '%s' "$CURRENT_RESPONSE" | jq -r '.data.versionCode // 0')"
ONLINE_NAME="$(printf '%s' "$CURRENT_RESPONSE" | jq -r '.data.versionName // ""')"
if [ "$VERSION_CODE" -le "$ONLINE_CODE" ]; then
  echo "✗ APK versionCode=${VERSION_CODE} 不大于线上 ${ONLINE_CODE}(${ONLINE_NAME})，拒绝发布"
  exit 1
fi

echo "▶ 发布预检通过"
echo "  线上版本: ${ONLINE_NAME} (${ONLINE_CODE})"
echo "  待发版本: ${VERSION_NAME} (${VERSION_CODE})"
echo "  APK:      ${APK}"
echo "  SHA-256:  ${LOCAL_SHA}"
echo "  更新说明:"
printf '%s\n' "$APP_CHANGELOG" | sed 's/^/    - /'

if [ "$DRY_RUN" -eq 1 ]; then
  echo "✅ dry-run 完成，未上传、未修改线上版本"
  exit 0
fi

PUBLISH_RESPONSE="$(curl -sS --max-time 600 -X POST "$BASE_URL/api/app/version/publish" \
  -H "sap-token: $TOKEN" \
  -F "file=@${APK};type=application/vnd.android.package-archive" \
  -F "versionCode=${VERSION_CODE}" \
  -F "versionName=${VERSION_NAME}" \
  --form-string "changelog=${APP_CHANGELOG}" \
  -F "forceUpdate=${FORCE_UPDATE}" \
  -F "minSupportedVersionCode=${MIN_SUPPORTED}")"

if [ "$(printf '%s' "$PUBLISH_RESPONSE" | jq -r '.code // 0')" != "200" ]; then
  printf '%s' "$PUBLISH_RESPONSE" | jq '{code,message}'
  echo "✗ 发布失败"
  exit 1
fi

REMOTE_CODE="$(printf '%s' "$PUBLISH_RESPONSE" | jq -r '.data.versionCode // empty')"
REMOTE_NAME="$(printf '%s' "$PUBLISH_RESPONSE" | jq -r '.data.versionName // empty')"
REMOTE_SHA="$(printf '%s' "$PUBLISH_RESPONSE" | jq -r '.data.sha256 // empty')"
REMOTE_SIZE="$(printf '%s' "$PUBLISH_RESPONSE" | jq -r '.data.size // empty')"
REMOTE_URL="$(printf '%s' "$PUBLISH_RESPONSE" | jq -r '.data.downloadUrl // empty')"

[ "$REMOTE_CODE" = "$VERSION_CODE" ] || { echo "✗ 发布后 versionCode 校验失败"; exit 1; }
[ "$REMOTE_NAME" = "$VERSION_NAME" ] || { echo "✗ 发布后 versionName 校验失败"; exit 1; }
[ "$REMOTE_SHA" = "$LOCAL_SHA" ] || { echo "✗ 发布后 SHA-256 校验失败"; exit 1; }
[ "$REMOTE_SIZE" = "$LOCAL_SIZE" ] || { echo "✗ 发布后文件大小校验失败"; exit 1; }

echo "✅ 在线更新发布成功"
echo "  版本: ${REMOTE_NAME} (${REMOTE_CODE})"
echo "  地址: ${REMOTE_URL}"
echo "  SHA-256: ${REMOTE_SHA}"
