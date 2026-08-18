// NPC 1300014 on 106020300 (Mushroom Castle), hide=1, reached only through the map's
// "investigate1" trigger portal (scripts/portal/investigate1.js).
//
// ponytail: the dispose is deliberate and stays. String.wz/Npc.img/1300014 is name="SELF" with no
// func and no d0/n0 lines - the placeholder name is the only text the client ships for it - and
// 1300014 appears in ZERO nodes of Act.img, Check.img, Say.img, QuestInfo.img, Exclusive.img,
// PQuest.img and PQuestSearch.img, so no quest hangs off it either. Any dialogue written here would
// be invented. Ticket 43 refuses it on those grounds; if v84 text for it ever turns up, that is the
// trigger to replace this.
function start() {
    cm.dispose();
}
