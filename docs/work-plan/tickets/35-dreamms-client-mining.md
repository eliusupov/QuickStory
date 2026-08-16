# 35 — mine the DreamMS v92 client for what it can actually teach us

**What to build:** a written intel report that tells this project which of DreamMS's techniques we
can reuse, which of its content we must not touch, and what it proves about the road ahead.

**Blocked by:** None — can start immediately. Read-only against a finished download.

**Status:** ready-for-agent

## What landed

The owner installed DreamMS to `D:\games\dreamms` — a complete ~7.6 GB v92-era client, finished
downloading 2026-08-16. DreamMS is one of only two known working v92 servers with **both Evan and
Dual Blade** (ticket 18). It does not publish source, so this is the client only.

Notable files, from a first inventory:

```
DreamMS.exe            8,802,304    the client
DreamMS.dll           13,667,344    the custom client mod — where the QoL/rendering work lives
ijl15.dll                356,352    Feb 2023 — the proxy DLL
DefaultSettings.reg       10,130    the full client feature surface, as registry defaults
version.data                  24    base64, 24 B
release.sequence               2    "13"
discord_game_sdk.dll   3,154,744
eTracer.aes / v3hunt.dll / suipre.dll / nmcogame.dll   stock AhnLab/nProtect, dated 2010
```

WZ archives are **split**: `Character.wz` + `Character01.wz` + `Character02.wz`,
`Map.wz` + `Map01.wz` + `Map02.wz`, `Skill.wz` + `Skill01.wz`.

## Hard boundaries — read before touching anything

1. **Do not run `DreamMS.exe`, `DreamLauncher.exe`, or anything under `D:\games\dreamms`.** This is
   read-only archaeology. The owner's v83 server is live and his v84 client work is mid-flight.
2. **Do not install the anti-cheat.** `eTracer`/`v3hunt`/`suipre`/`nmcogame` install system-wide.
3. **Do not copy DreamMS's custom content into our server or our WZ.** Their expanded art, items,
   maps and UI are *their* work, not Nexon's stock data, and this project does not ship other
   people's custom content. **Techniques, formats, mechanisms and measurements are fair game;
   assets are not.** If you are unsure which side of that line something falls on, it is an asset.
