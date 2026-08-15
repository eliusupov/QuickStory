# WZ baseline diff — machine-generated summary

Generated 2026-08-15 23:45:36
Roots: v83=`D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock;D:\games\MapleStory\Server\porting-resources\wz-data\v83-reactor` v84=`D:\games\MapleStory\Server\porting-resources\wz-data\v84` live=`D:\games\MapleStory`

`—` = not measurable (a required tree lacks this file). It never means zero.
Node counts are paths (directories + images + 3 levels of sub-properties).

| wz | v83-stock | v84 | live client | add (v84−v83) | removed (v83−v84) | protect (live − (v83 ∪ v84)) | modified v83→v84 | modified v83→live | add bytes | protect bytes |
|---|---|---|---|---|---|---|---|---|---|---|
| Base.wz | 431 | 431 | 431 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Character.wz | 2,007,010 | 2,053,507 | 2,013,420 | 438 | 136 | 2987 | 41 | 5114 | 5,461,522 | 62,921 |
| Effect.wz | 8,284 | 8,842 | 8,284 | 23 | 0 | 0 | 5 | 0 | 14,128,081 | 0 |
| Etc.wz | 103,850 | 113,236 | 158,475 | 10634 | 2028 | 6 | 10 | 3 | 0 | 600,921 |
| EzorsiaV2_UI.wz | MISSING | MISSING | 457 | — | — | — | — | — | — | — |
| Item.wz | 90,702 | 94,396 | 90,892 | 391 | 35 | 106 | 28 | 10 | 248,814 | 0 |
| List.wz | OPEN-FAILED: no encryption version parsed: GMS: InvalidDataException WZ header FStart is outside the file. / BMS: InvalidDataException WZ header FStart is outside the file. / EMS: InvalidDataException WZ header FStart is outside the file. | OPEN-FAILED: no encryption version parsed: GMS: InvalidDataException WZ header FStart is outside the file. / BMS: InvalidDataException WZ header FStart is outside the file. / EMS: InvalidDataException WZ header FStart is outside the file. | OPEN-FAILED: no encryption version parsed: GMS: InvalidDataException WZ header FStart is outside the file. / BMS: InvalidDataException WZ header FStart is outside the file. / EMS: InvalidDataException WZ header FStart is outside the file. | — | — | — | — | — | — | — |
| Map.wz | 2,064,680 | 1,744,919 | 2,062,967 | 601 | 1017 | 399 | 126 | 733 | 2,772,832 | 2,120,847 |
| Mob.wz | 315,083 | 328,472 | 315,253 | 1216 | 674 | 168 | 1171 | 84 | 18,122,373 | 0 |
| Morph.wz | 11,625 | 12,974 | 11,625 | 25 | 0 | 0 | 7 | 0 | 113,953 | 0 |
| Npc.wz | 75,265 | 76,972 | 199,125 | 98 | 31 | 5981 | 10 | 261 | 1,118,256 | 2,170,145 |
| Quest.wz | 85,169 | 91,445 | 85,275 | 924 | 39 | 225 | 5 | 4 | 2,941 | 0 |
| Reactor.wz | 18,722 | 18,972 | 18,795 | 6 | 0 | 49 | 0 | 19 | 635,966 | 11,690 |
| Skill.wz | 5,637 | 13,762 | 5,645 | 55 | 0 | 3 | 4 | 9 | 37,891,358 | 0 |
| Sound.wz | 10,731 | 10,963 | 10,731 | 62 | 2 | 0 | 6 | 0 | 0 | 0 |
| String.wz | 88,895 | 93,631 | 113,140 | 1579 | 0 | 7604 | 12 | 10 | 0 | 0 |
| TamingMob.wz | 49 | 49 | 49 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| UI.wz | 13,566 | 14,228 | 13,607 | 61 | 7 | 41 | 8 | 1 | 0 | 0 |

## image parse status

55,343 images parsed, **3 parse failures**.

A failed image contributes zero sub-nodes: in v84 it silently drops content,
in live it leaves custom content unprotected, in v83 it manufactures false adds.
Every manifest touching these files is suspect until they parse.

| tree | path | error |
|---|---|---|
| v83 | `Sound.wz/BgmGL.img` | InvalidDataException: WZ extended property exceeds its declared block. |
| v84 | `Sound.wz/BgmGL.img` | InvalidDataException: WZ extended property exceeds its declared block. |
| live | `Sound.wz/BgmGL.img` | InvalidDataException: WZ extended property exceeds its declared block. |
