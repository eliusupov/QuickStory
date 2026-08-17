-- characters.spawnpoint after the v84 portal take on the terrain-class maps.
--
-- spawnpoint is an INDEX into the map's portal array, not a name: Character#saveCharToDB writes
-- findClosestPlayerSpawnpoint(...).getId(), and PortalFactory takes that id from the node's NAME,
-- i.e. its slot. Taking v84's array put v84's rows at v84's indices, which moved some slots, so a
-- stored value can now name a different portal. This is the same correction changeSet 164 made
-- after the town portal reindex.
--
-- Only two of the 28 maps moved a slot that findClosestPlayerSpawnpoint can ever return - it only
-- hands back a pt 0 or 1 portal whose tm is MapId.NONE. Every other map either kept its eligible
-- slots in place or only moved coordinates within the same slot, which needs no correction.
--
--   109090000  Sheep Ranch Lobby        v84 carries seven sp spawns where we had one, so start00
--                                       moves from slot 1 to slot 7.
--   670010600  Amorian Challenge St.5   v84 has gt00PIA and gt01PIA, which our array lacked
--                                       entirely; every later gate portal shifts down by two.
--
-- Written as a single CASE per map on purpose: the 670010600 mapping overlaps (1 -> 2 and 2 -> 4),
-- so sequential UPDATEs would move the same row twice.

UPDATE characters
SET spawnpoint = 7
WHERE map = 109090000
  AND spawnpoint = 1;

UPDATE characters
SET spawnpoint = CASE spawnpoint
                     WHEN 1 THEN 2      -- gt00PIB
                     WHEN 2 THEN 4      -- gt01PIB
                     WHEN 4 THEN 6      -- gt02PIB
                     WHEN 6 THEN 8      -- gt03PIB
                     WHEN 8 THEN 10     -- gt04PIB
                     WHEN 10 THEN 12    -- gt05PIB
                     WHEN 12 THEN 14    -- gt06PIB
                     WHEN 13 THEN 15    -- st00
                     WHEN 15 THEN 17    -- gm01
                     ELSE spawnpoint
    END
WHERE map = 670010600
  AND spawnpoint IN (1, 2, 4, 6, 8, 10, 12, 13, 15);
