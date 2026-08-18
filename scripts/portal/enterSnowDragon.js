// Portal "in00" of 914100010 (Slumbering Dragon Island : Snowy Forest) - the shared front door of
// the four "Cave of Silence" rooms. Map.wz, ours and pristine v84 byte-identical on this node:
//   Map/Map9/914100010.img/portal/2  pn=in00 pt=7 x=2545 y=84 tm=999999999 tn=""
//                                    horizontalImpact=0 script=enterSnowDragon
// tm=999999999 on a pt=7 portal means the server picks. A tm scan over all 4848 pristine v84 map
// images returns ZERO hits for 914100020, 914100021, 914100022 and 914100023, so no portal in the
// game supplies any of the four - this script is their only entrance.
//
// Each room is claimed by its own contents plus that quest's Check.img gate. Nothing here comes
// from a wiki:
//
//   914100020  life EMPTY, reactor EMPTY, onUserEnter="", ten portals scr00..scr09 pt=9
//              script=stopIceWall.  ->  22580. stopIceWall.js writes 22599="2", which is exactly
//              Check.img/22580/1 (infoNumber 22599, infoex/0/value "2"), the COMPLETE gate.
//   914100021  life n 1205000 (Afrien), info/onUserEnter=evanTogether.  ->  22590 and 22591.
//              Check.img/22590/1/npc = 1205000 (hand-in), Check.img/22591/0/npc = 1205000 (start)
//              and Check.img/22591/1 = npc 1205000 + infoNumber 22601. Afrien is placed in this
//              map and nowhere else on the island.
//   914100022  reactor/0 id=1409000, info/onUserEnter=summonIceWall, and the only one of the four
//              whose out00 is scripted (pt=7 outSDI).  ->  22588. reactor/1409000.js writes
//              22605=1, which is Check.img/22588/1 (infoNumber 22605, value "1").
//   914100023  life m 9300392 x10, info/onUserEnter=blackSDI.  ->  22589. blackSDI.js writes
//              22604=1, which is Check.img/22589/1 (infoNumber 22604, value "1").
//
// The four are chained (22588 needs 22586 done, 22589 needs 22588, 22590 needs 22589, 22591 needs
// 22590), so at most one of those is ever STARTED at a time and their order below is readability,
// not correctness. 22580 is a different chain (it needs 22579) and CAN overlap them, so it is
// tested first: it is the lower-level quest (lvmin 62 against 70) and its objective is one touch.
//
// ponytail: the fallback is 914100020 and it is deliberately the inert room - empty life, empty
// reactor, info/onUserEnter="" and its only scripts are the ten stopIceWall triggers, each of
// which no-ops unless 22580 is STARTED. Nothing fires for a passer-by. This is the one branch the
// data does not decide (OWNER Q1 in ticket 55); if the owner picks another room it is this single
// warp line, and every gated branch above stays as it is.
//
// Effect.wz has no Direction node named enterSnowDragon, so this is not a cutscene and must never
// play one - a scene path the client cannot resolve takes the client down.
function enter(pi) {
    if (pi.isQuestStarted(22580)) {
        pi.playPortalSound();
        pi.warp(914100020, 0);
        return true;
    }

    if (pi.isQuestStarted(22588)) {
        pi.playPortalSound();
        pi.warp(914100022, 0);
        return true;
    }

    if (pi.isQuestStarted(22589)) {
        pi.playPortalSound();
        pi.warp(914100023, 0);
        return true;
    }

    if (pi.isQuestStarted(22590) || pi.isQuestStarted(22591)) {
        pi.playPortalSound();
        pi.warp(914100021, 0);
        return true;
    }

    pi.playPortalSound();
    pi.warp(914100020, 0);
    return true;
}
