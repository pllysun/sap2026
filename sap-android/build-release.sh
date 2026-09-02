#!/usr/bin/env bash
#
# 软协课表 App —— 测试/正式签名包构建脚本
# ---------------------------------------------------------------------------
# 必须显式选择模式：
#   • --test     调试/测试包：versionCode +1，versionName 默认不变；不要求、不写 App 更新日志；禁止上传平台。
#   • --release  正式发布包：versionCode +1，versionName 默认递增；必须先写 App 更新日志，之后上传升级平台。
#
# 用法：
#   ./build-release.sh --test
#   ./build-release.sh --release
#   ./build-release.sh --release --name 2.0
#   ./build-release.sh --test --prepare-only     # CI 先预留递增版本号，不构建
#   ./build-release.sh --release --prepare-only  # CI 先预留递增版本号/版本名，不构建
#   ./build-release.sh --release --no-bump  # 仅重打尚未上线的当前版本
#
# 完整规则见仓库根目录 APP_BUILD_RELEASE.md。
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # = sap-android/
APP_DIR="$SCRIPT_DIR"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"                    # = 仓库根 sap2026/
RELEASE_DIR="$REPO_ROOT/release"
BUILD_GRADLE="$APP_DIR/app/build.gradle.kts"

MODE=""
NEW_NAME=""
NO_BUMP=0
PREPARE_ONLY=0

set_mode() {
  if [ -n "$MODE" ] && [ "$MODE" != "$1" ]; then
    echo "✗ --test 与 --release 不能同时使用"
    exit 1
  fi
  MODE="$1"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --test|--build-only) set_mode test; shift ;;        # --build-only 为旧命令兼容别名
    --release) set_mode release; shift ;;
    --name)
      [ -n "${2:-}" ] || { echo "✗ --name 缺少版本名"; exit 1; }
      NEW_NAME="$2"; shift 2 ;;
    --no-bump) NO_BUMP=1; shift ;;
    --prepare-only) PREPARE_ONLY=1; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "✗ 未知参数: $1（-h 看用法）"; exit 1 ;;
  esac
done

if [ -z "$MODE" ]; then
  echo "✗ 必须显式指定 --test 或 --release"
  echo "  测试：./build-release.sh --test"
  echo "  发布：./build-release.sh --release"
  exit 1
fi
[ "$PREPARE_ONLY" -eq 0 ] || [ "$NO_BUMP" -eq 0 ] || {
  echo "✗ --prepare-only 与 --no-bump 不能同时使用"
  exit 1
}
[ "$MODE" = "release" ] || [ -z "$NEW_NAME" ] || {
  echo "✗ --name 只适用于正式发布"
  exit 1
}

if [ "$NO_BUMP" -eq 1 ]; then
  BUMP_VC=0
  BUMP_VN=0
else
  BUMP_VC=1
  if [ "$MODE" = "release" ] && [ -z "$NEW_NAME" ]; then BUMP_VN=1; else BUMP_VN=0; fi
fi

# ---- 前置检查 ----
[ -f "$BUILD_GRADLE" ] || { echo "✗ 不存在 $BUILD_GRADLE"; exit 1; }

# ---- 读当前版本 ----
CUR_VC="$(grep -E 'versionCode = [0-9]+' "$BUILD_GRADLE" | head -1 | sed -E 's/[^0-9]//g')"
CUR_VN="$(grep -E 'versionName = "' "$BUILD_GRADLE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[ -n "$CUR_VC" ] || { echo "✗ 读不到 versionCode"; exit 1; }

if [ "$BUMP_VC" -eq 1 ]; then NEW_VC=$((CUR_VC + 1)); else NEW_VC="$CUR_VC"; fi
if [ -n "$NEW_NAME" ]; then
  NEW_VN="$NEW_NAME"                                 # 显式指定（--name）
elif [ "$BUMP_VN" -eq 1 ]; then
  NEW_VN="${CUR_VN%.*}.$(( ${CUR_VN##*.} + 1 ))"     # 对外版本末位 +1：1.13 → 1.14
else
  NEW_VN="$CUR_VN"                                   # --build-only / --no-bump：不变
fi

