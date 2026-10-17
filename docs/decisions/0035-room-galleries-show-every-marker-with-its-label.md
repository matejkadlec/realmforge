# 0035. Room galleries show every marker with its label

Date: 2026-10-17

Status: Accepted

## Context

The room gallery (A4e, [0028](0028-dungeon-rooms-generated-or-imported.md)) renders rooms for Matěj's review. A room's
data markers (architecture 15.3: structure blocks in data mode whose label says `entrance`, `mob`, `chest` or `exit`)
rendered as the structure block's texture, the same "[+]" whatever the label. In the review of the first generated
rooms (A4d, RLM-121, 2026-10-17) Matěj could not tell which spot was which, and an agent told him to ignore the
markers while judging. His answer: that "was not good, those are important parts of the dungeons, and I think it's
not a negative that the gallery renders them but rather a positive so we can improve them". He asked that every
gallery show which label each marker has, as a permanent requirement.

## Decision

1. **Every room gallery shows every data marker with its label**, in the renders and in text:
   - each view draws a marker as a block in its label's colour (`MARKER_STYLES` in
     `tools/realmforge_tools/rooms/render.py`), also above a cutaway's cut, so a marker on a balcony shows in the
     plan;
   - each room's card lists its markers by label with their positions;
   - the page carries a legend: each label's colour and what the dungeon does at its spot, and what the
     connectors are.
2. **Every marker label has a style of its own**, a block and a colour no other label uses. A new marker gets one
   when it is added; a test holds `MARKERS` and `MARKER_STYLES` to the same labels. A structure block that is no
   known marker shows in a colour of its own too, so it is never hidden.
3. **Markers are reviewed, not ignored.** Where a room puts its markers is part of the room: a review judges them
   like the blocks, and agents never ask Matěj to look past them.
4. **Any later gallery follows the same rule:** whatever a reviewed piece carries that the game acts on and that
   does not show as itself (a marker, a trigger, a spawn spot), the gallery shows, labelled.

## Consequences

- The renders no longer show a marker as the block the file holds, but as a stand-in block; the game never places
  either, so nothing a player sees is misrepresented.
- A marker added for W4, W6 or later needs its style in `render.py` in the same change, or the test fails.
- Rooms imported without markers show "No markers" on their card, which makes the gap visible when W6 decides
  where their encounters go.
