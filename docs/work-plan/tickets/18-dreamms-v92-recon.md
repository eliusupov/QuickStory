# Ticket 18 — DreamMS / KaizenMS v92 recon

Status: research complete. Read-only; nothing downloaded, installed or run.

**Scope note (mid-ticket re-target):** the owner's phase 1 is now **GMS v84 feature-complete
(Evan first, Dual Blade after)**. v92 is deferred to phase 2. This report keeps the v92 recon as
phase-2 reference material but leads with the findings that apply to **v84 today**.

Evidence labels: `[FACT]` = sourced, URL given. `[REPORT]` = someone's claim. `[INFERENCE]` = mine.

---

## 0. Headline answers

| Question | Answer |
|---|---|
| Q1 — Is DreamMS really v92? | **Yes, GMS-like v92.** And critically: **it was v83 until late 2023 and performed a v83→v92 upgrade announced 26 Nov 2023.** `[FACT]` |
| Q1b — Is KaizenMS really v92? | **Yes**, self-described "v92 Pre-Big Bang", Evan + Dual Blade working. `[FACT]` |
| Q4 — Is either server's source public? | **No. Neither publishes source.** Treat both as **feature spec + existence proof**, not as code to port. `[FACT — absence of any repo/leak found]` |
| **Biggest find (phase 1)** | `Chronicle20/gms-83-dll` ships a **first-class, CI-built, released GMS v84.1 client-edit target** with localhost redirect + full security bypass. Prebuilt `release-GMS-84.1.zip`. `[FACT]` |
| Showstoppers found | **None for v84.** See §3 — v84 adds *zero* new client anti-cheat surface over v83. One real gap: **HD/widescreen is not ported to v84** (§3.4). |

---

## 1. Q1 — Version confirmation

### 1.1 DreamMS = v92, upgraded from v83

`[FACT]` <https://dreamms.gg/> (live, and archived <https://web.archive.org/web/20260620233147/https://dreamms.gg/>)
> "A GMS-like **v92** MapleStory private server built around a challenging yet fun experience with our low rates."
> `<div class="stat-card__value">v92</div><div class="stat-card__label">Version</div>`
> "Before Big Bang MapleStory, Reimagined."

`[FACT]` **The version claim changed between 2023-09-27 and 2024-05-09.** I pulled the tagline out of
15 Wayback snapshots of `https://dreamms.gg/`:

| Snapshot | Tagline |
|---|---|
| 2022-12-21, 2023-06-04, **2023-09-27** | "GMS-like **v83** MS private server…" |
| **2024-05-09** through 2026-06-20 | "GMS-like **v92** MS private server…" |

Snapshot URLs follow the pattern `https://web.archive.org/web/<timestamp>id_/https://dreamms.gg/`
with timestamps `20230927093315` (v83) and `20240509155815` (v92).

`[FACT]` The upgrade is confirmed in their own words. Thread *"[v92] Patch Notes - Part 1 (Skill
Balancing)"*, posted by **Capu on Nov 26, 2023**:
<https://web.archive.org/web/20250125134723/https://forum.dream.ms/threads/v92-patch-notes-part-1-skill-balancing.8804/>
> "With our **v92 upgrade**, we decided to review all the classes and work on balancing skills to the
> best of our ability. … The focus of today's patch notes is on skill changes. More information on
> the general updates of v92 will be provided as soon as we can."

`[FACT]` Their forum footer still read *"A GMS-like **v83** private server"* in the 2023-12-07 snapshot
of <https://forum.dream.ms/threads/faqs-technicals-installation-client-errors.3036/> — i.e. the
migration was in flight and the site was inconsistent for months.

**Why this matters more than the version number itself:** DreamMS is a **live, documented precedent
for exactly the migration the owner is contemplating** — a v83 pre-BB private server that moved its
client and content to v92 and kept running. Their changelog is effectively a post-mortem of that
migration (§6).

Related DreamMS URLs: forum `https://forum.dreamms.gg/` (was `forum.dream.ms`, both behind a
Cloudflare challenge as of 2026-08-16), wiki `https://wiki.dreamms.gg/` (NXDOMAIN as of today).
Note `dreamms.co` is an unrelated parked domain; `dreamms.org` is an unrelated 1990s non-profit site.

