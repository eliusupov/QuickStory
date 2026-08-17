-- characters.spawnpoint stores a portal ARRAY INDEX, not a name (Character.java saves
-- map.findClosestPlayerSpawnpoint(pos).getId(), and PortalFactory sets that id from the portal
-- node's name - its position in the array). Inserting v84's `unityPortal2` mid-array in the 17
-- town maps pushes every later portal down one, so a stored index at or after the insertion
-- point now names the portal that used to sit after it.
--
-- One +1 per map, at exactly the index v84 inserts at. Indices below it are untouched, which is
-- almost all of them: findClosestPlayerSpawnpoint only ever returns a pt=0/1 portal with
-- tm=999999999, and in every one of these maps the `sp` block sits at the head of the array.
--
-- Runs at startup, i.e. after the shutdown that saved these values under the old numbering.

UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 100000000 AND spawnpoint >= 12;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 101000000 AND spawnpoint >= 28;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 102000000 AND spawnpoint >= 14;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 103000000 AND spawnpoint >= 23;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 105040300 AND spawnpoint >= 12;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 120000000 AND spawnpoint >= 2;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 200000200 AND spawnpoint >= 1;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 211000000 AND spawnpoint >= 11;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 220000000 AND spawnpoint >= 9;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 221000000 AND spawnpoint >= 3;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 222000000 AND spawnpoint >= 16;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 230000000 AND spawnpoint >= 12;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 240000000 AND spawnpoint >= 4;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 250000000 AND spawnpoint >= 11;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 251000000 AND spawnpoint >= 7;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 260000000 AND spawnpoint >= 19;
UPDATE characters SET spawnpoint = spawnpoint + 1 WHERE map = 261000000 AND spawnpoint >= 15;
