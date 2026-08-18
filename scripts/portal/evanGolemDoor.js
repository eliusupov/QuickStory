// Portal "in00" of 106010101 (Golem's Temple 1) - the Warning Sign door. Pristine v84 Map.wz:
//   Map/Map1/106010101.img/portal/5  pn=in00 pt=7 x=92 y=-535 tm=999999999 tn=""
//                                    horizontalImpact=0 script=evanGolemDoor
// v83 had it as a plain pt=2 warp to 106010102/out00, which is why 910600000 was unreachable:
// nothing in Map.wz, scripts/ or src/ names 910600000 as a destination, and a tm scan over all
// 4848 pristine v84 images returns zero hits for it. The server picks, and this is the pick.
//
// 910600000 - QuestInfo.img/22555/1 ("Chief Stan's Test"): "go to the #m910600000#, hunt the
//             #o3000001#s there, and bring back #b#t4000068#s#k. You can enter #m910600000#
//             through the Warning Sign at the #m106010100#". The return side corroborates it:
//             910600000/portal/1 is out00 pt=2 tm=106010101 tn=in00, i.e. exactly this door.
//             (The quest text says 106010100; that map has 11 portals and no script portal at all.
//             106010101/in00 is the Warning Sign.)
// 106010102 - everyone else, and not a "nobody" branch: QuestInfo.img/22556 sends the player to
//             #m106010102# by name, and this portal is the temple's only route in.
//
// STARTED only, not started-or-completed, which is the opposite of enterDollcave.js and matches
// enterBlackFrog.js. The difference is what is behind the door: enterDollcave keeps 910050300 open
// after 22549 because npc 1063018 lives there alone and eight later quests need him. 910600000 has
// no npc, appears in exactly one QuestInfo string in the whole tree (22555's), and holds one mob
// with mobTime -1 - so once 22555 is handed in nothing asks the player back, and leaving the branch
// open would only take 106010102 away from him.
function enter(pi) {
    if (pi.isQuestStarted(22555)) {
        pi.playPortalSound();
        pi.warp(910600000, 0);
        return true;
    }

    pi.playPortalSound();
    pi.warp(106010102, "out00");
    return true;
}
