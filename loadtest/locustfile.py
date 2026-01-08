"""
NiceProxyServer TCP Load Test using Locust

Protocol:
- [10-byte length header][data] format
- EUC-KR encoding
- Length header = zero-padded data byte length

Load Pattern:
- Start: 20 TPS
- Peak: 200 TPS (spike)
- Gradual increase and decrease
"""

import socket
import time
import random
import string
from locust import User, task, events, between
from locust.env import Environment


class NiceTcpClient:
    """Custom TCP client for Nice protocol"""

    LENGTH_HEADER_SIZE = 10
    ENCODING = "euc-kr"

    def __init__(self, host: str, port: int, timeout: float = 30.0):
        self.host = host
        self.port = port
        self.timeout = timeout

    def _create_message(self, data: str) -> bytes:
        """Create Nice protocol message with length header"""
        data_bytes = data.encode(self.ENCODING)
        length_header = f"{len(data_bytes):010d}"
        return length_header.encode(self.ENCODING) + data_bytes

    def _read_response(self, sock: socket.socket) -> str:
        """Read Nice protocol response"""
        # Read length header (10 bytes)
        header = b""
        while len(header) < self.LENGTH_HEADER_SIZE:
            chunk = sock.recv(self.LENGTH_HEADER_SIZE - len(header))
            if not chunk:
                raise ConnectionError("Connection closed while reading header")
            header += chunk

        data_length = int(header.decode(self.ENCODING))

        # Read data
        data = b""
        while len(data) < data_length:
            chunk = sock.recv(min(4096, data_length - len(data)))
            if not chunk:
                raise ConnectionError("Connection closed while reading data")
            data += chunk

        return (header + data).decode(self.ENCODING)

    def send_request(self, data: str) -> tuple[str, float]:
        """
        Send request and receive response.
        Returns (response, response_time_ms)
        """
        start_time = time.perf_counter()
        sock = None

        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(self.timeout)
            sock.connect((self.host, self.port))

            message = self._create_message(data)
            sock.sendall(message)

            response = self._read_response(sock)
            response_time = (time.perf_counter() - start_time) * 1000

            return response, response_time

        finally:
            if sock:
                try:
                    sock.close()
                except:
                    pass


class NiceProxyUser(User):
    """Locust User for NiceProxy load testing"""

    # Wait time between tasks (0 for max throughput, controlled by spawn rate)
    wait_time = between(0.01, 0.05)

    def __init__(self, environment: Environment):
        super().__init__(environment)
        self.client = NiceTcpClient(
            host=self.host,
            port=int(environment.parsed_options.nice_port or 21003),
            timeout=30.0
        )

    def on_start(self):
        """Called when user starts"""
        pass

    def _generate_request_data(self) -> str:
        """Generate random request data"""
        req_id = "".join(random.choices(string.ascii_uppercase + string.digits, k=8))
        timestamp = time.strftime("%Y%m%d%H%M%S")
        random_data = "".join(random.choices(string.ascii_letters + string.digits, k=random.randint(100, 500)))
        return f"REQ_{req_id}_{timestamp}_{random_data}"

    @task
    def send_nice_request(self):
        """Send a single request to NiceProxy"""
        request_data = self._generate_request_data()
        request_name = "nice_tcp_request"

        try:
            response, response_time = self.client.send_request(request_data)

            # Report success
            events.request.fire(
                request_type="TCP",
                name=request_name,
                response_time=response_time,
                response_length=len(response),
                exception=None,
                context=self.context()
            )

        except Exception as e:
            # Report failure
            events.request.fire(
                request_type="TCP",
                name=request_name,
                response_time=0,
                response_length=0,
                exception=e,
                context=self.context()
            )


# Custom load shape: 20 TPS -> 200 TPS peak -> back to 20 TPS
# Uncomment below to enable automatic load shape
from locust import LoadTestShape


class NiceLoadShape(LoadTestShape):
    """
    Custom load shape for Nice Proxy stress test.

    Pattern:
    - Phase 1 (0-60s): Warm up from 20 to 50 TPS
    - Phase 2 (60-120s): Ramp up from 50 to 100 TPS
    - Phase 3 (120-150s): Spike to 200 TPS (peak)
    - Phase 4 (150-180s): Hold at 200 TPS
    - Phase 5 (180-240s): Cool down to 50 TPS
    - Phase 6 (240-300s): Stabilize at 20 TPS
    """

    stages = [
        # (duration_seconds, users, spawn_rate)
        {"duration": 60, "users": 50, "spawn_rate": 2},      # Warm up
        {"duration": 120, "users": 100, "spawn_rate": 2},    # Ramp up
        {"duration": 150, "users": 200, "spawn_rate": 10},   # Spike up
        {"duration": 180, "users": 200, "spawn_rate": 10},   # Hold peak
        {"duration": 240, "users": 50, "spawn_rate": 5},     # Cool down
        {"duration": 300, "users": 20, "spawn_rate": 2},     # Stabilize
    ]

    def tick(self):
        run_time = self.get_run_time()

        for stage in self.stages:
            if run_time < stage["duration"]:
                return (stage["users"], stage["spawn_rate"])

        return None  # Stop test


# Event hooks for custom logging
@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("=" * 60)
    print("NiceProxy TCP Load Test Started")
    print(f"Target: {environment.host}")
    print("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("=" * 60)
    print("NiceProxy TCP Load Test Completed")
    print("=" * 60)


# Custom command line arguments
@events.init_command_line_parser.add_listener
def init_parser(parser):
    parser.add_argument(
        "--nice-port",
        type=int,
        default=21003,
        help="NiceProxy server port (default: 21003)"
    )


@events.init.add_listener
def on_init(environment, **kwargs):
    if environment.parsed_options:
        print(f"NiceProxy Port: {environment.parsed_options.nice_port}")