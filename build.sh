#!/usr/bin/env bash
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ]; then
    export JAVA_HOME=/home/wakaztahir/java/jdk-26.0.1
fi

if [ -z "${GRADLE_HOME:-}" ]; then
    export GRADLE_HOME=/home/wakaztahir/gradle/gradle-9.5.1
fi

export PATH=$JAVA_HOME/bin:$GRADLE_HOME/bin:$PATH

GRADLE_CMD="gradle"
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
fi

case "${1:-}" in
    build)
        $GRADLE_CMD classes
        ;;
    run)
        $GRADLE_CMD run
        ;;
    clean)
        $GRADLE_CMD clean
        ;;
    rebuild)
        $GRADLE_CMD clean classes
        ;;
    jar)
        $GRADLE_CMD jar
        ;;
    package)
        $GRADLE_CMD app:package
        ;;
    *)
        echo "Usage: $0 {build|run|clean|rebuild|jar|package}"
        exit 1
        ;;
esac
