# Nice Mock Server

NiceProxyServer 테스트용 모킹 서버입니다.

## 프로토콜

Nice 연동 전문 규격을 따릅니다.

```
[10바이트 길이 헤더][데이터]
```

| 항목 | 설명 |
|------|------|
| 인코딩 | EUC-KR |
| 길이 헤더 | 10자리 숫자 문자열 (0 패딩), 데이터 부분의 바이트 수 |
| 데이터 | 실제 전문 내용 |

**예시:**
```
0000000050TEST_DATA_HERE...
```
- `0000000050` = 데이터가 50바이트
- 전체 전문 길이 = 10 + 50 = 60바이트

## 서버 스펙

| 항목 | 값 |
|------|-----|
| 응답 크기 | 10~14KB (랜덤) |
| 응답 지연 | 0.5~2초 (랜덤) |
| 최대 동시 세션 | 100개 (초과 시 연결 거부) |
| 기본 포트 | 9000 |

## 사용법

### 서버 실행

```bash
./run.sh             # 기본: 포트 9000, 스레드 50개
./run.sh 9001        # 포트 지정
./run.sh 9001 100    # 포트 + 최대 스레드 수
```

### 테스트 클라이언트

```bash
./test-client.sh                  # localhost:9000
./test-client.sh localhost 9001   # 포트 지정
```

## 프로젝트 구조

```
nice-test/
├── src/main/java/kr/honestfund/nice/mock/
│   ├── NiceMockServer.java   # 모킹 서버
│   └── TestClient.java       # 테스트 클라이언트
├── src/main/resources/
│   └── logback.xml           # 로깅 설정
├── lib/                      # 의존성 (logback, slf4j)
├── out/                      # 컴파일된 클래스
├── run.sh                    # 서버 실행 스크립트
└── test-client.sh            # 클라이언트 테스트 스크립트
```

## 코드 설명

### NiceMockServer.java

#### 주요 상수

```java
private static final int LENGTH_HEADER_SIZE = 10;      // 길이 헤더 크기
private static final int MIN_RESPONSE_SIZE = 10 * 1024; // 최소 응답 10KB
private static final int MAX_RESPONSE_SIZE = 14 * 1024; // 최대 응답 14KB
private static final int MIN_DELAY_MS = 500;            // 최소 지연 0.5초
private static final int MAX_DELAY_MS = 2000;           // 최대 지연 2초
private static final int MAX_SESSIONS = 100;            // 최대 동시 세션
```

#### 세션 제한 로직

`Semaphore`를 사용하여 동시 연결 수를 100개로 제한합니다.

```java
private final Semaphore sessionSemaphore = new Semaphore(MAX_SESSIONS);

// accept 후 세션 획득 시도 (non-blocking)
if (!sessionSemaphore.tryAcquire()) {
    // 100개 초과 시 즉시 연결 거부
    clientSocket.close();
    continue;
}

// 요청 처리 후 세션 반환
try {
    handleClient(clientSocket);
} finally {
    sessionSemaphore.release();
}
```

#### 전문 읽기 (readMessage)

1. 먼저 10바이트 길이 헤더를 읽음
2. 헤더를 파싱하여 데이터 길이 확인
3. 해당 길이만큼 데이터를 읽음
4. EUC-KR 바이트 수 기준으로 길이 체크

```java
// 1. 길이 헤더 읽기 (10바이트)
char[] lengthBuffer = new char[LENGTH_HEADER_SIZE];
// ... 읽기 ...

int dataLength = Integer.parseInt(lengthStr.trim());

// 2. 데이터 읽기 (EUC-KR 바이트 수 기준)
while (bytesRead < dataLength) {
    data.append((char) reader.read());
    bytesRead = data.substring(LENGTH_HEADER_SIZE).getBytes(EUC_KR).length;
}
```

#### 응답 생성 (generateResponse)

1. 10~14KB 범위에서 랜덤 크기 결정
2. 응답 데이터 생성 (요청 ID, 타임스탬프, 요청 일부 포함)
3. 목표 크기까지 패딩 데이터 추가
4. 10자리 길이 헤더 + 데이터 형태로 반환

```java
int targetSize = MIN_RESPONSE_SIZE + random.nextInt(MAX_RESPONSE_SIZE - MIN_RESPONSE_SIZE + 1);

// 데이터 생성
StringBuilder body = new StringBuilder();
body.append("MOCK_RESPONSE_").append(reqId).append("_")...

// 패딩 추가하여 목표 크기 맞추기
String padding = generatePaddingData(targetSize - body.length());
body.append(padding);

// 길이 헤더 (10자리 0패딩)
String lengthHeader = String.format("%010d", dataByteLength);
return lengthHeader + data;
```

## NiceProxyServer 연동 설정

`application.properties`에서 모킹 서버를 가리키도록 설정:

```properties
nice.host=localhost
nice.port=9000
nice.soTimeout=30000
```