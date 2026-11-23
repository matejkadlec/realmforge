"""Our sounds' audio (RLM-126, ADR 0079): WAV reading, the measurements, the treatment and the OGG conversion."""

from __future__ import annotations

import math

import pytest

from realmforge_tools.sound import convert
from realmforge_tools.sound.wav import (
    FULL_SCALE,
    Pcm,
    WavError,
    analyse,
    clipped_runs,
    loudness,
    read_wav,
    sound_bounds,
    wav_bytes,
)

RATE = 48000


def tone(seconds: float, amplitude: float, freq: float = 1000.0, rate: int = RATE) -> list[float]:
    return [amplitude * math.sin(2 * math.pi * freq * i / rate) for i in range(round(seconds * rate))]


def silence(seconds: float, rate: int = RATE) -> list[float]:
    return [0.0] * round(seconds * rate)


def stereo_wav(left: list[float], right: list[float], rate: int = RATE) -> bytes:
    """A 16-bit stereo WAV, as ElevenLabs sends its sound effects."""
    import io
    import struct
    import wave

    out = io.BytesIO()
    with wave.open(out, "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        frames = b"".join(
            struct.pack("<hh", round(a * FULL_SCALE), round(b * FULL_SCALE))
            for a, b in zip(left, right, strict=True)
        )
        wav.writeframes(frames)
    return out.getvalue()


def test_a_stereo_wav_is_read_as_the_mean_of_its_channels() -> None:
    pcm = read_wav(stereo_wav([0.5, -0.5, 0.25], [0.5, 0.5, -0.25]))
    assert pcm.rate == RATE
    assert pcm.channels == 2
    assert pcm.samples == pytest.approx((0.5, 0.0, 0.0), abs=1e-4)


def test_a_mono_wav_round_trips_within_one_step() -> None:
    samples = tuple(tone(0.01, 0.5))
    pcm = read_wav(wav_bytes(Pcm(RATE, samples)))
    assert pcm.channels == 1
    assert pcm.samples == pytest.approx(samples, abs=1 / FULL_SCALE)


def test_a_file_that_is_no_wav_is_refused() -> None:
    with pytest.raises(WavError):
        read_wav(b"OggS not a wav")


def test_clipping_counts_runs_of_full_scale_samples_not_a_single_peak() -> None:
    assert clipped_runs([0.0, 1.0, 0.0, -1.0, 0.0]) == 0
    assert clipped_runs([0.0, 1.0, 1.0, 1.0, 1.0, 0.0, -1.0, -1.0, -1.0]) == 2


def test_clipping_in_one_channel_counts_though_the_downmix_hides_it() -> None:
    clipped = [0.0, 1.0, 1.0, 1.0, 0.0]
    pcm = read_wav(stereo_wav(clipped, [0.0] * 5))
    assert clipped_runs(pcm.samples) == 0
    assert analyse(pcm).clipped_runs == 1


def test_the_sound_lies_between_its_first_and_last_sample_above_silence() -> None:
    samples = silence(0.1) + tone(0.2, 0.5) + silence(0.3)
    start, end = sound_bounds(samples)
    assert start / RATE == pytest.approx(0.1, abs=0.001)
    assert end / RATE == pytest.approx(0.3, abs=0.001)
    assert sound_bounds(silence(0.1)) == (0, 0)


def test_a_1_khz_sine_measures_its_bs1770_loudness() -> None:
    # A full-scale 1 kHz sine in one channel is -3.01 LUFS; half amplitude is 6.02 dB lower.
    assert loudness(tone(2.0, 1.0), RATE) == pytest.approx(-3.01, abs=0.1)
    assert loudness(tone(2.0, 0.5), RATE) == pytest.approx(-9.03, abs=0.1)
    # The same at 44.1 kHz: the filters follow the rate.
    assert loudness(tone(2.0, 0.5, rate=44100), 44100) == pytest.approx(-9.03, abs=0.1)


def test_gating_leaves_the_silence_out_of_the_loudness() -> None:
    # Ungated, three seconds of silence after one of tone would make it 6 dB quieter; the gates drop the silent
    # blocks, and only the blocks across the tone's end pull it down a little.
    loud = loudness(tone(1.0, 0.5), RATE)
    assert loudness(tone(1.0, 0.5) + silence(3.0), RATE) == pytest.approx(loud, abs=1.0)


