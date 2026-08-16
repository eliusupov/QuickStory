/*
 * Evan story quest 22546. v84 Check.img/22546: endscript q22546e and no startscript, so only
 * the completion side is server scripted. Act.img/22546/1 removes item 4161050 x1 and pays nothing else.
 *
 * The payout is repeated here on purpose. A scripted end never reaches Quest.complete() -
 * QuestActionHandler case 5 hands straight to QuestScriptManager.end(), and forceCompleteQuest()
 * only writes the status - so a scripted end that does not pay out, does not pay out at all.
 * Same reason quest/22000.js calls gainExp(20) next to its forceCompleteQuest().
 */
function end(mode, type, selection) {
    qm.forceCompleteQuest();
    qm.gainItem(4161050, -1);
    qm.dispose();
}