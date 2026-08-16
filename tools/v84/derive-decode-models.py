#!/usr/bin/env python3
"""Derive client-decode models for server->client opcodes from the Chronicle20/atlas IDA exports
and write them into the repo, so the Java test harness has a checked-in, reviewable source.

WHY THE MODEL COMES FROM v83, NOT v84
-------------------------------------
gms_v84.json is machine-generated and is NOT usable as a field list:
  * every `comment` is empty - no field names at all;
  * `guard` strings are decompiler locals ("v4 > 50"), not semantics;
  * branches are FLATTENED into one array (CLogin::OnCheckPasswordResult is 25 entries spanning
    both the success and the failure path);
  * `Delegate` entries call unfollowed subroutines that consume an unknown number of bytes
    (CMobPool::OnMobEnterField ends in three of them);
  * it has known spurious and missing reads relative to the real binary.
gms_v83.json is hand-annotated: real field names, semantic guards. So structure comes from v83,
and the genuine v84 deltas are applied on top from V84_DELTAS - each carrying the binary address
it was proven at, matching the `ServerConstants.VERSION >= 84` branches in tools/PacketCreator.java.

WHY MOST OPCODES ARE REJECTED
-----------------------------
A static model can only describe a packet with exactly ONE shape. An entry is rejected if it has
a `guard`, a `Delegate`, a length-unknown `DecodeBuf`/`DecodeBuffer`, or a comment that admits
conditionality in prose ("only if ...", "loop ..."). The atlas exports put a lot of conditionality
in prose with no `guard` key at all, so the prose filter is load-bearing, not belt-and-braces.

Guarded packets can still be modelled if the SERVER pins the discriminator - PacketCreator's
dropItemFromMapObject is always called with a fixed `mod`, so the "nEnterType != 2" branch is
decided server-side. Those are declared in VARIANTS.

OUTPUT: tools/v84/decode-models-v84.tsv
    status \t model_name \t opcode# \t IDA symbol \t name:kind,name:kind,...
kinds: 1/2/4/8 = Decode1/2/4/8, s = DecodeStr (2-byte length + that many bytes), bN = N raw bytes.

`status` is `verified` only for models an author has hand-promoted (PROMOTED below) after reading
the PacketCreator method that emits it and confirming it is straight-line. Everything else is
`candidate`. The Java harness loads ONLY `verified` rows.

Usage:  python tools/v84/derive-decode-models.py <path-to-atlas-repo> [-o out.tsv]
"""
import argparse
import json
import pathlib
import re
import sys

FLAT_OPS = {"Decode1": "1", "Decode2": "2", "Decode4": "4", "Decode8": "8", "DecodeStr": "s"}

# Comment prose that admits conditionality the `guard` key does not record.
PROSE_CONDITIONAL = re.compile(
    r"only if|only when|only for|gated|loop |representative arm|switch arm|if >|when .*!=|non-ammo",
    re.I)

