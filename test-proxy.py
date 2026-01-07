#!/usr/bin/env python3
"""NiceProxy 수동 테스트 스크립트"""

import socket
import sys
import time

def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 21003

    # 테스트 데이터
    data = f"TEST_REQUEST_{time.strftime('%Y%m%d%H%M%S')}_HELLO_NICE_PROXY"
    data_bytes = data.encode("euc-kr")
    length_header = f"{len(data_bytes):010d}".encode("euc-kr")

    print("=" * 50)
    print("NiceProxy Manual Test")
    print("=" * 50)
    print(f"Target: {host}:{port}")
    print(f"Data: {data}")
    print(f"Length Header: {length_header.decode()}")
    print("=" * 50)
    print()
    print("Sending request...")

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(30)
        sock.connect((host, port))

        # 요청 전송
        sock.sendall(length_header + data_bytes)
        print("Request sent, waiting for response...")

        # 응답 길이 헤더 읽기 (10바이트)
        header = b""
        while len(header) < 10:
            chunk = sock.recv(10 - len(header))
            if not chunk:
                print("ERROR: Connection closed while reading header")
                return
            header += chunk

        resp_length = int(header.decode("euc-kr"))
        print(f"Response length header: {header.decode()} (data: {resp_length} bytes)")

        # 응답 데이터 읽기
        data = b""
        while len(data) < resp_length:
            chunk = sock.recv(min(4096, resp_length - len(data)))
            if not chunk:
                print("ERROR: Connection closed while reading data")
                return
            data += chunk

        full_response = header + data
        print()
        print(f"Response received! Total: {len(full_response)} bytes")
        print()
        print("First 300 chars:")
        print(full_response.decode("euc-kr")[:300])
        print("...")

    except socket.timeout:
        print("ERROR: Timeout waiting for response")
    except Exception as e:
        print(f"ERROR: {e}")
    finally:
        sock.close()

if __name__ == "__main__":
    main()
