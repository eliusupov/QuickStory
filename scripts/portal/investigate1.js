/*
    Map 106020300 (Mushroom Castle : Deep Inside Mushroom Forest), trigger portal investigate1
    at (1113, -11).
    NPC 1300014 sits at (1426, 32) with hide=1, so this trigger is the only way a player can
    reach it - the sibling trigger next to it, obstacle at (1313, -11), is the one that actually
    opens the way on to 106020400.
    Note: scripts/npc/1300014.js is currently a stub that only calls cm.dispose(), so wiring the
    portal restores the GMS call path but produces no dialogue until that NPC script is filled in.
*/
function enter(pi) {
    pi.openNpc(1300014);
    return true;
}
