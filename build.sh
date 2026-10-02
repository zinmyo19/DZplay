#!/bin/bash
# Manual APK build for ComposeTest (no Gradle) — Kotlin + Jetpack Compose
# Proves Jetpack Compose works with the manual kotlinc/d8/aapt2 pipeline.
set -euo pipefail
export JAVA_HOME=$HOME/jdk17
export PATH=$JAVA_HOME/bin:$PATH

SDK=~/android-sdk
BT=$SDK/build-tools/34.0.0
AAPT2=$BT/aapt2
D8=$BT/d8
ZIPALIGN=$BT/zipalign
APKSIGNER=$BT/apksigner
ANDROID_JAR=$SDK/platforms/android-34/android.jar
KOTLINC=$HOME/kotlinc/bin/kotlinc
KEYSTORE=~/workspace/shizuku-build/manual/debug.keystore

PROJ=~/workspace/dzplay-compose
M=$PROJ/app/src/main
DEPS=$PROJ/deps
OUT=/tmp/dzplay-compose-out
chmod -R u+w $OUT 2>/dev/null || true
rm -rf $OUT && mkdir -p $OUT/compiled $OUT/gen $OUT/classes $OUT/dex \
  $OUT/aar $OUT/lib-classes $OUT/gen-lib

VER_CODE=11
VER_NAME="1.1.0"
APK_NAME="DZplay-1.1.0.apk"

# Compose compiler plugin (Kotlin 1.9.24 <-> compose compiler 1.5.14)
PLUGIN=$(ls $DEPS/compiler-1.5.14.jar)
[ -f "$PLUGIN" ] || { echo "FATAL: compose compiler plugin missing"; exit 1; }

