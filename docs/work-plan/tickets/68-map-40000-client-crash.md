# 68 - The v84 client crashes when the player attacks on map 40000

**Class:** v84 parity
**Work rows:** R44 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately, but it cannot be CLOSED without a client launch, which
no agent may perform

Attacking a monster on map **40000** crashes the v84 client with a WZ-reader end-of-file exception.
This blocks any playthrough, not just Evan's - 40000 is Maple Road, the fourth hop of the fresh
Explorer walk, and quest 1035 wants a kill there. The evidence is a client-uploaded crash blob, not
a server stack, so the work begins offline against the packet models rather than at a breakpoint.

## R44 - ZException error code 38 on FieldID 40000

    ver(84), CharacterName(uguuh), ..., FieldID(40000),
    ZException (error code : 38 (Reached the end of the file.))

**One** `CLIENT_START_ERROR` upload, at `00:01:38.237` in
`tools/v84/cutover-server.2026-08-17-0002.log`, whose body carries **three identical**
`FieldID(40000)` lines (the same packet also carries four `code : 38` lines with `FieldID(-1)`, which
are pre-login noise and not this defect). Two further copies sit in the untracked
`tools/v84/cutover-server.prev.log`. Both log files are untracked - do not assume they survive a
clean checkout.

Error code 38 is the client's reader running off the end of a buffer, which for a crash triggered by
an attack means a clientbound packet is shorter than the v84 client's decoder expects - a field the
v84 layout added and ours does not write, or a length prefix that disagrees with its body.

This is ticket 45 blocker **B2** (`45-early-game-play-order-verification.md:128`, and the walk step
at `:180`). It kills the session, not the save.

### Whether it is still live is unproven in BOTH directions

Do not open this assuming the crash reproduces, and do not close it assuming it is gone.

* `FieldID(40000)` appears in **zero** of the nine later `tools/v84/cutover-server.2026-08-17-*.log`
  files - but nothing in those logs shows anyone re-entered map 40000 either, so that is absence of
  a test, not evidence of a fix.
* **Four** `PacketCreator` fixes landed *after* the 00:01 crash and are not accounted for here:
  `393127dc6` (SPAWN_DRAGON one byte short), `480d95541`, `652bd34df` (CASHSHOP_OPERATION mode enum),
  `2dcbb4c64`. None is obviously on the attack path - the crashing character `uguuh` was an Explorer,
  not an Evan - but the offline replay must be run against **HEAD**, not against the writer as it
  stood on 2026-08-17.

### Packet capture: the flag you need is off, and it has NO live toggle

This is the first thing to get right, because the ticket previously said the opposite.

* `config.yaml:213` is **`USE_DEBUG_SHOW_PACKET: false`**. **No capture is accumulating.** Its
  comment now reads that the `CLIENT_START_ERROR` decoder reports crashes in plain text and full hex
  "is no longer the way we read them".
* `ShowPacketsCommand.java:37` toggles **only** `USE_DEBUG_SHOW_RCVD_PACKET` - the *receive* side.
  `config.yaml:211` is likewise `false`.
* This crash is in a **clientbound** packet. The flag that would capture it is
  `USE_DEBUG_SHOW_PACKET`, and **nothing toggles it live** - it is read from config at send time and
  there is no GM command for it. Getting that capture requires a restart, which no agent may do.

So the offline replay below is not the convenient path, it is the **only** path available to an
agent. Do not plan around a hex capture appearing.

The standing constraints apply without exception: **never restart the server, never kill it, never
launch a client.**

## Precedent

The reproduction path is the offline validator at
`src/main/java/tools/packetvalidator/PacketStructureValidator.java` (**not** under `tools/v84/` -
only the data lives there), driven by the decode models `tools/v84/decode-models-v84.tsv` and
`tools/v84/decode-models-v84-binary.tsv`. That is how the two earlier v84 combat crashes were found
and fixed (ticket 32-v84-combat-packets: `MOVE_MONSTER` and `DROP_ITEM_FROM_MAPOBJECT`, both
version-gated). Same method here: replay the attack sequence's clientbound packets against a v84
model and find the one whose model consumes more bytes than the server writes.

