-- Hall of Fame PlayerNPCs.
--
-- Quest 22402 "Meeting the Dragon Rider" names npc 9901000, which Etc.wz/NpcLocation.img puts on
-- map 102000004 (Hall of Warriors). 9901000 is not an ordinary NPC: it is the first slot of the
-- warrior branch of the PlayerNPC script-id allocator, so the only correct way to make it resolve
-- is to have a PlayerNPC occupy it. With `playernpcs` empty the quest, skill 20011004 and the whole
-- 22403-22413 branch are unreachable.
--
-- Every value below is what server.life.PlayerNPC#createPlayerNPCInternal would have written:
--   scriptid  = NpcId.PLAYER_NPC_BASE + 100 * GameConstants.getHallOfFameBranch(job, map),
--               lowest free id in the branch (warrior 10 -> 9901000, magician 11 -> 9901100,
--               bowman 12 -> 9901200, thief 13 -> 9901300, pirate 14 -> 9901400)
--   map       = GameConstants.getHallOfFameMapid(job)
--   x, cy, fh = MapleMap#getGroundBelow(PlayerNPCPodium#calcNextPos(rank, 1)) on the real
--               Map.wz footholds; rx0/rx1 = x +/- 50; dir = 1
--   job       = (job DIV 100) * 100, the job family, not the character's job
--   ranks     = the running counters, in the creation order below (highest level first)
-- server.life.positioner.HallOfFameSeedRealLoad recomputes all of it from wz and fails if it drifts.
--
-- Autodeploy (Character#levelUp) and the npc-script path (NPCConversationManager#canSpawnPlayerNpc)
-- both refuse GM characters and both require level == maxClassLevel; every character in this
-- database is gm >= 2, so neither path can ever fill these halls. The GM commands !playernpc /
-- !spawnallpnpcs deliberately carry no such check - seeding here is that same admin decision, and
-- the owner approved the GM characters explicitly. No guard is weakened.

INSERT INTO playernpcs (name, hair, face, skin, gender, x, cy, world, map, scriptid, dir, fh, rx0,
                        rx1, worldrank, overallrank, worldjobrank, job)
SELECT c.name, c.hair, c.face, c.skincolor, c.gender, s.x, s.cy, c.world, s.map, s.scriptid, 1, s.fh,
       s.x + 50, s.x - 50, s.wrank, s.wrank, s.jobrank, (c.job DIV 100) * 100
FROM characters c
         JOIN (SELECT 'Shadow' cname, 103000008 map, 9901300 scriptid, 0 x, -8 cy, 12 fh, 1 wrank, 1 jobrank
               UNION ALL
               SELECT 'Robinhood', 100000204, 9901200, 0, -1, 12, 2, 1
               UNION ALL
               SELECT 'Wall', 102000004, 9901000, 0, 33, 12, 3, 1
               UNION ALL
               SELECT 'monkeyDluffy', 103000008, 9901301, -120, 71, 5, 4, 2
               UNION ALL
               SELECT 'arikrab', 101000004, 9901100, 0, -8, 40, 5, 1
               UNION ALL
               SELECT 'CaptianKid', 120000105, 9901400, 0, -41, 18, 6, 1) s ON s.cname = c.name;

-- Equipped items, filtered exactly as createPlayerNPCInternal filters them: |position| 1-11 (visible
-- equips) and 101-111 (their cash overrides). Pet/medal/ring slots are skipped, as there.
INSERT INTO playernpcs_equip (npcid, equipid, equippos)
SELECT p.id, i.itemid, i.position
FROM playernpcs p
         JOIN characters c ON c.name = p.name AND c.world = p.world
         JOIN inventoryitems i ON i.characterid = c.id AND i.inventorytype = -1
WHERE p.scriptid IN (9901000, 9901100, 9901200, 9901300, 9901301, 9901400)
  AND (ABS(i.position) BETWEEN 1 AND 11 OR ABS(i.position) BETWEEN 101 AND 111);

-- Podium bookkeeping, so the next runtime deploy continues from these instead of stacking on top.
-- podium is PlayerNPCPodium#encodePodiumData(step, count) = count * 32 + step.
INSERT INTO playernpcs_field (world, map, step, podium)
VALUES (0, 103000008, 0, 65),
       (0, 100000204, 0, 33),
       (0, 102000004, 0, 33),
       (0, 101000004, 0, 33),
       (0, 120000105, 0, 33);
