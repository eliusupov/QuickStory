# Ticket 31 — Scripted NPC accept/decline never renders on v84

**Branch** `worktree-evan-dualblade` · **Status** cause found, fix landed, needs one live click to confirm

## Verdict

**Candidate 1**, and it is broader than accept/decline: **v84 shifted the entire NPC dialog-type enum
up by one from index 1**. The client inserted a `SayImage` case at 1 and renumbered everything after
it. `Say` (0) is the only type whose number did not move — which is exactly why `sendNext` and
`sendNextPrev` were the only two dialogues the owner ever saw.

`sendAcceptDecline` sends type **12** (`AskYesNoQuest` in the v83 enum). The v84 client's
`CScriptMan::OnScriptMessage` switch **has no case 12**. The frame is decoded, falls off the end of
the switch, and the client draws nothing and replies nothing. That is the whole symptom: no dialogue,
no third packet, no error anywhere.

Candidates 2, 3 and 4 are **ruled out by measurement**, not by argument — see §3.

## 1. The evidence

atlas IDA exports, `CScriptMan::OnScriptMessage`, per version:

| version | address | enum |
|---|---|---|
| v83 | `0x74660a` | 0=Say, 1=AskYesNo, 2=AskText, 3=AskNumber, 4=AskMenu, 5=AskQuiz, 6=AskSpeedQuiz, 7=AskAvatar, 8=AskMembershopAvatar, 9=AskPet, 10=AskPetAll, **12=AskYesNoQuest**, 13=AskBoxText, 14=AskSlideMenu — **no SayImage case** |
| v84 | `0x76850a` | switch cases **0,1,2,3,4,5,6,7,8,9,10,11,13,14,15** — `1 → sub_768844` (SayImage), `2 → OnAskYesNo`, `13 → OnAskYesNo`, `14 → OnAskBoxText`, `15 → sub_769B26` (SlideMenu). **No case 12.** |
| v87 | `0x791666` | annotator's own words: *"UNLIKE v83 (which had NO SayImage case), v87's switch HAS a SayImage case (case 1 → OnSayImage), matching v95"* — 0=Say, 1=SayImage, 2=AskYesNo … **13=AskYesNoQuest**, 14=AskBoxText, 15=AskSlideMenu |
| v95 | `0x6de0f0` | identical to v87 |

So `AskYesNoQuest` is **12 at v83 and 13 from v84 on**.

### Why this is not one of ticket 25's 183 artifacts

Ticket 25's artifact tell is *"it vanishes at v87 and v95."* This one does the opposite — it is
**present and hand-annotated at both v87 and v95**, and the v87 note calls out the v83 difference by
name without being asked. v84 sits inside a bracket whose two annotated ends agree with each other
and disagree with v83. The v84 raw export is only being used to *locate* the change inside the
bracket, and it does that unusually well: the coarse analyzer dumped the switch as fifteen explicit
`Delegate` entries with `v6 == N` guards, which is the exact datum needed.

### Proving the instrument before believing it

Cosmic is a working v83 encoder. Every dialog type it sends must therefore match atlas's v83
annotation, and it does — seven for seven:

| Cosmic call | byte sent | atlas v83 enum | agree |
|---|---|---|---|
| `sendNext`/`sendPrev`/`sendOk` | 0 | Say | yes |
| `sendYesNo` | 1 | AskYesNo | yes |
| `getNPCTalkText` | 2 | AskText | yes |
| `getNPCTalkNum` | 3 | AskNumber | yes |
| `sendSimple` | 4 | AskMenu | yes |
| `getNPCTalkStyle` | 7 | AskAvatar | yes |
| `sendAcceptDecline` | 12 | AskYesNoQuest | yes |
| `getDimensionalMirror` | 14 | AskSlideMenu | yes |

A source that reproduces all eight of the values we already get right, and disagrees about v84, has
earned belief about v84.

*(Two Cosmic values do **not** match: `OnAskQuiz` sends 6 = `AskSpeedQuiz` and `OnAskSpeedQuiz` sends
7 = `AskAvatar`. Those are **pre-existing v83 bugs**, unrelated to this ticket, and are deliberately
left alone — the rule is that v83 stays byte-exact, wrong or not.)*

## 2. Correction to ticket 25

Ticket 25 §3 concluded *"the packet layer is exonerated for quest 1021 … look at server-side
quest/script logic or client WZ data."* That conclusion was drawn over a scope list of six families
in which **the scripted-conversation family does not appear**. Its "Quest" row compared
`ResignQuest#Action`, `StartQuest#ActionScriptStart/End` and `OnMessage#CompleteQuestRecord` — the
quest-*record* family. `CScriptMan::*` / `NPC_TALK` was never compared. Scope item 3's "field objects
(mobs, **NPCs**, drops, reactors)" is NPC *spawn and movement*, not NPC *conversation*.

So the honest reading of ticket 25 is: **NPC_TALK was not compared, and no v84 delta was reported for
it because nobody looked.** Its §3 conclusion should be narrowed to the families it actually covered.

## 3. Ruling out candidates 2, 3 and 4

One test does all three: `src/test/java/server/Quest1021RealLoad.java` →
`acceptDeclineCarriesTheDialogTypeThisClientDispatchesOn`. It drives the **real** `QuestActionHandler`
with the owner's verbatim capture bytes, the **real** `Quest` load off `wz/`, and the **real**
`scripts/quest/1021.js` through the real Graal engine, advances statuses 0 → 1 → 2, and reads the
dialog-type byte off the emitted wire bytes.

It passes. Therefore, at status 2:

