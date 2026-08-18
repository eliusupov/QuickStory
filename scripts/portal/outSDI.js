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
// This exit is also the writer of quest record 22600 = "1", the START gate of quest 22589
// "Dangerous Premonition" (Check.img/22589/0: infoNumber 22600, infoex/0/value "1").
//
// QuestInfo.img/22589/0 states the trigger in its first clause: "#bWhen you come out of the
// cave#k, #p1013000# seems restless." 22588's own aftermath text says the same from the other
// side - QuestInfo.img/22588/2 "you were told to come out as there was nothing more for you to do
// there". And exactly ONE of the four Cave of Silence exits is scripted: 914100020, 914100021 and
// 914100023 all put out00 on this same pixel as a plain pt=2 tm=914100010 portal with no script
// node at all. v84 converted only this one into pt=7 tm=999999999 script=outSDI, and this is the
// room 22588 happens in (the only one with reactor 1409000 and onUserEnter=summonIceWall).
//
// The alternative once tracked in V84-OPEN-ITEMS - "22588's autoComplete" - is refused by the
// data: Act.img/22588/1 carries no info node and writes no record, and Check.img/22588/1 completes
// at npc 1013203 Hiver, who Map.wz places in exactly one map (922030000), off the island. 22588
// therefore completes AFTER this exit and cannot be what "when you come out of the cave" means.
//
// Both halves of the guard are load-bearing. Character.setQuestProgress resolves the slot through
// Quest.getInfoNumber(status): 22600 is the START block, so it is only reached while 22589 is
// NOT_STARTED - once 22589 is STARTED the identical call resolves to 22604 and this would stamp
// the COMPLETION gate instead. getQuestStatus(22588) == 1 is the altar errand still open, which is
// exactly the state Check.img/22589/0/quest/0 then requires at state 2.
//
// ponytail: still the warp plus one record. GMS almost certainly also cleans up the ice-wall
// encounter here - 914100022's summonIceWall / stopIceWall2 - and none of that exists in this tree.
function enter(pi) {
    if (pi.getQuestStatus(22588) == 1 && pi.getQuestStatus(22589) == 0) {
        pi.setQuestProgress(22589, 22600, 1);
    }

    pi.playPortalSound();
    pi.warp(914100010, "in00");
    return true;
}