# Packets whose branch is decided by the SERVER, so a fixed call site has one shape.
# model_name -> (opcode_name, {guard_string: keep?})
VARIANTS = {
    # PacketCreator.dropItemFromMapObject(..., mod, ...) - mod is the nEnterType discriminator and
    # every call site passes a constant. Item drops carry the 8-byte cashItemSN, meso drops do not.
    "DROP_ITEM_FROM_MAPOBJECT/spawn-item": ("DROP_ITEM_FROM_MAPOBJECT",
                                            {"nEnterType != 2": True, "isMoney == 0": True}),
    "DROP_ITEM_FROM_MAPOBJECT/spawn-meso": ("DROP_ITEM_FROM_MAPOBJECT",
                                            {"nEnterType != 2": True, "isMoney == 0": False}),
    # PacketCreator.updateMapItemObject writes a hard-coded mod of 2.
    "DROP_ITEM_FROM_MAPOBJECT/update-item": ("DROP_ITEM_FROM_MAPOBJECT",
                                             {"nEnterType != 2": False, "isMoney == 0": True}),
    # PacketCreator.killMonster only ever sends destroyType 0/1/2 - never 4 (swallowed).
    "KILL_MONSTER/normal": ("KILL_MONSTER", {"destroyType == 4": False}),
    # PacketCreator.removeItemFromMap picks the animation constant at the call site.
    "REMOVE_ITEM_FROM_MAP/picked-up": ("REMOVE_ITEM_FROM_MAP",
                                       {"destroyType in (2,3,5)": True, "destroyType == 4": False}),
    "REMOVE_ITEM_FROM_MAP/expired": ("REMOVE_ITEM_FROM_MAP",
                                     {"destroyType in (2,3,5)": False, "destroyType == 4": False}),
    # PacketCreator.skillBookResult always takes the populated branch.
    "SKILL_LEARN_ITEM_RESULT/result": ("SKILL_LEARN_ITEM_RESULT", {"v36": True}),
}

# Genuine v84 structure changes, applied after guard resolution. Keyed by model name.
# ("append", name, kind) or (index, name, kind) to insert.
V84_DELTAS = {
    # v84 CDropPool::OnDropEnterField ends with TWO unconditional Decode1 where v83 ends with one.
    # Proven from the live v84 process image: v83 localhome.exe 0x506385 has one trailing
    # call 0x4065F3 (Decode1); v84 has call 0x4066C9 at 0x50F20C AND again at 0x50F21D, straight
    # line, no branch between. This is the bug that killed the client on every monster drop.
    # PacketCreator.writeV84DropSpawnExtra. Ticket 30/32.
    "DROP_ITEM_FROM_MAPOBJECT/spawn-item": [("append", "v84 drop spawn effect", "1")],
    "DROP_ITEM_FROM_MAPOBJECT/spawn-meso": [("append", "v84 drop spawn effect", "1")],
    "DROP_ITEM_FROM_MAPOBJECT/update-item": [("append", "v84 drop spawn effect", "1")],
    # v84 CLogin::OnCheckPasswordResult @0x60d368 reads an 8-byte tail (m_aClientKey) after the
    # PIN/PIC bytes that v83 does not. PacketCreator.getAuthSuccess -> writeLong(0). Ticket 22.
    "LOGIN_STATUS": [("append", "v84 m_aClientKey", "8")],
    # v84 CWvsContext::OnSkillLearnItemResult @0xa6988 decodes Decode1 (bOnExclRequest) BEFORE the
    # character id; v83 starts at Decode4. 15-byte body at v83, 16 at v84+. Ticket 25.
    "SKILL_LEARN_ITEM_RESULT/result": [(0, "v84 bOnExclRequest", "1")],
}

# Models hand-promoted to `verified`. An entry belongs here only when BOTH hold:
#   1. this script derived it cleanly (no guard/Delegate/unknown-length buf/conditional prose), AND
#   2. a human read the PacketCreator method that emits it and confirmed it is straight-line:
#      no `if` that adds or drops a field, no loop, no variable-arity sub-record helper.
# Condition 2 cannot be derived from the exports. Adding a name here without doing (2) is exactly
# how this harness would start lying, so don't.
PROMOTED = {
    "DROP_ITEM_FROM_MAPOBJECT/spawn-item",
    "DROP_ITEM_FROM_MAPOBJECT/spawn-meso",
    "DROP_ITEM_FROM_MAPOBJECT/update-item",
    "KILL_MONSTER/normal",
    "SKILL_LEARN_ITEM_RESULT/result",
    "COOLDOWN",
    "REACTOR_DESTROY",
    "REACTOR_HIT",
    "REACTOR_SPAWN",
    "SPAWN_DOOR",
    "REMOVE_DOOR",
    "SPAWN_NPC",
    "SPAWN_NPC_REQUEST_CONTROLLER",
    "REMOVE_PLAYER_FROM_MAP",
    "SHOW_MONSTER_HP",
    "FACIAL_EXPRESSION",
    "SHOW_CHAIR",
    "MOVE_MONSTER_RESPONSE",
    "SCRIPT_PROGRESS_MESSAGE",
    "LAST_CONNECTED_WORLD",
    "DELETE_CHAR_RESPONSE",
    "CHAR_NAME_RESPONSE",
    "INCUBATOR_RESULT",
    "SPAWN_KITE",
    "REMOVE_KITE",
    "REMOVE_MIST",
    "SHOW_COMBO",
    "DESTROY_HIRED_MERCHANT",
    "MONSTER_BOOK_SET_COVER",
}

