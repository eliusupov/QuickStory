# v84 opcode table generator (ticket 21)

Regenerates `src/main/resources/opcodes/{send,recv}ops-84.properties` and
`docs/work-plan/v84-opcode-diff.md` from the Chronicle20/atlas artefacts in
`D:\games\MSv84\opcodes\`. Read-only against that directory.

```
python generate.py      # writes both properties files + the diff report
python adjudicate.py    # just prints the registry adjudication and its evidence
```

Paths are absolute in `lib.py`; edit `SRC` / `COS` if either tree moves.

## Why it does not trust `provenance`

The ticket's premise was that `provenance: csv-import` marks the rows still carrying v83
values. It does not. atlas's own `task100_summary.md` says 188 v84 rows were reshifted
against the IDB **without** updating provenance, so the field is stale on both the fixed
rows and the broken ones. What actually identifies a stale row is that its v84 opcode still
equals its v83 opcode inside a range the IDB says shifted - a delta=0 island in an otherwise
monotonically rising curve. That test finds `SERVERMESSAGE` (0x44, should be 0x46) on its
own, which is the smoke test the ticket set.

`ida_export_gms_v84.json` carries no opcode fields at all - it is a packet *structure* export
(function -> decode calls). Opcode numbers appear only in free prose inside its `note` and
`calls[].comment` strings. The usable independent evidence is `template_gms_84_1.json`, the
live v84 routing table: 222 writers + 145 handlers, each with an opcode *and* an fname. It
agrees with the registry on 357 of 357 fname matches once fnames shared by several ops are
excluded, and it is the only thing that proves the serverbound `0x3F-0x75` band genuinely
did not shift.