**This crash survived both of those fixes**, which is why it is a third defect and not a duplicate:
`0f9cee0b1` (MOVE_MONSTER) landed 2026-08-16 22:23 and `ade31567b` (DROP_ITEM_FROM_MAPOBJECT, "the
monster-kill crash") landed 2026-08-16 22:59, both **before** the 2026-08-17 00:01 upload.

The v84 layout authority is `D:\games\MSv84\opcodes\ida_export_gms_v84.json`, the same export that
settled the cash shop mode bytes and the SET_FIELD `nDurability` field.


## Acceptance criteria

Offline, and achievable by an agent:

- [ ] The attack sequence on 40000 is replayed against the `tools/v84/` models and **the specific
      opcode whose model over-consumes is named**, with the byte offset at which the server's
      written packet ends and the model expects more.
- [ ] A packet model in `decode-models-v84.tsv` or `-binary.tsv` reproduces the truncation
      deterministically - it fails on the current writer output and passes on the corrected one.
- [ ] The writer fix is behind a `VERSION >= 84` guard, with the v83 layout still produced below it,
      pinned by a test that asserts both byte lengths.
- [ ] The v84 field's presence and width are cited to `ida_export_gms_v84.json` by function name and
      address in the commit message, not asserted from inference.

Requires a client launch, and therefore is the owner's step, not an agent's:

- [ ] A character kills a mob on map 40000 on the real v84 client with **no crash upload** appearing
      in `tools/v84/cutover-server*.log` afterwards.
- [ ] Quest 1035 "Todd's Hunting Method" - kill 9300018, collect 4031802 - completes end to end on
      40000.

Run any new test class with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`.

## Do not

- Do not restart the server to get packet logging. `!showpackets` (GM5) is live but toggles only
  `USE_DEBUG_SHOW_RCVD_PACKET`, which is the wrong direction for this crash. There is no live toggle
  for the clientbound flag; work offline instead.
- Do not launch a client. The two in-game criteria above are recorded for the owner to run, not for
  an agent to attempt.
- Do not treat this as the same defect as ticket 32-v84-combat-packets. Those two crashes are fixed;
  this is a third one and it survived them.
- Do not guess at a fix by widening a packet until the crash stops being reported. The named-opcode
  criterion exists so the change is evidenced before it ships.

---

## RESOLVED - already fixed by `ade31567b`, and this ticket misread its own evidence

The premise above ("this crash survived `ade31567b`") is wrong. `CLIENT_START_ERROR` is the client's
**cumulative crash-history file, uploaded at client start** - the upload timestamp is not the crash
timestamp. The `00:01:38.237` blob is a 12-entry history containing five stale `ver(83)` 11001
entries, so it plainly predates itself.

**The last map-40000 crash is at 22:44:52, 2026-08-16**, seven minutes *before* the fix:

* `tools/v84/cutover-server.prev.log` - server started `22:43:40`, i.e. on a build older than
  `ade31567b` (`22:59:44`). Its `22:44:22` upload carries **two** `FieldID(40000)` entries.
* `:131` `22:44:52.904` `CLOSE_RANGE_ATTACK` on 40000 -> `:137` `22:44:52.927`
  `DROP_ITEM_FROM_MAPOBJECT [113] (40)` - the 38-byte body -> `22:44:53.541` `Connection reset`,
  client dead. That is the third crash, and the named opcode is `DROP_ITEM_FROM_MAPOBJECT`.
* `tools/v84/cutover-server.2026-08-17-0002.log` - server started `23:03:45`, **after** the fix.
  Its `00:01:38` upload carries **three** `FieldID(40000)` entries: the two old ones plus that
  22:44:52 kill. Nothing crashed at 00:01:38.

**The fixed build then survived the same sequence on the same map**, in that very log:

* `00:01:50.051` `FIELD_EFFECT maplemap/enter/40000` - uguuh is on the map.
* `00:01:54.390` `DROP_ITEM_FROM_MAPOBJECT [113] (41)` - **41 bytes, one more than the 40 that
  killed it**, exactly what `ade31567b` added.
* `00:01:59.305` and `00:02:00.074` `CLOSE_RANGE_ATTACK`, `00:02:00.077` `KILL_MONSTER` - a kill on
  40000, and the session runs on normally to the map change at `00:02:07`.

**And no fourth entry ever appeared.** The history stayed at 12 entries with three 40000 lines across
every later upload (`cutover-server.2026-08-17-1425.log:20`, "12 entries uploaded" at `14:26:42`);
the only entries that accrued afterwards are `evan @ map 100030102` (`1425.log:590`) and an unparsed
`com_error`.

Acceptance criteria 1-4 are already satisfied on the tree: the opcode is named
(`DROP_ITEM_FROM_MAPOBJECT`, body 38 -> 39), the fix is `VERSION >= 84`-guarded in
`PacketCreator.writeV84DropSpawnExtra`, and `src/test/java/tools/DropSpawnPacketTest.java` pins both
byte lengths (`itemDropCarriesTheV84TrailingByte`, `mapItemUpdateCarriesTheV84TrailingByte`). No
further code change exists to make.

**Cannot be settled offline, and remains the owner's step:** a live v84 client killing a mob on
40000 with no new crash entry, and quest 1035 end to end. The logs above are the owner's own past
session and are strong evidence, but not the in-client confirmation the ticket asks for.
