#!/usr/bin/env bash
set -euo pipefail

# Use environment variables if set, otherwise fallback to local paths
if [ -z "${JAVA_HOME:-}" ]; then
    export JAVA_HOME=/home/wakaztahir/java/jdk-26.0.1
    export PATH=$JAVA_HOME/bin:$PATH
fi

if [ -z "${GRADLE_HOME:-}" ]; then
    # In CI, gradle should be in PATH. Only set fallback if not in CI.
    if [ -z "${GITHUB_ACTIONS:-}" ]; then
        export GRADLE_HOME=/home/wakaztahir/gradle/gradle-9.5.1
        export PATH=$GRADLE_HOME/bin:$PATH
    fi
fi

GRADLE_CMD="gradle"
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
fi

case "${1:-}" in
    build)
        $GRADLE_CMD :app:classes
        ;;
    run)
        $GRADLE_CMD :app:run
        ;;
    clean)
        $GRADLE_CMD clean
        ;;
    rebuild)
        $GRADLE_CMD clean :app:classes
        ;;
    jar)
        $GRADLE_CMD :app:jar
        ;;
    package)
        $GRADLE_CMD :app:package
        ;;
    *)
        echo "Usage: $0 {build|run|clean|rebuild|jar|package}"
        exit 1
        ;;
esac
