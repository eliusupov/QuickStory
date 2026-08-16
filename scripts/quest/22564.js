/*
 * Evan story quest 22564. v84 Check.img/22564: endscript q22564e and no startscript, so only
 * the completion side is server scripted. Act.img/22564 is empty in v84 - this quest pays nothing.
 *
 * The payout is repeated here on purpose. A scripted end never reaches Quest.complete() -
 * QuestActionHandler case 5 hands straight to QuestScriptManager.end(), and forceCompleteQuest()
 * only writes the status - so a scripted end that does not pay out, does not pay out at all.
 * Same reason quest/22000.js calls gainExp(20) next to its forceCompleteQuest().
 */
function end(mode, type, selection) {
    qm.forceCompleteQuest();
    qm.dispose();
}