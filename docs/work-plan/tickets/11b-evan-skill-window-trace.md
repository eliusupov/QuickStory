# Ticket 11b — Evan (job 2001) skill-window crash: static trace of the open+draw path

Binary: `D:\games\MapleStory\local.exe` (unpacked memory dump, file offset == RVA, ImageBase
0x400000, ASLR off). All VAs below verified by disassembly of that image.

## TL;DR

- The **tab-strip hypothesis is dead.** Proven below, not asserted.
- The **Aran special-case tab never runs for Aran-beginner either**, so the owner's `!job 2000`
  test did not exercise it. It is not the discriminator the dispatch assumed.
- The client's Skill.wz loader has **no job filter**. Hypothesis (b) is dead.
- The **first thing that touches the root value on window open** is
  `CSkillInfo::GetSkillsByRoot` @`0x0075C7C5`, and it contains an **unguarded NULL deref at
  `0x0075C7E2`** that fires exactly when a root has no `<root>.img` registered. This is the only
  root-value-keyed code that runs before anything is drawn.
- I could **not prove** that root 2001 is the missing one (2001.img *is* installed, 4003 KB), so I
  am **not** claiming this is the fault. Ticket 11 made that mistake; I am not repeating it.
- The dispatch's "job 2001 lists zero rows too" inference **holds** — 2000.img and 2001.img are
  structural twins in the installed WZ (§5), so the icon/row draw is genuinely out of scope.
- After the two patched gates, **no remaining code in the skill path treats 2001 differently from
  2000** (full inventory in §5b). That negative is the real result of this ticket.
- Deliverable that actually ends this: a **14-byte in-place tracer on `_com_issue_error`
  @`0x00A5FDE4`** that freezes the client at the throw and hands us the exact faulting VA + HRESULT.
  Spec is at the bottom. Run it, and there is nothing left to guess.

---

## 1. Anchors — verified

### `CUISkill::SetSkillRoots` @ `0x008AD238` — confirmed

```
008AD285  call 0x4a8c4f            ; GetSkillRootList(job, &this->roots)
008AD299  mov  eax, ecx            ; job
008AD29B  push 0x64 / cdq / idiv   ; eax = job/100
008AD2A1  mov  esi, 0x7d1          ; 2001
008AD2A6  cmp  eax, 0x16           ; == 22 (Evan 2200-2218)  -> keep esi = 2001
008AD2A9  je   0x8ad2c1
008AD2AB  cmp  ecx, esi            ; job == 2001             -> keep esi = 2001
008AD2AD  je   0x8ad2c1
008AD2AF  ...  esi = (job/1000)*1000                          ; every other job
008AD2C1  push 0 / call 0x474c2e   ; InsertAt(0)
008AD2CB  mov  [eax], esi          ; roots[0] = esi
```

Root lists actually produced:

| job  | `GetSkillRootList` @0x004A8C4F | prepended | final |
|------|-------------------------------|-----------|-------|
| 0    | `{}` (0x4A8C7A: `(job%1000)/100 == 0` -> bail) | 0 | `[0]` |
| 2000 | `{}` (same bail) | 2000 | `[2000]` |
| 2001 | `{}` (same bail) | 2001 | `[2001]` |

**All three are a one-element list.** That matters for §2.

### `GetSkillRootList` @ `0x004A8C4F` — confirmed returns `{}` for 2001

`eax=job/1000`, `ecx=(job%1000)/100`; `test ecx,ecx / je return` at `0x004A8C78`. For 0, 2000 and
2001 alike, `ecx == 0`, so the list comes back empty. Correct as reported.

---

## 2. The tab-strip hypothesis is FALSE — hard evidence

`CUISkill::CreateTabs` is the SEH function starting at `0x008AD2D1` (called from
`CUISkill::UpdateSkills` @`0x008ACEB7` -> `0x008ACEC3`). Its tab loop:

```
008AD4A5  xor  edi, edi                    ; edi = tab INDEX
008AD4A7  mov  eax, [ebx+0x5b0]            ; roots array
008AD4AD  cmp  eax, esi(0) / je  0x8AD629  ; null-guarded
008AD4B5  cmp  edi, [eax-4] / jae 0x8AD629 ; index < count
008AD4C1  push edi                         ; <-- the INDEX, not roots[edi]
008AD4C5  push 0xaf2444                    ; ANSI "%d"
008AD4CA  push eax                         ; &buf
008AD4CF  call 0x445b4b                    ; sprintf(buf, "%d", index)
...       MultiByteToWideChar, then two WZ get_item()s keyed by that string
008AD623  inc  edi
008AD624  jmp  0x8AD4A7
```

