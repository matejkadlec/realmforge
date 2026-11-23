# 0079. Our sounds come from ElevenLabs through its API and ship as committed mono OGG

Date: 2026-11-23

Status: Accepted (amends [0007](0007-asset-pipeline.md): its sounds line)

## Context

[0007](0007-asset-pipeline.md) decided that sounds reuse vanilla's by reference and that new ones come from
ElevenLabs Sound Effects and Freesound CC0, as mono OGG. It did not say how a sound gets from a generator into the
jar. RLM-126 builds that pipeline, under these decisions and facts:

- **Matěj, 2026-11-04 (RLM-126):** sounds start now. M1 and M2 get our own sounds for our mobs and bosses, plus boss
  music from Eleven Music, so the pipeline covers music too. Since [0067](0067-the-mod-is-all-rights-reserved-imported-rooms-included.md)
  the mod and its assets are All Rights Reserved, so the licence rule written for CC BY 4.0 assets is checked again.
- **Matěj, 2026-11-23 (the `flow1 local` run of RLM-126, RLM-445 and RLM-621):** generated WAVs never go into git and
  never live only inside a worktree that gets removed: they stay with their sidecars in one durable folder outside
  the worktrees, and only the picked, converted OGGs are committed. Every generation reads the subscription before
  and after, records the real cost, prints the credits left and the reset date, and a run that would go below a
  reserve is refused. The plan is cancelled and ends at its reset date: never buy credit, upgrade or turn on
  usage-based billing. An agent that cannot hear picks by objective checks (the length fits the moment, no clipping,
  trimmed silence, a loudness target), and Matěj listens later and swaps picks.
- **No MCP route covers sound effects for agents** (research notes, section 5): the hosted ElevenLabs MCP server has
  none, and the local one is deprecated.
- **Measured on the Starter plan (2026-11-23):** 48 kHz WAV is allowed (16-bit stereo), a sound effect costs 10
  credits per second of requested length with either model (the docs say 40), and every response names its charge in
  a `character-cost` header. The subscription's `character_count` catches up only minutes later.
- **CI regenerates every generated file and fails on a byte of difference** (rule 2 of `CLAUDE.md`). Two Vorbis
  encodes of one WAV differ by their random Ogg serial unless ffmpeg's bitexact flags are set, and another encoder
  build may write other bytes.

## Decision

1. **The generator is the ElevenLabs HTTP API, called directly** by `uv run rf sound gen` (sound effects,
   `/v1/sound-generation`; music, `/v1/music`), with `ELEVENLABS_API_KEY` from the environment (Matěj's Claude Code
   settings), never from the repository, a ticket or a chat. Defaults: `eleven_text_to_sound_v2`, 48 kHz WAV, four
   variants. Generation runs in local sessions only.
2. **Candidates live in the sound store, outside the repository and its worktrees:** `~/realmforge-sounds/`, or the
   folder `REALMFORGE_SOUND_STORE` names. Each candidate is the audio as it came and a YAML sidecar: the prompt and
   parameters, the date, the source, the ticket and sound event it is for, what it cost, and its measurements. The
   store is never committed. A plan file (`rf sound gen --plan`) is resumable: it makes only what its prompts still
   lack, so no session pays for a candidate twice.
3. **Credits are guarded per request.** Each request reads `GET /v1/user/subscription` before and after it, and the
   sidecar and the store's ledger record both counts and the `character-cost` charge (a music response has no such
   header: its charge is the growth of the Music usage in `GET /v1/usage/character-stats` across the request). The credits left are the
   period's limit minus the higher of the subscription's count and the ledger's own floor (a request's count before
   it plus every charge since). A request whose estimate (the ledger's highest rate per second for its kind and
   model, cautious defaults until there is one) would leave fewer than `--reserve` (default 1,000) is refused
   before it is sent. Nothing is generated unless the subscription is an active paid plan (status `active`, a tier
   that is not free or a trial): free-plan output has no commercial licence. Each sidecar records the tier and status
   its candidate was made on. Agents never buy credit, upgrade or enable
   usage-based billing.
4. **A pick ships as a committed mono OGG Vorbis file:** `uv run rf sound pick <candidate>` cuts the silence before
   and after (never a loop's) with short fades, brings the sound to -18 LUFS (a loop to -26), never past a sample
   peak of -1 dBFS, and encodes it with libvorbis at quality 5 through ffmpeg with the bitexact flags and no
   metadata. The ffmpeg build is pinned with the `imageio-ffmpeg` package in `uv.lock`. The file goes to
   `art/sounds/<subject>/<name>.ogg`, its source into `art/sounds/sources.yaml`; it refuses a candidate whose sidecar
   does not record an active paid plan. `rf gen` only copies it to
   `assets/realmforge/sounds/`; a sound event of `content/sounds/` plays it with `file: <subject>/<name>`.
   Validation refuses a file that is not mono Vorbis, has no source, or that no event plays, and an event that names
   a file that does not exist. The loudness targets follow vanilla's own sounds (one-shots about -19 LUFS, ambient
   loops -26 and below).
5. **Matěj reviews in the store's gallery** (`uv run rf sound gallery`: a player per candidate, its score,
   measurements, prompt and cost; buttons copy the command that picks, swaps or rejects it). In a batch whose
   playtest he deferred, an agent picks the best score of `rank.py` (no clipping in any channel, at least 60 % of the
   length asked for and not cut off, little silence before the sound, a level above the noise, a loop's seam). Such
   a pick is provisional, as [0074](0074-an-agent-picks-the-art-of-a-batch-whose-review-matej-deferred.md) says for
   art: its source's `picked_by` says so and names the batch's deferred ticket, which owes his listening.
6. **The licence rule** (after 0067): we ship ElevenLabs output from the paid plan, CC0 sounds, CC BY 4.0 sounds with
   their credit (author and URL in `sources.yaml`), and Stable Audio output under the Stability AI Community License.
   Never ElevenLabs free-plan output, CC BY-NC, CC BY-SA, GPL, Pixabay or Sonniss GDC sounds, and never a copy of a
   vanilla sound (vanilla stays a reference in `sounds.json`).
7. **Music** goes through the same store as candidates (Eleven Music's MP3). Its conversion (stereo, streamed, not
   positional) and wiring come with the ticket that plays it (RLM-622).

## Consequences

- Candidates survive every worktree and session, and the ledger tells the next session what the period has already
  spent. The store is one folder on Matěj's WSL disk with no backup: losing it loses the unpicked candidates, not the
  game's sounds, which are committed.
- CI never encodes audio: the committed OGG is copied byte for byte, so generated files stay identical on every
  machine. A re-pick on another machine can give other bytes, which is a commit like any other art change.
- `uv sync` downloads the pinned ffmpeg (about 68 MB for Linux) on every machine and in CI's content job.
- The gallery plays candidates only where the store is: Matěj opens it from Windows at
  `\\wsl.localhost\<distro>\home\<user>\realmforge-sounds\gallery\index.html`.
- Agents' picks of a deferred batch are in the game before Matěj hears them; a swap is one command from the gallery
  and an `rf gen`.
- **The store's first candidates got their status afterwards** (2026-11-23): the sidecars made before the tools
  recorded `cost.status` (135, made that day on the Starter plan, `limit` 70,000, the period resetting
  2026-12-17) were given `status: active` once, by hand, because the subscription read `starter`, `active` all
  that day. Without it `rf sound pick` would refuse them and a plan would count them as made, so they would have to
  be paid for again. No later sidecar needs this: the tools record the status with every candidate.