### 1.2 KaizenMS = v92

`[FACT]` <https://kaizenms.net/?base=main>
> "Welcome to **Kaizen v92**!" … "**The Last and Best Pre-BB Version**" … "No more low effort v83
> remakes! Kaizen v92 is the perfect blend of the classic nostalgic Pre-BB … **Fully functional Evan
> and Dual Blade jobs**, Item Potential system, and **custom widescreen client that boots up in the
> blink of an eye**."

`[FACT]` <https://kaizenms.net/?base=main&page=guide>
> "V92 PRE-BIG BANG: Fully working Evan, Dual Blade, Item Potential, Item Sets, Equip Enhancement,
> **Shoulderpads, Kataras**." … "NO GFX BUG. Widescreen client with customizable settings."

Discord: <https://discord.gg/VnbfEmfSKv>. Two independent servers therefore prove **v92 + Evan +
Dual Blade + widescreen** is achievable in practice.

---

## 2. Q4 — Source availability (the honest answer)

`[FACT — searched and not found]` **Neither DreamMS nor KaizenMS publishes server source.** I searched
the GitHub repository API for `dreamms`, `kaizenms`, `KaizenMS maplestory`, and `maplestory v92`.
The only DreamMS-related public artifact is a **fan-made skills wiki**, not server code:

- `ransananes/dreamms-wiki` — <https://github.com/ransananes/dreamms-wiki> — README: *"Just a web
  skills library of a private maplestory server, showcase https://dreamms-wiki.onrender.com/"*.
  Contains `server/data/data.json` (1.88 MB of skill data) and ~300 skill icon PNGs. `[FACT]`
- Unrelated name collisions: `v3921358/dreamMS120` (Taiwanese v120 source, different project),
  `roych98/dreamms-mcp` (created 2026-08-15, unrelated), `mbelDev/DreamMS` (Korean, unrelated).
- `lukethomas1/maple-cube-sim` — "Cube simulator for Maplestory (**KaizenMS v92**)" — a fan Python
  cube simulator, not server code. `[FACT]`

No RaGEZONE leak, fork lineage, or partial source for either server was findable. **Do not plan any
work that assumes their code is obtainable.**

### 2.1 What IS obtainable — the actual code sources

These are the real porting inputs (the coordinator already flagged the Java ones; I verified the
opcode claim):

| Repo | Version | Value |
|---|---|---|
| `Chronicle20/Vertisy` <https://github.com/Chronicle20/Vertisy> | **v90/v92**, Java, unmaintained check-in | `src/main/java/net/SendOpcode.java` = **386 lines**, `net/RecvOpcode.java` = **250 lines**. Cosmic has 366/216. Also `tools/packets/{CLogin,CStage,CUserPool,CWvsContext,CCashShop}.java` and `server/shark/SharkPacket.java`. **This is the public v92 opcode table.** `[FACT]` |
| `MapleStoryA/orion-server` | v90, OdinMS lineage | per coordinator: all 26 Dual Blade skills |
| `iw2d/kinoko` | v95 | per coordinator |
| `Chronicle20/gms-83-dll` <https://github.com/Chronicle20/gms-83-dll> | **GMS 61/72/79/83/84/87/95/111 + JMS 185** | **The client side. See §3 — this is the phase-1 find.** `[FACT]` |
| `v3921358/ZeroMS085` | v85, Java, Chinese source | Untriaged. Nearest-to-v84 server source I saw; may be worth a look for v84 Evan handling. `[INFERENCE]` |

Note that `Vertisy` and `gms-83-dll` are by the **same author (Chronicle20)** — client-side and
server-side of the same version range, which raises confidence in both.

---

## 3. Q3 (re-prioritised) — NGS / CRC / Themida / localhost above v83

This is the section the coordinator asked to promote, and it has a clean, sourced answer.

### 3.1 GMS v84 needs NO new bypasses over v83

