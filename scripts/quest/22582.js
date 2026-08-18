/*
 * Evan story quest 22582. v84 Check.img/22582: endscript q22582e and no startscript, so only
 * the completion side is server scripted. Act.img/22582/1 removes item 4000144 - x100 in v84,
 * x50 here under the owner's halving - and pays nothing else. The removal below is the one that
 * actually runs, so it carries the halved number too, or the client would offer completion at
 * 50 and this would try to take 100.
 *
 * The payout is repeated here on purpose. A scripted end never reaches Quest.complete() -
 * QuestActionHandler case 5 hands straight to QuestScriptManager.end(), and forceCompleteQuest()
 * only writes the status - so a scripted end that does not pay out, does not pay out at all.
 * Same reason quest/22000.js calls gainExp(20) next to its forceCompleteQuest().
 */
function end(mode, type, selection) {
    qm.forceCompleteQuest();
    qm.gainItem(4000144, -50);
    qm.dispose();
}