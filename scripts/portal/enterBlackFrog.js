// Portal "scr00" of 220000300 (El Nath : Ice Valley II) - the door into the Frog House, where
// Hiver (npc 1013203, "Black Wing Captain") hands out the Black Wings' missions.
//
// The portal itself was missing from this tree until now. Pristine v84 Map.wz carries it:
//   Map/Map2/220000300.img/portal/4  pn=scr00 pt=7 x=-1674 y=106 tm=999999999 tn=""
//                                    horizontalImpact=0 script=enterBlackFrog
// v83 has 15 portals on this map and no scr00 at all; v84 has 16 and INSERTS scr00 at index 4,
// which is why ticket 08's positional add-list could not merge it and refused the row
// (V84MiscAreasNodeTest.theUnsafeRoutePortalRowsWereNotMerged). It is now hand-authored at the
// end of the array instead, which reaches the same portal without shifting the other fifteen.
//
// Two destinations, and the data names both. Across all 4505 map images in pristine v84 exactly
// two maps return to this portal - 922030000/out00 and 922030001/out00, both tm=220000300
// tn="scr00". Every other tm=220000300 in the file points at in00..in06 / west00 / east00 /
// h000 / h001. So this script has exactly two legal targets and no third.
//
// Which is which:
//   922030000 "Frog House" - life: n 1013203 (Hiver). Check.img/22581/1, /22582..22588 all name
//                            npc 1013203, so this is where missions one through five are taken.
//   922030001 "Frog House" - identical name, EMPTY life, info/onUserEnter="enterBlackfrog".
//                            QuestInfo.img/22596/1 ("Rage") says it outright: "go to the
//                            #b#m922030001##k in #m220000300# where you met #p1013203# in
//                            person ... #o9300393#". Check.img/22596/1 requires mob 9300393
//                            ("Gentleman") x1. That is the fight room.
// tm=999999999 on a pt=7 portal means the server picks; nothing about that choice is client-side.
//
// The room fills itself. v84 places mob 9300393 in no map's life (scanned: 0 hits for id=9300393
// over all 4848 v84 map images) and Mob.wz/9300393 has no info/revive, so the spawn is done by the
// map hook 922030001 declares - scripts/map/onUserEnter/enterBlackfrog.js, note the lowercase f, a
// different name from this file. That hook now exists; its header carries the derivation of the
// one coordinate it has to choose.
function enter(pi) {
    if (pi.isQuestStarted(22596)) {
        pi.playPortalSound();
        pi.warp(922030001, 0);
        return true;
    }

    pi.playPortalSound();
    pi.warp(922030000, 0);
    return true;
}
