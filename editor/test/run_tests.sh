#!/usr/bin/env bash
# Tous les tests automatisés (hôte uniquement, sans téléphone ni APK).
#   editor/test/run_tests.sh [chemin/vers/reference]
# Prérequis : JDK, g++, python3 + unicorn keystone-engine capstone (pip), et le dossier reference/ du pack.
set -euo pipefail
HERE=$(cd "$(dirname "$0")/.." && pwd)
REF=${1:-$HERE/../reference}
B=$HERE/build/test
rm -rf "$B" && mkdir -p "$B/jvm" "$B/gen"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstdout.encoding=UTF-8"

echo "== patch statique (ELF synthétique exécuté sous unicorn)"
python3 "$HERE/test/patch_test.py"

echo "== lib native (fausse API IL2CPP)"
python3 "$HERE/tools/patch_il2cpp.py" header "$B/gen/hooks_gen.h" >/dev/null
g++ -std=c++17 -Wall -I "$HERE/test/stub" -I "$HERE/test/jni" -I "$B/gen" "$HERE/test/hook_test.cpp" -o "$B/hook_test" -ldl -lpthread
"$B/hook_test" 2>/dev/null

echo "== modèle de niveaux (817 niveaux officiels)"
javac -encoding UTF-8 -d "$B/jvm" $(find "$HERE/java" -name '*.java' -exec grep -L '^import android' {} +) "$HERE/test/LevelTest.java" 2>&1 | grep -v JAVA_TOOL || true
java -cp "$B/jvm" LevelTest "$REF/levels" 2>&1 | grep -v JAVA_TOOL

if [ -d "$REF/assets/sprites" ] && [ -f "$HERE/tools/make_atlas.py" ]; then
  echo "== atlas de sprites"
  python3 -I "$HERE/tools/make_atlas.py" "$REF/assets/sprites" "$B/atlas" >/dev/null && echo "  OK   atlas généré depuis reference/assets"
fi
echo "TOUS LES TESTS OK"