def test_a_sound_shorter_than_one_block_still_has_a_loudness() -> None:
    assert loudness(tone(0.1, 0.5), RATE) == pytest.approx(-9.03, abs=0.3)


def test_the_analysis_measures_length_silence_peak_and_a_cut_off_end() -> None:
    a = analyse(Pcm(RATE, tuple(silence(0.2) + tone(0.5, 0.5) + silence(0.3))))
    assert a.seconds == pytest.approx(1.0)
    assert a.sound_seconds == pytest.approx(0.5, abs=0.002)
    assert a.lead_seconds == pytest.approx(0.2, abs=0.002)
    assert a.tail_seconds == pytest.approx(0.3, abs=0.002)
    assert a.peak_dbfs == pytest.approx(-6.02, abs=0.01)
    assert a.end_db < -100
    cut = analyse(Pcm(RATE, tuple(tone(0.5, 0.5))))
    assert cut.end_db == pytest.approx(-3.01, abs=0.1)


def test_treatment_cuts_the_silence_fades_the_ends_and_reaches_the_loudness_target() -> None:
    pcm = Pcm(RATE, tuple(silence(0.3) + tone(0.6, 0.1) + silence(0.5)))
    treated, report = convert.treat(pcm, convert.Treatment(loudness=-18.0))
    assert report.cut_before == pytest.approx(0.3 - convert.PRE_ROLL, abs=0.002)
    assert report.cut_after == pytest.approx(0.5 - convert.POST_ROLL, abs=0.002)
    assert treated.samples[0] == 0.0
    assert abs(treated.samples[-1]) < 1e-9
    assert report.loudness_lufs == pytest.approx(-18.0, abs=0.05)
    assert not report.limited_by_peak


def test_a_peaky_sound_stops_at_the_ceiling_before_its_loudness_target() -> None:
    click = silence(0.05) + tone(0.01, 0.2) + silence(0.5)
    _, report = convert.treat(Pcm(RATE, tuple(click)), convert.Treatment(loudness=-6.0))
    assert report.limited_by_peak
    assert report.peak_dbfs == pytest.approx(convert.CEILING_DBFS, abs=0.01)
    assert report.loudness_lufs < -6.0


def test_a_loop_keeps_every_sample() -> None:
    pcm = Pcm(RATE, tuple(silence(0.2) + tone(0.5, 0.1) + silence(0.2)))
    treated, report = convert.treat(pcm, convert.Treatment.for_loop())
    assert len(treated.samples) == len(pcm.samples)
    assert (report.cut_before, report.cut_after) == (0.0, 0.0)
    assert report.loudness_lufs == pytest.approx(convert.LOOP_LOUDNESS, abs=0.05)


def test_the_conversion_is_mono_vorbis_and_byte_identical_on_every_run() -> None:
    pcm = Pcm(RATE, tuple(silence(0.05) + tone(0.4, 0.3, freq=660) + silence(0.2)))
    treated, _ = convert.treat(pcm, convert.Treatment())
    first = convert.encode(treated)
    second = convert.encode(treated)
    assert first == second
    info = convert.ogg_info(first)
    assert (info.codec, info.channels, info.rate) == ("vorbis", 1, RATE)
    assert info.seconds == pytest.approx(len(treated.samples) / RATE, abs=0.01)
    # No metadata and no version: the comment header names the encoder without its build.
    assert b"encoder=Lavc libvorbis\x01" in first
    assert b"Lavf" not in first


def test_an_ogg_decodes_back_to_the_sound() -> None:
    pcm = Pcm(RATE, tuple(tone(0.3, 0.3, freq=440)))
    back = convert.decode(convert.encode(pcm))
    assert back.seconds == pytest.approx(0.3, abs=0.01)
    assert analyse(back).peak_dbfs == pytest.approx(analyse(pcm).peak_dbfs, abs=0.5)


def test_a_file_that_is_no_ogg_has_no_ogg_info() -> None:
    with pytest.raises(ValueError, match="not an Ogg stream"):
        convert.ogg_info(b"RIFF....WAVE")
