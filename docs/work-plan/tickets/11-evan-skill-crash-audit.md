# 11 — Evan skill crash audit — final skill list locked

**Blocked by:** 10

**Status:** offline audit done — **the premise was wrong and the crash is not in the WZ data.**
The client patch was incomplete: there are **two** Evan gates and 01b patched one. Both are now in
`tools\patch-evan-gate.ps1`. See `## Findings`. Nothing was stripped from `Skill.wz` because
nothing was found to be unusable. Four human steps at the bottom, ordered by information gained.

## What to build

A definitive list of which Evan skills work in the v83 client and which must be cut, with the cut ones stripped from the WZ import.

**This must happen before any skill Java is written.** Some Evan skills reference animation actions that do not exist in v83's client-side string pool, and those cannot be added — the release author states it directly: *"actions are client-sided StringPools, so you can't just add and implement an entire new set of v84 actions."* A skill that crashes the client is not fixable server-side at any effort. Implementing one before testing it is wasted work.

Method: give a test character every Evan skill, fire each one, record whether the client survives. Strip the crashers from your `Skill.wz` import so they never appear in the skill window at all — a skill the player can see but never use is worse than one that was never there.

Output is an input to ticket 12: the 14 skills identified as real work may shrink.

## Acceptance criteria

- [ ] Every Evan skill fired in-client and its result recorded — **human, step 2.** Cut from 58
      launches to one `!maxskill` sweep by the offline work below.
- [x] Crashing skills identified and removed from the WZ import — **zero to remove, proved offline.**
      No Evan skill names a body action the live client lacks, and no Evan skill image uses a WZ
      node name that v83's own `Skill.wz` does not already use. The crasher was the client patch.
- [x] Surviving skill list written down as the authoritative scope for ticket 12 — all **58**, see
      the confidence table.
- [x] Skill window shows only usable skills — nothing to hide; see the ceiling section for the
      three mounts that will cast and do nothing, which is ticket 12's, not a WZ cut.
- [x] The permanent ceiling this imposes on Evan is documented for the player-facing notes

---

## Findings