- **Candidate 2 (swallowed exception) — dead.** `sendAcceptDecline` returns and produces a packet.
- **Candidate 3 (conversation disposed/wedged) — dead.** The conversation survives both advances and
  the third `start()` reaches the `status == 2` branch.
- **Candidate 4 (a manager gate) — dead.** Nothing between the script and `Client.sendPacket`
  intercepted the call; the bytes were built and handed to the client.

The packet was always sent. The client threw it away.

## 4. The fix

All of it version-gated on `ServerConstants.VERSION >= 84`; the v83 wire is byte-identical.

**`src/main/java/tools/PacketCreator.java`** — one private helper, applied at all seven `NPC_TALK`
write sites, so every dialogue type is fixed at once rather than only the one the ticket named:

```java
private static byte dialogType(byte v83Type) {
    return ServerConstants.VERSION >= 84 && v83Type >= 1 ? (byte) (v83Type + 1) : v83Type;
}
```

**`src/main/java/net/server/channel/handlers/NPCMoreTalkHandler.java`** — the inverse, once, at the
door. The client echoes back the type it dispatched on, so on v84 it returns the shifted value; every
consumer downstream (`if (lastMsg == 2)` for AskText, and ~40 `type == n` tests across `scripts/`) is
written in v83 numbers and keeps working untouched:

```java
byte lastMsg = PacketCreator.v83DialogType(p.readByte());
```

**Also fixed in the same pass** (same family, same bracket): `getDimensionalMirror` gains a leading
`slideDlgType` int on v84+. `AskSlideMenu` reads menuType+message at v83 (`SetSlideMenuDlg@0x76b5c8`,
2 fields) but slideDlgType+menuType+message at v87 (`@0x7b92d1`) and v95 (`@0x6dbe50`, 3 fields).
Bracketed, not directly measured at v84 — v84's own export leaves that body unresolved.

### What this fixes beyond accept/decline

Every scripted dialogue except `Say` was mis-typed on v84 and would have failed the same way or worse:

| call | was sending (v84 meaning) | now sends |
|---|---|---|
| `sendYesNo` | 1 = **SayImage** → client reads a byte count + N strings out of the message text | 2 = AskYesNo |
| `sendGetText` | 2 = AskYesNo → the def/min/max fields are never read | 3 = AskText |
| `sendGetNumber` | 3 = AskText | 4 = AskNumber |
| `sendSimple` | 4 = AskNumber → reads 12 bytes of menu text as def/min/max | 5 = AskMenu |
| `sendStyle` | 7 = AskSpeedQuiz | 8 = AskAvatar |
| `sendAcceptDecline` | **12 = nothing at all** | 13 = AskYesNoQuest |
| `getDimensionalMirror` | 14 = AskBoxText | 15 = AskSlideMenu |

`sendYesNo` and `sendSimple` are the two most-used dialogues in `scripts/`. Expect a large amount of
NPC content to start working that nobody had got round to reporting yet.

## 5. Confidence, and the one thing that is inferred

**Measured** (IDA, four versions): the outgoing enum shift. High confidence.

**Inferred** (one step): that the v84 client *echoes* its own enum value in the NPC_TALK_MORE reply,
which is what `v83DialogType` undoes. The basis is that v83's reply encoders demonstrably echo the
value they dispatched on — `OnSay#Reply` writes 0, `OnAskText#Reply` writes 2, `OnAskMenu#Selection`
writes 4, all v83 numbers. v84's reply builders are unresolved in atlas's export, so this is not
measured.

**It is safe either way for the reported bug.** Accept/decline replies land in the handler's `else`
branch under both readings, and `1021.js` only tests `mode`. The inference only affects `AskText`
(`sendGetText`), which is 100% broken on v84 today regardless, so the change cannot make anything
worse.

## 6. The single click to confirm

Redeploy, then **run quest 1021 again and click through**. Two outcomes, both informative:

- **A third dialogue appears with Accept / Decline buttons.** Clicking Accept should give Roger's
  Apple (2010007) and set 1021 to STARTED. Fix confirmed.
- **Still only two Nexts.** Then the dialog-type enum was not the whole story, and the next thing to
  check is `bParam` — the `param & 4` bit gates a secondary NPC-template int that every v84 per-type
  handler reads at the head of its body. Cosmic writes `speaker` into that byte and always sends 0
  for these calls, so it should not fire; but it is the only remaining unread field in the frame.

A second, cheaper confirmation if he is willing: **talk to any NPC with a yes/no prompt** (before the
fix that sent SayImage and drew nothing sane). If those start rendering too, the enum shift is
confirmed across the family, not just for one type.

## 7. Files

- `src/main/java/tools/PacketCreator.java` — `dialogType`, `v83DialogType`, 7 call sites, slide-menu int
- `src/main/java/net/server/channel/handlers/NPCMoreTalkHandler.java` — reply normalisation
- `src/test/java/server/Quest1021RealLoad.java` — `acceptDeclineCarriesTheDialogTypeThisClientDispatchesOn`

Suite: **2110 passed, 0 failed.**

## 8. Not done, deliberately

- **`OnAskQuiz` / `OnAskSpeedQuiz` send the wrong v83 type** (6 and 7 instead of 5 and 6). Real bug,
  present on v83 too, out of scope tonight — fixing it would change the v83 wire.
- **The v83 `getDimensionalMirror` shape is unverified** beyond the AskSlideMenu note; only the v84
  leading int was added.
- **v84 reply-builder addresses are unresolved** in atlas's export, which is why §5 has an inference
  in it. Re-annotating `CScriptMan::OnAskYesNo`'s reply path at v84 would close it.
