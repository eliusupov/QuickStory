// Map 914100023 (Slumbering Dragon Island : Cave of Silence), the Black Wings ambush room.
// Map/Map9/914100023.img/info/onUserEnter = "blackSDI" is the only hook the map declares:
// info/onFirstUserEnter is the EMPTY STRING, its reactor node is empty, and its one real portal
// (portal/1 out00, pt=2, tm=914100010) carries no script node.
//
// Writer of quest record 22604 = "1", the COMPLETE gate of quest 22589 "Dangerous Premonition"
// (Check.img/22589/1: infoNumber 22604, infoex/0/value "1").
//
// The quest names the act: QuestInfo.img/22589/1 "#bTurn around and return to the Peaceful Cave
// where the Dragon is sleeping#k!", and Say.img/22589/1/stop/default/0 - the line shown while
// 22604 is not yet "1" - "Master! Let's go into the #bquiet cave where the dragon sleeps#k! Let's
// go back in there and find out what's going on!". Which of the four Cave of Silence rooms that is
// is settled by the room's own contents, not by elimination: 914100023 is the only one whose
// info/bgm is Bgm18/BlackWing and the only one carrying ten m 9300392 "Black Wing Henchman", and
// QuestInfo.img/22589/2 is the ambush - "When you talk to #p1013203#, who suddenly appeared in the
// cave, he attacked".
//
// NOTE for anyone re-deriving this: V84-OPEN-ITEMS used to name 914100021 for 22604. That is
// Afrien's room (life/0/id = 1205000) and it belongs to 22590/22591, whose Check.img entries name
// npc 1205000. evanTogether.js must NOT be given this write.
//
// The guard picks the record: Quest.getInfoNumber resolves 22604 only for a STARTED 22589 (the
// COMPLETE block); while 22589 is NOT_STARTED the same call yields 22600, the start gate, and this
// would satisfy the wrong half of the quest.
//
// ponytail: the record write and the UI unlock. Hiver "suddenly appearing" is a spawn v84 states
// nowhere - 1013203 is placed in exactly one map in all of Map.wz (922030000) and this room's life
// is ten henchmen - so the encounter is not invented here. Effect.wz has no Direction node named
// blackSDI, so this must never play a cutscene.
function start(ms) {
    ms.unlockUI();

    if (ms.getQuestStatus(22589) == 1) {
        ms.setQuestProgress(22589, 22604, 1);
    }
}
