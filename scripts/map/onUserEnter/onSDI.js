// Map 914100010 (Slumbering Dragon Island : Snowy Forest), the first map on the island that
// declares a hook at all: Map/Map9/914100010.img/info/onUserEnter = "onSDI".
//
// Writer of quest record 22599 = "1", the START gate of quest 22580 "Slumbering Dragon Island"
// (Check.img/22580/0: infoNumber 22599, infoex/0/value "1").
//
// Why here, and why arrival. QuestInfo.img/22580/0 is pure arrival state - "You arrive on the
// island after traveling for a long time on #p1002101#'s ship... #p1013000# seems to feel
// something" - and names nothing to click; Check.img/22580/0 declares only npc 1013000, who is
// Mir, a summoned dragon placed on no map. The maps before this one declare no hook to write it
// from: 200090080 and 914100000 both carry info/onUserEnter and info/onFirstUserEnter as the
// EMPTY STRING (v84 positively saying "no script"), 914100000/portal/1/script is "" as well, its
// reactor node is empty, and its one NPC 1013207 Olaf only boards the ferry
// (scripts/npc/1013207.js, ticket 55 R46 - the earlier note here called him "client-scripted and
// therefore unreachable", which is wrong: 1200004 and 1100008 carry the same info/script node and
// are server-talkable today). He writes no record, so this is still the first hook on the path.
// Mir's line fits the room: Say.img/22580/0/0 "I don't hear anything, not even birds chirping,
// squirrels running, or leaves rustling in the wind" - and 914100010 is the forest, with life EMPTY.
//
// The guard is not politeness, it decides WHICH RECORD is written. Character.setQuestProgress
// resolves the slot through Quest.getInfoNumber(status): while 22580 is NOT_STARTED that reads the
// START block and yields 22599, which is what this write needs. Once 22580 is STARTED the same
// call yields 22599 from the COMPLETE block instead and re-entering the forest would stamp "1"
// back over the "2" that stopIceWall.js wrote, locking the quest. Hence getQuestStatus(22580) == 0.
// getQuestStatus(22579) == 2 is the quest's own prerequisite, Check.img/22580/0/quest/0.
//
// Effect.wz has no Direction node named onSDI, so this is not a cutscene and must never play one -
// MapAndPortalScriptsRealLoad enforces that by name.
//
// ponytail: the record write and the UI unlock, nothing else. Whatever else GMS ran on arrival is
// not in the WZ, and the island is not even enterable yet (enterSDI / enterSnowDragon are both
// tm=999999999 with no script). Same treatment as evanTogether.js.
function start(ms) {
    ms.unlockUI();

    if (ms.getQuestStatus(22579) == 2 && ms.getQuestStatus(22580) == 0) {
        ms.setQuestProgress(22580, 22599, 1);
    }
}