# Derived cleanly by this script but deliberately NOT promoted, and why. Kept here so the reason
# is not rediscovered every time someone looks at the candidate list.
#
#   SKILL_EFFECT, CANCEL_SKILL_EFFECT, REMOVE_SPECIAL_MAPOBJECT, SPAWN_PLAYER
#       The gms_v83 entry is a STUB: it starts after the dispatcher-consumed characterId
#       (SKILL_EFFECT, CANCEL_SKILL_EFFECT), omits the summon object id
#       (REMOVE_SPECIAL_MAPOBJECT), or covers only the 4-byte dispatcher prefix and none of the
#       CUser::Init body (SPAWN_PLAYER). The export is inconsistent about this - FACIAL_EXPRESSION
#       and SHOW_CHAIR DO include their dispatcher prefix. Modelling these would report a huge
#       false OVER_SEND against correct packets.
#
#   GENDER_DONE, GUILD_MARK_CHANGED, GUILD_NAME_CHANGED, SHOW_UPGRADE_TOMB_EFFECT
#       Modelled fine, but Cosmic has no PacketCreator method that emits them, so there is
#       nothing for the harness to check. Promote if a writer is ever added.
#
# NARROWER THAN ITS NAME:
#   SPAWN_NPC_REQUEST_CONTROLLER has two shapes, selected by the leading localFlag byte: 21 bytes
#   to assign a controller and 5 bytes to drop one (PacketCreator.removeNPCController writes
#   localFlag 0 and the client stops reading). The v83 export records no guard for this, so the
#   model is the 21-byte shape only. Pinned in the test so it is not mistaken for full coverage.
#
# KNOWN FINDING, kept promoted on purpose:
#   KILL_MONSTER/normal - PacketCreator.killMonster writes the animation byte TWICE (a long-
#   standing OdinMS quirk); the client reads Decode4 + Decode1 and stops. Confirmed identical in
#   the v79/v83/v84/v87/v92/v95 exports. One surplus trailing byte on every mob death. Trailing
#   over-send is harmless to the client, so this is not urgent - but the harness reports it, and
#   the test pins it so that fixing it is a visible change rather than a silent one.

BUF_BYTES = re.compile(r"\((\d+)\s*bytes?\)")


def load_registry(atlas):
    """registry/gms_v84.yaml -> {op_name: (opcode, fname)} for clientbound entries."""
    text = (atlas / "docs/packets/registry/gms_v84.yaml").read_text(encoding="utf-8")
    out = {}
    for block in text.split("- op:")[1:]:
        op = block.splitlines()[0].strip()

        def field(key):
            m = re.search(r"^\s+%s:\s*(.+)$" % key, block, re.M)
            return m.group(1).strip() if m else None

        if field("direction") != "clientbound":
            continue
        opcode, fname = field("opcode"), field("fname")
        if opcode is not None and fname is not None:
            out.setdefault(op, (int(opcode), fname))
    return out


