#!/bin/bash

# Nice Mock Server 실행 스크립트
#
# 사용법:
#   ./run.sh [port] [max_threads]
#
# 예시:
#   ./run.sh           # 기본값: 포트 9000, 스레드 50개
#   ./run.sh 9001      # 포트 9001, 스레드 50개
#   ./run.sh 9001 100  # 포트 9001, 스레드 100개

PORT=${1:-9000}
MAX_THREADS=${2:-50}

# Use Java 11+ (logback 1.4.x requires Java 11)
export JAVA_HOME=$(/usr/libexec/java_home -v 11 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null)
export PATH="$JAVA_HOME/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 클래스 파일이 없으면 컴파일
if [ ! -f "out/kr/honestfund/nice/mock/NiceMockServer.class" ]; then
    echo "Compiling..."
    mkdir -p out

    # logback 다운로드 (없으면)
    mkdir -p lib
    if [ ! -f "lib/logback-classic-1.4.14.jar" ]; then
        echo "Downloading dependencies..."
        curl -sL -o lib/logback-classic-1.4.14.jar \
            "https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.4.14/logback-classic-1.4.14.jar"
        curl -sL -o lib/logback-core-1.4.14.jar \
            "https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.4.14/logback-core-1.4.14.jar"
        curl -sL -o lib/slf4j-api-2.0.9.jar \
            "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar"
    fi

    javac -d out -cp "lib/*" src/main/java/kr/honestfund/nice/mock/*.java
    cp -r src/main/resources/* out/
fi

echo "Starting Nice Mock Server on port $PORT (max threads: $MAX_THREADS)"
java -cp "out:lib/*" kr.honestfund.nice.mock.NiceMockServer $PORT $MAX_THREADS