# 11c — Evan skill-window resource completeness audit

Status: **DONE — root-cause candidate found.**
Tool: `docs/wz-baseline/tool-census` (`WzCensus.exe`), new modes `icons`, `shape`, `strskill`,
`decode`, `links`, `dump`, `find`, `ls`. No new WZ parser.
Files audited: `D:\games\MapleStory\Skill.wz`, `D:\games\MapleStory\UI.wz`,
`D:\games\MapleStory\String.wz`, backup `Server\_backup\client-v83-EzorsiaV2-2026-08-15\`,
v84 stock `Server\porting-resources\wz-data\v84\`. Nothing under `D:\games\MapleStory\` modified.

---

## THE FINDING — `UIWindow.img/Skill/Tab` tops out at 5 tabs; Evan needs 11

| node | present indices | count |
|---|---|---|
| `UI.wz/UIWindow.img/Skill/Tab/enabled/<n>` | 0,1,2,3,4 | 5 |
| `UI.wz/UIWindow.img/Skill/Tab/disabled/<n>` | 0,1,2,3,4 | 5 |
| `UI.wz/UIWindow.img/Skill/Tab/AranButton/Bt<d>` | **Bt1,Bt2,Bt3,Bt4** — no Bt0, no Bt5+ | 4 |
| `UI.wz/UIWindow.img/SkillEx/Tab/enabled/<n>` | 0..10 | **11** |
| `UI.wz/UIWindow.img/SkillEx/Tab/disabled/<n>` | 0..10 | **11** |
| `UI.wz/UIWindow.img/SkillEx/…/AranButton` | **does not exist** | 0 |

Job-chain lengths, from the image sets actually present in `Skill.wz`:

| job | chain | length | Skill/Tab can serve it? |
|---|---|---|---|
| 0 (Explorer beginner) | 0,100,110,111,112 | 5 | yes (0..4) — **opens, confirmed live** |
| 2000 (Aran) | 2000,2100,2110,2111,2112 | 5 | yes (0..4) — **opens, confirmed live** |
| 2001 (Evan) | 2001,2200,2210,2211,2212,2213,2214,2215,2216,2217,2218 | **11** | **NO — indices 5..10 absent** |

`SkillEx/Tab` having exactly 0..10 is the corroboration: v84 ships a *separate* window
(`SkillEx`) sized for Evan's 11 tabs, and it carries **no** `AranButton`. The v83 `Skill` row
was never widened.

This fires during **tab construction**, before any skill row is drawn — which is exactly the
observed behaviour: `!job 2000` opens the window with **zero** skills listed and does not crash,
so the per-skill draw path is not involved; `!job 2001` would also list zero skills and still
crashes.

The binary formats `UI/UIWindow.img/Skill/Tab/AranButton/Bt%d` (string @ `0x00B3B690`,
referenced from `0x008AD6E1`). For an 11-entry chain that formats `Bt5`..`Bt10` (and possibly
`Bt0`), none of which resolve.

### Additive fix — nodes to ADD (nothing deleted, nothing overwritten)

| node to ADD | donor (copy from) | note |
|---|---|---|
| `UI.wz/UIWindow.img/Skill/Tab/AranButton/Bt5` … `Bt10` | `UI.wz/UIWindow.img/Skill/Tab/AranButton/Bt4` | copy whole subtree: `normal/0`, `pressed/0`, `disabled/0`, `mouseOver/0`, each `34x18 f1` + `origin` |
| `UI.wz/UIWindow.img/Skill/Tab/AranButton/Bt0` | same (`Bt1`) | belt-and-braces; only if the index is 0-based |
| `UI.wz/UIWindow.img/Skill/Tab/enabled/5` … `10` | `UI.wz/UIWindow.img/SkillEx/Tab/enabled/5` … `10` (correct Evan glyphs, already in the live file) — fallback `Skill/Tab/enabled/4` | |
| `UI.wz/UIWindow.img/Skill/Tab/disabled/5` … `10` | `UI.wz/UIWindow.img/SkillEx/Tab/disabled/5` … `10` — fallback `Skill/Tab/disabled/4` | |

`AranButton/Bt1..Bt4` payload hashes (SHA256[:16] of the stored compressed bytes) — `Bt4` is a
real distinct glyph, so it is a valid donor:

```
Bt1 normal/pressed/disabled B2980422DFC7074C   mouseOver 6615B47C584AECB8
Bt2 normal/pressed/disabled B409E181D4282E71   mouseOver D8886CCD820DA993
Bt3 normal/pressed/disabled E92C72FB639369DF   mouseOver D1A8C9A0267A98B7
Bt4 normal/pressed/disabled 78B6AC7D3EC9A41B   mouseOver 5ADC507E179A5A7C
```

Caveat: the added glyphs will read "4th job" on tabs 5-10 until real art is drawn. That is a
cosmetic ceiling, not a crash. If the window still crashes with these added, the tab count is
being read from somewhere else and the investigation goes back to the binary.

### The merge did NOT disturb `Skill/Tab` — verified, not assumed

Full 6-level dump of `UIWindow.img/Skill`, canvases hashed by stored compressed bytes:

```
LIVE  vs  _backup\client-v83-EzorsiaV2-2026-08-15\UI.wz : IDENTICAL (zero differing lines)
LIVE  vs  porting-resources\wz-data\v84\UI.wz           : IDENTICAL (zero differing lines)
```

v84 **stock** also has only `AranButton/Bt1..Bt4` and no `EvanButton`. Name search under
`UIWindow.img` for `Button`: 2 hits in both live and v84 stock
(`Skill/Tab/AranButton`, `UserList/Friend/buttonbg`). Search for `Evan`: **0 hits** in both.
So there is no Evan tab-button art anywhere to donate — `Bt4` is the only honest donor.

---

## CLEAN NEGATIVES — everything else the window reads is complete

### 1. Icon completeness, `Skill.wz/<job>.img/skill/<id>/`

Every canvas was **inflated to a bitmap**, not merely counted.

| image | skills | `icon` | `iconMouseOver` | `iconDisabled` | `iconMouseOverDisabled` | undecodable |
|---|---|---|---|---|---|---|
| **2001.img (Evan)** | 27 | 27/27 | 27/27 | 27/27 | **0/27** | **0** |
| 000.img (job 0 — the job proven to work) | 25 | 25/25 | 25/25 | 25/25 | **0/25** | **0** |
| 2000.img (Aran) | 28 | 28/28 | 28/28 | 28/28 | **0/28** | **0** |
| 1000.img | 26 | 26/26 | 26/26 | 26/26 | **0/26** | **0** |
| 2200 / 2210-2218 | 2,2,2,2,2,4,4,4,5,4 | all | all | all | **0 everywhere** | **0** |

**Is any icon variant ever absent in stock v83?** Yes — `iconMouseOverDisabled` is absent on
**100% of skills in every image checked, including the working controls**. The v83 renderer
therefore cannot be relying on it. `icon` / `iconMouseOver` / `iconDisabled` are 100% present
and 100% decodable everywhere, Evan included. **2001.img matches the working control exactly.
No donor needed.**

All icons `32x32`, format 1 (ARGB4444), except `000.img/skill/0000100/*` at format 2 (ARGB8888).

### 2. Structural shape, `skill/<id>` direct children

Universal child set is **identical** in 2001.img and 000.img: `{icon, iconDisabled,
iconMouseOver, level}` — nothing else reaches 100% in either. Every child NAME used in 2001.img
(`invisible, effect, disable, effect0, timeLimited, info, action, affected, mobCode, repeat,
hit, screen`) also appears in 000.img. Image-root children identical
(`info:WzSubProperty, skill:WzSubProperty`), and `2001.img/info/icon` is `26x30 f1` with
`origin (-4,30)` — byte-for-byte the same shape as `000.img/info/icon` and `2000.img/info/icon`.

### 3. `skill/<id>/level/<n>` child sets

2001.img has 12 distinct level shapes, 000.img has 15. Every level-child NAME in 2001.img is a
subset of 000.img's (`hs, x, y, z, mdd, pdd, mpCon, time, cooltime, speed, ball, fixdamage, hit,
damage, damagepc, lt, rb, mobCount`) — **zero new names**. Level numbering is contiguous `1..N`
on every skill in every image; no gaps, no non-numeric names. Empty level child sets exist in
2001.img (`20011003/1`) *and* in the working 000.img (`0000008/1`, `0001003/1`, `0001006/1`).

Property-type census (whole image, deep) — 2001.img introduces no type the control lacks:

```
2001.img  Canvas=689 Int=1050 String=59  Sub=167 Vector=695   fmt1=689
000.img   Canvas=510 Int=834  String=62  Sub=152 Vector=516   fmt1=507 fmt2=3
2000.img  Canvas=559 Int=883  String=62  Sub=180 Vector=581   fmt1=556 fmt2=3
```

No `WzUOLProperty`, no `WzLuaProperty`, no leading-underscore names anywhere in the Evan images.

### 4. Links — UOL / `_inlink` / `_outlink`, dangling and cyclic

| scope | links | broken (dangling **or** cyclic) |
|---|---|---|
| `Skill.wz` 2001, 2000, 000, 2200, 2210-2218 | **0** | 0 |
| `UI.wz/UIWindow.img/{SkillEx, SkillMacroEx, Skill, SkillMacro, MacroSkill, SkillUp, AranSkillGuide}` | 48 | **0** |
| `UI.wz/UIWindow.img` (whole) + `Basic.img` + `StatusBar.img` | 186 | **0** |

The link walker follows chains up to 64 hops with a visited-set, so a cycle is reported as
`CYCLE(back to …)` — none occurred. The only links in the skill-window area are the 48 inside
`SkillEx/Dragon/{4,6,8,10}/<n> -> ../{3,5,7,9}/<n>`, all resolving.

### 5. Canvas decode sweep

| scope | canvases | bad |
|---|---|---|
| `Skill.wz` 2001 + 000 + 2000 + 2200 + 2210-2218 | **2273** | **0** |
| `UI.wz` SkillEx + SkillMacroEx + Skill + SkillMacro | **254** | **0** |
| `UI.wz` Skill/Tab, Skill/Tab/AranButton, SkillEx/Tab, Skill/BtSpUp, SkillEx/{Dragon,egg,Glow} | 156 | **0** |

"Bad" = null PNG property, decode returned null, decode threw, or decoded dimensions disagreed
with the declared `width`/`height`. Zero of any kind.

### 6. `String.wz/Skill.img` rows (the name/desc the window draws)

Every skill id in 2001.img, 000.img, 2000.img and 2200/2210-2218 has a `String.wz/Skill.img/<id>`
row, and every row has a `name` child. `noStringRow=0`, `noNameChild=0` in all 13 images.
`desc` and `name` are universal; `h1..h20` follow level counts.

Level `hs` values that name a missing String child exist — and exist **in the working control
too**: 2001.img 3 cases (`20019000/1`, `20019001/1`, `20019002/1` → `h1`), 000.img 5 cases
(`0001014`, `0001015`, `0009000`, `0009001`, `0009002`), 2000.img 8 cases. All on
`invisible=1` skills. Stock behaviour, not a delta.

---

## Provenance note

`2001.img`, `2200.img`, `2210-2218.img` are **absent** from the v83 backup Skill.wz — they are
purely merged-in v84 images. `000.img`, `2000.img`, `221.img` are the only relevant images the
backup already had. `UIWindow.img/SkillEx` and `/SkillMacroEx` are appended after `SlideMenu` in
the live UI.wz and absent from the backup — merged in, and they left `UIWindow.img/Skill`
byte-identical.
