// Portal "in00" of 220011000 (El Nath : Forest of Dead Trees IV) - the door v84 turns into a
// script gate. Pristine v84 Map.wz:
//   Map/Map2/220011000.img/portal/4  pn=in00 pt=7 x=613 y=132 tm=999999999 tn=""
//                                    horizontalImpact=0 script=enterBlackBC
// v83 had it as a plain pt=2 warp to 220011001/out00. tm=999999999 on a pt=7 portal means the
// server picks the destination; that is what this file is.
//
// Three destinations, all named by Quest.wz, none invented:
//   922030010 - QuestInfo.img/22583/1 "go to the #b#m922030010##k ... climb the ladder". The mob
//               the quest wants, Check.img/22583/1/mob/0 = 9300389, is placed in exactly one map
//               in the whole tree: 922030011, which hangs off 922030010/up00.
//   922030020 - QuestInfo.img/22584/1 "climb up to the #m922030020# once again ... enter the safe".
//               Check.img/22584/1/mob/0 = 9300390, again placed in exactly one map: 922030022,
//               reached 922030020/up00 -> 922030021/st00 -> 922030021/in00.
//   220011001 - everyone else. This portal is 220011001's only entrance in all of Map.wz, so the
//               fallback is mandatory, not cosmetic. It is also what our v83 node warped to.
// Both rooms return the same way: 922030010/out00 and 922030020/out00 are tm=220011000 tn=in00.
//
// The two quests can never both be started - Check.img/22584/0/quest/0 requires 22583 at state 2 -
// so the order below is for readability, not correctness.
function enter(pi) {
    if (pi.isQuestStarted(22584)) {
        pi.playPortalSound();
        pi.warp(922030020, 0);
        return true;
    }

    if (pi.isQuestStarted(22583)) {
        pi.playPortalSound();
        pi.warp(922030010, 0);
        return true;
    }

    pi.playPortalSound();
    pi.warp(220011001, "out00");
    return true;
}