4. Do not write anything under `D:\games\MapleStory\` or `D:\games\dreamms`. Put findings in the
   worktree.
5. Branch `worktree-evan-dualblade`, never master. Never bare `git stash`. Other agents are working
   this worktree concurrently — stage only your own files, never `git add -A`.

## The questions worth answering, roughly in value order

### 1. Is this stock v92, or heavily modified? (answer this FIRST — everything else depends on it)

Stock v84 `Character.wz` is 192 MB. DreamMS ships **2.2 GB across three Character archives**, and
`Mob.wz` at 1,040 MB against stock v84's 497 MB. That is not a rounding difference.

**Establish whether this client is usable as a "what v92 shipped" reference at all.** The project
has already been burned once by treating a fan artefact as authoritative (`sendops-92.properties`
turned out to be byte-identical to v90). If DreamMS's data is heavily customised — and the sizes
say it is — then say so plainly, and this client is a **technique** reference, not a **data**
reference. That single finding is worth more than everything below it.

`Server\porting-resources\clients\GMSSetupv92.exe` is the stock v92 installer, already on disk, and
is the correct data reference if one is needed later. You do not need to extract it for this ticket.

### 2. The split-WZ scheme — this one may pay off soon

`Character01/02`, `Map01/02`, `Skill01` are not a stock layout. Work out **how the client is told to
load them and how lookups resolve across archives** (order? overlay? does `01` shadow the base?).

Why it matters now, not in phase 2: this project's merges are **additive by policy**, so our
archives only ever grow. If there is a size ceiling on a `.wz`, we will meet it, and this is a
working demonstration of the way past it. Record the mechanism.

### 3. Resolution, widescreen and the HD phase

The owner's Ezorsia V2 HD mod is **v83-only** and is lost on any client move — this is the one
accepted regression of the v84 migration, and the owner has said HD comes last. So a working
example is valuable.

`DefaultSettings.reg` exposes `resolution`, `screen_mode`, `fullscreen`, `vsync`, `align_ui_left`,
`centered`. Find where `DreamMS.dll` consumes them and characterise the approach: is the resolution
change a client-side render-target change, a UI re-layout, upscaled art, or some combination?
Compare with what we know of Kaizen's `settings.ini`-driven model and with Ezorsia's
`dinput8.dll` + Detours proxy. **We want the cheapest model to copy later, named and justified.**

### 4. `ijl15.dll` — corroboration of a correction we just made

Ticket 20 corrected the project's belief that the v84 bypass loads as a `dinput8.dll` proxy: it is
actually **`ijl15.dll` + an `edits\` folder** (`dinput8.dll` is the Ezorsia lineage). DreamMS ships
an `ijl15.dll` too, dated Feb 2023.

Compare DreamMS's against the one in `D:\games\MSv84\bypass\` — same lineage, or independent? What
does each export? This is a cheap, direct check on a correction that is now load-bearing, and an
independent confirmation would let us stop hedging it.

### 5. Server routing

How does this client find its server — hosts file, packed config, hardcoded IP, a Winsock hook like
`gms-83-dll`'s `redirect.ini`? Our v84 route uses a `WSPPROC_TABLE` hook and it works; a second
independent example tells us whether that is the norm or a lucky choice.

### 6. The feature surface — treat this as a menu, not a task list

`DefaultSettings.reg` is effectively a specification of what a modern v92 private client offers:
extended quickslot, expanded inventory, chat plus, debuff/cooldown/party-buff rows, damage skins,
per-entity opacity, auto-hide sidebar, Discord RPC, and Evan-specific ones —
`dragon_roar_autopot`, `dragon_opacity`, `lv300_dragon`.

Extract the **full list**, and for each mark whether it is plausibly **client-side only** or needs
**server support**. The owner wants feature-completeness and this is a ready-made catalogue of what
players expect. It is intel for later prioritisation, **not** a mandate to build any of it.

Note the Evan entries specifically: they are an existence proof that Evan's dragon works properly on
a v92 client, which is relevant to ticket 14's dragon-evolution work.

### 7. Anything about Dual Blade

Phase 2, deferred, but if the client reveals anything cheap about how Dual Blade is presented
(character creation, the katara slot, UI), record it. Do not go digging for it at the cost of the
questions above.

## The standing lesson — it applies here more than usual

**PROVE THE INSTRUMENT BEFORE TRUSTING THE MEASUREMENT.** Three of this project's wrong conclusions
in one day came from broken instruments, not bad reasoning: a client binary that could not run, a
debug trap that caused the crash it was meant to catch, and a merge tool that corrupted an array and
then passed its own guard. Ticket 20 then **rejected its own** PE dump-detector because it flagged
`notepad.exe` as suspect.

You are reading a 13 MB DLL and 7 GB of archives with tools that will happily produce confident
nonsense. Whatever you use to read WZ or PE structure, first point it at something whose answer you
already know and confirm it gets it right. Say in the report which instruments you proved and how.

Specific known trap: **7-Zip silently truncates output when it sees only the first of two spanned
cabinets** — that is how `GMSSetupv84.exe` nearly cost us `Mob.wz`. Assume your extractor lies until
it demonstrates otherwise.

## Acceptance criteria

- [ ] Question 1 answered unambiguously: is this a usable v92 **data** reference, or technique-only?
- [ ] Split-WZ loading mechanism explained well enough to reuse
- [ ] Resolution/widescreen approach characterised, with a recommendation for the HD phase
- [ ] `ijl15.dll` compared against the v84 bypass copy — same lineage or not
- [ ] Server-routing mechanism identified
- [ ] Full feature list from `DefaultSettings.reg`, each marked client-side or needs-server
- [ ] Every instrument used is named, and how it was proved is stated
- [ ] Nothing executed, nothing installed, nothing copied out of `D:\games\dreamms` beyond small
      config/text artefacts needed as evidence
- [ ] Anything you could not determine is listed as unknown — no confident guesses

## Deliverable

`docs/work-plan/35-dreamms-client-mining-results.md`, and a "Delivered" section appended here.
Report failures with real output. The orchestrator will independently verify the load-bearing
claims, so under-reporting is much cheaper than a false pass.

## Priority

**This is phase-2 and HD-phase intel. It must not slow v84 down.** If a question turns into a
multi-hour reverse-engineering project, stop, record what you know, and mark the rest unknown. The
report is the deliverable — not a complete understanding of someone else's client.

---

## Delivered

**Report:** `docs/work-plan/35-dreamms-client-mining-results.md`
**Instruments (reusable, in-repo):** `docs/work-plan/tools/wzlist/` (C#/MapleLib WZ structure
lister), `docs/work-plan/tools/pe.ps1` (PE export/import reader), `docs/work-plan/tools/strings.ps1`.

Nothing under `D:\games\dreamms` was executed, installed, modified or copied out. No asset was
copied anywhere — only measurements, header fields, node names and sizes.

### Headline

**Q1: heavily modified. TECHNIQUE reference only — NOT a v92 data reference.** The binary is
genuinely v92 (`Base.wz` VERSION 92; `c:\ACGame_GL\Release_0092\Bin\MapleStory.pdb` left in
`DreamMS.exe`), but the WZ content is not: `Npc.wz` holds 7,413 images vs stock v84's 1,662 and
includes `9977777.img` plus a bulk block `9906000`–`9906599` (600 entries, 403 B each);
`Skill01.wz` is a parallel skill archive with an identical image-name set and much larger content;
`UI.wz` carries post-Big-Bang `StatusBar3.img`/`UIWindow2.img`. Do not cite this client for item
IDs, mob stats, skill data or strings — `GMSSetupv92.exe` stays the only authoritative source.

### Per-question

| Q | Result |
|---|---|
| 2 split-WZ | Discovery **solved and reusable**: `Base.wz` empty top-level dirs + `%s.wz` → one dir entry + one file, no client patch. Split is **disjoint subtree relocation** (`Character01`=Weapon, `Character02`=TamingMob, `Map01`=Back, `Map02`=Obj+Tile), plus `Skill01` as an alternate archive toggled by `use_skill01`. **How stock-form paths re-root into the numbered archive is UNKNOWN** — no `Character01`/`Map01` literal in either binary, and `DreamMS.dll` is Themida-packed. |
| 3 resolution | Implementation **UNKNOWN** (Themida). Established: MinHook 1.3.3, direct `d3d9`+`d3dx9_43` imports, and a shipped default window layout requiring ≥1522×832 (so `resolution=0` is a 1600×900-class default, not 1024×768). **Recommendation: MinHook + our existing `ijl15.dll`+`edits\` loader — cheapest, reuses everything we already have. Not Detours, not a new proxy.** |
| 4 `ijl15.dll` | **Ticket 20 confirmed; stop hedging.** DreamMS's copy is Nexon's own `ijl15.dll` with all five PE sections SHA-256-identical to our `ijl15.dll.bak`, plus one appended `.NewIT` section holding a rebuilt import directory that adds `DreamMS.dll!?MainThread@@YGHXZ`. Ours is a from-scratch MSVC-2015 rewrite with an extra `LoadDLLsFromDirectory` export. **Same host DLL, same base file, zero shared implementation** — a genuine independent data point. |
| 5 routing | **In-binary byte patch**, not a Winsock hook: three consecutive `char[16]` slots in `DreamMS.exe` (`0x735960/70/80`) all set to `127.0.0.1` — same cardinality and layout as the stock login array our `redirect.ini` names (`63.251.217.2/.3/.4`). **Our `WSPPROC_TABLE` hook is the more sophisticated option and should stay.** How their live endpoint is supplied at runtime is UNKNOWN. |
| 6 features | Full catalogue extracted (48 root keys + 13 subkeys), each marked client-side / needs-server / both. Needs-server: `party_buff_row`, `extended_quickslot`, `inventory_expanded`, `Expedition`, family/merchant notifications; both: damage skins, `MonsterInfo`. |
| 7 Dual Blade | Skill imgs `430`–`434` present; 35 katara images (`01342xxx`) in `Character01.wz/Weapon`. Creation-UI presentation not investigated (ticket priority). |

### For ticket 14 (Evan dragon)
`dragon_roar_autopot`, `dragon_opacity` and `lv300_dragon` ship as defaulted-on client settings —
existence proof that Evan's dragon renders correctly on a v92 client through the final evolution
stage. That is a client fact, independent of DreamMS's customised data.

### Instruments proved before use
- `WzList` → run on our own `D:\games\MSv84\client\Base.wz` first; returned `VERSION 84`, `IV GMS`,
  `PKG1`, and exactly the 15 known stock mounts. Cross-checked on v84 `Character.wz`.
- `pe.ps1` → run on `C:\Windows\SysWOW64\version.dll`; returned all 17 documented exports with
  correct ordinals *and* correctly flagged the two KERNEL32 forwarders. It also exposed a trap:
  that DLL's PE timestamp field reads as year 2097, so timestamps are only trusted where
  corroborated by content hashes.
- `strings.ps1` → run on `ijl15.dll` and cross-checked against `pe.ps1`'s independently parsed
  export/import tables; both agreed.
- No archive was extracted, so the spanned-cabinet truncation trap was never in play.

### Biggest limitation
`DreamMS.dll` is Themida-packed (`.themida` 7,168,000 virtual / 0 raw). Unknowns 1, 2 and 3 above
all trace to that, and resolving them means either running the client (forbidden) or unpacking
Themida (multi-hour). Stopped per the priority rule. Full unknown list is in the report.
