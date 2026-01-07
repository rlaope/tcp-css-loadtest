package kr.honestfund.nice.mock;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * 간단한 테스트 클라이언트
 *
 * 사용법: java TestClient [host] [port]
 */
public class TestClient {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static final int LENGTH_HEADER_SIZE = 10;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9000;

        System.out.println("Connecting to " + host + ":" + port);

        // 테스트 요청 생성
        String requestData = "TEST_REQUEST_DATA_" + System.currentTimeMillis();
        String lengthHeader = String.format("%010d", requestData.getBytes(EUC_KR).length);
        String request = lengthHeader + requestData;

        System.out.println("Sending request: " + request);
        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(30000);

            // 요청 전송
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(EUC_KR));
            out.flush();

            // 응답 읽기
            InputStream in = socket.getInputStream();
            String response = readMessage(in);

            long elapsed = System.currentTimeMillis() - startTime;

            System.out.println("\n=== Response ===");
            System.out.println("Size: " + response.getBytes(EUC_KR).length + " bytes");
            System.out.println("Elapsed: " + elapsed + " ms");
            System.out.println("First 200 chars: " + response.substring(0, Math.min(200, response.length())));
        }
    }

    private static String readMessage(InputStream in) throws IOException {
        InputStreamReader reader = new InputStreamReader(in, EUC_KR);

        // 길이 헤더 읽기
        char[] lengthBuffer = new char[LENGTH_HEADER_SIZE];
        int totalRead = 0;
        while (totalRead < LENGTH_HEADER_SIZE) {
            int read = reader.read(lengthBuffer, totalRead, LENGTH_HEADER_SIZE - totalRead);
            if (read == -1) throw new IOException("EOF");
            totalRead += read;
        }

        String lengthStr = new String(lengthBuffer);
        int dataLength = Integer.parseInt(lengthStr.trim());

        // 데이터 읽기
        StringBuilder data = new StringBuilder();
        data.append(lengthStr);

        int bytesRead = 0;
        while (bytesRead < dataLength) {
            int c = reader.read();
            if (c == -1) break;
            data.append((char) c);
            bytesRead = data.substring(LENGTH_HEADER_SIZE).getBytes(EUC_KR).length;
        }

        return data.toString();
    }
}
