#!/bin/bash

# NiceProxy 수동 테스트 스크립트
#
# 사용법:
#   ./test-proxy.sh [host] [port]
#
# 예시:
#   ./test-proxy.sh                  # localhost:21003
#   ./test-proxy.sh localhost 21003

HOST=${1:-localhost}
PORT=${2:-21003}

# 테스트 데이터 생성 (EUC-KR 기준)
DATA="TEST_REQUEST_$(date +%Y%m%d%H%M%S)_HELLO_NICE_PROXY"
DATA_LENGTH=$(echo -n "$DATA" | iconv -t EUC-KR | wc -c | tr -d ' ')
LENGTH_HEADER=$(printf "%010d" $DATA_LENGTH)

echo "=============================================="
echo "NiceProxy Manual Test"
echo "=============================================="
echo "Target: $HOST:$PORT"
echo "Data: $DATA"
echo "Length Header: $LENGTH_HEADER"
echo "=============================================="
echo ""
echo "Sending request..."

# nc로 전송 (타임아웃 30초)
RESPONSE=$(echo -n "${LENGTH_HEADER}${DATA}" | nc -w 30 $HOST $PORT 2>&1)

if [ -z "$RESPONSE" ]; then
    echo "ERROR: No response received (timeout or connection failed)"
    exit 1
fi

echo ""
echo "Response received!"
echo "Length: ${#RESPONSE} chars"
echo ""
echo "First 200 chars:"
echo "${RESPONSE:0:200}"
echo "..."