`[FACT]` `Chronicle20/gms-83-dll/bypass/security_hooks.cpp`
(<https://github.com/Chronicle20/gms-83-dll/blob/main/bypass/security_hooks.cpp>) neuters the client
security surface, and the version gates tell you exactly when each threat appears:

```cpp
// applies to ALL GMS versions, incl. 83 and 84:
INITMAPLEHOOK_OR_RETURN(..., CSecurityClient__OnPacket_Hook, C_SECURITY_CLIENT_ON_PACKET);
#if defined(REGION_GMS)
INITMAPLEHOOK_OR_RETURN(..., SendHSLog_Hook, SEND_HS_LOG);   // HackShield log
#endif

#if (defined(REGION_GMS) && BUILD_MAJOR_VERSION >= 87) || defined(REGION_JMS)
INITMAPLEHOOK_OR_RETURN(..., DR__check_Hook, DR_CHECK);      // v87+ ONLY
#endif

#if (defined(REGION_GMS) && BUILD_MAJOR_VERSION >= 95)
INITMAPLEHOOK_OR_RETURN(..., CeTracer__Run_Hook, CE_TRACER_RUN);  // v95+ ONLY
#endif
```

Reading:
- `CSecurityClient::OnPacket` and `SendHSLog` — present at v83 **and** v84. Already solved.
- `DR::check` — **first appears at GMS v87.** Not a v84 problem. Is a v92 problem.
- `CeTracer::Run` — **first appears at GMS v95.** Not a v84 or v92 problem.

`[INFERENCE, high confidence]` **v84 is anti-cheat-free relative to v83.** Phase 1 does not face a new
NGS/CRC hurdle. Phase 2 (v92) adds exactly one extra hook (`DR::check`), and the repo already
implements it for v87+.

### 3.2 Themida is not new — v83 is already Themida-packed

`[FACT]` `gms-83-dll` README: the VC++ redistributable troubleshooting note says a missing redist
*"may present as a **Themida error** 'Cannot find `ijl15.dll`, Please, re-install this application'"*
— i.e. the **v83** GMS client is already Themida-wrapped and the ijl15 proxy already coexists with it.

`[FACT]` The v84 task's acceptance criteria explicitly cover it:
`docs/tasks/task-006-gms-v84-support/prd.md` FR-16 — *"A live GMS v84 client launches with the proxy
`ijl15.dll` and the core edits deployed, reaches the title/login screen, and the targeted edits behave
(**no crash, no Themida fault**)"*; and NFR *"**AV/Themida compatibility.** Patched addresses must be
valid for the v84 image so the client passes Themida integrity expectations the same way other
versions do."

`[INFERENCE]` Themida is a solved, ongoing constraint at every version in this family, not a v84 cliff.

### 3.3 How a post-v83 private-server client actually connects — three documented mechanisms

**(a) DLL-proxy + Winsock redirect (the reusable one).**
`[FACT]` `gms-83-dll` README:
> "A collection of MS Client Edits which can be loaded through the provided **ijl15 proxy**. …
> Backup the original `ijl15.dll` … Place the proxy `ijl15.dll` in the root MapleStory directory.
> Create an `edits` folder … Place (any) DLLs and corresponding INI configurations within."
>
> "**redirect** — Redirect IP the game uses for socket connections. Provided configuration produces a
> 'localhost'."

`[FACT]` `redirect/redirect.ini` is literally this:
```ini
[Main]
OriginalIP1=63.251.217.2
OriginalIP2=63.251.217.3
OriginalIP3=63.251.217.4
RedirectIP=127.0.0.1
RedirectPort=8484
```
`redirect/dllmain.cpp` hooks the **Winsock service-provider table** (`WSPPROC_TABLE m_ProcTable`), so
it intercepts the connect at the socket layer rather than patching hardcoded IP bytes.
`[INFERENCE]` That makes the redirect largely **version-independent** — the addresses that change
between v83 and v84 are in the *bypass*, not in the *redirect*. This is the single most reusable
piece of knowledge asked for in the brief, and it is open source with a permissive-ish (unlicensed)
public repo.

Credited upstream `[FACT]`, from the README:
- **Hendi — "Localhost Workshop"**, <https://forum.ragezone.com/threads/localhost-workshop.1202021/>
  — *"Foundation for minimum client edits to produce a localhost."* This is the canonical RaGEZONE
  reference the brief asked for.
- **MinimumDelta — MapleClientCollection** — the DLL-edit framework.
- **izarooni — MapleEzorsia** <https://github.com/izarooni/MapleEzorsia> — *"Foundation for HD client
  edits."* This is the owner's current v83 HD hack's upstream.

**(b) Ship a fully pre-patched client as a ZIP (KaizenMS).**
`[FACT]` <https://kaizenms.net/?base=main&page=download>
> "1. Extract the ZIP somewhere. 2. Run **Kaizen v92.exe**. If you don't see the .exe, your anti-virus
> removed it."

Their troubleshooting page names the shipped files `[FACT]`: **`Kaizen v92.exe`**, **`Client.dll`**,
**`nmconew.dll`**, **`dinput8.dll`**, **`settings.ini`**. Notably:
> "If you still get the error even after installing the links above, try **deleting `dinput8.dll`**
> from the KaizenMS folder."

`[INFERENCE]` Kaizen uses the **same `dinput8.dll` proxy technique the owner already runs via
Ezorsia**, plus a `Client.dll` payload, plus a renamed/rebuilt `MapleStory.exe`. There is no launcher
and no patcher — one ZIP, run the exe. No NGS/Themida step appears anywhere in their support docs,
consistent with a pre-patched client.

**(c) Custom .NET launcher + differential patcher (DreamMS).**
`[FACT]` <https://dreamms.gg/> install modal:
> "Create a game folder **and exclude it from Windows Defender** scans so it doesn't flag client
> files. **Download our launcher** directly into that folder. **Extract the launcher and run it** to
> install our latest game files."

Prereqs listed on the same page `[FACT]`: Visual C++ Redistributables, DirectX Runtime, and
**.NET 5.0 Desktop Runtime** (`runtime-desktop-5.0.13-windows-x64`). Two mirrors:
Mega (`https://mega.nz/file/dn03hRCb#…`) and Google Drive (`…/file/d/1Hm7rxSWLYsB2lzMjw5sPXDCpU0sZd0U0/view`).
`[INFERENCE]` .NET 5 desktop runtime ⇒ a C#/WPF launcher; the patcher is a separate `Patcher.exe`
(and `Patcher (64-bit)`) inside the game folder.

`[FACT]` <https://forum.dream.ms/threads/faqs-technicals-installation-client-errors.3036/>
(2023-12-07 snapshot) — their whole technical FAQ, useful as a checklist of what breaks:
> "When I download the game, `dreamms.exe` is deleted → Turn off Windows Defender … Add the DreamMS
> folder to your exclusions"
> "Nothing happens when clicking on `dreamms.exe` → Run `DefaultSettings.reg` and make sure file
> location doesn't have any special characters."
> "Client crashes when opening inventory → `regedit` → `HKEY_LOCAL_MACHINE\Software\WOW6432Node` →
> delete `Wizet`."
> "How can I reset my Dream MS Client Mods settings to default? → `regedit` →
> `HKEY_CURRENT_USER\Software` → delete `DreamMS`."
> "Weird symbols instead of text in-game / portals don't work properly → set system locale to
> English (US)"
> "Crash when accessing widgets, selecting character, changing resolution or accessing fullscreen →
> **close any third-party game launchers** (Steam, Riot Vanguard, Blizzard, Discord). **These game
> engines' anticheat systems interfere with MapleStory.**"
> "`Gr2D has failed to detect ur screen mode` → check your monitor's refresh rate … at least 60 Hz"

`[FACT]` DreamMS's client is **VMProtect-wrapped** — a player thread exists titled
*"the code execution cannot proceed because `vmprotectsdk32.dll` was not found…"*
(`https://forum.dream.ms/threads/the-code-execution-cannot-proceed-because-vmprotectsdk32-dll-was-not-found-reinstalling-the-program-may-fix-this-problem.7647/`).
That is **their own** protection added on top, not Nexon's — irrelevant to us except as evidence they
rebuilt/repacked the client heavily.

**Bottom line for the owner:** neither server's client is a drop-in for Cosmic. DreamMS's is
VMProtect'd and launcher-bound; Kaizen's is a bespoke prebuilt. The *transferable* asset is
mechanism (a) — `gms-83-dll`, which is open, versioned, and already covers v84.

### 3.4 ⚠ The one real gap: HD/widescreen is NOT ported past v83

`[FACT]` Both HD sources are v83-only:
- `izarooni/MapleEzorsia` — description: *"**v83** edits for creating a custom resolution client"*,
  last push 2024-03-18.
- `444Ro666/MapleEzorsia-v2` — description: *"**v83** Standalone HD dll client/localhost"*,
  last push 2024-07-17.

`[FACT]` `gms-83-dll` **credits** Ezorsia as the "foundation for HD client edits" but its shipped edit
list contains **no HD/resolution edit**: the modules are `bypass`, `doom-fix`, `enable-minimize`,
`no-ad-balloon`, `no-beginner-party-block`, `no-enter-mts-map-restriction`, `no-patcher`, `redirect`.

`[INFERENCE — flag this to the plan]` Moving the client from v83 to v84 **loses the owner's current
`dinput8.dll` Detours HD hack** unless the Ezorsia resolution edits are re-based onto v84 addresses.
The `gms-83-dll` v84 memory map (145 keys, §3.5) gives most of the anchor addresses an HD port would
need, and both DreamMS and Kaizen demonstrate native widescreen is achievable at v92 — but nobody has
published it for v84. **Budget this as real work, not a freebie.** It is not a showstopper (the
client runs fine at 800×600), but it is a regression against the owner's current setup.

### 3.5 GMS v84 is already a released, CI-built target

`[FACT]` `.github/workflows/_build.yml` build matrix:
```yaml
- { region: GMS, major: 61,  minor: 1 }
- { region: GMS, major: 72,  minor: 1 }
- { region: GMS, major: 79,  minor: 1 }
- { region: GMS, major: 83,  minor: 1 }
- { region: GMS, major: 84,  minor: 1 }     # <-- phase 1
- { region: GMS, major: 87,  minor: 1 }
- { region: GMS, major: 95,  minor: 1 }
- { region: GMS, major: 111, minor: 1 }
- { region: JMS, major: 185, minor: 1 }
```

`[FACT]` Releases carry prebuilt per-version zips. `v2.1.2` (2026-07-28) assets include
**`release-GMS-84.1.zip`**, `release-GMS-83.1.zip`, `release-GMS-87.1.zip`, `release-GMS-95.1.zip`.
v84 has been in the release set since `v2.1.0` (2026-06-19).

`[FACT]` `memory_maps/GMS/v84_1.cmake` exists, 261 lines, all 145 required keys, e.g.:
```cmake
# Protocol constants read from the v84 CClientSocket::OnConnect send path (0x499DCD),
# not copied from v83 — all three coincide with v83.
set(VERSION_HEADER 8)        # cmp byte ptr [ebp+namelen+3], 8 @ 0x49A08A
set(PLAYER_LOGGED_IN 0x14)   # push 14h @ 0x49A2F9
set(CLIENT_START_ERROR 0x19) # push 19h @ 0x49A2A0
```

**This is a directly load-bearing fact for phase 1:** `VERSION_HEADER`, `PLAYER_LOGGED_IN` and
`CLIENT_START_ERROR` are **identical between v83 and v84**, verified against the v84 binary rather
than assumed. The handshake/login opcode plumbing Cosmic already has should not need changing for
v84. `[FACT — the comment states the verification; INFERENCE — that the server side therefore
needs no change]`

`[FACT]` The task PRD (`docs/tasks/task-006-gms-v84-support/prd.md`, created 2026-06-05) also states
the open question was resolved in our favour, and describes the porting method for future versions:
> "Are the protocol constants (`VERSION_HEADER`=8, `PLAYER_LOGGED_IN`=0x14, `CLIENT_START_ERROR`=0x19)
> identical in v84, or did the opcode table shift between v83 and v84?"

and

> "v84 sits *exactly* on an existing source gate boundary (`< 84` / `> 83` / `== 83` gates already
> exist), every one of those gates is an unverified hypothesis for v84. This task verifies the
> layout/size of all 22 version-gated `common/` headers against the v84 binary."

