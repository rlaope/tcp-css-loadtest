package kr.honestfund.nice.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nice 연동 테스트용 모킹 서버
 *
 * 프로토콜:
 * - 인코딩: EUC-KR
 * - 전문 포맷: [10바이트 길이][데이터]
 * - 길이는 데이터 부분의 바이트 수 (10바이트 헤더 제외)
 *
 * 응답 특성:
 * - 응답 크기: 10~14KB (랜덤)
 * - 지연 시간: 0.5~2초 (랜덤)
 * - 최대 동시 세션: 100개 (초과 시 연결 거부)
 */
public class NiceMockServer {

    private static final Logger log = LoggerFactory.getLogger(NiceMockServer.class);
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static final int LENGTH_HEADER_SIZE = 10;

    private static final int MIN_RESPONSE_SIZE = 10 * 1024;  // 10KB
    private static final int MAX_RESPONSE_SIZE = 14 * 1024;  // 14KB
    private static final int MIN_DELAY_MS = 500;             // 0.5초
    private static final int MAX_DELAY_MS = 2000;            // 2초
    private static final int MAX_SESSIONS = 100;             // 최대 동시 세션

    private final int port;
    private final ExecutorService executor;
    private final Semaphore sessionSemaphore;
    private final Random random = new Random();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);

    private volatile boolean running = true;

    public NiceMockServer(int port, int maxThreads) {
        this.port = port;
        this.executor = Executors.newFixedThreadPool(maxThreads);
        this.sessionSemaphore = new Semaphore(MAX_SESSIONS);
    }

    public void start() {
        log.info("===========================================");
        log.info("Nice Mock Server Starting...");
        log.info("Port: {}", port);
        log.info("Max Sessions: {}", MAX_SESSIONS);
        log.info("Response Size: {} ~ {} bytes", MIN_RESPONSE_SIZE, MAX_RESPONSE_SIZE);
        log.info("Response Delay: {} ~ {} ms", MIN_DELAY_MS, MAX_DELAY_MS);
        log.info("===========================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Server listening on port {}", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // 세션 제한 체크 (non-blocking)
                    if (!sessionSemaphore.tryAcquire()) {
                        log.warn("Max sessions reached ({}). Rejecting connection from {}",
                            MAX_SESSIONS, clientSocket.getRemoteSocketAddress());
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {}
                        continue;
                    }

                    int activeSessions = activeSessionCount.incrementAndGet();
                    log.debug("Session acquired. Active sessions: {}/{}", activeSessions, MAX_SESSIONS);

                    executor.submit(() -> {
                        try {
                            handleClient(clientSocket);
                        } finally {
                            sessionSemaphore.release();
                            int remaining = activeSessionCount.decrementAndGet();
                            log.debug("Session released. Active sessions: {}/{}", remaining, MAX_SESSIONS);
                        }
                    });
                } catch (IOException e) {
                    if (running) {
                        log.error("Accept error", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Server error", e);
        }
    }

    private void handleClient(Socket socket) {
        int reqId = requestCount.incrementAndGet();
        long startTime = System.currentTimeMillis();

        log.info("[REQ-{}] Client connected: {} (active sessions: {}/{})",
            reqId, socket.getRemoteSocketAddress(), activeSessionCount.get(), MAX_SESSIONS);

        try {
            socket.setSoTimeout(30000); // 30초 타임아웃

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // 1. 요청 읽기
            String request = readMessage(in);
            if (request == null || request.isEmpty()) {
                log.warn("[REQ-{}] Empty request received", reqId);
                return;
            }

            log.info("[REQ-{}] Request received, size={} bytes", reqId, request.getBytes(EUC_KR).length);
            log.debug("[REQ-{}] Request content: {}", reqId, truncate(request, 200));

            // 2. 랜덤 지연
            int delay = MIN_DELAY_MS + random.nextInt(MAX_DELAY_MS - MIN_DELAY_MS + 1);
            log.info("[REQ-{}] Simulating delay: {} ms", reqId, delay);
            Thread.sleep(delay);

            // 3. 응답 생성 및 전송
            String response = generateResponse(reqId, request);
            byte[] responseBytes = response.getBytes(EUC_KR);

            out.write(responseBytes);
            out.flush();

            long elapsed = System.currentTimeMillis() - startTime;

            log.info("[REQ-{}] Response sent, size={} bytes, elapsed={} ms",
                reqId, responseBytes.length, elapsed);

        } catch (Exception e) {
            log.error("[REQ-{}] Error handling client", reqId, e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
            log.info("[REQ-{}] Connection closed", reqId);
        }
    }

    /**
     * 전문 읽기 (10바이트 길이 헤더 + 데이터)
     */
    private String readMessage(InputStream in) throws IOException {
        InputStreamReader reader = new InputStreamReader(in, EUC_KR);

        // 1. 길이 헤더 읽기 (10바이트)
        char[] lengthBuffer = new char[LENGTH_HEADER_SIZE];
        int totalRead = 0;
        while (totalRead < LENGTH_HEADER_SIZE) {
            int read = reader.read(lengthBuffer, totalRead, LENGTH_HEADER_SIZE - totalRead);
            if (read == -1) {
                return null;
            }
            totalRead += read;
        }

        String lengthStr = new String(lengthBuffer);
        int dataLength;
        try {
            dataLength = Integer.parseInt(lengthStr.trim());
        } catch (NumberFormatException e) {
            log.error("Invalid length header: '{}'", lengthStr);
            return null;
        }

        // 2. 데이터 읽기
        StringBuilder data = new StringBuilder();
        data.append(lengthStr);

        int bytesRead = 0;
        while (bytesRead < dataLength) {
            int c = reader.read();
            if (c == -1) {
                break;
            }
            data.append((char) c);

            // EUC-KR 바이트 수 기준으로 체크
            bytesRead = data.substring(LENGTH_HEADER_SIZE).getBytes(EUC_KR).length;
        }

        return data.toString();
    }

    /**
     * 모킹 응답 생성
     */
    private String generateResponse(int reqId, String request) {
        // 랜덤 응답 크기 결정 (10~14KB)
        int targetSize = MIN_RESPONSE_SIZE + random.nextInt(MAX_RESPONSE_SIZE - MIN_RESPONSE_SIZE + 1);

        // 응답 데이터 생성
        StringBuilder body = new StringBuilder();
        body.append("MOCK_RESPONSE_");
        body.append(String.format("%06d", reqId));
        body.append("_");
        body.append(System.currentTimeMillis());
        body.append("_");

        // 요청의 일부 정보 포함 (있다면)
        if (request.length() > LENGTH_HEADER_SIZE + 20) {
            body.append("REQ:");
            body.append(request.substring(LENGTH_HEADER_SIZE, Math.min(LENGTH_HEADER_SIZE + 50, request.length())));
            body.append("_");
        }

        // 패딩 데이터 추가하여 목표 크기 맞추기
        // 한글은 EUC-KR에서 2바이트이므로 ASCII 문자로 패딩
        String padding = generatePaddingData(targetSize - body.toString().getBytes(EUC_KR).length);
        body.append(padding);

        String data = body.toString();
        int dataByteLength = data.getBytes(EUC_KR).length;

        // 길이 헤더 (10자리, 0패딩)
        String lengthHeader = String.format("%010d", dataByteLength);

        log.debug("[REQ-{}] Response generated, header={}, dataSize={}", reqId, lengthHeader, dataByteLength);

        return lengthHeader + data;
    }

    /**
     * 패딩 데이터 생성
     */
    private String generatePaddingData(int size) {
        if (size <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(size);
        String[] patterns = {
            "DATA_BLOCK_",
            "SAMPLE_TEXT_",
            "MOCK_FIELD_",
            "TEST_VALUE_",
            "RESPONSE_ITEM_"
        };

        int blockNum = 0;
        while (sb.length() < size) {
            String pattern = patterns[blockNum % patterns.length];
            sb.append(pattern);
            sb.append(String.format("%05d", blockNum));
            sb.append("_");

            // 가끔 줄바꿈 추가
            if (blockNum % 10 == 0) {
                sb.append("\n");
            }
            blockNum++;
        }

        // 정확한 크기로 자르기
        if (sb.length() > size) {
            return sb.substring(0, size);
        }
        return sb.toString();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    public void stop() {
        running = false;
        executor.shutdown();
    }

    public static void main(String[] args) {
        int port = 9000;  // 기본 포트
        int maxThreads = 50;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        if (args.length > 1) {
            try {
                maxThreads = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid max threads: " + args[1]);
                System.exit(1);
            }
        }

        NiceMockServer server = new NiceMockServer(port, maxThreads);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down server...");
            server.stop();
        }));

        server.start();
    }
}
