#!/bin/sh
# Builds the test-only harness mod. Run ../../build.sh first.
set -e
cd "$(dirname "$0")"
JDK=${JDK:-/usr/lib/jvm/java-25-openjdk/bin}
SERVER=${SERVER:-$HOME/minecraft-server}
ROOT=../..
if ! ls $ROOT/lib/extra/fabric-lifecycle-events-v1-*.jar >/dev/null 2>&1; then
  unzip -oqj "$(ls $SERVER/mods/fabric-api-*.jar | head -1)" 'META-INF/jars/fabric-api-base-*' 'META-INF/jars/fabric-lifecycle-events-v1-*' 'META-INF/jars/fabric-events-interaction-v0-*' -d $ROOT/lib/extra
fi
CP="$SERVER/versions/26.3/server-26.3.jar:$ROOT/build/classes:$(find $ROOT/lib/META-INF/libraries $ROOT/lib/extra -name '*.jar' | tr '\n' ':')$(find $SERVER/libraries/net/fabricmc -name '*.jar' | tr '\n' ':')"
rm -rf classes && mkdir classes
$JDK/javac --release 25 -proc:none -cp "$CP" -d classes $(find src -name '*.java')
cp fabric.mod.json classes/
$JDK/jar --create --file zoommaps-test.jar -C classes .
