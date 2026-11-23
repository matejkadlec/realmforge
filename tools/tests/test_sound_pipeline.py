"""The sound pipeline (RLM-126, ADR 0079): the store and its sidecars, the credit guard, plans, the gallery, picks into
art/sounds/ and their validation. ElevenLabs is a fake here: no test spends a credit."""

from __future__ import annotations

import json
import math
from pathlib import Path

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.sound import art, convert, elevenlabs, gallery, rank, runner, store
from realmforge_tools.sound.elevenlabs import Audio, CreditsReserved, Subscription
from realmforge_tools.sound.wav import Pcm, wav_bytes
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

RESET = 1797518118
SOUNDS_JSON = "mod/src/generated/resources/assets/realmforge/sounds.json"
OGG_OUT = "mod/src/generated/resources/assets/realmforge/sounds/test_rock/hurt_1.ogg"


def sample_wav(seconds: float = 0.8, freq: float = 500.0) -> bytes:
    lead = [0.0] * 2400
    sound = [
        0.4 * math.sin(2 * math.pi * freq * i / 48000) * (1 - i / (seconds * 48000))
        for i in range(round(seconds * 48000))
    ]
    return wav_bytes(Pcm(48000, tuple(lead + sound + [0.0] * 4800)))


class FakeClient(elevenlabs.Client):
    """Charges 10 credits a second, and its subscription count lags one request behind, like the real one."""

    def __init__(
        self, limit: int = 70000, used: int = 0, tier: str = "starter", status: str = "active"
    ) -> None:
        super().__init__("test-key")
        self.tier = tier
        self.status = status
        self.limit = limit
        self.charged = used
        self.shown = used
        self.requests: list[dict[str, object]] = []
        self.music_charged = 0

    def usage(self, product: str) -> float | None:
        return float(self.music_charged) if product == "Music" else None

    def music(
        self, prompt, *, length_ms, instrumental, model=elevenlabs.MUSIC_MODEL, output_format="mp3_44100_128"
    ):  # type: ignore[no-untyped-def,override]
        # No character-cost header, like the real endpoint: only the usage stats show the charge.
        cost = math.ceil(12 * length_ms / 1000)
        self.charged += cost
        self.music_charged += cost
        self.requests.append({"text": prompt, "length_ms": length_ms, "model": model})
        return Audio(sample_wav(), "audio/wav", None, "song-1")

    def subscription(self) -> Subscription:
        shown, self.shown = self.shown, self.charged
        return Subscription(
            count=shown, limit=self.limit, reset_unix=RESET, tier=self.tier, status=self.status
        )

    def sound_effect(
        self, text, *, duration, loop, influence, model=elevenlabs.SFX_MODEL, output_format="wav_48000"
    ):  # type: ignore[no-untyped-def,override]
        cost = math.ceil(10 * (duration or 1.0))
        self.charged += cost
        self.requests.append({"text": text, "duration": duration, "loop": loop, "model": model})
        return Audio(sample_wav(), "audio/wav", cost, f"req-{len(self.requests)}")


def spec(**overrides: object) -> runner.Spec:
    fields: dict[str, object] = {
        "subject": "frostbolt",
        "event": "impact",
        "prompt": "ice shattering",
        "ticket": "RLM-445",
        "sound_event": "realmforge:spell.frostbolt.impact",
        "duration": 1.0,
        "variants": 3,
    }
    fields.update(overrides)
    return runner.Spec(**fields)  # type: ignore[arg-type]


