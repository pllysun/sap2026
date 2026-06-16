#!/usr/bin/env bash
#
# 软协课表 App —— 正式包一键构建脚本
# ---------------------------------------------------------------------------
# 每次运行都会：
#   1) 自动把 versionCode +1（强制递增，杜绝"忘了升版本"导致用户端识别不到更新）
#   2) R8 混淆 + 资源压缩 + 正式 keystore 签名 打 release 包
#   3) 产物按版本归档到  <repo>/release/sap-<versionName>-<versionCode>.apk
#      连同 mapping.txt（崩溃栈反混淆用）
#   4) 打印 大小 / SHA-256 / 签名证书，供管理平台「App 版本发布」填写
#
# 用法：
#   ./build-release.sh                 # versionCode +1，versionName 不变
#   ./build-release.sh --name 1.5      # 同时把 versionName 改成 1.5
#   ./build-release.sh --no-bump       # 仅重打当前版本（慎用：不升版本，用户端识别不到更新）
#
# 可用环境变量覆盖（跨机器/CI）：JAVA_HOME、GRADLE_BIN
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # = sap-android/
APP_DIR="$SCRIPT_DIR"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"                    # = 仓库根 sap2026/
RELEASE_DIR="$REPO_ROOT/release"
BUILD_GRADLE="$APP_DIR/app/build.gradle.kts"

NEW_NAME=""
BUMP=1
while [ $# -gt 0 ]; do
  case "$1" in
    --name) NEW_NAME="${2:-}"; shift 2 ;;
    --no-bump) BUMP=0; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "✗ 未知参数: $1（-h 看用法）"; exit 1 ;;
  esac
done

# ---- 定位 JDK21 ----
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
  if [ -x /usr/libexec/java_home ]; then JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"; fi
fi
JAVA_HOME="${JAVA_HOME:-/Users/pllysun/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home}"
[ -d "$JAVA_HOME" ] || { echo "✗ 找不到 JDK21，请设 JAVA_HOME"; exit 1; }
export JAVA_HOME

# ---- 定位 gradle ----
GRADLE_BIN="${GRADLE_BIN:-}"
if [ -z "$GRADLE_BIN" ]; then
  if   [ -x "$APP_DIR/gradlew" ]; then GRADLE_BIN="$APP_DIR/gradlew"
  elif [ -x "$HOME/gradle-dist/gradle-8.9/bin/gradle" ]; then GRADLE_BIN="$HOME/gradle-dist/gradle-8.9/bin/gradle"
  elif command -v gradle >/dev/null 2>&1; then GRADLE_BIN="$(command -v gradle)"
  else echo "✗ 找不到 gradle，请设 GRADLE_BIN"; exit 1; fi
fi

# ---- 前置检查 ----
[ -f "$BUILD_GRADLE" ] || { echo "✗ 不存在 $BUILD_GRADLE"; exit 1; }
if [ ! -f "$APP_DIR/keystore.properties" ]; then
  echo "✗ 缺 keystore.properties → release 会回退 debug 签名、不可分发也不能覆盖升级。已中止。"
  exit 1
fi

# ---- 读当前版本 ----
CUR_VC="$(grep -E 'versionCode = [0-9]+' "$BUILD_GRADLE" | head -1 | sed -E 's/[^0-9]//g')"
CUR_VN="$(grep -E 'versionName = "' "$BUILD_GRADLE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[ -n "$CUR_VC" ] || { echo "✗ 读不到 versionCode"; exit 1; }

if [ "$BUMP" -eq 1 ]; then NEW_VC=$((CUR_VC + 1)); else NEW_VC="$CUR_VC"; fi
NEW_VN="${NEW_NAME:-$CUR_VN}"

# ---- 原子写回 build.gradle.kts（先写临时文件并校验，再替换，失败不破坏原文件）----
TMP="$(mktemp)"
sed -E \
  -e "s/(versionCode = )[0-9]+/\\1${NEW_VC}/" \
  -e "s/(versionName = )\"[^\"]*\"/\\1\"${NEW_VN}\"/" \
  "$BUILD_GRADLE" > "$TMP"
grep -qE "versionCode = ${NEW_VC}\b" "$TMP" || { echo "✗ versionCode 写回失败，已回滚"; rm -f "$TMP"; exit 1; }
grep -qE "versionName = \"${NEW_VN}\"" "$TMP" || { echo "✗ versionName 写回失败，已回滚"; rm -f "$TMP"; exit 1; }
mv "$TMP" "$BUILD_GRADLE"
echo "▶ 版本：${CUR_VC}(${CUR_VN})  →  ${NEW_VC}(${NEW_VN})"

# ---- 构建 ----
echo "▶ 构建 release（R8 混淆 + 资源压缩 + 正式签名）…"
( cd "$APP_DIR" && "$GRADLE_BIN" clean :app:assembleRelease -q )

APK="$APP_DIR/app/build/outputs/apk/release/app-release.apk"
MAP="$APP_DIR/app/build/outputs/mapping/release/mapping.txt"
[ -f "$APK" ] || { echo "✗ 未生成 APK"; exit 1; }

# ---- 归档 ----
mkdir -p "$RELEASE_DIR"
OUT_APK="$RELEASE_DIR/sap-${NEW_VN}-${NEW_VC}.apk"
OUT_MAP="$RELEASE_DIR/mapping-${NEW_VN}-${NEW_VC}.txt"
cp "$APK" "$OUT_APK"
[ -f "$MAP" ] && cp "$MAP" "$OUT_MAP" || true

# ---- 信息 ----
SIZE="$(stat -f%z "$OUT_APK" 2>/dev/null || stat -c%s "$OUT_APK")"
SHA="$(shasum -a 256 "$OUT_APK" | awk '{print $1}')"
APKSIGNER="$(ls -d "$HOME/Library/Android/sdk/build-tools/"*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
CERT=""
[ -n "$APKSIGNER" ] && CERT="$("$APKSIGNER" verify --print-certs "$OUT_APK" 2>/dev/null | grep -m1 'certificate DN' | sed 's/.*DN: //' || true)"

echo
echo "✅ 打包完成"
echo "  APK:      $OUT_APK"
echo "  版本:     versionCode=${NEW_VC}  versionName=${NEW_VN}"
echo "  大小:     ${SIZE} bytes ($(awk -v b="$SIZE" 'BEGIN{printf "%.1f", b/1024/1024}')MB)"
echo "  SHA-256:  ${SHA}"
[ -f "$OUT_MAP" ] && echo "  mapping:  $OUT_MAP"
[ -n "$CERT" ] && echo "  签名:     $CERT"
echo
echo "下一步：管理平台「App 版本发布」上传 $OUT_APK，填 versionCode=${NEW_VC} / versionName=${NEW_VN}（sha256/size 后端自动算）。"
