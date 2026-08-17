# 46 - v84 map life parity: 106010000, 106010100, and the 196000000 exception

Follow-on to f4bccbc0f (map 101030001). Owner authorised replacing map life arrays outright where
exact v84 parity requires deleting spawns this tree held over from v83. That licence covers map life
arrays and nothing else.

Source of truth: `D:\games\wz-stage\v84-base\Map.wz`, dumped with

```
docs/wz-baseline/tool-merge/bin/Release/net10.0-windows/WzMerge.exe dump \
    "D:/games/wz-stage/v84-base/Map.wz" "Map/Map1/<id>.img/life"
```

Nothing below was reconstructed by hand; each replaced array is the dump written back verbatim -
same slots, same field order, same values (verified field-by-field after the write).

## 106010000 - Warning Street, "The Road to the Dungeon" (REPLACED)

| | entries | composition |
|---|---|---|
| before | 46 | 26x 2220100, 19x 2110200, 1x npc 1072002 |
| after / v84 | 44 | 31x 2220100, 11x 2220110, 1x 2110200, 1x npc 1072002 |

Delta: 2110200 (Horny Mushroom) 19 -> 1 (-18), 2220100 (Blue Mushroom) 26 -> 31 (+5),
2220110 (Crying Blue Mushroom) 0 -> 11 (+11). Net -2.

NPC: **none added, none removed.** 1072002 "Bowman Job Instructor" is in v84's array too
(slot 32, fh 52) - the 2nd-job advancement NPC, so this was the one that had to survive.
Footholds: all 44 distinct v84 `fh` values resolve in this tree's foothold table for this map.
Slots 0..43 complete.

## 106010100 - Warning Street, "Henesys Dungeon Entrance" (REPLACED)

| | entries | composition |
|---|---|---|
| before | 32 | 14x 2110200, 9x 2220100, 8x 2230101, 1x npc 1040000 |
| after / v84 | 32 | 13x 2220110, 12x 2220100, 3x 2110200, 3x 2230101, 1x npc 1040000 |

Delta: same total, entirely different population. 2110200 14 -> 3 (-11), 2220100 9 -> 12 (+3),
2230101 (Zombie Mushroom) 8 -> 3 (-5), 2220110 0 -> 13 (+13).

NPC: **none added, none removed.** 1040000 "Luke" is in v84's array too (slot 31, fh 218).
Footholds: all 31 distinct v84 `fh` values resolve. Slots 0..31 complete.

## 196000000 - NOT replaced, deliberately

v84 stock life for this id is 22x 9200018 + 1x 9200019 and **no npc at all**. This tree runs the map
as Cafe PQ stage 5 - `scripts/event/CafePQ_5.js` has `entryMap = 196000000` - populated with
22x 5100000 (Jr. Yeti) + 1x 5140000 (White Fang) and npc **1052013 "Computer"**, the PQ's entry point
(`scripts/npc/1052013.js`, the same npc appears on 190000000-197000000).

Replacing would delete that npc and every PQ mob, breaking Cafe PQ stage 5. Left alone.

The map itself *is* the same map in both trees - `info` matches on `bgm=Bgm04/WarmRegard`,
`mapMark=ElNath`, `returnMap=193000000`, `tS=snowyLightrock2` - so this is a population difference,
not a repurposed id. (`fieldLimit` and `forcedReturn` also differ between the trees; out of scope
here, life arrays only. UNPROVEN whether v84's population is what the v84 client actually expects
for a Cafe PQ run, since the PQ is server-scripted.)

## Verification

`src/test/java/server/V84MapLifeParityRealLoad.java` - exact counts, not floors, plus a slot check.
The 196000000 case is asserted too, so a later blind replacement fails loudly rather than silently
removing the entry npc.

```
mvnw.cmd -o test -Dtest=V84MapLifeParityRealLoad
```

Non-vacuity: mutating the expected `2220110` count from 11 to 10 produced exactly one failure naming
106010000.

`*RealLoad` classes are outside surefire's default includes (`*Test`/`Test*`/`*Tests`/`*TestCase`;
the pom adds none), so they do not move the `mvnw.cmd -o test` count - 2163/0 before and after.