def test_the_store_is_home_unless_the_environment_names_another(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    monkeypatch.delenv(store.STORE_ENV, raising=False)
    assert store.store_root() == Path.home() / "realmforge-sounds"
    monkeypatch.setenv(store.STORE_ENV, str(tmp_path / "elsewhere"))
    assert store.store_root() == tmp_path / "elsewhere"


def test_a_run_writes_each_candidate_with_its_sidecar_and_ledger_line(tmp_path: Path) -> None:
    client = FakeClient()
    made = runner.run(spec(), tmp_path, client, reserve=1000, say=lambda _: None)
    assert [c.id for c in made] == ["frostbolt/impact/01", "frostbolt/impact/02", "frostbolt/impact/03"]
    back = store.load(tmp_path, "frostbolt/impact/02")
    assert back == made[1]
    assert back.ticket == "RLM-445"
    assert back.sound_event == "realmforge:spell.frostbolt.impact"
    assert back.prompt == "ice shattering"
    assert back.source == "elevenlabs"
    assert back.cost.charged == 10
    assert back.measured is not None and back.measured.lead_seconds == pytest.approx(0.05, abs=0.002)
    assert store.audio_path(tmp_path, back).read_bytes()[:4] == b"RIFF"
    assert (tmp_path / "README.md").is_file()
    assert [entry["charged"] for entry in store.ledger(tmp_path)] == [10, 10, 10]
    assert store.next_number(tmp_path, "frostbolt", "impact") == "04"


def test_the_credits_used_count_charges_the_lagging_subscription_does_not_show_yet(tmp_path: Path) -> None:
    client = FakeClient(used=5)
    runner.run(spec(), tmp_path, client, reserve=0, say=lambda _: None)
    # Each request saw the count before the one ahead of it; the ledger's floor is the first count plus every charge.
    assert store.used(tmp_path, RESET, 5) == 35
    assert store.used(tmp_path, RESET, 50) == 50


def test_a_request_that_would_go_below_the_reserve_is_refused_before_it_is_made(tmp_path: Path) -> None:
    client = FakeClient(limit=1030)
    with pytest.raises(CreditsReserved, match="reserve of 1000"):
        runner.run(spec(duration=1.0), tmp_path, client, reserve=1000, say=lambda _: None)
    # The default estimate is the docs' 40 credits a second: nothing was requested.
    assert client.requests == []
    assert store.candidates(tmp_path) == []


@pytest.mark.parametrize(
    ("tier", "status"),
    [
        ("free", "free"),
        ("trial", "free"),
        ("starter", "free_disabled"),
        ("starter", "past_due"),
        ("", "active"),
    ],
)
def test_nothing_is_generated_unless_a_paid_plan_is_active(tmp_path: Path, tier: str, status: str) -> None:
    client = FakeClient(tier=tier, status=status)
    with pytest.raises(elevenlabs.ElevenLabsError, match="only an active paid plan"):
        runner.run(spec(), tmp_path, client, reserve=0, say=lambda _: None)
    assert client.requests == []


def test_a_candidate_is_picked_only_when_it_was_made_on_an_active_paid_plan(
    repo: RepoPaths, tmp_path: Path
) -> None:
    made = runner.run(spec(variants=1), tmp_path, FakeClient(), reserve=0, say=lambda _: None)[0]
    assert (made.cost.tier, made.cost.status) == ("starter", "active")
    for tier, status in (("free", "active"), ("trial", "free"), ("starter", None), (None, "active")):
        unpaid = made.model_copy(
            update={"cost": made.cost.model_copy(update={"tier": tier, "status": status})}
        )
        with pytest.raises(art.SoundsError, match="only an active paid plan"):
            art.pick(repo, tmp_path, unpaid, picked_by="Matěj")


def test_music_is_charged_as_the_usage_stats_grew_across_its_request(tmp_path: Path) -> None:
    made = runner.run(
        spec(subject="astone", event="music", kind="music", duration=10.0, variants=1),
        tmp_path,
        FakeClient(),
        reserve=0,
        say=lambda _: None,
        sleep=lambda _: None,
    )[0]
    assert made.kind == "music"
    assert made.music_length_ms == 10000
    assert made.cost.charged == 120
    assert store.ledger(tmp_path)[0]["charged"] == 120


def test_the_estimate_learns_the_real_rate_from_the_ledger(tmp_path: Path) -> None:
    assert elevenlabs.estimate("sfx", elevenlabs.SFX_MODEL, 2.0, []) == 80
    runner.run(spec(variants=1), tmp_path, FakeClient(), reserve=0, say=lambda _: None)
    assert elevenlabs.estimate("sfx", elevenlabs.SFX_MODEL, 2.0, store.ledger(tmp_path)) == 20
    assert elevenlabs.estimate("sfx", "eleven_text_to_sound_v3", 2.0, store.ledger(tmp_path)) == 80


def test_a_plan_is_resumable_and_makes_only_what_its_prompts_lack(tmp_path: Path) -> None:
    plan = write(
        tmp_path / "plan.yaml",
        "ticket: RLM-445\nentries:\n"
        "  - {subject: frostbolt, event: impact, prompt: ice shattering, duration: 1.0, variants: 2}\n"
        "  - {subject: frostbolt, event: form, prompt: ice forming, duration: 1.5, variants: 2, ticket: RLM-590}\n",
    )
    root = tmp_path / "store"
    client = FakeClient()
    specs = runner.load_plan(plan)
    assert [s.ticket for s in specs] == ["RLM-445", "RLM-590"]
    runner.run_plan(specs, root, client, reserve=0, say=lambda _: None)
    assert len(client.requests) == 4
    runner.run_plan(specs, root, client, reserve=0, say=lambda _: None)
    assert len(client.requests) == 4
    said: list[str] = []
    runner.run_plan(specs, root, client, reserve=0, dry_run=True, say=said.append)
    assert said[0] == "frostbolt/impact: 2 made, 0 to make (about 0 credits)"


def test_a_spec_refuses_what_the_api_would() -> None:
    with pytest.raises(ValueError, match=r"0\.5 to 30"):
        spec(duration=31.0)
    with pytest.raises(ValueError, match="RLM-<n>"):
        spec(ticket="445")
    with pytest.raises(ValueError, match="snake_case"):
        spec(event="Impact")
    with pytest.raises(ValueError, match="music needs a duration"):
        spec(kind="music", duration=None)


def test_no_key_says_where_it_comes_from(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv(elevenlabs.KEY_ENV, raising=False)
    with pytest.raises(elevenlabs.ElevenLabsError, match="env block of your Claude Code settings"):
        elevenlabs.api_key()


def test_a_candidate_scores_lower_for_clipping_a_cut_off_end_or_a_late_start(tmp_path: Path) -> None:
    good = runner.run(spec(variants=1), tmp_path, FakeClient(), reserve=0, say=lambda _: None)[0]
    assert good.measured is not None
    assert rank.score(good).value == 100
    clipped = good.model_copy(update={"measured": good.measured.model_copy(update={"clipped_runs": 2})})
    cut = good.model_copy(update={"measured": good.measured.model_copy(update={"end_db": -6.0})})
    late = good.model_copy(update={"measured": good.measured.model_copy(update={"lead_seconds": 0.4})})
    short = good.model_copy(update={"measured": good.measured.model_copy(update={"sound_seconds": 0.3})})
    assert rank.score(clipped).reasons == ("clips (2 runs)",)
    assert rank.score(cut).reasons == ("cut off at the end (-6 dB)",)
    assert rank.score(late).reasons == ("starts after 0.40 s",)
    assert rank.score(short).reasons == ("0.30 s of sound for 1 s",)
    assert [c.id for c, _ in rank.ranked([clipped, good])] == [good.id, clipped.id]


ROCK = """id: test_rock
category: entity
events:
  hurt:
    subtitle: {en_us: Rock cracks}
    sounds:
      - {file: test_rock/hurt_1, weight: 2}
      - {event: minecraft:block.deepslate.break, pitch: 0.8}
"""


def picked(repo: RepoPaths, tmp_path: Path) -> tuple[str, store.Candidate]:
    root = tmp_path / "store"
    candidate = runner.run(
        spec(subject="test_rock", event="hurt", sound_event="realmforge:entity.test_rock.hurt", variants=1),
        root,
        FakeClient(),
        reserve=0,
        say=lambda _: None,
    )[0]
    name, _ = art.pick(repo, root, candidate, picked_by="Claude (provisional, RLM-999)")
    return name, candidate


def test_a_pick_becomes_a_mono_ogg_in_art_sounds_with_its_source(repo: RepoPaths, tmp_path: Path) -> None:
    name, candidate = picked(repo, tmp_path)
    assert name == "test_rock/hurt_1"
    ogg = (repo.root / "art/sounds/test_rock/hurt_1.ogg").read_bytes()
    info = convert.ogg_info(ogg)
    assert (info.codec, info.channels) == ("vorbis", 1)
    source = art.read_sources(repo)[name]
    assert source.candidate == candidate.id
    assert source.prompt == candidate.prompt
    assert source.license == "elevenlabs-paid"
    assert source.picked_by == "Claude (provisional, RLM-999)"
    assert source.conversion is not None and source.conversion["loudness_target"] == convert.SFX_LOUDNESS
    assert art.next_name(repo, "test_rock", "hurt") == "hurt_2"
    with pytest.raises(art.SoundsError, match="--replace"):
        art.pick(repo, tmp_path / "store", candidate, name="hurt_1", picked_by="Matěj")


def test_an_event_plays_our_file_and_rf_gen_ships_it(repo: RepoPaths, tmp_path: Path) -> None:
    picked(repo, tmp_path)
    write(repo.content / "sounds" / "test_rock.yaml", ROCK)
    content, _ = load_and_validate(repo)
    files = {repo.rel(path): data for path, data in generate_all(content, repo).items()}
    entry = json.loads(files[SOUNDS_JSON])["entity.test_rock.hurt"]
    assert entry["sounds"] == [
        {"name": "realmforge:test_rock/hurt_1", "weight": 2},
        {"name": "minecraft:block.deepslate.break", "type": "event", "pitch": 0.8},
    ]
    assert files[OGG_OUT] == (repo.root / "art/sounds/test_rock/hurt_1.ogg").read_bytes()


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def test_a_file_no_event_plays_and_an_event_without_its_file_are_problems(
    repo: RepoPaths, tmp_path: Path
) -> None:
    picked(repo, tmp_path)
    assert any("no sound event plays it" in p for p in problems_for(repo))
    (repo.root / "art/sounds/test_rock/hurt_1.ogg").unlink()
    write(repo.content / "sounds" / "test_rock.yaml", ROCK)
    found = problems_for(repo)
    assert any("test_rock/hurt_1: no file art/sounds/test_rock/hurt_1.ogg" in p for p in found)
    assert any("art/sounds/test_rock/hurt_1.ogg does not exist" in p for p in found)


def test_a_file_without_a_source_or_in_stereo_is_a_problem(repo: RepoPaths, tmp_path: Path) -> None:
    picked(repo, tmp_path)
    write(repo.content / "sounds" / "test_rock.yaml", ROCK)
    (repo.root / "art/sounds/sources.yaml").write_text("{}\n", encoding="utf-8")
    assert any("no entry in art/sounds/sources.yaml" in p for p in problems_for(repo))


def test_the_licence_rule_wants_credits_for_cc_by_and_refuses_unknown_licences() -> None:
    with pytest.raises(ValueError, match="author and URL"):
        art.SourceDef(
            source="freesound", license="cc-by-4.0", url="https://freesound.org/s/1", picked_by="Matěj"
        )
    with pytest.raises(ValueError):
        art.SourceDef(source="freesound", license="cc-by-nc-4.0", picked_by="Matěj")  # type: ignore[arg-type]
    with pytest.raises(ValueError, match="paid plan"):
        art.SourceDef(
            source="elevenlabs", license="cc0", candidate="a/b/01", prompt="x", model="m", picked_by="Matěj"
        )
    art.SourceDef(
        source="freesound",
        license="cc-by-4.0",
        author="someone",
        url="https://freesound.org/s/1",
        picked_by="Matěj",
    )


def test_an_event_names_a_vanilla_event_or_a_file_never_both(repo: RepoPaths) -> None:
    write(
        repo.content / "sounds" / "test_rock.yaml",
        ROCK.replace(
            "{file: test_rock/hurt_1, weight: 2}",
            "{file: test_rock/hurt_1, event: minecraft:block.stone.break}",
        ),
    )
    assert any("give event or file" in p for p in problems_for(repo))


def test_a_vanilla_event_keeps_its_own_weight_and_reach(repo: RepoPaths) -> None:
    write(
        repo.content / "sounds" / "test_rock.yaml",
        ROCK.replace(
            "{event: minecraft:block.deepslate.break, pitch: 0.8}",
            "{event: minecraft:block.deepslate.break, weight: 3}",
        ),
    )
    assert any("keeps its own weight" in p for p in problems_for(repo))


def test_the_gallery_plays_every_candidate_and_marks_the_pick(repo: RepoPaths, tmp_path: Path) -> None:
    picked(repo, tmp_path)
    root = tmp_path / "store"
    page = gallery.write(root, art.read_sources(repo), "credits: test")
    text = page.read_text(encoding="utf-8")
    assert page == root / "gallery" / "index.html"
    assert '<audio controls preload="none" src="../candidates/test_rock/hurt/01.wav">' in text
    assert "provisional pick: art/sounds/test_rock/hurt_1.ogg" in text
    assert "uv run rf sound pick test_rock/hurt/01 --name hurt_1 --replace --by Matěj" in text
    assert "RLM-445" in text and "ice shattering" in text


def test_a_stereo_file_is_a_problem_unless_its_source_says_stereo(repo: RepoPaths, tmp_path: Path) -> None:
    import subprocess

    picked(repo, tmp_path)
    write(repo.content / "sounds" / "test_rock.yaml", ROCK)
    target = repo.root / "art/sounds/test_rock/hurt_1.ogg"
    source = tmp_path / "in.wav"
    source.write_bytes(sample_wav())
    subprocess.run(
        [
            convert.ffmpeg(),
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-ac",
            "2",
            "-c:a",
            "libvorbis",
            str(target),
        ],
        check=True,
    )
    assert any("2 channels: positional sounds are mono" in p for p in problems_for(repo))


class _Response:
    def __init__(self, body: bytes, headers: dict[str, str]) -> None:
        self._body, self.headers = body, headers

    def __enter__(self) -> _Response:
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def read(self) -> bytes:
        return self._body


def test_the_client_retries_a_rate_limit_and_reads_the_charge_from_its_header() -> None:
    import io
    import urllib.error

    calls: list[str] = []

    def opener(request, timeout):  # type: ignore[no-untyped-def]
        calls.append(request.full_url)
        if len(calls) == 1:
            raise urllib.error.HTTPError(request.full_url, 429, "busy", {}, io.BytesIO(b"{}"))  # type: ignore[arg-type]
        assert request.get_header("Xi-api-key") == "test-key"
        assert json.loads(request.data) == {
            "text": "a knock",
            "loop": False,
            "model_id": elevenlabs.SFX_MODEL,
            "duration_seconds": 0.5,
        }
        return _Response(b"RIFF", {"Content-Type": "audio/wav", "character-cost": "5", "request-id": "abc"})

    client = elevenlabs.Client("test-key", opener=opener, sleep=lambda _: None)
    audio = client.sound_effect("a knock", duration=0.5, loop=False, influence=None)
    assert (audio.charged, audio.request_id, audio.content_type) == (5, "abc", "audio/wav")
    assert calls == ["https://api.elevenlabs.io/v1/sound-generation?output_format=wav_48000"] * 2


def test_a_refused_request_says_what_the_api_answered() -> None:
    import io
    import urllib.error

    def opener(request, timeout):  # type: ignore[no-untyped-def]
        raise urllib.error.HTTPError(request.full_url, 422, "bad", {}, io.BytesIO(b'{"detail": "too long"}'))  # type: ignore[arg-type]

    client = elevenlabs.Client("test-key", opener=opener, sleep=lambda _: None)
    with pytest.raises(elevenlabs.ElevenLabsError, match=r"HTTP 422.*too long"):
        client.sound_effect("a knock", duration=0.5, loop=False, influence=None)
