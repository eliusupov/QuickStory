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

Three occurrences in `tools/v84/cutover-server.2026-08-17-0002.log`, all uploaded 2026-08-17T00:01.
Error code 38 is the client's reader running off the end of a buffer, which for a crash triggered by
an attack means a clientbound packet is shorter than the v84 client's decoder expects - a field the
v84 layout added and ours does not write, or a length prefix that disagrees with its body.

This is ticket 45 blocker **B2**. It kills the session, not the save.

Packet visibility does not need a restart: `USE_DEBUG_SHOW_RCVD_PACKET` toggles **live** via the GM5
`!showpackets` command. Check that before anyone schedules one.

The standing constraints apply without exception: **never restart the server, never kill it, never
launch a client.**

## Precedent

The reproduction path is `tools/v84/`: the offline `PacketStructureValidator` and the decode models
in `decode-models-v84.tsv` and `-binary.tsv`, which is how the two earlier v84 combat crashes were
found and fixed (ticket 32-v84-combat-packets: `MOVE_MONSTER` and
`DROP_ITEM_FROM_MAPOBJECT`, both version-gated). Same method here: replay the attack sequence's
clientbound packets against a v84 model and find the one whose model consumes more bytes than the
server writes.

The v84 layout authority is `D:\games\MSv84\opcodes\ida_export_gms_v84.json`, the same export that
settled the cash shop mode bytes and the SET_FIELD `nDurability` field.

`config.yaml` already carries `USE_DEBUG_SHOW_PACKET: true` with the comment
`#TEMPORARY - capturing the attack sequence to find the v84 combat crash`, so a capture may already
be accumulating.

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

- Do not restart the server to get packet logging. `!showpackets` is live at GM5.
- Do not launch a client. The two in-game criteria above are recorded for the owner to run, not for
  an agent to attempt.
- Do not treat this as the same defect as ticket 32-v84-combat-packets. Those two crashes are fixed;
  this is a third one and it survived them.
- Do not guess at a fix by widening a packet until the crash stops being reported. The named-opcode
  criterion exists so the change is evidenced before it ships.
