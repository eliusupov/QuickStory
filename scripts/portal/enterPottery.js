/*
    Ticket 08 (GMS v84). Herb Town "Pirate Cave" (251010403) -> 925110000 Pirate Treasure Vault.

    Client half is Map.wz/Map/Map2/251010403.img/portal/4 (pn=in00, pt=8), a v84 addition this
    ticket merges as a verified pure append onto the live 4-portal array.

    Ungated on purpose - see enterDollcave.js.
*/
function enter(pi) {
    pi.playPortalSound();
    pi.warp(925110000, "out00");
    return true;
}
