#!/usr/bin/env bash
# Full build: compile -> bake every sprite from the 3D model code -> desktop jar -> DEX -> universal (desktop+Android) jar
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; VENDOR="${VENDOR:-$HOME/.cache/vendor}"
JAVA_HOME="${JAVA_HOME:-$(find "$VENDOR" -maxdepth 1 -type d -name 'jdk-17*' | head -1)}"
JAVA="$JAVA_HOME/bin/java"; JAR="$JAVA_HOME/bin/jar"; VERSION="4.0.1"
MINDUSTRY_JAR="$VENDOR/Mindustry.jar"; R8_JAR="$VENDOR/r8.jar"; ANDROID_JAR="$VENDOR/plat/android-35/android.jar"
BUILD="$ROOT/build"; OUT="$ROOT/dist"
VENDOR="$VENDOR" JAVA_HOME="$JAVA_HOME" "$ROOT/build_classes.sh"
if [ "${SKIP_BAKE:-0}" != "1" ]; then
  (cd "$ROOT" && "$JAVA" -Xmx700m -Djava.awt.headless=true -cp "build/tools:build/classes:$MINDUSTRY_JAR" bake.BakeAll assets/sprites preview)
fi
rm -rf "$BUILD/jar" "$BUILD/dex"; mkdir -p "$BUILD/jar" "$BUILD/dex" "$OUT"
cp -r "$BUILD/classes/." "$BUILD/jar/"; cp "$ROOT/mod.hjson" "$BUILD/jar/"; cp -r "$ROOT/assets/." "$BUILD/jar/"
(cd "$BUILD/jar" && "$JAR" --create --file "$BUILD/RBMKWhiteReactorDesktop.jar" .)
"$JAVA" -Xmx700m -cp "$R8_JAR" com.android.tools.r8.D8 --release --min-api 21 --lib "$ANDROID_JAR" --classpath "$MINDUSTRY_JAR" --output "$BUILD/dex" "$BUILD/RBMKWhiteReactorDesktop.jar"
cp "$BUILD/dex/classes.dex" "$BUILD/jar/classes.dex"
rm -f "$OUT"/RBMK-White-Reactor-v*.jar
(cd "$BUILD/jar" && "$JAR" --create --file "$OUT/RBMK-White-Reactor-v$VERSION.jar" .)
echo "built: $OUT/RBMK-White-Reactor-v$VERSION.jar"
