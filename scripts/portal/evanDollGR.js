/*
    Ticket 08 (GMS v84). Golem's Temple 2 (106010102) -> 910600010 Abandoned Hideout.

    Client half is Map.wz/Map/Map1/106010102.img/portal/8 (pn=scr00, pt=8), a v84 addition this
    ticket merges as a verified pure append onto the live 8-portal array.
    910600010's own out00 returns to 106010102 at tn=scr00, i.e. this portal.

    Ungated on purpose - see enterDollcave.js.
*/
function enter(pi) {
    pi.playPortalSound();
    pi.warp(910600010, "out00");
    return true;
}
