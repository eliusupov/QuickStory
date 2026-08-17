// Portal "out00" of 914100022 (Slumbering Dragon Island : Cave of Silence, the ice-wall room).
//
// Its tm is 999999999, so with no script the portal is dead - GenericPortal.enterPortal only
// consults tm/tn when scriptName is null. The destination is not a guess: all four Cave of
// Silence rooms put out00 at exactly (-548, 143), and the other three (914100020, 914100021,
// 914100023) are plain pt=2 portals with tm=914100010 / tn="in00". 914100022's own
// returnMap and forcedReturn are 914100010 as well. Same portal name, same pixel, same
// declared return - the way out of this room is Snowy Forest's "in00".
//
// Effect.wz has no Direction node named outSDI (33 nodes across Direction.img..Direction4.img,
// 25 distinct, checked), so this is not a cutscene and must never play one - a scene path the
// client cannot resolve takes the client down, which is how every female Evan was crashing on
// PromiseDragon/Scene1. MapAndPortalScriptsRealLoad enforces that on this file by name.
//
// ponytail: the warp only. GMS almost certainly also gates or cleans up here - this is the exit
// of the ice-wall encounter that 914100022's summonIceWall / stopIceWall2 / reactor 1409000
// drive, and none of those exist in this tree. That condition is NOT invented here: an
// unconditional exit can only let a player leave a room, never enter one. Add the gate when the
// encounter it belongs to is built (docs/work-plan/tickets/47-evan-ice-cave.md).
function enter(pi) {
    pi.playPortalSound();
    pi.warp(914100010, "in00");
    return true;
}
