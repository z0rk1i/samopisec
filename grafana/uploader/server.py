import os
from http.server import BaseHTTPRequestHandler, HTTPServer

CSV_PATH = "/tmp/samopisec.csv"
HEADER = "id,button_id,ts"

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/health", "/"):
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"ok")
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path != "/upload":
            self.send_response(404)
            self.end_headers()
            return
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length else b""
        text = body.decode("utf-8", errors="ignore")
        lines = [l for l in text.splitlines() if l.strip() != ""]
        if not lines:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"empty")
            return
        if lines[0] != HEADER:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"bad header")
            return
        tmp = CSV_PATH + ".tmp"
        try:
            with open(tmp, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
                if not text.endswith("\n"):
                    f.write("\n")
            os.replace(tmp, CSV_PATH)
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(str(e).encode())
            return
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(b"ok")

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def log_message(self, format, *args):
        print("%s - - [%s] %s" % (self.client_address[0], self.log_date_time_string(), format % args))

if __name__ == "__main__":
    addr = ("0.0.0.0", 8002)
    httpd = HTTPServer(addr, Handler)
    print(f"uploader listening on {addr}, CSV={CSV_PATH}")
    httpd.serve_forever()
