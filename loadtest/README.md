# NiceProxy TCP Load Test

Locust 기반 NiceProxyServer TCP 부하 테스트 도구입니다.

## 설치

```bash
cd loadtest
pip install -r requirements.txt
```

## 사용법

### Web UI 모드 (권장)

```bash
./run-loadtest.sh web localhost 8080
```
- http://localhost:8089 에서 Web UI 접속
- Users, Spawn Rate 직접 설정 가능
- 실시간 그래프 및 통계 확인

### Headless 모드

```bash
./run-loadtest.sh headless localhost 8080
```

**부하 패턴 (NiceLoadShape):**
| Phase | Duration | Target Users | Description |
|-------|----------|--------------|-------------|
| 1 | 0-60s | 50 | Warm up |
| 2 | 60-120s | 100 | Ramp up |
| 3 | 120-150s | 200 | Spike (peak) |
| 4 | 150-180s | 200 | Hold peak |
| 5 | 180-240s | 50 | Cool down |
| 6 | 240-300s | 20 | Stabilize |

### Quick 테스트 (60초)

```bash
./run-loadtest.sh quick localhost 8080
```
- 100 users, 5 spawn/s
- 빠른 검증용

### Stress 테스트 (200 TPS 유지)

```bash
./run-loadtest.sh stress localhost 8080
```
- 200 users, 10 spawn/s
- 3분간 지속

## 결과 파일

`results/` 디렉토리에 저장:
- `*_stats.csv` - 요청 통계
- `*_failures.csv` - 실패 목록
- `*_stats_history.csv` - 시계열 데이터
- `*.html` - HTML 리포트

## 프로토콜

Nice 전문 규격:
```
[10바이트 길이 헤더][데이터]
- 인코딩: EUC-KR
- 길이 헤더: 데이터 바이트 수 (0 패딩)
```

## 테스트 시나리오

```
NiceProxy Server (8080)
        │
        ▼
   Load Test ────────► TCP Request
        │                  │
        │                  ▼
        │            Nice Mock Server (9000)
        │                  │
        └──────────────────┘
              Response
```

## 커스터마이징

### 부하 패턴 변경

`locustfile.py`의 `NiceLoadShape.stages` 수정:

```python
stages = [
    {"duration": 60, "users": 50, "spawn_rate": 2},
    {"duration": 120, "users": 100, "spawn_rate": 2},
    # ... 원하는 패턴 추가
]
```

### 요청 데이터 변경

`NiceProxyUser._generate_request_data()` 메서드 수정