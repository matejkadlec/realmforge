# 0003. Platform: Minecraft 1.21.1 on NeoForge

Date: 2026-10-11

Status: Accepted

## Context

The platform decides which mods from the owner's list exist, which APIs we get and how well AI agents know them. Comparison in [research notes, section 2](../research-notes.md#2-platform):

- NeoForge 1.21.1 covers almost the whole list (only a few small UI mods are missing), has by far the largest NeoForge modpack community (about 2,150 modpacks on Modrinth; over 1,000 new ones in 2026 vs 34 on 26.1.2) and still gets fixes (21.1.259 released 2026-10-10).
- Its APIs fit an RPG: data components (rarity, affixes, gems on items), data attachments (player stats, class, talents), data-driven enchantments, and the attribute tooltip API (in NeoForge since 21.1.60).
- AI models know 1.21.1 well; the 26.x APIs (March to September 2026) are weakly represented in training data.
- Forge 1.20.1 and Fabric 1.21.1 lack key mods; NeoForge 26.x lacks MineColonies, EMI, Quark, TreeChop and more.

## Decision

Pinned exactly:

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.259 |
| ModDevGradle | 2.0.148 |
| Gradle (wrapper) | 9.2.1 |
| Java | 21 |
| Parchment | 1.21.1 / 2024.11.17 |

- No upgrade of any of these without a new ADR ([0001](0001-record-architecture-decisions.md)).
- Porting to a newer Minecraft version is re-evaluated in 2027 at the earliest; 26.1.2 is the current candidate (ATM11 and FTB Evolution 2 build on it).
- To keep a future port cheap: rendering and GUI code stays in a few isolated packages, and content stays in data ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)).

## Consequences

- A later port will be expensive: the NeoForge porting primers from 1.21.1 to 26.1 total roughly 160,000 words of changes, mostly entity rendering, item models and GUI. Content in YAML ports cheaply (only generators change).
- We give up the unobfuscated Minecraft of 26.1+; Mojang mappings plus Parchment keep sources readable.
- Agents must use 1.21.1 idioms (data components instead of NBT, `ResourceLocation.fromNamespaceAndPath`, payload networking) and verify APIs against the pinned sources.
