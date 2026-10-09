#!/usr/bin/env bash
# Construit TotM Editor à partir de l'APK officiel 1.2.28.
#   ./build.sh tomb-of-the-mask-1-2-28.apk sortie.apk
# Prérequis : NDK r26d, build-tools 34 (zipalign, apksigner), android.jar, r8.jar, apktool.jar (2.9), python3 + UnityPy + Pillow, JDK.
set -euo pipefail

APK_IN=$(realpath "$1"); APK_OUT=$(realpath -m "$2")
HERE=$(cd "$(dirname "$0")" && pwd)
ANDROID=${ANDROID:-/opt/android}
NDK=${NDK:-$ANDROID/android-ndk-r26d/toolchains/llvm/prebuilt/linux-x86_64/bin}
BT=${BT:-$ANDROID/android-14}
ANDROID_JAR=${ANDROID_JAR:-$ANDROID/android-5.0.1/android.jar}  # API 21 = minSdk : refuse toute API plus récente
KEYSTORE=${KEYSTORE:-$HERE/debug.keystore}
PKG=${PKG:-com.happymagenta.fromcore.editor}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

export PATH="$BT:$PATH"
APKTOOL=${APKTOOL:-"java -jar $ANDROID/apktool.jar"}

echo "== 1. lib native (en-tête des hooks généré depuis tools/hooks.json)"
mkdir -p "$HERE/build/gen"
python3 -I "$HERE/tools/patch_il2cpp.py" header "$HERE/build/gen/hooks_gen.h"
"$NDK/aarch64-linux-android21-clang++" -std=c++17 -shared -fPIC -O2 -Wall -Wextra -fvisibility=hidden -static-libstdc++ \
  -Wl,-z,max-page-size=16384 -I "$HERE/build/gen" -o "$HERE/build/libtotmeditor.so" "$HERE/native/totmeditor.cpp" -llog -ldl

echo "== 2. code Java -> classes2.dex"
rm -rf "$HERE/build/classes" "$HERE/build/dex" && mkdir -p "$HERE/build/classes" "$HERE/build/dex"
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$ANDROID_JAR" -d "$HERE/build/classes" $(find "$HERE/java" -name '*.java')
java -cp "${R8_JAR:-$ANDROID/r8.jar}" com.android.tools.r8.D8 --release --min-api 21 --lib "$ANDROID_JAR" --output "$HERE/build/dex" $(find "$HERE/build/classes" -name '*.class')

echo "== 3. décompilation de l'APK (sans toucher au classes.dex d'origine)"
$APKTOOL d -q -s -f "$APK_IN" -o "$WORK/dec"
D="$WORK/dec"

echo "== 4. patch libil2cpp + lib éditeur (arm64 uniquement)"
python3 -I "$HERE/tools/patch_il2cpp.py" patch "$D/lib/arm64-v8a/libil2cpp.so" "$D/lib/arm64-v8a/libil2cpp.so"
rm -rf "$D/lib/armeabi-v7a"
cp "$HERE/build/libtotmeditor.so" "$D/lib/arm64-v8a/"
cp "$HERE/build/dex/classes.dex" "$D/classes2.dex"

echo "== 5. assets de l'éditeur extraits de l'APK : niveaux officiels, sprites (atlas), couleurs"
mkdir -p "$D/assets/totm_editor" "$WORK/ext"
python3 -I "$HERE/tools/extract_assets.py" "$APK_IN" "$WORK/ext"
cp "$WORK/ext/stages.bytes" "$WORK/ext/sections.bytes" "$D/assets/totm_editor/"
python3 -I "$HERE/tools/make_atlas.py" "$WORK/ext/sprites" "$D/assets/totm_editor" "$WORK/ext/colors.txt"

echo "== 6. manifeste"
python3 -I "$HERE/tools/edit_manifest.py" "$D" "$PKG"

echo "== 7. reconstruction, alignement, signature"
$APKTOOL b -q --use-aapt2 "$D" -o "$WORK/unsigned.apk"
zipalign -p -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias totmeditor \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TotM Editor" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --out "$APK_OUT" "$WORK/aligned.apk"
apksigner verify "$APK_OUT"
echo "OK -> $APK_OUT"