**`roots[edi]` is never read inside this loop.** The roots array is used only for its element
count at `0x008AD4B5`. The tab resource name is the decimal **loop index**.

Therefore job 0, job 2000 and job 2001 all build the identical tab name `"0"` and both look it up
under the same two parent nodes (the `Tab/enabled` and `Tab/disabled` `IWzProperty`s resolved at
`0x008AD31D` / `0x008AD3F4`, resource ids 0xA6A / 0xA6B). **The tab strip cannot differ between the
three jobs.** Job 0 works, so the tab strip works.

### The AranButton block never runs for Evan *or* for Aran-beginner

```
008AD64A  mov  ecx, [0xbebf98]      ; local player
008AD650  cmp  ecx, esi / je 0x8AD759
008AD65A  call [eax+0x40]           ; -> job
008AD65D  mov  edi, 0x3e8 / cdq / idiv edi
008AD667  cmp  eax, 2               ; job/1000 == 2 ?
008AD66A  jne  0x8AD759             ; no  -> skip whole block
008AD678  call [eax+0x40] / cdq / idiv edi   ; edx = job%1000
008AD67E  push 0x64 / pop ecx / mov eax,edx / cdq / idiv ecx
008AD686  cmp  eax, 1               ; (job%1000)/100 == 1 ?
008AD689  jne  0x8AD759             ; no  -> skip whole block
008AD6E1  push 0xb3b690             ; L"UI/UIWindow.img/Skill/Tab/AranButton/Bt%d"
```

Gate = **job in 2100..2199 only.**

- job 2001 -> `2001%1000 = 1`, `1/100 = 0` -> **skipped**
- job 2000 -> `2000%1000 = 0`, `0/100 = 0` -> **skipped**
- job 2110 (real Aran) -> `110/100 = 1` -> taken

So `!job 2000` opening fine says **nothing** about the Aran tab resource — that job never asks for
it. The dispatch's "Aran works, so the special-case resource path works" inference does not hold.

### There is no Evan tab resource, and none is asked for

- `"UI/UIWindow.img/Skill/Tab/AranButton/Bt%d"` @`0x00B3B690` (UTF-16) is the **only** `img/Skill`
  path literal in the whole image.
- `"EvanButton"` — **absent** in both ASCII and UTF-16.
- `"AranButton"` occurs exactly once, at `0x00B3B6C4` (inside the string above).

No missing-node fix applies here, because nothing looks a missing node up.

---

## 3. Hypothesis (b) — a "third gate" in the Skill.wz loader — is FALSE

`CSkillInfo::LOAD` @`0x0075C060` enumerates every child of the Skill.wz namespace
(`0x0075C22D` iterator init, `0x0075C24C` next, loop head `0x0075C23C`). The only skips are, in order:

| VA | test | skips |
|----|------|-------|
| `0x0075C494` | `name == <res-string 0x15AF>` | one named node |
| `0x0075C4CF` | `name.Left(2) == L"MC"` @0x00AFE178 | `MCSkill.img`, `MCGuardian.img` |
| `0x0075C512` | `name == L"Ite"` @0x00AFE170 | (matches nothing) |
| `0x0075C56F` | `name.Left(2) == L"BF"` @0x00AFE168 | `BFSkill.img` |

Nothing job-based, nothing numeric. `2001.img` is not filtered.

Each surviving img goes to `CSkillInfo::LoadSkillRoot` @`0x0075C858`, which:

- takes `root = _wtoi(name)` (`0x0075C5EC`),
- drops any child skill whose `id/10000 != root` (`0x0075CB23`),
- and **unconditionally registers the root** in the map at `this+0x1C`
  (`0x0075CCA6  lea ecx,[eax+0x1C] / call 0x007672FC`).

So if `Skill.wz/2001.img` is present, root 2001 is in the map. (A subagent parsed the installed
`Skill.wz` root directory: 88 entries, **2001.img present, 4003 KB**, alongside 2200/2210-2218 and
a `Dragon` directory.)

---

## 4. What actually runs first on window open — and the unguarded NULL deref

`CUISkill::UpdateSkills` @`0x008ACEB7`:

```
008ACEBA  push 1 / call 0x008ADA59      ; <-- 1. refresh skill list from CSkillInfo
008ACEC3  call 0x008AD2D1               ;     2. build tabs
008ACECA  call 0x008AD790               ;     3. scrollbar
008ACED1  call 0x008AD7C9               ;     4. fill the 4 visible rows
```