`[INFERENCE]` The existence of `< 84` / `> 83` / `== 83` gates *predating* the v84 task means the
v83↔v84 client struct boundary is a **known, real** divergence point in `CWvsContext`, `CLogin`, and
`socket_hooks`. Worth reading `struct-verification.md` and `signature-catalog.md` in that task folder
before touching the client. (Not read in this ticket — timeboxed.)

---

## 4. Q2 — Client distribution, side by side

| | **DreamMS** | **KaizenMS** |
|---|---|---|
| Delivery | Small **launcher** downloaded into an empty folder; it then installs the game files. Also a full ZIP fallback. `[FACT]` | Single **ZIP**, extract and run `Kaizen v92.exe`. `[FACT]` |
| Mirrors | Mega + Google Drive `[FACT]` | Mega ×2, transfer.it ×2, Gofile.io, Fastupload `[FACT]` |
| Size | not stated on any page found — **no sources found** | not stated — **no sources found** |
| Updates | **Differential `Patcher.exe`** — *"Our Patcher was developed to only make the necessary edits to your game files - this means patches are generally small in size"* `[FACT, features.js]`. Players run `Patcher` on "client is outdated". | None documented; re-download. |
| Runtime deps | VC++ redist, DirectX runtime, **.NET 5.0 Desktop Runtime** `[FACT]` | VC++ redist **x86 and x64** (2010/2012/2015+) `[FACT]` |
| Protection | Their own **VMProtect** `[FACT]` | none documented |
| Client files | `dreamms.exe`, `DreamMS.dll`, `Patcher.exe`, `DefaultSettings.reg`; settings in `HKCU\Software\DreamMS` `[FACT]` | `Kaizen v92.exe`, `Client.dll`, `nmconew.dll`, `dinput8.dll`, `settings.ini` `[FACT]` |
| How it reaches the server | **Not documented publicly.** Client is theirs and VMProtect'd. Indirect evidence: a *"Singapore Routing"* client-mods flag *"re-routes the client's connection to the server via our Singapore data center"* `[FACT, changelog]` ⇒ the endpoint is client-configurable at runtime. `[INFERENCE]` | **Not documented publicly.** `dinput8.dll` proxy + `Client.dll` present ⇒ same proxy-DLL family as Ezorsia. `[INFERENCE]` |
| Resolution config | **Widget Manager → Client Mods tab**, in-game, no re-client: 800×600, 1024×768, 1280×720, 1600×900, 1920×1080, Borderless Fullscreen `[FACT]` | **`settings.ini`** — *"Users may open the `settings.ini` file and configurate settings such as Screen Resolution"* `[FACT]` |
| Mac | not documented | **Crossover / Wine** supported incl. Apple Silicon; recommends Crossover 23.7.1 / Wine 7.7 `[FACT]` |

