/* Quest 28363 - "Third Evan Launch Gift from the Admin" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.19+. Act.img/28363 is empty;
 * QuestInfo.img/28363/rewardSummary declares "#i1003089:# #t1003089:# 1".
 * Check.img/28363/0/end is "2010050500" - the event window has closed.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    if (!qm.canHold(1003089)) {
        qm.sendOk("Please make some room in your equip inventory first.");
        qm.dispose();
        return;
    }
    qm.gainItem(1003089, 1);
    qm.forceCompleteQuest();
    qm.dispose();
}