Step 1 runs **before any UI is built and before any icon is drawn.** It is the only place the root
*value* is consumed:

```
008ADA67  mov  eax, [esi+0x5b8]     ; tab control
008ADA6D  mov  eax, [eax+0x3c]      ; selected tab index
008ADA70  mov  ecx, [esi+0x5b0]     ; roots array          (no null check)
008ADA78  mov  edi, [ecx+eax*4]     ; roots[index]         (no bounds check)
008ADAAC  call 0x0075C7C5           ; GetSkillsByRoot(root, pChar, &this->0x5EC)
```

`CSkillInfo::GetSkillsByRoot` @`0x0075C7C5`:

```
0075C7D8  call 0x0075C70A           ; GetSkillRootEntry(root)  -- plain map lookup
0075C7DD  mov  edi, [ebp+0x10]      ; out
0075C7E0  mov  esi, eax             ; entry  <-- MAY BE NULL
0075C7E2  mov  eax, [esi]           ; *** UNGUARDED DEREF ***
0075C7E4  mov  [edi], eax
```

`GetSkillRootEntry` @`0x0075C70A` *is* written to return NULL — it null-checks its own result at
`0x0075C736` before releasing it — and it has exactly **one** caller, the one above, which does
**not** check. That is a genuine defect on exactly this path.

It fires iff `roots[curTab]` has no `<root>.img` registered. **I have not proven that is true for
2001** — 2001.img is installed. Two ways it could still be the fault:

1. `[esi+0x5b8]+0x3c` (selected tab index) is stale/out of range on the first
   `UpdateSkills` — tabs are built *after* this call — so `roots[index]` reads past the
   1-element array and hands `GetSkillRootEntry` garbage, which misses the map and returns NULL.
   Job 0 and 2000 would hit the same path though, so this only explains the delta if the stale
   index differs, which I cannot show statically.
2. `LoadSkillRoot` bailed on 2001.img at runtime (v84 data through a v83 parser) and the
   registration at `0x0075CCA6` never happened. Cannot be shown statically either.

**I am not claiming either. See §6.**

---

## 5. The dispatch's "zero rows" inference holds — checked, and it survives

Row visibility is `CSkillInfo::IsSkillVisible` @`0x007619B0`:

```
visible = (learned(pChar, id) || entry->0x28 == 0)
       && (entry->0x38 == 0  || learnedLevel != 0)
```

`entry->0x28` and `entry->0x38` are each parsed as a **bool from a WZ int child** of the skill node
(`0x0075CF50`-`0x0075CFA2` and `0x0075D090`-`0x0075D0CB`). Neither patched gate affects this: the
entry comes straight out of the root's skill array (`0x0075C81B`) so it is never NULL and `GetSkill`
is never called (`0x007619C7 jne`), and the level comes from the character's learned-skill map at
`pChar+0x467`, not from `GetSkillLevel`.

I briefly thought this could put the row/icon draw back in scope, on the theory that 2001.img (v84
data) might expose rows that 2000.img does not. **A WZ dump of the installed Skill.wz kills that
theory.** 2000.img and 2001.img are structural twins:

- 28 vs 27 skills, same shapes skill-for-skill.
- Every skill in both has `icon`, `iconMouseOver`, `iconDisabled`, `level`.
- Exactly four skills in each lack an `invisible` child, and they are the direct counterparts:
  2000 -> `20001000/20001001/20001002/20000012`, 2001 -> `20011000/20011001/20011002/20010012`.
  Their icon canvases are **byte-identical** between the pairs.
- Neither image has `masterLevel` or `req` anywhere. All canvases decode (0 bad). Zero UOLs,
  zero `_inlink`/`_outlink`. String.wz has `name` + `desc` for all 27 of 2001's ids.
- `2001.img/info` contains exactly one child, `icon`, **bit-identical** to `2000.img/info/icon`.

Since the observed row count for job 2000 is zero and the two images are shape-identical, job 2001's
row count is zero too. **The crash happens with an empty skill list, during window construction.**
The dispatch's inference stands; deprioritise the icon/row draw as instructed.

(For the record, `CSkillInfo::GetSkillLevel` @`0x007616F6` hardcodes level 1 for Evan's
beginner-common ids `0x1315801`-`0x1315803` (20011009-20011011) and `0x0131580C` (20011020) at
`0x00761785`-`0x0076179F` — behind the very gate ticket 11 NOPed. That changes reported levels, but
`IsSkillVisible` does not consult `GetSkillLevel`, so it does not change the row set.)

## 5b. Complete inventory of every 2001/22 discriminator in the image

