# 11e — Evan on a v83 client: community-sourced feasibility research

**Type:** web research, no code.
**Question that decides everything:** is Evan on a GMS v83 client a dead end?
**Answer:** *Partially yes, and worse than "dead end" — it is a **permanently partial** path.* Read §1.

Evidence labels used throughout:
- **[F]** documented fact (patch notes, dated release records)
- **[R]** a specific named person's report on a forum
- **[I]** my inference, clearly marked

---

## 1. HEADLINE — what the community actually says

### 1.1 The timeline is not in dispute [F]

| Version | Date | Content |
|---|---|---|
| GMS v82 | 2010-02-10 | PICs, login from website, Mardi Gras |
| **GMS v83** | **2010-02-22** | Neo City, Kerning Square, "Year of the Tiger" |
| **GMS v84** | **2010-03-31** | **Evan**, Neo City expansion, UI updates |
| GMS v85 | 2010-05-05 | 5th Anniversary, Dragon Rider PQ |
| GMS v86 | 2010-06-16 | Golden Temple, Return of the Explorers |
| **GMS v88** | **2010-07-21** | **Dual Blade**, Chaos bosses, Item Potential |
| GMS v92 | 2010-11-18 | Ulu City — **last pre-Big-Bang version** |
| GMS v93 | 2010-12-07 | **Big Bang Part 1** |

Sources:
- v84 = Evan: <https://msupdate.wordpress.com/2010/03/30/gms-v84-content-notes-neo-city-expansion-ui-updates/> — *"Dragon Master Evan – Players can now play as an Evan and deal devastating attacks with the help of the dragon Mir."*
- v83 date/content and the v82/v83/v84 table: <https://msupdate.wordpress.com/2011/01/19/gms-v91v93v94v95-ratings/> (ratings list enumerates v82 2010-02-10, v83 2010-02-22, v84 2010-03-31, v85, v86, **v88 2010-07-21 "Dual Blade, Chaos Bosses, Friend Finder, Item Potential System"**); v83 patch name "Year of the Tiger": <https://www.hidden-street.net/forum/threads/41902-v-83-Patch-Year-of-the-Tiger>
- Dual Blade = v88, confirmed by devs: <https://forum.ragezone.com/threads/what-version-did-dual-blade-release.1075178/> — *"v88 is the official release (there are sources for v88, but no real localhost), you can find v90 sources and a v90 localhost, although v88 sources are generally more complete."*
- Big Bang = v93, 2010-12-07: <https://maplenewsnetwork.wordpress.com/2010/12/06/gmsv-93-patch-notes-big-bang-part-1/>; v92 explicitly labelled *"Original Setup (Final pre-big bang version)"* in the RaGEZONE client archive: <https://forum.ragezone.com/threads/maplestory-client-localhost-archive.1101897/>

**So: the v83 client binary was compiled and shipped 5 weeks before Evan existed.** Any Evan support inside it is incidental — Nexon code that happened to land early — not a supported feature.

### 1.2 Community consensus: "no Evan in v83 unless you WZ-edit, and even then it is not complete" [R]

Three separate RaGEZONE threads, three different authors, same conclusion.

**(a) `[Release] [v83] Evans` — <https://forum.ragezone.com/threads/release-v83-evans.1108138/>**
The release itself carries the warning: *"These Evan classes were not FULLY implemented. You may want to disable these skills entirely!"* It requires *"lots of editing/implementation"*, *"client editing with basic hex-editing knowledge"*, and *"a lot of WZ edits"*.

**(b) `v83 in evan?` — <https://forum.ragezone.com/threads/v83-in-evan.1107098/>**
- *"It's not really practical adding evan to v83. And unless you are somewhat experienced you won't even be able to."*
- *"The client prevents the leveling up of evan skills. You will have to nop out a few if statements"* … *"and even after that some actions weren't in the client and will crash you unless removed."*
- **Twdtwd**, who actually tried it: *"the dragon animations weren't handled in the client yet. Or if they were, we couldn't find them. We were able to process either the character animations, or the dragon but not both."* and *"all skills and actions while playing Evan looked really funky and off."*
- **Eric** on where to source data: *"from v84, not anything above v92 because skill data changed."* (i.e. Big Bang v93 rewrote skill data — do not pull Evan from v95+.)

