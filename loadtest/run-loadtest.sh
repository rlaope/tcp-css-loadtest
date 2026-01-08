#!/bin/bash

# NiceProxy TCP Load Test Runner
#
# Usage:
#   ./run-loadtest.sh [mode] [host] [port]
#
# Modes:
#   web      - Web UI mode (default, http://localhost:8089)
#   headless - Headless mode with custom load shape
#   quick    - Quick 60s test (20->100 TPS)
#
# Examples:
#   ./run-loadtest.sh                     # Web UI, localhost:8080
#   ./run-loadtest.sh web localhost 8080  # Web UI, custom host/port
#   ./run-loadtest.sh headless            # Headless with NiceLoadShape
#   ./run-loadtest.sh quick               # Quick 60s test

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

MODE=${1:-web}
HOST=${2:-localhost}
PORT=${3:-21003}

# Activate venv
if [ -d "venv" ]; then
    source venv/bin/activate
else
    echo "Creating virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
fi

echo "=============================================="
echo "NiceProxy TCP Load Test"
echo "=============================================="
echo "Mode: $MODE"
echo "Target: $HOST:$PORT"
echo "=============================================="

case $MODE in
    web)
        echo "Starting Locust Web UI at http://localhost:8089"
        echo "Configure users and spawn rate in the web interface"
        echo ""
        locust -f locustfile.py \
            --host=$HOST \
            --nice-port=$PORT
        ;;

    headless)
        echo "Starting headless load test with NiceLoadShape"
        echo "Pattern: 20 TPS -> 200 TPS (peak) -> 20 TPS"
        echo "Total duration: ~5 minutes"
        echo ""
        locust -f locustfile.py \
            --host=$HOST \
            --nice-port=$PORT \
            --headless \
            --csv=results/loadtest \
            --html=results/report.html \
            --logfile=results/locust.log
        ;;

    quick)
        echo "Starting quick 60-second load test"
        echo "Pattern: 20 users, spawn 5/s"
        echo ""
        mkdir -p results
        locust -f locustfile.py \
            --host=$HOST \
            --nice-port=$PORT \
            --headless \
            --users 100 \
            --spawn-rate 5 \
            --run-time 60s \
            --csv=results/quick \
            --html=results/quick_report.html
        ;;

    stress)
        echo "Starting stress test (200 TPS sustained)"
        echo ""
        mkdir -p results
        locust -f locustfile.py \
            --host=$HOST \
            --nice-port=$PORT \
            --headless \
            --users 200 \
            --spawn-rate 10 \
            --run-time 180s \
            --csv=results/stress \
            --html=results/stress_report.html
        ;;

    *)
        echo "Unknown mode: $MODE"
        echo "Available modes: web, headless, quick, stress"
        exit 1
        ;;
esac