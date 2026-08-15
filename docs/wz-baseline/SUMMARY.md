# WZ baseline diff — machine-generated summary

Generated 2026-08-15 22:58:41
Roots: v83=`D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock;D:\games\MapleStory\Server\porting-resources\wz-data\v83-reactor` v84=`D:\games\MapleStory\Server\porting-resources\wz-data\v84` live=`D:\games\MapleStory`

`—` = not measurable (a required tree lacks this file). It never means zero.
Node counts are paths (directories + images + one level of sub-properties).

| wz | v83-stock | v84 | live client | add (v84−v83) | removed (v83−v84) | protect (live − (v83 ∪ v84)) | modified v83→v84 | modified v83→live | add bytes | protect bytes |
|---|---|---|---|---|---|---|---|---|---|---|
| Base.wz | MISSING | MISSING | 322 | — | — | — | — | — | — | — |
| Character.wz | 212,238 | 216,238 | 212,350 | 182 | 28 | 4 | 41 | 5114 | 5,461,522 | 62,921 |
| Effect.wz | MISSING | MISSING | 400 | — | — | — | — | — | — | — |
| Etc.wz | 14,556 | 14,708 | 19,829 | 229 | 77 | 4 | 10 | 3 | 0 | 600,921 |
| EzorsiaV2_UI.wz | MISSING | MISSING | 326 | — | — | — | — | — | — | — |
| Item.wz | 7,357 | 7,624 | 7,361 | 236 | 0 | 4 | 28 | 10 | 248,814 | 0 |
| List.wz | MISSING | MISSING | OPEN-FAILED: no encryption version parsed: GMS: InvalidDataException WZ header FStart is outside the file. / BMS: InvalidDataException WZ header FStart is outside the file. / EMS: InvalidDataException WZ header FStart is outside the file. | — | — | — | — | — | — | — |
| Map.wz | 57,429 | 44,996 | 57,474 | 86 | 833 | 10 | 126 | 733 | 2,772,832 | 2,120,847 |
| Mob.wz | 9,159 | 9,446 | 9,159 | 37 | 0 | 0 | 1171 | 84 | 18,122,373 | 0 |
| Morph.wz | MISSING | MISSING | 508 | — | — | — | — | — | — | — |
| Npc.wz | 6,767 | 6,948 | 28,136 | 50 | 7 | 5333 | 10 | 261 | 1,118,256 | 2,170,145 |
| Quest.wz | 11,263 | 12,060 | 11,267 | 796 | 1 | 4 | 5 | 4 | 2,941 | 0 |
| Reactor.wz | 2,560 | 2,594 | 2,565 | 6 | 0 | 7 | 0 | 19 | 635,966 | 11,690 |
| Skill.wz | 290 | 480 | 290 | 14 | 0 | 0 | 4 | 9 | 37,891,358 | 0 |
| Sound.wz | MISSING | MISSING | 2,480 | — | — | — | — | — | — | — |
| String.wz | 7,482 | 7,911 | 12,859 | 429 | 0 | 5417 | 12 | 10 | 0 | 0 |
| TamingMob.wz | MISSING | MISSING | 14 | — | — | — | — | — | — | — |
| UI.wz | 448 | 459 | 448 | 11 | 0 | 0 | 8 | 1 | 0 | 0 |

## image parse status

55,112 images parsed, **1 parse failures**.

A failed image contributes zero sub-nodes: in v84 it silently drops content,
in live it leaves custom content unprotected, in v83 it manufactures false adds.
Every manifest touching these files is suspect until they parse.

| tree | path | error |
|---|---|---|
| live | `Sound.wz/BgmGL.img` | InvalidDataException: WZ extended property exceeds its declared block. |