**(c) `Evan class for v83` — <https://forum.ragezone.com/threads/evan-class-for-v83.1226050/>** (the most recent and most complete attempt; uses **HeavenMS + v83 client + v84 WZ**, i.e. exactly this project's stack)
Author's own verdict: *"Even though we can make the Evan class work with v83, there are still a lot of parts missing to it (actions, maps, quests, equipment,...)."*

### 1.3 Is the answer "you cannot, use v87+"? — **No, and I will not tell you that, because no source says it** [I]

This is the one place I have to push back on the premise in the brief. I searched specifically for it and found **no source** claiming that private servers offering Evan run v87+, and **no source** describing a working HeavenMS/Cosmic-family server on a v87+ client. The real, sourced picture is:

- The correct client for Evan is **v84 or later** [F]. Not v87 — v84 is the version that shipped it.
- The correct client for **Evan + Dual Blade** (this branch's name) is **v88 or later** [F].
- The correct client for **Evan + Dual Blade + pre-Big-Bang gameplay** is **v88–v92** [F]. v92 is the last pre-BB build.
- Full original GMS setups for **v84, v85, v86, v87, v88, v89, v90, v91, v92, v93, v94, v95** are all archived and downloadable [R]: <https://forum.ragezone.com/threads/maplestory-client-localhost-archive.1101897/>. So the client is not the scarce resource.
- The scarce resource is the **server**. Every mature open-source HeavenMS-lineage server is v83-locked. Higher-version Java sources exist but are far less complete: **Kinoko v95** (<https://forum.ragezone.com/threads/kinoko-v95.1233229/>) is written from scratch, *"still missing a bunch of stuff like actual progression, but enough for people to flash jump around Henesys"*; **Xeon v97**; **Rebirth95** (C#/Python, <https://github.com/67-6f-64/Rebirth95.Server>); **v90 FusionSource**. None is a Cosmic-class server.

**So the honest headline is not "v83 is impossible, go to v87". It is:**

> **v83 Evan is achievable to the level the RaGEZONE threads reached — skills that load, a character that renders — and no further. Dragon rendering, Evan actions, Evan maps/quests/equipment are missing from the v83 client and no one in the community has solved them. Multiple people who tried say the result "looked really funky and off". Meanwhile the only client that has real Evan support is v84+, and the only Cosmic-quality server is v83-locked. There is no combination that gives you both.**

Dual Blade specifically is *worse* than Evan: it is 5 versions further from v83, and I found **zero** reports of anyone attempting Dual Blade on a v83 client.

### 1.4 What about servers advertising "Evan" on v83 lists?

Server-list sites (gtop100, TopG) do list v83 servers claiming Evan. Treat these with suspicion [I], because of this from **Eric** in <https://forum.ragezone.com/threads/new-job-v83-help.1131763/>: *"D.ChaosMS used skill covers instead of true classes"* — i.e. an existing job re-skinned with Evan's skill icons and effects, not job 2001. Building a real new class *"requires a lot of knowledge in the MapleStory client and experience in assembly."*

I found **no video, no screenshot, and no post anywhere confirming a fully functional Evan on a v83 client.** The best-documented attempt (thread (c)) explicitly stops short.

---

## 2. How the people who got furthest did it — and it is the same two patches you already applied

**<https://forum.ragezone.com/threads/evan-class-for-v83.1226050/>** gives literally your two addresses [R]:

```
PatchNop(0x0075C783, 4);   // Active the skills 1
PatchNop(0x00761714, 21);  // Active the skills 2
```

**<https://forum.ragezone.com/threads/release-v83-evans.1108138/>** gives the same second gate as a byte pattern [R]:

```
find:    83 F8 16 0F 84 D7 00 00 00 81 FE D1 07 00 00 0F 84 CB 00 00 00
replace: 90 x21
```
described as *"internal checks for Evan classes"* inside **CSkillInfo::GetSkill**, *"optionally"* at file offset `00361710` / virtual `00761707`.

`83 F8 16` = `cmp eax, 22` (job/100 == 22) and `81 FE D1 07 00 00` = `cmp esi, 2001`. **This is byte-for-byte the gate you found.** You are at the community's known frontier — there is no third published patch beyond these two.

### The full published WZ recipe (thread (c)), for cross-checking your merge

| File | Edit |
|---|---|
| `Character.wz` | replace `00002000.img`; new WzDirectory **`Dragon`** |
| `Skill.wz` | import `2001.img` … `2218.img`; new WzDirectory **`Dragon`** |
| `String.wz` | replace `Skill.img` (Evan skill/job strings), Evan item strings in `Eqp.img` |
| `UI.wz` | **`Basic.img/Tab8`**; **`UIWindow.img/SkillEx`**; **`UIWindow.img/SkillMacroEx`**; Evan quest icons |
| `StringPool` (client) | lines ~5490–5507 (search `magicmissile`) — remap Evan action names, e.g. `dragonAura` → `recoveryAura` |

Corroborated independently by thread (b): *"Add the SkillEx windows for Evan Skills/Macros in UI.wz/UIWindow.img"*, *"add Tab8 in Basic"*, *"add the Dragons into Skill.wz"*, *"add the Dragon equipment into Character.wz"*.

**No download links or GitHub repo were posted in any of these threads.** The Evans release is attachment-only behind RaGEZONE registration.

---

## 3. Your exact crash IS the known, unsolved community crash — and the published "fix" is a guess

**This is the single most operationally useful finding.** In <https://forum.ragezone.com/threads/release-v83-evans.1108138/page-2>, three users report *your* bug [R]:

- post #27: *"localhost not respond"* on pressing **K** (skill tab)
- post #32: **"After I modified everything, the client crashed when I opened the skill bar. No prompt from the client."**
- post #33: **"Ive gotten the same problem as StoryMs, as soon as i open the skill menu it crashes."**

"No prompt from the client" matches your observation exactly: instant death, no `_com_error`, no packet.

The only answer given, by **Eric**:
> *"Likely a wz error when trying to open SkillEx. Or when you attempted to change CSkillInfo::GetSkill (the modified bytes in the client) you broke something."*

**Nobody in the thread ever confirms a fix.** The reports go unanswered. This is an open bug in the community, ~10 years old, at exactly the point you are stuck.

### 3.1 The `SkillEx` lead, and why your own audit already kills half of it [I]

Two independent threads say the v83 `UIWindow.img` needs `SkillEx` + `SkillMacroEx` added from v84, and `Basic.img/Tab8`. Your `STATUS.md:1300-1301` records the opposite finding: *"the string `SkillEx` appears nowhere in the client image in either encoding."*

If that dump is sound, then the v83 **binary** has no `CUISkillEx` at all, and adding the WZ nodes cannot help — which would explain why Eric's suggested fix was never confirmed by anyone. It also reframes the whole thing: **v84 did not just add Evan data, it added a new skill-window UI class to the executable.** That is a code-level feature the v83 binary does not have, and WZ merging cannot supply.

Two concrete things worth checking before accepting that [I]:
1. **`Basic.img/Tab8` is genuinely missing in your tree.** Confirmed locally: `wz/UI.wz/Basic.img.xml` at HEAD contains `Tab`, `Tab2`…`Tab7` and **no `Tab8`** — and `docs/wz-baseline/add-list/UI.txt:7` lists `UI.wz/Basic.img/Tab8` as an unapplied add. The v83 skill window's own tab strip is `UIWindow.img/Skill/Tab/enabled/0..4` — **five slots** (your `STATUS.md:1340`), while v84's `SkillEx/Tab/enabled` has **eleven** (`STATUS.md:1343`). Evan has ten job advancements. A five-slot tab array being indexed for an eleven-tab job is a textbook out-of-bounds with no exception, no packet, and no `_com_error` — precisely your symptom set.
2. **Confirm the memory dump covered the decrypted image.** `MapleStory.exe` is Themida-packed; I verified locally that `SkillEx`, `SkillMacroEx`, `UIWindow` and even `Skill.img` return **zero** hits on the file on disk in both ASCII and UTF-16 — so a static scan proves nothing, and a runtime dump has to be taken after full unpack and cover all sections including any string pool decrypted lazily.

---

## 4. Other hardcoded job checks in the v83 client

Sparse. The community has never published an inventory. What exists:

- **Skill-point distribution gate — "You're lacking Level X Skills"** [R]: <https://forum.ragezone.com/threads/removing-a-check-in-the-maplestory-client-v83.1147791/>. There are *three* separate checks (2nd, 3rd, 4th job). The 2nd-job one is found via string id `0DB6` (3510 dec); the fix is turning `JL 008AD224` into `JMP 008AD224` (`0F 8C BB 00 00 00` → `E9 BC 00 00 00` + `90`). **This is directly relevant to Evan**, whose ten advancements will trip a check built for a four-advancement job chain. Same problem is logged server-side at <https://github.com/ronancpl/HeavenMS/issues/114> ("You're lacking Level 1 Skills").
- **Client actions/animations** [R]: *"some actions weren't in the client and will crash you unless removed"* (thread (b)) — a crash class, but per-skill, not enumerable from sources.
- **Dragon rendering** [R]: Twdtwd — the client can process the character *or* the dragon, not both. This is a hard limit, not a check to NOP.
- **New-job creation generally** [R]: Eric, thread <https://forum.ragezone.com/threads/new-job-v83-help.1131763/> — a real new class needs *"code-cave"* work and *"your own functions and checks for each job"*. No addresses given.

**No sources found** for: stat-window job checks, job-advancement UI checks, or a job string-table enumeration in v83. If they exist, nobody has written them up.

---

## 5. What a client upgrade would cost the server

### 5.1 What the community says has to change [R]

<https://forum.ragezone.com/threads/help-on-making-a-maplestory-private-server-of-any-version.1228156/>:
> updating a version requires *"updating AES keys, updating packet structures (if they changed), and changing the [patches] you need to do to the client (mainly NGS and CRC bypasses)"* — and you *"need to reverse engineer the addresses for patches, and the structure of packets"* with x64dbg and IDA.

Same thread on why v83 is a trap for exactly this reason:
> *"v83 clients are highly edited and modified, and working with them doesn't reflect the workflow of working with other versions."*

### 5.2 What that means for Cosmic concretely [F, verified in this repo]

- `src/main/java/constants/net/ServerConstants.java` is a **five-line file**: `public static final short VERSION = 83;`. That part is trivial.
- `src/main/java/net/opcodes/SendOpcode.java` — **308** hardcoded opcode values. `RecvOpcode.java` — **180**. **488 opcodes** hand-mapped to v83, every one of which shifts when Nexon inserts a packet. This is the real cost, and it is not automatable without a client IDB.
- `src/main/java/client/Job.java:59-63` — Cosmic **already has** `EVAN(2001)`, `EVAN1(2200)` … `EVAN10(2218)`, inherited from the OdinMS lineage. This corroborates thread (b)'s *"everything needed to handle Evan was available for the server in v83."* **The server was never the blocker.**
- Cosmic's README states *"Only the server side is maintained. The client is directly copied from HeavenMS"* and *"Cosmic requires custom wz files due to legacy reasons"* (<https://github.com/P0nk/Cosmic>). A client-version change means abandoning the entire HeavenMS client + custom-WZ inheritance.

### 5.3 The version-choice trade [I], and the one non-obvious data point

If a client upgrade were ever on the table, the pre-Big-Bang ceiling is **v92**, and the Evan+Dual-Blade floor is **v88** — so **v88–v92** is the only window that keeps the pre-BB game *and* gets both classes natively. v90 is the pragmatic pick inside it: it is the only one in that band with both a source *and* a working localhost per <https://forum.ragezone.com/threads/what-version-did-dual-blade-release.1075178/>.

The non-obvious data point: the RaGEZONE archive tags **v95** as the *".idb leak version"* — a public IDA database exists for v95, which is why every modern from-scratch server (Kinoko, Rebirth95) targets it. That makes v95 by far the cheapest version to reverse-engineer against. **But v95 is post-Big-Bang** (v93 was Big Bang Part 1), so it would replace the v83 game the owner actually wants, not extend it. The version with the best tooling and the version with the right gameplay are not the same version. There is no v88–v92 IDB.

---

## 6. Bottom line

1. **The premise "everyone who has Evan runs v87+" is not supported by any source I found** — but neither is "Evan works on v83". The truth is that *nobody has a working Evan*: not on v83 (partial at best, dragon rendering unsolved) and not on any Cosmic-class server (all v83-locked).
2. **You are already at the exact frontier the community reached**, using the same two patch addresses, blocked on the same unsolved skill-window crash that three people reported in 2011 and nobody answered.
3. **The one unexplored lead is a real one and it is cheap:** `Basic.img/Tab8` is confirmed absent from your tree and sits unapplied in `add-list/UI.txt`, and the v83 skill window has 5 tab slots against Evan's 10 advancements. Apply `Tab8` and the `SkillEx`/`SkillMacroEx` nodes as the RaGEZONE recipe prescribes, then re-test `!job 2001`. It costs one merge and either fixes it or eliminates the last published hypothesis.
4. **If it does not fix it**, the sourced conclusion is that v84 added a skill-window UI *class* (`CUISkillEx`) to the executable, not just data — and no amount of WZ merging can add code to a Themida-packed binary. At that point v83 Evan is done, and the honest options are: (a) ship Evan with the skill window disabled, as the RaGEZONE release itself suggests (*"You may want to disable these skills entirely"*), or (b) accept that Evan + Dual Blade means a v88–v92 client and a ~488-opcode server re-target that no one in the community has ever done to a HeavenMS descendant.
5. **Dual Blade on v83 has never been attempted by anyone.** It is 5 versions further out than Evan. Treat `evan-dualblade` as two problems, and the second one has no prior art at all.

---

## Sources

- <https://msupdate.wordpress.com/2010/03/30/gms-v84-content-notes-neo-city-expansion-ui-updates/> — v84 = Evan, 2010-03-31
- <https://msupdate.wordpress.com/2011/01/19/gms-v91v93v94v95-ratings/> — GMS version/date/content table v82–v95
- <https://www.hidden-street.net/forum/threads/41902-v-83-Patch-Year-of-the-Tiger> — v83 patch identity
- <https://maplenewsnetwork.wordpress.com/2010/12/06/gmsv-93-patch-notes-big-bang-part-1/> — v93 = Big Bang, 2010-12-07
- <https://forum.ragezone.com/threads/release-v83-evans.1108138/> — the v83 Evan release; GetSkill byte pattern; "not FULLY implemented"
- <https://forum.ragezone.com/threads/release-v83-evans.1108138/page-2> — **the skill-window crash reports (#27, #32, #33) and Eric's unconfirmed SkillEx hypothesis**
- <https://forum.ragezone.com/threads/v83-in-evan.1107098/> — feasibility consensus; SkillEx/Tab8/Dragon WZ recipe; dragon-rendering limit; "use v84 data, not above v92"
- <https://forum.ragezone.com/threads/evan-class-for-v83.1226050/> — HeavenMS+v83 guide; `PatchNop(0x0075C783,4)` / `PatchNop(0x00761714,21)`; full WZ list; StringPool 5490–5507
- <https://forum.ragezone.com/threads/new-job-v83-help.1131763/> — new classes need code caves; D.ChaosMS used fake "skill covers"
- <https://forum.ragezone.com/threads/removing-a-check-in-the-maplestory-client-v83.1147791/> — "You're lacking Level X Skills" gate, `JL 008AD224` → `JMP`
- <https://forum.ragezone.com/threads/what-version-did-dual-blade-release.1075178/> — Dual Blade = v88; v88 sources w/o localhost, v90 has both
- <https://forum.ragezone.com/threads/maplestory-client-localhost-archive.1101897/> — v84–v95 original setups archived; v92 "final pre-big bang"; v95 ".idb leak version"
- <https://forum.ragezone.com/threads/help-on-making-a-maplestory-private-server-of-any-version.1228156/> — cost of a version change (AES keys, packet structures, NGS/CRC bypass)
- <https://forum.ragezone.com/threads/kinoko-v95.1233229/> — v95 from-scratch server, incomplete
- <https://github.com/67-6f-64/Rebirth95.Server> — C#/Python v95 emulator
- <https://github.com/P0nk/Cosmic> — Cosmic is GMS v83; server-side only; custom WZ
- <https://github.com/ronancpl/HeavenMS/issues/114> — "You're lacking Level 1 Skills"
- <https://github.com/444Ro666/MapleEzorsia-v2> — the HD/localhost DLL this client is built on; resolution patches only, no job checks

RaGEZONE returns HTTP 403 to direct fetches; all RaGEZONE content above was read through the `r.jina.ai` text proxy.