# KMP-successor duplicates: the old collection / collection-ktx jars contain the
# same androidx.collection.* classes as collection-jvm (their KMP successor).
# d8 aborts on the duplicates, so drop the obsolete ones (newest wins).
DROP_JARS="collection-1.0.0.jar collection-ktx-1.2.0.jar kotlin-stdlib-1.8.0.jar listenablefuture-1.0.jar"
echo "=== [1/8] Extracting AARs/JARs ==="
CP="$ANDROID_JAR"
AAR_RES_ZIPS=""
> $OUT/aar_pkgs.txt
# Media3 AARs (local, from zyne-play) + Compose deps
for aar in $PROJ/libs/*.aar $DEPS/*.aar; do
  [ -e "$aar" ] || continue
  n=$(basename "$aar" .aar)
  d=$OUT/aar/$n
  mkdir -p "$d"
  unzip -q -o "$aar" -d "$d"
  if [ -f "$d/classes.jar" ]; then
    CP="$CP:$d/classes.jar"
    mkdir -p "$OUT/lib-classes/$n"
    (cd "$OUT/lib-classes/$n" && unzip -q -n "$d/classes.jar")
  fi
  for j in "$d"/libs/*.jar; do
    [ -f "$j" ] || continue
    CP="$CP:$j"
  done
  if [ -d "$d/res" ]; then
    # overlay-merge library resources into the app package (avoids the
    # proto-manifest that aapt2 emits when --static-lib is used on the app link)
    $AAPT2 compile --dir "$d/res" -o "$OUT/compiled/aar-$n.zip"
    AAR_RES_ZIPS="$AAR_RES_ZIPS -R $OUT/compiled/aar-$n.zip"
    pkg=$(grep -o 'package="[^"]*"' "$d/AndroidManifest.xml" | head -1 | cut -d'"' -f2)
    echo "$n|$pkg" >> $OUT/aar_pkgs.txt
    echo "aar res: $n ($pkg)"
  fi
done
# plain jars (kotlin-stdlib et al); keep one stdlib version only.
# NOTE: the compose plugin jar and the embeddable compiler must NOT be
# packaged into the APK.
for j in $PROJ/libs/*.jar $DEPS/*.jar; do
  case "$j" in *compiler-1.5.14.jar|*kotlin-compiler-embeddable*) continue;; esac
  bn0=$(basename "$j")
  case " $DROP_JARS " in *" $bn0 "*) echo "drop (dup): $bn0"; continue;; esac
  CP="$CP:$j"
  bn=$(basename "$j" .jar)
  mkdir -p "$OUT/lib-classes/plain-$bn"
  (cd "$OUT/lib-classes/plain-$bn" && unzip -q -n "$j")
done
echo "classpath entries: $(echo "$CP" | tr ':' '\n' | wc -l)"
# d8 cannot handle module-info.class / multi-release META-INF entries
find $OUT/lib-classes -path "*/META-INF/*" -delete 2>/dev/null || true

echo "=== [2/8] Compiling resources ==="
$AAPT2 compile --dir $M/res -o $OUT/compiled/res.zip

echo "=== [3/8] Linking ==="
# shellcheck disable=SC2086
$AAPT2 link -o $OUT/base.apk \
  -I "$ANDROID_JAR" \
  --manifest $M/AndroidManifest.xml \
  -R $OUT/compiled/res.zip \
  $AAR_RES_ZIPS \
  --auto-add-overlay \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code $VER_CODE \
  --version-name "$VER_NAME" \
  --java $OUT/gen \
  --output-text-symbols $OUT/R.txt
echo "R.java files: $(find $OUT/gen -name 'R.java' | wc -l)"

echo "=== [3b/8] Generating library R.java ==="
ZYNE_OUT=$OUT ZYNE_PROJ=$PROJ python3 $PROJ/gen_lib_r.py
echo "lib R.java files: $(find $OUT/gen-lib-r -name 'R.java' | wc -l)"

echo "=== [4/8] Compiling Kotlin + Java (Compose plugin) ==="
# The Compose plugin is built against kotlin-compiler-embeddable (relocated
# intellij: org.jetbrains.kotlin.com.intellij.*), so it cannot load under the
# regular kotlinc distribution. Run K2JVMCompiler from the embeddable jar.
# Kotlin compiles the .java sources directly (better than pre-compiled classes
# for inner-class resolution).
$JAVA_HOME/bin/java -cp "$DEPS/kotlin-compiler-embeddable-1.9.24.jar:$DEPS/kotlin-stdlib-1.8.0.jar:$HOME/kotlinc/lib/trove4j.jar:$HOME/kotlinc/lib/annotations-13.0.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect \
  -Xplugin="$PLUGIN" \
  -cp "$CP" \
  -d $OUT/classes \
  $(find $M/java -name '*.kt' -o -name '*.java')
echo "compiled classes: $(find $OUT/classes -name '*.class' | wc -l)"

echo "=== [5/8] Compiling R.java ==="
$JAVA_HOME/bin/javac -encoding UTF-8 -nowarn \
  -cp "$CP" \
  -d $OUT/classes \
  $(find $OUT/gen $OUT/gen-lib-r -name 'R.java')
[ -f $OUT/classes/com/zynelabs/dzplay/R.class ] || { echo "FATAL: R.class missing"; exit 1; }

echo "=== [5b/8] Compiling app Java sources (kotlinc SKIPS .java files) ==="
$JAVA_HOME/bin/javac -encoding UTF-8 -nowarn \
  -cp "$CP:$OUT/classes" \
  -d $OUT/classes \
  $(find $M/java -name '*.java')
echo "total classes: $(find $OUT/classes -name '*.class' | wc -l)"
[ -f $OUT/classes/com/zynelabs/dzplay/data/Store.class ] || { echo "FATAL: Store.class missing"; exit 1; }

echo "=== [6/8] Dexing ==="
$D8 --min-api 24 --lib "$ANDROID_JAR" --output $OUT/dex \
  $(find $OUT/classes $OUT/lib-classes -name '*.class')
ls $OUT/dex/

echo "=== [7/8] Packaging, aligning, signing ==="
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -q -j $OUT/unsigned.apk classes*.dex)
$ZIPALIGN -p 4 $OUT/unsigned.apk $OUT/aligned.apk
$APKSIGNER sign --ks $KEYSTORE --ks-pass pass:android --out $OUT/$APK_NAME $OUT/aligned.apk
$APKSIGNER verify --print-certs $OUT/$APK_NAME | head -3
$ZIPALIGN -c -p 4 $OUT/$APK_NAME && echo "zipalign OK"

echo "=== [8/8] Verifying ==="
cp $OUT/$APK_NAME ~/workspace/user/files/$APK_NAME
ls -la ~/workspace/user/files/$APK_NAME
unzip -l ~/workspace/user/files/$APK_NAME | grep "classes.*\.dex"
echo "BUILD DONE"
