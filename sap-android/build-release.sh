#!/usr/bin/env bash
#
# 软协课表 App —— 正式包一键构建脚本
# ---------------------------------------------------------------------------
# 版本号约定（重要）：
#   • versionName（如 1.13 → 1.14）= 对外发布版本，每次发布递增——这是用户看到的「版本」。
#   • versionCode（21、22…）       = 内部构建号，仅内部使用，每次构建自动 +1（驱动「检查更新」的比对）。
#
# 每次运行都会：
#   1) 默认升「对外版本」versionName 末位 +1（如 1.13→1.14）；versionCode 始终内部 +1
#   2) 校验「更新日志」：即将发布的 (versionCode, versionName) 必须在
#      app/.../update/Changelog.kt 里有完全匹配的条目，缺失/不一致则拒绝打包（每个版本都要写更新日志）
#   3) R8 混淆 + 资源压缩 + 正式 keystore 签名 打 release 包
#   4) 产物按版本归档到  <repo>/release/sap-<versionName>-<versionCode>.apk
#      连同 mapping.txt（崩溃栈反混淆用）
#   5) 打印 大小 / SHA-256 / 签名证书，供管理平台「App 版本发布」填写
#
# ⚠️ 发版前先在 Changelog.kt 顶部加好「本次 (versionCode, versionName)」的更新日志，否则脚本会中止。
#
# 用法：
#   ./build-release.sh                 # 发对外新版本：versionName 末位 +1（1.13→1.14）、versionCode 内部 +1
#   ./build-release.sh --name 2.0      # 指定 versionName=2.0（大版本跳号）、versionCode 内部 +1
#   ./build-release.sh --build-only    # 仅内部构建：versionCode +1、versionName 不变（不对外发版）
#   ./build-release.sh --no-bump       # 仅重打当前版本（慎用：不升任何号，用户端识别不到更新）
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
BUMP_VN=1     # 默认：升对外版本 versionName 末位（1.13→1.14）
BUMP_VC=1     # versionCode 始终内部 +1（仅 --no-bump 时为 0）
while [ $# -gt 0 ]; do
  case "$1" in
    --name) NEW_NAME="${2:-}"; BUMP_VN=0; shift 2 ;;   # 显式指定 versionName（不再自动末位 +1）
    --build-only) BUMP_VN=0; shift ;;                  # 仅内部构建：versionName 不变、versionCode +1
    --no-bump) BUMP_VN=0; BUMP_VC=0; shift ;;          # 仅重打：什么都不升
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
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

if [ "$BUMP_VC" -eq 1 ]; then NEW_VC=$((CUR_VC + 1)); else NEW_VC="$CUR_VC"; fi
if [ -n "$NEW_NAME" ]; then
  NEW_VN="$NEW_NAME"                                 # 显式指定（--name）
elif [ "$BUMP_VN" -eq 1 ]; then
  NEW_VN="${CUR_VN%.*}.$(( ${CUR_VN##*.} + 1 ))"     # 对外版本末位 +1：1.13 → 1.14
else
  NEW_VN="$CUR_VN"                                   # --build-only / --no-bump：不变
fi

# ---- 打包铁律：每个版本必须写「更新日志」----
# 即将发布的 (versionCode, versionName) 必须在 Changelog.kt 里有完全匹配的条目，否则拒绝打包
# （与"强制 versionCode +1"同级的硬约束，杜绝发版忘写更新日志/版本名对不上）。在改动版本号之前先校验。
CHANGELOG_KT="$APP_DIR/app/src/main/java/edu/csuft/sap/update/Changelog.kt"
[ -f "$CHANGELOG_KT" ] || { echo "✗ 找不到更新日志数据源 $CHANGELOG_KT。已中止（未改动版本号）。"; exit 1; }
VN_RE="$(printf '%s' "$NEW_VN" | sed 's/[.]/\\./g')"   # 转义点号，精确匹配版本名
if ! grep -qE "versionCode[[:space:]]*=[[:space:]]*${NEW_VC},[[:space:]]*versionName[[:space:]]*=[[:space:]]*\"${VN_RE}\"" "$CHANGELOG_KT"; then
  echo "✗ 更新日志缺失/不匹配：Changelog.kt 没有 (versionCode=${NEW_VC}, versionName=\"${NEW_VN}\") 的条目。"
  echo "  打包铁律——每个版本都要写更新日志，且 versionCode/versionName 必须与本次发布一致。"
  echo "  请在 Changelog.kt 的 entries【最前面】新增一条后重试（versionCode 与 versionName 写同一行）："
  echo "    ChangelogEntry(versionCode = ${NEW_VC}, versionName = \"${NEW_VN}\", date = \"YYYY-MM-DD\", changes = listOf(\"...\"))"
  echo "  已中止（未改动版本号）。"
  exit 1
fi
echo "▶ 更新日志校验通过：Changelog.kt 已含 (versionCode=${NEW_VC}, versionName=\"${NEW_VN}\") 条目"

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