`[INFERENCE]` For the owner's HD requirement, **Kaizen's `settings.ini` model is the one to copy**:
a resolution value read from an INI by an injected DLL, exactly the shape of an Ezorsia-style edit,
rather than DreamMS's in-client Widget Manager UI (much larger build).

---

## 5. Q6 — Feature coverage as a v92 completeness checklist

### DreamMS `[FACT — https://dreamms.gg/assets/js/features.js?1.9]`

- **Classes:** "Choose from Adventurers, Knights of Cygnus, **Aran**, **Evan** or **Dual Blade**.
  All classes have their level limit increased to 300." (300 is their custom cap, not v92 stock.)
- Confirmed by their fan wiki's job table `[FACT — ransananes/dreamms-wiki `client/src/constants/jobs.js`]`:
  `BEGINNER: ["Beginner","Aran","Evan","Blade Recruit"]`; Magician 4th tier lists **Evan 1 … Evan 10**;
  Thief tiers list **Blade Acolyte → Blade Specialist → Blade Lord → Blade Master**.
  **No** Battle Mage / Wild Hunter / Mechanic / Cannoneer / Mercedes — consistent with pre-Big-Bang v92.
- **Party Quests:** "Henesys, Kerning, Ludibrium, Ludibrium Maze, **Monster Carnival**, Pirate, Ellin,
  Romeo & Juliet, Amoria, Guild, **Tower of Oz**, Orbis" + a PQ Points system via NPC Ephey.
  ⚠ Note they **kept Monster Carnival** — which the owner intends to keep too, so its removal at
  v84/v92 is evidently not forced by the client.
