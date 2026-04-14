#!/usr/bin/env python3

import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


HOST = os.getenv("FACTCHECK_HOST", "0.0.0.0")
PORT = int(os.getenv("FACTCHECK_PORT", "8080"))
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-5")


SYSTEM_PROMPT = """You are a fact-checking assistant.
Use live web search results to assess the user's text.
Return strict JSON with this shape:
{
  "claim": "main claim being checked",
  "verdict": "True|Mostly True|Unclear|Mostly False|False",
  "confidence": 0.0,
  "summary": "2-4 sentence factual summary",
  "sources": [
    {"title": "source title", "url": "https://..."}
  ]
}

Rules:
- Only include sources you actually relied on.
- Prefer primary sources and high-quality reporting.
- If evidence is mixed or weak, say "Unclear".
- Confidence must be between 0 and 1.
- Return JSON only.
"""


def call_openai_fact_check(text: str) -> dict:
    if not OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY is not set.")

    payload = {
        "model": OPENAI_MODEL,
        "reasoning": {"effort": "low"},
        "tools": [{"type": "web_search"}],
        "tool_choice": "auto",
        "input": [
            {
                "role": "system",
                "content": [{"type": "input_text", "text": SYSTEM_PROMPT}],
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_text",
                        "text": f"Fact-check this text using live web data and cite sources: {text}",
                    }
                ],
            },
        ],
    }

    request = Request(
        "https://api.openai.com/v1/responses",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {OPENAI_API_KEY}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urlopen(request, timeout=60) as response:
            response_json = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI API error {exc.code}: {details}") from exc
    except URLError as exc:
        raise RuntimeError(f"Network error: {exc}") from exc

    output_text = response_json.get("output_text", "").strip()
    if not output_text:
        raise RuntimeError("OpenAI response did not include output_text.")

    try:
        result = json.loads(output_text)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Model did not return valid JSON: {output_text}") from exc

    return {
        "claim": result.get("claim", ""),
        "verdict": result.get("verdict", "Unclear"),
        "confidence": float(result.get("confidence", 0.0)),
        "summary": result.get("summary", ""),
        "sources": result.get("sources", []),
    }


class FactCheckHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/factcheck":
            self.send_json(404, {"error": "Not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            raw_body = self.rfile.read(content_length).decode("utf-8")
            body = json.loads(raw_body)
            text = str(body.get("text", "")).strip()
            if not text:
                self.send_json(400, {"error": "Missing 'text' field"})
                return

            result = call_openai_fact_check(text)
            self.send_json(200, result)
        except Exception as exc:
            self.send_json(500, {"error": str(exc)})

    def do_GET(self):
        if self.path == "/health":
            self.send_json(
                200,
                {
                    "ok": True,
                    "model": OPENAI_MODEL,
                    "openai_key_present": bool(OPENAI_API_KEY),
                },
            )
            return

        self.send_json(404, {"error": "Not found"})

    def send_json(self, status_code: int, payload: dict):
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt: str, *args):
        return


if __name__ == "__main__":
    server = HTTPServer((HOST, PORT), FactCheckHandler)
    print(f"Fact-check backend listening on http://{HOST}:{PORT}")
    server.serve_forever()
