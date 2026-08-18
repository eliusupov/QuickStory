# 69 - RESEARCH: the two rows that cannot be settled without disassembling the v84 client

**Class:** research - **no implementation plan, and none may be written until the missing artifact
exists**
**Work rows:** R22, R23 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** the v84 client binary disassembly. This is the same missing artifact that blocks
tickets 24 and 25. It is not a diff waiting to be written; it is a datum waiting to be read.

Both rows look like small code changes and are not. In each case the code shape to copy is already
identified and the **value** is the thing nobody has. Writing either without the value means
inventing a mapping, which is how a wrong table gets shipped and believed.

## R22 - Evan's Magic Resistance buff is written and never read

* Written at `StatEffect.java:665-667`; the enum is `client/BuffStat.java:105`.
* **No `getBuffedValue` read anywhere** in the tree.
* `TakeDamageHandler.java:72` reads and discards the element byte - the line is literally
  `p.readByte(); //Element`.

**The question to answer:** what is the value-to-element mapping for the element byte the client
sends in `CUser::OnAttacked`?

**What would settle it:** a v84 client disassembly of the `CUser::OnAttacked` sender, showing which
integer the client writes for each element. Nothing in the WZ data answers it. `Mob.wz`'s `elemAttr`
is the mob's own *resistance*, not the element of its attack, and per-attack nodes carry no element
at all - `MobAttackInfoFactory.java:62-67` shows the only flag is `magic`.

**Why no analogue helps:** no elemental-mitigation precedent exists in this codebase.
`ELEMENTAL_RESISTANCE` is declared three times - `DragonKnight.java:28`, `Priest.java:28`,
`BlazeWizard.java:43` - and read nowhere. There is nothing to copy from.

**The shape to copy once the value exists:** `TakeDamageHandler.java:274-277` (Invincible), which
already gates on `is_magic`.

Effort once unblocked: large.

## R23 - the NoticeFailReason byte table is v83 values carried over untested

* `PacketCreator.java:7309-7358`, roughly 50 reason codes.

**The question to answer:** do v84's NoticeFailReason byte values match v83's, and where they differ,
what are they?

**What would settle it:** the client's own reason-code-to-string table, which lives in the binary.

**What has already been checked and does not settle it:** `ida_export_gms_v84.json` mentions
NoticeFailReason **33 times**, but only ever as a decode-op comment marking where a reason byte is
read. It is a packet-**structure** export - `docs/work-plan/tools/v84-opcodes/README.md:24`, and
ticket 21:127 - and carries no value-to-string table. The single numeric datum in it is
`reason == 0x48` in `CITC::OnNormalItemResult#RegisterSaleEntryFailed`.

Consequence if wrong: it changes the wording of a failure notice and nothing else. **This is the
lowest-value row on the whole tracker** and should not outrank anything a player can hit.

Effort once unblocked: medium.

## Acceptance criteria

- [ ] This ticket states, for each row, the exact question and the exact artifact that answers it -
      done above, so the criterion is that nobody re-derives it.
- [ ] `ida_export_gms_v84.json` is confirmed, by a grep the ticket records, to contain no
      value-to-element mapping and no reason-code table. If it turns out to contain either, the row
      leaves this ticket and becomes an implementation ticket the same day.
- [ ] No code is changed under `src/main/java` by this ticket.
- [ ] No mapping, table or element constant is added anywhere on the strength of a guess.
- [ ] The two rows stay flagged as blocked in whatever tracker the orchestrator owns, with the
      artifact named, so they are not re-filed as "small Java fixes".

## Do not

- Do not implement `MAGIC_RESISTANCE` against a guessed element table. A wrong mapping mitigates the
  wrong damage type and looks like it works.
- Do not delete `BuffStat.MAGIC_RESISTANCE` or the write at `StatEffect.java:665-667` as dead code.
  It is Evan's real v84 buff; the reader is missing, not the buff.
- Do not "fix" `TakeDamageHandler.java:72` by assigning the byte to a variable that nothing reads.
- Do not renumber the NoticeFailReason table by pattern-matching v83 against a later version. v84 is
  the only version that answers for v84.
- Do not launch the client, and do not restart the server, to chase either of these.