A full-image scan for the immediate `0x000007D1` found 60 sites. Every one that can be reached from
the skill path:

| VA | what it is | job 2000 | job 2001 | verdict |
|----|-----------|----------|----------|---------|
| `0x0075C776` | `CSkillInfo::GetSkill` gate | pass | reject | **patched by ticket 11** |
| `0x0076171D` | `CSkillInfo::GetSkillLevel` gate | pass | reject | **patched by ticket 11** |
| `0x004E8F1E` | classifier called from the icon draw at `0x008F2605` | returns 0 | returns 0 | **not a discriminator** — traced through `0x004E8F66`, which early-returns 1 for `root==2001` (`0x004E8F77`) and 1 for `root%100==0`; `0x004E8F04` then requires the result to be 9 or 10, so both jobs get 0 |
| `0x008AD2A6` | `CUISkill::SetSkillRoots` | prepends 2000 | prepends 2001 | enables Evan, does not reject it |
| `0x008BC76C` | identical `SetSkillRoots` in a **second** skill-ish UI class (its `GetSkillsByRoot` callers are `0x008B2CB2`, `0x008B4115`, `0x008B5877`, `0x008BCE4D`) | same | same | same |

Nothing else. And within `CUISkill`'s whole address range (`0x008A9000`-`0x008AE000`) the local
player object `0x00BEBF98` is read at only three sites: `0x008ABF03`, and `0x008AD64C`/`0x008AD672`
— the latter two being the Aran gate Evan skips (§2).

**So the only job-dependent state that reaches the skill window is the root list, and the only
consumer of the root VALUE is the map lookup at `0x0075C70A`.** Everything else — tabs, rows,
scrollbar — is index- and count-driven and identical for jobs 0, 2000 and 2001.

Worth flagging for whoever reads the tracer output: `2001 % 10 == 1`, whereas every other beginner
job id ends in 0. Any v83 routine doing the classic `job%10` advancement-digit arithmetic will read
2001 as "1st advancement of class 200", not as a beginner. If the fault turns out to be outside
`CUISkill`, that is the shape to look for.

---

## 6. What to do next — the tracer (recommended, do this first)

The dialog text `_com_error` / `"Unknown error 0x%0lX"` (`0x00B3D8E8`) means a **thrown C++
exception**, not a raw crash — which is why there is no Windows crash event. Every
`if (FAILED(hr)) _com_issue_error(hr)` in this binary funnels through:

```
_com_issue_error  @0x00A5FDE4  ->  0x00A605C3  ->  _CxxThrowException wrapper @0x00A60BB7
```

`_com_issue_error` has **22,687 call sites**, so hooking it catches whichever one throws.

### The patch (14 bytes, in place, no allocation)

| | |
|---|---|
| Name | `ComErrorTrap` |
| VA | `0x00A5FDE4` |
| local.exe offset | `0x0065FDE4` |
| Original | `6A 00 FF 74 24 08 E8 D4 07 00 00 C2 04 00` |
| Replacement | `89 25 EE FD A5 00 EB FE 90 90 00 00 00 00` |

Verified: the original 14 bytes are **unique** in local.exe (guard pattern is safe), and the next
function begins at `0x00A5FDF2`, so nothing outside the stub is touched.

Disassembly of the replacement:

```
00A5FDE4  89 25 EE FD A5 00   mov dword ptr [0x00A5FDEE], esp
00A5FDEA  EB FE               jmp  0x00A5FDEA        ; freeze here
00A5FDEC  90 90               pad
00A5FDEE  00 00 00 00         scratch (the dead tail of this same function)
```

Procedure:

1. Apply the two existing gates **and** `ComErrorTrap`.
2. Log in as the Evan character, open the skill window.
3. The client **hangs** instead of showing the dialog. That alone confirms the crash is a thrown
   `_com_error`.
4. Read `0x00A5FDEE` -> `esp`. Then `ReadProcessMemory(esp, 0x80)`:
   - `[esp+0]` = **the return address, i.e. the exact VA of the failing check**
   - `[esp+4]` = the HRESULT
   - filter the rest of the dump for values in `0x00400000..0x00A80000` for a poor-man's stack trace.
5. Report those and this ticket is closed.

If the client does **not** hang, the throw came from `0x00A60BB7` directly — the only two such sites
are `0x0075C61E` and `0x0075CC89`, both `E_FAIL` out of the Skill.wz loader — and that would mean
`LoadSkillRoot` is failing on `2001.img`, which is a WZ-data fix. Either outcome is decisive.

---

## 7. Fallback: defensive null guard at 0x0075C7E2

