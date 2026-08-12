#!/usr/bin/env python3
"""Minimal OpenAI-compatible mock for on-device tool-loop tests."""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = "/tmp/lightui-mock-openai.log"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8787


def log(msg):
    line = msg if isinstance(msg, str) else json.dumps(msg)
    with open(LOG, "a") as f:
        f.write(line + "\n")
        f.flush()
    print(line, flush=True)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        log("http " + (fmt % args))

    def _send(self, code, body, content_type="application/json"):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.endswith("/models") or self.path.endswith("/v1/models"):
            self._send(200, json.dumps({"data": [{"id": "mock-llm", "name": "mock llm", "context_length": 8192}]}))
            return
        self._send(404, json.dumps({"error": {"message": "not found"}}))

    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n).decode("utf-8") if n else ""
        log("POST " + self.path)
        log(raw[:4000])
        try:
            body = json.loads(raw) if raw else {}
        except Exception:
            body = {}
        has_tools = "tools" in body
        blob = json.dumps(body)
        has_tool_role = '"role": "tool"' in blob or '"role":"tool"' in blob
        has_text_result = "Tool result (web_search)" in blob
        if has_tool_role or has_text_result:
            text = "Typical DDR5-6000 64GB kits are about $280-$360 today from the search results."
            self._sse_content(text)
            return
        if has_tools:
            self._sse_tool("ddr5 6000 64gb price")
            return
        self._sse_content("Sure.\nSEARCH: ddr5 6000 64gb price")

    def _sse_content(self, text):
        chunk = json.dumps({"choices": [{"delta": {"content": text}}]})
        done = json.dumps({"choices": [{"delta": {}, "finish_reason": "stop"}]})
        payload = f"data: {chunk}\n\ndata: {done}\n\ndata: [DONE]\n\n"
        self._send(200, payload, "text/event-stream")

    def _sse_tool(self, query):
        first = json.dumps({"choices": [{"delta": {"tool_calls": [{"index": 0, "id": "call_1", "type": "function", "function": {"name": "web_search", "arguments": ""}}]}}]})
        second = json.dumps({"choices": [{"delta": {"tool_calls": [{"index": 0, "function": {"arguments": json.dumps({"query": query})}}]}}]})
        done = json.dumps({"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
        payload = f"data: {first}\n\ndata: {second}\n\ndata: {done}\n\ndata: [DONE]\n\n"
        self._send(200, payload, "text/event-stream")


if __name__ == "__main__":
    open(LOG, "w").write("mock openai listening on %s\n" % PORT)
    httpd = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    log("listening on 127.0.0.1:%s" % PORT)
    httpd.serve_forever()
