-- Re-anchor the seeded Hall of Fame PlayerNPCs after the v84 terrain take.
--
-- changeSet 163 wrote x/cy/fh straight out of
-- MapleMap#getGroundBelow(PlayerNPCPodium#calcNextPos(rank, 1)) against the foothold tables as they
-- stood then - v83's. The five podium halls have since taken v84's foothold table verbatim, because
-- the client the owner plays resolves every `fh` the server sends against ITS OWN copy of the map:
-- these six rows were citing ids 12, 40 and 18 on maps whose v84 image has 2, 67 and 12 footholds.
-- The podium's hardcoded platform coordinates still resolve on all five halls (no slot lands on
-- nothing), so this is a re-anchor, not a redesign - the largest move is 27 pixels.
--
--   map        npc       163 (x, cy, fh)   now (x, cy, fh)
--   103000008  9901300   (0, -8, 12)       (0, -7, 1)
--   100000204  9901200   (0, -1, 12)       (0, -3, 1)
--   102000004  9901000   (0, 33, 12)       (0, 34, 5)
--   103000008  9901301   (-120, 71, 5)     (-120, 71, 2)
--   101000004  9901100   (0, -8, 40)       (0, -35, 3)
--   120000105  9901400   (0, -41, 18)      (0, -45, 3)
--
-- x is unchanged on every row, so rx0/rx1 need no correction.
-- server.life.positioner.HallOfFameSeedRealLoad recomputes all of it from wz and fails if it drifts.

UPDATE playernpcs SET cy = -7, fh = 1 WHERE scriptid = 9901300 AND map = 103000008;
UPDATE playernpcs SET cy = -3, fh = 1 WHERE scriptid = 9901200 AND map = 100000204;
UPDATE playernpcs SET cy = 34, fh = 5 WHERE scriptid = 9901000 AND map = 102000004;
UPDATE playernpcs SET cy = 71, fh = 2 WHERE scriptid = 9901301 AND map = 103000008;
UPDATE playernpcs SET cy = -35, fh = 3 WHERE scriptid = 9901100 AND map = 101000004;
UPDATE playernpcs SET cy = -45, fh = 3 WHERE scriptid = 9901400 AND map = 120000105;