- **Mu Lung Dojo exists** — forum thread `dojo-points-reset.3666` `[FACT]`.
- **Imported content:** "Ulu City, Shaolin Temple, Shanghai, Ninja Castle, Neo City : Year 2227,
  Henesys Ruins, Dark Ereve, Knight Stronghold, Thailand, Golden Temple, Narin, Root Abyss."
  `[INFERENCE]` "Imported" = pulled from other regions/later versions, i.e. **not** shipped by GMS v92.
- Other systems: Monster Book / Crusader Codex, Maker, Family, Guild, Expeditions with a loot pool,
  Weekly Challenges, no HP washing, custom drop rates, 4× EXP.
- **Cash Shop: not mentioned anywhere in their feature list.** Cash items clearly exist (their
  changelog has *"Fixed Carly's Uniform not being a cash item after v92 upgrade"*), but no
  documented cash-shop feature claim. **No sources found** either way.

### KaizenMS `[FACT — https://kaizenms.net/?base=main and ?page=guide]`

- "Fully working **Evan**, **Dual Blade**, **Item Potential**, Item Sets, Equip Enhancement,
  **Shoulderpads**, **Kataras**."
- Content: "Coke Town, Ulu City, Neo Tokyo, Ninja Castle, Lion King's Castle, Golden Temple, Gate To
  Future, Twilight Perion, Knight's Stronghold, Empress Cygnus, Arkarium, Hilla, Magnus, Mori
  Ranmaru, Dragon Rider"; "All classic PQs working, Boss Points, **Custom Dojo**, Resurrection of the
  Hoblin King, Ghost Ship."
