#!/usr/bin/env bash
# =============================================================
# 加固重签：加固平台(腾讯云乐固/360加固保/梆梆)输出的 APK 已破坏原签名与
# 字节对齐，必须「先 zipalign 对齐、后 apksigner 重签」，且用与打包完全相同的
# keystore/alias，否则老用户无法覆盖升级(INSTALL_FAILED_UPDATE_INCOMPATIBLE)。
#
# 流程：assembleRelease 出正式签名包 → 送加固平台 → 用本脚本重签加固包 → 分发
# 用法：./harden-resign.sh <加固后的apk> [输出apk(默认 app-release-fortified-signed.apk)]
# =============================================================
set -euo pipefail

IN="${1:?用法: $0 <fortified.apk> [out.apk]}"
OUT="${2:-app-release-fortified-signed.apk}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPS="$SCRIPT_DIR/keystore.properties"
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"

[ -f "$PROPS" ] || { echo "缺少 $PROPS（正式签名配置）"; exit 1; }
BT="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || { echo "未找到 build-tools，请设置 ANDROID_HOME"; exit 1; }
ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"

# 读取与打包同一把签名
prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2-; }
KS="$(prop storeFile)"; ALIAS="$(prop keyAlias)"
export KSPASS="$(prop storePassword)" KEYPASS="$(prop keyPassword)"
[ -f "$KS" ] || { echo "keystore 不存在: $KS"; exit 1; }

ALIGNED="$(mktemp -u).apk"
echo "[1/3] zipalign 对齐(-p 保证 .so 页对齐)..."
"$ZIPALIGN" -p -f 4 "$IN" "$ALIGNED"

echo "[2/3] apksigner 重签(v1+v2+v3，同一把 keystore)..."
"$APKSIGNER" sign \
  --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass env:KSPASS --key-pass env:KEYPASS \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$ALIGNED"
rm -f "$ALIGNED"

echo "[3/3] 校验签名与证书指纹..."
"$APKSIGNER" verify -v --print-certs "$OUT"
echo "✅ 加固重签完成: $OUT"
echo "   请核对证书 SHA-256 与原包一致，并实机回归：覆盖安装、桌面小组件、应用内自升级。"