# ---- 仅正式构建校验 App 内更新日志；prepare-only 先预留版本，日志由 CI 随后注入 ----
CHANGELOG_KT="$APP_DIR/app/src/main/java/edu/csuft/sap/update/Changelog.kt"
if [ "$MODE" = "release" ] && [ "$PREPARE_ONLY" -eq 0 ]; then
  [ -f "$CHANGELOG_KT" ] || { echo "✗ 找不到更新日志数据源 $CHANGELOG_KT。已中止。"; exit 1; }
  VN_RE="$(printf '%s' "$NEW_VN" | sed 's/[.]/\\./g')"
  if ! grep -qE "versionCode[[:space:]]*=[[:space:]]*${NEW_VC},[[:space:]]*versionName[[:space:]]*=[[:space:]]*\"${VN_RE}\"" "$CHANGELOG_KT"; then
    echo "✗ 正式发布缺少 App 更新日志：(versionCode=${NEW_VC}, versionName=\"${NEW_VN}\")"
    echo "  请把自上一个线上版本以来的全部变更写入 Changelog.kt，再重新构建。"
    exit 1
  fi
  echo "▶ 正式发布日志校验通过：${NEW_VN} (${NEW_VC})"
else
  if [ "$MODE" = "release" ]; then
    echo "▶ 正式版本预留：暂不校验更新日志（CI 将在正式构建前注入并再次强制校验）"
  else
    echo "▶ 测试构建：不校验、不新增 App 更新日志"
  fi
fi

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

if [ "$PREPARE_ONLY" -eq 1 ]; then
  echo
  echo "✅ 版本号预留完成（尚未构建 APK）"
  echo "  versionCode=${NEW_VC}"
  echo "  versionName=${NEW_VN}"
  exit 0
fi

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

if [ ! -f "$APP_DIR/keystore.properties" ]; then
  echo "✗ 缺 keystore.properties → release 会回退 debug 签名、不可分发也不能覆盖升级。已中止。"
  exit 1
fi

# ---- 构建；正式发布额外强制单元测试与 lint ----
if [ "$MODE" = "release" ]; then
  echo "▶ 正式发布检查（单元测试 + lint）并构建签名 release…"
  ( cd "$APP_DIR" && "$GRADLE_BIN" clean :app:testDebugUnitTest :app:lintDebug :app:assembleRelease -q )
else
  echo "▶ 构建测试签名 release（R8 混淆 + 资源压缩）…"
  ( cd "$APP_DIR" && "$GRADLE_BIN" clean :app:assembleRelease -q )
fi

APK="$APP_DIR/app/build/outputs/apk/release/app-release.apk"
MAP="$APP_DIR/app/build/outputs/mapping/release/mapping.txt"
[ -f "$APK" ] || { echo "✗ 未生成 APK"; exit 1; }

# ---- 按模式归档，避免把测试包误当成可发布包 ----
if [ "$MODE" = "release" ]; then
  OUT_DIR="$RELEASE_DIR"
  OUT_APK="$OUT_DIR/sap-${NEW_VN}-${NEW_VC}.apk"
  OUT_MAP="$OUT_DIR/mapping-${NEW_VN}-${NEW_VC}.txt"
  TYPE_LABEL="正式发布"
else
  OUT_DIR="$RELEASE_DIR/test"
  OUT_APK="$OUT_DIR/sap-test-${NEW_VN}-${NEW_VC}.apk"
  OUT_MAP="$OUT_DIR/mapping-test-${NEW_VN}-${NEW_VC}.txt"
  TYPE_LABEL="测试"
fi
mkdir -p "$OUT_DIR"
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
echo "  类型:     ${TYPE_LABEL}"
echo "  APK:      $OUT_APK"
echo "  版本:     versionCode=${NEW_VC}  versionName=${NEW_VN}"
echo "  大小:     ${SIZE} bytes ($(awk -v b="$SIZE" 'BEGIN{printf "%.1f", b/1024/1024}')MB)"
echo "  SHA-256:  ${SHA}"
[ -f "$OUT_MAP" ] && echo "  mapping:  $OUT_MAP"
[ -n "$CERT" ] && echo "  签名:     $CERT"
echo
if [ "$MODE" = "release" ]; then
  echo "下一步：用 publish-release.sh 上传；脚本会把 App 内同一份更新日志写入升级平台。"
else
  echo "测试包禁止上传在线升级平台；确认功能后再用 --release 构建正式发布包。"
fi