- Cash Shop exists — "Tickets can be bought in **Cash Shop** or point shops", gachapons for
  chairs/mounts/pets/damage skins. `[FACT]`

`[INFERENCE]` The union of the two lists is a fair working definition of "v92 feature-complete as
practised". Both have Evan + Dual Blade fully working; both keep Dojo; DreamMS keeps Monster
Carnival. Neither lists anything as missing-and-hard, so no v92 feature is flagged by them as
defeated. **Shoulderpads and Kataras** (Kaizen) are worth noting as v88 Dual-Blade-era equip slots
that Cosmic v83 will not have.

---

## 6. Q7 — What actually broke in a real v83→v92 migration

This is the most directly useful artifact in the whole ticket for phase 2, and it is *their own
changelog* — a de-facto post-mortem. `[FACT — https://forum.dream.ms/threads/changelog.9/, via
https://web.archive.org/web/2024/https://forum.dream.ms/threads/changelog.9/]` Verbatim entries:

- "Fixed **Carly's Uniform not being a cash item after v92 upgrade**"
- "Fixed being able to apply Dragon Stone on Chaos Horntail Necklace **since v92 update** (affected
  items have been altered)"
- "Fixed **incorrect hp for Red/Blue Rex Earrings since v92 update**"
- "Fixed new characters created **[since v92 launch] missing 8 AP** (added 8 AP to affected characters)"
- "Fixed **Power Up, Magic Up, Power Guard Up, Magic Guard Up mob debuffs not functioning since
  upgrading to v92**"
- "Fixed **item indicators from v83 not showing**"
- "Fixed **mob elemental resistance not synced with v83 server**"
- "Added client support to handle **inlink/outlink in wz files**"

`[INFERENCE]` Pattern: the breakage was **not** protocol or opcodes — it was **item/stat/WZ data
drift**. Cash-item flags, equip stat blocks, starting AP, mob debuff/elemental tables, and WZ
`inlink`/`outlink` indirection. The line *"not synced with **v83 server**"* strongly suggests they ran
a **v92 client against a still-v83-era server data set** for a period and chased desyncs — i.e. they
did a client-first migration, exactly the shape of the owner's ticket-17 route analysis.

