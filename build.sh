#!/bin/sh
# Builds build/zoommaps-<version>.jar with plain javac. 26.3 is unobfuscated, so no remapping is needed.
# SERVER must be a Fabric 26.3 server folder that has been started once.
set -e
cd "$(dirname "$0")"
JDK=${JDK:-/usr/lib/jvm/java-25-openjdk/bin}
SERVER=${SERVER:-$HOME/minecraft-server}
if [ ! -d lib/META-INF/libraries ]; then
  mkdir -p lib && unzip -oq "$SERVER/.fabric/server/26.3-server.jar" 'META-INF/libraries/*' -d lib
fi
if ! ls lib/extra/mixinextras-*.jar >/dev/null 2>&1; then
  mkdir -p lib/extra && unzip -oqj "$(ls $SERVER/libraries/net/fabricmc/fabric-loader/*/fabric-loader-*.jar | head -1)" 'META-INF/jars/mixinextras-fabric-*.jar' -d lib/extra
fi
CP="$SERVER/versions/26.3/server-26.3.jar:$(find lib/META-INF/libraries lib/extra -name '*.jar' | tr '\n' ':')$(find $SERVER/libraries/net/fabricmc -name '*.jar' | tr '\n' ':')"
VERSION=$(grep '"version"' src/main/resources/fabric.mod.json | cut -d'"' -f4)
rm -rf build/classes && mkdir -p build/classes
$JDK/javac --release 25 -proc:none -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp src/main/resources/* build/classes/
$JDK/jar --create --file "build/zoommaps-$VERSION.jar" -C build/classes .
echo "built build/zoommaps-$VERSION.jar"
