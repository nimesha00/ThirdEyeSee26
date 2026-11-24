"""Third Eye vision backend.

The Android app sends one camera photo and a mode; this service asks Claude to
identify what is in it and returns one short answer written to be spoken aloud.

The Anthropic API key lives only here (ANTHROPIC_API_KEY on Cloud Run). It must
never be built into the APK, where anyone could extract it.
"""

import base64
import binascii
import hmac
import logging
import os
from typing import Literal, Optional

import anthropic
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

log = logging.getLogger("third-eye-vision")
logging.basicConfig(level=logging.INFO)

MODEL = "claude-opus-5-5"

# Shared secret the app sends in X-App-Token. It ships inside the APK, so it only
# keeps casual traffic out; keep Cloud Run's max-instances and a billing budget
# alert as the real cost guard.
APP_TOKEN = os.environ.get("APP_TOKEN", "")

# Phone photos are downscaled to ~1280px JPEG before upload; 6 MB of base64 is generous.
MAX_IMAGE_B64_CHARS = 6 * 1024 * 1024

Mode = Literal["describe", "money", "display", "text"]

SYSTEM_PROMPT = """You are the eyes of Third Eye, a phone assistant for blind and \
low-vision people in Sri Lanka. The user has just pointed the phone camera at \
something and cannot see it. Your reply is read aloud by text-to-speech.

How to answer:
- Plain spoken English only: no markdown, bullet points, symbols, or emoji.
- Lead with the single most useful fact, then add detail only if it helps. Keep it \
to one to three short sentences unless the task says otherwise.
- Say where things are using clock positions or left, centre, right, and rough \
distance in steps or metres when you can judge it.
- Be honest about uncertainty. If the photo is blurry, too dark, or the subject is \
cut off, say so and tell the user how to fix it, for example "move the phone back \
a little" or "turn on a light".
- Mention hazards first if you see any: steps, kerbs, open drains, traffic, \
wet floors, obstacles at head height.
- Never guess a person's identity. You may describe people (how many, roughly \
where, what they are doing, clothing) but do not name anyone.
"""

MODE_INSTRUCTIONS = {
    "describe": (
        "Identify the main object or objects in front of the user and say what "
        "they are. If it is a product, include the brand and product name if "
        "readable. If it is a scene, describe the layout briefly."
    ),
    "money": (
        "Identify any money in the photo. For Sri Lankan rupee notes and coins, "
        "say the denomination in words, for example 'one thousand rupee note'. "
        "If there are several notes or coins, say each denomination and the "
        "total. If it is foreign currency, name the currency and value. If you "
        "cannot see the value clearly, say so instead of guessing, because the "
        "user may hand this money to someone."
    ),
    "display": (
        "The user is pointing at an electronic display, such as an ATM, bus "
        "board, meter, oven, lift panel, or TV. Read out exactly what the "
        "display shows, in reading order, including numbers and units. Say what "
        "kind of display it is first. If part of it is unreadable, say which part."
    ),
    "text": (
        "Read all the printed or handwritten text in the photo, in natural "
        "reading order. Begin by saying what it is, such as a letter, label, or "
        "sign. You may use more than three sentences here because the user wants "
        "the full text."
    ),
}

FRIENDLY_REFUSAL = "Sorry, I can't describe this one. Please try pointing the camera at something else."

client = anthropic.Anthropic()
app = FastAPI(title="Third Eye vision backend")


class IdentifyRequest(BaseModel):
    image_base64: str = Field(..., description="JPEG photo, base64 encoded, no data: prefix")
    mode: Mode = "describe"
    question: Optional[str] = Field(
        default=None,
        max_length=500,
        description="Optional follow-up the user asked aloud, e.g. 'what colour is it?'",
    )


class IdentifyResponse(BaseModel):
    speech: str


def _check_token(token: Optional[str]) -> None:
    if not APP_TOKEN:
        # Fail closed: an unconfigured service must not spend money for anyone.
        raise HTTPException(status_code=503, detail="Service not configured")
    if token is None or not hmac.compare_digest(token, APP_TOKEN):
        raise HTTPException(status_code=401, detail="Unauthorized")


def _validate_image(image_base64: str) -> None:
    if len(image_base64) > MAX_IMAGE_B64_CHARS:
        raise HTTPException(status_code=413, detail="Image too large")
    try:
        head = base64.b64decode(image_base64[:16], validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="image_base64 is not valid base64")
    if not head.startswith(b"\xff\xd8"):
        raise HTTPException(status_code=400, detail="Image must be a JPEG")


def ask_claude(image_base64: str, mode: str, question: Optional[str]) -> str:
    task = MODE_INSTRUCTIONS[mode]
    if question:
        task += f"\n\nThe user also asked: {question}"

    response = client.beta.messages.create(
        model=MODEL,
        max_tokens=16000,
        system=SYSTEM_PROMPT,
        # Medium keeps answers quick enough for someone waiting with a camera up;
        # it is also this model's default, set explicitly so it doesn't drift.
        output_config={"effort": "medium"},
        # On a safety decline, let the API retry on a suitable fallback model
        # inside the same call instead of leaving the user with silence.
        betas=["server-side-fallback-2026-07-01"],
        fallbacks="default",
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {"type": "base64", "media_type": "image/jpeg", "data": image_base64},
                    },
                    {"type": "text", "text": task},
                ],
            }
        ],
    )

    if response.stop_reason == "refusal":
        log.info("Refused request %s", response._request_id)
        return FRIENDLY_REFUSAL

    text = " ".join(block.text for block in response.content if block.type == "text").strip()
    if not text:
        log.warning("Empty answer, stop_reason=%s request=%s", response.stop_reason, response._request_id)
        return "Sorry, I couldn't work that out. Please try again."
    return text


@app.get("/healthz")
def healthz() -> dict:
    return {"ok": True}


@app.post("/identify", response_model=IdentifyResponse)
def identify(req: IdentifyRequest, x_app_token: Optional[str] = Header(default=None)) -> IdentifyResponse:
    _check_token(x_app_token)
    _validate_image(req.image_base64)

    try:
        speech = ask_claude(req.image_base64, req.mode, req.question)
    except anthropic.BadRequestError as e:
        log.error("Bad request to Claude: %s", e.message)
        raise HTTPException(status_code=400, detail="The photo could not be processed")
    except anthropic.AuthenticationError:
        log.error("Anthropic API key is missing or invalid")
        raise HTTPException(status_code=503, detail="Service not configured")
    except anthropic.RateLimitError:
        raise HTTPException(status_code=429, detail="Busy, try again shortly")
    except anthropic.APIStatusError as e:
        log.error("Claude API error %s: %s", e.status_code, e.message)
        raise HTTPException(status_code=502, detail="Vision service error")
    except anthropic.APIConnectionError:
        log.error("Could not reach the Claude API")
        raise HTTPException(status_code=502, detail="Vision service unreachable")

    return IdentifyResponse(speech=speech)