`[INFERENCE — plan input]` **WZ `inlink`/`outlink` support is a named, concrete requirement.** v84+ WZ
files use indirection references that a v83-era pipeline may not resolve. Put it on the WZ merge
checklist (`docs/work-plan/WZ-MERGE-PROCEDURE.md`).

Other v92-era DreamMS threads worth pulling if phase 2 activates:
`v92-party-quest-rework.8821`, `the-ultimate-dreamms-evan-thread-of-justice.9080`, `dual-blade.8167`,
`universal-pq-map-party-quest-zone-improvements.9676`, `pq-orbis-party-quest.9806` — all under
`https://forum.dream.ms/threads/<slug>/`, readable via Wayback (live forum is Cloudflare-gated).

**Abandoned attempts / failures:** **no sources found.** I found no thread or guide documenting a
failed v83→v9x migration. DreamMS's is the only completed one I located.

---

## 7. Q5 — v92 opcodes (phase 2, parked)

`[FACT]` `Chronicle20/Vertisy` is a public v90/v92 Java source with `net/SendOpcode.java` (386 lines)
and `net/RecvOpcode.java` (250 lines) — against Cosmic's 366 / 216. It also carries
`tools/MaplePacketCreator.java` and per-screen packet builders (`CLogin`, `CStage`, `CUserPool`,
`CWvsContext`, `CCashShop`), which is the PacketCreator equivalent the brief asked for.

`[FACT]` For v84 specifically, the three protocol constants the client bypass depends on
(`VERSION_HEADER`, `PLAYER_LOGGED_IN`, `CLIENT_START_ERROR`) were **verified identical to v83**
(§3.5), so no opcode remap is indicated for phase 1.

I did **not** collect or transcribe any opcode values — per the brief, no speculation about opcode
values or addresses. The v95 leaked IDB question was **not** investigated (timeboxed after the
re-target); `gms-83-dll` references "the v95 PDB" as a secondary RE reference, which implies symbol
data for v95 is available to that project. `[FACT — prd.md wording]`

---

## 8. Recommendations

1. **Phase 1 client:** pull `Chronicle20/gms-83-dll` and build (or take the prebuilt
   `release-GMS-84.1.zip`) for `GMS 84.1`. That gives ijl15 proxy + `bypass` + `redirect`
   (127.0.0.1:8484) on a real v84 client with no new anti-cheat work.
2. **Budget the HD port.** Ezorsia's resolution edits are v83-only and do not exist for v84. Copy
   Kaizen's `settings.ini` model. This is the only regression the client move creates.
3. **Read `docs/tasks/task-006-gms-v84-support/{struct-verification,signature-catalog,risks}.md`**
   before any client work — they enumerate the real v83↔v84 struct divergences.
4. **Add WZ `inlink`/`outlink` handling to the WZ merge procedure** — named explicitly as a v92
   migration requirement by DreamMS, and likely to bite at v84 too.
5. **Do not plan around DreamMS or Kaizen source.** Use their feature lists (§5) as the phase-2
   acceptance spec and DreamMS's changelog (§6) as the phase-2 risk register. Nothing more.
6. Monster Carnival and Mu Lung Dojo survive on a live v92 server — the owner's "keep deleted
   content" goal is validated by precedent.

---

## Appendix — research notes

- `WebSearch` was budget-exhausted at the start of this ticket; Bing/DDG returned nothing usable for
  these terms. All findings came from **direct HTTP fetches, the Wayback CDX/replay API, and the
  authenticated GitHub API**. Where a live site is Cloudflare-gated (`forum.dreamms.gg`), quotes are
  from Wayback snapshots with the snapshot timestamp in the URL.
- Nothing was downloaded, extracted, installed or executed. `D:\games\MapleStory\` was not touched.
- Not chased (timeboxed after the re-target): the DreamMS `features.366` and full `changelog.9`
  threads beyond the greps above; the v95 leaked-IDB question; `v3921358/ZeroMS085` triage;
  the KaizenMS Discord.
