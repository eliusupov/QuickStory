# How Ezorsia v2 gives assets their "HD treatment" — settled

**Verdict: there is NO per-asset HD pipeline. HD is pure runtime render-scaling done by a DLL
that patches EXE addresses. v84's new maps/assets get HD automatically at launch. The only
asset file is one OPTIONAL, hand-authored UI overlay.** Upstream README's claim is confirmed,
not diverged.

## A — pure render-scaling, not treated asset files
The HD effect is the exe-address patch set that changes window+canvas resolution and scales
component boundaries (upstream README l.56–57, 94, 96; wiki setup guide). It ships as
`client-hd\edits\hd-res-3.3.0.dll` + `hd-res.ini` (fitted formula `value = aW*W/2 + aH*H/2 +
aWH*W*H + k`, reads resolution at runtime). Login/cashshop "displays as 800x600, centered …
regardless of resolution" — vanilla art rendered larger, not upscaled art.

Decisive byte-compares (client-hd vs pristine v84 carve `porting-resources\wz-data\v84\`):
- `Map.wz` 628,959,453 B — **BYTE-IDENTICAL** (`cmp` clean).
- `UI.wz` 33,041,077 B — **BYTE-IDENTICAL**.
So the HD client ships stock v84 Map/UI; no map/sprite is redrawn or upscaled.

## B — the only asset override, and it's manual + optional
`EzorsiaV2_UI.wz` (1,370,002 B; present at root and in `client-hd\`, md5 differ so it's
hand-maintained, not regenerated). It is a user-authored stringpool-redirected UI overlay
loaded only when the file is present next to the exe (`verify.py:191`: flag forced TRUE when
`EzorsiaV2_UI.wz` present). Upstream README l.81: "do all your edits in your own
EzorsiaV2_UI.wz or MapleEzorsiaV2wzfiles.img". **No tool, list, or script produces it** — none
exists on disk in the fork, `client-hd\`, or `tools\hd\`.

## tools/hd — code addresses only, never assets
`tools\hd\` verifies/ports the v83 hardcoded HD addresses to v84 (`verify.py`: ADDR/SHAPE/
instruction anchors in the memory dump; `gen_loader.py` → C++ patch table). It writes an EXE
patch `.inc`, touches **zero** `.wz`/sprite/bitmap. Confirmed by grep.

## C — what v84 added that this affects (nothing needs per-asset work)
`add-list\Map.txt` = 601 new copy-roots (dragonRoad/dragonDream, Neo City, Slumbering Dragon
Is. etc.); `add-list\UI.txt` = 61 roots (Login RaceSelect BtEvan, DragonEquip, new MobGage
bars, evan char-select). All are stock v84 nodes already inside the byte-identical v84
Map.wz/UI.wz. They render HD at launch with no processing. The only per-content HD work is
**launch-time viewport fixes** — if a NEW login/cashshop/boss-bar screen looks off at 1280x720,
either (a) add an `EzorsiaV2_UI.wz` override by hand or (b) add/adjust an `hd-res` scaling
address — discoverable only by launching, which is out of scope here.

## D — "HD2 recommends" refers to no real tool
No "HD2" tool/pipeline exists anywhere on disk or in upstream. Nearest real referents:
Magpie (upstream's recommended borderless-fullscreen upscaler for 1280x720), the login
"view recommended" button, and the "recommended" MsgAmount = height/2/14. None is an asset
processor.

## Bottom line for the v84 port
v84's new maps/assets need **no per-asset HD production**. They inherit HD from the same
render-scaling DLL. Remaining HD work is only: (1) port the open `hd-res` addresses (tools/hd,
already 99.7%), and (2) launch-time viewport fixes for any new UI screen that looks wrong —
optionally via a hand-made `EzorsiaV2_UI.wz` edit, exactly as v83 did.
