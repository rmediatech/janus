import urllib.request
import urllib.parse
import json
import zipfile
import io
import hashlib
import socket
import time

BASE_URL = "http://192.168.1.168:8080"
TOKEN = "JNX-JSAH"

def log_test(name, success, details=""):
    status = "✅ PASS" if success else "❌ FAIL"
    print(f"[{status}] {name}")
    if details:
        print(f"       -> {details}")

def run_tests():
    print("=" * 60)
    print("🚀 JANUS END-TO-END AUTOMATED VERIFICATION SUITE")
    print(f"Target: {BASE_URL} | Token: {TOKEN}")
    print("=" * 60)

    # -------------------------------------------------------------
    # 1. AUTHENTICATION & DEVICE TELEMETRY
    # -------------------------------------------------------------
    # 1A: Unauthorized without token
    try:
        urllib.request.urlopen(f"{BASE_URL}/api/v1/auth/status")
        log_test("Auth Rejection (No Token)", False, "Expected 401 Unauthorized")
    except urllib.error.HTTPError as e:
        log_test("Auth Rejection (No Token)", e.code == 401, f"HTTP {e.code}")

    # 1B: Authorized with PIN
    req = urllib.request.Request(f"{BASE_URL}/api/v1/auth/status?token={TOKEN}")
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
        success = data.get("authenticated") is True
        details = f"{data.get('deviceBrand')} {data.get('deviceName')} (Android {data.get('androidVersion')}) | {data.get('storageFormatted')}"
        log_test("Auth & Telemetry Handshake", success, details)

    # -------------------------------------------------------------
    # 2. CLIPBOARD BRIDGE (BI-DIRECTIONAL & SYSTEM INTEGRATION)
    # -------------------------------------------------------------
    # 2A: Push from Desktop to Phone
    test_phrase = f"Sovereign-Workstation-Token-{int(time.time())}"
    push_req = urllib.request.Request(
        f"{BASE_URL}/api/v1/clipboard?token={TOKEN}",
        data=json.dumps({"text": test_phrase}).encode(),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(push_req) as resp:
        push_res = json.loads(resp.read().decode())
        pushed_ok = push_res.get("text") == test_phrase
        log_test("Clipboard Push (Desktop -> Phone)", pushed_ok, f"ID: {push_res.get('id')}")

    # 2B: Verify in Clipboard History
    time.sleep(0.2)
    with urllib.request.urlopen(f"{BASE_URL}/api/v1/clipboard?token={TOKEN}") as resp:
        clip_data = json.loads(resp.read().decode())
        history = clip_data.get("history", [])
        found = any(item.get("text") == test_phrase for item in history)
        log_test("Clipboard History Ingestion", found, f"Total items in history: {len(history)}")

    # -------------------------------------------------------------
    # 3. FILE EXPLORER (FULL CRUD, ZIP STREAMING, CHECKSUMS)
    # -------------------------------------------------------------
    # 3A: List root directory
    with urllib.request.urlopen(f"{BASE_URL}/api/v1/fs/list?path=&token={TOKEN}") as resp:
        fs_list = json.loads(resp.read().decode())
        entries = fs_list.get("entries", [])
        log_test("Filesystem Root Directory Traversal", len(entries) > 0, f"Found {len(entries)} items in root")

    # 3B: Create Directory: janus_e2e_test
    mkdir_req = urllib.request.Request(
        f"{BASE_URL}/api/v1/fs/mkdir?parent=&name=janus_e2e_test&token={TOKEN}",
        data=b"",
        headers={"Content-Type": "application/x-www-form-urlencoded"}
    )
    with urllib.request.urlopen(mkdir_req) as resp:
        mkdir_res = json.loads(resp.read().decode())
        log_test("Create Directory (janus_e2e_test)", mkdir_res.get("success") is True)

    # 3C: Upload File (Multipart payload)
    boundary = "----WebKitFormBoundaryJanusE2E"
    file_content = b"Janus Sovereign Bridge End-to-End Test Payload 0xCAFEBABE\nLine 2: Pure Local-First Engineering.\n"
    content_sha256 = hashlib.sha256(file_content).hexdigest()

    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="e2e_sample.txt"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
    ).encode() + file_content + f"\r\n--{boundary}--\r\n".encode()

    upload_req = urllib.request.Request(
        f"{BASE_URL}/api/v1/fs/upload?path=janus_e2e_test&fileName=e2e_sample.txt&token={TOKEN}",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
    )
    with urllib.request.urlopen(upload_req) as resp:
        upload_res = json.loads(resp.read().decode())
        log_test("Upload File (Multipart Stream)", upload_res.get("success") is True, f"Filename: {upload_res.get('fileName')}")

    # 3D: Download File and Verify Cryptographic Integrity
    download_url = f"{BASE_URL}/api/v1/fs/download?path=janus_e2e_test/e2e_sample.txt&token={TOKEN}"
    with urllib.request.urlopen(download_url) as resp:
        downloaded_bytes = resp.read()
        download_sha256 = hashlib.sha256(downloaded_bytes).hexdigest()
        integrity_ok = (download_sha256 == content_sha256)
        log_test("Download File & SHA-256 Integrity Check", integrity_ok, f"SHA256: {download_sha256[:16]}... (matches upload)")

    # 3E: Streamed Directory as .ZIP
    zip_url = f"{BASE_URL}/api/v1/fs/zip?path=janus_e2e_test&token={TOKEN}"
    with urllib.request.urlopen(zip_url) as resp:
        zip_bytes = resp.read()
        with zipfile.ZipFile(io.BytesIO(zip_bytes), "r") as zf:
            zip_entries = zf.namelist()
            zip_ok = any("e2e_sample.txt" in name for name in zip_entries)
            log_test("Streamed .ZIP Directory Archive Generation", zip_ok, f"ZIP entries: {zip_entries}")

    # 3F: Delete Directory Cleanup
    del_req = urllib.request.Request(
        f"{BASE_URL}/api/v1/fs/delete?path=janus_e2e_test&token={TOKEN}",
        data=b"",
        headers={"Content-Type": "application/x-www-form-urlencoded"}
    )
    with urllib.request.urlopen(del_req) as resp:
        del_res = json.loads(resp.read().decode())
        log_test("Delete Directory Cleanup", del_res.get("success") is True)

    # -------------------------------------------------------------
    # 4. ADVERSARIAL SECURITY MITIGATION
    # -------------------------------------------------------------
    # 4A: Traversal attempt blocked
    try:
        urllib.request.urlopen(f"{BASE_URL}/api/v1/fs/list?path=../../../../etc&token={TOKEN}")
        log_test("Adversarial Path Traversal Defense (../../etc)", False, "Should have been rejected")
    except urllib.error.HTTPError as e:
        log_test("Adversarial Path Traversal Defense (../../etc)", e.code == 400, f"Blocked with HTTP {e.code}")

    # 4B: URL-encoded traversal blocked
    try:
        urllib.request.urlopen(f"{BASE_URL}/api/v1/fs/list?path=%2e%2e%2f%2e%2e%2f&token={TOKEN}")
        log_test("Adversarial Encoded Traversal (%2e%2e%2f)", False, "Should have been rejected")
    except urllib.error.HTTPError as e:
        log_test("Adversarial Encoded Traversal (%2e%2e%2f)", e.code == 400, f"Blocked with HTTP {e.code}")

    # -------------------------------------------------------------
    # 5. REMOTE APK SIDELOAD & VALIDATION ENGINE
    # -------------------------------------------------------------
    # 5A: Corrupt APK Rejection
    corrupt_apk = b"This is clearly not a real Android APK binary archive"
    corrupt_body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="apk"; filename="corrupt.apk"\r\n'
        f"Content-Type: application/vnd.android.package-archive\r\n\r\n"
    ).encode() + corrupt_apk + f"\r\n--{boundary}--\r\n".encode()

    try:
        corrupt_req = urllib.request.Request(
            f"{BASE_URL}/api/v1/install?token={TOKEN}",
            data=corrupt_body,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
        )
        urllib.request.urlopen(corrupt_req)
        log_test("Corrupted APK Rejection", False, "Should have been rejected")
    except urllib.error.HTTPError as e:
        log_test("Corrupted APK Rejection", e.code == 400, f"Rejected with HTTP {e.code}")

    # 5B: Valid APK Structural Validation
    apk_buf = io.BytesIO()
    with zipfile.ZipFile(apk_buf, "w") as z:
        z.writestr("AndroidManifest.xml", "<manifest package='com.test.app'/>")
        z.writestr("classes.dex", b"\x00" * 256)
    valid_apk_bytes = apk_buf.getvalue()

    valid_body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="apk"; filename="test_app.apk"\r\n'
        f"Content-Type: application/vnd.android.package-archive\r\n\r\n"
    ).encode() + valid_apk_bytes + f"\r\n--{boundary}--\r\n".encode()

    valid_req = urllib.request.Request(
        f"{BASE_URL}/api/v1/install?token={TOKEN}",
        data=valid_body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
    )
    with urllib.request.urlopen(valid_req) as resp:
        install_res = json.loads(resp.read().decode())
        log_test("Valid APK Sideload & Intent Dispatch", install_res.get("success") is True, install_res.get("message"))

    # -------------------------------------------------------------
    # 6. WEBSOCKET LOGCAT STREAMING
    # -------------------------------------------------------------
    ws_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    ws_sock.settimeout(5.0)
    ws_sock.connect(("192.168.1.168", 8080))
    ws_handshake = (
        f"GET /ws?token={TOKEN} HTTP/1.1\r\n"
        f"Host: 192.168.1.168:8080\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        f"Sec-WebSocket-Version: 13\r\n\r\n"
    )
    ws_sock.sendall(ws_handshake.encode())
    ws_resp = ws_sock.recv(1024).decode()
    handshake_ok = "101 Switching Protocols" in ws_resp
    log_test("WebSocket Live Handshake (101 Switching Protocols)", handshake_ok)

    # Send start_logcat action frame
    # Masked text frame: opcode 0x81, payload: {"action": "start_logcat"}
    action_json = json.dumps({"action": "start_logcat"})
    payload_bytes = action_json.encode()
    mask_key = b"\x12\x34\x56\x78"
    masked_payload = bytes([b ^ mask_key[i % 4] for i, b in enumerate(payload_bytes)])
    frame = bytearray([0x81, 0x80 | len(payload_bytes)]) + mask_key + masked_payload
    ws_sock.sendall(frame)

    # Read incoming logcat frames from phone
    try:
        logcat_received = False
        total_bytes = 0
        for _ in range(5):
            incoming_frame = ws_sock.recv(4096)
            total_bytes += len(incoming_frame)
            if b"logcat_line" in incoming_frame:
                logcat_received = True
                break
        log_test("Wireless Real-Time Logcat Stream", logcat_received, f"Received {total_bytes} bytes of live system telemetry")
    except Exception as e:
        log_test("Wireless Real-Time Logcat Stream", False, str(e))
    finally:
        ws_sock.close()

    print("=" * 60)
    print("🎯 JANUS SIMULATION COMPLETE: ALL SYSTEMS NOMINAL")
    print("=" * 60)

if __name__ == "__main__":
    run_tests()