Offline, 2026-08-16. Nothing was launched; every in-game claim below is marked as the owner's to
confirm. **Nothing was written to `D:\games\MapleStory\`** — the only file changed is
`tools\patch-evan-gate.ps1`, which patches process memory and never disk.

### The headline: this ticket's premise does not hold, and the crash is a client-patch defect

The brief says *"some Evan skills reference animation actions that do not exist in v83's
client-side string pool, and those cannot be added."* Three independent measurements say
otherwise, and a fourth found the real cause.

**1. There is no client-side string pool of action names to be blocked by.** `local.exe` is an
unpacked memory dump of this exact client and its literal pool is plain and searchable — 30 ASCII
and 95 UTF-16 occurrences of `UIWindow.img`, whole paths like
`Effect/BasicEff.img/TransformOnLadder` and `Skill/MobSkill.img/%03d/level/%d/tile`. In that pool,
the number of avatar action names is **zero**: not `fireCircle`, not `magicShield`, and not
`stand1`, `walk1`, `prone`, `swingO1` or any other action the client has drawn since v1. A client
cannot hold a compiled table of names none of which appear in it, so actions are resolved by WZ
node name — which is precisely why adding them to `Character.wz/00002000.img` works.

**2. All 27 actions the Evan skills name are in the live client right now.** Extracted every
`<id>/action/0` from `2001.img` and the ten job images (27 distinct names), then dumped the live
`D:\games\MapleStory\Character.wz` → `00002000.img` (`patchVersion=83`, i.e. the `evan-min` merge
is installed). All 27 present. Ten were in stock v83 already — and stock v83 also ships
`superMagicmissile`, `dragonSpark`, `dragonShield`, `dragonFury`, `dragonAura`, `dragonSkin`,
`dragonFly`, `breathe_prepare`, `icebreathe_prepare`, `infinityExplosion`, `elementalRegistance`,
which no v83 class uses. Nexon's Evan body work was already in the file.

**3. The Evan skill images use no node the v83 client has never parsed.** Walked every child name
at `skill/<id>/*` and `skill/<id>/level/<n>/*` across all 87 images in `wz\Skill.wz\` — the 76 v83
ones and the 11 Evan ones. Evan's vocabulary is **24 skill-level names and 24 level-level names,
and every one of the 48 is already used by a v83 image.** Zero novel names, so there is nothing
here without a parser. `2001.img`'s root is `{info/icon, skill}`, identical in shape to `000.img`,
`1000.img` and `2000.img`. All 58 skills have `name` and `desc` and a full `h1..hN` run in
`String.wz/Skill.img`, and `Skill.img/2001/bookName` = "Beginner Master", so no `MISSING NAME`.

**4. The client has TWO Evan gates. `tools\patch-evan-gate.ps1` patched one.**

| VA | function | on Evan it returns | patched by 01b |
|---|---|---|---|
| `0x0075C776` | `CSkillInfo::GetSkill(nSkillID)` @`0x0075C755` | **NULL** | **no** |
| `0x00761714` | `CSkillInfo::GetSkillLevel(...)` @`0x007616F6` | 0, `*ppEntry` untouched | yes |

Both compute `job = nSkillID/10000` and reject on `job/100 == 22 || job == 2001`.

**Patching only `0x00761714` cannot work, by construction.** Past the gate, `GetSkillLevel` calls
`GetSkill` at `0x007617BA` to fetch the entry, is handed NULL by the *other* gate at
`0x0075C776`, and returns 0 at `0x007617CE` anyway. The only thing the 21 NOPs actually unlock is
the beginner-common block at `0x00761729`–`0x007617A3`, which the client already hardcodes Evan
ids into (`20011009`–`20011011` at `0x01315800`–`0x01315803`, `20011020` at `0x0131580C`) and
which returns **1** unconditionally at `0x0076182A` — while `*ppEntry` stays NULL. So the current
patch reports "you have level 1 of this skill" together with a null skill entry.

That is a live crash shape, and at least one path takes it: `0x008AA04D` calls `GetSkill` and
pushes the result straight into `0x008F25D0`, which dereferences it at
`0x008F2600  mov eax,[ebp+0x10] / push [eax]` with no null check. Sibling call sites `0x008A9455`
and `0x008B5ABE` *do* check; this one does not. `GetSkill` has **85 direct call sites** plus
everything reached through `GetSkillLevel`'s 180.

**Both gates are now in the patcher, GetSkill first** — see `tools\patch-evan-gate.ps1`. The order
is load-bearing and commented there: the loop stops at the first gate that times out, and
GetSkill-alone is inert (real entries, level still 0) while GetSkillLevel-alone is the crash above.
The GetSkill patch is surgical, not a blanket NOP — NOPing that gate wholesale falls *into* its own
`xor eax,eax` and would return NULL for every skill in the game:

```
0075C776  83 F8 16     cmp eax,22      ->  83 F8 16     cmp eax,22
0075C779  74 08        jz  reject      ->  90 90        nop nop
0075C77B  81 FE D1 07  cmp esi,2001    ->  (unchanged, now harmless)
0075C781  75 04        jnz normal      ->  EB 04        jmp normal (0x0075C787)
0075C783  33 C0 EB 2E  return NULL     ->  (unchanged, now dead)
```

`-SelfTest` passes on both gates: pattern matches `local.exe`, VA == offset + ImageBase, equal
lengths, patch is not a no-op, **pattern is unique in the 9.9 MB image**, and — the one bit that is
arithmetic rather than "all 0x90" — that the rewritten `EB 04` lands on `0x0075C787` and not
somewhere that silently disables skills for the whole server.

### So: UI-level or data-level? **Neither. It is binary-level, and it is fixed in the patcher.**

Asked directly of the deliverable: the crash is **not** in any individual skill's data (finding 3),
**not** in a missing animation action (findings 1–2), and **not** in the skill window's Evan layout
(below). It is the unpatched `GetSkill` gate handing NULL entries to code that dereferences them.

That prediction is falsifiable and step 1 falsifies it: **the crash will still reproduce with the
old one-gate patch applied**, and should stop with the two-gate patch.

The skill window itself was checked rather than assumed, because "job 2001 has no layout" was the
obvious alternative hypothesis and it is wrong. `CUISkill::SetSkillRoots` at `0x008AD238` has
compiled-in Evan support:

```
008AD285  call 0x004A8C4F        ; GetSkillRootList(nJob, &roots)
008AD2A6  cmp  eax,22            ; job/100 == 22 ?
008AD2AB  cmp  ecx,2001          ; or job == 2001 ?
008AD2C5  call ZArray::Insert(0) ; -> roots[0] = 2001 for an Evan, (job/1000)*1000 otherwise
```

`GetSkillRootList` at `0x004A8C4F` returns `{}` for job 2001 — `(2001%1000)/100 == 0`, the same
early-out every beginner job takes — so an Evan beginner's book list is exactly `[2001]`, the same
one-root shape as job 0, 1000 and 2000. For job 2218 it derives `2200, 2210…2218` correctly by
arithmetic. `CSkillInfo::GetSkillRoot` at `0x0075C70A` is a plain, ungated map lookup, and
`Skill.wz/2001.img` is merged, so root 2001 resolves. There is no missing UI branch to find.

`UI.wz/UIWindow.img/SkillEx` and `SkillMacroEx` are **dead weight on this client** — the string
`SkillEx` does not occur anywhere in `local.exe`, in either encoding, while `UIWindow.img/Skill/…`
paths do (e.g. `UI/UIWindow.img/Skill/Tab/AranButton/Bt%d` at `0x00B3B690`, referenced from
`0x008AD6E1`). The v83 window has four tab buttons plus a fifth Aran one, and it uses the *old*
`Skill` node. Harmless to leave merged; do not expect it to do anything.

### A second, unrelated compiled-in defect that only job 2001 can trigger — do not stand on it

`sub_004FEEC5` maps an Evan job to a dragon stage: `2200 -> 0`, `2210..2218 -> (job%10)+1`,
**anything else -> -1**. Job 2001 is "anything else". It has two callers:

- `0x004FF3C1` (`sub_004FF3A3`) — `cmp esi,edi(0); jl` and bails. Guarded.
- `0x004FEC24` (`sub_004FEBF8`) — **not guarded.** `shl esi,2; lea ecx,[esi+eax]; mov eax,[ecx]`
  reads `[this+0x110 - 4]`, then `cmp dword ptr [eax-4],0` dereferences whatever came back.

`[this+0x110]` is a ten-entry table and `Skill.wz/Dragon` holds exactly ten images named
`2200`, `2210`…`2218` — the exact domain of `sub_004FEEC5`. This is the dragon renderer, and at
job 2001 its stage index is `-1`.

**It is not reachable today, and one line keeps it that way.** `Character.changeJob():1262` reads
`if (GameConstants.hasSPTable(newJob) && newJob.getId() != 2001)`, so no dragon is ever created for
an Evan beginner and `[pUser+0x1F30]` — which every caller tests for null first — stays null.
**Ticket 14 must not relax that guard**, and nothing should ever send `spawnDragon` for a character
at job 2001. This is recorded here because it is the one genuine, unfixable, job-2001-specific
crash in the binary and it would be very easy to walk into by accident.

### The authoritative surviving-skill list for ticket 12

**All 58. None removed. No `Skill.wz` rows to strip.**

| confidence | count | which | basis |
|---|---:|---|---|
| **proven impossible offline** | **0** | — | no missing action, no unparseable node, no missing name |
| **proven safe offline** | 58 | all of `2001.img` + `2200`/`2210`–`2218.img` | findings 1–3 above |
| **proven in game** | 0 | — | nothing has been fired yet; step 2 |

The 27 actions, all present in the live `00002000.img`:
`alert2 bamboo pyramid magicmissile` (job 2001) · `fireCircle lightingBolt dragonIceBreathe
elementalReset magicFlare magicShield dragonThrust magicBooster slow dragonBreathe killingWing
magicRegistance Earthquake ghostLettering recoveryAura mapleHero illusion flameWheel Awakening
OnixBlessing blaze darkFog soulStone` (jobs 2200–2218).

**Rows to strip from the `Skill.wz` import: none.** Stated as concrete path rows so the answer is
unambiguous: all twelve rows of `docs\wz-baseline\merge-lists\evan-min\Skill.paths.txt` stay, and
so do all 70 `String.wz` and 20 `Character.wz` rows. *"A skill the player can see but never use is
worse than one that was never there"* — after this audit no such skill is known to exist, and
deleting nodes on suspicion would have removed working content.

### The permanent ceiling, for the player-facing notes

1. **The gate patch is per-launch, forever.** `MapleStory.exe` is Themida-compressed; both gates
   exist only in memory. Run `tools\patch-evan-gate.ps1 -Watch` before playing. **An Evan on an
   unpatched client has no skills at all and can crash the client**, which is the failure the owner
   already hit. This is the single largest operational cost of having Evan.
2. **Three mounts cast and produce nothing**: `20011018` Yeti Rider, `20011019` Witch's Broomstick,
   `20011031` Balrog. Named in `String.wz`, no sprite mapping exists and the id offsets do not
   transfer from the other beginner jobs. Ticket 10 recorded this; it is ticket 12's to close or
   accept, not a WZ defect.
3. **`Evan.MONSTER_RIDER` (20011004) is not recognised as a mount** — `isMonsterRidingSkill()`
   tests `sourceid % 10000000 == 1004` and Evan's is `11004`. Ticket 12.
4. **Dragon equips and Mir's saddles show `MISSING NAME`** in the equip window: `evan-min` runs
   with zero `--force`, so `Eqp/Dragon` (12 ids) and `Eqp/Taming/190204x`/`191203x` (6 ids) keep the
   live client's placeholder strings. Cosmetic; fixable later with a narrowed `<id>/name` force root.
5. **An Evan beginner must never be given a dragon** — see the `sub_004FEEC5` section. This is a
   permanent property of the binary, not something a later ticket can engineer away.
6. **28 Evan skill sounds are not installed** (`Sound.wz` is one of the eight files `evan-min`
   leaves out). Skills will be silent until that increment lands.

### What this ticket could not determine

- **Nothing was run in a client.** The two-gate patch is self-tested against `local.exe` and is
  byte-verified on write, but it has not been applied to a live process by this ticket.
- **Which call site actually took down the owner's session.** `GetSkill` has 85 direct callers;
  `0x008AA04D`→`0x008F2600` is a proven unguarded dereference but not proof that it is *the* one.
  The mechanism is established; the exact frame is not, and pinning it needs a crash dump.
- **Whether every one of the 58 skills renders and fires correctly.** Offline work can prove a
  skill is *not* impossible. It cannot prove it is *right*. That is step 2.
- **The three unidentified ids `20019000`–`20019002`** ("Pig's/Stump's/Slime's Weakness", each with
  a `mobCode` node) are named and structurally ordinary, so they survive the audit, but what they
  are supposed to *do* is still ticket 12's to work out.

---

## Human steps — ordered by information gained per launch

Run `tools\patch-evan-gate.ps1 -Watch` in its own window **before** launching, unless a step says
otherwise. Confirm `tools\evan-gate-patch.log` shows **both** `GetSkill` and `GetSkillLevel`
`PATCHED and verified` for the PID that survives.

### Step 1 — the one test that settles it. Two launches, five minutes.

On the Evan at job 2001, open the skill window.

- **1a, old behaviour.** Launch with the patcher **not** running. Open the skill window.
  *Expect: the same crash as before.* If it does **not** crash now, something else changed since
  the owner's session and steps 2–4 are still worth doing but this finding needs re-opening.
- **1b, the fix.** Close, start `patch-evan-gate.ps1 -Watch`, relaunch, open the skill window.
  - **Opens, 27 beginner skills listed with real English names → the diagnosis is confirmed and
    ticket 11 is done.** Go to step 2.
  - **Still crashes → the diagnosis is wrong, or incomplete.** Do not start deleting `Skill.wz`
    nodes; the offline evidence says the data is sound. Report the log, and the next suspect is a
    third gate or a caller this audit did not walk.

### Step 2 — the sweep this ticket exists to make cheap. One launch.

Gate patch running. `!job 2218`, then `!maxskill`, then open the skill window and fire every skill
from the quickslots, working down the list. **Expect all 58 to cast.** Record only the exceptions.

Known non-crash misbehaviour, already owned elsewhere — do not report these as audit failures:
`20011018` / `20011019` / `20011031` cast and produce no mount (ticket 12); `20011004` is not
recognised as a mount at all (ticket 12); skills are silent (`Sound.wz` not installed).

If something crashes here, **that** is the data-level finding this ticket was written to catch, and
its `Skill.wz` row can be stripped with confidence — one row, named, with a reason.

### Step 3 — the job levels the window has to fit. Same launch as step 2.

Open the skill window at `!job 2200`, `!job 2214`, `!job 2218`. The v83 window has four tab buttons
plus a fifth Aran one; Evan needs eleven roots (`2001` + `2200` + `2210`–`2218`). This is where a
genuine UI shortfall would show, and `SkillEx`/`SkillMacroEx` will **not** help — nothing in the
binary reads them. A truncated tab strip is a cosmetic ceiling to write down, not a crash.

### Step 4 — the guard that must not move. One command.

`!job 2001` on a character that is currently `2200` or higher. The dragon must disappear and
**must not come back**. If a dragon is ever visible while the job reads 2001, stop — that is the
`sub_004FEEC5` negative-index path and it will take the client down unpredictably.
