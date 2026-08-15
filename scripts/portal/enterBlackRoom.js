/*
    Ticket 08 (GMS v84). Orbis Tower 20th Floor (200080600) -> 200080601 Orbis Tower <Secret Room>.

    Client half is Map.wz/Map/Map2/200080600.img/portal/6 (pn=in00, pt=8), a v84 addition this
    ticket merges as a verified pure append onto the live 6-portal array.

    Ungated on purpose - see enterDollcave.js.
*/
function enter(pi) {
    pi.playPortalSound();
    pi.warp(200080601, "out00");
    return true;
}
