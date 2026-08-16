/*
 * Evan story quest 22567 - Secret Organization 1. v84 Check.img/22567: endscript q22567e,
 * completion at npc 2012034 requires item 4032468 x10. Act.img/22567 is empty in v84 -
 * both states - so the ten Growth Accelerants are consumed here, the way every other Evan
 * quest consumes its requirement through a negative Act item count. No exp or sp is invented:
 * v84 grants none for this quest.
 *
 * A scripted end never reaches Quest.complete() - QuestActionHandler case 5 hands straight to
 * QuestScriptManager.end(), and forceCompleteQuest() only writes the status - so anything a
 * scripted end owes the player has to be done here, as quest/22000.js does with its gainExp(20).
 */
function end(mode, type, selection) {
    qm.forceCompleteQuest();
    qm.gainItem(4032468, -10);      // after the status write, so a failed removal cannot strand
    qm.dispose();                   // the quest in STARTED with the items already gone
}