#!/bin/bash

# 테스트 클라이언트 실행 스크립트
#
# 사용법:
#   ./test-client.sh [host] [port]
#
# 예시:
#   ./test-client.sh                  # localhost:9000
#   ./test-client.sh localhost 9001   # localhost:9001

HOST=${1:-localhost}
PORT=${2:-9000}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 클래스 파일이 없으면 컴파일
if [ ! -f "out/kr/honestfund/nice/mock/TestClient.class" ]; then
    echo "Compiling..."
    mkdir -p out
    javac -d out src/main/java/kr/honestfund/nice/mock/TestClient.java
fi

echo "Testing connection to $HOST:$PORT"
java -cp "out" kr.honestfund.nice.mock.TestClient $HOST $PORT