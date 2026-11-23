"""The ElevenLabs API, called directly (RLM-126, ADR 0079): sound effects, music and the subscription's credits.

No MCP server covers sound effects for agents (the hosted one has none, the local one is deprecated: research notes,
section 5), so the tools call the HTTP API with ``ELEVENLABS_API_KEY`` from the environment (Matěj's Claude Code
settings, ``docs/dev-setup.md``), never from a file in the repository.

**Credits** (Matěj, 2026-11-23): every request reads ``GET /v1/user/subscription`` before and after, and the store
records the response's ``character-cost`` header, the real charge. The subscription's ``character_count`` lags behind
a request by minutes, so the credits left are the period's limit minus whichever is higher: that count, or the
store's ledger (each request's count before it plus every charge since, ``store.used``). A run that would leave fewer than its reserve is refused before
it spends anything. Never buy credit or turn on usage-based billing: the plan ends at its reset date.
"""

from __future__ import annotations

import json
import math
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import UTC, datetime

API = "https://api.elevenlabs.io"
KEY_ENV = "ELEVENLABS_API_KEY"
SFX_MODEL = "eleven_text_to_sound_v2"
MUSIC_MODEL = "music_v1"
# 48 kHz WAV works on the Starter plan (checked 2026-11-23); ElevenLabs sends it as 16-bit stereo.
SFX_FORMAT = "wav_48000"
MUSIC_FORMAT = "mp3_44100_128"
# What a request is estimated at before the ledger knows better: the docs' 40 credits per second of sound effect
# (2026-11-23 measured 10 per second, so this is cautious), and a sound effect of unknown length at its longest.
DEFAULT_SFX_RATE = 40.0
DEFAULT_MUSIC_RATE_PER_SECOND = 100.0
MAX_SFX_SECONDS = 30.0
RETRIES = 3
TIMEOUT = 300
# The usage stats' product names, for a charge that a response does not carry in its header.
PRODUCTS = {"sfx": "Sound Effects", "music": "Music"}
# How often and how long to wait for the usage stats to show a charge.
USAGE_POLLS = 6
USAGE_WAIT = 3.0
# A paid plan's subscription is active (SubscriptionStatusType: trialing, active, incomplete, past_due, free,
# free_disabled); a free account may still name a tier, so these tier names never count as paid either (ADR 0079).
PAID_STATUS = "active"
UNPAID_TIERS = frozenset({"", "none", "null", "free", "trial"})


class ElevenLabsError(RuntimeError):
    """A request that failed, or no API key."""


class CreditsReserved(ElevenLabsError):
    """A request that would leave fewer credits than the reserve."""


@dataclass(frozen=True)
class Subscription:
    count: int
    limit: int
    reset_unix: int
    tier: str
    status: str

    def reset_date(self) -> str:
        return datetime.fromtimestamp(self.reset_unix, UTC).strftime("%Y-%m-%d %H:%M UTC")


@dataclass(frozen=True)
class Audio:
    """A response's audio and the headers the store records."""

    body: bytes
    content_type: str
    charged: int | None
    request_id: str | None


Opener = Callable[[urllib.request.Request, float], object]


def api_key() -> str:
    key = os.environ.get(KEY_ENV, "").strip()
    if not key:
        raise ElevenLabsError(
            f"{KEY_ENV} is not set: add it to the env block of your Claude Code settings or export it "
            "(docs/dev-setup.md, ElevenLabs)"
        )
    return key


