/* Quest 28364 - "Fourth Evan Launch Gift from the Admin" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.20+. Act.img/28364 is empty;
 * QuestInfo.img/28364/rewardSummary declares "#i1072443:# #t1072443:# 1".
 * Check.img/28364/0/end is "2010050500" - the event window has closed.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    if (!qm.canHold(1072443)) {
        qm.sendOk("Please make some room in your equip inventory first.");
        qm.dispose();
        return;
    }
    qm.gainItem(1072443, 1);
    qm.forceCompleteQuest();
    qm.dispose();
}
