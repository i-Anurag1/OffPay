#!/bin/sh
# Maven Wrapper launcher script.
# Downloads maven-wrapper.jar (if missing) then delegates to it, which in
# turn downloads the pinned Maven distribution from .mvn/wrapper/maven-wrapper.properties.
# If your environment has no internet access, install Maven 3.9+ yourself
# and run `mvn spring-boot:run` / `mvn test` instead.

set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_PROPS="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep '^wrapperUrl=' "$WRAPPER_PROPS" | cut -d'=' -f2-)
  echo "Downloading Maven Wrapper from $WRAPPER_URL ..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "Neither curl nor wget is available. Install Maven manually and run 'mvn' instead." >&2
    exit 1
  fi
fi

JAVA_EXE="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

exec "$JAVA_EXE" \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -cp "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