Only worth applying if the tracer points here. **This one cannot be done in equal length without
dropping something** — the brief asked me to say so explicitly, so: it needs 4 more bytes than the
9 available, and the least harmful 4 bytes to drop are `out->root = entry->root`.

| | |
|---|---|
| Name | `GetSkillsByRootNullGuard` |
| VA | `0x0075C7DD` |
| local.exe offset | `0x0035C7DD` |
| Original | `8B 7D 10 8B F0 8B 06 89 07` |
| Replacement | `8B F0 8B 7D 10 85 C0 74 6B` |

```
0075C7DD  8B F0        mov  esi, eax          ; entry
0075C7DF  8B 7D 10     mov  edi, [ebp+0x10]   ; out
0075C7E2  85 C0        test eax, eax
0075C7E4  74 6B        jz   0x0075C851        ; 0x0075C7E6 + 0x6B = 0x0075C851  (verified)
```

`0x0075C851` is the function's own epilogue (`pop edi / pop esi / pop ebx / leave / ret 0xC`) and
`esp` at `0x0075C7DD` is identical to `esp` immediately after the four register pushes, so the jump
is stack-safe. On the NULL path `out` keeps whatever it held; for `CUISkill` that is the
ctor-initialised empty list at `this+0x5EC`, and the downstream row loop null-checks the array at
`0x008AD8C0`. Result: the window opens with zero skills, exactly like job 2000.

**Behavioural drop:** `out->root` is no longer written on *any* call. A scan of the `+0x5EC`
references (`0x008AA627`, `0x008ADAA3`, `0x008ADAB3`, `0x008B9CF5`, `0x008BA1C2`, `0x008BAE3C`)
shows only address-taking; all consumers read `+8` (the array). Field 0 appears unread, but this is
the one thing about this patch that is not proven.

If the drop is unacceptable, the alternative is a trampoline: `VirtualAllocEx` an RWX page in the
target and repoint the `call` at `0x0075C7D8` (`E8 2D FF FF FF`) at a stub that calls
`0x0075C70A` and substitutes a pointer to 16 zero bytes when the result is NULL. There is **no
in-image code cave** to do this without an allocation — I scanned `0x00401000`-`0x00A70000` and
found zero runs of 0xCC or 0x00 of length >= 16.

---

## 8. Ruled out — do not re-derive

| claim | status |
|-------|--------|
| Tab strip / `Bt%d` / a missing tab canvas | **false** — tab name is the loop index; identical for jobs 0, 2000, 2001 |
| AranButton block is Evan's problem | **false** — gated to jobs 2100-2199; neither 2000 nor 2001 reaches it |
| A missing `EvanButton` UI.wz node | **false** — no such string, and nothing asks for one |
| A job filter in the Skill.wz loader ("third gate") | **false** — `0x0075C060` skips only MC*/BF*/two literals |
| Any other `cmp 2001` / `cmp 22` on the skill-UI path | none. Full-image scan for `0x000007D1` immediates found 60 sites; the only ones in the skill/UI path are the two already-patched gates (`0x0075C77B`, `0x0076171D`) and `SetSkillRoots` (`0x008AD2A1`), which *enables* Evan rather than rejecting it |
| `0x008AA04D` -> `0x008F2600` | **ruled out** — a row-draw frame, and §5 shows job 2001's row set is empty (2000.img and 2001.img are structural twins and job 2000 renders zero rows) |
| WZ data shape (missing node, bad canvas, dangling UOL, missing String.wz row) in 2001.img | **ruled out** — see §5; the images are twins, all canvases decode, zero links, String.wz complete |
| `CSkillInfo::LOAD` being lazy (i.e. Skill.wz parsed on window open) | **false** — `0x0075C060` has exactly one caller, `0x009F8C66`, on the startup path. The client boots, so 2001.img parsed without throwing |

## 9. Not pinned — and what that means

I did **not** pin the faulting VA, and I will not guess.

What §5b establishes is a genuinely useful negative: after removing the two patched gates, there is
**no remaining code in the skill-window path that behaves differently for root/job 2001 than for
2000**, and the WZ data for the two roots is a structural twin. Yet 2000 opens and 2001 crashes.
Exactly one of these must be true, and only runtime state can say which:

1. `GetSkillRootEntry(2001)` @`0x0075C70A` returns NULL at runtime despite 2001.img being
   installed — crash at `0x0075C7E2` (§4, §7 has the patch).
2. The fault is **outside `CUISkill` entirely**, in something else the window open touches that I
   have not identified.

§6 distinguishes them in a single run and hands over the exact VA. Do that before writing any more
analysis.
