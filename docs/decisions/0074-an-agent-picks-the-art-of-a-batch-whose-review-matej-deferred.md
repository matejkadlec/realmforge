# 0074. An agent picks the art of a batch whose review Matěj deferred

Date: 2026-11-10

Status: Accepted (amends [0023](0023-texture-generator.md) and [0007](0007-asset-pipeline.md): the gallery review
before content uses a texture)

## Context

[0007](0007-asset-pipeline.md) says the owner reviews all visuals, and [0023](0023-texture-generator.md) that every
texture is "reviewed in the gallery (`uv run rf art gallery`) before content uses it": Matěj picks the candidate, and
only then does it go into the game.

On 2026-11-10 Matěj started two marathon runs (`flow1 local` for the art and spell visuals batches, `flow1 cloud`
for the rest of the sprint) and deferred his own part of them, because he has no time to play or review for some
weeks and wants the work to go on. His instructions for the local run:

- "Carry a playtest batch through its handoff, with every automated check, and with the screenshots and
  rendered-client checks you can do yourself. Then don't wait for Matěj: continue as if he answered `qa1`, and carry
  the pull request through `finalize-pr` to its merge."
- "Make any choice the flow would put to Matěj yourself: take the recommended option, and record it in the claim
  comment and in the deferred ticket as an open question."
- "For every such batch, create a ticket 'Playtest (deferred): …' exactly like RLM-634."

The gallery pick is such a choice. The first art batch of that run (A1's first part: the spell icons, two effect
icons and the spellbook's tiles; RLM-588, RLM-591, RLM-609, RLM-642) therefore installed textures an agent picked,
which 0023 and 0007 do not allow as written. His instructions are the decision
([0030](0030-adrs-bind-agents-not-the-owner.md),
[0060](0060-what-counts-as-the-owners-confirmation-of-an-adr-conflicting-change.md)); this ADR records it and its
limits. The playtest side of the same waiver is [0022](0022-playtest-before-the-pull-request.md)'s, recorded per
batch in its deferred ticket, as [0072](0072-a-fourth-coin-the-converter-by-use-and-coin-drops-by-level-and-rank.md)
did for the coins.

## Decision

1. **In a batch whose playtest Matěj deferred, an agent makes the gallery picks.** It generates and cleans the
   candidates as 0023 says, looks at every candidate itself, picks, and installs the pick. Everything else of 0023
   and 0007 stands: the generator, the cleanup to the project palette, no vanilla textures or derivatives.
2. **Only for the batches he deferred:** the batches of the two marathon runs of 2026-11-10, and a later batch only
   when Matěj defers it in his own words. Outside them he picks before content uses a texture, as before. This is
   his waiver for those batches, not a new default.
3. **The picks are provisional until he has reviewed the gallery.** Each such batch records that where the next
   session reads it:
   - `gallery.yaml`'s intro says the picks are an agent's and names the deferred ticket that owes his review;
   - that ticket ("Playtest (deferred): …") lists the picks as his open questions, with the gallery command;
   - a change he asks for is a fix ticket: another candidate cleaned over the file, or a new generation.
4. **"Approved art" names the folder, not his review.** A texture in `art/textures/` is the installed pick, which
   `rf gen` writes into the game. Whether Matěj has reviewed it is what the batch's `gallery.yaml` and its deferred
   ticket say. Until that ticket is Done, agents building on such a texture name it as an agent's pick.

## Consequences

- Art batches no longer wait for Matěj while he is away, and the game shows real textures instead of placeholders
  weeks earlier.
- Textures he has never seen are on `master`. His review may replace any of them, so nothing should be built that
  only fits one pick (a recolour of it, a model shaped to it) before the deferred ticket is Done, unless the batch
  that does so says it in its own deferred ticket.
- The first batch under this ADR is the spell icons' (RLM-588, RLM-591, RLM-609, RLM-642); its owed review is
  RLM-648.