def build_fields(entry, pins=None):
    """[(name, kind)] for the export entry, or (None, reason). `pins` resolves guard strings."""
    calls = entry.get("calls")
    if not calls:
        return None, "no calls (dispatcher-only entry)"
    fields = []
    for i, c in enumerate(calls):
        guard = c.get("guard")
        if guard:
            if pins is None or guard not in pins:
                return None, "conditional (guard: %s)" % guard
            if not pins[guard]:
                continue
        comment = (c.get("comment") or "").strip()
        if PROSE_CONDITIONAL.search(comment) and not (pins and guard):
            return None, "conditional in prose only"
        op = c["op"]
        if op in FLAT_OPS:
            kind = FLAT_OPS[op]
        elif op in ("DecodeBuf", "DecodeBuffer"):
            m = BUF_BYTES.search(comment)
            if not m:
                return None, "%s of unknown length" % op
            kind = "b" + m.group(1)
        else:
            return None, op
        name = re.sub(r"[\t,:]", " ", comment).strip()[:60] or ("f%d" % i)
        fields.append((name, kind))
    return fields, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("atlas", type=pathlib.Path, help="path to the Chronicle20/atlas checkout")
    ap.add_argument("-o", "--out", type=pathlib.Path,
                    default=pathlib.Path(__file__).with_name("decode-models-v84.tsv"))
    args = ap.parse_args()

    v83 = json.loads((args.atlas / "docs/packets/ida-exports/gms_v83.json")
                     .read_text(encoding="utf-8"))["functions"]
    registry = load_registry(args.atlas)

    rows, rejected, no_entry = [], {}, 0
    wanted = [(op, op, None) for op in registry]
    wanted += [(name, op, pins) for name, (op, pins) in VARIANTS.items()]

    for model, op, pins in sorted(wanted):
        if op not in registry:
            rejected[model] = "opcode not in the v84 registry"
            continue
        opcode, fname = registry[op]
        entry = v83.get(fname)
        if entry is None:
            no_entry += 1
            rejected[model] = "no gms_v83 entry for %s" % fname
            continue
        fields, why = build_fields(entry, pins)
        if fields is None:
            rejected[model] = why
            continue
        for where, name, kind in V84_DELTAS.get(model, []):
            fields.insert(len(fields) if where == "append" else where, (name, kind))
        rows.append(("verified" if model in PROMOTED else "candidate", model, opcode, fname,
                     ",".join("%s:%s" % (n, k) for n, k in fields)))

    header = [
        "# Generated by tools/v84/derive-decode-models.py - DO NOT EDIT BY HAND.",
        "# Structure from the hand-annotated gms_v83 IDA export; genuine v84 deltas applied from",
        "# V84_DELTAS in that script. kinds: 1/2/4/8 = Decode1/2/4/8, s = DecodeStr, bN = N bytes.",
        "# Only `verified` rows are loaded by the Java harness - see PROMOTED in the script.",
        "# status\tmodel\topcode\tida_symbol\tfields",
    ]
    args.out.write_text("\n".join(header + ["\t".join(map(str, r)) for r in rows]) + "\n",
                        encoding="utf-8")

    verified = sum(1 for r in rows if r[0] == "verified")
    print("clientbound opcodes in atlas registry gms_v84.yaml : %d" % len(registry))
    print("  no gms_v83 IDA entry to model from               : %d" % no_entry)
    print("  modelled (candidate rows)                        : %d" % len(rows))
    print("  hand-promoted to verified                        : %d" % verified)
    print("  rejected as not statically modellable            : %d" % (len(wanted) - len(rows)))
    reasons = {}
    for why in rejected.values():
        key = re.sub(r"\(guard: .*\)", "(guard)", re.sub(r"for \S+", "for <symbol>", why))
        reasons[key] = reasons.get(key, 0) + 1
    for k, v in sorted(reasons.items(), key=lambda kv: -kv[1]):
        print("      %4d  %s" % (v, k))
    print("wrote %s" % args.out)

    missing = PROMOTED - {r[1] for r in rows}
    if missing:
        print("ERROR: promoted but not derivable: %s" % sorted(missing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
