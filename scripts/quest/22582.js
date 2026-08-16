/*
 * Evan story quest 22582. v84 Check.img/22582: endscript q22582e and no startscript, so only
 * the completion side is server scripted. Act.img/22582/1 removes item 4000144 x100 and pays nothing else.
 *
 * The payout is repeated here on purpose. A scripted end never reaches Quest.complete() -
 * QuestActionHandler case 5 hands straight to QuestScriptManager.end(), and forceCompleteQuest()
 * only writes the status - so a scripted end that does not pay out, does not pay out at all.
 * Same reason quest/22000.js calls gainExp(20) next to its forceCompleteQuest().
 */
function end(mode, type, selection) {
    qm.forceCompleteQuest();
    qm.gainItem(4000144, -100);
    qm.dispose();
}