class Client:
    """The few endpoints the pipeline uses. ``opener`` is ``urllib.request.urlopen`` unless a test hands in its
    own."""

    def __init__(self, key: str, opener: Opener | None = None, sleep: Callable[[float], None] = time.sleep):
        self._key = key
        self._open = opener or (lambda request, timeout: urllib.request.urlopen(request, timeout=timeout))
        self._sleep = sleep

    def _request(
        self, method: str, path: str, query: Mapping[str, str] | None = None, body: object = None
    ) -> tuple[bytes, Mapping[str, str]]:
        url = API + path + (("?" + urllib.parse.urlencode(query)) if query else "")
        data = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {"xi-api-key": self._key, "Accept": "*/*"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        for attempt in range(RETRIES + 1):
            request = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                response = self._open(request, TIMEOUT)
                with response as r:  # type: ignore[attr-defined]
                    return r.read(), {k.lower(): v for k, v in r.headers.items()}
            except urllib.error.HTTPError as e:
                detail = e.read().decode("utf-8", "replace")[:500]
                if (e.code == 429 or e.code >= 500) and attempt < RETRIES:
                    self._sleep(2 ** (attempt + 1))
                    continue
                raise ElevenLabsError(f"{method} {path}: HTTP {e.code}: {detail}") from e
            except urllib.error.URLError as e:
                if attempt < RETRIES:
                    self._sleep(2 ** (attempt + 1))
                    continue
                raise ElevenLabsError(f"{method} {path}: {e.reason}") from e
        raise ElevenLabsError(f"{method} {path}: no answer")  # pragma: no cover - the loop returns or raises

    def usage(self, product: str) -> float | None:
        """The credits ``product`` (``Music``, ``Sound Effects``) used in the last two days, from
        ``GET /v1/usage/character-stats``; None when the API gives no answer. A response without a
        ``character-cost`` header (music) is charged as the difference across the request."""
        now = int(time.time())
        query = {
            "start_unix": str((now - 2 * 86400) * 1000),
            "end_unix": str((now + 3600) * 1000),
            "breakdown_type": "product_type",
        }
        try:
            body, _ = self._request("GET", "/v1/usage/character-stats", query)
            series = json.loads(body).get("usage", {}).get(product, [])
            return float(sum(series))
        except (ElevenLabsError, ValueError, TypeError, AttributeError):
            return None

    def subscription(self) -> Subscription:
        body, _ = self._request("GET", "/v1/user/subscription")
        data = json.loads(body)
        return Subscription(
            count=int(data["character_count"]),
            limit=int(data["character_limit"]),
            reset_unix=int(data["next_character_count_reset_unix"]),
            tier=str(data.get("tier") or ""),
            status=str(data.get("status") or ""),
        )

    def _audio(self, path: str, output_format: str, body: dict[str, object]) -> Audio:
        data, headers = self._request("POST", path, {"output_format": output_format}, body)
        charged = headers.get("character-cost")
        return Audio(
            body=data,
            content_type=headers.get("content-type", ""),
            charged=int(charged) if charged and charged.strip().isdigit() else None,
            request_id=headers.get("request-id") or headers.get("song-id"),
        )

    def sound_effect(
        self,
        text: str,
        *,
        duration: float | None,
        loop: bool,
        influence: float | None,
        model: str = SFX_MODEL,
        output_format: str = SFX_FORMAT,
    ) -> Audio:
        body: dict[str, object] = {"text": text, "loop": loop, "model_id": model}
        if duration is not None:
            body["duration_seconds"] = duration
        if influence is not None:
            body["prompt_influence"] = influence
        return self._audio("/v1/sound-generation", output_format, body)

    def music(
        self,
        prompt: str,
        *,
        length_ms: int,
        instrumental: bool,
        model: str = MUSIC_MODEL,
        output_format: str = MUSIC_FORMAT,
    ) -> Audio:
        body: dict[str, object] = {
            "prompt": prompt,
            "music_length_ms": length_ms,
            "force_instrumental": instrumental,
            "model_id": model,
        }
        return self._audio("/v1/music", output_format, body)


def credits_left(subscription: Subscription, used: int) -> int:
    """The period's credits left: its limit minus what is used, at least the subscription's own count (the store's
    ledger knows charges the count does not show yet: ``store.used``)."""
    return subscription.limit - max(subscription.count, used)


def estimate(kind: str, model: str, seconds: float | None, history: list[dict[str, object]]) -> int:
    """What one request is expected to cost, from the ledger's charges for the same kind and model per second of
    requested length (the most expensive rate seen), or the cautious defaults until there are any."""
    rates = [
        float(entry["charged"]) / float(entry["seconds"])  # type: ignore[arg-type]
        for entry in history
        if entry.get("kind") == kind
        and entry.get("model") == model
        and entry.get("charged")
        and entry.get("seconds")
    ]
    if kind == "music":
        rate = max(rates, default=DEFAULT_MUSIC_RATE_PER_SECOND)
        return math.ceil(rate * (seconds or 60.0))
    rate = max(rates, default=DEFAULT_SFX_RATE)
    return math.ceil(rate * (seconds if seconds is not None else MAX_SFX_SECONDS))


def is_paid(tier: str | None, status: str | None) -> bool:
    """Whether audio made under ``tier`` and ``status`` may ship: only an active paid plan's output has the
    commercial licence (ADR 0079). Fails closed: an unknown status, a missing tier or a free one is unpaid. The
    Starter plan ends at its reset date and the account falls back to the free plan."""
    if not tier or not status or status.strip().lower() != PAID_STATUS:
        return False
    name = tier.strip().lower()
    return name not in UNPAID_TIERS and not name.startswith(("free", "trial"))


def check_paid(subscription: Subscription) -> None:
    if not is_paid(subscription.tier, subscription.status):
        raise ElevenLabsError(
            f"the subscription is {subscription.tier or 'no tier'} ({subscription.status or 'no status'}): only an "
            "active paid plan's output has a commercial licence and may ship (ADR 0079), so nothing is generated"
        )


def check_reserve(left: int, cost: int, reserve: int) -> None:
    if left - cost < reserve:
        raise CreditsReserved(
            f"{left} credits left; this request (about {cost}) would leave fewer than the reserve of {reserve}"
        )
