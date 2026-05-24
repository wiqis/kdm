#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME=/home/wakaztahir/java/jdk-26.0.1
GRADLE_HOME=/home/wakaztahir/gradle/gradle-9.5.1
export JAVA_HOME GRADLE_HOME
export PATH=$JAVA_HOME/bin:$GRADLE_HOME/bin:$PATH

case "${1:-}" in
    build)
        gradle classes
        ;;
    run)
        gradle run
        ;;
    clean)
        gradle clean
        ;;
    rebuild)
        gradle clean classes
        ;;
    jar)
        gradle jar
        ;;
    *)
        echo "Usage: $0 {build|run|clean|rebuild|jar}"
        exit 1
        ;;
esac
