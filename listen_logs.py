import os
import shutil
import socket
import subprocess
import sys
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 9999

def setup_adb_reverse():
    """Attempts to find adb and forward port 9999 automatically."""
    adb_candidates = [
        "adb",
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
        r"C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    ]
    
    adb_path = None
    for candidate in adb_candidates:
        if shutil.which(candidate) or os.path.exists(candidate):
            adb_path = candidate
            break

    if adb_path:
        try:
            res = subprocess.run([adb_path, "reverse", f"tcp:{PORT}", f"tcp:{PORT}"], capture_output=True, text=True, timeout=5)
            if res.returncode == 0:
                print(f"[\033[92m✓\033[0m] ADB USB/Studio Bridge ACTIVE! Port {PORT} forwarded.")
                print(f"    -> On your phone, you can simply use Laptop IP: \033[92m127.0.0.1\033[0m")
                return True
        except Exception:
            pass
    return False

def get_local_ips():
    """Detect all active local IPv4 addresses and determine primary Wi-Fi/LAN IP."""
    ips = []
    primary_ip = None

    # Probe primary outbound interface (doesn't send actual internet data)
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        primary_ip = s.getsockname()[0]
        s.close()
        if primary_ip and not primary_ip.startswith("127."):
            ips.append(primary_ip)
    except Exception:
        pass

    # Gather additional host network interfaces
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except Exception:
        pass

    return ips, (primary_ip or (ips[0] if ips else "127.0.0.1"))

def log_received(source, text):
    print(f"\n--- Log from {source} ---")
    print(text)
    with open("laptop_rail_log.txt", "a", encoding="utf-8") as f:
        f.write(f"\n--- {source} ---\n" + text + "\n")

# HTTP Receiver (100% reliable, bypasses Android kernel EPERM restrictions)
class LogHttpHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8', errors='replace')
            log_received(f"HTTP {self.client_address[0]}", body)
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"OK")
        except Exception:
            self.send_response(500)
            self.end_headers()

    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"RailGuard Logger Online")

    def log_message(self, format, *args):
        pass  # Suppress default server access logs

def run_http():
    try:
        httpd = HTTPServer(("0.0.0.0", PORT), LogHttpHandler)
        httpd.serve_forever()
    except Exception as e:
        print(f"[HTTP Server Error]: {e}")

# UDP Receiver
def run_udp():
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind(("0.0.0.0", PORT))
        while True:
            data, addr = sock.recvfrom(65535)
            text = data.decode('utf-8', errors='replace')
            log_received(f"UDP {addr[0]}", text)
    except Exception as e:
        print(f"[UDP Server Error]: {e}")

if __name__ == "__main__":
    all_ips, primary = get_local_ips()

    print("=" * 60)
    print("           RAILGUARD LIVE LOG RECEIVER (HTTP + UDP)")
    print("=" * 60)
    
    adb_active = setup_adb_reverse()
    if not adb_active:
        print(f"[*] PRIMARY WI-FI IP:                  \033[92m{primary}\033[0m")
        if len(all_ips) > 1:
            other_ips = [ip for ip in all_ips if ip != primary]
            print(f"[*] Other Available Laptop IPs:        {', '.join(other_ips)}")
    
    print(f"[*] Listening on Port:                 {PORT}")
    print("=" * 60)
    print("Waiting for logs from RailGuard mobile app...")
    print("• Tap 'Send Test Packet to Laptop' in the app to test.")
    print("• Tap 'START MONITORING' to stream continuous AI profiling.\n")

    # Start UDP listener in background thread
    t_udp = threading.Thread(target=run_udp, daemon=True)
    t_udp.start()

    # Start HTTP listener on main thread
    try:
        run_http()
    except KeyboardInterrupt:
        print("\nLogger stopped.")
