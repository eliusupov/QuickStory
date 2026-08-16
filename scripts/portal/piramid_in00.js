/*
    Map 926010000 (Hidden Street : Pyramid Dunes), portal in00 at (935, 214).
    The pyramid gate. Duarte (NPC 2103013) stands right beside it at (1013, 212) and
    scripts/npc/2103013.js already has the whole 926010000 branch, so walking into the gate is
    the same conversation the player gets from clicking him.
    Until Nett's Pyramid is re-enabled that conversation answers "The PyramidPQ is currently
    unavailable." - which is still the right answer for the portal, and better than the silent
    dead end a missing script leaves.
*/
function enter(pi) {
    pi.openNpc(2103013);
    return true;
}
