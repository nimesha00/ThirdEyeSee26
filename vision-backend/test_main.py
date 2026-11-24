"""Tests for the request handling around the Claude call.

The Anthropic SDK is replaced with a fake, so these run offline and cost nothing.
They do not prove the real Claude request is accepted; do one live check after deploying.
"""

import base64
import os
import sys
import types
import unittest

# --- fake anthropic module (installed before main is imported) ---------------
fake = types.ModuleType("anthropic")


class _APIError(Exception):
    def __init__(self, message="err", status_code=500):
        super().__init__(message)
        self.message = message
        self.status_code = status_code


for _name in ("APIStatusError", "BadRequestError", "AuthenticationError", "RateLimitError"):
    setattr(fake, _name, type(_name, (_APIError,), {}))
fake.BadRequestError.__bases__ = (fake.APIStatusError,)
fake.AuthenticationError.__bases__ = (fake.APIStatusError,)
fake.RateLimitError.__bases__ = (fake.APIStatusError,)
fake.APIConnectionError = type("APIConnectionError", (Exception,), {})


class _Block:
    def __init__(self, type_, text=""):
        self.type = type_
        self.text = text


class _Response:
    def __init__(self, blocks, stop_reason="end_turn"):
        self.content = blocks
        self.stop_reason = stop_reason
        self._request_id = "req_test"


class _Messages:
    def __init__(self):
        self.next = None
        self.raise_ = None
        self.last_kwargs = None

    def create(self, **kwargs):
        self.last_kwargs = kwargs
        if self.raise_:
            raise self.raise_
        return self.next


class _Client:
    def __init__(self, *a, **k):
        self.beta = types.SimpleNamespace(messages=_Messages())


fake.Anthropic = _Client
sys.modules["anthropic"] = fake
os.environ["APP_TOKEN"] = "test-token"

import main  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

JPEG_B64 = base64.b64encode(b"\xff\xd8\xff\xe0" + b"\x00" * 64).decode()
PNG_B64 = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"\x00" * 64).decode()
HEADERS = {"X-App-Token": "test-token"}


class IdentifyTests(unittest.TestCase):
    def setUp(self):
        self.http = TestClient(main.app)
        self.messages = main.client.beta.messages
        self.messages.raise_ = None
        self.messages.next = _Response([_Block("thinking"), _Block("text", "A one thousand rupee note.")])

    def post(self, body, headers=HEADERS):
        return self.http.post("/identify", json=body, headers=headers)

    def test_returns_spoken_text_and_skips_thinking_blocks(self):
        r = self.post({"image_base64": JPEG_B64, "mode": "money"})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), {"speech": "A one thousand rupee note."})

    def test_sends_image_mode_prompt_model_and_fallback(self):
        self.post({"image_base64": JPEG_B64, "mode": "display", "question": "what's the total?"})
        kw = self.messages.last_kwargs
        self.assertEqual(kw["model"], "claude-opus-5-5")
        self.assertEqual(kw["fallbacks"], "default")
        self.assertIn("server-side-fallback-2026-07-01", kw["betas"])
        content = kw["messages"][0]["content"]
        self.assertEqual(content[0]["source"]["data"], JPEG_B64)
        self.assertEqual(content[0]["source"]["media_type"], "image/jpeg")
        self.assertIn("electronic display", content[1]["text"])
        self.assertIn("what's the total?", content[1]["text"])

    def test_wrong_or_missing_token_is_rejected_before_calling_claude(self):
        self.messages.last_kwargs = None
        self.assertEqual(self.post({"image_base64": JPEG_B64}, headers={"X-App-Token": "nope"}).status_code, 401)
        self.assertEqual(self.post({"image_base64": JPEG_B64}, headers={}).status_code, 401)
        self.assertIsNone(self.messages.last_kwargs)

    def test_unconfigured_token_fails_closed(self):
        old, main.APP_TOKEN = main.APP_TOKEN, ""
        try:
            self.assertEqual(self.post({"image_base64": JPEG_B64}).status_code, 503)
        finally:
            main.APP_TOKEN = old

    def test_rejects_non_jpeg_bad_base64_unknown_mode_and_huge_image(self):
        self.assertEqual(self.post({"image_base64": PNG_B64}).status_code, 400)
        self.assertEqual(self.post({"image_base64": "!!!not-base64!!!"}).status_code, 400)
        self.assertEqual(self.post({"image_base64": JPEG_B64, "mode": "colourblind"}).status_code, 422)
        self.assertEqual(self.post({"image_base64": "A" * (main.MAX_IMAGE_B64_CHARS + 1)}).status_code, 413)

    def test_refusal_becomes_friendly_speech(self):
        self.messages.next = _Response([], stop_reason="refusal")
        r = self.post({"image_base64": JPEG_B64})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["speech"], main.FRIENDLY_REFUSAL)

    def test_empty_answer_becomes_retry_prompt(self):
        self.messages.next = _Response([_Block("thinking")])
        self.assertIn("try again", self.post({"image_base64": JPEG_B64}).json()["speech"])

    def test_api_errors_map_to_http_status(self):
        cases = [
            (fake.RateLimitError("slow down", 429), 429),
            (fake.AuthenticationError("bad key", 401), 503),
            (fake.BadRequestError("bad image", 400), 400),
            (fake.APIStatusError("boom", 500), 502),
            (fake.APIConnectionError("offline"), 502),
        ]
        for exc, status in cases:
            with self.subTest(exc=type(exc).__name__):
                self.messages.raise_ = exc
                self.assertEqual(self.post({"image_base64": JPEG_B64}).status_code, status)

    def test_healthz(self):
        self.assertEqual(self.http.get("/healthz").json(), {"ok": True})


if __name__ == "__main__":
    unittest.main()
